package dev.zophop.domain.engine.executors

import android.content.Context
import dev.zophop.domain.engine.ExecutionContext
import dev.zophop.domain.model.WorkflowNode
import org.json.JSONObject
import java.util.regex.Pattern

/**
 * Strategy executor for [NodeType.CONDITION].
 * Evaluates variable values against operators (`EQUALS`, `NOT_EQUALS`, `CONTAINS`, `NOT_CONTAINS`, `REGEX_MATCH`, `NOT_EMPTY`, `IS_EMPTY`, `GREATER_THAN`, `LESS_THAN`).
 * Returns [NodeExecutionResult.Success] on pass or [NodeExecutionResult.Failed] on failure.
 */
class ConditionExecutor : NodeExecutor {
    override suspend fun execute(node: WorkflowNode, context: ExecutionContext, appContext: Context?): NodeExecutionResult {
        val config = try { JSONObject(node.configJson) } catch (_: Exception) { JSONObject() }
        val variableName = config.optString("variable", "code").ifEmpty { config.optString("variable", "body") }.trim()
        val operator = config.optString("operator", "NOT_EMPTY").trim()
        val targetValue = config.optString("value", "").trim()

        val actualValue = context.getString(variableName, "")

        val conditionMet = when (operator.uppercase()) {
            "EQUALS" -> actualValue.equals(targetValue, ignoreCase = true)
            "NOT_EQUALS" -> !actualValue.equals(targetValue, ignoreCase = true)
            "CONTAINS" -> actualValue.contains(targetValue, ignoreCase = true)
            "NOT_CONTAINS" -> !actualValue.contains(targetValue, ignoreCase = true)
            "REGEX_MATCH" -> try { Pattern.compile(targetValue).matcher(actualValue).find() } catch (_: Exception) { false }
            "NOT_EMPTY" -> actualValue.isNotBlank()
            "IS_EMPTY" -> actualValue.isBlank()
            "GREATER_THAN" -> (actualValue.toDoubleOrNull() ?: 0.0) > (targetValue.toDoubleOrNull() ?: 0.0)
            "LESS_THAN" -> (actualValue.toDoubleOrNull() ?: 0.0) < (targetValue.toDoubleOrNull() ?: 0.0)
            else -> actualValue.isNotBlank()
        }

        val targetStr = if (targetValue.isNotBlank()) " '$targetValue'" else ""
        return if (conditionMet) {
            NodeExecutionResult.Success(outputPort = "main", logMessage = "Condition '$variableName $operator$targetStr' passed (TRUE)")
        } else {
            NodeExecutionResult.Failed("Condition '$variableName $operator$targetStr' failed (FALSE, Actual: '$actualValue')")
        }
    }
}
