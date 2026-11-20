package dev.zophop.plugin.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NodePluginManifestTest {

    @Test
    fun testNodePluginManifestJsonSerializationAndDeserialization() {
        val fields = listOf(
            ConfigFieldSpec.TextField("botToken", "Telegram Bot Token", tooltip = "Bot token from @BotFather"),
            ConfigFieldSpec.DropdownField(
                "parseMode",
                "Parse Mode",
                options = listOf("HTML" to "HTML Formatting", "MARKDOWN" to "Markdown Formatting")
            ),
            ConfigFieldSpec.CodeTemplateField("messageTemplate", "Message Template", minLines = 4)
        )

        val originalManifest = NodePluginManifest(
            typeId = "com.example.telegram.TELEGRAM_ACTION",
            displayName = "Telegram Action Plugin",
            description = "Forwards alerts to Telegram Chat",
            category = NodeCategory.ACTION,
            iconResName = "ic_telegram",
            apiVersion = 1,
            configSchema = fields
        )

        val json = originalManifest.toJson()
        val deserialized = NodePluginManifest.fromJson(json.toString())

        assertEquals(originalManifest.typeId, deserialized.typeId)
        assertEquals(originalManifest.displayName, deserialized.displayName)
        assertEquals(originalManifest.description, deserialized.description)
        assertEquals(originalManifest.category, deserialized.category)
        assertEquals(originalManifest.apiVersion, deserialized.apiVersion)
        assertEquals(3, deserialized.configSchema.size)

        val textSpec = deserialized.configSchema[0] as ConfigFieldSpec.TextField
        assertEquals("botToken", textSpec.key)
        assertEquals("Telegram Bot Token", textSpec.label)

        val dropdownSpec = deserialized.configSchema[1] as ConfigFieldSpec.DropdownField
        assertEquals("parseMode", dropdownSpec.key)
        assertEquals(2, dropdownSpec.options.size)

        val templateSpec = deserialized.configSchema[2] as ConfigFieldSpec.CodeTemplateField
        assertEquals("messageTemplate", templateSpec.key)
        assertEquals(4, templateSpec.minLines)
    }
}
