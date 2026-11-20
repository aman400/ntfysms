package com.example.zophop.gotifyplugin

import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.zophop.plugin.api.NodePluginManifest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(manifest = Config.NONE)
class GotifyNodePluginServiceTest {

    @Test
    fun testGotifyPluginManifestStructure() {
        val serviceController = Robolectric.buildService(GotifyNodePluginService::class.java).create()
        val service = serviceController.get()
        val binder = service.onBind(null) as dev.zophop.plugin.api.INodePluginService
        val manifestJson = binder.getManifestJson()

        assertNotNull(manifestJson)
        val manifests = NodePluginManifest.parseManifests(manifestJson)

        assertEquals(1, manifests.size)
        val gotifyAction = manifests.first()

        assertEquals("com.example.zophop.gotifyplugin.GOTIFY_ACTION", gotifyAction.typeId)
        assertEquals("GOTIFY_AUTH", gotifyAction.requiredCredentialType)
        assertTrue(gotifyAction.requiredPermissions.isEmpty())
        assertEquals(4, gotifyAction.configSchema.size)
    }

    @Test
    fun testGotifyActionFailsOnEmptyServerUrl() {
        val serviceController = Robolectric.buildService(GotifyNodePluginService::class.java).create()
        val service = serviceController.get()
        val binder = service.onBind(null) as dev.zophop.plugin.api.INodePluginService

        val configJson = """{"typeId":"com.example.zophop.gotifyplugin.GOTIFY_ACTION"}"""
        // Missing cred_serverUrl and config serverUrl
        val resultJson = binder.executeNode("node_1", configJson, "{}")

        val json = JSONObject(resultJson)
        assertEquals(false, json.getBoolean("isSuccess"))
        assertTrue(json.getString("errorReason").contains("serverUrl is empty"))
    }
}
