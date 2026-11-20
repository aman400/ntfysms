package dev.zophop.plugin.notification

import android.app.Service
import android.content.Intent
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
import java.util.regex.Pattern

class NotificationTriggerExecutor : NodeExecutor {
    override suspend fun execute(node: WorkflowNode, context: ExecutionContext, appContext: android.content.Context?): NodeExecutionResult {
        if (appContext != null) {
            val pkg = appContext.packageName
            val flat = android.provider.Settings.Secure.getString(appContext.contentResolver, "enabled_notification_listeners")
            val hasPermission = flat != null && flat.contains(pkg)
            if (!hasPermission) {
                return NodeExecutionResult.Failed("Missing Notification Listener Service permission (Notification Access)")
            }
        }

        val config = try { JSONObject(node.configJson) } catch (_: Exception) { JSONObject() }
        val packageFilter = config.optString("packageFilter", ".*").ifBlank { ".*" }
        val textRegex = config.optString("textRegex", ".*").ifBlank { ".*" }

        val packageName = context.getString("package_name", "").ifEmpty { context.getString("package", "") }
        val title = context.getString("title", "")
        val text = context.getString("text", "").ifEmpty { context.getString("body", "") }

        if (packageFilter != ".*") {
            val filters = packageFilter.split(",", "\n").map { it.trim() }.filter { it.isNotEmpty() && it != ".*" }
            if (filters.isNotEmpty()) {
                val matches = filters.any { filter ->
                    try {
                        val regex = Pattern.compile(filter.replace("*", ".*"), Pattern.CASE_INSENSITIVE)
                        regex.matcher(packageName).find()
                    } catch (_: Exception) { false }
                }
                if (!matches) {
                    return NodeExecutionResult.Ignored("Package '$packageName' did not match filter '$packageFilter'")
                }
            }
        }

        if (textRegex != ".*") {
            val content = "$title $text"
            val matches = try {
                val regex = Pattern.compile(textRegex, Pattern.CASE_INSENSITIVE)
                regex.matcher(content).find()
            } catch (_: Exception) { false }
            if (!matches) {
                return NodeExecutionResult.Ignored("Notification content did not match text regex '$textRegex'")
            }
        }

        return NodeExecutionResult.Success(logMessage = "Notification trigger matched app: $packageName")
    }
}

object NotificationPluginModule : NodePluginModule {
    override val moduleId: String = "notification-plugin-module"

    const val NOTIFICATION_TRIGGER = "NOTIFICATION_TRIGGER"

    override fun createExecutor(
        typeId: String,
        okHttpClient: OkHttpClient,
        credentialRepository: Any?
    ): NodeExecutor? {
        return when {
            typeId.contains("NOTIFICATION_TRIGGER") -> NotificationTriggerExecutor()
            else -> null
        }
    }

    override fun validateConfig(typeId: String, configJson: String): String? {
        val config = try { JSONObject(configJson) } catch (_: Exception) { JSONObject() }
        val textRegex = config.optString("textRegex", ".*").trim()
        if (textRegex.isNotBlank() && textRegex != ".*") {
            try { java.util.regex.Pattern.compile(textRegex) } catch (e: Exception) { return "Invalid text regex filter: ${e.message}" }
        }
        return null
    }

    override fun getManifests(): List<NodePluginManifest> {
        return listOf(
            NodePluginManifest(
                typeId = "dev.zophop.plugin.notification.NOTIFICATION_TRIGGER",
                displayName = "App Notification Trigger",
                description = "Triggers when a notification from selected apps is intercepted",
                category = NodeCategory.TRIGGER,
                iconResName = "ic_node_notification_trigger",
                configSchema = listOf(
                    ConfigFieldSpec.TextField(
                        key = "packageFilter",
                        label = "Target App Packages (Comma separated or .* for all)",
                        placeholder = ".*messaging.*",
                        singleLine = true
                    ),
                    ConfigFieldSpec.TextField(
                        key = "textRegex",
                        label = "Notification Text Regex Filter",
                        placeholder = ".*verification code.*",
                        singleLine = true
                    ),
                    ConfigFieldSpec.TextField(
                        key = "testPackageName",
                        label = "Simulated Target Package",
                        placeholder = "com.example.bankapp",
                        singleLine = true,
                        isTestField = true
                    ),
                    ConfigFieldSpec.TextField(
                        key = "testTitle",
                        label = "Simulated Notification Title",
                        placeholder = "Bank Alert: OTP Code",
                        singleLine = true,
                        isTestField = true
                    ),
                    ConfigFieldSpec.CodeTemplateField(
                        key = "testText",
                        label = "Simulated Notification Text",
                        minLines = 2,
                        isTestField = true
                    )
                )
            )
        )
    }
}

class NotificationNodePluginService : Service() {
    private val binder = object : INodePluginService.Stub() {
        override fun getPluginApiVersion(): Int = 1
        override fun getManifests(): List<NodePluginManifest> = NotificationPluginModule.getManifests()
        override fun executeNode(nodeId: String, configJson: String, inputContextJson: String): String {
            return JSONObject().apply {
                put("isSuccess", true)
                put("outputPort", "main")
                put("logMessage", "Notification plugin executed")
            }.toString()
        }
    }
    override fun onBind(intent: Intent?): IBinder = binder
}
