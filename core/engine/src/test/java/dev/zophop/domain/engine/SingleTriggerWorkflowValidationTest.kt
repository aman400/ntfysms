package dev.zophop.domain.engine

import dev.zophop.plugin.api.NodeCategory
import dev.zophop.plugin.api.NodePluginManifest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SingleTriggerWorkflowValidationTest {

    @Test
    fun testNodeCategoryTriggerVersusRegularHelpers() {
        val triggerManifest = NodePluginManifest(
            typeId = "com.example.CALL_TRIGGER",
            displayName = "Call Trigger",
            description = "Triggers on call",
            category = NodeCategory.TRIGGER
        )

        val actionManifest = NodePluginManifest(
            typeId = "com.example.DISCONNECT_ACTION",
            displayName = "Disconnect Call",
            description = "Disconnects active call",
            category = NodeCategory.ACTION
        )

        val parserManifest = NodePluginManifest(
            typeId = "com.example.REGEX_PARSER",
            displayName = "Regex Parser",
            description = "Parses regex",
            category = NodeCategory.EXTRACTION
        )

        assertTrue(triggerManifest.isTriggerNode())
        assertFalse(triggerManifest.isRegularNode())

        assertFalse(actionManifest.isTriggerNode())
        assertTrue(actionManifest.isRegularNode())

        assertFalse(parserManifest.isTriggerNode())
        assertTrue(parserManifest.isRegularNode())
    }
}
