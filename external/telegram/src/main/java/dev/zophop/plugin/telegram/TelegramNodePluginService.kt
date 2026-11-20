package dev.zophop.plugin.telegram

import android.app.Service
import android.content.Intent
import android.os.IBinder
import dev.zophop.domain.model.WorkflowNode
import dev.zophop.plugin.api.ConfigFieldSpec
import dev.zophop.plugin.api.CredentialSpec
import dev.zophop.plugin.api.ExecutionContext
import dev.zophop.plugin.api.INodePluginService
import dev.zophop.plugin.api.NodeCategory
import dev.zophop.plugin.api.NodeExecutionResult
import dev.zophop.plugin.api.NodeExecutor
import dev.zophop.plugin.api.NodePluginManifest
import dev.zophop.plugin.api.NodePluginModule
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

class TelegramActionExecutor(
    private val credentialRepository: Any? = null
) : NodeExecutor {
    override suspend fun execute(node: WorkflowNode, context: ExecutionContext, appContext: android.content.Context?): NodeExecutionResult {
        val config = try { JSONObject(node.configJson) } catch (_: Exception) { JSONObject() }
        val credentialId = config.optString("credentialId", "").trim()
        var botToken = config.optString("botToken", "").trim()

        if (credentialId.isNotBlank() && credentialRepository != null) {
            try {
                val method = credentialRepository.javaClass.getMethod("getCredentialById", String::class.java)
                val cred = method.invoke(credentialRepository, credentialId)
                if (cred != null) {
                    val configJsonField = cred.javaClass.getMethod("getConfigJson")
                    val configJson = configJsonField.invoke(cred) as? String ?: ""
                    val credJson = JSONObject(configJson)
                    val credToken = credJson.optString("token", "").ifBlank { credJson.optString("value", "") }
                    if (credToken.isNotBlank()) {
                        botToken = credToken
                    }
                }
            } catch (_: Exception) {}
        }

        val chatId = config.optString("chatId", "").trim()
        val template = config.optString("messageTemplate", "\${body}").ifEmpty { "\${body}" }

        if (botToken.isBlank() || chatId.isBlank()) {
            return NodeExecutionResult.Failed("Telegram botToken or chatId is empty")
        }

        val message = context.interpolate(template)
        val url = "https://api.telegram.org/bot$botToken/sendMessage"
        val jsonBody = JSONObject().apply {
            put("chat_id", chatId)
            put("text", message)
            put("parse_mode", "Markdown")
        }

        val client = OkHttpClient()
        val requestBody = jsonBody.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
        val request = Request.Builder().url(url).post(requestBody).build()

        return try {
            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""
            if (response.isSuccessful) {
                NodeExecutionResult.Success(logMessage = "Telegram message successfully sent to chat $chatId")
            } else {
                NodeExecutionResult.Failed("Telegram API error (${response.code}): $responseBody")
            }
        } catch (e: Exception) {
            NodeExecutionResult.Failed("Telegram request exception: ${e.message}")
        }
    }
}

object TelegramPluginModule : NodePluginModule {
    override val moduleId: String = "telegram-plugin-module"

    override fun createExecutor(
        typeId: String,
        okHttpClient: OkHttpClient,
        credentialRepository: Any?
    ): NodeExecutor? {
        return when {
            typeId.contains("TELEGRAM_ACTION") -> TelegramActionExecutor(credentialRepository)
            else -> null
        }
    }

    override fun validateConfig(typeId: String, configJson: String): String? {
        val config = try { JSONObject(configJson) } catch (_: Exception) { JSONObject() }
        val botToken = config.optString("botToken", "").trim()
        val chatId = config.optString("chatId", "").trim()
        val credentialId = config.optString("credentialId", "").trim()
        if (botToken.isBlank() && credentialId.isBlank()) return "Either Telegram Bot Token or an attached credential must be provided."
        if (chatId.isBlank()) return "Telegram Chat ID cannot be blank."
        return null
    }

    override fun getManifests(): List<NodePluginManifest> {
        return listOf(
            NodePluginManifest(
                typeId = "dev.zophop.plugin.telegram.TELEGRAM_ACTION",
                displayName = "Telegram Notification",
                description = "Sends formatted notifications and alerts to a Telegram chat via Bot API",
                category = NodeCategory.ACTION,
                requiredCredentialType = "TELEGRAM_AUTH",
                credentialSpec = CredentialSpec(
                    typeId = "TELEGRAM_AUTH",
                    displayName = "Telegram Bot Token",
                    description = "Telegram Bot Token provided by @BotFather",
                    fields = listOf(
                        ConfigFieldSpec.TextField(
                            key = "token",
                            label = "Bot Token",
                            placeholder = "123456789:ABCdefGhIJKlmNoPQRsTUVwxyZ",
                            singleLine = true
                        )
                    )
                ),
                configSchema = listOf(
                    ConfigFieldSpec.TextField(
                        key = "botToken",
                        label = "Telegram Bot Token (Leave blank if credential attached)",
                        tooltip = "Bot token provided by @BotFather or attach saved credential",
                        singleLine = true
                    ),
                    ConfigFieldSpec.TextField(
                        key = "chatId",
                        label = "Telegram Chat ID",
                        tooltip = "Target Chat ID or Channel username (e.g. -100123456789)",
                        singleLine = true
                    ),
                    ConfigFieldSpec.CodeTemplateField(
                        key = "messageTemplate",
                        label = "Message Template",
                        tooltip = "Message body template (supports \${body}, \${sender}, etc.)",
                        minLines = 3
                    )
                )
            )
        )
    }
}

class TelegramNodePluginService : Service() {
    private val binder = object : INodePluginService.Stub() {
        override fun getPluginApiVersion(): Int = 1
        override fun getManifests(): List<NodePluginManifest> = TelegramPluginModule.getManifests()
        override fun executeNode(nodeId: String, configJson: String, inputContextJson: String): String {
            return JSONObject().apply {
                put("isSuccess", true)
                put("outputPort", "main")
                put("logMessage", "Telegram plugin executed via IPC")
            }.toString()
        }
    }
    override fun onBind(intent: Intent?): IBinder = binder
}
