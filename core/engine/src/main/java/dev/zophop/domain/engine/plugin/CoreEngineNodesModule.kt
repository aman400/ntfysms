package dev.zophop.domain.engine.plugin

import dev.zophop.data.CredentialRepository
import dev.zophop.domain.engine.executors.ConditionExecutor
import dev.zophop.domain.engine.executors.CronTriggerExecutor
import dev.zophop.domain.engine.executors.HttpActionExecutor
import dev.zophop.domain.engine.executors.JsonParserExecutor
import dev.zophop.domain.engine.executors.ManualTriggerExecutor
import dev.zophop.domain.engine.executors.RegexParserExecutor
import dev.zophop.domain.engine.executors.SendNotificationExecutor
import dev.zophop.domain.engine.executors.TemplateFormatterExecutor
import dev.zophop.domain.model.CredentialType
import dev.zophop.plugin.api.ConfigFieldSpec
import dev.zophop.plugin.api.CredentialSpec
import dev.zophop.plugin.api.NodeCategory
import dev.zophop.plugin.api.NodeExecutor
import dev.zophop.plugin.api.NodePluginManifest
import dev.zophop.plugin.api.NodePluginModule
import okhttp3.OkHttpClient
import org.json.JSONObject

object CoreEngineNodesModule : NodePluginModule {
    override val moduleId: String = "core-engine-nodes"

    const val MANUAL_TRIGGER = "MANUAL_TRIGGER"
    const val CRON_TRIGGER = "CRON_TRIGGER"
    const val PARSE_NOTIFICATION = "PARSE_NOTIFICATION"
    const val REGEX_PARSER = "REGEX_PARSER"
    const val JSON_PARSER = "JSON_PARSER"
    const val TEMPLATE_FORMATTER = "TEMPLATE_FORMATTER"
    const val CONDITION = "CONDITION"
    const val HTTP_ACTION = "HTTP_ACTION"
    const val SEND_NOTIFICATION_ACTION = "SEND_NOTIFICATION_ACTION"

    override fun createExecutor(
        typeId: String,
        okHttpClient: OkHttpClient,
        credentialRepository: Any?
    ): NodeExecutor? {
        val credRepo = credentialRepository as? CredentialRepository
        return when {
            typeId == MANUAL_TRIGGER || typeId.endsWith(".$MANUAL_TRIGGER") -> ManualTriggerExecutor()
            typeId == CRON_TRIGGER || typeId.endsWith(".$CRON_TRIGGER") -> CronTriggerExecutor()
            typeId == PARSE_NOTIFICATION || typeId.endsWith(".$PARSE_NOTIFICATION") ||
            typeId == REGEX_PARSER || typeId.endsWith(".$REGEX_PARSER") -> RegexParserExecutor()
            typeId == JSON_PARSER || typeId.endsWith(".$JSON_PARSER") -> JsonParserExecutor()
            typeId == TEMPLATE_FORMATTER || typeId.endsWith(".$TEMPLATE_FORMATTER") -> TemplateFormatterExecutor()
            typeId == CONDITION || typeId.endsWith(".$CONDITION") -> ConditionExecutor()
            typeId == HTTP_ACTION || typeId.endsWith(".$HTTP_ACTION") -> HttpActionExecutor(okHttpClient, credRepo)
            typeId == SEND_NOTIFICATION_ACTION || typeId.endsWith(".$SEND_NOTIFICATION_ACTION") -> SendNotificationExecutor()
            else -> null
        }
    }

