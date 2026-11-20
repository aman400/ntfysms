package com.example.zophop.gotifyplugin

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

class GotifyNodePluginService : Service() {

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private val binder = object : INodePluginService.Stub() {
        override fun getPluginApiVersion(): Int = 1

        override fun getManifestJson(): String {
            val manifest = JSONObject().apply {
                put("typeId", "com.example.zophop.gotifyplugin.GOTIFY_ACTION")
                put("displayName", "Gotify Push Action")
                put("description", "Sends payload to a Gotify push server")
                put("category", "ACTION")
                put("apiVersion", 1)
                put("requiredCredentialType", "GOTIFY_AUTH")
                put("configSchema", JSONArray().apply {
                    put(JSONObject().apply {
                        put("key", "serverUrl")
                        put("label", "Gotify Server URL (Leave blank for credential default)")
                        put("type", "TEXT")
                        put("placeholder", "https://gotify.example.com")
                    })
                    put(JSONObject().apply {
                        put("key", "title")
                        put("label", "Notification Title")
                        put("type", "TEXT")
                    })
                    put(JSONObject().apply {
                        put("key", "priority")
                        put("label", "Priority (1-10)")
                        put("type", "DROPDOWN")
                        put("options", JSONArray().apply {
                            put(JSONObject().apply { put("value", "10"); put("label", "Max / Urgent (10)") })
                            put(JSONObject().apply { put("value", "8"); put("label", "High (8)") })
                            put(JSONObject().apply { put("value", "5"); put("label", "Default / Normal (5)") })
                            put(JSONObject().apply { put("value", "3"); put("label", "Low (3)") })
                            put(JSONObject().apply { put("value", "1"); put("label", "Min (1)") })
                        })
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
