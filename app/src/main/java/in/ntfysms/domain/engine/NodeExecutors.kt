package `in`.ntfysms.domain.engine

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.telephony.SmsManager
import android.util.Base64
import androidx.core.app.NotificationCompat
import `in`.ntfysms.R
import `in`.ntfysms.data.CredentialRepository
import `in`.ntfysms.domain.model.Credential
import `in`.ntfysms.domain.model.CredentialType
import `in`.ntfysms.domain.model.NodeType
import `in`.ntfysms.domain.model.WorkflowNode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

sealed class NodeExecutionResult {
    data class Success(
        val outputPort: String = "main",
        val logMessage: String = "Node executed successfully"
    ) : NodeExecutionResult()

    data class Failed(
        val reason: String
    ) : NodeExecutionResult()
}

interface NodeExecutor {
    suspend fun execute(
        node: WorkflowNode,
        context: ExecutionContext,
        appContext: Context?
    ): NodeExecutionResult
}

class NodeExecutorFactory(
    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build(),
    private val credentialRepository: CredentialRepository? = null
) {

    fun getExecutor(type: NodeType): NodeExecutor {
        return when (type) {
            NodeType.SMS_TRIGGER -> SmsTriggerExecutor()
            NodeType.NOTIFICATION_TRIGGER -> NotificationTriggerExecutor()
            NodeType.MANUAL_TRIGGER -> ManualTriggerExecutor()
            NodeType.PARSE_NOTIFICATION, NodeType.REGEX_PARSER -> RegexParserExecutor()
            NodeType.JSON_PARSER -> JsonParserExecutor()
            NodeType.TEMPLATE_FORMATTER -> TemplateFormatterExecutor()
            NodeType.CONDITION -> ConditionExecutor()
            NodeType.NTFY_ACTION -> NtfyActionExecutor(HttpActionExecutor(okHttpClient, credentialRepository), credentialRepository)
            NodeType.GOTIFY_ACTION -> GotifyActionExecutor(HttpActionExecutor(okHttpClient, credentialRepository), credentialRepository)
            NodeType.HTTP_ACTION -> HttpActionExecutor(okHttpClient, credentialRepository)
            NodeType.SEND_NOTIFICATION_ACTION -> SendNotificationExecutor()
            NodeType.SEND_SMS_ACTION -> SendSmsExecutor()
        }
    }
}

// 1. SMS Trigger
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

        val sender = context.getString("sender", "")
        val body = context.getString("body", "")

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

        return NodeExecutionResult.Success(logMessage = "SMS trigger matched sender: $sender")
    }
}

// 3. Manual Trigger
class ManualTriggerExecutor : NodeExecutor {
    override suspend fun execute(node: WorkflowNode, context: ExecutionContext, appContext: Context?): NodeExecutionResult {
        return NodeExecutionResult.Success(logMessage = "Manual trigger executed on demand")
    }
}

// 2. Notification Trigger
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
            val matches = try {
                val regex = Pattern.compile(packageFilter.replace("*", ".*"), Pattern.CASE_INSENSITIVE)
                regex.matcher(packageName).find()
            } catch (_: Exception) { false }
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

// 3. Regex / Parse Executor
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

// 4. JSON Parser Executor
class JsonParserExecutor : NodeExecutor {
    override suspend fun execute(node: WorkflowNode, context: ExecutionContext, appContext: Context?): NodeExecutionResult {
        val config = try { JSONObject(node.configJson) } catch (_: Exception) { JSONObject() }
        val jsonKey = config.optString("jsonKey", "code")
        val outputKey = config.optString("outputKey", jsonKey)

        val rawInput = context.getString("body", "").ifEmpty { context.getString("http_response", "") }

        return try {
            val json = JSONObject(rawInput)
            if (json.has(jsonKey)) {
                val value = json.get(jsonKey)
                context.set(outputKey, value)
                NodeExecutionResult.Success(logMessage = "Parsed JSON key '$jsonKey' = '$value'")
            } else {
                NodeExecutionResult.Failed("JSON does not contain key '$jsonKey'")
            }
        } catch (e: Exception) {
            NodeExecutionResult.Failed("JSON parsing error: ${e.message}")
        }
    }
}

// 5. Template Formatter Executor
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

