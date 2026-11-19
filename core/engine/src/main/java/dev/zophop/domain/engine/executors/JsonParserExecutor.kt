package dev.zophop.domain.engine.executors

import android.content.Context
import dev.zophop.domain.engine.ExecutionContext
import dev.zophop.domain.model.WorkflowNode
import org.json.JSONArray
import org.json.JSONObject

/**
 * Strategy executor for [NodeType.JSON_PARSER].
 * Extracts values from JSON strings using property path queries (e.g. `data.user.id`, `items[0].code`)
 * and converts them to target types (`STRING`, `NUMBER`, `DOUBLE`, `BOOLEAN`, `JSON_STRING`).
 */
class JsonParserExecutor : NodeExecutor {

    private fun extractJsonPath(root: Any, path: String): Any? {
        if (path.isBlank()) return root
        val segments = path.split(".")
        var current: Any? = root

        for (segment in segments) {
            if (current == null) return null
            val key = segment.trim()
            if (key.isEmpty()) continue

            val arrayIndexMatch = Regex("^(.+?)\\[(\\d+)]$").find(key)
            if (arrayIndexMatch != null) {
                val propName = arrayIndexMatch.groupValues[1]
                val index = arrayIndexMatch.groupValues[2].toIntOrNull() ?: 0

                if (propName.isNotEmpty()) {
                    current = when (val c = current) {
                        is JSONObject -> if (c.has(propName)) c.get(propName) else null
                        else -> null
                    }
                }

                current = when (val c = current) {
                    is JSONArray -> if (index in 0 until c.length()) c.get(index) else null
                    else -> null
                }
            } else {
                current = when (val c = current) {
                    is JSONObject -> if (c.has(key)) c.get(key) else null
                    is JSONArray -> {
                        val index = key.toIntOrNull()
                        if (index != null && index in 0 until c.length()) c.get(index) else null
                    }
                    else -> null
                }
            }
        }

        return if (current == JSONObject.NULL) null else current
    }

    private fun convertType(value: Any?, targetType: String, defaultValue: String): Any? {
        if (value == null || value == JSONObject.NULL) {
            if (defaultValue.isNotBlank()) {
                return when (targetType.uppercase()) {
                    "NUMBER", "INTEGER", "INT" -> defaultValue.toIntOrNull() ?: defaultValue.toDoubleOrNull()
                    "DOUBLE", "FLOAT" -> defaultValue.toDoubleOrNull()
                    "BOOLEAN", "BOOL" -> defaultValue.lowercase() in listOf("true", "1", "yes", "on")
                    else -> defaultValue
                }
            }
            return null
        }

        return when (targetType.uppercase()) {
            "STRING" -> value.toString()
            "NUMBER", "INTEGER", "INT" -> {
                when (value) {
                    is Number -> value.toInt()
                    is Boolean -> if (value) 1 else 0
                    else -> value.toString().trim().toIntOrNull()
                        ?: value.toString().trim().toDoubleOrNull()?.toInt()
                        ?: defaultValue.toIntOrNull()
                }
            }
            "DOUBLE", "FLOAT" -> {
                when (value) {
                    is Number -> value.toDouble()
                    is Boolean -> if (value) 1.0 else 0.0
                    else -> value.toString().trim().toDoubleOrNull() ?: defaultValue.toDoubleOrNull()
                }
            }
            "BOOLEAN", "BOOL" -> {
                when (value) {
                    is Boolean -> value
                    is Number -> value.toDouble() != 0.0
                    else -> {
                        val str = value.toString().trim().lowercase()
                        if (str in listOf("true", "1", "yes", "on")) true
                        else if (str in listOf("false", "0", "no", "off")) false
                        else defaultValue.toBooleanStrictOrNull() ?: false
                    }
                }
            }
            "JSON_STRING" -> {
                when (value) {
                    is JSONObject -> value.toString()
                    is JSONArray -> value.toString()
                    else -> value.toString()
                }
            }
            else -> { // "AUTO" / "RAW"
                when (value) {
                    is JSONObject, is JSONArray -> value.toString()
                    else -> value
                }
            }
        }
    }

    override suspend fun execute(node: WorkflowNode, context: ExecutionContext, appContext: Context?): NodeExecutionResult {
        val config = try { JSONObject(node.configJson) } catch (_: Exception) { JSONObject() }
        val jsonKey = config.optString("jsonKey", "code").trim()
        val outputKey = config.optString("outputKey", jsonKey).ifEmpty { jsonKey }.trim()
        val targetType = config.optString("targetType", "AUTO")
        val defaultValue = config.optString("defaultValue", "")
        val inputVariable = config.optString("inputVariable", "").trim()

        val rawInput = when {
            inputVariable.isNotBlank() && context.get(inputVariable) != null -> context.getString(inputVariable, "")
            context.getString("http_response", "").isNotBlank() -> context.getString("http_response", "")
            context.getString("body", "").isNotBlank() -> context.getString("body", "")
            else -> context.getString("http_response", "")
        }

        if (rawInput.isBlank()) {
            return NodeExecutionResult.Failed("Input JSON payload is empty")
        }

        return try {
            val trimmedInput = rawInput.trim()
            val jsonRoot: Any = when {
                trimmedInput.startsWith("[") -> JSONArray(trimmedInput)
                trimmedInput.startsWith("{") -> JSONObject(trimmedInput)
                else -> JSONObject(mapOf("value" to trimmedInput))
            }

            val extracted = extractJsonPath(jsonRoot, jsonKey)
            val converted = convertType(extracted, targetType, defaultValue)

            if (converted != null) {
                context.set(outputKey, converted)
                NodeExecutionResult.Success(logMessage = "Parsed '$jsonKey' [$targetType] -> '$outputKey' = $converted")
            } else if (defaultValue.isNotBlank()) {
                context.set(outputKey, defaultValue)
                NodeExecutionResult.Success(logMessage = "Parsed '$jsonKey' null/missing, used default -> '$outputKey' = $defaultValue")
            } else {
                NodeExecutionResult.Failed("JSON path '$jsonKey' not found or null")
            }
        } catch (e: Exception) {
            NodeExecutionResult.Failed("JSON parsing error for '$jsonKey': ${e.message}")
        }
    }
}
