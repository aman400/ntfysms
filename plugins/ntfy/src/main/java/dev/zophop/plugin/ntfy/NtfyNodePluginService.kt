package dev.zophop.plugin.ntfy

import android.app.Service
import android.content.Intent
import android.os.IBinder
import dev.zophop.plugin.api.ConfigFieldSpec
import dev.zophop.plugin.api.CredentialSpec
import dev.zophop.plugin.api.INodePluginService
import dev.zophop.plugin.api.NodeCategory
import dev.zophop.plugin.api.NodeExecutor
import dev.zophop.plugin.api.NodePluginManifest
import dev.zophop.plugin.api.NodePluginModule
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object NtfyPluginModule : NodePluginModule {
    override val moduleId: String = "ntfy-plugin-module"

    override fun validateConfig(typeId: String, configJson: String): String? {
        val config = try { JSONObject(configJson) } catch (_: Exception) { JSONObject() }
        val serverUrl = config.optString("serverUrl", "").trim()
        val topic = config.optString("topic", "").trim()
        val credentialId = config.optString("credentialId", "").trim()
        if (serverUrl.isBlank() && credentialId.isBlank()) return "Server URL or an attached credential must be provided."
        if (serverUrl.isNotBlank() && !serverUrl.startsWith("http://", ignoreCase = true) && !serverUrl.startsWith("https://", ignoreCase = true)) return "Server URL must start with http:// or https://."
        if (topic.isBlank()) return "Topic name cannot be blank."
        return null
    }

    override fun getManifests(): List<NodePluginManifest> {
        return listOf(
            NodePluginManifest(
                typeId = "dev.zophop.plugin.ntfy.NTFY_ACTION",
                displayName = "ntfy Push Action",
                description = "Sends payload to a ntfy.sh or self-hosted server",
                category = NodeCategory.ACTION,
                requiredCredentialType = "NTFY_AUTH",
                credentialSpec = CredentialSpec(
                    typeId = "NTFY_AUTH",
                    displayName = "ntfy Server & Token",
                    description = "ntfy server URL, topic name, and access token credentials",
                    fields = listOf(
                        ConfigFieldSpec.TextField(
                            key = "serverUrl",
                            label = "ntfy Server URL",
                            placeholder = "https://ntfy.sh"
                        ),
                        ConfigFieldSpec.TextField(
                            key = "topic",
                            label = "ntfy Topic Name",
                            placeholder = "my-topic"
                        ),
                        ConfigFieldSpec.TextField(
                            key = "token",
                            label = "ntfy Access Token (Optional)"
                        )
                    )
                ),
                configSchema = listOf(
                    ConfigFieldSpec.TextField(
                        key = "serverUrl",
                        label = "Server URL (Leave blank to use credential default)",
                        placeholder = "https://ntfy.sh"
                    ),
                    ConfigFieldSpec.TextField(
                        key = "topic",
                        label = "Topic"
                    ),
                    ConfigFieldSpec.TextField(
                        key = "title",
                        label = "Title"
                    ),
                    ConfigFieldSpec.DropdownField(
                        key = "priority",
                        label = "Priority (1-5)",
                        options = listOf(
                            "5" to "Max / Urgent (5)",
                            "4" to "High (4)",
                            "3" to "Default / Normal (3)",
                            "2" to "Low (2)",
                            "1" to "Min / Silent (1)"
                        )
                    ),
                    ConfigFieldSpec.TextField(
                        key = "tags",
                        label = "Tags (comma separated)"
                    ),
                    ConfigFieldSpec.CodeTemplateField(
                        key = "template",
                        label = "Message Template"
                    )
                )
            )
        )
    }

    override fun getCredentialSpecs(): List<CredentialSpec> {
        return getManifests().mapNotNull { it.credentialSpec }
    }

    override fun createExecutor(
        typeId: String,
        okHttpClient: OkHttpClient,
        credentialRepository: Any?
    ): NodeExecutor? {
        return when {
            typeId == "NTFY_ACTION" || typeId.endsWith(".NTFY_ACTION") -> NtfyActionExecutor(okHttpClient, credentialRepository)
            else -> null
        }
    }
}

class NtfyNodePluginService : Service() {

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private val binder = object : INodePluginService.Stub() {
        override fun getPluginApiVersion(): Int = 1

        override fun getManifests(): List<NodePluginManifest> = NtfyPluginModule.getManifests()

        override fun executeNode(nodeId: String, configJson: String, inputContextJson: String): String {
            val config = try { JSONObject(configJson) } catch (_: Exception) { JSONObject() }
            val inputCtx = try { JSONObject(inputContextJson) } catch (_: Exception) { JSONObject() }

            val credServerUrl = inputCtx.optString("cred_serverUrl", "")
            val credToken = inputCtx.optString("cred_token", "")
            val credTopic = inputCtx.optString("cred_topic", "")

            val serverUrl = config.optString("serverUrl", "").ifBlank { credServerUrl }.trim().trimEnd('/')
            if (serverUrl.isBlank()) return fail("ntfy serverUrl is empty")

            val topic = config.optString("topic", "").ifBlank { credTopic }.trim()
            val token = config.optString("token", "").ifBlank { credToken }.trim()
            val title = config.optString("title", "ZopHop Notification").trim()
            val priority = config.optString("priority", "3")
            val tags = config.optString("tags", "").trim()

            // Try to extract dynamically replaced template passed by engine or fallback to context body
            val payload = config.optString("template", inputCtx.optString("formatted_payload", inputCtx.optString("body", "")))

            val targetUrl = "$serverUrl/$topic"

            try {
                val requestBuilder = Request.Builder().url(targetUrl).post(payload.toRequestBody("text/plain; charset=utf-8".toMediaType()))

                if (title.isNotBlank()) requestBuilder.addHeader("Title", title)
                if (priority.isNotBlank()) requestBuilder.addHeader("Priority", priority)
                if (tags.isNotBlank()) requestBuilder.addHeader("Tags", tags)
                if (token.isNotBlank()) requestBuilder.addHeader("Authorization", "Bearer $token")

                val response = okHttpClient.newCall(requestBuilder.build()).execute()
                response.use { resp ->
                    val respBody = resp.body?.string() ?: ""
                    if (resp.isSuccessful) {
                        return success("Delivered to ntfy topic '$topic'")
                    } else {
                        return fail("ntfy request failed: HTTP ${resp.code} - $respBody")
                    }
                }
            } catch (e: Exception) {
                return fail("ntfy dispatch failed: ${e.message}")
            }
        }

        private fun success(msg: String) = JSONObject().apply {
            put("isSuccess", true)
            put("outputPort", "main")
            put("logMessage", msg)
        }.toString()

        private fun fail(reason: String) = JSONObject().apply {
            put("isSuccess", false)
            put("errorReason", reason)
        }.toString()
    }

    override fun onBind(intent: Intent?): IBinder = binder
}
