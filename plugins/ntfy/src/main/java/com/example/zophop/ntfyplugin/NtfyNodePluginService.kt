package com.example.zophop.ntfyplugin

import android.app.Service
import android.content.Intent
import android.os.IBinder
import dev.zophop.plugin.api.INodePluginService
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class NtfyNodePluginService : Service() {

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private val binder = object : INodePluginService.Stub() {
        override fun getPluginApiVersion(): Int = 1

        override fun getManifestJson(): String {
            val manifest = JSONObject().apply {
                put("typeId", "com.example.zophop.ntfyplugin.NTFY_ACTION")
                put("displayName", "ntfy Push Action")
                put("description", "Sends payload to a ntfy.sh or self-hosted server")
                put("category", "ACTION")
                put("apiVersion", 1)
                put("requiredCredentialType", "NTFY_AUTH")
                put("configSchema", JSONArray().apply {
                    put(JSONObject().apply {
                        put("key", "serverUrl")
                        put("label", "Server URL (Leave blank to use credential default)")
                        put("type", "TEXT")
                        put("placeholder", "https://ntfy.sh")
                    })
                    put(JSONObject().apply {
                        put("key", "topic")
                        put("label", "Topic")
                        put("type", "TEXT")
                    })
                    put(JSONObject().apply {
                        put("key", "title")
                        put("label", "Title")
                        put("type", "TEXT")
                    })
                    put(JSONObject().apply {
                        put("key", "priority")
                        put("label", "Priority (1-5)")
                        put("type", "DROPDOWN")
                        put("options", JSONArray().apply {
                            put(JSONObject().apply { put("value", "5"); put("label", "Max / Urgent (5)") })
                            put(JSONObject().apply { put("value", "4"); put("label", "High (4)") })
                            put(JSONObject().apply { put("value", "3"); put("label", "Default / Normal (3)") })
                            put(JSONObject().apply { put("value", "2"); put("label", "Low (2)") })
                            put(JSONObject().apply { put("value", "1"); put("label", "Min / Silent (1)") })
                        })
                    })
                    put(JSONObject().apply {
                        put("key", "tags")
                        put("label", "Tags (comma separated)")
                        put("type", "TEXT")
                    })
                    put(JSONObject().apply {
                        put("key", "template")
                        put("label", "Message Template")
                        put("type", "TEMPLATE")
                    })
                })
            }
            return JSONArray().put(manifest).toString()
        }

        override fun getRequiredPermissions(): List<String> = emptyList()
        override fun getSettingsIntentAction(): String = ""

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
