package dev.zophop.domain.engine.executors

import android.content.Context
import dev.zophop.data.CredentialRepository
import dev.zophop.domain.engine.ExecutionContext
import dev.zophop.domain.model.NodeType
import dev.zophop.domain.model.WorkflowNode
import org.json.JSONObject

/**
 * Preset action executor for [NodeType.NTFY_ACTION].
 * Formats ntfy HTTP push requests with title, priority, tags, and credential resolution.
 */
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

/**
 * Preset action executor for [NodeType.GOTIFY_ACTION].
 * Formats Gotify HTTP push messages with title, priority, and app tokens.
 */
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
        val title = context.interpolate(config.optString("title", "ZopHop Notification")).trim()
        val priority = config.optInt("priority", 5)
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
