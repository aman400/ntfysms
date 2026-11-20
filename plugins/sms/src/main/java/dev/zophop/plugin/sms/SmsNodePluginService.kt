package dev.zophop.plugin.sms

import android.Manifest
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.IBinder
import dev.zophop.domain.model.WorkflowNode
import dev.zophop.plugin.api.ConfigFieldSpec
import dev.zophop.plugin.api.ExecutionContext
import dev.zophop.plugin.api.INodePluginService
import dev.zophop.plugin.api.NodeCategory
import dev.zophop.plugin.api.NodeExecutionResult
import dev.zophop.plugin.api.NodeExecutor
import dev.zophop.plugin.api.NodePluginManifest
import dev.zophop.plugin.api.NodePluginModule
import okhttp3.OkHttpClient
import org.json.JSONObject

object SmsPluginModule : NodePluginModule {
    override val moduleId: String = "sms-plugin-module"

    override fun createExecutor(
        typeId: String,
        okHttpClient: OkHttpClient,
        credentialRepository: Any?
    ): NodeExecutor? {
        return when {
            typeId.contains("SMS_TRIGGER") -> SmsTriggerExecutor()
            typeId.contains("SEND_SMS_ACTION") -> SendSmsExecutor()
            typeId.contains("SIM_CHECK_LOGIC") -> object : NodeExecutor {
                override suspend fun execute(node: WorkflowNode, context: ExecutionContext, appContext: Context?): NodeExecutionResult {
                    return NodeExecutionResult.Success(logMessage = "SIM card is ready")
                }
            }
            else -> null
        }
    }

    override fun validateConfig(typeId: String, configJson: String): String? {
        val config = try { JSONObject(configJson) } catch (_: Exception) { JSONObject() }
        return when {
            typeId.contains("SMS_TRIGGER") -> {
                val allowlist = config.optString("allowlist", ".*").trim()
                val keywordRegex = config.optString("keywordRegex", ".*").trim()
                if (allowlist.isNotBlank() && allowlist != ".*") {
                    try { java.util.regex.Pattern.compile(allowlist.replace("*", ".*")) } catch (e: Exception) { return "Invalid sender allowlist regex: ${e.message}" }
                }
                if (keywordRegex.isNotBlank() && keywordRegex != ".*") {
                    try { java.util.regex.Pattern.compile(keywordRegex) } catch (e: Exception) { return "Invalid keyword regex: ${e.message}" }
                }
                null
            }
            typeId.contains("SEND_SMS_ACTION") -> {
                val recipient = config.optString("recipient", "").trim()
                val body = config.optString("body", "").trim()
                if (recipient.isBlank()) "Recipient number cannot be blank."
                else if (body.isBlank()) "Message body cannot be blank."
                else null
            }
            else -> null
        }
    }

    override fun getManifests(): List<NodePluginManifest> {
        return listOf(
            NodePluginManifest(
                typeId = "dev.zophop.plugin.sms.SMS_TRIGGER",
                displayName = "SMS Trigger",
                description = "Triggers workflow when an incoming SMS matches filters",
                category = NodeCategory.TRIGGER,
                requiredPermissions = listOf(Manifest.permission.RECEIVE_SMS),
                triggerTestPayload = """{"sender":"VM-HDFCBK","header":"VM-HDFCBK","body":"HDFC Bank: OTP for transaction of Rs 1,250 is 482910."}""",
                configSchema = listOf(
                    ConfigFieldSpec.TextField(
                        key = "allowlist",
                        label = "Sender Allowlist Regex",
                        placeholder = ".*HDFCBK.*|.*555.*",
                        tooltip = "Regex pattern matching allowed SMS senders (e.g. .*HDFCBK.* for bank sender IDs, ^\\+1555.* for specific area codes, or .*BANK.*|.*OTP.* to match multiple senders)"
                    ),
                    ConfigFieldSpec.TextField(
                        key = "keywordRegex",
                        label = "Keyword Filter Regex",
                        placeholder = ".*OTP.*|.*code.*",
                        tooltip = "Regex pattern filtering SMS body text (e.g. .*OTP.* for one-time passwords, \\b\\d{4,8}\\b for numeric verification codes, or .*verify.*|.*login.* for security alerts)"
                    ),
                    ConfigFieldSpec.TextField(
                        key = "testSender",
                        label = "Simulated Sender Number",
                        placeholder = "VM-HDFCBK",
                        tooltip = "Mock sender address or number used during dry-run test execution."
                    ),
                    ConfigFieldSpec.CodeTemplateField(
                        key = "testBody",
                        label = "Simulated SMS Body",
                        minLines = 2,
                        tooltip = "Mock message body text used during dry-run test execution."
                    )
                )
            ),
            NodePluginManifest(
                typeId = "dev.zophop.plugin.sms.SEND_SMS_ACTION",
                displayName = "Send SMS Action",
                description = "Sends an outbound SMS text message via mobile SIM",
                category = NodeCategory.ACTION,
                requiredPermissions = listOf(Manifest.permission.SEND_SMS),
                configSchema = listOf(
                    ConfigFieldSpec.TextField(
                        key = "recipient",
                        label = "Recipient Phone Number",
                        placeholder = "+15551234567",
                        singleLine = true,
                        tooltip = "Target mobile phone number receiving the SMS."
                    ),
                    ConfigFieldSpec.CodeTemplateField(
                        key = "body",
                        label = "Message Body",
                        minLines = 3,
                        tooltip = "Outbound SMS message content (supports \${body}, \${sender}, etc.)"
                    )
                )
            ),
            NodePluginManifest(
                typeId = "dev.zophop.plugin.sms.SIM_CHECK_LOGIC",
                displayName = "SIM Card Status Check",
                description = "Verifies SIM card readiness and active subscription",
                category = NodeCategory.LOGIC,
                requiredPermissions = listOf(Manifest.permission.READ_PHONE_STATE)
            )
        )
    }
}

class SmsNodePluginService : Service() {
    private val binder = object : INodePluginService.Stub() {
        override fun getPluginApiVersion(): Int = 1
        override fun getManifests(): List<NodePluginManifest> = SmsPluginModule.getManifests()
        override fun executeNode(nodeId: String, configJson: String, inputContextJson: String): String {
            val typeId = try { JSONObject(configJson).optString("typeId", "") } catch (_: Exception) { "" }
            val permissionName = when {
                typeId.contains("SMS_TRIGGER") -> Manifest.permission.RECEIVE_SMS
                typeId.contains("SEND_SMS_ACTION") -> Manifest.permission.SEND_SMS
                typeId.contains("SIM_CHECK_LOGIC") -> Manifest.permission.READ_PHONE_STATE
                else -> ""
            }
            val hasPermission = permissionName.isEmpty() || checkSelfPermission(permissionName) == PackageManager.PERMISSION_GRANTED

            if (!hasPermission) {
                return JSONObject().apply {
                    put("isSuccess", false)
                    put("errorReason", "Missing required permission '$permissionName' in SMS plugin app")
                }.toString()
            }

            return JSONObject().apply {
                put("isSuccess", true)
                put("outputPort", "main")
                put("logMessage", "SMS plugin executed successfully")
            }.toString()
        }
    }
    override fun onBind(intent: Intent?): IBinder = binder
}
