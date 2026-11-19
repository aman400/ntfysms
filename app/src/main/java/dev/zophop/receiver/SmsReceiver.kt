package dev.zophop.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
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

class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            return
        }

        val settings = EncryptedSettingsRepository(context).getSettings()
        if (!settings.globalWorkflowsEnabled) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (messages.isNullOrEmpty()) return

        val sender = messages.firstOrNull()?.originatingAddress ?: "Unknown"
        val fullBody = messages.joinToString("") { it.displayMessageBody ?: it.messageBody ?: "" }

        if (sender.isBlank() || fullBody.isBlank()) return

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = AppDatabase.getInstance(context)
                val workflowRepository = RoomWorkflowRepository(db.workflowDao())
                val credentialRepository = RoomCredentialRepository(db.credentialDao())

                DefaultWorkflowInitializer(workflowRepository).checkAndInitialize()

                val activeWorkflows = workflowRepository.getActiveWorkflows()
                val evaluator = PreconditionEvaluator(context)
                val engine = WorkflowEngine(evaluator, credentialRepository)

                val execContext = ExecutionContext(
                    mapOf(
                        "sender" to sender,
                        "body" to fullBody,
                        "received_at" to System.currentTimeMillis()
                    )
                )

                for (workflow in activeWorkflows) {
                    val result = engine.executeWorkflow(workflow, execContext, context)
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
            } finally {
                pendingResult.finish()
            }
        }
    }
}
