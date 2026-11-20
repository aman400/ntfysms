package dev.zophop.domain.engine

import dev.zophop.domain.engine.plugin.ExternalPluginExecutor
import dev.zophop.domain.engine.plugin.ExternalPluginServiceInfo
import dev.zophop.plugin.api.NodeCategory
import dev.zophop.plugin.api.NodePluginManifest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class ExternalPluginIpcTest {

    @Test
    fun testExternalPluginServiceInfoDataStructure() {
        val manifest = NodePluginManifest(
            typeId = "com.example.discord.DISCORD_ACTION",
            displayName = "Discord Webhook Action",
            description = "Posts alerts to Discord Channel",
            category = NodeCategory.ACTION
        )

        val info = ExternalPluginServiceInfo(
            packageName = "com.example.discord",
            serviceClassName = "com.example.discord.DiscordNodePluginService",
            manifest = manifest,
            apiVersion = 1
        )

        assertNotNull(info)
        assertEquals("com.example.discord", info.packageName)
        assertEquals("com.example.discord.DiscordNodePluginService", info.serviceClassName)
        assertEquals("com.example.discord.DISCORD_ACTION", info.manifest.typeId)
        assertEquals(1, info.apiVersion)
    }

    @Test
    fun testExternalPluginExecutorInstantiation() {
        val manifest = NodePluginManifest(
            typeId = "com.example.slack.SLACK_ACTION",
            displayName = "Slack Action",
            description = "Posts to Slack Channel",
            category = NodeCategory.ACTION
        )
        val info = ExternalPluginServiceInfo("com.example.slack", "SlackService", manifest, 1)

        val executor = ExternalPluginExecutor(info)
        assertNotNull(executor)
    }
}
