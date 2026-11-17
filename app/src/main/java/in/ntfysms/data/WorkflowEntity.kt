package `in`.ntfysms.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import `in`.ntfysms.domain.model.NodeType
import `in`.ntfysms.domain.model.Precondition
import `in`.ntfysms.domain.model.PreconditionType
import `in`.ntfysms.domain.model.Workflow
import `in`.ntfysms.domain.model.WorkflowConnection
import `in`.ntfysms.domain.model.WorkflowNode
import org.json.JSONArray
import org.json.JSONObject

@Entity(tableName = "workflows")
data class WorkflowEntity(
    @PrimaryKey val id: String,
    val name: String,
    val description: String,
    val isEnabled: Boolean,
    val graphJson: String,
    val createdAt: Long,
    val updatedAt: Long
) {
    fun toDomain(): Workflow {
        val nodesList = mutableListOf<WorkflowNode>()
        val connectionsList = mutableListOf<WorkflowConnection>()

        try {
            val json = JSONObject(graphJson)

            val nodesArr = json.optJSONArray("nodes") ?: JSONArray()
            for (i in 0 until nodesArr.length()) {
                val nodeObj = nodesArr.getJSONObject(i)
                val id = nodeObj.getString("id")
                val name = nodeObj.getString("name")
                val typeStr = nodeObj.getString("type")
                val isEnabled = nodeObj.optBoolean("isEnabled", true)
                val configJson = nodeObj.optString("configJson", "{}")

                val preconditions = mutableListOf<Precondition>()
                val preArr = nodeObj.optJSONArray("preconditions") ?: JSONArray()
                for (j in 0 until preArr.length()) {
                    val pObj = preArr.getJSONObject(j)
                    val pTypeStr = pObj.getString("type")
                    val isPEnabled = pObj.optBoolean("isEnabled", true)

                    val prec = when (PreconditionType.valueOf(pTypeStr)) {
                        PreconditionType.INTERNET_CHECK -> Precondition.InternetCheck(isPEnabled)
                        PreconditionType.VPN_CHECK -> Precondition.VpnCheck(isPEnabled)
                        PreconditionType.HEALTH_CHECK -> Precondition.HealthCheck(
                            isEnabled = isPEnabled,
                            targetUrl = pObj.optString("targetUrl", ""),
                            method = pObj.optString("method", "GET"),
                            headers = pObj.optString("headers", ""),
                            expectedStatus = pObj.optInt("expectedStatus", 200)
                        )
                        PreconditionType.SIM_CHECK -> Precondition.SimCheck(
                            isEnabled = isPEnabled,
                            requiredCarrier = pObj.optString("requiredCarrier", ""),
                            requiredSlot = pObj.optInt("requiredSlot", -1)
                        )
                        PreconditionType.GPS_CHECK -> Precondition.GpsCheck(
                            isEnabled = isPEnabled,
                            requireGpsEnabled = pObj.optBoolean("requireGpsEnabled", true)
                        )
                        PreconditionType.CHAINED_CHECK -> {
                            val nodesList = mutableListOf<WorkflowNode>()
                            val connectionsList = mutableListOf<WorkflowConnection>()
                            val pJsonStr = pObj.optString("pipelineJson", "")
                            if (pJsonStr.isNotBlank()) {
                                try {
                                    val pJson = JSONObject(pJsonStr)
                                    val nodesArr = pJson.optJSONArray("nodes") ?: JSONArray()
                                    for (k in 0 until nodesArr.length()) {
                                        val nObj = nodesArr.getJSONObject(k)
                                        nodesList.add(
                                            WorkflowNode(
                                                id = nObj.getString("id"),
                                                name = nObj.getString("name"),
                                                type = NodeType.valueOf(nObj.getString("type")),
                                                isEnabled = nObj.optBoolean("isEnabled", true),
                                                configJson = nObj.optString("configJson", "{}")
                                            )
                                        )
                                    }
                                    val connArr = pJson.optJSONArray("connections") ?: JSONArray()
                                    for (k in 0 until connArr.length()) {
                                        val cObj = connArr.getJSONObject(k)
                                        connectionsList.add(
                                            WorkflowConnection(
                                                fromNodeId = cObj.getString("fromNodeId"),
                                                toNodeId = cObj.getString("toNodeId"),
                                                outputPort = cObj.optString("outputPort", "main"),
                                                inputPort = cObj.optString("inputPort", "main")
                                            )
                                        )
                                    }
                                } catch (_: Exception) {}
                            }
                            Precondition.ChainCheck(
                                isEnabled = isPEnabled,
                                nodes = nodesList,
                                connections = connectionsList
                            )
                        }
                    }
                    preconditions.add(prec)
                }

                nodesList.add(
                    WorkflowNode(
                        id = id,
                        name = name,
                        type = NodeType.valueOf(typeStr),
                        isEnabled = isEnabled,
                        configJson = configJson,
                        preconditions = preconditions
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
                        outputPort = connObj.optString("outputPort", "main"),
                        inputPort = connObj.optString("inputPort", "main")
                    )
                )
            }
        } catch (_: Exception) {}

        return Workflow(
            id = id,
            name = name,
            description = description,
            isEnabled = isEnabled,
            nodes = nodesList,
            connections = connectionsList,
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }

    companion object {
        fun fromDomain(workflow: Workflow): WorkflowEntity {
            val json = JSONObject()

            val nodesArr = JSONArray()
            for (node in workflow.nodes) {
                val nodeObj = JSONObject().apply {
                    put("id", node.id)
                    put("name", node.name)
                    put("type", node.type.name)
                    put("isEnabled", node.isEnabled)
                    put("configJson", node.configJson)

                    val preArr = JSONArray()
                    for (prec in node.preconditions) {
                        val pObj = JSONObject().apply {
                            put("type", prec.type.name)
                            when (prec) {
                                is Precondition.InternetCheck -> put("isEnabled", prec.isEnabled)
                                is Precondition.VpnCheck -> put("isEnabled", prec.isEnabled)
                                is Precondition.HealthCheck -> {
                                    put("isEnabled", prec.isEnabled)
                                    put("targetUrl", prec.targetUrl)
                                    put("method", prec.method)
                                    put("headers", prec.headers)
                                    put("expectedStatus", prec.expectedStatus)
                                }
                                is Precondition.SimCheck -> {
                                    put("isEnabled", prec.isEnabled)
                                    put("requiredCarrier", prec.requiredCarrier)
                                    put("requiredSlot", prec.requiredSlot)
                                }
                                is Precondition.GpsCheck -> {
                                    put("isEnabled", prec.isEnabled)
                                    put("requireGpsEnabled", prec.requireGpsEnabled)
                                }
                                is Precondition.ChainCheck -> {
                                    put("isEnabled", prec.isEnabled)
                                    val pipelineObj = JSONObject().apply {
                                        val nodesArr = JSONArray()
                                        prec.nodes.forEach { node ->
                                            nodesArr.put(JSONObject().apply {
                                                put("id", node.id)
                                                put("name", node.name)
                                                put("type", node.type.name)
                                                put("isEnabled", node.isEnabled)
                                                put("configJson", node.configJson)
                                            })
                                        }
                                        put("nodes", nodesArr)

                                        val connArr = JSONArray()
                                        prec.connections.forEach { conn ->
                                            connArr.put(JSONObject().apply {
                                                put("fromNodeId", conn.fromNodeId)
                                                put("toNodeId", conn.toNodeId)
                                                put("outputPort", conn.outputPort)
                                                put("inputPort", conn.inputPort)
                                            })
                                        }
                                        put("connections", connArr)
                                    }
                                    put("pipelineJson", pipelineObj.toString())
                                }
                            }
                        }
                        preArr.put(pObj)
                    }
                    put("preconditions", preArr)
                }
                nodesArr.put(nodeObj)
            }

            val connArr = JSONArray()
            for (conn in workflow.connections) {
                val connObj = JSONObject().apply {
                    put("fromNodeId", conn.fromNodeId)
                    put("toNodeId", conn.toNodeId)
                    put("outputPort", conn.outputPort)
                    put("inputPort", conn.inputPort)
                }
                connArr.put(connObj)
            }

            json.put("nodes", nodesArr)
            json.put("connections", connArr)

            return WorkflowEntity(
                id = workflow.id,
                name = workflow.name,
                description = workflow.description,
                isEnabled = workflow.isEnabled,
                graphJson = json.toString(),
                createdAt = workflow.createdAt,
                updatedAt = workflow.updatedAt
            )
        }
    }
}

@Entity(tableName = "workflow_execution_logs")
data class WorkflowExecutionLogEntity(
    @PrimaryKey val id: String,
    val workflowId: String,
    val status: String,
    val timestamp: Long,
    val messageId: String?,
    val logs: String,
    val errorReason: String?
)
