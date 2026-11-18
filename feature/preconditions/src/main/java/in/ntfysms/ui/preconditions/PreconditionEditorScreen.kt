package `in`.ntfysms.ui.preconditions

import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import `in`.ntfysms.data.toDomain
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import `in`.ntfysms.core.ui.components.NoSwipeBottomSheet
import `in`.ntfysms.data.PreconditionEntity
import `in`.ntfysms.domain.engine.NodeExecutionStep
import `in`.ntfysms.domain.engine.PreconditionResult
import `in`.ntfysms.domain.model.Credential
import `in`.ntfysms.domain.model.NodeType
import `in`.ntfysms.domain.model.Precondition
import `in`.ntfysms.domain.model.PreconditionType
import `in`.ntfysms.domain.model.WorkflowConnection
import `in`.ntfysms.domain.model.WorkflowNode
import `in`.ntfysms.ui.workflows.NodeConfigBottomSheet
import `in`.ntfysms.ui.workflows.NodeExecutionStepper
import `in`.ntfysms.ui.workflows.WorkflowUiHelper
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * Editor screen for creating or modifying a Precondition Entity.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PreconditionEditorScreen(
    precondition: PreconditionEntity?,
    credentials: List<Credential> = emptyList(),
    onTestPreconditionWithProgress: ((PreconditionEntity, (List<NodeExecutionStep>) -> Unit, (PreconditionResult) -> Unit) -> Unit)? = null,
    onDismiss: () -> Unit,
    onSave: (PreconditionEntity) -> Unit
) {
    val context = LocalContext.current

    var name by remember { mutableStateOf(precondition?.name ?: "") }
    var selectedType by remember { mutableStateOf(precondition?.type ?: PreconditionType.INTERNET_CHECK.name) }
    var isEnabled by remember { mutableStateOf(precondition?.isEnabled ?: (precondition?.type != PreconditionType.CHAINED_CHECK.name)) }
    var expandedTypeMenu by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showDiscardDialog by remember { mutableStateOf(false) }
    var showTopOverflowMenu by remember { mutableStateOf(false) }

    // Healthcheck HTTP fields
    var targetUrl by remember { mutableStateOf(precondition?.targetUrl ?: "https://example.com/health") }
    var method by remember { mutableStateOf(precondition?.method ?: "GET") }
    var headers by remember { mutableStateOf(precondition?.headers ?: "") }
    var expectedStatus by remember { mutableStateOf((precondition?.expectedStatus ?: 200).toString()) }

    // SIM fields
    var requiredCarrier by remember { mutableStateOf(precondition?.requiredCarrier ?: "") }
    var credentialId by remember { mutableStateOf(precondition?.credentialId ?: "") }
    var expandedCredMenu by remember { mutableStateOf(false) }

    // Failure policy fields
    var onFailureAction by remember { mutableStateOf(precondition?.onFailureAction ?: "FAIL_IMMEDIATELY") }
    var maxRetries by remember { mutableStateOf((precondition?.maxRetries ?: 3).toString()) }
    var retryIntervalSeconds by remember { mutableStateOf((precondition?.retryIntervalSeconds ?: 30).toString()) }
    var showNotificationOnFailure by remember { mutableStateOf(precondition?.showNotificationOnFailure ?: false) }

    LaunchedEffect(precondition) {
        if (precondition != null) {
            name = precondition.name
            selectedType = precondition.type
            isEnabled = precondition.isEnabled
            targetUrl = precondition.targetUrl
            method = precondition.method
            headers = precondition.headers
            expectedStatus = precondition.expectedStatus.toString()
            requiredCarrier = precondition.requiredCarrier
            credentialId = precondition.credentialId
            onFailureAction = precondition.onFailureAction
            maxRetries = precondition.maxRetries.toString()
            retryIntervalSeconds = precondition.retryIntervalSeconds.toString()
            showNotificationOnFailure = precondition.showNotificationOnFailure
        }
    }

    // Testing state & bottom sheet
    var isTestingSheet by remember { mutableStateOf(false) }
    var sheetTestResult by remember { mutableStateOf<PreconditionResult?>(null) }
    var sheetTestSteps by remember { mutableStateOf<List<NodeExecutionStep>>(emptyList()) }
    var showTestStepperSheet by remember { mutableStateOf(false) }

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

    var draggedChainIndex by remember { mutableStateOf<Int?>(null) }
    var dragChainOffsetY by remember { mutableFloatStateOf(0f) }

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

    val hasUnsavedChanges = remember(
        precondition,
        name,
        selectedType,
        isEnabled,
        targetUrl,
        method,
        headers,
        expectedStatus,
        requiredCarrier,
        credentialId,
        pipelineJson,
        onFailureAction,
        maxRetries,
        retryIntervalSeconds,
        showNotificationOnFailure
    ) {
        if (precondition == null) {
            name.isNotBlank() ||
            selectedType != PreconditionType.INTERNET_CHECK.name ||
            !isEnabled ||
            onFailureAction != "FAIL_IMMEDIATELY" ||
            maxRetries != "3" ||
            retryIntervalSeconds != "30" ||
            showNotificationOnFailure ||
            (selectedType == PreconditionType.HEALTH_CHECK.name && (
                targetUrl != "https://example.com/health" ||
                method != "GET" ||
                headers.isNotBlank() ||
                expectedStatus != "200" ||
                credentialId.isNotBlank()
            )) ||
            (selectedType == PreconditionType.SIM_CHECK.name && requiredCarrier.isNotBlank()) ||
            (selectedType == PreconditionType.CHAINED_CHECK.name && chainNodes.isNotEmpty())
        } else {
            name != precondition.name ||
            selectedType != precondition.type ||
            isEnabled != precondition.isEnabled ||
            targetUrl != precondition.targetUrl ||
            method != precondition.method ||
            headers != precondition.headers ||
            expectedStatus != precondition.expectedStatus.toString() ||
            requiredCarrier != precondition.requiredCarrier ||
            credentialId != precondition.credentialId ||
            onFailureAction != precondition.onFailureAction ||
            maxRetries != precondition.maxRetries.toString() ||
            retryIntervalSeconds != precondition.retryIntervalSeconds.toString() ||
            showNotificationOnFailure != precondition.showNotificationOnFailure ||
            (selectedType == PreconditionType.CHAINED_CHECK.name && pipelineJson != precondition.pipelineJson)
        }
    }

    BackHandler(enabled = true) {
        if (isTestingSheet) return@BackHandler
        if (hasUnsavedChanges) {
            showDiscardDialog = true
        } else {
            onDismiss()
        }
    }

    fun reorderChainNodes(fromIndex: Int, toIndex: Int) {
        if (fromIndex <= 0 || toIndex <= 0 || fromIndex >= chainNodes.size || toIndex >= chainNodes.size) return
        val list = chainNodes.toMutableList()
        val item = list.removeAt(fromIndex)
        list.add(toIndex, item)
        chainNodes = list

        val newConns = mutableListOf<WorkflowConnection>()
        for (i in 0 until list.size - 1) {
            newConns.add(WorkflowConnection(fromNodeId = list[i].id, toNodeId = list[i + 1].id))
        }
        chainConnections = newConns
    }

    LaunchedEffect(selectedType) {
        sheetTestResult = null
        if (selectedType == PreconditionType.CHAINED_CHECK.name) {
            val hasTrigger = chainNodes.any { it.type == NodeType.MANUAL_TRIGGER }
            if (!hasTrigger) {
                val triggerId = UUID.randomUUID().toString()
                val triggerNode = WorkflowNode(
                    id = triggerId,
                    name = "Precondition Test Data",
                    type = NodeType.MANUAL_TRIGGER,
                    isEnabled = true,
                    configJson = JSONObject().apply {
                        put("testBody", "{\"code\": \"849201\", \"status\": \"active\"}")
                        put("testSender", "+15551234567")
                    }.toString()
                )
                val restNodes = if (chainNodes.isEmpty()) {
                    val id1 = UUID.randomUUID().toString()
                    val id2 = UUID.randomUUID().toString()
                    val id3 = UUID.randomUUID().toString()
                    listOf(
                        WorkflowNode(id = id1, name = "HTTP Request", type = NodeType.HTTP_ACTION, isEnabled = true),
                        WorkflowNode(id = id2, name = "JSON Parser", type = NodeType.JSON_PARSER, isEnabled = true),
                        WorkflowNode(id = id3, name = "Condition Check", type = NodeType.CONDITION, isEnabled = true)
                    )
                } else chainNodes

                val newNodes = listOf(triggerNode) + restNodes
                chainNodes = newNodes

                val newConns = mutableListOf<WorkflowConnection>()
                for (i in 0 until newNodes.size - 1) {
                    newConns.add(WorkflowConnection(fromNodeId = newNodes[i].id, toNodeId = newNodes[i + 1].id))
                }
                chainConnections = newConns
            }
        }
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
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(if (precondition == null) "Add Precondition" else "Edit Precondition")
                        if (hasUnsavedChanges) {
                            SuggestionChip(
                                onClick = {},
                                label = {
                                    Text(
                                        text = "• Unsaved",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                },
                                colors = SuggestionChipDefaults.suggestionChipColors(
                                    containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.8f),
                                    labelColor = MaterialTheme.colorScheme.onErrorContainer
                                )
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (isTestingSheet) return@IconButton
                            if (hasUnsavedChanges) {
                                showDiscardDialog = true
                            } else {
                                onDismiss()
                            }
                        },
                        enabled = !isTestingSheet
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                },
                actions = {
                    Box {
                        IconButton(
                            onClick = { showTopOverflowMenu = true },
                            enabled = !isTestingSheet
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "More Options"
                            )
                        }

                        DropdownMenu(
                            expanded = showTopOverflowMenu,
                            onDismissRequest = { showTopOverflowMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("View Stepper History") },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.History,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                },
                                enabled = sheetTestSteps.isNotEmpty(),
                                onClick = {
                                    showTopOverflowMenu = false
                                    showTestStepperSheet = true
                                }
                            )
                        }
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
                                pipelineJson = if (selectedType == PreconditionType.CHAINED_CHECK.name) pipelineJson else "",
                                onFailureAction = onFailureAction,
                                maxRetries = maxRetries.toIntOrNull() ?: 3,
                                retryIntervalSeconds = retryIntervalSeconds.toIntOrNull() ?: 30,
                                showNotificationOnFailure = showNotificationOnFailure
                            )
                            isTestingSheet = true
                            showTestStepperSheet = true
                            sheetTestResult = null
                            sheetTestSteps = emptyList()
                            onTestPreconditionWithProgress?.invoke(
                                tempEntity,
                                { steps -> sheetTestSteps = steps },
                                { res ->
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
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Verifying...", fontSize = 12.sp)
                        } else {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Test", fontSize = 12.sp)
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(
                            onClick = {
                                if (hasUnsavedChanges) {
                                    showDiscardDialog = true
                                } else {
                                    onDismiss()
                                }
                            },
                            enabled = !isTestingSheet
                        ) {
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
                                    onFailureAction = onFailureAction,
                                    maxRetries = maxRetries.toIntOrNull() ?: 3,
                                    retryIntervalSeconds = retryIntervalSeconds.toIntOrNull() ?: 30,
                                    showNotificationOnFailure = showNotificationOnFailure,
                                    updatedAt = System.currentTimeMillis()
                                )
                                onSave(entity)
                            },
                            enabled = !isTestingSheet
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
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            if (isTestingSheet) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Text(
                            text = "⚡ Precondition Verification in Progress... Editing is disabled during test execution.",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }

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
                singleLine = true,
                enabled = !isTestingSheet
            )

            // Direct switch toggle without forced execution
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
                Switch(
                    checked = isEnabled,
                    onCheckedChange = { isEnabled = it },
                    enabled = !isTestingSheet
                )
            }

            ExposedDropdownMenuBox(
                expanded = expandedTypeMenu && !isTestingSheet,
                onExpandedChange = { if (!isTestingSheet) expandedTypeMenu = !expandedTypeMenu }
            ) {
                val selectedLabel = typeOptions.find { it.first == selectedType }?.second ?: selectedType
                OutlinedTextField(
                    value = selectedLabel,
                    onValueChange = {},
                    readOnly = true,
                    enabled = !isTestingSheet,
                    label = { Text("Precondition Type") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedTypeMenu) },
                    modifier = Modifier
                        .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                        .fillMaxWidth()
                )

                ExposedDropdownMenu(
                    expanded = expandedTypeMenu && !isTestingSheet,
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
                        singleLine = true,
                        enabled = !isTestingSheet
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
                        singleLine = true,
                        enabled = !isTestingSheet
                    )

                    OutlinedTextField(
                        value = method,
                        onValueChange = { method = it },
                        label = { Text("HTTP Method (GET, HEAD, POST)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        enabled = !isTestingSheet
                    )

                    OutlinedTextField(
                        value = expectedStatus,
                        onValueChange = { expectedStatus = it },
                        label = { Text("Expected Status Code (Default 200)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        enabled = !isTestingSheet
                    )

                    OutlinedTextField(
                        value = headers,
                        onValueChange = { headers = it },
                        label = { Text("Headers (Key: Value)") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                        enabled = !isTestingSheet
                    )

                    val selectedCredName = credentials.find { it.id == credentialId }?.name ?: "None (Optional Auth)"

                    ExposedDropdownMenuBox(
                        expanded = expandedCredMenu && !isTestingSheet,
                        onExpandedChange = { if (!isTestingSheet) expandedCredMenu = !expandedCredMenu }
                    ) {
                        OutlinedTextField(
                            value = selectedCredName,
                            onValueChange = {},
                            readOnly = true,
                            enabled = !isTestingSheet,
                            label = { Text("🔑 Attach Saved Credential") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedCredMenu) },
                            modifier = Modifier
                                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                                .fillMaxWidth()
                        )

                        ExposedDropdownMenu(
                            expanded = expandedCredMenu && !isTestingSheet,
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
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                            enabled = !isTestingSheet
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Add Step", fontSize = 11.sp)
                        }
                    }

                    var chainCardHeightPx by remember { mutableFloatStateOf(100f) }

                    chainNodes.forEachIndexed { index, node ->
                        val isTrigger = node.type == NodeType.MANUAL_TRIGGER
                        val isDragged = draggedChainIndex == index
                        val dragModifier = if (isDragged) {
                            Modifier
                                .zIndex(10f)
                                .graphicsLayer { translationY = dragChainOffsetY }
                        } else Modifier

                        OutlinedCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(dragModifier)
                                .onGloballyPositioned { layoutCoordinates ->
                                    if (layoutCoordinates.size.height > 0) {
                                        chainCardHeightPx = layoutCoordinates.size.height.toFloat()
                                    }
                                }
                                .clickable(enabled = !isTestingSheet, onClick = { editingChainNode = node }),
                            colors = CardDefaults.outlinedCardColors(
                                containerColor = if (isDragged) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surface
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    if (!isTrigger) {
                                        Icon(
                                            imageVector = Icons.Default.DragHandle,
                                            contentDescription = "Drag to reorder",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier
                                                .size(20.dp)
                                                .pointerInput(index, chainNodes.size) {
                                                    if (!isTestingSheet) {
                                                        detectDragGesturesAfterLongPress(
                                                            onDragStart = {
                                                                draggedChainIndex = index
                                                                dragChainOffsetY = 0f
                                                            },
                                                            onDrag = { change, dragAmount ->
                                                                change.consume()
                                                                dragChainOffsetY += dragAmount.y
                                                                val curIdx = draggedChainIndex ?: return@detectDragGesturesAfterLongPress
                                                                val threshold = (chainCardHeightPx * 0.5f).coerceAtLeast(40f)

                                                                if (dragChainOffsetY > threshold && curIdx < chainNodes.size - 1) {
                                                                    reorderChainNodes(curIdx, curIdx + 1)
                                                                    draggedChainIndex = curIdx + 1
                                                                    dragChainOffsetY -= chainCardHeightPx
                                                                } else if (dragChainOffsetY < -threshold && curIdx > 1) {
                                                                    reorderChainNodes(curIdx, curIdx - 1)
                                                                    draggedChainIndex = curIdx - 1
                                                                    dragChainOffsetY += chainCardHeightPx
                                                                }
                                                            },
                                                            onDragEnd = {
                                                                draggedChainIndex = null
                                                                dragChainOffsetY = 0f
                                                            },
                                                            onDragCancel = {
                                                                draggedChainIndex = null
                                                                dragChainOffsetY = 0f
                                                            }
                                                        )
                                                    }
                                                }
                                        )
                                    }
                                    Icon(
                                        imageVector = WorkflowUiHelper.getNodeIcon(node.type),
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Column {
                                        Text(
                                            text = "${index + 1}. ${node.name}",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = if (isTrigger) "Fixed Entry Point" else WorkflowUiHelper.getNodeTitle(node.type),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontSize = 10.sp
                                        )
                                    }
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(
                                        onClick = { editingChainNode = node },
                                        modifier = Modifier.size(28.dp),
                                        enabled = !isTestingSheet
                                    ) {
                                        Icon(Icons.Default.Edit, contentDescription = "Edit", modifier = Modifier.size(15.dp))
                                    }
                                    if (!isTrigger) {
                                        IconButton(
                                            onClick = {
                                                val newNodes = chainNodes.filterNot { it.id == node.id }
                                                chainNodes = newNodes
                                                val newConns = mutableListOf<WorkflowConnection>()
                                                for (i in 0 until newNodes.size - 1) {
                                                    newConns.add(WorkflowConnection(fromNodeId = newNodes[i].id, toNodeId = newNodes[i + 1].id))
                                                }
                                                chainConnections = newConns
                                            },
                                            modifier = Modifier.size(28.dp),
                                            enabled = !isTestingSheet
                                        ) {
                                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(15.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Failure Policy Card
            FailurePolicyCard(
                onFailureAction = onFailureAction,
                onFailureActionChanged = { onFailureAction = it },
                maxRetries = maxRetries,
                onMaxRetriesChanged = { maxRetries = it },
                retryIntervalSeconds = retryIntervalSeconds,
                onRetryIntervalSecondsChanged = { retryIntervalSeconds = it },
                showNotificationOnFailure = showNotificationOnFailure,
                onShowNotificationOnFailureChanged = { showNotificationOnFailure = it }
            )

            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    if (showTestStepperSheet) {
        PreconditionExecutionStepperBottomSheet(
            preconditionName = name.ifBlank { "Precondition Test" },
            steps = sheetTestSteps,
            isTesting = isTestingSheet,
            onDismiss = { showTestStepperSheet = false }
        )
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
            }
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

    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            title = { Text("Discard Changes?") },
            text = { Text("You have unsaved changes in this precondition. Are you sure you want to discard them?") },
            confirmButton = {
                Button(
                    onClick = {
                        showDiscardDialog = false
                        onDismiss()
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddChainNodeDialog(
    onDismiss: () -> Unit,
    onSelectType: (NodeType) -> Unit
) {
    val extractionNodes = listOf(
        NodeType.JSON_PARSER,
        NodeType.REGEX_PARSER,
        NodeType.TEMPLATE_FORMATTER,
        NodeType.PARSE_NOTIFICATION
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

    NoSwipeBottomSheet(
        onDismissRequest = onDismiss
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp, bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Select Pipeline Step Node",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "🔍 Extraction & Parsing",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                extractionNodes.forEach { type ->
                    NodeSelectionCard(type = type, onClick = { onSelectType(type) })
                }

                Text(
                    text = "🔀 Logic & Filtering",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                logicNodes.forEach { type ->
                    NodeSelectionCard(type = type, onClick = { onSelectType(type) })
                }

                Text(
                    text = "🚀 Actions & Delivery",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                actionNodes.forEach { type ->
                    NodeSelectionCard(type = type, onClick = { onSelectType(type) })
                }
            }
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

/**
 * Modal Bottom Sheet container wrapper for creating or editing Precondition entities.
 */
@Composable
fun PreconditionBottomSheet(
    precondition: PreconditionEntity?,
    credentials: List<Credential> = emptyList(),
    onTestPreconditionWithProgress: ((PreconditionEntity, (List<NodeExecutionStep>) -> Unit, (PreconditionResult) -> Unit) -> Unit)? = null,
    onDismiss: () -> Unit,
    onSave: (PreconditionEntity) -> Unit
) {
    NoSwipeBottomSheet(
        onDismissRequest = onDismiss
    ) {
        PreconditionEditorScreen(
            precondition = precondition,
            credentials = credentials,
            onTestPreconditionWithProgress = onTestPreconditionWithProgress,
            onDismiss = onDismiss,
            onSave = onSave
        )
    }
}
