package dev.zophop.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import dev.zophop.domain.model.Precondition
import dev.zophop.domain.model.PreconditionType
import dev.zophop.domain.model.WorkflowConnection
import dev.zophop.domain.model.WorkflowNode
import org.json.JSONArray
import org.json.JSONObject

@Entity(tableName = "preconditions")
data class PreconditionEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val type: String, // "INTERNET_CHECK", "VPN_CHECK", "HEALTH_CHECK", "SIM_CHECK", "GPS_CHECK", "CHAINED_CHECK"
    val isEnabled: Boolean = true,
    val targetUrl: String = "",
    val method: String = "GET",
    val headers: String = "",
    val expectedStatus: Int = 200,
    val requiredCarrier: String = "",
    val requiredPermission: String = "",
    val credentialId: String = "",
    val pipelineJson: String = "",
    val onFailureAction: String = "FAIL_IMMEDIATELY", // "FAIL_IMMEDIATELY" or "RETRY"
    val maxRetries: Int = 3,
    val retryIntervalSeconds: Int = 30,
    val showNotificationOnFailure: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis()
)

fun PreconditionEntity.toDomain(): Precondition {
    return when (type) {
        PreconditionType.INTERNET_CHECK.name -> Precondition.InternetCheck(
            id = id,
            isEnabled = isEnabled,
            onFailureAction = onFailureAction,
            maxRetries = maxRetries,
            retryIntervalSeconds = retryIntervalSeconds,
            showNotificationOnFailure = showNotificationOnFailure
        )
        PreconditionType.VPN_CHECK.name -> Precondition.VpnCheck(
            id = id,
            isEnabled = isEnabled,
            onFailureAction = onFailureAction,
            maxRetries = maxRetries,
            retryIntervalSeconds = retryIntervalSeconds,
            showNotificationOnFailure = showNotificationOnFailure
        )
        PreconditionType.HEALTH_CHECK.name -> Precondition.HealthCheck(
            id = id,
            isEnabled = isEnabled,
            targetUrl = targetUrl,
            method = method,
            headers = headers,
            expectedStatus = expectedStatus,
            onFailureAction = onFailureAction,
            maxRetries = maxRetries,
            retryIntervalSeconds = retryIntervalSeconds,
            showNotificationOnFailure = showNotificationOnFailure
        )
        PreconditionType.SIM_CHECK.name -> Precondition.SimCheck(
            id = id,
            isEnabled = isEnabled,
            requiredCarrier = requiredCarrier,
            onFailureAction = onFailureAction,
            maxRetries = maxRetries,
            retryIntervalSeconds = retryIntervalSeconds,
            showNotificationOnFailure = showNotificationOnFailure
        )
        PreconditionType.GPS_CHECK.name -> Precondition.GpsCheck(
            id = id,
            isEnabled = isEnabled,
            requireGpsEnabled = true,
            onFailureAction = onFailureAction,
            maxRetries = maxRetries,
            retryIntervalSeconds = retryIntervalSeconds,
            showNotificationOnFailure = showNotificationOnFailure
        )
        PreconditionType.CHAINED_CHECK.name -> {
            val nodesList = mutableListOf<WorkflowNode>()
            val connectionsList = mutableListOf<WorkflowConnection>()
            try {
                val json = JSONObject(pipelineJson.ifBlank { "{}" })
                val nodesArr = json.optJSONArray("nodes") ?: JSONArray()
                for (i in 0 until nodesArr.length()) {
                    val nodeObj = nodesArr.getJSONObject(i)
                    nodesList.add(
                        WorkflowNode(
                            id = nodeObj.getString("id"),
                            name = nodeObj.getString("name"),
                            type = nodeObj.getString("type"),
                            isEnabled = nodeObj.optBoolean("isEnabled", true),
                            configJson = nodeObj.optString("configJson", "{}")
                        )
                    )
                }
                val connArr = json.optJSONArray("connections") ?: JSONArray()
                for (i in 0 until connArr.length()) {
                    val connObj = connArr.getJSONObject(i)
                    connectionsList.add(
                        WorkflowConnection(
                            fromNodeId = connObj.getString("fromNodeId"),
                            toNodeId = connObj.getString("toNodeId"),
                            outputPort = connObj.optString("outputPort", "main")
                        )
                    )
                }
            } catch (_: Exception) {}
            Precondition.ChainCheck(
                id = id,
                isEnabled = isEnabled,
                nodes = nodesList,
                connections = connectionsList,
                onFailureAction = onFailureAction,
                maxRetries = maxRetries,
                retryIntervalSeconds = retryIntervalSeconds,
                showNotificationOnFailure = showNotificationOnFailure
            )
        }
        else -> Precondition.InternetCheck(
            id = id,
            isEnabled = isEnabled,
            onFailureAction = onFailureAction,
            maxRetries = maxRetries,
            retryIntervalSeconds = retryIntervalSeconds,
            showNotificationOnFailure = showNotificationOnFailure
        )
    }
}
