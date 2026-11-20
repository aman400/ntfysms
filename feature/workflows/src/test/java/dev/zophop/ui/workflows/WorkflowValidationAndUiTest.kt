package dev.zophop.ui.workflows

import dev.zophop.domain.model.NodeType
import dev.zophop.domain.model.PreconditionType
import dev.zophop.domain.model.Workflow
import dev.zophop.domain.model.WorkflowConnection
import dev.zophop.domain.model.WorkflowNode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkflowValidationAndUiTest {

    @Test
    fun testAllNodeTypesHaveTitlesDescriptionsAndIcons() {
        listOf(
            NodeType.SMS_TRIGGER, NodeType.NOTIFICATION_TRIGGER, NodeType.MANUAL_TRIGGER,
            NodeType.PARSE_NOTIFICATION, NodeType.REGEX_PARSER, NodeType.JSON_PARSER,
            NodeType.TEMPLATE_FORMATTER, NodeType.CONDITION, NodeType.NTFY_ACTION,
            NodeType.GOTIFY_ACTION, NodeType.HTTP_ACTION, NodeType.SEND_NOTIFICATION_ACTION,
            NodeType.SEND_SMS_ACTION
        ).forEach { type ->
            val title = WorkflowUiHelper.getNodeTitle(type)
            val desc = WorkflowUiHelper.getNodeDescription(type)
            val icon = WorkflowUiHelper.getNodeIcon(type)

            assertTrue(title.isNotBlank())
            assertTrue(desc.isNotBlank())
            assertNotNull(icon)
        }
    }

    @Test
    fun testAllPreconditionTypesHaveTitlesAndIcons() {
        val types = listOf(
            PreconditionType.INTERNET_CHECK.name,
            PreconditionType.VPN_CHECK.name,
            PreconditionType.HEALTH_CHECK.name,
            PreconditionType.SIM_CHECK.name,
            PreconditionType.GPS_CHECK.name,
            PreconditionType.CHAINED_CHECK.name
        )

        types.forEach { typeName ->
            val title = WorkflowUiHelper.getPreconditionTitle(typeName)
            val icon = WorkflowUiHelper.getPreconditionIcon(typeName)

            assertTrue(title.isNotBlank())
            assertNotNull(icon)
        }
    }

    @Test
    fun testWorkflowStructureValidation() {
        val triggerNode = WorkflowNode("n1", "SMS Trigger", NodeType.SMS_TRIGGER)
        val actionNode = WorkflowNode("n2", "ntfy Action", NodeType.NTFY_ACTION)

        val validWorkflow = Workflow(
            id = "w_val",
            name = "Valid Workflow",
            isEnabled = true,
            nodes = listOf(triggerNode, actionNode),
            connections = listOf(WorkflowConnection("n1", "n2"))
        )

        assertEquals("Valid Workflow", validWorkflow.name)
        assertEquals(2, validWorkflow.nodes.size)
        assertEquals(1, validWorkflow.connections.size)
    }
}
