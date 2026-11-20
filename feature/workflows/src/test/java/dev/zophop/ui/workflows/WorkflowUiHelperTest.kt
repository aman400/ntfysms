package dev.zophop.ui.workflows

import dev.zophop.domain.model.NodeType
import dev.zophop.domain.model.PreconditionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class WorkflowUiHelperTest {

    @Test
    fun testNodeTitlesAndDescriptions() {
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
            assertNotNull(title)
            assertNotNull(desc)
            assertNotNull(icon)
        }
    }

    @Test
    fun testPreconditionHelpers() {
        val internetTitle = WorkflowUiHelper.getPreconditionTitle(PreconditionType.INTERNET_CHECK.name)
        assertEquals("Network Internet Check", internetTitle)

        val internetIcon = WorkflowUiHelper.getPreconditionIcon(PreconditionType.INTERNET_CHECK.name)
        assertNotNull(internetIcon)
    }
}
