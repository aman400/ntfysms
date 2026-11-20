package dev.zophop.plugin.gps

import android.Manifest
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.IBinder
import dev.zophop.plugin.api.ConfigFieldSpec
import dev.zophop.plugin.api.INodePluginService
import dev.zophop.plugin.api.NodeCategory
import dev.zophop.plugin.api.NodePluginManifest
import dev.zophop.plugin.api.NodePluginModule
import org.json.JSONObject

object GpsPluginModule : NodePluginModule {
    override val moduleId: String = "gps-plugin-module"

    override fun getManifests(): List<NodePluginManifest> {
        return listOf(
            NodePluginManifest(
                typeId = "dev.zophop.plugin.gps.GPS_CHECK_LOGIC",
                displayName = "GPS Active Check",
                description = "Fails precondition if device GPS/Location services are disabled or permissions missing",
                category = NodeCategory.LOGIC,
                requiredPermissions = listOf(Manifest.permission.ACCESS_FINE_LOCATION),
                setupActivity = "dev.zophop.plugin.gps.SetupActivity",
                configSchema = listOf(
                    ConfigFieldSpec.DropdownField(
                        key = "requireHighAccuracy",
                        label = "Require High Accuracy (GPS)",
                        options = listOf(
                            "true" to "Yes",
                            "false" to "No (Coarse is fine)"
                        )
                    )
                )
            )
        )
    }
}

class GpsNodePluginService : Service() {

    private val binder = object : INodePluginService.Stub() {
        override fun getPluginApiVersion(): Int = 1

        override fun getManifests(): List<NodePluginManifest> = GpsPluginModule.getManifests()

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
