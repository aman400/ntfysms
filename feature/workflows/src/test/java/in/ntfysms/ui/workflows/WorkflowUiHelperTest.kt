package `in`.ntfysms.ui.workflows

import `in`.ntfysms.domain.model.NodeType
import `in`.ntfysms.domain.model.PreconditionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class WorkflowUiHelperTest {

    @Test
    fun testNodeTitlesAndDescriptions() {
        NodeType.values().forEach { type ->
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
