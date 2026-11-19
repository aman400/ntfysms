package dev.zophop.domain.model

import java.util.UUID

enum class NodeType {
    // Triggers
    SMS_TRIGGER,
    NOTIFICATION_TRIGGER,
    MANUAL_TRIGGER,

    // Parsers & Logic
    PARSE_NOTIFICATION,
    REGEX_PARSER,
    JSON_PARSER,
    TEMPLATE_FORMATTER,
    CONDITION,

    // Actions
    NTFY_ACTION,
    GOTIFY_ACTION,
    HTTP_ACTION,
    SEND_NOTIFICATION_ACTION,
    SEND_SMS_ACTION
}

data class WorkflowNode(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val type: NodeType,
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
