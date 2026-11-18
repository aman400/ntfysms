package `in`.ntfysms.data

import `in`.ntfysms.domain.model.NodeType
import `in`.ntfysms.domain.model.Workflow
import `in`.ntfysms.domain.model.WorkflowConnection
import `in`.ntfysms.domain.model.WorkflowNode
import org.json.JSONObject
import java.util.UUID

class DefaultWorkflowInitializer(
    private val workflowRepository: WorkflowRepository
) {
    suspend fun checkAndInitialize() {
        if (workflowRepository.getWorkflowCount() > 0) {
            return
        }

        val triggerId = UUID.randomUUID().toString()
        val triggerNode = WorkflowNode(
            id = triggerId,
            name = "SMS Input Filter",
            type = NodeType.SMS_TRIGGER,
            isEnabled = true,
            configJson = JSONObject().apply {
                put("allowlist", ".*")
                put("keywordRegex", ".*")
            }.toString()
        )

        val parserId = UUID.randomUUID().toString()
        val parserNode = WorkflowNode(
            id = parserId,
            name = "OTP Code Extractor",
            type = NodeType.REGEX_PARSER,
            isEnabled = true,
            configJson = JSONObject().apply {
                put("regex", "\\b\\d{4,8}\\b")
                put("outputKey", "code")
            }.toString()
        )

        val templateId = UUID.randomUUID().toString()
        val templateNode = WorkflowNode(
            id = templateId,
            name = "Message Payload Formatter",
            type = NodeType.TEMPLATE_FORMATTER,
            isEnabled = true,
            configJson = JSONObject().apply {
                put("template", "\${body}")
            }.toString()
        )

        val actionId = UUID.randomUUID().toString()
        val actionNode = WorkflowNode(
            id = actionId,
            name = "ntfy Target Action",
            type = NodeType.NTFY_ACTION,
            isEnabled = true,
            configJson = JSONObject().apply {
                put("serverUrl", "https://ntfy.sh")
                put("topic", "ntfysms-topic-" + UUID.randomUUID().toString().take(6))
                put("priority", "3")
                put("tags", "otp,sms")
            }.toString()
        )

        val defaultWorkflow = Workflow(
            id = "default_sms_workflow",
            name = "Primary SMS Forwarder Workflow",
            description = "Default automated workflow for SMS OTP extraction and ntfy notification forwarding",
            isEnabled = true,
            nodes = listOf(triggerNode, parserNode, templateNode, actionNode),
            connections = listOf(
                WorkflowConnection(fromNodeId = triggerId, toNodeId = parserId),
                WorkflowConnection(fromNodeId = parserId, toNodeId = templateId),
                WorkflowConnection(fromNodeId = templateId, toNodeId = actionId)
            )
        )

        workflowRepository.saveWorkflow(defaultWorkflow)
    }
}
