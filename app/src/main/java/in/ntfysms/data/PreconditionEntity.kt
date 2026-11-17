package `in`.ntfysms.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import `in`.ntfysms.domain.model.NodeType
import `in`.ntfysms.domain.model.Precondition
import `in`.ntfysms.domain.model.PreconditionType
import `in`.ntfysms.domain.model.WorkflowConnection
import `in`.ntfysms.domain.model.WorkflowNode
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
    val updatedAt: Long = System.currentTimeMillis()
) {
    fun toDomain(): Precondition {
        return when (type) {
            PreconditionType.INTERNET_CHECK.name -> Precondition.InternetCheck(isEnabled = isEnabled)
            PreconditionType.VPN_CHECK.name -> Precondition.VpnCheck(isEnabled = isEnabled)
            PreconditionType.HEALTH_CHECK.name -> Precondition.HealthCheck(
                isEnabled = isEnabled,
                targetUrl = targetUrl,
                method = method,
                headers = headers,
                expectedStatus = expectedStatus
            )
            PreconditionType.SIM_CHECK.name -> Precondition.SimCheck(
                isEnabled = isEnabled,
                requiredCarrier = requiredCarrier
            )
            PreconditionType.GPS_CHECK.name -> Precondition.GpsCheck(
                isEnabled = isEnabled,
                requireGpsEnabled = true
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
                                type = NodeType.valueOf(nodeObj.getString("type")),
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
                    isEnabled = isEnabled,
                    nodes = nodesList,
                    connections = connectionsList
                )
            }
            else -> Precondition.InternetCheck(isEnabled = isEnabled)
        }
    }
}
