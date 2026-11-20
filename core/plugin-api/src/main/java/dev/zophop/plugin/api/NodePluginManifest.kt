package dev.zophop.plugin.api

import org.json.JSONArray
import org.json.JSONObject

enum class NodeCategory {
    TRIGGER,
    EXTRACTION,
    LOGIC,
    ACTION
}

sealed class ConfigFieldSpec(
    val key: String,
    val label: String,
    val tooltip: String?
) {
    class TextField(
        key: String,
        label: String,
        tooltip: String? = null,
        val placeholder: String? = null,
        val singleLine: Boolean = true,
        val minLines: Int = 1
    ) : ConfigFieldSpec(key, label, tooltip)

    class DropdownField(
        key: String,
        label: String,
        tooltip: String? = null,
        val options: List<Pair<String, String>>
    ) : ConfigFieldSpec(key, label, tooltip)

    class CodeTemplateField(
        key: String,
        label: String,
        tooltip: String? = null,
        val minLines: Int = 3
    ) : ConfigFieldSpec(key, label, tooltip)
}

data class NodePluginManifest(
    val typeId: String,
    val displayName: String,
    val description: String,
    val category: NodeCategory,
    val iconResName: String = "ic_node_plugin",
    val apiVersion: Int = CURRENT_API_VERSION,
    val requiredPermissions: List<String> = emptyList(),
    val requiredCredentialType: String? = null,
    val settingsIntentAction: String? = null,
    val configSchema: List<ConfigFieldSpec> = emptyList()
) {
    companion object {
        const val CURRENT_API_VERSION = 1

        fun fromJson(jsonStr: String): NodePluginManifest {
            val json = JSONObject(jsonStr)
            val typeId = json.getString("typeId")
            val displayName = json.getString("displayName")
            val description = json.optString("description", "")
            val category = try {
                NodeCategory.valueOf(json.optString("category", "ACTION"))
            } catch (_: Exception) {
                NodeCategory.ACTION
            }
            val iconResName = json.optString("iconResName", "ic_node_plugin")
            val apiVersion = json.optInt("apiVersion", CURRENT_API_VERSION)

            val fields = mutableListOf<ConfigFieldSpec>()
            val schemaArr = json.optJSONArray("configSchema") ?: JSONArray()
            for (i in 0 until schemaArr.length()) {
                val fObj = schemaArr.getJSONObject(i)
                val key = fObj.getString("key")
                val label = fObj.getString("label")
                val tooltip = if (fObj.has("tooltip")) fObj.getString("tooltip") else null
                when (fObj.optString("type", "TEXT")) {
                    "DROPDOWN" -> {
                        val optList = mutableListOf<Pair<String, String>>()
                        val optsArr = fObj.optJSONArray("options") ?: JSONArray()
                        for (j in 0 until optsArr.length()) {
                            val oObj = optsArr.getJSONObject(j)
                            optList.add(oObj.getString("value") to oObj.getString("label"))
                        }
                        fields.add(ConfigFieldSpec.DropdownField(key, label, tooltip, optList))
                    }
                    "TEMPLATE" -> {
                        fields.add(ConfigFieldSpec.CodeTemplateField(key, label, tooltip, fObj.optInt("minLines", 3)))
                    }
                    else -> {
                        fields.add(
                            ConfigFieldSpec.TextField(
                                key, label, tooltip,
                                placeholder = if (fObj.has("placeholder")) fObj.getString("placeholder") else null,
                                singleLine = fObj.optBoolean("singleLine", true),
                                minLines = fObj.optInt("minLines", 1)
                            )
                        )
                    }
                }
            }

            val reqPerms = mutableListOf<String>()
            val permsArr = json.optJSONArray("requiredPermissions") ?: JSONArray()
            for (i in 0 until permsArr.length()) {
                reqPerms.add(permsArr.getString(i))
            }
            val requiredCredentialType = if (json.has("requiredCredentialType")) json.getString("requiredCredentialType") else null
            val settingsIntentAction = if (json.has("settingsIntentAction")) json.getString("settingsIntentAction") else null

            return NodePluginManifest(
                typeId, displayName, description, category, iconResName, apiVersion,
                requiredPermissions = reqPerms,
                requiredCredentialType = requiredCredentialType,
                settingsIntentAction = settingsIntentAction,
                configSchema = fields
            )
        }

        fun parseManifests(jsonStr: String): List<NodePluginManifest> {
            val trimmed = jsonStr.trim()
            if (trimmed.isEmpty()) return emptyList()
            if (trimmed.startsWith("[")) {
                val arr = JSONArray(trimmed)
                val result = mutableListOf<NodePluginManifest>()
                for (i in 0 until arr.length()) {
                    result.add(fromJson(arr.getJSONObject(i).toString()))
                }
                return result
            }
            val obj = JSONObject(trimmed)
            if (obj.has("nodes")) {
                val arr = obj.getJSONArray("nodes")
                val result = mutableListOf<NodePluginManifest>()
                for (i in 0 until arr.length()) {
                    result.add(fromJson(arr.getJSONObject(i).toString()))
                }
                return result
            }
            return listOf(fromJson(trimmed))
        }
    }

    fun toJson(): JSONObject {
        val json = JSONObject()
        json.put("typeId", typeId)
        json.put("displayName", displayName)
        json.put("description", description)
        json.put("category", category.name)
        json.put("iconResName", iconResName)
        json.put("apiVersion", apiVersion)
        
        val permissionsArr = JSONArray()
        requiredPermissions.forEach { permissionsArr.put(it) }
        json.put("requiredPermissions", permissionsArr)
        
        json.put("requiredCredentialType", requiredCredentialType)
        json.put("settingsIntentAction", settingsIntentAction)

        val schemaArr = JSONArray()
        for (field in configSchema) {
            val fObj = JSONObject()
            fObj.put("key", field.key)
            fObj.put("label", field.label)
            if (field.tooltip != null) fObj.put("tooltip", field.tooltip)

            when (field) {
                is ConfigFieldSpec.DropdownField -> {
                    fObj.put("type", "DROPDOWN")
                    val optsArr = JSONArray()
                    for ((v, l) in field.options) {
                        optsArr.put(JSONObject().apply { put("value", v); put("label", l) })
                    }
                    fObj.put("options", optsArr)
                }
                is ConfigFieldSpec.CodeTemplateField -> {
                    fObj.put("type", "TEMPLATE")
                    fObj.put("minLines", field.minLines)
                }
                is ConfigFieldSpec.TextField -> {
                    fObj.put("type", "TEXT")
                    if (field.placeholder != null) fObj.put("placeholder", field.placeholder)
                    fObj.put("singleLine", field.singleLine)
                    fObj.put("minLines", field.minLines)
                }
            }
            schemaArr.put(fObj)
        }
        json.put("configSchema", schemaArr)
        return json
    }
}
