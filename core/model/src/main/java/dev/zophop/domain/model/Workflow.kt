package dev.zophop.domain.model

import java.util.UUID

object NodeType {
    // Triggers
    const val SMS_TRIGGER = "SMS_TRIGGER"
    const val NOTIFICATION_TRIGGER = "NOTIFICATION_TRIGGER"
    const val MANUAL_TRIGGER = "MANUAL_TRIGGER"
    const val CRON_TRIGGER = "CRON_TRIGGER"

    // Parsers & Logic
    const val PARSE_NOTIFICATION = "PARSE_NOTIFICATION"
    const val REGEX_PARSER = "REGEX_PARSER"
    const val JSON_PARSER = "JSON_PARSER"
    const val TEMPLATE_FORMATTER = "TEMPLATE_FORMATTER"
    const val CONDITION = "CONDITION"

    // Actions
    const val NTFY_ACTION = "NTFY_ACTION"
    const val GOTIFY_ACTION = "GOTIFY_ACTION"
    const val HTTP_ACTION = "HTTP_ACTION"
    const val SEND_NOTIFICATION_ACTION = "SEND_NOTIFICATION_ACTION"
    const val SEND_SMS_ACTION = "SEND_SMS_ACTION"
}

data class WorkflowNode(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val type: String,
    val isEnabled: Boolean = true,
    val configJson: String = "{}",
    val preconditions: List<Precondition> = emptyList()
)

data class WorkflowConnection(
    val fromNodeId: String,
    val toNodeId: String,
    val outputPort: String = "main", // "main", "true", "false"
    val inputPort: String = "main"
)

data class Workflow(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val description: String = "",
    val isEnabled: Boolean = true,
    val nodes: List<WorkflowNode> = emptyList(),
    val connections: List<WorkflowConnection> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
