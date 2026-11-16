package `in`.ntfysms.worker

import `in`.ntfysms.data.AppSettings
import `in`.ntfysms.data.MessageDao
import `in`.ntfysms.data.MessageEntity
import `in`.ntfysms.data.MessageStatus
import `in`.ntfysms.data.SettingsRepository
import `in`.ntfysms.network.NtfyClient
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

class FakeMessageDao : MessageDao {
    val messages = mutableMapOf<String, MessageEntity>()

    override suspend fun insertMessage(message: MessageEntity) {
        messages[message.id] = message
    }

    override suspend fun updateMessage(message: MessageEntity) {
        messages[message.id] = message
    }

    override suspend fun getMessageById(id: String): MessageEntity? {
        return messages[id]
    }

    override suspend fun getPendingMessages(): List<MessageEntity> {
        return messages.values.filter { it.status == MessageStatus.QUEUED || it.status == MessageStatus.FAILED }
    }

    override fun getRecentEventsFlow(limit: Int): Flow<List<MessageEntity>> {
        return flowOf(messages.values.sortedByDescending { it.receivedAt }.take(limit))
    }

    override fun getAllEventsFlow(): Flow<List<MessageEntity>> {
        return flowOf(messages.values.sortedByDescending { it.receivedAt })
    }

    override suspend fun deleteMessage(id: String) {
        messages.remove(id)
    }

    override suspend fun purgeOldEvents(keepCount: Int) {
        if (messages.size > keepCount) {
            val toKeep = messages.values.sortedByDescending { it.receivedAt }.take(keepCount).map { it.id }.toSet()
            val toRemove = messages.keys.filter { it !in toKeep }
            toRemove.forEach { messages.remove(it) }
        }
    }

    override suspend fun deleteMessagesOlderThan(cutoffTimestamp: Long): Int {
        val toRemove = messages.values.filter { it.receivedAt < cutoffTimestamp }.map { it.id }
        toRemove.forEach { messages.remove(it) }
        return toRemove.size
    }

    override suspend fun clearAllEvents() {
        messages.clear()
    }
}

class FakeNtfyClient(
    private val failCount: Int = 0
) : NtfyClient {
    var callCount = 0

    override suspend fun postMessage(
        serverUrl: String,
        topic: String,
        token: String,
        sender: String,
        body: String,
        allowInsecurePrivateHttp: Boolean,
        serverType: String
    ): Result<Unit> {
        callCount++
        return if (callCount <= failCount) {
            Result.failure(IOException("Fake connection error"))
        } else {
            Result.success(Unit)
        }
    }
}

class FakeSettingsRepository(private val settings: AppSettings) : SettingsRepository {
    constructor() : this(AppSettings(isEnabled = true, serverUrl = "https://ntfy.sh", topic = "otp", expiryMinutes = 10))

    override fun getSettings(): AppSettings = settings
    override fun saveSettings(settings: AppSettings) {}
    override fun setEnabled(enabled: Boolean) {}
}

class QueueAndRetryTest {

    private lateinit var messageDao: FakeMessageDao
    private lateinit var settingsRepo: FakeSettingsRepository

    @Before
    fun setup() {
        messageDao = FakeMessageDao()
        settingsRepo = FakeSettingsRepository()
    }

    @Test
    fun testSuccessfulDeliveryClearsBodyAndMarksDelivered() = runTest {
        val fakeClient = FakeNtfyClient(failCount = 0)
        val manager = ForwardingManager(
            messageDao = messageDao,
            settingsRepository = settingsRepo,
            ntfyClient = fakeClient,
            delayProvider = {} // Instant delay for fast tests
        )

        val entity = manager.enqueueIncomingSms(sender = "HDFCBK", body = "Your OTP is 123456")

        val updated = messageDao.getMessageById(entity.id)
        assertTrue(updated != null)
        assertEquals(MessageStatus.DELIVERED, updated?.status)
        assertNull(updated?.body) // OTP text must be deleted immediately on delivery!
        assertEquals(1, fakeClient.callCount)
    }

    @Test
    fun testExpiredMessageIsNotSentAndBodyIsCleared() = runTest {
        val fakeClient = FakeNtfyClient(failCount = 0)
        val manager = ForwardingManager(
            messageDao = messageDao,
            settingsRepository = settingsRepo,
            ntfyClient = fakeClient,
            delayProvider = {}
        )

        val oldTimestamp = System.currentTimeMillis() - (15 * 60 * 1000L) // 15 minutes ago
        val expiredEntity = MessageEntity(
            id = "msg-old",
            sender = "ICICIB",
            body = "Your OTP is 999888",
            receivedAt = oldTimestamp,
            status = MessageStatus.QUEUED
        )
        messageDao.insertMessage(expiredEntity)

        manager.processPendingQueue()

        val result = messageDao.getMessageById("msg-old")
        assertEquals(MessageStatus.EXPIRED, result?.status)
        assertNull(result?.body) // Body cleared on expiry
        assertEquals(0, fakeClient.callCount) // Never sent to HTTP server
    }

    @Test
    fun testImmediateRetryLoopFailsThenSucceeds() = runTest {
        val fakeClient = FakeNtfyClient(failCount = 2) // Fails first 2 attempts, succeeds on 3rd
        val manager = ForwardingManager(
            messageDao = messageDao,
            settingsRepository = settingsRepo,
            ntfyClient = fakeClient,
            delayProvider = {}
        )

        val entity = manager.enqueueIncomingSms(sender = "HDFCBK", body = "Your OTP is 777666")

        val updated = messageDao.getMessageById(entity.id)
        assertEquals(MessageStatus.DELIVERED, updated?.status)
        assertNull(updated?.body)
        assertEquals(3, fakeClient.callCount)
    }

    @Test
    fun testManualRetryFailedMessage() = runTest {
        val fakeClient = FakeNtfyClient(failCount = 0)
        val manager = ForwardingManager(
            messageDao = messageDao,
            settingsRepository = settingsRepo,
            ntfyClient = fakeClient,
            delayProvider = {}
        )

        val failedMsg = MessageEntity(
            id = "msg-fail",
            sender = "AXISBK",
            body = null,
            receivedAt = System.currentTimeMillis(),
            status = MessageStatus.FAILED,
            errorMessage = "HTTP 500"
        )
        messageDao.insertMessage(failedMsg)

        val result = manager.retryMessage("msg-fail")
        assertTrue(result.isSuccess)
        val updated = messageDao.getMessageById("msg-fail")
        assertEquals(MessageStatus.DELIVERED, updated?.status)
    }

    @Test
    fun testClearAllEvents() = runTest {
        val fakeClient = FakeNtfyClient(failCount = 0)
        val manager = ForwardingManager(
            messageDao = messageDao,
            settingsRepository = settingsRepo,
            ntfyClient = fakeClient,
            delayProvider = {}
        )

        manager.enqueueIncomingSms(sender = "SENDER-1", body = "Body 1")
        manager.enqueueIncomingSms(sender = "SENDER-2", body = "Body 2")
        assertTrue(messageDao.messages.isNotEmpty())

        manager.clearAllEvents()
        assertTrue(messageDao.messages.isEmpty())
    }
}
