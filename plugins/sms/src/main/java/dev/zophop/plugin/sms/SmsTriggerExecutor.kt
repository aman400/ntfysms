package dev.zophop.plugin.sms

import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import dev.zophop.domain.model.WorkflowNode
import dev.zophop.plugin.api.ExecutionContext
import dev.zophop.plugin.api.NodeExecutionResult
import dev.zophop.plugin.api.NodeExecutor
import org.json.JSONObject
import java.util.regex.Pattern

/**
 * Strategy executor for [NodeType.SMS_TRIGGER].
 */
class SmsTriggerExecutor : NodeExecutor {
    override suspend fun execute(node: WorkflowNode, context: ExecutionContext, appContext: Context?): NodeExecutionResult {
        if (appContext != null) {
            val hasReceiveSms = ContextCompat.checkSelfPermission(
                appContext,
                android.Manifest.permission.RECEIVE_SMS
            ) == PackageManager.PERMISSION_GRANTED
            if (!hasReceiveSms) {
                return NodeExecutionResult.Failed("Missing Receive SMS permission (RECEIVE_SMS)")
            }
        }

        val config = try { JSONObject(node.configJson) } catch (_: Exception) { JSONObject() }
        val allowlist = config.optString("allowlist", ".*")
        val keywordRegex = config.optString("keywordRegex", ".*")

        val sender = context.getString("sender", "").ifEmpty {
            context.getString("header", "").ifEmpty {
                context.getString("sms_header", "").ifEmpty {
                    context.getString("address", "")
                }
            }
        }

        val body = context.getString("body", "").ifEmpty {
            context.getString("sms_body", "").ifEmpty {
                context.getString("message", "").ifEmpty {
                    context.getString("text", "")
                }
            }
        }

        val allowlistPatterns = allowlist.split(",", "\n").map { it.trim() }.filter { it.isNotEmpty() }
        val senderMatches = allowlistPatterns.isEmpty() || allowlistPatterns.any { pattern ->
            try {
                val regex = Pattern.compile(pattern.replace("*", ".*"), Pattern.CASE_INSENSITIVE)
                regex.matcher(sender).find()
            } catch (_: Exception) { false }
        }

        if (!senderMatches) {
            return NodeExecutionResult.Ignored("Sender '$sender' did not match allowlist filters")
        }

        if (keywordRegex.isNotBlank() && keywordRegex != ".*") {
            try {
                val regex = Pattern.compile(keywordRegex, Pattern.CASE_INSENSITIVE)
                if (!regex.matcher(body).find()) {
                    return NodeExecutionResult.Ignored("Message body did not match keyword regex '$keywordRegex'")
                }
            } catch (e: Exception) {
                return NodeExecutionResult.Failed("Invalid keyword regex: ${e.message}")
            }
        }

        // Normalize SMS Header & Body variables in context
        context.set("sender", sender)
        context.set("header", sender)
        context.set("sms_header", sender)
        context.set("address", sender)
        context.set("body", body)
        context.set("sms_body", body)
        context.set("message", body)
        context.set("text", body)

        return NodeExecutionResult.Success(logMessage = "SMS trigger matched sender: $sender")
    }
}