// 6. Condition Executor
class ConditionExecutor : NodeExecutor {
    override suspend fun execute(node: WorkflowNode, context: ExecutionContext, appContext: Context?): NodeExecutionResult {
        val config = try { JSONObject(node.configJson) } catch (_: Exception) { JSONObject() }
        val variableName = config.optString("variable", "body")
        val operator = config.optString("operator", "NOT_EMPTY")
        val targetValue = config.optString("value", "")

        val actualValue = context.getString(variableName, "")

        val conditionMet = when (operator.uppercase()) {
            "EQUALS" -> actualValue.equals(targetValue, ignoreCase = true)
            "CONTAINS" -> actualValue.contains(targetValue, ignoreCase = true)
            "REGEX_MATCH" -> try { Pattern.compile(targetValue).matcher(actualValue).find() } catch (_: Exception) { false }
            "NOT_EMPTY" -> actualValue.isNotBlank()
            else -> actualValue.isNotBlank()
        }

        val port = if (conditionMet) "true" else "false"
        return NodeExecutionResult.Success(outputPort = port, logMessage = "Condition evaluated to $conditionMet")
    }
}

// 7. HTTP Action Executor (Extensive REST Webhook Node Engine)
class HttpActionExecutor(
    private val okHttpClient: OkHttpClient,
    private val credentialRepository: CredentialRepository? = null
) : NodeExecutor {

    override suspend fun execute(
        node: WorkflowNode,
        context: ExecutionContext,
        appContext: Context?
    ): NodeExecutionResult = withContext(Dispatchers.IO) {
        val config = try { JSONObject(node.configJson) } catch (_: Exception) { JSONObject() }

        val rawUrl = config.optString("url", "")
        val method = config.optString("method", "GET").uppercase()
        val queryParams = config.optString("queryParams", "")
        val authType = config.optString("authType", "NONE").uppercase()
        val bearerToken = config.optString("bearerToken", "")
        val basicUser = config.optString("basicUser", "")
        val basicPassword = config.optString("basicPassword", "")
        val credentialId = config.optString("credentialId", "")

        val headers = config.optString("headers", "")
        val bodyType = config.optString("bodyType", "JSON").uppercase()
        val bodyTemplate = config.optString("bodyTemplate", "")
        val expectedStatus = config.optInt("expectedStatus", 200)

        var targetUrl = context.interpolate(rawUrl).trim()
        if (targetUrl.isBlank()) {
            return@withContext NodeExecutionResult.Failed("HTTP URL is empty")
        }

        // 1. Query parameters appending
        if (queryParams.isNotBlank()) {
            val interpolatedQuery = context.interpolate(queryParams)
            targetUrl = if (targetUrl.contains("?")) "$targetUrl&$interpolatedQuery" else "$targetUrl?$interpolatedQuery"
        }

        try {
            val requestBuilder = Request.Builder().url(targetUrl)

            // 2. Body construction
            val interpolatedBody = context.interpolate(bodyTemplate)
            val requestBody: RequestBody? = when (bodyType) {
                "NONE" -> null
                "FORM_DATA" -> {
                    val formBuilder = FormBody.Builder()
                    interpolatedBody.split("\n", "&").forEach { pair ->
                        val parts = pair.split("=", limit = 2)
                        if (parts.size == 2) {
                            formBuilder.add(parts[0].trim(), parts[1].trim())
                        }
                    }
                    formBuilder.build()
                }
                "RAW" -> interpolatedBody.toRequestBody("text/plain; charset=utf-8".toMediaType())
                else -> interpolatedBody.toRequestBody("application/json; charset=utf-8".toMediaType()) // JSON
            }

            when (method) {
                "POST" -> requestBuilder.post(requestBody ?: ByteArray(0).toRequestBody(null))
                "PUT" -> requestBuilder.put(requestBody ?: ByteArray(0).toRequestBody(null))
                "PATCH" -> requestBuilder.patch(requestBody ?: ByteArray(0).toRequestBody(null))
                "DELETE" -> if (requestBody != null) requestBuilder.delete(requestBody) else requestBuilder.delete()
                "HEAD" -> requestBuilder.head()
                else -> requestBuilder.get()
            }

            // 3. Custom Headers
            if (headers.isNotBlank()) {
                context.interpolate(headers).split("\n", ",").forEach { line ->
                    val parts = line.split(":", limit = 2)
                    if (parts.size == 2) {
                        requestBuilder.addHeader(parts[0].trim(), parts[1].trim())
                    }
                }
            }

            // 4. Authentication & Credential Store Resolution
            val resolvedCred: Credential? = if (credentialId.isNotBlank() && credentialRepository != null) {
                credentialRepository.getCredentialById(credentialId)
            } else null

            if (resolvedCred != null) {
                applyCredentialToRequest(requestBuilder, resolvedCred, context)
            } else {
                when (authType) {
                    "BEARER" -> {
                        val token = context.interpolate(bearerToken).trim()
                        if (token.isNotBlank()) {
                            requestBuilder.addHeader("Authorization", "Bearer $token")
                        }
                    }
                    "BASIC" -> {
                        val user = context.interpolate(basicUser).trim()
                        val pass = context.interpolate(basicPassword).trim()
                        val credentials = "$user:$pass"
                        val authHeader = "Basic " + Base64.encodeToString(credentials.toByteArray(), Base64.NO_WRAP)
                        requestBuilder.addHeader("Authorization", authHeader)
                    }
                }
            }

            val response = okHttpClient.newCall(requestBuilder.build()).execute()
            response.use { resp ->
                val responseBody = resp.body?.string() ?: ""
                context.set("http_status", resp.code)
                context.set("http_response", responseBody)

                val responseHeaders = resp.headers.toString().trim()
                val detailedLog = buildString {
                    appendLine("HTTP $method $targetUrl")
                    appendLine("Status: ${resp.code} ${resp.message}")
                    if (responseHeaders.isNotBlank()) {
                        appendLine("--- Headers ---")
                        appendLine(responseHeaders)
                    }
                    if (responseBody.isNotBlank()) {
                        appendLine("--- Response Body ---")
                        appendLine(responseBody)
                    }
                }

                if (resp.code == expectedStatus || (expectedStatus == 200 && resp.isSuccessful)) {
                    NodeExecutionResult.Success(logMessage = detailedLog)
                } else {
                    NodeExecutionResult.Failed("HTTP request failed with status ${resp.code}\n\n$detailedLog")
                }
            }
        } catch (e: Exception) {
            NodeExecutionResult.Failed("HTTP request exception: ${e.message}")
        }
    }

    private fun applyCredentialToRequest(
        builder: Request.Builder,
        credential: Credential,
        context: ExecutionContext
    ) {
        val data = credential.dataMap
        when (credential.type) {
            CredentialType.BEARER_TOKEN -> {
                val token = context.interpolate(data["token"] ?: "").trim()
                if (token.isNotBlank()) {
                    builder.addHeader("Authorization", "Bearer $token")
                }
            }
            CredentialType.BASIC_AUTH -> {
                val user = context.interpolate(data["username"] ?: "").trim()
                val pass = context.interpolate(data["password"] ?: "").trim()
                val authHeader = "Basic " + Base64.encodeToString("$user:$pass".toByteArray(), Base64.NO_WRAP)
                builder.addHeader("Authorization", authHeader)
            }
            CredentialType.HEADER_SET -> {
                val headers = data["headers"] ?: ""
                context.interpolate(headers).split("\n", ",").forEach { line ->
                    val parts = line.split(":", limit = 2)
                    if (parts.size == 2) {
                        builder.addHeader(parts[0].trim(), parts[1].trim())
                    }
                }
            }
            CredentialType.NTFY_AUTH -> {
                val token = context.interpolate(data["token"] ?: "").trim()
                if (token.isNotBlank()) {
                    builder.addHeader("Authorization", "Bearer $token")
                }
            }
            CredentialType.GOTIFY_AUTH -> {
                val appToken = context.interpolate(data["appToken"] ?: "").trim()
                if (appToken.isNotBlank()) {
                    builder.addHeader("X-Gotify-Key", appToken)
                }
            }
        }
    }
}

