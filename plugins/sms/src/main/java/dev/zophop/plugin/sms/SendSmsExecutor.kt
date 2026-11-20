package dev.zophop.plugin.sms

import android.content.Context
import android.content.pm.PackageManager
import android.telephony.SmsManager
import androidx.core.content.ContextCompat
import dev.zophop.domain.model.WorkflowNode
import dev.zophop.plugin.api.ExecutionContext
import dev.zophop.plugin.api.NodeExecutionResult
import dev.zophop.plugin.api.NodeExecutor
import org.json.JSONObject

/**
 * Strategy executor for [NodeType.SEND_SMS_ACTION].
 */
class SendSmsExecutor : NodeExecutor {
    override suspend fun execute(node: WorkflowNode, context: ExecutionContext, appContext: Context?): NodeExecutionResult {
        if (appContext != null) {
            val hasSendSms = ContextCompat.checkSelfPermission(
                appContext,
                android.Manifest.permission.SEND_SMS
            ) == PackageManager.PERMISSION_GRANTED
            if (!hasSendSms) {
                return NodeExecutionResult.Failed("Missing Send SMS permission (SEND_SMS)")
            }
        }

        val config = try { JSONObject(node.configJson) } catch (_: Exception) { JSONObject() }
        val recipientTemplate = config.optString("recipient", "")
        val bodyTemplate = config.optString("body", "\${formatted_payload}")

        val recipient = context.interpolate(recipientTemplate).trim()
        val body = context.interpolate(bodyTemplate).trim()

        if (recipient.isBlank() || body.isBlank()) {
            return NodeExecutionResult.Failed("SMS recipient or body is empty")
        }

        return try {
            val smsManager = SmsManager.getDefault()
            smsManager.sendTextMessage(recipient, null, body, null, null)
            NodeExecutionResult.Success(logMessage = "Dispatched SMS to $recipient")
        } catch (e: Exception) {
            NodeExecutionResult.Failed("Failed to send SMS: ${e.message}")
        }
    }
}