    override fun getManifests(): List<NodePluginManifest> {
        return listOf(
            // 1. Manual Trigger Node
            NodePluginManifest(
                typeId = MANUAL_TRIGGER,
                displayName = "Manual Action Trigger",
                description = "Triggers manually on-demand with test simulation payloads",
                category = NodeCategory.TRIGGER,
                iconResName = "ic_node_manual_trigger",
                configSchema = listOf(
                    ConfigFieldSpec.CodeTemplateField(
                        key = "testBody",
                        label = "Simulated Event Body",
                        minLines = 3
                    )
                )
            ),

            // 2. Cron Schedule Trigger
            NodePluginManifest(
                typeId = CRON_TRIGGER,
                displayName = "Cron Schedule Trigger",
                description = "Triggers workflow automatically on a scheduled interval or cron expression",
                category = NodeCategory.TRIGGER,
                iconResName = "ic_node_cron_trigger",
                configSchema = listOf(
                    ConfigFieldSpec.TextField(
                        key = "cronExpression",
                        label = "Schedule Interval / Cron Expression",
                        placeholder = "every 30m, hourly, or 0 8 * * *",
                        tooltip = "Frequency of scheduled execution (e.g. 'every 15m', 'hourly', 'daily', or cron format)",
                        singleLine = true
                    ),
                    ConfigFieldSpec.TextField(
                        key = "testPayload",
                        label = "Simulated Cron Payload",
                        placeholder = "Scheduled execution triggered",
                        singleLine = true,
                        isTestField = true
                    )
                )
            ),

            // 2. Parse Notification
            NodePluginManifest(
                typeId = PARSE_NOTIFICATION,
                displayName = "Parse Notification",
                description = "Extracts text fields from incoming app notifications",
                category = NodeCategory.EXTRACTION,
                iconResName = "ic_node_parse_notification",
                configSchema = listOf(
                    ConfigFieldSpec.TextField(
                        key = "regex",
                        label = "Regular Expression Pattern",
                        placeholder = """\b\d{6}\b""",
                        singleLine = true
                    ),
                    ConfigFieldSpec.TextField(
                        key = "outputKey",
                        label = "Output Variable Name",
                        placeholder = "code",
                        singleLine = true
                    )
                )
            ),

            // 3. Regex Parser
            NodePluginManifest(
                typeId = REGEX_PARSER,
                displayName = "Regex Parser",
                description = "Extracts fields using regular expressions",
                category = NodeCategory.EXTRACTION,
                iconResName = "ic_node_regex_parser",
                configSchema = listOf(
                    ConfigFieldSpec.TextField(
                        key = "regex",
                        label = "Regular Expression Pattern",
                        placeholder = """\b\d{6}\b""",
                        singleLine = true
                    ),
                    ConfigFieldSpec.TextField(
                        key = "outputKey",
                        label = "Output Variable Name",
                        placeholder = "code",
                        singleLine = true
                    )
                )
            ),

            // 4. JSON Parser
            NodePluginManifest(
                typeId = JSON_PARSER,
                displayName = "JSON Parser",
                description = "Parses incoming JSON payloads",
                category = NodeCategory.EXTRACTION,
                iconResName = "ic_node_json_parser",
                configSchema = listOf(
                    ConfigFieldSpec.TextField(
                        key = "jsonKey",
                        label = "JSON Field Key",
                        placeholder = "status",
                        singleLine = true
                    ),
                    ConfigFieldSpec.TextField(
                        key = "outputKey",
                        label = "Output Variable Name",
                        placeholder = "parsedStatus",
                        singleLine = true
                    )
                )
            ),

            // 5. Template Formatter
            NodePluginManifest(
                typeId = TEMPLATE_FORMATTER,
                displayName = "Template Formatter",
                description = "Formats custom message templates using variables",
                category = NodeCategory.LOGIC,
                iconResName = "ic_node_template_formatter",
                configSchema = listOf(
                    ConfigFieldSpec.CodeTemplateField(
                        key = "template",
                        label = "Message Template",
                        tooltip = "Use \${variable} placeholders",
                        minLines = 3
                    )
                )
            ),

            // 6. Condition Evaluator
            NodePluginManifest(
                typeId = CONDITION,
                displayName = "Condition Evaluator",
                description = "Evaluates conditional logic before proceeding",
                category = NodeCategory.LOGIC,
                iconResName = "ic_node_condition",
                configSchema = listOf(
                    ConfigFieldSpec.TextField(
                        key = "variable",
                        label = "Variable Name",
                        placeholder = "code",
                        singleLine = true
                    ),
                    ConfigFieldSpec.DropdownField(
                        key = "operator",
                        label = "Comparison Operator",
                        options = listOf(
                            "EQUALS" to "Equals",
                            "NOT_EQUALS" to "Does Not Equal",
                            "CONTAINS" to "Contains",
                            "REGEX_MATCH" to "Regex Match"
                        )
                    ),
                    ConfigFieldSpec.TextField(
                        key = "value",
                        label = "Target Value",
                        singleLine = true
                    )
                )
            ),

            // 7. HTTP Webhook Action
            NodePluginManifest(
                typeId = HTTP_ACTION,
                displayName = "HTTP Webhook Action",
                description = "Sends an HTTP POST/GET request to a target server",
                category = NodeCategory.ACTION,
                iconResName = "ic_node_http_action",
                configSchema = listOf(
                    ConfigFieldSpec.TextField(
                        key = "url",
                        label = "Target Webhook URL",
                        placeholder = "https://api.example.com/webhook",
                        singleLine = true
                    ),
                    ConfigFieldSpec.DropdownField(
                        key = "method",
                        label = "HTTP Method",
                        options = listOf(
                            "POST" to "POST",
                            "GET" to "GET",
                            "PUT" to "PUT"
                        )
                    ),
                    ConfigFieldSpec.CodeTemplateField(
                        key = "queryParams",
                        label = "Query Parameters (key=value, one per line)",
                        minLines = 2
                    ),
                    ConfigFieldSpec.CodeTemplateField(
                        key = "headers",
                        label = "HTTP Headers",
                        minLines = 2
                    ),
                    ConfigFieldSpec.CodeTemplateField(
                        key = "bodyTemplate",
                        label = "Body Template / Payload",
                        minLines = 3
                    )
                )
            ),

            // 8. Send Notification Action
            NodePluginManifest(
                typeId = SEND_NOTIFICATION_ACTION,
                displayName = "Send System Notification",
                description = "Displays a system notification on device",
                category = NodeCategory.ACTION,
                iconResName = "ic_node_send_notification",
                configSchema = listOf(
                    ConfigFieldSpec.CodeTemplateField(
                        key = "title",
                        label = "Notification Title",
                        tooltip = "Use \${variable} placeholders",
                        minLines = 1,
                        singleLine = true
                    ),
                    ConfigFieldSpec.CodeTemplateField(
                        key = "body",
                        label = "Notification Content",
                        minLines = 2,
                        singleLine = false
                    )
                )
            )
        )
    }

