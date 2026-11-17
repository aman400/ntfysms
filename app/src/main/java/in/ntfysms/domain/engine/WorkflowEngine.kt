package `in`.ntfysms.domain.engine

import android.content.Context
import `in`.ntfysms.data.CredentialRepository
import `in`.ntfysms.domain.model.NodeType
import `in`.ntfysms.domain.model.Workflow
import `in`.ntfysms.domain.model.WorkflowNode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

sealed class WorkflowExecutionResult {
    data class Success(
        val workflowId: String,
        val logs: List<String>
    ) : WorkflowExecutionResult()

    data class PreconditionFailed(
        val workflowId: String,
        val nodeId: String,
        val nodeName: String,
        val reason: String,
        val logs: List<String>
    ) : WorkflowExecutionResult()

    data class Failed(
        val workflowId: String,
        val nodeId: String?,
        val reason: String,
        val logs: List<String>
    ) : WorkflowExecutionResult()
}

enum class NodeStepState {
    PENDING,
    EXECUTING,
    SUCCESS,
    FAILED,
    SKIPPED
}

data class NodeExecutionStep(
    val nodeId: String,
    val nodeName: String,
    val nodeType: NodeType,
    val state: NodeStepState = NodeStepState.PENDING,
    val logMessage: String = "",
    val errorReason: String? = null
)

