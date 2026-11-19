package dev.zophop.domain.engine

import dev.zophop.data.CredentialRepository
import dev.zophop.domain.engine.executors.ConditionExecutor
import dev.zophop.domain.engine.executors.GotifyActionExecutor
import dev.zophop.domain.engine.executors.HttpActionExecutor
import dev.zophop.domain.engine.executors.JsonParserExecutor
import dev.zophop.domain.engine.executors.ManualTriggerExecutor
import dev.zophop.domain.engine.executors.NodeExecutor
import dev.zophop.domain.engine.executors.NotificationTriggerExecutor
import dev.zophop.domain.engine.executors.NtfyActionExecutor
import dev.zophop.domain.engine.executors.RegexParserExecutor
import dev.zophop.domain.engine.executors.SendNotificationExecutor
import dev.zophop.domain.engine.executors.SendSmsExecutor
import dev.zophop.domain.engine.executors.SmsTriggerExecutor
import dev.zophop.domain.engine.executors.TemplateFormatterExecutor
import dev.zophop.domain.model.NodeType
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

typealias NodeExecutionResult = dev.zophop.domain.engine.executors.NodeExecutionResult

open class NodeExecutorFactory(
    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build(),
    private val credentialRepository: CredentialRepository? = null
) {

    open fun getExecutor(type: NodeType): NodeExecutor {
        return when (type) {
            NodeType.SMS_TRIGGER -> SmsTriggerExecutor()
            NodeType.NOTIFICATION_TRIGGER -> NotificationTriggerExecutor()
            NodeType.MANUAL_TRIGGER -> ManualTriggerExecutor()
            NodeType.PARSE_NOTIFICATION, NodeType.REGEX_PARSER -> RegexParserExecutor()
            NodeType.JSON_PARSER -> JsonParserExecutor()
            NodeType.TEMPLATE_FORMATTER -> TemplateFormatterExecutor()
            NodeType.CONDITION -> ConditionExecutor()
            NodeType.NTFY_ACTION -> NtfyActionExecutor(HttpActionExecutor(okHttpClient, credentialRepository), credentialRepository)
            NodeType.GOTIFY_ACTION -> GotifyActionExecutor(HttpActionExecutor(okHttpClient, credentialRepository), credentialRepository)
            NodeType.HTTP_ACTION -> HttpActionExecutor(okHttpClient, credentialRepository)
            NodeType.SEND_NOTIFICATION_ACTION -> SendNotificationExecutor()
            NodeType.SEND_SMS_ACTION -> SendSmsExecutor()
        }
    }
}
