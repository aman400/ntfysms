package `in`.ntfysms.worker

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
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
import `in`.ntfysms.service.SmsForwarderService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.UUID
import java.util.concurrent.TimeUnit

class ForwardingManager(
    private val messageDao: MessageDao,
    private val settingsRepository: SettingsRepository,
    private val ntfyClient: NtfyClient = OkHttpNtfyClient(),
    private val workManager: WorkManager? = null,
    private val preCheckManager: PreCheckManager? = null,
    private val delayProvider: suspend (Long) -> Unit = { delay(it) },
    private val context: Context? = null
) {

    private val mutex = Mutex()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    @Volatile private var isPollingActive = false
    private var pollingJob: Job? = null

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

        // Trigger queue processing immediately
        processPendingQueue()
        entity
    }

    suspend fun processPendingQueue(): Unit = withContext(Dispatchers.IO) {
        mutex.withLock {
            try {
                val settings = settingsRepository.getSettings()

                // Run auto-clear if enabled
                if (settings.autoClearEnabled && settings.autoClearMinutes > 0) {
                    val cutoff = System.currentTimeMillis() - (settings.autoClearMinutes * 60 * 1000L)
                    messageDao.deleteMessagesOlderThan(cutoff)
                }

                if (!settings.isEnabled) {
                    return@withLock
                }

                val pending = messageDao.getPendingMessages()
                if (pending.isEmpty()) {
                    return@withLock
                }

                val activeNodes = settings.activeNodes.filter { it.isEnabled && it.serverUrl.isNotBlank() }
                if (activeNodes.isEmpty()) {
                    return@withLock
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

                    var allNodesDelivered = true
                    val nodeNotes = mutableListOf<String>()
                    val preCheckFailures = mutableListOf<String>()

                    for (node in activeNodes) {
                        // Node-Level Pre-Checks Verification & Throttling
                        if (preCheckManager != null) {
                            val nodeCheck = preCheckManager.verifyNodePreChecks(node)
                            if (nodeCheck is PreCheckResult.Failed) {
                                allNodesDelivered = false
                                val failureDesc = "${node.name} (${nodeCheck.reason})"
                                nodeNotes.add("${node.name} throttled: ${nodeCheck.reason}")
                                preCheckFailures.add(failureDesc)
                                continue
                            }
                        }

                        // Update notification status: Executing payload forwarding
                        context?.let { ctx ->
                            SmsForwarderService.updateNotificationStatus(
                                ctx,
                                "🚀 Executing Forwarding Trigger",
                                "Forwarding SMS from ${current.sender} to ${node.name}..."
                            )
                        }

                        var nodeDelivered = false
                        val maxImmediateAttempts = 4

                        for (attempt in 1..maxImmediateAttempts) {
                            val result = ntfyClient.postMessage(
                                serverUrl = node.serverUrl,
                                topic = node.topic,
                                token = node.token,
                                sender = current.sender,
                                body = formattedPayload,
                                allowInsecurePrivateHttp = settings.allowInsecureHttp,
                                serverType = node.serverType
                            )

                            if (result.isSuccess) {
                                nodeDelivered = true
                                break
                            } else {
                                val errMsg = result.exceptionOrNull()?.message ?: "Delivery failure"
                                nodeNotes.add("${node.name}: $errMsg")
                                if (attempt < maxImmediateAttempts) {
                                    delayProvider(15_000L)
                                }
                            }
                        }

                        if (!nodeDelivered) {
                            allNodesDelivered = false
                        }
                    }

                    if (allNodesDelivered) {
                        val deliveredEntity = current.copy(
                            status = MessageStatus.DELIVERED,
                            body = null, // Delete body immediately on delivery
                            retryCount = current.retryCount + 1,
                            errorMessage = null
                        )
                        messageDao.updateMessage(deliveredEntity)

                        context?.let { ctx ->
                            SmsForwarderService.updateNotificationStatus(
                                ctx,
                                "✅ Forwarding Trigger Executed",
                                "Successfully delivered SMS from ${current.sender} to target nodes"
                            )
                            delayProvider(4000L)
                            SmsForwarderService.resetToDefaultStatus(ctx)
                        }
                    } else {
                        hasUnsentFailures = true
                        val isThrottled = preCheckFailures.isNotEmpty()
                        val newRetryCount = if (isThrottled) current.retryCount else current.retryCount + 1

                        val (updatedStatus, noteText) = if (isThrottled) {
                            MessageStatus.QUEUED to "⏳ Waiting for Pre-Conditions: " + preCheckFailures.joinToString("; ")
                        } else if (newRetryCount >= settings.maxRetries) {
                            MessageStatus.FAILED to "Failed after ${settings.maxRetries} retries: " + nodeNotes.joinToString("; ")
                        } else {
                            MessageStatus.QUEUED to "Retry attempt $newRetryCount of ${settings.maxRetries}: " + nodeNotes.joinToString("; ")
                        }

                        val updatedEntity = current.copy(
                            status = updatedStatus,
                            retryCount = newRetryCount,
                            errorMessage = noteText
                        )
                        messageDao.updateMessage(updatedEntity)

                        context?.let { ctx ->
                            if (isThrottled) {
                                SmsForwarderService.updateNotificationStatus(
                                    ctx,
                                    "⏳ Waiting on Pre-Condition",
                                    "SMS from ${current.sender}: Waiting for ${preCheckFailures.joinToString(", ")}"
                                )
                            } else if (updatedStatus == MessageStatus.QUEUED) {
                                SmsForwarderService.updateNotificationStatus(
                                    ctx,
                                    "🔄 Retrying Forwarding Trigger",
                                    "SMS from ${current.sender}: Attempt $newRetryCount of ${settings.maxRetries}"
                                )
                            } else {
                                SmsForwarderService.updateNotificationStatus(
                                    ctx,
                                    "❌ Forwarding Trigger Failed",
                                    "Failed to deliver SMS from ${current.sender}: ${nodeNotes.joinToString("; ")}"
                                )
                                delayProvider(5000L)
                                SmsForwarderService.resetToDefaultStatus(ctx)
                            }
                        }
                    }
                }

                if (hasUnsentFailures) {
                    startActiveQueuePolling()
                    if (workManager != null) {
                        scheduleWorkManagerRetry()
                    }
                }
            } catch (e: Exception) {
                // Log exception
            }
        }
    }

    private fun startActiveQueuePolling() {
        synchronized(this) {
            if (isPollingActive) return
            isPollingActive = true
        }

        pollingJob?.cancel()
        pollingJob = scope.launch {
            try {
                while (isPollingActive) {
                    delay(10_000L) // Poll pre-conditions every 10s while messages are queued
                    val pending = messageDao.getPendingMessages()
                    if (pending.isEmpty()) {
                        synchronized(this@ForwardingManager) { isPollingActive = false }
                        break
                    }
                    processPendingQueue()
                }
            } catch (e: Exception) {
                synchronized(this@ForwardingManager) { isPollingActive = false }
            }
        }
    }

    suspend fun retryMessage(messageId: String): Result<Unit> = withContext(Dispatchers.IO) {
        val current = messageDao.getMessageById(messageId)
            ?: return@withContext Result.failure(IllegalArgumentException("Message log not found"))

        val rawBody = current.body ?: "Retry delivery for message from ${current.sender}"

        // Reset status to QUEUED and timestamp to now so expiry timer resets
        val resetEntity = current.copy(
            receivedAt = System.currentTimeMillis(),
            body = rawBody,
            status = MessageStatus.QUEUED,
            errorMessage = "Retrying message delivery..."
        )
        messageDao.updateMessage(resetEntity)

        // Process queue immediately
        processPendingQueue()
        Result.success(Unit)
    }

    fun scheduleWorkManagerRetry() {
        workManager?.let { wm ->
            val constraints = Constraints.Builder().build()

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
                ExistingWorkPolicy.REPLACE,
                workRequest
            )
        }
    }

    suspend fun sendTestMessage(): Result<Unit> = withContext(Dispatchers.IO) {
        val settings = settingsRepository.getSettings()
        val activeNodes = settings.activeNodes.filter { it.isEnabled && it.serverUrl.isNotBlank() }
        if (activeNodes.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException("No active target nodes configured"))
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

        var overallSuccess = true
        val errors = mutableListOf<String>()

        context?.let { ctx ->
            SmsForwarderService.updateNotificationStatus(
                ctx,
                "🚀 Executing Test Trigger",
                "Sending test ping to target delivery nodes..."
            )
        }

        for (node in activeNodes) {
            if (preCheckManager != null) {
                val nodeCheck = preCheckManager.verifyNodePreChecks(node)
                if (nodeCheck is PreCheckResult.Failed) {
                    overallSuccess = false
                    errors.add("${node.name} throttled: ${nodeCheck.reason}")
                    continue
                }
            }

            val result = ntfyClient.postMessage(
                serverUrl = node.serverUrl,
                topic = node.topic,
                token = node.token,
                sender = testSender,
                body = formattedPayload,
                allowInsecurePrivateHttp = settings.allowInsecureHttp,
                serverType = node.serverType
            )

            if (result.isFailure) {
                overallSuccess = false
                errors.add("${node.name}: ${result.exceptionOrNull()?.message}")
            }
        }

        val isThrottled = errors.any { it.contains("throttled") }
        val status = if (overallSuccess) {
            MessageStatus.DELIVERED
        } else if (isThrottled) {
            MessageStatus.QUEUED
        } else {
            MessageStatus.FAILED
        }

        val errorSummary = if (errors.isNotEmpty()) {
            if (isThrottled) "⏳ Waiting for Pre-Conditions: " + errors.joinToString("; ") else errors.joinToString("; ")
        } else null

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

        context?.let { ctx ->
            if (overallSuccess) {
                SmsForwarderService.updateNotificationStatus(
                    ctx,
                    "✅ Test Trigger Executed",
                    "Test ping successfully delivered to target nodes"
                )
            } else if (isThrottled) {
                SmsForwarderService.updateNotificationStatus(
                    ctx,
                    "⏳ Waiting on Pre-Condition",
                    "Test ping waiting for pre-conditions"
                )
            } else {
                SmsForwarderService.updateNotificationStatus(
                    ctx,
                    "❌ Test Trigger Failed",
                    "Test ping failed: ${errorSummary ?: "Pre-check or server error"}"
                )
            }
            delayProvider(4000L)
            SmsForwarderService.resetToDefaultStatus(ctx)
        }

        if (overallSuccess) Result.success(Unit) else Result.failure(Exception(errorSummary ?: "Test failed"))
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
                    preCheckManager = pcm,
                    context = context.applicationContext
                )
                INSTANCE = instance
                instance
            }
        }
    }
}
