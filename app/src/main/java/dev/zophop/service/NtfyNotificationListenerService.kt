package dev.zophop.service

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import dev.zophop.data.AppDatabase
import dev.zophop.data.DefaultWorkflowInitializer
import dev.zophop.data.EncryptedSettingsRepository
import dev.zophop.data.RoomCredentialRepository
import dev.zophop.data.RoomWorkflowRepository
import dev.zophop.data.WorkflowExecutionLogEntity
import dev.zophop.domain.engine.ExecutionContext
import dev.zophop.domain.engine.PreconditionEvaluator
import dev.zophop.domain.engine.WorkflowEngine
import dev.zophop.domain.engine.WorkflowExecutionResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.UUID

class NtfyNotificationListenerService : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return
        if (sbn.packageName == packageName) return

        val settings = EncryptedSettingsRepository(applicationContext).getSettings()
        if (!settings.globalWorkflowsEnabled) return

        val extras = sbn.notification?.extras ?: return

        val rawTitle = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
            ?: extras.getCharSequence(Notification.EXTRA_TITLE_BIG)?.toString()

        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
            ?: extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
            ?: return

        if (text.isBlank()) return

        val sender = if (!rawTitle.isNullOrBlank()) rawTitle else sbn.packageName

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = AppDatabase.getInstance(applicationContext)
                val workflowRepository = RoomWorkflowRepository(db.workflowDao())
                val credentialRepository = RoomCredentialRepository(db.credentialDao())

                DefaultWorkflowInitializer(workflowRepository).checkAndInitialize()

                val activeWorkflows = workflowRepository.getActiveWorkflows()
                val evaluator = PreconditionEvaluator(applicationContext)
                val engine = WorkflowEngine(evaluator, credentialRepository)

                val execContext = ExecutionContext(
                    mapOf(
                        "package_name" to sbn.packageName,
                        "title" to (rawTitle ?: sbn.packageName),
                        "text" to text,
                        "sender" to sender,
                        "body" to text,
                        "received_at" to System.currentTimeMillis()
                    )
                )

                for (workflow in activeWorkflows) {
                    val result = engine.executeWorkflow(workflow, execContext, applicationContext)
                    val logEntity = WorkflowExecutionLogEntity(
                        id = UUID.randomUUID().toString(),
                        workflowId = workflow.id,
                        status = when (result) {
                            is WorkflowExecutionResult.Success -> "SUCCESS"
                            is WorkflowExecutionResult.PreconditionFailed -> "PRECONDITION_FAILED"
                            is WorkflowExecutionResult.Failed -> "FAILED"
                            is WorkflowExecutionResult.Cancelled -> "CANCELLED"
                        },
                        timestamp = System.currentTimeMillis(),
                        messageId = null,
                        logs = when (result) {
                            is WorkflowExecutionResult.Success -> result.logs.joinToString("\n")
                            is WorkflowExecutionResult.PreconditionFailed -> result.logs.joinToString("\n")
                            is WorkflowExecutionResult.Failed -> result.logs.joinToString("\n")
                            is WorkflowExecutionResult.Cancelled -> result.logs.joinToString("\n")
                        },
                        errorReason = when (result) {
                            is WorkflowExecutionResult.Success -> null
                            is WorkflowExecutionResult.PreconditionFailed -> result.reason
                            is WorkflowExecutionResult.Failed -> result.reason
                            is WorkflowExecutionResult.Cancelled -> "Execution cancelled by user"
                        }
                    )
                    workflowRepository.saveExecutionLog(logEntity)
                }
            } catch (_: Exception) {
            }
        }
    }
}
