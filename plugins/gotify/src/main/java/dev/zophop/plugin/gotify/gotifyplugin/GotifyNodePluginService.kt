package dev.zophop.gotifyplugin

import android.app.Service
import android.content.Intent
import android.os.IBinder
import dev.zophop.plugin.api.ConfigFieldSpec
import dev.zophop.plugin.api.CredentialSpec
import dev.zophop.plugin.api.INodePluginService
import dev.zophop.plugin.api.NodeCategory
import dev.zophop.plugin.api.NodePluginManifest
import dev.zophop.plugin.api.NodePluginModule
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object GotifyPluginModule : NodePluginModule {
    override val moduleId: String = "gotify-plugin-module"

    override fun getManifests(): List<NodePluginManifest> {
        return listOf(
            NodePluginManifest(
                typeId = "dev.zophop.gotifyplugin.GOTIFY_ACTION",
                displayName = "Gotify Push Action",
                description = "Sends payload to a Gotify push server",
                category = NodeCategory.ACTION,
                requiredCredentialType = "GOTIFY_AUTH",
                credentialSpec = CredentialSpec(
                    typeId = "GOTIFY_AUTH",
                    displayName = "Gotify Server & App Token",
                    description = "Gotify server URL and application token credentials",
                    fields = listOf(
                        ConfigFieldSpec.TextField(
                            key = "serverUrl",
                            label = "Gotify Server URL",
                            placeholder = "https://gotify.example.com"
                        ),
                        ConfigFieldSpec.TextField(
                            key = "appToken",
                            label = "Gotify Application Token"
                        )
                    )
                ),
                configSchema = listOf(
                    ConfigFieldSpec.TextField(
                        key = "serverUrl",
                        label = "Gotify Server URL (Leave blank for credential default)",
                        placeholder = "https://gotify.example.com"
                    ),
                    ConfigFieldSpec.TextField(
                        key = "title",
                        label = "Notification Title"
                    ),
                    ConfigFieldSpec.DropdownField(
                        key = "priority",
                        label = "Priority (1-10)",
                        options = listOf(
                            "10" to "Max / Urgent (10)",
                            "8" to "High (8)",
                            "5" to "Default / Normal (5)",
                            "3" to "Low (3)",
                            "1" to "Min (1)"
                        )
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
}

class GotifyNodePluginService : Service() {

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private val binder = object : INodePluginService.Stub() {
        override fun getPluginApiVersion(): Int = 1

        override fun getManifests(): List<NodePluginManifest> = GotifyPluginModule.getManifests()

        override fun executeNode(nodeId: String, configJson: String, inputContextJson: String): String {
            val config = try { JSONObject(configJson) } catch (_: Exception) { JSONObject() }
            val inputCtx = try { JSONObject(inputContextJson) } catch (_: Exception) { JSONObject() }

            val credServerUrl = inputCtx.optString("cred_serverUrl", "")
            val credAppToken = inputCtx.optString("cred_appToken", "")

            val serverUrl = config.optString("serverUrl", "").ifBlank { credServerUrl }.trim().trimEnd('/')
            if (serverUrl.isBlank()) return fail("Gotify serverUrl is empty")

            val appToken = config.optString("appToken", "").ifBlank { credAppToken }.trim()
            val title = config.optString("title", "ZopHop Notification").trim()
            val priority = config.optInt("priority", 5)

            // Try to extract dynamically replaced template passed by engine or fallback to context body
            val payload = config.optString("template", inputCtx.optString("formatted_payload", inputCtx.optString("body", "")))

            val targetUrl = "$serverUrl/message" + (if (appToken.isNotBlank()) "?token=$appToken" else "")
            val jsonBody = JSONObject().apply {
                put("title", title)
                put("message", payload)
                put("priority", priority)
            }.toString()

            try {
                val requestBuilder = Request.Builder().url(targetUrl).post(jsonBody.toRequestBody("application/json; charset=utf-8".toMediaType()))
                if (appToken.isNotBlank()) requestBuilder.addHeader("X-Gotify-Key", appToken)

                val response = okHttpClient.newCall(requestBuilder.build()).execute()
                response.use { resp ->
                    val respBody = resp.body?.string() ?: ""
                    if (resp.isSuccessful) {
                        return success("Delivered to Gotify server")
                    } else {
                        return fail("Gotify request failed: HTTP ${resp.code} - $respBody")
                    }
                }
            } catch (e: Exception) {
                return fail("Gotify dispatch failed: ${e.message}")
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
