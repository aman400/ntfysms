package `in`.ntfysms.ui.preconditions

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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
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
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import `in`.ntfysms.data.PreconditionEntity
import `in`.ntfysms.domain.engine.NodeExecutionStep
import `in`.ntfysms.domain.engine.PreconditionResult
import `in`.ntfysms.domain.model.Credential
import `in`.ntfysms.domain.model.Precondition
import `in`.ntfysms.domain.model.PreconditionType
import `in`.ntfysms.domain.model.NodeType
import `in`.ntfysms.domain.model.WorkflowConnection
import `in`.ntfysms.domain.model.WorkflowNode
import `in`.ntfysms.ui.MainViewModel
import `in`.ntfysms.ui.components.NoSwipeBottomSheet
import `in`.ntfysms.ui.workflows.NodeConfigBottomSheet
import `in`.ntfysms.ui.workflows.NodeExecutionStepper
import `in`.ntfysms.ui.workflows.WorkflowUiHelper
import org.json.JSONArray
import org.json.JSONObject
import androidx.compose.foundation.layout.PaddingValues
import java.util.UUID

@Composable
fun PreconditionsScreen(
    viewModel: MainViewModel,
    onAddPrecondition: (() -> Unit)? = null,
    onEditPrecondition: ((PreconditionEntity) -> Unit)? = null
) {
    val preconditions by viewModel.preconditions.collectAsState()
    val credentials by viewModel.credentials.collectAsState()

    var editingPrecondition by remember { mutableStateOf<PreconditionEntity?>(null) }
    var pendingDeletePrecondition by remember { mutableStateOf<PreconditionEntity?>(null) }
    var showAddSheet by remember { mutableStateOf(false) }

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
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "Rules Vault",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Configure reusable rules (e.g. check for internet, VPN, or certain app permissions) to guard when automations are allowed to run.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (preconditions.isEmpty()) {
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
                                text = "No preconditions configured yet. Tap '+ Create Precondition' below to add one.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                items(preconditions, key = { it.id }) { prec ->
                    PreconditionCardItem(
                        precondition = prec,
                        viewModel = viewModel,
                        onToggle = { enabled ->
                            viewModel.savePrecondition(prec.copy(isEnabled = enabled))
                        },
                        onEdit = {
                            if (onEditPrecondition != null) onEditPrecondition(prec) else editingPrecondition = prec
                        },
                        onDelete = { pendingDeletePrecondition = prec }
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(80.dp)) }
        }

        ExtendedFloatingActionButton(
            onClick = {
                if (onAddPrecondition != null) onAddPrecondition() else showAddSheet = true
            },
            icon = { Icon(Icons.Default.Add, contentDescription = "Create Precondition") },
            text = { Text("Create Precondition") },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        )
    }

    if (showAddSheet) {
        PreconditionEditorScreen(
            precondition = null,
            credentials = credentials,
            viewModel = viewModel,
            onDismiss = { showAddSheet = false },
            onSave = { newPrec ->
                viewModel.savePrecondition(newPrec)
                showAddSheet = false
            }
        )
        return
    }

    editingPrecondition?.let { prec ->
        PreconditionEditorScreen(
            precondition = prec,
            credentials = credentials,
            viewModel = viewModel,
            onDismiss = { editingPrecondition = null },
            onSave = { updated ->
                viewModel.savePrecondition(updated)
                editingPrecondition = null
            }
        )
        return
    }

    // Confirm Delete Precondition Dialog
    pendingDeletePrecondition?.let { prec ->
        AlertDialog(
            onDismissRequest = { pendingDeletePrecondition = null },
            title = { Text("Delete Precondition?") },
            text = { Text("Are you sure you want to delete precondition '${prec.name}'?") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deletePrecondition(prec.id)
                        pendingDeletePrecondition = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeletePrecondition = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PreconditionCardItem(
    precondition: PreconditionEntity,
    viewModel: MainViewModel,
    onToggle: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    var isTesting by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<PreconditionResult?>(null) }
    var testSteps by remember { mutableStateOf<List<NodeExecutionStep>>(emptyList()) }

    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { dismissValue ->
            if (dismissValue == SwipeToDismissBoxValue.EndToStart || dismissValue == SwipeToDismissBoxValue.StartToEnd) {
                onDelete()
                false
            } else false
        }
    )

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ ->
        isTesting = true
        testResult = null
        testSteps = emptyList()
        viewModel.testPreconditionWithProgress(
            entity = precondition,
            onStepUpdate = { steps -> testSteps = steps },
            onResult = { res ->
                isTesting = false
                testResult = res
            }
        )
    }

    val (iconStr, typeName) = when (precondition.type) {
        PreconditionType.INTERNET_CHECK.name -> "🌐" to "Internet Check"
        PreconditionType.VPN_CHECK.name -> "🔐" to "VPN Check"
        PreconditionType.GPS_CHECK.name -> "📍" to "GPS Enabled Check"
        PreconditionType.SIM_CHECK.name -> "📱" to "SIM Card Check"
        PreconditionType.HEALTH_CHECK.name -> "🩺" to "HTTP Healthcheck"
        else -> "🛡️" to precondition.type
    }

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
            modifier = Modifier.fillMaxWidth(),
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
                        Text(text = iconStr, fontSize = 18.sp)
                        Text(
                            text = precondition.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Switch(
                        checked = precondition.isEnabled,
                        onCheckedChange = onToggle
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SuggestionChip(
                        onClick = {},
                        label = {
                            Text(
                                text = typeName,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold
                            )
                        },
                        colors = SuggestionChipDefaults.suggestionChipColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            labelColor = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    )

                    Text(
                        text = "ID: ${precondition.id.take(8)}",
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (precondition.type == PreconditionType.HEALTH_CHECK.name && precondition.targetUrl.isNotBlank()) {
                    Text(
                        text = "HTTP ${precondition.method} → ${precondition.targetUrl}",
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Inline Verification Result Alert Box
                testResult?.let { result ->
                    val (bg, fg, msg) = when (result) {
                        is PreconditionResult.Passed -> Triple(
                            MaterialTheme.colorScheme.primaryContainer,
                            MaterialTheme.colorScheme.onPrimaryContainer,
                            "✅ Passed: Precondition verification succeeded!"
                        )
                        is PreconditionResult.Failed -> Triple(
                            MaterialTheme.colorScheme.errorContainer,
                            MaterialTheme.colorScheme.onErrorContainer,
                            "❌ Failed: ${result.reason}"
                        )
                    }

                    val targetPermissionToRequest = when {
                        precondition.type == PreconditionType.GPS_CHECK.name && result is PreconditionResult.Failed && result.reason.contains("Missing", ignoreCase = true) -> "android.permission.ACCESS_FINE_LOCATION"
                        precondition.type == PreconditionType.SIM_CHECK.name && result is PreconditionResult.Failed && result.reason.contains("Missing", ignoreCase = true) -> "android.permission.READ_PHONE_STATE"
                        else -> null
                    }

                    val stepsToShow = if (isTesting) testSteps else (when (result) {
                        is PreconditionResult.Passed -> result.steps
                        is PreconditionResult.Failed -> result.steps
                    })

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(bg, shape = RoundedCornerShape(8.dp))
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = msg,
                            style = MaterialTheme.typography.bodySmall,
                            color = fg,
                            fontWeight = FontWeight.Bold
                        )

                        if (stepsToShow.isNotEmpty()) {
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                color = MaterialTheme.colorScheme.surface,
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text(
                                        text = "Chained Pipeline Stepper",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(bottom = 6.dp)
                                    )
                                    NodeExecutionStepper(steps = stepsToShow)
                                }
                            }
                        }

                        if (result is PreconditionResult.Failed && result.reason.contains("Missing", ignoreCase = true)) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.align(Alignment.End)
                            ) {
                                if (targetPermissionToRequest != null) {
                                    Button(
                                        onClick = {
                                            permissionLauncher.launch(targetPermissionToRequest)
                                        }
                                    ) {
                                        Text("Grant Permission", fontSize = 11.sp)
                                    }
                                }

                                OutlinedButton(
                                    onClick = {
                                        try {
                                            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                                data = android.net.Uri.fromParts("package", context.packageName, null)
                                            }
                                            context.startActivity(intent)
                                        } catch (_: Exception) {}
                                    }
                                ) {
                                    Text("App Settings", fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = {
                            isTesting = true
                            testResult = null
                            testSteps = emptyList()
                            viewModel.testPreconditionWithProgress(
                                entity = precondition,
                                onStepUpdate = { steps -> testSteps = steps },
                                onResult = { res ->
                                    isTesting = false
                                    testResult = res
                                }
                            )
                        },
                        enabled = !isTesting
                    ) {
                        if (isTesting) {
                            CircularProgressIndicator(
                                modifier = Modifier.height(16.dp).width(16.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Verifying...", fontSize = 12.sp)
                        } else {
                            Icon(Icons.Default.PlayArrow, contentDescription = null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Verify Check", fontSize = 12.sp)
                        }
                    }

                    // Bottom Right Edit Button
                    OutlinedButton(onClick = onEdit) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit Precondition"
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
fun PreconditionEditorScreen(
    precondition: PreconditionEntity?,
    credentials: List<Credential> = emptyList(),
    viewModel: MainViewModel,
    onDismiss: () -> Unit,
    onSave: (PreconditionEntity) -> Unit
) {
    val context = LocalContext.current

    BackHandler(enabled = true) {
        onDismiss()
    }

    var name by remember { mutableStateOf(precondition?.name ?: "") }
    var selectedType by remember { mutableStateOf(precondition?.type ?: PreconditionType.INTERNET_CHECK.name) }
    var isEnabled by remember { mutableStateOf(precondition?.isEnabled ?: true) }
    var expandedTypeMenu by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Healthcheck HTTP fields
    var targetUrl by remember { mutableStateOf(precondition?.targetUrl ?: "https://example.com/health") }
    var method by remember { mutableStateOf(precondition?.method ?: "GET") }
    var headers by remember { mutableStateOf(precondition?.headers ?: "") }
    var expectedStatus by remember { mutableStateOf((precondition?.expectedStatus ?: 200).toString()) }

    // SIM fields
    var requiredCarrier by remember { mutableStateOf(precondition?.requiredCarrier ?: "") }
    var credentialId by remember { mutableStateOf(precondition?.credentialId ?: "") }
    var expandedCredMenu by remember { mutableStateOf(false) }

    // Testing state
    var isTestingSheet by remember { mutableStateOf(false) }
    var sheetTestResult by remember { mutableStateOf<PreconditionResult?>(null) }
    var sheetTestSteps by remember { mutableStateOf<List<NodeExecutionStep>>(emptyList()) }

    var chainNodes by remember {
        mutableStateOf(
            (precondition?.toDomain() as? Precondition.ChainCheck)?.nodes ?: emptyList()
        )
    }
    var chainConnections by remember {
        mutableStateOf(
            (precondition?.toDomain() as? Precondition.ChainCheck)?.connections ?: emptyList()
        )
    }
    var editingChainNode by remember { mutableStateOf<WorkflowNode?>(null) }
    var showAddNodeDialog by remember { mutableStateOf(false) }

    LaunchedEffect(selectedType) {
        sheetTestResult = null
        if (selectedType == PreconditionType.CHAINED_CHECK.name && chainNodes.isEmpty()) {
            val id1 = UUID.randomUUID().toString()
            val id2 = UUID.randomUUID().toString()
            val id3 = UUID.randomUUID().toString()
            chainNodes = listOf(
                WorkflowNode(id = id1, name = "HTTP Request", type = NodeType.HTTP_ACTION, isEnabled = true),
                WorkflowNode(id = id2, name = "JSON Parser", type = NodeType.JSON_PARSER, isEnabled = true),
                WorkflowNode(id = id3, name = "Condition Check", type = NodeType.CONDITION, isEnabled = true)
            )
            chainConnections = listOf(
                WorkflowConnection(fromNodeId = id1, toNodeId = id2),
                WorkflowConnection(fromNodeId = id2, toNodeId = id3)
            )
        }
    }

    val pipelineJson = remember(chainNodes, chainConnections) {
        JSONObject().apply {
            val nodesArr = JSONArray()
            chainNodes.forEach { node ->
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
            chainConnections.forEach { conn ->
                connArr.put(JSONObject().apply {
                    put("fromNodeId", conn.fromNodeId)
                    put("toNodeId", conn.toNodeId)
                    put("outputPort", conn.outputPort)
                })
            }
            put("connections", connArr)
        }.toString()
    }

    val sheetPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ ->
        val tempEntity = PreconditionEntity(
            id = precondition?.id ?: UUID.randomUUID().toString(),
            name = name,
            type = selectedType,
            isEnabled = isEnabled,
            targetUrl = targetUrl,
            method = method,
            headers = headers,
            expectedStatus = expectedStatus.toIntOrNull() ?: 200,
            requiredCarrier = requiredCarrier,
            credentialId = credentialId,
            pipelineJson = if (selectedType == PreconditionType.CHAINED_CHECK.name) pipelineJson else ""
        )
        isTestingSheet = true
        sheetTestResult = null
        sheetTestSteps = emptyList()
        viewModel.testPreconditionWithProgress(
            entity = tempEntity,
            onStepUpdate = { steps -> sheetTestSteps = steps },
            onResult = { res ->
                isTestingSheet = false
                sheetTestResult = res
            }
        )
    }

    val typeOptions = listOf(
        PreconditionType.INTERNET_CHECK.name to "🌐 Network / Internet Check",
        PreconditionType.VPN_CHECK.name to "🔐 VPN Check (Tailscale/WireGuard)",
        PreconditionType.GPS_CHECK.name to "📍 GPS / Location Enabled Check",
        PreconditionType.SIM_CHECK.name to "📱 SIM Card Active Check",
        PreconditionType.HEALTH_CHECK.name to "🩺 HTTP Healthcheck (Reusing HTTP Node)",
        PreconditionType.CHAINED_CHECK.name to "⛓️ Chained Pipeline (HTTP → Parse → Condition)"
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (precondition == null) "Create Precondition" else "Edit Precondition") },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }
            )
        },
        bottomBar = {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surfaceContainer,
                tonalElevation = 6.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = {
                            val tempEntity = PreconditionEntity(
                                id = precondition?.id ?: UUID.randomUUID().toString(),
                                name = name,
                                type = selectedType,
                                isEnabled = isEnabled,
                                targetUrl = targetUrl,
                                method = method,
                                headers = headers,
                                expectedStatus = expectedStatus.toIntOrNull() ?: 200,
                                requiredCarrier = requiredCarrier,
                                credentialId = credentialId,
                                pipelineJson = if (selectedType == PreconditionType.CHAINED_CHECK.name) pipelineJson else ""
                            )
                            isTestingSheet = true
                            sheetTestResult = null
                            sheetTestSteps = emptyList()
                            viewModel.testPreconditionWithProgress(
                                entity = tempEntity,
                                onStepUpdate = { steps -> sheetTestSteps = steps },
                                onResult = { res ->
                                    isTestingSheet = false
                                    sheetTestResult = res
                                }
                            )
                        },
                        enabled = !isTestingSheet
                    ) {
                        if (isTestingSheet) {
                            CircularProgressIndicator(
                                modifier = Modifier.height(16.dp).width(16.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(Icons.Default.PlayArrow, contentDescription = null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Test", fontSize = 12.sp)
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = onDismiss) {
                            Text("Cancel")
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Button(
                            onClick = {
                                if (name.isBlank()) {
                                    errorMessage = "Precondition name cannot be blank."
                                    return@Button
                                }
                                if (selectedType == PreconditionType.HEALTH_CHECK.name && targetUrl.isBlank()) {
                                    errorMessage = "Healthcheck target URL cannot be blank."
                                    return@Button
                                }
                                errorMessage = null

                                val entity = (precondition ?: PreconditionEntity(
                                    id = UUID.randomUUID().toString(),
                                    name = name,
                                    type = selectedType
                                )).copy(
                                    name = name,
                                    type = selectedType,
                                    isEnabled = isEnabled,
                                    targetUrl = targetUrl,
                                    method = method,
                                    headers = headers,
                                    expectedStatus = expectedStatus.toIntOrNull() ?: 200,
                                    requiredCarrier = requiredCarrier,
                                    credentialId = credentialId,
                                    pipelineJson = if (selectedType == PreconditionType.CHAINED_CHECK.name) pipelineJson else "",
                                    updatedAt = System.currentTimeMillis()
                                )
                                onSave(entity)
                            }
                        ) {
                            Text("Save")
                        }
                    }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp)
                .imePadding()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
                errorMessage?.let { err ->
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = err,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = { errorMessage = null }, modifier = Modifier.size(20.dp)) {
                                Icon(Icons.Default.Close, contentDescription = "Dismiss", tint = MaterialTheme.colorScheme.onErrorContainer)
                            }
                        }
                    }
                }
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Precondition Name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Precondition Enabled",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Switch(checked = isEnabled, onCheckedChange = { isEnabled = it })
                }

                ExposedDropdownMenuBox(
                    expanded = expandedTypeMenu,
                    onExpandedChange = { expandedTypeMenu = !expandedTypeMenu }
                ) {
                    val selectedLabel = typeOptions.find { it.first == selectedType }?.second ?: selectedType
                    OutlinedTextField(
                        value = selectedLabel,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Precondition Type") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedTypeMenu) },
                        modifier = Modifier
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                            .fillMaxWidth()
                    )

                    ExposedDropdownMenu(
                        expanded = expandedTypeMenu,
                        onDismissRequest = { expandedTypeMenu = false }
                    ) {
                        typeOptions.forEach { (typeVal, label) ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = {
                                    selectedType = typeVal
                                    expandedTypeMenu = false
                                }
                            )
                        }
                    }
                }

                when (selectedType) {
                    PreconditionType.INTERNET_CHECK.name -> {
                        Text(
                            text = "Verifies active Internet connection before executing the node.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    PreconditionType.VPN_CHECK.name -> {
                        Text(
                            text = "Verifies active VPN connection (Tailscale, WireGuard, OpenVPN, tun) before executing.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    PreconditionType.GPS_CHECK.name -> {
                        Text(
                            text = "Verifies Location permissions (ACCESS_FINE_LOCATION) and checks if GPS/Location hardware is enabled.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    PreconditionType.SIM_CHECK.name -> {
                        Text(
                            text = "Verifies Phone state permissions (READ_PHONE_STATE) and checks active SIM card status.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedTextField(
                            value = requiredCarrier,
                            onValueChange = { requiredCarrier = it },
                            label = { Text("Required Carrier Name (Optional)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                    }
                    PreconditionType.HEALTH_CHECK.name -> {
                        Text(
                            text = "Reuses HTTP node logic: performs a healthcheck HTTP request to a target server.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        OutlinedTextField(
                            value = targetUrl,
                            onValueChange = { targetUrl = it },
                            label = { Text("Healthcheck Target URL") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )

                        OutlinedTextField(
                            value = method,
                            onValueChange = { method = it },
                            label = { Text("HTTP Method (GET, HEAD, POST)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )

                        OutlinedTextField(
                            value = expectedStatus,
                            onValueChange = { expectedStatus = it },
                            label = { Text("Expected Status Code (Default 200)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )

                        OutlinedTextField(
                            value = headers,
                            onValueChange = { headers = it },
                            label = { Text("Headers (Key: Value)") },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 2
                        )

                        val selectedCredName = credentials.find { it.id == credentialId }?.name ?: "None (Optional Auth)"

                        ExposedDropdownMenuBox(
                            expanded = expandedCredMenu,
                            onExpandedChange = { expandedCredMenu = !expandedCredMenu }
                        ) {
                            OutlinedTextField(
                                value = selectedCredName,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("🔑 Attach Saved Credential") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedCredMenu) },
                                modifier = Modifier
                                    .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                                    .fillMaxWidth()
                            )

                            ExposedDropdownMenu(
                                expanded = expandedCredMenu,
                                onDismissRequest = { expandedCredMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("None") },
                                    onClick = {
                                        credentialId = ""
                                        expandedCredMenu = false
                                    }
                                )
                                credentials.forEach { cred ->
                                    DropdownMenuItem(
                                        text = { Text("🔑 ${cred.name} (${cred.type.name})") },
                                        onClick = {
                                            credentialId = cred.id
                                            expandedCredMenu = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                    PreconditionType.CHAINED_CHECK.name -> {
                        Text(
                            text = "Chained Pipeline: Execute an HTTP request, parse data (JSON/Regex), and evaluate with a condition check.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Pipeline Steps (${chainNodes.size})",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            OutlinedButton(
                                onClick = { showAddNodeDialog = true },
                                modifier = Modifier.height(32.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Add Step", fontSize = 11.sp)
                            }
                        }

                        chainNodes.forEachIndexed { index, node ->
                            OutlinedCard(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(onClick = { editingChainNode = node }),
                                colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
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
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Column {
                                            Text(
                                                text = "${index + 1}. ${node.name}",
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = WorkflowUiHelper.getNodeTitle(node.type),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }

                                    Row {
                                        IconButton(
                                            onClick = { editingChainNode = node },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(Icons.Default.Edit, contentDescription = "Edit", modifier = Modifier.size(16.dp))
                                        }
                                        if (chainNodes.size > 1) {
                                            IconButton(
                                                onClick = {
                                                    chainNodes = chainNodes.filterNot { it.id == node.id }
                                                    chainConnections = chainConnections.filterNot { it.fromNodeId == node.id || it.toNodeId == node.id }
                                                },
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                sheetTestResult?.let { res ->
                    val (bg, fg, msg) = when (res) {
                        is PreconditionResult.Passed -> Triple(
                            MaterialTheme.colorScheme.primaryContainer,
                            MaterialTheme.colorScheme.onPrimaryContainer,
                            "✅ Passed: Precondition verification succeeded!"
                        )
                        is PreconditionResult.Failed -> Triple(
                            MaterialTheme.colorScheme.errorContainer,
                            MaterialTheme.colorScheme.onErrorContainer,
                            "❌ Failed: ${res.reason}"
                        )
                    }

                    val sheetTargetPerm = when {
                        selectedType == PreconditionType.GPS_CHECK.name && res is PreconditionResult.Failed && res.reason.contains("Missing", ignoreCase = true) -> "android.permission.ACCESS_FINE_LOCATION"
                        selectedType == PreconditionType.SIM_CHECK.name && res is PreconditionResult.Failed && res.reason.contains("Missing", ignoreCase = true) -> "android.permission.READ_PHONE_STATE"
                        else -> null
                    }

                    val sheetStepsToShow = if (isTestingSheet) sheetTestSteps else (when (res) {
                        is PreconditionResult.Passed -> res.steps
                        is PreconditionResult.Failed -> res.steps
                    })

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(bg, shape = RoundedCornerShape(8.dp))
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = msg,
                            style = MaterialTheme.typography.bodySmall,
                            color = fg,
                            fontWeight = FontWeight.Bold
                        )

                        if (sheetStepsToShow.isNotEmpty()) {
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                color = MaterialTheme.colorScheme.surface,
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text(
                                        text = "Chained Pipeline Stepper",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(bottom = 6.dp)
                                    )
                                    NodeExecutionStepper(steps = sheetStepsToShow)
                                }
                            }
                        }

                        if (res is PreconditionResult.Failed && res.reason.contains("Missing", ignoreCase = true)) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.align(Alignment.End)
                            ) {
                                if (sheetTargetPerm != null) {
                                    Button(
                                        onClick = {
                                            sheetPermissionLauncher.launch(sheetTargetPerm)
                                        }
                                    ) {
                                        Text("Grant Permission", fontSize = 11.sp)
                                    }
                                }

                                OutlinedButton(
                                    onClick = {
                                        try {
                                            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                                data = android.net.Uri.fromParts("package", context.packageName, null)
                                            }
                                            context.startActivity(intent)
                                        } catch (_: Exception) {}
                                    }
                                ) {
                                    Text("App Settings", fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Sticky Bottom Action Bar
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surfaceContainer,
                tonalElevation = 6.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = {
                            val tempEntity = PreconditionEntity(
                                id = precondition?.id ?: UUID.randomUUID().toString(),
                                name = name,
                                type = selectedType,
                                isEnabled = isEnabled,
                                targetUrl = targetUrl,
                                method = method,
                                headers = headers,
                                expectedStatus = expectedStatus.toIntOrNull() ?: 200,
                                requiredCarrier = requiredCarrier,
                                credentialId = credentialId
                            )
                            isTestingSheet = true
                            sheetTestResult = null
                            viewModel.testPrecondition(tempEntity) { res ->
                                isTestingSheet = false
                                sheetTestResult = res
                            }
                        },
                        enabled = !isTestingSheet
                    ) {
                        if (isTestingSheet) {
                            CircularProgressIndicator(
                                modifier = Modifier.height(16.dp).width(16.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(Icons.Default.PlayArrow, contentDescription = null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Test", fontSize = 12.sp)
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = onDismiss) {
                            Text("Cancel")
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Button(
                            onClick = {
                                if (name.isBlank()) {
                                    errorMessage = "Precondition name cannot be blank."
                                    return@Button
                                }
                                if (selectedType == PreconditionType.HEALTH_CHECK.name && targetUrl.isBlank()) {
                                    errorMessage = "Healthcheck target URL cannot be blank."
                                    return@Button
                                }
                                errorMessage = null

                                val entity = (precondition ?: PreconditionEntity(
                                    id = UUID.randomUUID().toString(),
                                    name = name,
                                    type = selectedType
                                )).copy(
                                    name = name,
                                    type = selectedType,
                                    isEnabled = isEnabled,
                                    targetUrl = targetUrl,
                                    method = method,
                                    headers = headers,
                                    expectedStatus = expectedStatus.toIntOrNull() ?: 200,
                                    requiredCarrier = requiredCarrier,
                                    credentialId = credentialId,
                                    pipelineJson = if (selectedType == PreconditionType.CHAINED_CHECK.name) pipelineJson else "",
                                    updatedAt = System.currentTimeMillis()
                                )
                                onSave(entity)
                            }
                        ) {
                            Text("Save")
                        }
                    }
                }
            }
        }

    editingChainNode?.let { node ->
        NodeConfigBottomSheet(
            node = node,
            credentials = credentials,
            vaultPreconditions = emptyList(),
            onDismiss = { editingChainNode = null },
            onSave = { updated ->
                chainNodes = chainNodes.map { if (it.id == updated.id) updated else it }
                editingChainNode = null
            },
            viewModel = viewModel
        )
    }

    if (showAddNodeDialog) {
        AddChainNodeDialog(
            onDismiss = { showAddNodeDialog = false },
            onSelectType = { type ->
                val newNodeId = UUID.randomUUID().toString()
                val newNode = WorkflowNode(
                    id = newNodeId,
                    name = WorkflowUiHelper.getNodeTitle(type),
                    type = type,
                    isEnabled = true
                )
                val lastId = chainNodes.lastOrNull()?.id
                chainNodes = chainNodes + newNode
                if (lastId != null) {
                    chainConnections = chainConnections + WorkflowConnection(fromNodeId = lastId, toNodeId = newNodeId)
                }
                showAddNodeDialog = false
                editingChainNode = newNode
            }
        )
    }
}

@Composable
fun AddChainNodeDialog(
    onDismiss: () -> Unit,
    onSelectType: (NodeType) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select Pipeline Step Type") },
        text = {
            Box(modifier = Modifier.heightIn(max = 400.dp)) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val nodeTypes = listOf(
                        NodeType.HTTP_ACTION to "🌐 HTTP Request Action",
                        NodeType.JSON_PARSER to "🔍 JSON Property Parser",
                        NodeType.REGEX_PARSER to "🔍 Regex Pattern Extractor",
                        NodeType.TEMPLATE_FORMATTER to "📝 Template Formatter",
                        NodeType.CONDITION to "🔀 Condition Check (If / Evaluate)"
                    )
                    nodeTypes.forEach { (type, label) ->
                        OutlinedButton(
                            onClick = { onSelectType(type) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(label)
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun PreconditionBottomSheet(
    precondition: PreconditionEntity?,
    credentials: List<Credential> = emptyList(),
    viewModel: MainViewModel,
    onDismiss: () -> Unit,
    onSave: (PreconditionEntity) -> Unit
) {
    NoSwipeBottomSheet(
        onDismissRequest = onDismiss
    ) {
        PreconditionEditorScreen(
            precondition = precondition,
            credentials = credentials,
            viewModel = viewModel,
            onDismiss = onDismiss,
            onSave = onSave
        )
    }
}
