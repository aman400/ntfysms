package `in`.ntfysms.domain.engine.executors

import android.content.Context
import `in`.ntfysms.domain.engine.ExecutionContext
import `in`.ntfysms.domain.model.WorkflowNode

/**
 * Sealed class representing the result of executing a single workflow node.
 */
sealed class NodeExecutionResult {
    /**
     * Execution succeeded.
     * @param outputPort The target connection output port ("main", "true", "false").
     * @param logMessage Detailed execution log message.
     */
    data class Success(
        val outputPort: String = "main",
        val logMessage: String = "Node executed successfully"
    ) : NodeExecutionResult()

    /**
     * Execution failed.
     * @param reason Failure description or error message.
     */
    data class Failed(
        val reason: String
    ) : NodeExecutionResult()
}

/**
 * Interface implemented by all node execution strategies.
 */
interface NodeExecutor {
    /**
     * Executes node logic against the provided execution context.
     * @param node Workflow node metadata and configuration JSON.
     * @param context Dynamic execution context holding variables.
     * @param appContext Android application context for system services.
     */
    suspend fun execute(
        node: WorkflowNode,
        context: ExecutionContext,
        appContext: Context?
    ): NodeExecutionResult
}
