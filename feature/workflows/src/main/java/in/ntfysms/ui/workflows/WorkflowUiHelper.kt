package `in`.ntfysms.ui.workflows

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.DataObject
import androidx.compose.material.icons.filled.DeviceHub
import androidx.compose.material.icons.filled.Http
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.NotificationImportant
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Rule
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.Transform
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.ui.graphics.vector.ImageVector
import `in`.ntfysms.domain.model.NodeType
import `in`.ntfysms.domain.model.PreconditionType

object WorkflowUiHelper {

    fun getNodeTitle(type: NodeType): String {
        return when (type) {
            NodeType.SMS_TRIGGER -> "SMS Trigger"
            NodeType.NOTIFICATION_TRIGGER -> "App Notification Trigger"
            NodeType.MANUAL_TRIGGER -> "Manual Action Trigger"
            NodeType.PARSE_NOTIFICATION -> "Parse Notification"
            NodeType.REGEX_PARSER -> "Regex Parser"
            NodeType.JSON_PARSER -> "JSON Parser"
            NodeType.TEMPLATE_FORMATTER -> "Template Formatter"
            NodeType.CONDITION -> "Condition Evaluator"
            NodeType.NTFY_ACTION -> "ntfy Push Action"
            NodeType.GOTIFY_ACTION -> "Gotify Push Action"
            NodeType.HTTP_ACTION -> "HTTP Webhook Action"
            NodeType.SEND_NOTIFICATION_ACTION -> "Send System Notification"
            NodeType.SEND_SMS_ACTION -> "Send Outgoing SMS"
        }
    }

    fun getNodeDescription(type: NodeType): String {
        return when (type) {
            NodeType.SMS_TRIGGER -> "Triggers when an incoming SMS text is received"
            NodeType.NOTIFICATION_TRIGGER -> "Triggers when a notification from selected apps is intercepted"
            NodeType.MANUAL_TRIGGER -> "Triggers manually on-demand"
            NodeType.PARSE_NOTIFICATION -> "Extracts text from incoming notifications"
            NodeType.REGEX_PARSER -> "Extracts fields using regular expressions"
            NodeType.JSON_PARSER -> "Parses incoming JSON payloads"
            NodeType.TEMPLATE_FORMATTER -> "Formats custom message templates"
            NodeType.CONDITION -> "Evaluates conditional logic before proceeding"
            NodeType.NTFY_ACTION -> "Forwards message to an ntfy server or topic"
            NodeType.GOTIFY_ACTION -> "Forwards message to a Gotify server"
            NodeType.HTTP_ACTION -> "Sends an HTTP webhook request"
            NodeType.SEND_NOTIFICATION_ACTION -> "Displays a system notification on device"
            NodeType.SEND_SMS_ACTION -> "Sends an outgoing text message"
        }
    }

    fun getNodeIcon(type: NodeType): ImageVector {
        return when (type) {
            NodeType.SMS_TRIGGER -> Icons.Default.Call
            NodeType.NOTIFICATION_TRIGGER -> Icons.Default.Notifications
            NodeType.MANUAL_TRIGGER -> Icons.Default.PlayArrow
            NodeType.PARSE_NOTIFICATION -> Icons.Default.Transform
            NodeType.REGEX_PARSER -> Icons.Default.Code
            NodeType.JSON_PARSER -> Icons.Default.DataObject
            NodeType.TEMPLATE_FORMATTER -> Icons.Default.Transform
            NodeType.CONDITION -> Icons.Default.Rule
            NodeType.NTFY_ACTION -> Icons.Default.DeviceHub
            NodeType.GOTIFY_ACTION -> Icons.Default.NotificationImportant
            NodeType.HTTP_ACTION -> Icons.Default.Http
            NodeType.SEND_NOTIFICATION_ACTION -> Icons.Default.Notifications
            NodeType.SEND_SMS_ACTION -> Icons.Default.Sms
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
