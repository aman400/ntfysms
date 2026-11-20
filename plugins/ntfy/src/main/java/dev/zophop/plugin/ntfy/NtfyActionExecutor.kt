package dev.zophop.plugin.ntfy

import android.content.Context
import dev.zophop.data.CredentialRepository
import dev.zophop.domain.model.WorkflowNode
import dev.zophop.plugin.api.ExecutionContext
import dev.zophop.plugin.api.NodeExecutionResult
import dev.zophop.plugin.api.NodeExecutor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

/**
 * Preset action executor for [NodeType.NTFY_ACTION].
 * Formats ntfy HTTP push requests with title, priority, tags, and credential resolution.
 */
class NtfyActionExecutor(
    private val okHttpClient: OkHttpClient,
    private val credentialRepository: Any? = null
) : NodeExecutor {

    override suspend fun execute(node: WorkflowNode, context: ExecutionContext, appContext: Context?): NodeExecutionResult {
        val credRepo = credentialRepository as? CredentialRepository
        val config = try { JSONObject(node.configJson) } catch (_: Exception) { JSONObject() }
        val credentialId = config.optString("credentialId", "")
        val resolvedCred = if (credentialId.isNotBlank() && credRepo != null) {
            credRepo.getCredentialById(credentialId)
        } else null

        val credServerUrl = resolvedCred?.dataMap?.get("serverUrl") ?: ""
        val credTopic = resolvedCred?.dataMap?.get("topic") ?: ""
        val credToken = resolvedCred?.dataMap?.get("token") ?: ""

        val serverUrl = context.interpolate(config.optString("serverUrl", "").ifBlank { credServerUrl }).trim().trimEnd('/')
        val topic = context.interpolate(config.optString("topic", "").ifBlank { credTopic }).trim()
        val token = context.interpolate(config.optString("token", "").ifBlank { credToken }).trim()
        val title = context.interpolate(config.optString("title", "ZopHop Notification")).trim()
        val priority = config.optString("priority", "3")
        val tags = config.optString("tags", "otp,sms")
        val customTemplate = config.optString("template", "")

        val payload = if (customTemplate.isNotBlank()) {
            context.interpolate(customTemplate)
        } else {
            context.getString("formatted_payload", "").ifEmpty {
                context.getString("payload", "").ifEmpty {
                    context.getString("body", "")
                }
            }
        }

        if (serverUrl.isBlank() || topic.isBlank()) {
            return NodeExecutionResult.Failed("Ntfy serverUrl or topic is empty")
        }

        val targetUrl = "$serverUrl/$topic"

        try {
            val requestBuilder = Request.Builder()
                .url(targetUrl)
                .post(payload.toRequestBody("text/plain; charset=utf-8".toMediaType()))

            if (title.isNotBlank()) requestBuilder.addHeader("Title", title)
            if (priority.isNotBlank()) requestBuilder.addHeader("Priority", priority)
            if (tags.isNotBlank()) requestBuilder.addHeader("Tags", tags)
            if (token.isNotBlank()) requestBuilder.addHeader("Authorization", "Bearer $token")

            val response = okHttpClient.newCall(requestBuilder.build()).execute()
            response.use { resp ->
                val respBody = resp.body?.string() ?: ""
                if (resp.isSuccessful) {
                    val detailedLog = "Delivered to ntfy topic '$topic' (HTTP ${resp.code})\nURL: $targetUrl\nTitle: $title\nBody: $payload"
                    return NodeExecutionResult.Success(outputPort = "main", logMessage = detailedLog)
                } else {
                    return NodeExecutionResult.Failed("ntfy request failed: HTTP ${resp.code} - $respBody")
                }
            }
        } catch (e: Exception) {
            return NodeExecutionResult.Failed("ntfy dispatch failed: ${e.message}")
        }
    }
}