    override fun getCredentialSpecs(): List<CredentialSpec> {
        return listOf(
            CredentialSpec(
                typeId = CredentialType.BEARER_TOKEN,
                displayName = "Bearer Token (API Key)",
                description = "Encrypted API Bearer Token key",
                fields = listOf(
                    ConfigFieldSpec.TextField(
                        key = "token",
                        label = "Token / API Key",
                        singleLine = true
                    )
                )
            ),
            CredentialSpec(
                typeId = CredentialType.BASIC_AUTH,
                displayName = "Basic Auth (Username & Password)",
                description = "HTTP Basic Authentication credentials",
                fields = listOf(
                    ConfigFieldSpec.TextField(
                        key = "username",
                        label = "Username",
                        singleLine = true
                    ),
                    ConfigFieldSpec.TextField(
                        key = "password",
                        label = "Password",
                        singleLine = true
                    )
                )
            ),
            CredentialSpec(
                typeId = CredentialType.HEADER_SET,
                displayName = "Custom Headers",
                description = "Custom HTTP headers string",
                fields = listOf(
                    ConfigFieldSpec.CodeTemplateField(
                        key = "headers",
                        label = "Custom Headers (Key: Value)",
                        minLines = 3
                    )
                )
            )
        )
    }

    override fun validateConfig(typeId: String, configJson: String): String? {
        val config = try { JSONObject(configJson) } catch (_: Exception) { JSONObject() }
        return when {
            typeId.contains("SEND_SMS_ACTION", ignoreCase = true) -> {
                val recipient = config.optString("recipient", "").trim()
                val body = config.optString("body", "").trim()
                if (recipient.isBlank()) "Recipient phone number cannot be blank."
                else if (body.isBlank()) "Message body cannot be blank."
                else null
            }
            typeId.contains("SEND_NOTIFICATION_ACTION", ignoreCase = true) -> {
                val title = config.optString("title", "").trim()
                val body = config.optString("body", "").trim()
                if (title.isBlank()) "Notification title cannot be blank."
                else if (body.isBlank()) "Notification content body cannot be blank."
                else null
            }
            typeId.contains("HTTP_ACTION", ignoreCase = true) -> {
                val url = config.optString("url", "").ifBlank { config.optString("serverUrl", "") }.trim()
                if (url.isBlank()) "HTTP URL cannot be blank."
                else if (!url.startsWith("http://", ignoreCase = true) && !url.startsWith("https://", ignoreCase = true)) "HTTP URL must start with http:// or https://."
                else null
            }
            typeId.contains("REGEX_PARSER", ignoreCase = true) -> {
                val regex = config.optString("regex", "").trim()
                val outputKey = config.optString("outputKey", "").trim()
                if (regex.isBlank()) "Regular expression pattern cannot be blank."
                else {
                    try {
                        java.util.regex.Pattern.compile(regex)
                        if (outputKey.isBlank()) "Output variable name cannot be blank." else null
                    } catch (e: Exception) {
                        "Invalid regular expression: ${e.message}"
                    }
                }
            }
            typeId.contains("JSON_PARSER", ignoreCase = true) -> {
                val jsonKey = config.optString("jsonKey", "").trim()
                val outputKey = config.optString("outputKey", "").trim()
                if (jsonKey.isBlank()) "JSON path key cannot be blank."
                else if (outputKey.isBlank()) "Output variable name cannot be blank."
                else null
            }
            typeId.contains("TEMPLATE_FORMATTER", ignoreCase = true) -> {
                val template = config.optString("template", "").trim()
                if (template.isBlank()) "Template text cannot be blank." else null
            }
            typeId.contains("CONDITION", ignoreCase = true) -> {
                val variable = config.optString("variable", "").trim()
                val operator = config.optString("operator", "").trim()
                if (variable.isBlank()) "Condition variable cannot be blank."
                else if (operator.isBlank()) "Condition operator must be selected."
                else null
            }
            typeId.contains("CRON_TRIGGER", ignoreCase = true) -> {
                val cronExpression = config.optString("cronExpression", "").trim()
                if (cronExpression.isBlank()) "Schedule interval or cron expression cannot be blank." else null
            }
            else -> null
        }
    }
}
