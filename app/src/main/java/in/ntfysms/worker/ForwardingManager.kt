package `in`.ntfysms.worker

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import `in`.ntfysms.data.AppDatabase
import `in`.ntfysms.data.EncryptedSettingsRepository
import `in`.ntfysms.data.MessageDao
import `in`.ntfysms.data.MessageEntity
import `in`.ntfysms.data.MessageStatus
import `in`.ntfysms.data.SettingsRepository
import `in`.ntfysms.filter.MessageTemplateParser
import `in`.ntfysms.network.NtfyClient
import `in`.ntfysms.network.OkHttpNtfyClient
import `in`.ntfysms.network.PreCheckManager
import `in`.ntfysms.network.PreCheckResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import java.util.UUID
import java.util.concurrent.TimeUnit

class ForwardingManager(
    private val messageDao: MessageDao,
    private val settingsRepository: SettingsRepository,
    private val ntfyClient: NtfyClient = OkHttpNtfyClient(),
    private val workManager: WorkManager? = null,
    private val preCheckManager: PreCheckManager? = null,
    private val delayProvider: suspend (Long) -> Unit = { delay(it) }
) {

    private val mutex = Mutex()

    suspend fun enqueueIncomingSms(sender: String, body: String): MessageEntity = withContext(Dispatchers.IO) {
        val entity = MessageEntity(
            id = UUID.randomUUID().toString(),
            sender = sender,
            body = body,
            receivedAt = System.currentTimeMillis(),
            status = MessageStatus.QUEUED,
            retryCount = 0
        )
        messageDao.insertMessage(entity)
        messageDao.purgeOldEvents(50)

        // Trigger queue processing
        processPendingQueue()
        entity
    }

    suspend fun processPendingQueue(): Unit = withContext(Dispatchers.IO) {
        if (!mutex.tryLock()) {
            return@withContext
        }
        try {
            val settings = settingsRepository.getSettings()

            // Run auto-clear if enabled
            if (settings.autoClearEnabled && settings.autoClearMinutes > 0) {
                val cutoff = System.currentTimeMillis() - (settings.autoClearMinutes * 60 * 1000L)
                messageDao.deleteMessagesOlderThan(cutoff)
            }

            if (!settings.isEnabled) {
                return@withContext
            }

            val pending = messageDao.getPendingMessages()
            if (pending.isEmpty()) return@withContext

            val targetUrl = settings.activeServerUrl
            if (targetUrl.isBlank()) return@withContext

            // Pre-checks verification (Internet, VPN, Healthcheck)
            preCheckManager?.let { pcm ->
                val preCheckResult = pcm.verifyPreChecks(settings)
                if (preCheckResult is PreCheckResult.Failed) {
                    for (msg in pending) {
                        messageDao.updateMessage(
                            msg.copy(
                                status = MessageStatus.QUEUED,
                                errorMessage = "Pre-check paused: ${preCheckResult.reason}"
                            )
                        )
                    }
                    if (workManager != null) {
                        scheduleWorkManagerRetry()
                    }
                    return@withContext
                }
            }

            val now = System.currentTimeMillis()
            val expiryMillis = settings.expiryMinutes * 60 * 1000L

            var hasUnsentFailures = false

            for (msg in pending) {
                val current = messageDao.getMessageById(msg.id) ?: continue
                if (current.status == MessageStatus.DELIVERED || current.status == MessageStatus.EXPIRED) {
                    continue
                }

                // Check expiry
                if (now - current.receivedAt > expiryMillis) {
                    val expiredEntity = current.copy(
                        status = MessageStatus.EXPIRED,
                        body = null, // Delete OTP text on expiry
                        errorMessage = "Expired after ${settings.expiryMinutes} minutes"
                    )
                    messageDao.updateMessage(expiredEntity)
                    continue
                }

                val msgBody = current.body
                if (msgBody == null) {
                    messageDao.updateMessage(current.copy(status = MessageStatus.EXPIRED, body = null))
                    continue
                }

                val formattedPayload = MessageTemplateParser.formatPayload(
                    sender = current.sender,
                    body = msgBody,
                    senderRegex = settings.senderRegex,
                    bodyParseRegex = settings.bodyParseRegex,
                    template = settings.customMessageTemplate
                )

                var delivered = false
                var attempts = current.retryCount
                val maxImmediateAttempts = 4

                for (attempt in 1..maxImmediateAttempts) {
                    attempts++
                    val result = ntfyClient.postMessage(
                        serverUrl = targetUrl,
                        topic = settings.activeTopic,
                        token = settings.activeToken,
                        sender = current.sender,
                        body = formattedPayload,
                        allowInsecurePrivateHttp = settings.allowInsecureHttp,
                        serverType = settings.serverType
                    )

                    if (result.isSuccess) {
                        delivered = true
                        val deliveredEntity = current.copy(
                            status = MessageStatus.DELIVERED,
                            body = null, // Delete body immediately on delivery
                            retryCount = attempts,
                            errorMessage = null
                        )
                        messageDao.updateMessage(deliveredEntity)
                        break
                    } else {
                        val errMsg = result.exceptionOrNull()?.message ?: "HTTP delivery failure"
                        val updatedFailed = current.copy(
                            status = MessageStatus.FAILED,
                            retryCount = attempts,
                            errorMessage = errMsg
                        )
                        messageDao.updateMessage(updatedFailed)

                        if (attempt < maxImmediateAttempts) {
                            delayProvider(15_000L)
                        }
                    }
                }

                if (!delivered) {
                    hasUnsentFailures = true
                }
            }

            if (hasUnsentFailures && workManager != null) {
                scheduleWorkManagerRetry()
            }
        } finally {
            mutex.unlock()
        }
    }

    suspend fun retryMessage(messageId: String): Result<Unit> = withContext(Dispatchers.IO) {
        val settings = settingsRepository.getSettings()
        val current = messageDao.getMessageById(messageId)
            ?: return@withContext Result.failure(IllegalArgumentException("Message log not found"))

        val targetUrl = settings.activeServerUrl
        if (targetUrl.isBlank()) {
            return@withContext Result.failure(IllegalStateException("No server URL configured in Settings"))
        }

        // Run pre-checks before manual retry as well
        preCheckManager?.let { pcm ->
            val preCheckResult = pcm.verifyPreChecks(settings)
            if (preCheckResult is PreCheckResult.Failed) {
                return@withContext Result.failure(IllegalStateException("Pre-check failed: ${preCheckResult.reason}"))
            }
        }

        // If body is missing (purged), we send a notification summary retry
        val rawBody = current.body ?: "Retry ping for message from ${current.sender}"
        val formattedPayload = MessageTemplateParser.formatPayload(
            sender = current.sender,
            body = rawBody,
            senderRegex = settings.senderRegex,
            bodyParseRegex = settings.bodyParseRegex,
            template = settings.customMessageTemplate
        )

        val result = ntfyClient.postMessage(
            serverUrl = targetUrl,
            topic = settings.activeTopic,
            token = settings.activeToken,
            sender = current.sender,
            body = formattedPayload,
            allowInsecurePrivateHttp = settings.allowInsecureHttp,
            serverType = settings.serverType
        )

        if (result.isSuccess) {
            val updated = current.copy(
                status = MessageStatus.DELIVERED,
                body = null, // Clear body
                retryCount = current.retryCount + 1,
                errorMessage = null
            )
            messageDao.updateMessage(updated)
            Result.success(Unit)
        } else {
            val err = result.exceptionOrNull()?.message ?: "Retry delivery failed"
            val updated = current.copy(
                status = MessageStatus.FAILED,
                retryCount = current.retryCount + 1,
                errorMessage = err
            )
            messageDao.updateMessage(updated)
            Result.failure(Exception(err))
        }
    }

    fun scheduleWorkManagerRetry() {
        workManager?.let { wm ->
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val workRequest = OneTimeWorkRequestBuilder<DeliveryWorker>()
                .setConstraints(constraints)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    10_000L,
                    TimeUnit.MILLISECONDS
                )
                .build()

            wm.enqueueUniqueWork(
                WORK_NAME,
                ExistingWorkPolicy.KEEP,
                workRequest
            )
        }
    }

    suspend fun sendTestMessage(): Result<Unit> = withContext(Dispatchers.IO) {
        val settings = settingsRepository.getSettings()
        val targetUrl = settings.activeServerUrl
        if (targetUrl.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Server URL is empty"))
        }

        // Run pre-checks before test dispatch
        preCheckManager?.let { pcm ->
            val preCheckResult = pcm.verifyPreChecks(settings)
            if (preCheckResult is PreCheckResult.Failed) {
                return@withContext Result.failure(IllegalStateException("Pre-check failed: ${preCheckResult.reason}"))
            }
        }

        val testSender = "NtfySMS Test"
        val testBody = "Test message from NtfySMS app"

        val formattedPayload = MessageTemplateParser.formatPayload(
            sender = testSender,
            body = testBody,
            senderRegex = settings.senderRegex,
            bodyParseRegex = settings.bodyParseRegex,
            template = settings.customMessageTemplate
        )

        val result = ntfyClient.postMessage(
            serverUrl = targetUrl,
            topic = settings.activeTopic,
            token = settings.activeToken,
            sender = testSender,
            body = formattedPayload,
            allowInsecurePrivateHttp = settings.allowInsecureHttp,
            serverType = settings.serverType
        )

        val status = if (result.isSuccess) MessageStatus.DELIVERED else MessageStatus.FAILED
        val errorSummary = result.exceptionOrNull()?.message

        val testMsg = MessageEntity(
            id = UUID.randomUUID().toString(),
            sender = testSender,
            body = null, // Do not store body
            receivedAt = System.currentTimeMillis(),
            status = status,
            errorMessage = errorSummary
        )
        messageDao.insertMessage(testMsg)
        messageDao.purgeOldEvents(50)

        if (result.isSuccess) Result.success(Unit) else Result.failure(Exception(errorSummary ?: "Test failed"))
    }

    suspend fun clearAllEvents() = withContext(Dispatchers.IO) {
        messageDao.clearAllEvents()
    }

    companion object {
        const val WORK_NAME = "ntfy_sms_retry_worker"

        @Volatile
        private var INSTANCE: ForwardingManager? = null

        fun getInstance(context: Context): ForwardingManager {
            return INSTANCE ?: synchronized(this) {
                val db = AppDatabase.getInstance(context)
                val repo = EncryptedSettingsRepository(context)
                val wm = WorkManager.getInstance(context.applicationContext)
                val pcm = PreCheckManager(context.applicationContext)
                val instance = ForwardingManager(
                    messageDao = db.messageDao(),
                    settingsRepository = repo,
                    ntfyClient = OkHttpNtfyClient(),
                    workManager = wm,
                    preCheckManager = pcm
                )
                INSTANCE = instance
                instance
            }
        }
    }
}
