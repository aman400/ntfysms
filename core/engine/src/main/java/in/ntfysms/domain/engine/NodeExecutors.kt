package `in`.ntfysms.domain.engine

import `in`.ntfysms.data.CredentialRepository
import `in`.ntfysms.domain.engine.executors.ConditionExecutor
import `in`.ntfysms.domain.engine.executors.GotifyActionExecutor
import `in`.ntfysms.domain.engine.executors.HttpActionExecutor
import `in`.ntfysms.domain.engine.executors.JsonParserExecutor
import `in`.ntfysms.domain.engine.executors.ManualTriggerExecutor
import `in`.ntfysms.domain.engine.executors.NodeExecutor
import `in`.ntfysms.domain.engine.executors.NotificationTriggerExecutor
import `in`.ntfysms.domain.engine.executors.NtfyActionExecutor
import `in`.ntfysms.domain.engine.executors.RegexParserExecutor
import `in`.ntfysms.domain.engine.executors.SendNotificationExecutor
import `in`.ntfysms.domain.engine.executors.SendSmsExecutor
import `in`.ntfysms.domain.engine.executors.SmsTriggerExecutor
import `in`.ntfysms.domain.engine.executors.TemplateFormatterExecutor
import `in`.ntfysms.domain.model.NodeType
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

typealias NodeExecutionResult = `in`.ntfysms.domain.engine.executors.NodeExecutionResult

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
