package dev.zophop.ui.workflows

import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import dev.zophop.core.ui.components.dashedBorder
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import dev.zophop.core.ui.components.InfoTooltipField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.zophop.core.ui.EditorDarkColorScheme
import dev.zophop.core.ui.EditorLightColorScheme
import dev.zophop.core.ui.components.NoSwipeBottomSheet
import dev.zophop.data.PreconditionEntity
import dev.zophop.domain.engine.NodeExecutionStep
import dev.zophop.domain.engine.WorkflowExecutionResult
import dev.zophop.domain.model.Credential
import dev.zophop.domain.model.NodeType
import dev.zophop.domain.model.Precondition
import dev.zophop.domain.model.Workflow
import dev.zophop.domain.model.WorkflowConnection
import dev.zophop.domain.model.WorkflowNode
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkflowEditorScreen(
    workflow: Workflow,
    credentials: List<Credential> = emptyList(),
    vaultPreconditions: List<PreconditionEntity> = emptyList(),
    hasNotificationListenerPermission: Boolean = true,
    hasPostNotificationsPermission: Boolean = true,
    hasSmsPermission: Boolean = true,
    onRequestSmsPermission: (() -> Unit)? = null,
    onRequestPostNotificationsPermission: (() -> Unit)? = null,
    onBack: () -> Unit,
    onSaveWorkflow: (Workflow) -> Unit,
    onTestRunWorkflow: (Workflow, (String) -> Unit) -> Unit,
    onTestRunWorkflowWithProgress: ((Workflow, (List<NodeExecutionStep>) -> Unit, (WorkflowExecutionResult) -> Unit) -> Unit)? = null,
    onCancelWorkflowExecution: ((String) -> Unit)? = null,
    onSaveCredential: ((Credential) -> Unit)? = null,
    onSavePrecondition: ((PreconditionEntity) -> Unit)? = null,
    onRequestCreatePrecondition: ((onCreated: (PreconditionEntity) -> Unit) -> Unit)? = null
) {
    var name by remember { mutableStateOf(workflow.name) }
    var description by remember { mutableStateOf(workflow.description) }
    var isEnabled by remember { mutableStateOf(workflow.isEnabled) }
    var nodes by remember { mutableStateOf(workflow.nodes) }
    var connections by remember { mutableStateOf(workflow.connections) }

    var editingNode by remember { mutableStateOf<WorkflowNode?>(null) }
    var pendingDeleteNode by remember { mutableStateOf<WorkflowNode?>(null) }
    var showAddNodeSheet by remember { mutableStateOf(false) }
    var unsavedNodeIds by remember { mutableStateOf(setOf<String>()) }
    var testSteps by remember { mutableStateOf<List<NodeExecutionStep>>(emptyList()) }
    var showTestStepperSheet by remember { mutableStateOf(false) }
    var isTesting by remember { mutableStateOf(false) }
    var showSaveConfirmDialog by remember { mutableStateOf(false) }

    val hasUnsavedChanges = (
        name != workflow.name ||
        description != workflow.description ||
        isEnabled != workflow.isEnabled ||
        nodes != workflow.nodes ||
        connections != workflow.connections ||
        unsavedNodeIds.isNotEmpty()
    )

    var showDiscardDialog by remember { mutableStateOf(false) }

    val handleBack = {
        if (hasUnsavedChanges) {
            showDiscardDialog = true
        } else {
            onBack()
        }
    }

    BackHandler(enabled = editingNode != null) {
        editingNode = null
    }

    BackHandler(enabled = showAddNodeSheet) {
        showAddNodeSheet = false
    }

    BackHandler(enabled = editingNode == null && !showAddNodeSheet) {
        handleBack()
    }

    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) EditorDarkColorScheme else EditorLightColorScheme
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            when {
                editingNode != null -> {
                    NodeConfigBottomSheet(
                        node = editingNode!!,
                        credentials = credentials,
                        vaultPreconditions = vaultPreconditions,
                        hasNotificationListenerPermission = hasNotificationListenerPermission,
                        hasPostNotificationsPermission = hasPostNotificationsPermission,
                        hasSmsPermission = hasSmsPermission,
                        onRequestSmsPermission = onRequestSmsPermission,
                        onRequestPostNotificationsPermission = onRequestPostNotificationsPermission,
                        onDismiss = { editingNode = null },
                        onSave = { updatedNode ->
                            nodes = nodes.map { if (it.id == updatedNode.id) updatedNode else it }
                            unsavedNodeIds = unsavedNodeIds - updatedNode.id
                            editingNode = null
                        },
                        onSaveCredential = onSaveCredential,
                        onSavePrecondition = onSavePrecondition,
                        onRequestCreatePrecondition = onRequestCreatePrecondition
                    )
                }
                showAddNodeSheet -> {
                    AddNodeScreen(
                        onBack = { showAddNodeSheet = false },
                        onSelectNodeType = { type ->
                            val newNodeId = UUID.randomUUID().toString()
                            val newNode = WorkflowNode(
                                id = newNodeId,
                                name = WorkflowUiHelper.getNodeTitle(type),
                                type = type,
                                isEnabled = true
                            )
                            val lastId = nodes.lastOrNull()?.id
                            nodes = nodes + newNode
                            if (lastId != null) {
                                connections = connections + WorkflowConnection(fromNodeId = lastId, toNodeId = newNodeId)
                            }
                            showAddNodeSheet = false
                            unsavedNodeIds = unsavedNodeIds + newNode.id
                            editingNode = newNode
                        }
                    )
                }
                else -> {
                    Scaffold(
                        topBar = {
                            TopAppBar(
                                title = {
                                    Column {
                                        Text(
                                            text = "Pipeline Builder",
                                            style = MaterialTheme.typography.titleLarge,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "${nodes.size} Nodes • ${connections.size} Connections",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                },
                                navigationIcon = {
                                    IconButton(onClick = handleBack) {
                                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                                    }
                                },
                                actions = {
                                    Button(
                                        onClick = { showSaveConfirmDialog = true },
                                        enabled = hasUnsavedChanges
                                    ) {
                                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Save")
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                }
                            )
                        }
                    ) { innerPadding ->
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(innerPadding)
                        ) {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 16.dp),
                                verticalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                item { Spacer(modifier = Modifier.height(12.dp)) }

                                item {
                                    Surface(
                                        modifier = Modifier.fillMaxWidth(),
                                        color = MaterialTheme.colorScheme.secondaryContainer,
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(14.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.AccountTree,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onSecondaryContainer
                                            )
                                            Column {
                                                Text(
                                                    text = "Pipeline Builder Mode",
                                                    style = MaterialTheme.typography.titleSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                                )
                                                Text(
                                                    text = "Configure trigger rules, parsers, and delivery actions.",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                                                )
                                            }
                                        }
                                    }
                                }

                                item {
                                    InfoTooltipField(
                                        value = name,
                                        onValueChange = { name = it },
                                        label = "Workflow Name",
                                        tooltipText = "Descriptive name for this automated workflow pipeline.",
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true
                                    )
                                }

                                itemsIndexed(nodes, key = { _, node -> node.id }) { index, node ->
                                    val isFirst = index == 0
                                    val isUnsaved = unsavedNodeIds.contains(node.id)

                                    WorkflowNodeCardItem(
                                        node = node,
                                        isFirst = isFirst,
                                        index = index,
                                        totalNodes = nodes.size,
                                        isUnsaved = isUnsaved,
                                        hasNotificationListenerPermission = hasNotificationListenerPermission,
                                        hasPostNotificationsPermission = hasPostNotificationsPermission,
                                        hasSmsPermission = hasSmsPermission,
                                        onRequestSmsPermission = onRequestSmsPermission,
                                        onRequestPostNotificationsPermission = onRequestPostNotificationsPermission,
                                        onToggleNode = { enabled ->
                                            nodes = nodes.map { if (it.id == node.id) it.copy(isEnabled = enabled) else it }
                                        },
                                        onEditNode = { editingNode = node },
                                        onDeleteNode = { pendingDeleteNode = node },
                                        onMoveUp = {
                                            if (index > 1) {
                                                val (newNodes, newConns) = reorderWorkflowNodes(nodes, connections, index, index - 1)
                                                nodes = newNodes
                                                connections = newConns
                                            }
                                        },
                                        onMoveDown = {
                                            if (index < nodes.size - 1) {
                                                val (newNodes, newConns) = reorderWorkflowNodes(nodes, connections, index, index + 1)
                                                nodes = newNodes
                                                connections = newConns
                                            }
                                        }
                                    )
                                }

                                item { Spacer(modifier = Modifier.height(96.dp)) }
                            }

                            // Bottom-Left FAB: Test Run
                            ExtendedFloatingActionButton(
                                onClick = {
                                    isTesting = true
                                    showTestStepperSheet = true
                                    testSteps = emptyList()
                                    val testWorkflow = workflow.copy(
                                        name = name,
                                        description = description,
                                        isEnabled = isEnabled,
                                        nodes = nodes,
                                        connections = connections
                                    )
                                    onTestRunWorkflowWithProgress?.invoke(
                                        testWorkflow,
                                        { steps -> testSteps = steps },
                                        { _ -> isTesting = false }
                                    ) ?: run {
                                        onTestRunWorkflow(testWorkflow) { _ -> isTesting = false }
                                    }
                                },
                                icon = { Icon(Icons.Default.PlayArrow, contentDescription = "Test Run") },
                                text = { Text("Test Run") },
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .padding(16.dp),
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                            )

                            // Bottom-Right FAB: Add Step
                            ExtendedFloatingActionButton(
                                onClick = { showAddNodeSheet = true },
                                icon = { Icon(Icons.Default.Add, contentDescription = "Add Step") },
                                text = { Text("Add Step") },
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(16.dp),
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
            }

            if (showTestStepperSheet) {
                TestRunLogBottomSheet(
                    workflowName = name,
                    steps = testSteps,
                    isTesting = isTesting,
                    onCancelExecution = { onCancelWorkflowExecution?.invoke(workflow.id) },
                    onDismiss = { showTestStepperSheet = false }
                )
            }

            if (showSaveConfirmDialog) {
                val nameChanged = name != workflow.name
                val descChanged = description != workflow.description
                val originalNodeIds = workflow.nodes.map { it.id }.toSet()
                val currentNodeIds = nodes.map { it.id }.toSet()
                val addedNodeIds = currentNodeIds - originalNodeIds
                val removedNodeIds = originalNodeIds - currentNodeIds
                val addedNodes = nodes.filter { addedNodeIds.contains(it.id) }
                val removedNodes = workflow.nodes.filter { removedNodeIds.contains(it.id) }
                val modifiedNodes = nodes.filter { node ->
                    val orig = workflow.nodes.find { it.id == node.id }
                    orig != null && (orig.name != node.name || orig.configJson != node.configJson || orig.isEnabled != node.isEnabled)
                }
                val reordered = nodes.map { it.id } != workflow.nodes.map { it.id }

                AlertDialog(
                    onDismissRequest = { showSaveConfirmDialog = false },
                    title = { Text("Confirm Workflow Changes") },
                    text = {
                        Column(
                            modifier = Modifier.verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("Here is a summary of new changes to be saved:")
                            if (nameChanged) {
                                Text("• Name: '${workflow.name}' → '$name'", fontWeight = FontWeight.Bold)
                            }
                            if (descChanged) {
                                Text("• Description updated")
                            }
                            if (addedNodes.isNotEmpty()) {
                                Text("• Added Nodes (${addedNodes.size}):", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                addedNodes.forEach {
                                    Text("  + ${it.name} [${it.type.name}]", fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                                }
                            }
                            if (removedNodes.isNotEmpty()) {
                                Text("• Removed Nodes (${removedNodes.size}):", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                                removedNodes.forEach {
                                    Text("  - ${it.name} [${it.type.name}]", fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                                }
                            }
                            if (modifiedNodes.isNotEmpty()) {
                                Text("• Modified Nodes (${modifiedNodes.size}):", fontWeight = FontWeight.Bold)
                                modifiedNodes.forEach {
                                    Text("  ~ ${it.name} [${it.type.name}]", fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                                }
                            }
                            if (reordered && addedNodes.isEmpty() && removedNodes.isEmpty() && modifiedNodes.isEmpty()) {
                                Text("• Pipeline steps reordered", fontWeight = FontWeight.Bold)
                            }
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                showSaveConfirmDialog = false
                                val savedWorkflow = workflow.copy(
                                    name = name,
                                    description = description,
                                    isEnabled = isEnabled,
                                    nodes = nodes,
                                    connections = connections,
                                    updatedAt = System.currentTimeMillis()
                                )
                                unsavedNodeIds = emptySet()
                                onSaveWorkflow(savedWorkflow)
                            }
                        ) {
                            Text("Confirm & Save")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showSaveConfirmDialog = false }) {
                            Text("Cancel")
                        }
                    }
                )
            }

            pendingDeleteNode?.let { nodeToDelete ->
                AlertDialog(
                    onDismissRequest = { pendingDeleteNode = null },
                    title = { Text("Delete Pipeline Node?") },
                    text = { Text("Are you sure you want to delete node '${nodeToDelete.name}'?") },
                    confirmButton = {
                        Button(
                            onClick = {
                                val newNodes = nodes.filterNot { it.id == nodeToDelete.id }
                                nodes = newNodes
                                val newConns = mutableListOf<WorkflowConnection>()
                                for (i in 0 until newNodes.size - 1) {
                                    newConns.add(WorkflowConnection(fromNodeId = newNodes[i].id, toNodeId = newNodes[i + 1].id))
                                }
                                connections = newConns
                                pendingDeleteNode = null
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                        ) {
                            Text("Delete")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { pendingDeleteNode = null }) {
                            Text("Cancel")
                        }
                    }
                )
            }

            if (showDiscardDialog) {
                AlertDialog(
                    onDismissRequest = { showDiscardDialog = false },
                    title = { Text("Discard Changes?") },
                    text = { Text("You have unsaved changes in this workflow. Are you sure you want to discard them?") },
                    confirmButton = {
                        Button(
                            onClick = {
                                showDiscardDialog = false
                                onBack()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                        ) {
                            Text("Discard")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showDiscardDialog = false }) {
                            Text("Keep Editing")
                        }
                    }
                )
            }
        }
    }
}

private fun reorderWorkflowNodes(
    nodesList: List<WorkflowNode>,
    connectionsList: List<WorkflowConnection>,
    fromIndex: Int,
    toIndex: Int
): Pair<List<WorkflowNode>, List<WorkflowConnection>> {
    if (fromIndex <= 0 || toIndex <= 0 || fromIndex >= nodesList.size || toIndex >= nodesList.size) {
        return Pair(nodesList, connectionsList)
    }
    val newNodes = nodesList.toMutableList()
    val item = newNodes.removeAt(fromIndex)
    newNodes.add(toIndex, item)

    val newConns = mutableListOf<WorkflowConnection>()
    for (i in 0 until newNodes.size - 1) {
        newConns.add(WorkflowConnection(fromNodeId = newNodes[i].id, toNodeId = newNodes[i + 1].id))
    }
    return Pair(newNodes, newConns)
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun WorkflowNodeCardItem(
    node: WorkflowNode,
    isFirst: Boolean,
    index: Int,
    totalNodes: Int,
    isUnsaved: Boolean = false,
    hasNotificationListenerPermission: Boolean = true,
    hasPostNotificationsPermission: Boolean = true,
    hasSmsPermission: Boolean = true,
    onRequestSmsPermission: (() -> Unit)? = null,
    onRequestPostNotificationsPermission: (() -> Unit)? = null,
    onToggleNode: (Boolean) -> Unit,
    onEditNode: () -> Unit,
    onDeleteNode: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit
) {
    val context = LocalContext.current

    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { dismissValue ->
            when (dismissValue) {
                SwipeToDismissBoxValue.EndToStart -> {
                    if (!isFirst) onDeleteNode()
                    false
                }
                SwipeToDismissBoxValue.StartToEnd -> {
                    onEditNode()
                    false
                }
                else -> false
            }
        }
    )

    val sendSmsPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ -> }

    val hasSendSmsPermission = remember(context) {
        androidx.core.content.ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.SEND_SMS
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    val (missingPermTitle, missingPermDesc, onGrantClick) = when {
        node.type == NodeType.NOTIFICATION_TRIGGER && !hasNotificationListenerPermission -> Triple(
            "⚠️ Notification Listener Permission Missing",
            "Workflow cannot run until Notification Access is granted in Android settings.",
            {
                try {
                    context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                } catch (_: Exception) {}
            }
        )
        node.type == NodeType.SMS_TRIGGER && !hasSmsPermission -> Triple(
            "⚠️ SMS Permission Missing (RECEIVE_SMS)",
            "Workflow cannot intercept incoming SMS text messages without SMS permission.",
            { onRequestSmsPermission?.invoke() }
        )
        node.type == NodeType.SEND_SMS_ACTION && !hasSendSmsPermission -> Triple(
            "⚠️ Send SMS Permission Missing (SEND_SMS)",
            "Sending text messages via SIM card requires SEND_SMS permission.",
            { sendSmsPermissionLauncher.launch(android.Manifest.permission.SEND_SMS) }
        )
        node.type == NodeType.SEND_NOTIFICATION_ACTION && android.os.Build.VERSION.SDK_INT >= 33 && !hasPostNotificationsPermission -> Triple(
            "⚠️ Notification Permission Missing (POST_NOTIFICATIONS)",
            "Displaying local system notifications requires POST_NOTIFICATIONS permission.",
            { onRequestPostNotificationsPermission?.invoke() }
        )
        else -> Triple(null, null, null)
    }

    val unsavedBorder = if (isUnsaved) Modifier.dashedBorder(Color(0xFFFFD54F), 2.dp, 16.dp) else Modifier

    val cardContent: @Composable () -> Unit = {
        OutlinedCard(
            modifier = Modifier
                .fillMaxWidth()
                .then(unsavedBorder)
                .clickable(onClick = onEditNode),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.outlinedCardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = WorkflowUiHelper.getNodeIcon(node.type),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Column {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = node.name,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                if (isUnsaved) {
                                    Surface(
                                        color = MaterialTheme.colorScheme.tertiaryContainer,
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text(
                                            text = "Unsaved",
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                            style = MaterialTheme.typography.labelSmall,
                                            fontSize = 9.sp,
                                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                            Text(
                                text = if (isFirst) "Fixed Trigger Entry Point" else WorkflowUiHelper.getNodeTitle(node.type),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 10.sp
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (!isFirst) {
                            IconButton(
                                onClick = onMoveUp,
                                enabled = index > 1,
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.KeyboardArrowUp,
                                    contentDescription = "Move Up",
                                    modifier = Modifier.size(18.dp),
                                    tint = if (index > 1) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                                )
                            }
                            IconButton(
                                onClick = onMoveDown,
                                enabled = index < totalNodes - 1,
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.KeyboardArrowDown,
                                    contentDescription = "Move Down",
                                    modifier = Modifier.size(18.dp),
                                    tint = if (index < totalNodes - 1) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                                )
                            }
                        }
                        Switch(
                            checked = node.isEnabled,
                            onCheckedChange = onToggleNode,
                            modifier = Modifier.padding(start = 4.dp)
                        )
                    }
                }

                // Permission Error Banner on Node Card
                if (missingPermTitle != null && missingPermDesc != null && onGrantClick != null) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                color = MaterialTheme.colorScheme.errorContainer,
                                shape = RoundedCornerShape(6.dp)
                            )
                            .padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = missingPermTitle,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            fontSize = 11.sp
                        )
                        Text(
                            text = missingPermDesc,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            fontSize = 10.sp
                        )
                        Button(
                            onClick = { onGrantClick() },
                            modifier = Modifier.align(Alignment.End).height(26.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                        ) {
                            Text("Grant Permission", fontSize = 10.sp)
                        }
                    }
                }

                // Preconditions badges
                if (node.preconditions.isNotEmpty()) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        node.preconditions.forEach { prec ->
                            val badgeText = when (prec) {
                                is Precondition.InternetCheck -> "🌐 Internet"
                                is Precondition.VpnCheck -> "🔐 VPN"
                                is Precondition.HealthCheck -> "🩺 Healthcheck"
                                is Precondition.SimCheck -> "📱 SIM"
                                is Precondition.GpsCheck -> "📍 GPS"
                                is Precondition.ChainCheck -> "⛓️ Chained"
                            }
                            Surface(
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = badgeText,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontSize = 9.sp,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            val direction = dismissState.dismissDirection
            val color = when {
                direction == SwipeToDismissBoxValue.StartToEnd -> MaterialTheme.colorScheme.secondaryContainer
                direction == SwipeToDismissBoxValue.EndToStart && !isFirst -> MaterialTheme.colorScheme.errorContainer
                else -> Color.Transparent
            }
            val alignment = if (direction == SwipeToDismissBoxValue.StartToEnd) Alignment.CenterStart else Alignment.CenterEnd
            val icon = if (direction == SwipeToDismissBoxValue.StartToEnd) Icons.Default.Edit else Icons.Default.Delete
            val text = if (direction == SwipeToDismissBoxValue.StartToEnd) "Edit" else "Delete"
            val tint = if (direction == SwipeToDismissBoxValue.StartToEnd) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onErrorContainer

            if (direction != SwipeToDismissBoxValue.EndToStart || !isFirst) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(color, shape = RoundedCornerShape(16.dp))
                        .padding(horizontal = 24.dp),
                    contentAlignment = alignment
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(imageVector = icon, contentDescription = text, tint = tint, modifier = Modifier.size(18.dp))
                        Text(text = text, color = tint, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
        }
    ) {
        cardContent()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddNodeScreen(
    onBack: () -> Unit,
    onSelectNodeType: (NodeType) -> Unit
) {
    val triggerNodes = listOf(NodeType.SMS_TRIGGER, NodeType.NOTIFICATION_TRIGGER)
    val parserNodes = listOf(NodeType.PARSE_NOTIFICATION, NodeType.REGEX_PARSER, NodeType.JSON_PARSER, NodeType.TEMPLATE_FORMATTER, NodeType.CONDITION)
    val actionNodes = listOf(NodeType.NTFY_ACTION, NodeType.GOTIFY_ACTION, NodeType.HTTP_ACTION, NodeType.SEND_NOTIFICATION_ACTION, NodeType.SEND_SMS_ACTION)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Add Pipeline Step Node") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            Text(text = "⚡ Trigger Sources", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            triggerNodes.forEach { type ->
                NodeSelectionCard(type = type, onClick = { onSelectNodeType(type) })
            }

            Text(text = "🔍 Parsers & Logic", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            parserNodes.forEach { type ->
                NodeSelectionCard(type = type, onClick = { onSelectNodeType(type) })
            }

            Text(text = "🚀 Actions & Delivery", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            actionNodes.forEach { type ->
                NodeSelectionCard(type = type, onClick = { onSelectNodeType(type) })
            }

            Spacer(modifier = Modifier.height(36.dp))
        }
    }
}

@Composable
fun NodeSelectionCard(
    type: NodeType,
    onClick: () -> Unit
) {
    OutlinedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(imageVector = WorkflowUiHelper.getNodeIcon(type), contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column {
                Text(text = WorkflowUiHelper.getNodeTitle(type), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(text = WorkflowUiHelper.getNodeDescription(type), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TestRunLogBottomSheet(
    workflowName: String,
    steps: List<NodeExecutionStep>,
    isTesting: Boolean,
    onCancelExecution: (() -> Unit)? = null,
    onDismiss: () -> Unit
) {
    val scrollState = rememberScrollState()

    androidx.compose.runtime.LaunchedEffect(steps.size, steps.map { "${it.nodeId}_${it.state}" }) {
        if (steps.isNotEmpty()) {
            scrollState.animateScrollTo(scrollState.maxValue)
        }
    }

    NoSwipeBottomSheet(
        onDismissRequest = onDismiss,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 12.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 24.dp, bottom = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = "Test Execution Stepper", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(text = workflowName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isTesting) {
                        CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp).size(20.dp), strokeWidth = 2.dp)
                        if (onCancelExecution != null) {
                            OutlinedButton(
                                onClick = onCancelExecution,
                                modifier = Modifier.padding(end = 8.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                            ) {
                                Text("Cancel", fontSize = 11.sp, color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(scrollState),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (steps.isEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        Text("Running workflow test execution...", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    NodeExecutionStepper(steps = steps)
                }
            }
        }
    }
}
