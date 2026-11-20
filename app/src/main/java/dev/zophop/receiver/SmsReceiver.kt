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
import dev.zophop.plugin.api.ExecutionContext
import dev.zophop.domain.engine.PreconditionEvaluator
import dev.zophop.domain.engine.WorkflowEngine
import dev.zophop.domain.engine.ExecutionJobRegistry
import dev.zophop.domain.engine.WorkflowExecutionResult
import dev.zophop.domain.engine.toJsonString
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
                val workflowRepository = RoomWorkflowRepository(db.workflowDao(), db.preconditionDao())
                val credentialRepository = RoomCredentialRepository(db.credentialDao())

                DefaultWorkflowInitializer(workflowRepository).checkAndInitialize()

                val activeWorkflows = workflowRepository.getActiveWorkflows()
                val evaluator = PreconditionEvaluator(context)
                val engine = WorkflowEngine(evaluator, credentialRepository)

                val execContext = ExecutionContext(
                    mapOf(
                        "sender" to sender,
                        "header" to sender,
                        "sms_header" to sender,
                        "address" to sender,
                        "body" to fullBody,
                        "sms_body" to fullBody,
                        "message" to fullBody,
                        "text" to fullBody,
                        "received_at" to System.currentTimeMillis()
                    )
                )

                for (workflow in activeWorkflows) {
                    val job = launch {
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
                            logs = result.steps.toJsonString(),
                            errorReason = when (result) {
                                is WorkflowExecutionResult.Success -> null
                                is WorkflowExecutionResult.PreconditionFailed -> result.reason
                                is WorkflowExecutionResult.Failed -> result.reason
                                is WorkflowExecutionResult.Cancelled -> "Execution cancelled by user"
                            }
                        )
                        workflowRepository.saveExecutionLog(logEntity)
                    }
                    ExecutionJobRegistry.registerJob(workflow.id, job)
                    job.join()
                }
            } catch (_: Exception) {
            } finally {
                pendingResult.finish()
            }
        }
    }
}