open class WorkflowEngine(
    private val preconditionEvaluator: PreconditionEvaluator,
    private val credentialRepository: CredentialRepository? = null,
    private val executorFactory: NodeExecutorFactory = NodeExecutorFactory(credentialRepository = credentialRepository)
) {

    open suspend fun executeWorkflow(
        workflow: Workflow,
        context: ExecutionContext,
        appContext: Context?
    ): WorkflowExecutionResult = withContext(Dispatchers.IO) {
        if (!workflow.isEnabled) {
            return@withContext WorkflowExecutionResult.Failed(
                workflowId = workflow.id,
                nodeId = null,
                reason = "Workflow '${workflow.name}' is disabled",
                logs = listOf("Workflow is disabled")
            )
        }

        val logs = mutableListOf<String>()
        logs.add("Started executing workflow '${workflow.name}' (${workflow.id})")

        // Find root trigger nodes or entry nodes
        var triggerNodes = workflow.nodes.filter {
            it.type == NodeType.SMS_TRIGGER || it.type == NodeType.NOTIFICATION_TRIGGER || it.type == NodeType.MANUAL_TRIGGER
        }
        if (triggerNodes.isEmpty()) {
            // Fallback for pipelines (e.g. Chained Precondition pipelines) without explicit trigger nodes:
            // Use root entry nodes (nodes with no incoming connections) or the first node.
            triggerNodes = workflow.nodes.filter { node ->
                workflow.connections.none { conn -> conn.toNodeId == node.id }
            }.ifEmpty { workflow.nodes.take(1) }
        }

        if (triggerNodes.isEmpty()) {
            return@withContext WorkflowExecutionResult.Failed(
                workflowId = workflow.id,
                nodeId = null,
                reason = "No trigger or entry nodes found in workflow",
                logs = logs
            )
        }

        val visitedNodeIds = mutableSetOf<String>()
        val queue = ArrayDeque<Pair<WorkflowNode, String>>() // (Node, inputPort)

        triggerNodes.filter { it.isEnabled }.forEach { trigger ->
            queue.add(trigger to "main")
        }

        while (queue.isNotEmpty()) {
            val (currentNode, inputPort) = queue.removeFirst()
            if (visitedNodeIds.contains(currentNode.id)) continue
            visitedNodeIds.add(currentNode.id)

            logs.add("Evaluating Node '${currentNode.name}' (${currentNode.type})")

            if (!currentNode.isEnabled) {
                logs.add("Node '${currentNode.name}' is disabled, skipping.")
                continue
            }

            // 1. Evaluate Node Preconditions
            val preconditionResult = preconditionEvaluator.evaluateAll(currentNode.preconditions)
            if (preconditionResult is PreconditionResult.Failed) {
                val failReason = preconditionResult.reason
                logs.add("⏳ Precondition Failed for node '${currentNode.name}': $failReason")
                return@withContext WorkflowExecutionResult.PreconditionFailed(
                    workflowId = workflow.id,
                    nodeId = currentNode.id,
                    nodeName = currentNode.name,
                    reason = failReason,
                    logs = logs
                )
            }

            // 2. Execute Node Strategy
            val executor = executorFactory.getExecutor(currentNode.type)
            val result = executor.execute(currentNode, context, appContext)

            when (result) {
                is NodeExecutionResult.Failed -> {
                    logs.add("❌ Node '${currentNode.name}' failed: ${result.reason}")
                    return@withContext WorkflowExecutionResult.Failed(
                        workflowId = workflow.id,
                        nodeId = currentNode.id,
                        reason = result.reason,
                        logs = logs
                    )
                }
                is NodeExecutionResult.Success -> {
                    logs.add("✅ Node '${currentNode.name}': ${result.logMessage}")

                    // Traverse outgoing connections
                    val outgoing = workflow.connections.filter {
                        it.fromNodeId == currentNode.id && it.outputPort == result.outputPort
                    }

                    for (conn in outgoing) {
                        val nextNode = workflow.nodes.find { it.id == conn.toNodeId }
                        if (nextNode != null) {
                            queue.add(nextNode to conn.inputPort)
                        }
                    }
                }
            }
        }

        logs.add("Completed workflow '${workflow.name}' successfully.")
        WorkflowExecutionResult.Success(workflowId = workflow.id, logs = logs)
    }

    open suspend fun executeWorkflowWithProgress(
        workflow: Workflow,
        context: ExecutionContext,
        appContext: Context?,
        onStepUpdate: (List<NodeExecutionStep>) -> Unit
    ): WorkflowExecutionResult = withContext(Dispatchers.IO) {
        val steps = workflow.nodes.map {
            NodeExecutionStep(
                nodeId = it.id,
                nodeName = it.name,
                nodeType = it.type,
                state = NodeStepState.PENDING
            )
        }.toMutableList()

        fun updateStep(nodeId: String, newState: NodeStepState, message: String = "", error: String? = null) {
            val idx = steps.indexOfFirst { it.nodeId == nodeId }
            if (idx != -1) {
                steps[idx] = steps[idx].copy(state = newState, logMessage = message, errorReason = error)
                onStepUpdate(steps.toList())
            }
        }

        onStepUpdate(steps.toList())

        val logs = mutableListOf<String>()
        logs.add("Started executing workflow '${workflow.name}' (${workflow.id})")

        var triggerNodes = workflow.nodes.filter {
            it.type == NodeType.SMS_TRIGGER || it.type == NodeType.NOTIFICATION_TRIGGER || it.type == NodeType.MANUAL_TRIGGER
        }
        if (triggerNodes.isEmpty()) {
            // Fallback for pipelines (e.g. Chained Precondition pipelines) without explicit trigger nodes:
            // Use root entry nodes (nodes with no incoming connections) or the first node.
            triggerNodes = workflow.nodes.filter { node ->
                workflow.connections.none { conn -> conn.toNodeId == node.id }
            }.ifEmpty { workflow.nodes.take(1) }
        }

        if (triggerNodes.isEmpty()) {
            steps.indices.forEach { steps[it] = steps[it].copy(state = NodeStepState.SKIPPED) }
            onStepUpdate(steps.toList())
            return@withContext WorkflowExecutionResult.Failed(
                workflowId = workflow.id,
                nodeId = null,
                reason = "No trigger or entry nodes found in workflow",
                logs = logs
            )
        }

        val visitedNodeIds = mutableSetOf<String>()
        val queue = ArrayDeque<Pair<WorkflowNode, String>>()

        triggerNodes.filter { it.isEnabled }.forEach { trigger ->
            queue.add(trigger to "main")
        }

        while (queue.isNotEmpty()) {
            val (currentNode, inputPort) = queue.removeFirst()
            if (visitedNodeIds.contains(currentNode.id)) continue
            visitedNodeIds.add(currentNode.id)

            updateStep(currentNode.id, NodeStepState.EXECUTING, message = "Executing node...")
            kotlinx.coroutines.delay(200)

            if (!currentNode.isEnabled) {
                logs.add("Node '${currentNode.name}' is disabled, skipping.")
                updateStep(currentNode.id, NodeStepState.SKIPPED, message = "Node disabled")
                continue
            }

            // 1. Evaluate Preconditions
            val preconditionResult = preconditionEvaluator.evaluateAll(currentNode.preconditions) { chainSteps ->
                val activeStep = chainSteps.lastOrNull { it.state == NodeStepState.EXECUTING || it.state == NodeStepState.SUCCESS || it.state == NodeStepState.FAILED }
                val stepDesc = activeStep?.let { " [Chained: ${it.nodeName} (${it.state})]" } ?: ""
                updateStep(currentNode.id, NodeStepState.EXECUTING, message = "Evaluating preconditions$stepDesc")
            }
            if (preconditionResult is PreconditionResult.Failed) {
                val failReason = preconditionResult.reason
                logs.add("⏳ Precondition Failed for node '${currentNode.name}': $failReason")
                updateStep(currentNode.id, NodeStepState.FAILED, error = failReason, message = "Precondition failed: $failReason")

                steps.forEachIndexed { i, s ->
                    if (s.state == NodeStepState.PENDING || s.state == NodeStepState.EXECUTING) {
                        if (s.nodeId != currentNode.id) {
                            steps[i] = steps[i].copy(state = NodeStepState.SKIPPED, logMessage = "Skipped due to upstream failure")
                        }
                    }
                }
                onStepUpdate(steps.toList())

                return@withContext WorkflowExecutionResult.PreconditionFailed(
                    workflowId = workflow.id,
                    nodeId = currentNode.id,
                    nodeName = currentNode.name,
                    reason = failReason,
                    logs = logs
                )
            }

            // 2. Execute Node
            val executor = executorFactory.getExecutor(currentNode.type)
            val result = executor.execute(currentNode, context, appContext)

            when (result) {
                is NodeExecutionResult.Failed -> {
                    logs.add("❌ Node '${currentNode.name}' failed: ${result.reason}")
                    updateStep(currentNode.id, NodeStepState.FAILED, error = result.reason, message = result.reason)

                    steps.forEachIndexed { i, s ->
                        if (s.state == NodeStepState.PENDING) {
                            steps[i] = steps[i].copy(state = NodeStepState.SKIPPED, logMessage = "Skipped due to upstream failure")
                        }
                    }
                    onStepUpdate(steps.toList())

                    return@withContext WorkflowExecutionResult.Failed(
                        workflowId = workflow.id,
                        nodeId = currentNode.id,
                        reason = result.reason,
                        logs = logs
                    )
                }
                is NodeExecutionResult.Success -> {
                    logs.add("✅ Node '${currentNode.name}': ${result.logMessage}")
                    updateStep(currentNode.id, NodeStepState.SUCCESS, message = result.logMessage)

                    val outgoing = workflow.connections.filter {
                        it.fromNodeId == currentNode.id && it.outputPort == result.outputPort
                    }
                    for (conn in outgoing) {
                        val nextNode = workflow.nodes.find { it.id == conn.toNodeId }
                        if (nextNode != null) {
                            queue.add(nextNode to conn.inputPort)
                        }
                    }
                }
            }
        }

        logs.add("Completed workflow '${workflow.name}' successfully.")
        WorkflowExecutionResult.Success(workflowId = workflow.id, logs = logs)
    }
}
