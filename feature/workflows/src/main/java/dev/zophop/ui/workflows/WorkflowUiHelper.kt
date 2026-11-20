package dev.zophop.ui.workflows

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.DataObject
import androidx.compose.material.icons.filled.DeviceHub
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Http
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.NotificationImportant
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Rule
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.Transform
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.ui.graphics.vector.ImageVector
import dev.zophop.domain.engine.plugin.BuiltInPluginRegistry
import dev.zophop.domain.model.NodeType
import dev.zophop.domain.model.PreconditionType

object WorkflowUiHelper {

    fun getNodeTitle(type: String): String {
        val manifest = BuiltInPluginRegistry.getBuiltInManifestByTypeId(type)
        if (manifest != null) return manifest.displayName
        return type.substringAfterLast(".").replace("_", " ")
    }

    fun getNodeDescription(type: String): String {
        val manifest = BuiltInPluginRegistry.getBuiltInManifestByTypeId(type)
        if (manifest != null) return manifest.description
        return "Plugin provided workflow step ($type)"
    }

    fun getNodeIcon(type: String): ImageVector {
        return when {
            type.contains("SMS_TRIGGER", ignoreCase = true) -> Icons.Default.Call
            type.contains("NOTIFICATION_TRIGGER", ignoreCase = true) -> Icons.Default.Notifications
            type.contains("CRON_TRIGGER", ignoreCase = true) -> Icons.Default.Schedule
            type.contains("MANUAL_TRIGGER", ignoreCase = true) -> Icons.Default.PlayArrow
            type.contains("PARSE_NOTIFICATION", ignoreCase = true) -> Icons.Default.Transform
            type.contains("REGEX_PARSER", ignoreCase = true) -> Icons.Default.Code
            type.contains("JSON_PARSER", ignoreCase = true) -> Icons.Default.DataObject
            type.contains("TEMPLATE_FORMATTER", ignoreCase = true) -> Icons.Default.Transform
            type.contains("CONDITION", ignoreCase = true) -> Icons.Default.Rule
            type.contains("NTFY_ACTION", ignoreCase = true) -> Icons.Default.DeviceHub
            type.contains("GOTIFY_ACTION", ignoreCase = true) -> Icons.Default.NotificationImportant
            type.contains("HTTP_ACTION", ignoreCase = true) -> Icons.Default.Http
            type.contains("SEND_NOTIFICATION_ACTION", ignoreCase = true) -> Icons.Default.Notifications
            type.contains("SEND_SMS_ACTION", ignoreCase = true) -> Icons.Default.Sms
            else -> Icons.Default.Extension
        }
    }

    fun getPreconditionTitle(type: String): String {
        return when (type) {
            PreconditionType.INTERNET_CHECK.name -> "Network Internet Check"
            PreconditionType.VPN_CHECK.name -> "VPN Connection Check"
            PreconditionType.HEALTH_CHECK.name -> "HTTP Endpoint Healthcheck"
            PreconditionType.SIM_CHECK.name -> "SIM Card Ready Check"
            PreconditionType.GPS_CHECK.name -> "GPS Location Service Check"
            PreconditionType.CHAINED_CHECK.name -> "Chained Pipeline"
            else -> type
        }
    }

    fun getPreconditionIcon(type: String): ImageVector {
        return when (type) {
            PreconditionType.INTERNET_CHECK.name -> Icons.Default.Wifi
            PreconditionType.VPN_CHECK.name -> Icons.Default.VpnKey
            PreconditionType.HEALTH_CHECK.name -> Icons.Default.LocalHospital
            PreconditionType.SIM_CHECK.name -> Icons.Default.PhoneAndroid
            PreconditionType.GPS_CHECK.name -> Icons.Default.LocationOn
            PreconditionType.CHAINED_CHECK.name -> Icons.Default.DeviceHub
            else -> Icons.Default.Security
        }
    }
}