// 8. Ntfy Action Executor (Built as HTTP Extension Preset)
class NtfyActionExecutor(
    private val httpExecutor: HttpActionExecutor,
    private val credentialRepository: CredentialRepository? = null
) : NodeExecutor {

    override suspend fun execute(node: WorkflowNode, context: ExecutionContext, appContext: Context?): NodeExecutionResult {
        val config = try { JSONObject(node.configJson) } catch (_: Exception) { JSONObject() }
        val credentialId = config.optString("credentialId", "")
        val resolvedCred = if (credentialId.isNotBlank() && credentialRepository != null) {
            credentialRepository.getCredentialById(credentialId)
        } else null

        val credServerUrl = resolvedCred?.dataMap?.get("serverUrl") ?: ""
        val credTopic = resolvedCred?.dataMap?.get("topic") ?: ""
        val credToken = resolvedCred?.dataMap?.get("token") ?: ""

        val serverUrl = context.interpolate(config.optString("serverUrl", "").ifBlank { credServerUrl }).trim().trimEnd('/')
        val topic = context.interpolate(config.optString("topic", "").ifBlank { credTopic }).trim()
        val token = context.interpolate(config.optString("token", "").ifBlank { credToken }).trim()
        val title = context.interpolate(config.optString("title", "NtfySMS Notification")).trim()
        val priority = config.optString("priority", "3")
        val tags = config.optString("tags", "otp,sms")

        val payload = context.getString("formatted_payload", "").ifEmpty {
            context.getString("payload", "").ifEmpty {
                context.getString("body", "")
            }
        }

        if (serverUrl.isBlank() || topic.isBlank()) {
            return NodeExecutionResult.Failed("Ntfy serverUrl or topic is empty")
        }

        val fullUrl = "$serverUrl/$topic"
        val headers = StringBuilder().apply {
            append("Title: ").append(title).append("\n")
            append("Priority: ").append(priority).append("\n")
            append("Tags: ").append(tags)
        }.toString()

        val httpConfig = JSONObject().apply {
            put("url", fullUrl)
            put("method", "POST")
            put("headers", headers)
            put("bodyType", "RAW")
            put("bodyTemplate", payload)
            put("authType", if (token.isNotBlank()) "BEARER" else "NONE")
            put("bearerToken", token)
            put("credentialId", credentialId)
            put("expectedStatus", 200)
        }

        val ntfyNode = node.copy(configJson = httpConfig.toString())
        return httpExecutor.execute(ntfyNode, context, appContext)
    }
}

