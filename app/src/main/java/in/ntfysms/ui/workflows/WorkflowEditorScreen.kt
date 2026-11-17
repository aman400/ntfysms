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
import androidx.compose.foundation.isSystemInDarkTheme
import `in`.ntfysms.ui.EditorDarkColorScheme
import `in`.ntfysms.ui.EditorLightColorScheme
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import `in`.ntfysms.ui.MainViewModel
import `in`.ntfysms.ui.components.NoSwipeBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import `in`.ntfysms.data.PreconditionEntity
import `in`.ntfysms.domain.engine.NodeExecutionStep
import `in`.ntfysms.domain.engine.WorkflowExecutionResult
import `in`.ntfysms.domain.model.Credential
import `in`.ntfysms.domain.model.NodeType
import `in`.ntfysms.domain.model.Precondition
import `in`.ntfysms.domain.model.Workflow
import `in`.ntfysms.domain.model.WorkflowConnection
import `in`.ntfysms.domain.model.WorkflowNode
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
    onSaveCredential: ((Credential) -> Unit)? = null,
    onSavePrecondition: ((PreconditionEntity) -> Unit)? = null,
    viewModel: MainViewModel? = null
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
                        viewModel = viewModel
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
                            val lastNodeId = nodes.lastOrNull()?.id
                            nodes = nodes + newNode
                            if (lastNodeId != null) {
                                connections = connections + WorkflowConnection(fromNodeId = lastNodeId, toNodeId = newNodeId)
                            }
                            unsavedNodeIds = unsavedNodeIds + newNodeId
                            showAddNodeSheet = false
                            editingNode = newNode
                        }
                    )
                }
                else -> {
                    Scaffold(
                        topBar = {
                            TopAppBar(
                                title = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = "Workflow Builder",
                                            style = MaterialTheme.typography.titleLarge,
                                            fontWeight = FontWeight.Bold
                                        )
                                        if (hasUnsavedChanges) {
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Surface(
                                                color = MaterialTheme.colorScheme.tertiaryContainer,
                                                shape = RoundedCornerShape(4.dp)
                                            ) {
                                                Text(
                                                    text = "Unsaved",
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }
                                },
                                navigationIcon = {
                                    IconButton(onClick = handleBack) {
                                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                                    }
                                },
                                actions = {
                                    Button(
                                        onClick = {
                                            val updated = workflow.copy(
                                                name = name,
                                                description = description,
                                                isEnabled = isEnabled,
                                                nodes = nodes,
                                                connections = connections
                                            )
                                            onSaveWorkflow(updated)
                                        },
                                        modifier = Modifier.padding(end = 12.dp)
                                    ) {
                                        Icon(Icons.Default.Check, contentDescription = null)
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Save")
                                    }
                                },
                                colors = TopAppBarDefaults.topAppBarColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                    actionIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                )
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
                                item { Spacer(modifier = Modifier.height(8.dp)) }

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
                                    ElevatedCard(
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = CardDefaults.elevatedCardColors(
                                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                                        )
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(16.dp),
                                            verticalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            Text(
                                                text = "Workflow Configuration",
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold
                                            )

                                            OutlinedTextField(
                                                value = name,
                                                onValueChange = { name = it },
                                                label = { Text("Workflow Title") },
                                                modifier = Modifier.fillMaxWidth(),
                                                singleLine = true
                                            )

                                            OutlinedTextField(
                                                value = description,
                                                onValueChange = { description = it },
                                                label = { Text("Description") },
                                                modifier = Modifier.fillMaxWidth()
                                            )

                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = "Enable Automated Trigger",
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                                Switch(
                                                    checked = isEnabled,
                                                    onCheckedChange = { isEnabled = it }
                                                )
                                            }

                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Button(
                                                    onClick = { showAddNodeSheet = true },
                                                    colors = ButtonDefaults.buttonColors(
                                                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                                                    )
                                                ) {
                                                    Icon(Icons.Default.Add, contentDescription = null)
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text("Add Pipeline Node")
                                                }

                                                Button(
                                                    onClick = {
                                                        isTesting = true
                                                        testSteps = emptyList()
                                                        showTestStepperSheet = true
                                                        val temp = workflow.copy(
                                                            name = name,
                                                            description = description,
                                                            isEnabled = isEnabled,
                                                            nodes = nodes,
                                                            connections = connections
                                                        )
                                                        if (onTestRunWorkflowWithProgress != null) {
                                                            onTestRunWorkflowWithProgress(
                                                                temp,
                                                                { steps -> testSteps = steps },
                                                                { _ -> isTesting = false }
                                                            )
                                                        } else {
                                                            onTestRunWorkflow(temp) { _ -> isTesting = false }
                                                        }
                                                    },
                                                    enabled = !isTesting
                                                ) {
                                                    if (isTesting) {
                                                        CircularProgressIndicator(
                                                            modifier = Modifier.height(16.dp).width(16.dp),
                                                            strokeWidth = 2.dp,
                                                            color = MaterialTheme.colorScheme.onPrimary
                                                        )
                                                    } else {
                                                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                                                        Text("Test Run")
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }

                                item {
                                    Text(
                                        text = "Pipeline Nodes (${nodes.size})",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                itemsIndexed(nodes) { index, node ->
                                    WorkflowNodeCardItem(
                                        node = node,
                                        isFirst = index == 0,
                                        isUnsaved = unsavedNodeIds.contains(node.id),
                                        hasNotificationListenerPermission = hasNotificationListenerPermission,
                                        hasPostNotificationsPermission = hasPostNotificationsPermission,
                                        hasSmsPermission = hasSmsPermission,
                                        onRequestSmsPermission = onRequestSmsPermission,
                                        onRequestPostNotificationsPermission = onRequestPostNotificationsPermission,
                                        onToggleNode = { enabled ->
                                            nodes = nodes.map { if (it.id == node.id) it.copy(isEnabled = enabled) else it }
                                        },
                                        onEditNode = { editingNode = node },
                                        onDeleteNode = {
                                            if (index > 0) {
                                                pendingDeleteNode = node
                                            }
                                        }
                                    )
                                }

                                item { Spacer(modifier = Modifier.height(30.dp)) }
                            }
                        }
                    }
                }
            }

            // Confirm Delete Node Dialog
            pendingDeleteNode?.let { target ->
                AlertDialog(
                    onDismissRequest = { pendingDeleteNode = null },
                    title = { Text("Remove Node?") },
                    text = { Text("Are you sure you want to remove node '${target.name}' from this workflow?") },
                    confirmButton = {
                        Button(
                            onClick = {
                                nodes = nodes.filterNot { it.id == target.id }
                                connections = connections.filterNot { it.fromNodeId == target.id || it.toNodeId == target.id }
                                pendingDeleteNode = null
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                        ) {
                            Text("Remove Node")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { pendingDeleteNode = null }) {
                            Text("Cancel")
                        }
                    }
                )
            }

            // Test Run Stepper Bottom Sheet
            if (showTestStepperSheet) {
                TestRunLogBottomSheet(
                    workflowName = name,
                    steps = testSteps,
                    isTesting = isTesting,
                    onDismiss = { showTestStepperSheet = false }
                )
            }

            // Discard Unsaved Changes Dialog
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

    // Confirm Delete Node Dialog
    pendingDeleteNode?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingDeleteNode = null },
            title = { Text("Remove Node?") },
            text = { Text("Are you sure you want to remove node '${target.name}' from this workflow?") },
            confirmButton = {
                Button(
                    onClick = {
                        nodes = nodes.filterNot { it.id == target.id }
                        connections = connections.filterNot { it.fromNodeId == target.id || it.toNodeId == target.id }
                        pendingDeleteNode = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Remove Node")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteNode = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Test Run Stepper Bottom Sheet
    if (showTestStepperSheet) {
        TestRunLogBottomSheet(
            workflowName = name,
            steps = testSteps,
            isTesting = isTesting,
            onDismiss = { showTestStepperSheet = false }
        )
    }

    // Discard Unsaved Changes Dialog
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

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun WorkflowNodeCardItem(
    node: WorkflowNode,
    isFirst: Boolean,
    isUnsaved: Boolean = false,
    hasNotificationListenerPermission: Boolean = true,
    hasPostNotificationsPermission: Boolean = true,
    hasSmsPermission: Boolean = true,
    onRequestSmsPermission: (() -> Unit)? = null,
    onRequestPostNotificationsPermission: (() -> Unit)? = null,
    onToggleNode: (Boolean) -> Unit,
    onEditNode: () -> Unit,
    onDeleteNode: () -> Unit
) {
    val context = LocalContext.current

    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { dismissValue ->
            if (!isFirst && (dismissValue == SwipeToDismissBoxValue.EndToStart || dismissValue == SwipeToDismissBoxValue.StartToEnd)) {
                onDeleteNode()
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

    val cardContent: @Composable () -> Unit = {
        OutlinedCard(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onEditNode),
            colors = CardDefaults.outlinedCardColors(
                containerColor = MaterialTheme.colorScheme.surface
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
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = WorkflowUiHelper.getNodeIcon(node.type),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Column {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = node.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                if (isUnsaved) {
                                    Surface(
                                        color = MaterialTheme.colorScheme.tertiaryContainer,
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text(
                                            text = "Unsaved",
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                            Text(
                                text = WorkflowUiHelper.getNodeTitle(node.type),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Switch(
                        checked = node.isEnabled,
                        onCheckedChange = onToggleNode
                    )
                }

                // Permission Error Banner on Node Card
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

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isFirst) {
                        SuggestionChip(
                            onClick = {},
                            label = {
                                Text(
                                    text = "Node #1 (Mandatory Trigger Node)",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            },
                            colors = SuggestionChipDefaults.suggestionChipColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                labelColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        )
                    } else {
                        Spacer(modifier = Modifier.width(1.dp))
                    }

                    // Bottom Right Edit Button
                    OutlinedButton(onClick = onEditNode) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit Node"
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Edit", fontSize = 12.sp)
                    }
                }

                // Preconditions badges
                if (node.preconditions.isNotEmpty()) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
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
                            SuggestionChip(
                                onClick = {},
                                label = {
                                    Text(
                                        text = badgeText,
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                },
                                colors = SuggestionChipDefaults.suggestionChipColors(
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                    labelColor = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            )
                        }
                    }
                }
            }
        }
    }

    if (!isFirst) {
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
                        contentDescription = "Swipe to Delete Node",
                        tint = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        ) {
            cardContent()
        }
    } else {
        cardContent()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TestRunLogBottomSheet(
    workflowName: String,
    steps: List<NodeExecutionStep>,
    isTesting: Boolean,
    onDismiss: () -> Unit
) {
    NoSwipeBottomSheet(
        onDismissRequest = onDismiss,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 12.dp)
        ) {
            // Header Row with Title and Top Right Close Button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 30.dp, bottom = 20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Live Execution Stepper",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = workflowName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isTesting) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .padding(end = 8.dp)
                                .size(20.dp),
                            strokeWidth = 2.dp
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
            }

            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (steps.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Initializing workflow execution...", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    NodeExecutionStepper(steps = steps)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddNodeScreen(
    onBack: () -> Unit,
    onSelectNodeType: (NodeType) -> Unit
) {
    val extractionNodes = listOf(
        NodeType.PARSE_NOTIFICATION,
        NodeType.REGEX_PARSER,
        NodeType.JSON_PARSER,
        NodeType.TEMPLATE_FORMATTER
    )

    val logicNodes = listOf(
        NodeType.CONDITION
    )

    val actionNodes = listOf(
        NodeType.HTTP_ACTION,
        NodeType.NTFY_ACTION,
        NodeType.GOTIFY_ACTION,
        NodeType.SEND_NOTIFICATION_ACTION,
        NodeType.SEND_SMS_ACTION
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Select Pipeline Node") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.Close, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "Choose a step to add to your workflow pipeline.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Category 1: Extraction & Parsing
            Text(
                text = "🔍 Extraction & Parsing",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            extractionNodes.forEach { type ->
                NodeSelectionCard(type = type, onClick = { onSelectNodeType(type) })
            }

            // Category 2: Logic & Flow
            Text(
                text = "🔀 Logic & Filtering",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            logicNodes.forEach { type ->
                NodeSelectionCard(type = type, onClick = { onSelectNodeType(type) })
            }

            // Category 3: Actions & Delivery
            Text(
                text = "🚀 Actions & Delivery",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            actionNodes.forEach { type ->
                NodeSelectionCard(type = type, onClick = { onSelectNodeType(type) })
            }

            Spacer(modifier = Modifier.height(30.dp))
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
                imageVector = WorkflowUiHelper.getNodeIcon(type),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Column {
                Text(
                    text = WorkflowUiHelper.getNodeTitle(type),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = WorkflowUiHelper.getNodeDescription(type),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
