package dev.zophop.plugin.call

import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import android.telecom.TelecomManager
import dev.zophop.plugin.api.ConfigFieldSpec
import dev.zophop.plugin.api.INodePluginService
import dev.zophop.plugin.api.NodeCategory
import dev.zophop.plugin.api.NodePluginManifest
import dev.zophop.plugin.api.NodePluginModule
import org.json.JSONObject
import java.util.regex.Pattern

object CallPluginModule : NodePluginModule {
    override val moduleId: String = "call-plugin-module"

    override fun getManifests(): List<NodePluginManifest> {
        return listOf(
            NodePluginManifest(
                typeId = "dev.zophop.plugin.call.CALL_TRIGGER",
                displayName = "Incoming Call Trigger",
                description = "Triggers when an incoming phone call is received",
                category = NodeCategory.TRIGGER,
                requiredPermissions = listOf(android.Manifest.permission.READ_PHONE_STATE),
                setupActivity = "dev.zophop.plugin.call.SetupActivity",
                triggerTestPayload = """{"caller_number":"+15559876543","sender":"+15559876543"}""",
                configSchema = listOf(
                    ConfigFieldSpec.TextField(
                        key = "callerRegex",
                        label = "Caller Number Regex Filter",
                        tooltip = "Regex pattern matching caller phone numbers (e.g. .*555.*)"
                    )
                )
            ),
            NodePluginManifest(
                typeId = "dev.zophop.plugin.call.DISCONNECT_CALL_ACTION",
                displayName = "Disconnect Call Action",
                description = "Rejects or disconnects an incoming phone call",
                category = NodeCategory.ACTION,
                requiredPermissions = listOf(android.Manifest.permission.ANSWER_PHONE_CALLS),
                setupActivity = "dev.zophop.plugin.call.SetupActivity"
            ),
            NodePluginManifest(
                typeId = "dev.zophop.plugin.call.ANSWER_CALL_ACTION",
                displayName = "Answer Call Action",
                description = "Answers an incoming phone call in speakerphone/hands-free mode",
                category = NodeCategory.ACTION,
                requiredPermissions = listOf(android.Manifest.permission.ANSWER_PHONE_CALLS),
                setupActivity = "dev.zophop.plugin.call.SetupActivity"
            )
        )
    }
}

class CallNodePluginService : Service() {

    private val binder = object : INodePluginService.Stub() {

        override fun getPluginApiVersion(): Int = 1

        override fun getManifests(): List<NodePluginManifest> = CallPluginModule.getManifests()

        override fun executeNode(nodeId: String, configJson: String, inputContextJson: String): String {
            val config = try { JSONObject(configJson) } catch (_: Exception) { JSONObject() }
            val inputCtx = try { JSONObject(inputContextJson) } catch (_: Exception) { JSONObject() }

            val typeId = config.optString("typeId", nodeId)
            val callerNumber = inputCtx.optString("caller_number", inputCtx.optString("sender", ""))

            return when {
                typeId.contains("DISCONNECT_CALL_ACTION") -> executeDisconnectCall(callerNumber)
                typeId.contains("ANSWER_CALL_ACTION") -> executeAnswerCall(callerNumber)
                else -> executeCallTrigger(config, callerNumber)
            }
        }

        private fun executeCallTrigger(config: JSONObject, callerNumber: String): String {
            val hasPhonePermission = checkSelfPermission(android.Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED
            if (!hasPhonePermission) {
                return JSONObject().apply {
                    put("isSuccess", false)
                    put("errorReason", "READ_PHONE_STATE permission not granted in Call Plugin App")
                }.toString()
            }

            val callerRegex = config.optString("callerRegex", ".*")
            val matches = try {
                Pattern.compile(callerRegex, Pattern.CASE_INSENSITIVE).matcher(callerNumber).find()
            } catch (_: Exception) {
                false
            }

            if (!matches) {
                return JSONObject().apply {
                    put("isSuccess", false)
                    put("errorReason", "Caller number '$callerNumber' did not match filter regex '$callerRegex'")
                }.toString()
            }

            val outputMap = JSONObject().apply {
                put("caller_number", callerNumber)
                put("sender", callerNumber)
                put("header", callerNumber)
            }

            return JSONObject().apply {
                put("isSuccess", true)
                put("outputPort", "main")
                put("logMessage", "Incoming call trigger matched caller '$callerNumber'")
                put("outputDataMap", outputMap)
            }.toString()
        }

        private fun executeDisconnectCall(callerNumber: String): String {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val hasPermission = checkSelfPermission(android.Manifest.permission.ANSWER_PHONE_CALLS) == PackageManager.PERMISSION_GRANTED ||
                        checkSelfPermission(android.Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED
                if (!hasPermission) {
                    return JSONObject().apply {
                        put("isSuccess", false)
                        put("errorReason", "ANSWER_PHONE_CALLS or READ_PHONE_STATE permission not granted")
                    }.toString()
                }

                val telecomManager = getSystemService(TELECOM_SERVICE) as TelecomManager
                val endSuccess = try { telecomManager.endCall(); true } catch (_: Exception) { false }

                return JSONObject().apply {
                    put("isSuccess", endSuccess)
                    put("outputPort", "main")
                    put("logMessage", if (endSuccess) "Disconnected incoming call from '$callerNumber'" else "Failed to end call")
                    put("outputDataMap", JSONObject().apply { put("call_status", if (endSuccess) "DISCONNECTED" else "FAILED") })
                }.toString()
            } else {
                return JSONObject().apply {
                    put("isSuccess", false)
                    put("errorReason", "Disconnecting call requires Android 9.0+ (API 28)")
                }.toString()
            }
        }

        private fun executeAnswerCall(callerNumber: String): String {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val hasPermission = checkSelfPermission(android.Manifest.permission.ANSWER_PHONE_CALLS) == PackageManager.PERMISSION_GRANTED
                if (!hasPermission) {
                    return JSONObject().apply {
                        put("isSuccess", false)
                        put("errorReason", "ANSWER_PHONE_CALLS permission not granted in Call Plugin App")
                    }.toString()
                }

                val telecomManager = getSystemService(TELECOM_SERVICE) as TelecomManager
                val acceptSuccess = try { telecomManager.acceptRingingCall(); true } catch (_: Exception) { false }

                return JSONObject().apply {
                    put("isSuccess", acceptSuccess)
                    put("outputPort", "main")
                    put("logMessage", if (acceptSuccess) "Answered incoming call from '$callerNumber'" else "Failed to answer call")
                    put("outputDataMap", JSONObject().apply { put("call_status", if (acceptSuccess) "ANSWERED" else "FAILED") })
                }.toString()
            } else {
                return JSONObject().apply {
                    put("isSuccess", false)
                    put("errorReason", "Answering call requires Android 8.0+ (API 26)")
                }.toString()
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder = binder
}