// 9. Gotify Action Executor (Built as HTTP Extension Preset)
class GotifyActionExecutor(
    private val httpExecutor: HttpActionExecutor,
    private val credentialRepository: CredentialRepository? = null
) : NodeExecutor {

    override suspend fun execute(node: WorkflowNode, context: ExecutionContext, appContext: Context?): NodeExecutionResult {
        val config = try { JSONObject(node.configJson) } catch (_: Exception) { JSONObject() }
        val credentialId = config.optString("credentialId", "")
        val resolvedCred = if (credentialId.isNotBlank() && credentialRepository != null) {
            credentialRepository.getCredentialById(credentialId)
        } else null

        val credServerUrl = resolvedCred?.dataMap?.get("serverUrl") ?: ""
        val credAppToken = resolvedCred?.dataMap?.get("appToken") ?: ""

        val serverUrl = context.interpolate(config.optString("serverUrl", "").ifBlank { credServerUrl }).trim().trimEnd('/')
        val appToken = context.interpolate(config.optString("appToken", "").ifBlank { credAppToken }).trim()
        val title = context.interpolate(config.optString("title", "NtfySMS Notification")).trim()
        val priority = config.optInt("priority", 5)

        val payload = context.getString("formatted_payload", "").ifEmpty {
            context.getString("payload", "").ifEmpty {
                context.getString("body", "")
            }
        }

        if (serverUrl.isBlank()) {
            return NodeExecutionResult.Failed("Gotify serverUrl is empty")
        }

        val targetUrl = "$serverUrl/message" + (if (appToken.isNotBlank()) "?token=$appToken" else "")
        val jsonBody = JSONObject().apply {
            put("title", title)
            put("message", payload)
            put("priority", priority)
        }

        val httpConfig = JSONObject().apply {
            put("url", targetUrl)
            put("method", "POST")
            put("bodyType", "JSON")
            put("bodyTemplate", jsonBody.toString())
            put("credentialId", credentialId)
            put("expectedStatus", 200)
        }

        val gotifyNode = node.copy(configJson = httpConfig.toString())
        return httpExecutor.execute(gotifyNode, context, appContext)
    }
}

// 10. Local Send Notification Executor
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
        val titleTemplate = config.optString("title", "NtfySMS Forwarder")
        val messageTemplate = config.optString("message", "\${body}").ifEmpty {
            config.optString("template", "\${body}")
        }

        val title = context.interpolate(titleTemplate).ifBlank { "NtfySMS Notification" }
        val text = context.interpolate(messageTemplate).ifBlank { context.getString("body", "Notification alert") }

        try {
            val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channelId = "ntfy_sms_workflow_channel_v2"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    channelId,
                    "Workflow Alerts",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Notifications posted by NtfySMS Workflows"
                    enableVibration(true)
                }
                nm.createNotificationChannel(channel)
            }

            val notification = NotificationCompat.Builder(ctx, channelId)
                .setSmallIcon(R.drawable.ic_notification)
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

// 11. Send SMS Executor
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
