package `in`.ntfysms.domain.engine

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import `in`.ntfysms.data.CredentialRepository
import `in`.ntfysms.domain.engine.executors.NodeExecutionResult
import `in`.ntfysms.domain.model.NodeType
import `in`.ntfysms.domain.model.Workflow
import `in`.ntfysms.domain.model.WorkflowNode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
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

    data class Cancelled(
        val workflowId: String,
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

    private fun postProgressNotification(
        context: Context?,
        execContext: ExecutionContext?,
        notificationId: Int,
        workflowName: String,
        statusText: String,
        isFinished: Boolean = false,
        isSuccess: Boolean = true
    ) {
        val showNotif = execContext?.get("show_workflow_notifications") as? Boolean ?: true
        if (!showNotif) return

        val ctx = context ?: return
        try {
            val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            val channelId = "ntfy_sms_execution_channel"

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    channelId,
                    "Workflow Execution Progress",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Shows live status notifications during workflow execution"
                    setShowBadge(false)
                }
                nm.createNotificationChannel(channel)
            }

            if (isFinished) {
                val title = when {
                    statusText.contains("Cancelled", ignoreCase = true) -> "🛑 Execution Cancelled: $workflowName"
                    isSuccess -> "✅ Workflow Executed: $workflowName"
                    else -> "❌ Workflow Failed: $workflowName"
                }
                val notification = NotificationCompat.Builder(ctx, channelId)
                    .setSmallIcon(android.R.drawable.ic_dialog_info)
                    .setContentTitle(title)
                    .setContentText(statusText)
                    .setStyle(NotificationCompat.BigTextStyle().bigText(statusText))
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .setAutoCancel(true)
                    .setTimeoutAfter(8000)
                    .build()

                nm.notify(notificationId, notification)
            } else {
                val cancelIntent = Intent("in.ntfysms.ACTION_CANCEL_WORKFLOW").apply {
                    putExtra("workflow_id", execContext?.getString("workflow_id", ""))
                }
                val pendingCancelIntent = PendingIntent.getBroadcast(
                    ctx,
                    notificationId,
                    cancelIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                val notification = NotificationCompat.Builder(ctx, channelId)
                    .setSmallIcon(android.R.drawable.ic_popup_sync)
                    .setContentTitle("⚡ Executing Workflow: $workflowName")
                    .setContentText(statusText)
                    .setStyle(NotificationCompat.BigTextStyle().bigText(statusText))
                    .setPriority(NotificationCompat.PRIORITY_LOW)
                    .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Cancel Execution", pendingCancelIntent)
                    .setOngoing(true)
                    .build()

                nm.notify(notificationId, notification)
            }
        } catch (_: Exception) {}
    }

    open suspend fun executeWorkflow(
        workflow: Workflow,
        context: ExecutionContext,
        appContext: Context?,
        startNodeId: String? = null
    ): WorkflowExecutionResult = withContext(Dispatchers.IO) {
        val notifId = workflow.id.hashCode()
        if (!workflow.isEnabled) {
            return@withContext WorkflowExecutionResult.Failed(
                workflowId = workflow.id,
                nodeId = null,
                reason = "Workflow '${workflow.name}' is disabled",
                logs = listOf("Workflow is disabled")
            )
        }

        val logs = mutableListOf<String>()
        try {
            val startLogPrefix = if (!startNodeId.isNullOrBlank()) "Resuming from node '$startNodeId' in " else "Started executing "
            logs.add("${startLogPrefix}workflow '${workflow.name}' (${workflow.id})")
            context.set("workflow_id", workflow.id)
            postProgressNotification(appContext, context, notifId, workflow.name, "Starting workflow execution...", isFinished = false)

            var triggerNodes = if (!startNodeId.isNullOrBlank()) {
                workflow.nodes.filter { it.id == startNodeId }
            } else {
                emptyList()
            }

            if (triggerNodes.isEmpty()) {
                triggerNodes = workflow.nodes.filter {
                    it.type == NodeType.SMS_TRIGGER || it.type == NodeType.NOTIFICATION_TRIGGER || it.type == NodeType.MANUAL_TRIGGER
                }
                if (triggerNodes.isEmpty()) {
                    triggerNodes = workflow.nodes.filter { node ->
                        workflow.connections.none { conn -> conn.toNodeId == node.id }
                    }.ifEmpty { workflow.nodes.take(1) }
                }
            }

            if (triggerNodes.isEmpty()) {
                val failReason = "No trigger or entry nodes found in workflow"
                postProgressNotification(appContext, context, notifId, workflow.name, failReason, isFinished = true, isSuccess = false)
                return@withContext WorkflowExecutionResult.Failed(
                    workflowId = workflow.id,
                    nodeId = null,
                    reason = failReason,
                    logs = logs
                )
            }

            val visitedNodeIds = mutableSetOf<String>()
            val queue = ArrayDeque<Pair<WorkflowNode, String>>()

            triggerNodes.filter { it.isEnabled }.forEach { trigger ->
                queue.add(trigger to "main")
            }

            while (queue.isNotEmpty()) {
                ensureActive()

                val (currentNode, inputPort) = queue.removeFirst()
                if (visitedNodeIds.contains(currentNode.id)) continue
                visitedNodeIds.add(currentNode.id)

                logs.add("Evaluating Node '${currentNode.name}' (${currentNode.type})")
                postProgressNotification(appContext, context, notifId, workflow.name, "Step (${currentNode.name}): Executing...", isFinished = false)

                if (!currentNode.isEnabled) {
                    logs.add("Node '${currentNode.name}' is disabled, skipping.")
                    continue
                }

                // 1. Evaluate Node Preconditions
                val preconditionResult = preconditionEvaluator.evaluateAll(currentNode.preconditions) { precSteps ->
                    val lastStep = precSteps.lastOrNull()
                    if (lastStep != null) {
                        logs.add("  - Precondition '${lastStep.nodeName}': ${lastStep.logMessage}")
                        postProgressNotification(
                            appContext,
                            context,
                            notifId,
                            workflow.name,
                            "Precondition '${currentNode.name}': ${lastStep.nodeName} (${lastStep.logMessage})",
                            isFinished = false
                        )
                    }
                }
                if (preconditionResult is PreconditionResult.Failed) {
                    val failReason = preconditionResult.reason
                    logs.add("⏳ Precondition Failed for node '${currentNode.name}': $failReason")
                    postProgressNotification(appContext, context, notifId, workflow.name, "Precondition Failed at '${currentNode.name}': $failReason", isFinished = true, isSuccess = false)
                    return@withContext WorkflowExecutionResult.PreconditionFailed(
                        workflowId = workflow.id,
                        nodeId = currentNode.id,
                        nodeName = currentNode.name,
                        reason = failReason,
                        logs = logs
                    )
                }

                ensureActive()

                // 2. Execute Node Strategy
                val executor = executorFactory.getExecutor(currentNode.type)
                val result = executor.execute(currentNode, context, appContext)

                when (result) {
                    is NodeExecutionResult.Failed -> {
                        logs.add("❌ Node '${currentNode.name}' failed: ${result.reason}")
                        postProgressNotification(appContext, context, notifId, workflow.name, "Failed at step '${currentNode.name}': ${result.reason}", isFinished = true, isSuccess = false)
                        return@withContext WorkflowExecutionResult.Failed(
                            workflowId = workflow.id,
                            nodeId = currentNode.id,
                            reason = result.reason,
                            logs = logs
                        )
                    }
                    is NodeExecutionResult.Success -> {
                        logs.add("✅ Node '${currentNode.name}': ${result.logMessage}")

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
            postProgressNotification(appContext, context, notifId, workflow.name, "Workflow completed successfully", isFinished = true, isSuccess = true)
            WorkflowExecutionResult.Success(workflowId = workflow.id, logs = logs)
        } catch (e: CancellationException) {
            logs.add("🛑 Workflow execution manually cancelled by user.")
            postProgressNotification(appContext, context, notifId, workflow.name, "Workflow execution manually cancelled", isFinished = true, isSuccess = false)
            WorkflowExecutionResult.Cancelled(workflowId = workflow.id, logs = logs)
        }
    }

    open suspend fun executeWorkflowWithProgress(
        workflow: Workflow,
        context: ExecutionContext,
        appContext: Context?,
        startNodeId: String? = null,
        onStepUpdate: (List<NodeExecutionStep>) -> Unit
    ): WorkflowExecutionResult = withContext(Dispatchers.IO) {
        val notifId = workflow.id.hashCode()
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
        try {
            val startLogPrefix = if (!startNodeId.isNullOrBlank()) "Resuming from node '$startNodeId' in " else "Started executing "
            logs.add("${startLogPrefix}workflow '${workflow.name}' (${workflow.id})")
            context.set("workflow_id", workflow.id)
            postProgressNotification(appContext, context, notifId, workflow.name, "Starting workflow execution...", isFinished = false)

            var triggerNodes = if (!startNodeId.isNullOrBlank()) {
                workflow.nodes.filter { it.id == startNodeId }
            } else {
                emptyList()
            }

            if (triggerNodes.isEmpty()) {
                triggerNodes = workflow.nodes.filter {
                    it.type == NodeType.SMS_TRIGGER || it.type == NodeType.NOTIFICATION_TRIGGER || it.type == NodeType.MANUAL_TRIGGER
                }
                if (triggerNodes.isEmpty()) {
                    triggerNodes = workflow.nodes.filter { node ->
                        workflow.connections.none { conn -> conn.toNodeId == node.id }
                    }.ifEmpty { workflow.nodes.take(1) }
                }
            }

            if (triggerNodes.isEmpty()) {
                steps.indices.forEach { steps[it] = steps[it].copy(state = NodeStepState.SKIPPED) }
                onStepUpdate(steps.toList())
                val failReason = "No trigger or entry nodes found in workflow"
                postProgressNotification(appContext, context, notifId, workflow.name, failReason, isFinished = true, isSuccess = false)
                return@withContext WorkflowExecutionResult.Failed(
                    workflowId = workflow.id,
                    nodeId = null,
                    reason = failReason,
                    logs = logs
                )
            }

            val visitedNodeIds = mutableSetOf<String>()
            val queue = ArrayDeque<Pair<WorkflowNode, String>>()

            triggerNodes.filter { it.isEnabled }.forEach { trigger ->
                queue.add(trigger to "main")
            }

            while (queue.isNotEmpty()) {
                ensureActive()

                val (currentNode, inputPort) = queue.removeFirst()
                if (visitedNodeIds.contains(currentNode.id)) continue
                visitedNodeIds.add(currentNode.id)

                updateStep(currentNode.id, NodeStepState.EXECUTING, message = "Executing node...")
                postProgressNotification(appContext, context, notifId, workflow.name, "Executing step: '${currentNode.name}'", isFinished = false)
                kotlinx.coroutines.delay(200)

                if (!currentNode.isEnabled) {
                    logs.add("Node '${currentNode.name}' is disabled, skipping.")
                    updateStep(currentNode.id, NodeStepState.SKIPPED, message = "Node disabled")
                    continue
                }

                // 1. Evaluate Preconditions
                val preconditionResult = preconditionEvaluator.evaluateAll(currentNode.preconditions) { chainSteps ->
                    val activeStep = chainSteps.lastOrNull()
                    val stepDesc = activeStep?.let { " [${it.nodeName}: ${it.logMessage}]" } ?: ""
                    updateStep(currentNode.id, NodeStepState.EXECUTING, message = "Evaluating preconditions$stepDesc")
                    postProgressNotification(appContext, context, notifId, workflow.name, "Evaluating preconditions for '${currentNode.name}'$stepDesc", isFinished = false)
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
                    postProgressNotification(appContext, context, notifId, workflow.name, "Precondition failed at '${currentNode.name}': $failReason", isFinished = true, isSuccess = false)

                    return@withContext WorkflowExecutionResult.PreconditionFailed(
                        workflowId = workflow.id,
                        nodeId = currentNode.id,
                        nodeName = currentNode.name,
                        reason = failReason,
                        logs = logs
                    )
                }

                ensureActive()

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
                        postProgressNotification(appContext, context, notifId, workflow.name, "Failed at step '${currentNode.name}': ${result.reason}", isFinished = true, isSuccess = false)

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
            postProgressNotification(appContext, context, notifId, workflow.name, "Workflow completed successfully", isFinished = true, isSuccess = true)
            WorkflowExecutionResult.Success(workflowId = workflow.id, logs = logs)
        } catch (e: CancellationException) {
            logs.add("🛑 Workflow execution manually cancelled by user.")
            steps.forEachIndexed { i, s ->
                if (s.state == NodeStepState.EXECUTING) {
                    steps[i] = s.copy(
                        state = NodeStepState.FAILED,
                        logMessage = "Execution manually cancelled by user",
                        errorReason = "Cancelled"
                    )
                } else if (s.state == NodeStepState.PENDING) {
                    steps[i] = s.copy(
                        state = NodeStepState.SKIPPED,
                        logMessage = "Skipped due to cancellation"
                    )
                }
            }
            onStepUpdate(steps.toList())
            postProgressNotification(appContext, context, notifId, workflow.name, "Workflow execution manually cancelled", isFinished = true, isSuccess = false)
            WorkflowExecutionResult.Cancelled(workflowId = workflow.id, logs = logs)
        }
    }
}
