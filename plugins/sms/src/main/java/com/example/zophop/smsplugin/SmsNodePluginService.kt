package com.example.zophop.smsplugin

import android.Manifest
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.os.IBinder
import android.telephony.SmsManager
import dev.zophop.plugin.api.INodePluginService
import org.json.JSONArray
import org.json.JSONObject
import java.util.regex.Pattern

class SmsNodePluginService : Service() {

    private val binder = object : INodePluginService.Stub() {
        override fun getPluginApiVersion(): Int = 1

        override fun getManifestJson(): String {
            val manifestsArr = JSONArray().apply {
                // SMS Trigger
                put(JSONObject().apply {
                    put("typeId", "com.example.zophop.smsplugin.SMS_TRIGGER")
                    put("displayName", "SMS Trigger")
                    put("description", "Triggers workflow when an incoming SMS matches filters")
                    put("category", "TRIGGER")
                    put("apiVersion", 1)
                    put("requiredPermissions", JSONArray().put(Manifest.permission.RECEIVE_SMS))
                    put("configSchema", JSONArray().apply {
                        put(JSONObject().apply {
                            put("key", "allowlist")
                            put("label", "Sender Allowlist (Comma separated)")
                            put("type", "TEXT")
                        })
                        put(JSONObject().apply {
                            put("key", "keywordRegex")
                            put("label", "Body Keyword Regex")
                            put("type", "TEXT")
                        })
                    })
                })

                // Send SMS Action
                put(JSONObject().apply {
                    put("typeId", "com.example.zophop.smsplugin.SEND_SMS_ACTION")
                    put("displayName", "Send SMS Action")
                    put("description", "Sends an outgoing SMS message")
                    put("category", "ACTION")
                    put("apiVersion", 1)
                    put("requiredPermissions", JSONArray().put(Manifest.permission.SEND_SMS))
                    put("configSchema", JSONArray().apply {
                        put(JSONObject().apply {
                            put("key", "recipient")
                            put("label", "Recipient Number")
                            put("type", "TEXT")
                        })
                        put(JSONObject().apply {
                            put("key", "body")
                            put("label", "Message Body")
                            put("type", "TEMPLATE")
                            put("minLines", 3)
                        })
                    })
                })

                // SIM Check Precondition equivalent (logic node style)
                put(JSONObject().apply {
                    put("typeId", "com.example.zophop.smsplugin.SIM_CHECK_LOGIC")
                    put("displayName", "SIM Active Check")
                    put("description", "Fails if active SIM does not match required carrier")
                    put("category", "LOGIC")
                    put("apiVersion", 1)
                    put("requiredPermissions", JSONArray().put(Manifest.permission.READ_PHONE_STATE))
                    put("configSchema", JSONArray().apply {
                        put(JSONObject().apply {
                            put("key", "requiredCarrier")
                            put("label", "Required Carrier Name")
                            put("type", "TEXT")
                        })
                    })
                })
            }
            return manifestsArr.toString()
        }

        override fun getRequiredPermissions(): List<String> = emptyList() // Now managed per-node in manifest
        override fun getSettingsIntentAction(): String = ""

        override fun executeNode(nodeId: String, configJson: String, inputContextJson: String): String {
            val config = try { JSONObject(configJson) } catch (_: Exception) { JSONObject() }
            val inputCtx = try { JSONObject(inputContextJson) } catch (_: Exception) { JSONObject() }

            val typeId = config.optString("typeId", nodeId)

            return when {
                typeId.contains("SEND_SMS_ACTION") -> executeSendSms(config, inputCtx)
                typeId.contains("SIM_CHECK_LOGIC") -> executeSimCheck(config)
                else -> executeSmsTrigger(config, inputCtx)
            }
        }

        private fun executeSmsTrigger(config: JSONObject, inputCtx: JSONObject): String {
            if (checkSelfPermission(Manifest.permission.RECEIVE_SMS) != PackageManager.PERMISSION_GRANTED) {
                return fail("RECEIVE_SMS permission missing")
            }

            val allowlist = config.optString("allowlist", ".*")
            val keywordRegex = config.optString("keywordRegex", ".*")

            val sender = inputCtx.optString("sender", "").ifEmpty { inputCtx.optString("address", "") }
            val body = inputCtx.optString("body", "").ifEmpty { inputCtx.optString("text", "") }

            val allowlistPatterns = allowlist.split(",", "\n").map { it.trim() }.filter { it.isNotEmpty() }
            val senderMatches = allowlistPatterns.isEmpty() || allowlistPatterns.any { pattern ->
                try {
                    Pattern.compile(pattern.replace("*", ".*"), Pattern.CASE_INSENSITIVE).matcher(sender).find()
                } catch (_: Exception) { false }
            }

            if (!senderMatches) return fail("Sender '$sender' did not match allowlist")

            if (keywordRegex.isNotBlank() && keywordRegex != ".*") {
                val matches = try {
                    Pattern.compile(keywordRegex, Pattern.CASE_INSENSITIVE).matcher(body).find()
                } catch (_: Exception) { false }
                if (!matches) return fail("Message body did not match keyword regex '$keywordRegex'")
            }

            return success("SMS trigger matched sender: $sender")
        }

        private fun executeSendSms(config: JSONObject, inputCtx: JSONObject): String {
            if (checkSelfPermission(Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
                return fail("SEND_SMS permission missing")
            }

            val recipient = config.optString("recipient", "").trim()
            val body = config.optString("body", "").trim()

            if (recipient.isBlank() || body.isBlank()) return fail("Recipient or body is empty")

            return try {
                SmsManager.getDefault().sendTextMessage(recipient, null, body, null, null)
                success("Dispatched SMS to $recipient")
            } catch (e: Exception) {
                fail("Failed to send SMS: ${e.message}")
            }
        }

        private fun executeSimCheck(config: JSONObject): String {
            if (checkSelfPermission(Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
                return fail("READ_PHONE_STATE permission missing")
            }
            // Mocking carrier logic for plugin example
            val requiredCarrier = config.optString("requiredCarrier", "").trim()
            if (requiredCarrier.isNotBlank()) {
                // actual implementation would check TelephonyManager.networkOperatorName
                return success("SIM Check Passed (Simulation)")
            }
            return success("No carrier restricted")
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
