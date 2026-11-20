package dev.zophop.domain.engine.executors

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.telephony.SmsManager
import androidx.core.app.NotificationCompat
import dev.zophop.domain.engine.ExecutionContext
import dev.zophop.domain.model.NodeType
import dev.zophop.domain.model.WorkflowNode
import org.json.JSONObject
import java.util.regex.Pattern

/**
 * Strategy executor for [NodeType.SMS_TRIGGER].
 */
class SmsTriggerExecutor : NodeExecutor {
    override suspend fun execute(node: WorkflowNode, context: ExecutionContext, appContext: Context?): NodeExecutionResult {
        if (appContext != null) {
            val hasReceiveSms = androidx.core.content.ContextCompat.checkSelfPermission(
                appContext,
                android.Manifest.permission.RECEIVE_SMS
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
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
            return NodeExecutionResult.Failed("Sender '$sender' did not match allowlist")
        }

        if (keywordRegex.isNotBlank() && keywordRegex != ".*") {
            try {
                val regex = Pattern.compile(keywordRegex, Pattern.CASE_INSENSITIVE)
                if (!regex.matcher(body).find()) {
                    return NodeExecutionResult.Failed("Message body did not match keyword regex '$keywordRegex'")
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

/**
 * Strategy executor for [NodeType.MANUAL_TRIGGER].
 */
class ManualTriggerExecutor : NodeExecutor {
    override suspend fun execute(node: WorkflowNode, context: ExecutionContext, appContext: Context?): NodeExecutionResult {
        return NodeExecutionResult.Success(logMessage = "Manual trigger executed on demand")
    }
}

/**
 * Strategy executor for [NodeType.NOTIFICATION_TRIGGER].
 */
class NotificationTriggerExecutor : NodeExecutor {
    override suspend fun execute(node: WorkflowNode, context: ExecutionContext, appContext: Context?): NodeExecutionResult {
        if (appContext != null) {
            val pkg = appContext.packageName
            val flat = android.provider.Settings.Secure.getString(appContext.contentResolver, "enabled_notification_listeners")
            val hasPermission = flat != null && flat.contains(pkg)
            if (!hasPermission) {
                return NodeExecutionResult.Failed("Missing Notification Listener Service permission (Notification Access)")
            }
        }

        val config = try { JSONObject(node.configJson) } catch (_: Exception) { JSONObject() }
        val packageFilter = config.optString("packageFilter", ".*")
        val textRegex = config.optString("textRegex", ".*")

        val packageName = context.getString("package_name", "")
        val title = context.getString("title", "")
        val text = context.getString("text", "")

        if (packageFilter.isNotBlank() && packageFilter != ".*") {
            val filters = packageFilter.split(",", "\n").map { it.trim() }.filter { it.isNotEmpty() }
            val matches = filters.isEmpty() || filters.any { filter ->
                try {
                    val regex = Pattern.compile(filter.replace("*", ".*"), Pattern.CASE_INSENSITIVE)
                    regex.matcher(packageName).find()
                } catch (_: Exception) { false }
            }
            if (!matches) {
                return NodeExecutionResult.Failed("Package '$packageName' did not match filter '$packageFilter'")
            }
        }

        if (textRegex.isNotBlank() && textRegex != ".*") {
            val content = "$title $text"
            val matches = try {
                val regex = Pattern.compile(textRegex, Pattern.CASE_INSENSITIVE)
                regex.matcher(content).find()
            } catch (_: Exception) { false }
            if (!matches) {
                return NodeExecutionResult.Failed("Notification content did not match text regex '$textRegex'")
            }
        }

        return NodeExecutionResult.Success(logMessage = "Notification trigger matched app: $packageName")
    }
}

/**
 * Strategy executor for [NodeType.REGEX_PARSER].
 */
class RegexParserExecutor : NodeExecutor {
    override suspend fun execute(node: WorkflowNode, context: ExecutionContext, appContext: Context?): NodeExecutionResult {
        val config = try { JSONObject(node.configJson) } catch (_: Exception) { JSONObject() }
        val regexStr = config.optString("regex", "\\b\\d{4,8}\\b")
        val outputKey = config.optString("outputKey", "code")

        val inputText = context.getString("body", "").ifEmpty { context.getString("text", "") }

        if (inputText.isBlank()) {
            return NodeExecutionResult.Failed("Input text is empty for regex parsing")
        }

        try {
            val pattern = Pattern.compile(regexStr, Pattern.CASE_INSENSITIVE)
            val matcher = pattern.matcher(inputText)
            if (matcher.find()) {
                val matchedVal = if (matcher.groupCount() >= 1) matcher.group(1) ?: matcher.group(0) else matcher.group(0)
                if (matchedVal != null) {
                    context.set(outputKey, matchedVal)
                    context.set("code", matchedVal)
                    context.set("otp", matchedVal)
                    return NodeExecutionResult.Success(logMessage = "Extracted '$outputKey' = '$matchedVal'")
                }
            }
            return NodeExecutionResult.Failed("No match found for regex '$regexStr'")
        } catch (e: Exception) {
            return NodeExecutionResult.Failed("Regex execution failed: ${e.message}")
        }
    }
}

/**
 * Strategy executor for [NodeType.TEMPLATE_FORMATTER].
 */
class TemplateFormatterExecutor : NodeExecutor {
    override suspend fun execute(node: WorkflowNode, context: ExecutionContext, appContext: Context?): NodeExecutionResult {
        val config = try { JSONObject(node.configJson) } catch (_: Exception) { JSONObject() }
        val template = config.optString("template", "\${body}")

        val formatted = context.interpolate(template)
        context.set("formatted_payload", formatted)
        context.set("payload", formatted)

        return NodeExecutionResult.Success(logMessage = "Formatted payload created (${formatted.length} chars)")
    }
}

/**
 * Strategy executor for [NodeType.SEND_NOTIFICATION_ACTION].
 */
class SendNotificationExecutor : NodeExecutor {
    override suspend fun execute(node: WorkflowNode, context: ExecutionContext, appContext: Context?): NodeExecutionResult {
        val ctx = appContext ?: return NodeExecutionResult.Failed("Context unavailable for local notification")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val hasPostNotif = androidx.core.content.ContextCompat.checkSelfPermission(
                ctx,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            if (!hasPostNotif) {
                return NodeExecutionResult.Failed("Missing Post Notifications Permission (POST_NOTIFICATIONS)")
            }
        }

        val config = try { JSONObject(node.configJson) } catch (_: Exception) { JSONObject() }
        val titleTemplate = config.optString("title", "ZopHop Forwarder")
        val messageTemplate = config.optString("message", "\${body}").ifEmpty {
            config.optString("template", "\${body}")
        }

        val title = context.interpolate(titleTemplate).ifBlank { "ZopHop Notification" }
        val text = context.interpolate(messageTemplate).ifBlank { context.getString("body", "Notification alert") }

        try {
            val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channelId = "zophop_workflow_channel_v2"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    channelId,
                    "Workflow Alerts",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Notifications posted by ZopHop Workflows"
                    enableVibration(true)
                }
                nm.createNotificationChannel(channel)
            }

            val notification = NotificationCompat.Builder(ctx, channelId)
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .setAutoCancel(true)
                .build()

            nm.notify(System.currentTimeMillis().toInt(), notification)
            return NodeExecutionResult.Success(logMessage = "Posted system notification: '$title'")
        } catch (e: Exception) {
            return NodeExecutionResult.Failed("Notification posting failed: ${e.message}")
        }
    }
}

/**
 * Strategy executor for [NodeType.SEND_SMS_ACTION].
 */
class SendSmsExecutor : NodeExecutor {
    override suspend fun execute(node: WorkflowNode, context: ExecutionContext, appContext: Context?): NodeExecutionResult {
        if (appContext != null) {
            val hasSendSms = androidx.core.content.ContextCompat.checkSelfPermission(
                appContext,
                android.Manifest.permission.SEND_SMS
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
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
