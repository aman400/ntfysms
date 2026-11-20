package com.example.zophop.gpsplugin

import android.Manifest
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.zophop.plugin.api.NodePluginManifest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(manifest = Config.NONE)
class GpsNodePluginServiceTest {

    @Test
    fun testGpsPluginManifestStructure() {
        val serviceController = Robolectric.buildService(GpsNodePluginService::class.java).create()
        val service = serviceController.get()
        val binder = service.onBind(null) as dev.zophop.plugin.api.INodePluginService
        val manifestJson = binder.getManifestJson()

        assertNotNull(manifestJson)
        val manifests = NodePluginManifest.parseManifests(manifestJson)

        assertEquals(1, manifests.size)
        val gpsCheck = manifests.first()

        assertEquals("com.example.zophop.gpsplugin.GPS_CHECK_LOGIC", gpsCheck.typeId)
        assertTrue(gpsCheck.requiredPermissions.contains(Manifest.permission.ACCESS_FINE_LOCATION))
        assertEquals(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS, gpsCheck.settingsIntentAction)
    }

    @Test
    fun testGpsCheckFailsWhenPermissionMissing() {
        val serviceController = Robolectric.buildService(GpsNodePluginService::class.java).create()
        val service = serviceController.get()
        Shadows.shadowOf(service).denyPermissions(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)

        val binder = service.onBind(null) as dev.zophop.plugin.api.INodePluginService

        val configJson = """{"typeId":"com.example.zophop.gpsplugin.GPS_CHECK_LOGIC"}"""
        val resultJson = binder.executeNode("node_1", configJson, "{}")

        val json = JSONObject(resultJson)
        assertFalse(json.getBoolean("isSuccess"))
        assertTrue(json.getString("errorReason").contains("Location permissions"))
    }
}
