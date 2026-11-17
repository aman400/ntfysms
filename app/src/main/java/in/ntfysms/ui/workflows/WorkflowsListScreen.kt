package `in`.ntfysms.ui.workflows

import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import `in`.ntfysms.ui.components.NoSwipeBottomSheet
import `in`.ntfysms.domain.engine.NodeExecutionStep
import `in`.ntfysms.domain.engine.NodeStepState
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SheetValue
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
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
import `in`.ntfysms.domain.model.NodeType
import `in`.ntfysms.domain.model.Workflow
import `in`.ntfysms.domain.model.WorkflowConnection
import `in`.ntfysms.domain.model.WorkflowNode
import `in`.ntfysms.ui.MainViewModel
import java.util.UUID

@Composable
fun WorkflowsListScreen(
    viewModel: MainViewModel,
    hasPostNotificationsPermission: Boolean = true,
    hasSmsPermission: Boolean = true,
    onRequestSmsPermission: (() -> Unit)? = null,
    onRequestPostNotificationsPermission: (() -> Unit)? = null,
    onEditWorkflow: (Workflow) -> Unit,
    onViewLogs: ((Workflow) -> Unit)? = null
) {
    val workflows by viewModel.workflows.collectAsState()
    val uiState by viewModel.uiState.collectAsState()

    var selectedWorkflowForLogs by remember { mutableStateOf<Workflow?>(null) }
    var showCreateTriggerSheet by remember { mutableStateOf(false) }
    var pendingDeleteWorkflow by remember { mutableStateOf<Workflow?>(null) }

    val autoActiveCount = workflows.count { it.isEnabled && it.nodes.firstOrNull()?.type != NodeType.MANUAL_TRIGGER }
    val manualCount = workflows.count { it.nodes.firstOrNull()?.type == NodeType.MANUAL_TRIGGER }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item { Spacer(modifier = Modifier.height(4.dp)) }

            item {
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "Automations & Rules",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "$autoActiveCount Auto Active • $manualCount Manual",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            SuggestionChip(
                                onClick = {},
                                label = {
                                    Text(
                                        text = "${workflows.size} Workflows",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                },
                                colors = SuggestionChipDefaults.suggestionChipColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                    labelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            )
                        }
                    }
                }
            }

            if (workflows.isEmpty()) {
                item {
                    OutlinedCard(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.outlinedCardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No workflows created yet. Tap '+ Create Workflow' below to choose a trigger.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                items(workflows, key = { it.id }) { workflow ->
                    WorkflowCardItem(
                        workflow = workflow,
                        hasNotificationListenerPermission = uiState.hasNotificationListenerPermission,
                        hasPostNotificationsPermission = hasPostNotificationsPermission,
                        hasSmsPermission = hasSmsPermission,
                        onRequestSmsPermission = onRequestSmsPermission,
                        onRequestPostNotificationsPermission = onRequestPostNotificationsPermission,
                        onToggle = { enabled ->
                            viewModel.setWorkflowEnabled(workflow.id, enabled)
                        },
                        onRunManual = {
                            viewModel.testRunWorkflow(workflow) { _ -> }
                        },
                        onEdit = { onEditWorkflow(workflow) },
                        onViewLogs = {
                            if (onViewLogs != null) {
                                onViewLogs(workflow)
                            } else {
                                selectedWorkflowForLogs = workflow
                            }
                        },
                        onDelete = { pendingDeleteWorkflow = workflow }
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(80.dp)) }
        }

        ExtendedFloatingActionButton(
            onClick = { showCreateTriggerSheet = true },
            icon = { Icon(Icons.Default.Add, contentDescription = "Create Workflow") },
            text = { Text("Create Automation Rule") },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        )
    }

    if (showCreateTriggerSheet) {
        CreateWorkflowTriggerBottomSheet(
            onDismiss = { showCreateTriggerSheet = false },
            onSelectTrigger = { triggerType ->
                showCreateTriggerSheet = false
                val triggerNodeId = UUID.randomUUID().toString()
                val triggerNode = WorkflowNode(
                    id = triggerNodeId,
                    name = WorkflowUiHelper.getNodeTitle(triggerType),
                    type = triggerType,
                    isEnabled = true
                )
                val actionNodeId = UUID.randomUUID().toString()
                val actionNode = WorkflowNode(
                    id = actionNodeId,
                    name = WorkflowUiHelper.getNodeTitle(NodeType.NTFY_ACTION),
                    type = NodeType.NTFY_ACTION,
                    isEnabled = true
                )

                val newWorkflow = Workflow(
                    id = UUID.randomUUID().toString(),
                    name = when (triggerType) {
                        NodeType.SMS_TRIGGER -> "SMS Forwarder Workflow"
                        NodeType.NOTIFICATION_TRIGGER -> "App Notification Workflow"
                        NodeType.MANUAL_TRIGGER -> "Manual Trigger Workflow"
                        else -> "New Workflow"
                    },
                    description = if (triggerType == NodeType.MANUAL_TRIGGER) "Manual trigger workflow" else "Automated background workflow",
                    isEnabled = triggerType != NodeType.MANUAL_TRIGGER,
                    nodes = listOf(triggerNode, actionNode),
                    connections = listOf(WorkflowConnection(fromNodeId = triggerNodeId, toNodeId = actionNodeId))
                )
                viewModel.saveWorkflow(newWorkflow)
                onEditWorkflow(newWorkflow)
            }
        )
    }

    selectedWorkflowForLogs?.let { workflow ->
        WorkflowLogsScreen(
            workflow = workflow,
            viewModel = viewModel,
            onBack = { selectedWorkflowForLogs = null }
        )
        return
    }

    // Confirm Delete Workflow Dialog
    pendingDeleteWorkflow?.let { wf ->
        AlertDialog(
            onDismissRequest = { pendingDeleteWorkflow = null },
            title = { Text("Delete Rule?") },
            text = { Text("Are you sure you want to delete this rule '${wf.name}'?") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteWorkflow(wf.id)
                        pendingDeleteWorkflow = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteWorkflow = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun WorkflowCardItem(
    workflow: Workflow,
    hasNotificationListenerPermission: Boolean = true,
    hasPostNotificationsPermission: Boolean = true,
    hasSmsPermission: Boolean = true,
    onRequestSmsPermission: (() -> Unit)? = null,
    onRequestPostNotificationsPermission: (() -> Unit)? = null,
    onToggle: (Boolean) -> Unit,
    onRunManual: () -> Unit,
    onEdit: () -> Unit,
    onViewLogs: () -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current

    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { dismissValue ->
            if (dismissValue == SwipeToDismissBoxValue.EndToStart || dismissValue == SwipeToDismissBoxValue.StartToEnd) {
                onDelete()
                false
            } else false
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

    val firstNodeType = workflow.nodes.firstOrNull()?.type
    val isManual = firstNodeType == NodeType.MANUAL_TRIGGER

    val hasNotifTrigger = workflow.nodes.any { it.type == NodeType.NOTIFICATION_TRIGGER }
    val hasSmsTrigger = workflow.nodes.any { it.type == NodeType.SMS_TRIGGER }
    val hasSendSmsAction = workflow.nodes.any { it.type == NodeType.SEND_SMS_ACTION }
    val hasSendNotifAction = workflow.nodes.any { it.type == NodeType.SEND_NOTIFICATION_ACTION }

    val (missingPermTitle, missingPermDesc, onGrantClick) = when {
        hasNotifTrigger && !hasNotificationListenerPermission -> Triple(
            "⚠️ Notification Listener Permission Missing — Workflow Disabled",
            "Workflow is disabled until Notification Access permission is granted in Android settings.",
            {
                try {
                    context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                } catch (_: Exception) {}
            }
        )
        hasSmsTrigger && !hasSmsPermission -> Triple(
            "⚠️ SMS Intercept Permission Missing (RECEIVE_SMS)",
            "Intercepting incoming SMS text messages requires RECEIVE_SMS permission.",
            { onRequestSmsPermission?.invoke() }
        )
        hasSendSmsAction && !hasSendSmsPermission -> Triple(
            "⚠️ Send SMS Permission Missing (SEND_SMS)",
            "Sending text messages via SIM card requires SEND_SMS permission.",
            { sendSmsPermissionLauncher.launch(android.Manifest.permission.SEND_SMS) }
        )
        hasSendNotifAction && android.os.Build.VERSION.SDK_INT >= 33 && !hasPostNotificationsPermission -> Triple(
            "⚠️ Notification Permission Missing (POST_NOTIFICATIONS)",
            "Displaying local system notifications requires POST_NOTIFICATIONS permission.",
            { onRequestPostNotificationsPermission?.invoke() }
        )
        else -> Triple(null, null, null)
    }

    val isMissingAnyPerm = missingPermTitle != null

    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            val color = if (dismissState.targetValue != SwipeToDismissBoxValue.Settled) {
                MaterialTheme.colorScheme.errorContainer
            } else {
                Color.Transparent
            }
            val alignment = if (dismissState.targetValue == SwipeToDismissBoxValue.StartToEnd) {
                Alignment.CenterStart
            } else {
                Alignment.CenterEnd
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(color, shape = RoundedCornerShape(12.dp))
                    .padding(horizontal = 20.dp),
                contentAlignment = alignment
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Swipe to Delete",
                    tint = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
    ) {
        OutlinedCard(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onEdit),
            colors = CardDefaults.outlinedCardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = workflow.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        if (workflow.description.isNotBlank()) {
                            Text(
                                text = workflow.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    if (isManual) {
                        SuggestionChip(
                            onClick = {},
                            label = {
                                Text(
                                    text = "Manual Only",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            },
                            colors = SuggestionChipDefaults.suggestionChipColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                labelColor = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        )
                    } else {
                        Switch(
                            checked = if (isMissingAnyPerm) false else workflow.isEnabled,
                            enabled = !isMissingAnyPerm,
                            onCheckedChange = onToggle
                        )
                    }
                }

                // Missing System Permission Alert Banner on Workflow Card
                if (missingPermTitle != null && missingPermDesc != null && onGrantClick != null) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                color = MaterialTheme.colorScheme.errorContainer,
                                shape = RoundedCornerShape(8.dp)
                            )
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = missingPermTitle,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Text(
                            text = missingPermDesc,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Button(
                            onClick = { onGrantClick() },
                            modifier = Modifier.align(Alignment.End)
                        ) {
                            Text("Grant Permission", fontSize = 11.sp)
                        }
                    }
                }

                // Pipeline node sequence
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    workflow.nodes.forEachIndexed { idx, node ->
                        Text(
                            text = node.name,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = if (node.isEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (idx < workflow.nodes.size - 1) {
                            Text(
                                text = "→",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (isManual) {
                            Button(
                                onClick = onRunManual,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Run Now", fontSize = 12.sp)
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                        }

                        OutlinedButton(onClick = onViewLogs) {
                            Icon(Icons.Default.History, contentDescription = null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Logs", fontSize = 12.sp)
                        }
                    }

                    // Bottom Right Edit Button
                    OutlinedButton(onClick = onEdit) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit Workflow"
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Edit", fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateWorkflowTriggerBottomSheet(
    onDismiss: () -> Unit,
    onSelectTrigger: (NodeType) -> Unit
) {
    val triggerTypes = listOf(
        NodeType.SMS_TRIGGER,
        NodeType.NOTIFICATION_TRIGGER,
        NodeType.MANUAL_TRIGGER
    )

    NoSwipeBottomSheet(
        onDismissRequest = onDismiss,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 12.dp)
        ) {
            // Top Header Row with Title and Close Button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Choose Trigger Source",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "What should trigger this automation?",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                triggerTypes.forEach { type ->
                    val label = WorkflowUiHelper.getNodeTitle(type)
                    val desc = WorkflowUiHelper.getNodeDescription(type)
                    val icon = WorkflowUiHelper.getNodeIcon(type)

                    OutlinedCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelectTrigger(type) },
                        colors = CardDefaults.outlinedCardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Column(modifier = Modifier.padding(end = 12.dp)) {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = desc,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkflowLogsScreen(
    workflow: Workflow,
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    val logsFlow = remember(workflow.id) { viewModel.getLogsForWorkflow(workflow.id) }
    val logs by logsFlow.collectAsState(initial = emptyList())
    var showClearConfirm by remember { mutableStateOf(false) }

    BackHandler(enabled = true) {
        onBack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Execution History",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = workflow.name,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (logs.isNotEmpty()) {
                        TextButton(onClick = { showClearConfirm = true }) {
                            Text("Clear Logs", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (logs.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No execution log entries for this workflow.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item { Spacer(modifier = Modifier.height(4.dp)) }

                    items(logs, key = { it.id }) { log ->
                        OutlinedCard(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.outlinedCardColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = if (log.status == "SUCCESS") "✅ SUCCESS" else "❌ ${log.status}",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = if (log.status == "SUCCESS") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                                    )
                                    val currentLocale = androidx.compose.ui.platform.LocalConfiguration.current.locales[0]
                                    Text(
                                        text = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", currentLocale).format(java.util.Date(log.timestamp)),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                NodeExecutionStepper(steps = parseLogsToSteps(log.logs, log.status, workflow.nodes))
                            }
                        }
                    }

                    item { Spacer(modifier = Modifier.height(24.dp)) }
                }
            }
        }
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("Clear Execution Logs?") },
            text = { Text("Are you sure you want to clear execution history for '${workflow.name}'?") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.clearLogsForWorkflow(workflow.id)
                        showClearConfirm = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Clear Logs")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

fun parseLogsToSteps(logText: String, status: String, workflowNodes: List<WorkflowNode> = emptyList()): List<NodeExecutionStep> {
    if (workflowNodes.isEmpty()) {
        val steps = mutableListOf<NodeExecutionStep>()
        val lines = logText.split("\n")
        for (line in lines) {
            val trimmed = line.trim()
            when {
                trimmed.startsWith("Evaluating Node") -> {
                    val match = Regex("Evaluating Node '(.+?)' \\((.+?)\\)").find(trimmed)
                    if (match != null) {
                        val name = match.groupValues.getOrNull(1) ?: "Node"
                        val typeStr = match.groupValues.getOrNull(2) ?: "HTTP_ACTION"
                        val type = try { NodeType.valueOf(typeStr) } catch (_: Exception) { NodeType.HTTP_ACTION }
                        steps.add(NodeExecutionStep(
                            nodeId = UUID.randomUUID().toString(),
                            nodeName = name,
                            nodeType = type,
                            state = NodeStepState.SUCCESS
                        ))
                    }
                }
                trimmed.startsWith("✅ Node") -> {
                    val match = Regex("✅ Node '(.+?)[\\':](.*)").find(trimmed)
                    val name = match?.groupValues?.getOrNull(1) ?: ""
                    val msg = match?.groupValues?.getOrNull(2)?.trim() ?: trimmed
                    val lastStep = steps.lastOrNull { it.nodeName == name } ?: steps.lastOrNull()
                    if (lastStep != null) {
                        val idx = steps.indexOf(lastStep)
                        steps[idx] = lastStep.copy(state = NodeStepState.SUCCESS, logMessage = msg)
                    }
                }
                trimmed.startsWith("❌ Node") || trimmed.startsWith("❌ Failed") -> {
                    val match = Regex("❌ (?:Node '(.+?)' failed|Failed): (.*)").find(trimmed)
                    val name = match?.groupValues?.getOrNull(1) ?: steps.lastOrNull()?.nodeName ?: "Node"
                    val err = match?.groupValues?.getOrNull(2) ?: trimmed
                    val lastStep = steps.lastOrNull { it.nodeName == name } ?: steps.lastOrNull()
                    if (lastStep != null) {
                        val idx = steps.indexOf(lastStep)
                        steps[idx] = lastStep.copy(state = NodeStepState.FAILED, logMessage = err)
                    }
                }
                trimmed.startsWith("⏳ Precondition Failed") -> {
                    val lastStep = steps.lastOrNull()
                    if (lastStep != null) {
                        val idx = steps.indexOf(lastStep)
                        steps[idx] = lastStep.copy(state = NodeStepState.FAILED, logMessage = trimmed)
                    }
                }
            }
        }
        if (steps.isEmpty()) {
            steps.add(NodeExecutionStep(
                nodeId = "summary",
                nodeName = "Execution Summary",
                nodeType = NodeType.MANUAL_TRIGGER,
                state = if (status == "SUCCESS") NodeStepState.SUCCESS else NodeStepState.FAILED,
                logMessage = logText
            ))
        }
        return steps
    }

    val stepsMap = workflowNodes.map { node ->
        node.id to NodeExecutionStep(
            nodeId = node.id,
            nodeName = node.name,
            nodeType = node.type,
            state = NodeStepState.PENDING
        )
    }.toMap().toMutableMap()

    val orderedNodeIds = workflowNodes.map { it.id }

    val lines = logText.split("\n")
    for (line in lines) {
        val trimmed = line.trim()
        when {
            trimmed.startsWith("Evaluating Node") -> {
                val match = Regex("Evaluating Node '(.+?)' \\((.+?)\\)").find(trimmed)
                val name = match?.groupValues?.getOrNull(1) ?: ""
                val step = stepsMap.values.firstOrNull { it.nodeName == name }
                if (step != null && step.state == NodeStepState.PENDING) {
                    stepsMap[step.nodeId] = step.copy(state = NodeStepState.SUCCESS)
                }
            }
            trimmed.startsWith("✅ Node") -> {
                val match = Regex("✅ Node '(.+?)[\\':](.*)").find(trimmed)
                val name = match?.groupValues?.getOrNull(1) ?: ""
                val msg = match?.groupValues?.getOrNull(2)?.trim() ?: trimmed
                val step = stepsMap.values.firstOrNull { it.nodeName == name }
                if (step != null) {
                    stepsMap[step.nodeId] = step.copy(state = NodeStepState.SUCCESS, logMessage = msg)
                }
            }
            trimmed.startsWith("❌ Node") || trimmed.startsWith("❌ Failed") -> {
                val match = Regex("❌ (?:Node '(.+?)' failed|Failed): (.*)").find(trimmed)
                val name = match?.groupValues?.getOrNull(1) ?: ""
                val err = match?.groupValues?.getOrNull(2) ?: trimmed
                val step = stepsMap.values.firstOrNull { it.nodeName == name } ?: stepsMap.values.lastOrNull { it.state == NodeStepState.SUCCESS }
                if (step != null) {
                    stepsMap[step.nodeId] = step.copy(state = NodeStepState.FAILED, logMessage = err)
                }
            }
            trimmed.startsWith("⏳ Precondition Failed") -> {
                val match = Regex("⏳ Precondition Failed for node '(.+?)': (.*)").find(trimmed)
                val name = match?.groupValues?.getOrNull(1) ?: ""
                val reason = match?.groupValues?.getOrNull(2) ?: trimmed
                val step = stepsMap.values.firstOrNull { it.nodeName == name } ?: stepsMap.values.firstOrNull()
                if (step != null) {
                    stepsMap[step.nodeId] = step.copy(state = NodeStepState.FAILED, logMessage = reason)
                }
            }
        }
    }

    var failedEncountered = false
    return orderedNodeIds.mapNotNull { id ->
        val step = stepsMap[id] ?: return@mapNotNull null
        if (failedEncountered) {
            step.copy(state = NodeStepState.SKIPPED)
        } else {
            if (step.state == NodeStepState.FAILED) {
                failedEncountered = true
            }
            step
        }
    }
}
