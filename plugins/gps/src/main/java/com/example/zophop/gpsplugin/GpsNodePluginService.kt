package com.example.zophop.gpsplugin

import android.Manifest
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.IBinder
import dev.zophop.plugin.api.INodePluginService
import org.json.JSONArray
import org.json.JSONObject

class GpsNodePluginService : Service() {

    private val binder = object : INodePluginService.Stub() {
        override fun getPluginApiVersion(): Int = 1

        override fun getManifestJson(): String {
            val manifest = JSONObject().apply {
                put("typeId", "com.example.zophop.gpsplugin.GPS_CHECK_LOGIC")
                put("displayName", "GPS Active Check")
                put("description", "Fails precondition if device GPS/Location services are disabled or permissions missing")
                put("category", "LOGIC")
                put("apiVersion", 1)
                put("requiredPermissions", JSONArray().put(Manifest.permission.ACCESS_FINE_LOCATION))
                put("settingsIntentAction", android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                put("configSchema", JSONArray().apply {
                    put(JSONObject().apply {
                        put("key", "requireHighAccuracy")
                        put("label", "Require High Accuracy (GPS)")
                        put("type", "DROPDOWN")
                        put("options", JSONArray().apply {
                            put(JSONObject().apply { put("value", "true"); put("label", "Yes") })
                            put(JSONObject().apply { put("value", "false"); put("label", "No (Coarse is fine)") })
                        })
                    })
                })
            }
            return JSONArray().put(manifest).toString()
        }

        override fun getRequiredPermissions(): List<String> = emptyList()
        override fun getSettingsIntentAction(): String = ""

        override fun executeNode(nodeId: String, configJson: String, inputContextJson: String): String {
            val hasLocationPerm = checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                    checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

            if (!hasLocationPerm) {
                return fail("Location permissions (ACCESS_FINE_LOCATION or ACCESS_COARSE_LOCATION) not granted in GPS Plugin App")
            }

            val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
            val isGpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
            val isNetworkEnabled = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)

            if (!isGpsEnabled && !isNetworkEnabled) {
                return fail("Device Location / GPS is turned off")
            }

            val config = try { JSONObject(configJson) } catch (_: Exception) { JSONObject() }
            val requireHighAccuracy = config.optString("requireHighAccuracy", "true") == "true"

            if (requireHighAccuracy && !isGpsEnabled) {
                return fail("High Accuracy GPS is required but only Network location is enabled")
            }

            return success("Location services are active")
        }

        private fun success(msg: String) = JSONObject().apply {
            put("isSuccess", true)
            put("outputPort", "main")
            put("logMessage", msg)
        }.toString()

        private fun fail(reason: String) = JSONObject().apply {
            put("isSuccess", false)
            put("errorReason", reason)
        }.toString()
    }

    override fun onBind(intent: Intent?): IBinder = binder
}
