package dev.zophop.ui.workflows

import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import dev.zophop.domain.engine.WorkflowEngine
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import androidx.compose.foundation.Image
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Checkbox
import dev.zophop.core.ui.components.NoSwipeBottomSheet
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.zophop.core.ui.components.CredentialBottomSheet
import dev.zophop.core.ui.components.HeaderInputEditor
import dev.zophop.core.ui.components.InfoTooltipField
import dev.zophop.core.ui.components.SearchableDropdownSelector
import dev.zophop.core.ui.components.rememberAlwaysFloatingInteractionSource
import dev.zophop.data.PreconditionEntity
import dev.zophop.data.toDomain
import dev.zophop.domain.engine.NodeExecutionStep
import dev.zophop.domain.engine.PreconditionResult
import dev.zophop.domain.model.Credential
import dev.zophop.domain.model.NodeType
import dev.zophop.domain.model.WorkflowNode
import org.json.JSONObject
import androidx.core.net.toUri

private data class Quadruple<A, B, C, D>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NodeConfigBottomSheet(
    node: WorkflowNode,
    credentials: List<Credential> = emptyList(),
    vaultPreconditions: List<PreconditionEntity> = emptyList(),
    showPreconditionsGuard: Boolean = true,
    hasNotificationListenerPermission: Boolean = true,
    hasPostNotificationsPermission: Boolean = true,
    hasSmsPermission: Boolean = true,
    onRequestSmsPermission: (() -> Unit)? = null,
    onRequestPostNotificationsPermission: (() -> Unit)? = null,
    onDismiss: () -> Unit,
    onSave: (WorkflowNode) -> Unit,
    onSaveCredential: ((Credential) -> Unit)? = null,
    onSavePrecondition: ((PreconditionEntity) -> Unit)? = null,
    onRequestCreatePrecondition: ((onCreated: (PreconditionEntity) -> Unit) -> Unit)? = null,
    onTestPreconditionWithProgress: ((PreconditionEntity, (List<NodeExecutionStep>) -> Unit, (PreconditionResult) -> Unit) -> Unit)? = null
) {
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    val unifiedRegistry = remember { dev.zophop.domain.engine.plugin.UnifiedPluginRegistry(context) }
    val externalPluginRegistry = remember { dev.zophop.domain.engine.plugin.ExternalPluginRegistry(context) }
    var pluginInfo by remember { mutableStateOf<dev.zophop.domain.engine.plugin.ExternalPluginServiceInfo?>(null) }
    var pluginManifest by remember { mutableStateOf<dev.zophop.plugin.api.NodePluginManifest?>(null) }

    androidx.compose.runtime.LaunchedEffect(node.type) {
        pluginManifest = unifiedRegistry.getManifestByTypeId(node.type)
        val plugins = externalPluginRegistry.discoverPlugins()
        pluginInfo = plugins.find { it.manifest.typeId == node.type }
    }

    val sendSmsPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ -> }

    var notifPerm by remember { mutableStateOf(isNotificationListenerEnabled(context)) }
    var smsPerm by remember {
        mutableStateOf(
            androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECEIVE_SMS) == android.content.pm.PackageManager.PERMISSION_GRANTED
        )
    }
    var sendSmsPerm by remember {
        mutableStateOf(
            androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.SEND_SMS) == android.content.pm.PackageManager.PERMISSION_GRANTED
        )
    }
    var postNotifPerm by remember {
        mutableStateOf(
            if (android.os.Build.VERSION.SDK_INT >= 33) {
                androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED
            } else true
        )
    }
    var permissionCheckTrigger by remember { mutableIntStateOf(0) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                notifPerm = isNotificationListenerEnabled(context)
                smsPerm = androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECEIVE_SMS) == android.content.pm.PackageManager.PERMISSION_GRANTED
                sendSmsPerm = androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.SEND_SMS) == android.content.pm.PackageManager.PERMISSION_GRANTED
                postNotifPerm = if (android.os.Build.VERSION.SDK_INT >= 33) {
                    androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED
                } else true
                permissionCheckTrigger++
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val (isMissingPerm, missingPermTitle, missingPermDesc, onGrantClick) = remember(permissionCheckTrigger, pluginManifest, pluginInfo, notifPerm, smsPerm, sendSmsPerm, postNotifPerm) {
        when {
            node.type.contains("NOTIFICATION_TRIGGER") && !notifPerm -> Quadruple(
                true,
                "⚠️ Notification Listener Permission Missing",
                "Node is disabled. Grant Notification Access in system settings to enable.",
                {
                    try {
                        context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                    } catch (_: Exception) {}
                }
            )
            pluginManifest?.requiredPermissions?.isNotEmpty() == true && pluginInfo != null -> {
                val info = pluginInfo!!
                val manifest = info.manifest
                val isGranted = manifest.requiredPermissions.all { perm ->
                    context.packageManager.checkPermission(perm, info.packageName) == android.content.pm.PackageManager.PERMISSION_GRANTED
                }
                if (!isGranted) {
                    Quadruple(
                        true,
                        "⚠️ Plugin Permission Missing",
                        "Node requires ${manifest.requiredPermissions.joinToString(", ")}. Grant in Plugin App.",
                        {
                            if (manifest.setupActivity != null) {
                                try {
                                    val intent = Intent().apply {
                                        component = android.content.ComponentName(info.packageName, manifest.setupActivity!!)
                                    }
                                    context.startActivity(intent)
                                } catch (_: Exception) {
                                    try {
                                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                            data = ("package:" + info.packageName).toUri()
                                        }
                                        context.startActivity(intent)
                                    } catch (_: Exception) {}
                                }
                            } else {
                                try {
                                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                        data = ("package:" + info.packageName).toUri()
                                    }
                                    context.startActivity(intent)
                                } catch (_: Exception) {}
                            }
                        }
                    )
                } else {
                    Quadruple(false, "", "", {})
                }
            }
            node.type.contains("SMS_TRIGGER") && !smsPerm -> Quadruple(
                true,
                "⚠️ SMS Permission Missing (RECEIVE_SMS)",
                "Node is disabled. Grant SMS permission to intercept incoming text messages.",
                { onRequestSmsPermission?.invoke() }
            )
            node.type.contains("SEND_SMS_ACTION") && !sendSmsPerm -> Quadruple(
                true,
                "⚠️ Send SMS Permission Missing (SEND_SMS)",
                "Node is disabled. Grant SEND_SMS permission to send text messages via SIM card.",
                { sendSmsPermissionLauncher.launch(android.Manifest.permission.SEND_SMS) }
            )
            node.type.contains("SEND_NOTIFICATION_ACTION") && android.os.Build.VERSION.SDK_INT >= 33 && !postNotifPerm -> Quadruple(
                true,
                "⚠️ Notification Permission Missing (POST_NOTIFICATIONS)",
                "Node is disabled. Grant POST_NOTIFICATIONS permission to display notifications.",
                { onRequestPostNotificationsPermission?.invoke() }
            )
            else -> Quadruple(false, null, null, null)
        }
    }

    var name by remember { mutableStateOf(node.name) }
    var isEnabled by remember { mutableStateOf(node.isEnabled) }

    val initialConfigMap = remember(node.configJson) {
        val map = mutableMapOf<String, String>()
        try {
            val json = JSONObject(node.configJson)
            val keys = json.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                map[k] = json.optString(k, "")
            }
        } catch (_: Exception) {}
        map
    }
    var configStateMap by remember(initialConfigMap) { mutableStateOf<Map<String, String>>(initialConfigMap) }
    var credentialId by remember(configStateMap) { mutableStateOf(configStateMap["credentialId"] ?: "") }

    // Preconditions State - Attached Vault Precondition IDs
    var attachedVaultPrecIds by remember {
        mutableStateOf(
            vaultPreconditions.filter { prec ->
                node.preconditions.any { it.id == prec.id || it.type.name == prec.type }
            }.map { it.id }.toSet()
        )
    }

    var showCreateCredSheet by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    BackHandler(enabled = true) {
        onDismiss()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Configure Node") },
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
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            keyboardController?.hide()
                            focusManager.clearFocus()
                            if (name.isBlank()) {
                                errorMessage = "Node name cannot be blank."
                                return@Button
                            }

                            val configMapForValidation = mutableMapOf<String, String>()
                            for ((k, v) in configStateMap) {
                                configMapForValidation[k] = v
                            }
                            configMapForValidation["credentialId"] = credentialId

                            val validationError = validateNodeConfig(node.type, configMapForValidation)
                            if (validationError != null) {
                                errorMessage = validationError
                                return@Button
                            }
                            errorMessage = null

                            val newConfig = JSONObject().apply {
                                for ((k, v) in configStateMap) {
                                    put(k, v)
                                }
                                put("credentialId", credentialId)
                            }

                            val attachedPreconditions = if (showPreconditionsGuard) {
                                vaultPreconditions
                                    .filter { attachedVaultPrecIds.contains(it.id) && it.isEnabled }
                                    .map { it.toDomain() }
                            } else emptyList()

                            val updatedNode = node.copy(
                                name = name,
                                isEnabled = if (isMissingPerm) false else isEnabled,
                                configJson = newConfig.toString(),
                                preconditions = attachedPreconditions
                            )
                            onSave(updatedNode)
                        }
                    ) {
                        Text("Save Node")
                    }
                }
            }
        }
    ) { padding ->
        // Scrollable Content
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Spacer(modifier = Modifier.height(4.dp))
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

            InfoTooltipField(
                value = name,
                onValueChange = { name = it },
                label = "Node Name",
                tooltipText = "Unique name identifying this step in the workflow pipeline.",
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            // Permission Warning Banner
            if (isMissingPerm && missingPermTitle != null && missingPermDesc != null && onGrantClick != null) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
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
            }

            // Credential attachment selector for Actions
            if (node.type.contains("HTTP_ACTION") || node.type.contains("NTFY_ACTION") || node.type.contains("GOTIFY_ACTION") || node.type.contains("TELEGRAM_ACTION") || pluginManifest?.requiredCredentialType != null) {
                val selectedCred = credentials.find { it.id == credentialId }
                val credName = selectedCred?.let { "🔑 ${it.name} (${it.type})" } ?: "None (Optional Credential)"

                SearchableDropdownSelector(
                    selectedItemText = credName,
                    label = "🔑 Attach Saved Credential",
                    searchPlaceholder = "Search credentials...",
                    items = credentials,
                    itemText = { cred -> "${cred.name} (${cred.type})" },
                    onItemSelected = { cred ->
                        credentialId = cred?.id ?: ""
                    },
                    isItemSelected = { cred -> cred.id == credentialId },
                    showNoneOption = true,
                    noneOptionText = "None",
                    onCreateNewClick = {
                        showCreateCredSheet = true
                    },
                    createNewText = "➕ Create New Credential...",
                    tooltipText = "Attach saved vault credentials for authenticated server requests.",
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Dynamic Manifest-Driven Config Fields
            if (pluginManifest != null && pluginManifest!!.configSchema.isNotEmpty()) {
                val schema = pluginManifest!!.configSchema
                val mainFields = schema.filter { !it.isTestField && !it.key.startsWith("test", ignoreCase = true) }
                val testFields = schema.filter { it.isTestField || it.key.startsWith("test", ignoreCase = true) }

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Section 1: Main Node Configuration Inputs
                    if (mainFields.isNotEmpty()) {
                        Text(
                            text = "⚙️ Node Configuration",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        mainFields.forEach { field ->
                            RenderDynamicConfigField(
                                field = field,
                                currentVal = configStateMap[field.key] ?: "",
                                onValueChange = { newValue ->
                                    configStateMap = configStateMap + (field.key to newValue)
                                }
                            )
                        }
                    }

                    // Section 2: Test Simulation Payload Inputs
                    if (testFields.isNotEmpty()) {
                        var isTestSectionExpanded by remember { mutableStateOf(true) }

                        Surface(
                            color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.7f),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { isTestSectionExpanded = !isTestSectionExpanded },
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "🧪 Test Simulation Payload",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.weight(1f)
                                    )
                                    IconButton(
                                        onClick = { isTestSectionExpanded = !isTestSectionExpanded },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (isTestSectionExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                            contentDescription = if (isTestSectionExpanded) "Collapse" else "Expand",
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }

                                AnimatedVisibility(visible = isTestSectionExpanded) {
                                    Column(
                                        verticalArrangement = Arrangement.spacedBy(10.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = "Mock input data used specifically for dry-running and testing this workflow on demand.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))

                                        testFields.forEach { field ->
                                            RenderDynamicConfigField(
                                                field = field,
                                                currentVal = configStateMap[field.key] ?: "",
                                                onValueChange = { newValue ->
                                                    configStateMap = configStateMap + (field.key to newValue)
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (showPreconditionsGuard) {
                // Precondition Vault Attachment Section
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "🛡️ Precondition Rules Guard",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Attach saved vault precondition rules that must pass before this node executes.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Attached Preconditions Badges & Remove Actions
                if (attachedVaultPrecIds.isNotEmpty()) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        attachedVaultPrecIds.forEach { precId ->
                            val vaultPrec = vaultPreconditions.find { it.id == precId }
                            if (vaultPrec != null) {
                                val isEnabled = vaultPrec.isEnabled
                                SuggestionChip(
                                    onClick = {
                                        attachedVaultPrecIds = attachedVaultPrecIds - precId
                                    },
                                    label = { Text("🔒 ${vaultPrec.name}" + if (!isEnabled) " (Disabled)" else "" + " ✕") },
                                    colors = SuggestionChipDefaults.suggestionChipColors(
                                        containerColor = if (isEnabled) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                        labelColor = if (isEnabled) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                    )
                                )
                            }
                        }
                    }
                }

                // Search & Attach Precondition Dropdown
                SearchableDropdownSelector(
                    selectedItemText = "Search & Attach Precondition Rule...",
                    label = "Search & Attach Precondition Rule",
                    searchPlaceholder = "Search preconditions...",
                    items = vaultPreconditions.filter { it.isEnabled },
                    itemText = { prec -> "${prec.name} (${prec.type})" },
                    onItemSelected = { prec ->
                        if (prec != null) {
                            if (attachedVaultPrecIds.contains(prec.id)) {
                                attachedVaultPrecIds = attachedVaultPrecIds - prec.id
                            } else {
                                attachedVaultPrecIds = attachedVaultPrecIds + prec.id
                            }
                        }
                    },
                    isItemSelected = { prec -> attachedVaultPrecIds.contains(prec.id) },
                    isMultiSelect = true,
                    showNoneOption = false,
                    onCreateNewClick = {
                        onRequestCreatePrecondition?.invoke { newPrec ->
                            attachedVaultPrecIds = attachedVaultPrecIds + newPrec.id
                        }
                    },
                    createNewText = "➕ Create New Precondition...",
                    tooltipText = "Search and attach saved vault precondition rules that must pass before executing this node.",
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    if (showCreateCredSheet) {
        CredentialBottomSheet(
            credential = null,
            onDismiss = { showCreateCredSheet = false },
            onSave = { newCred ->
                onSaveCredential?.invoke(newCred)
                credentialId = newCred.id
                showCreateCredSheet = false
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutocompleteTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    tooltipText: String? = null,
    modifier: Modifier = Modifier,
    minLines: Int = 1,
    singleLine: Boolean = false,
    maxLength: Int? = null
) {
    val commonVariables = listOf("sender", "body", "code", "http_response", "timestamp", "title", "text", "package", "formatted_payload")
    var expanded by remember { mutableStateOf(false) }
    var showTooltip by remember { mutableStateOf(false) }
    var filterQuery by remember { mutableStateOf("") }
    val interactionSource = rememberAlwaysFloatingInteractionSource()

    val filteredVars = remember(filterQuery, commonVariables) {
        if (filterQuery.isBlank()) commonVariables else commonVariables.filter { it.contains(filterQuery, ignoreCase = true) }
    }

    Column(modifier = modifier) {
        ExposedDropdownMenuBox(
            expanded = expanded && filteredVars.isNotEmpty(),
            onExpandedChange = { expanded = it },
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedTextField(
                value = value,
                onValueChange = { newValue ->
                    val constrained = if (maxLength != null && newValue.length > maxLength) newValue.take(maxLength) else newValue
                    onValueChange(constrained)
                    val lastDollarIdx = constrained.lastIndexOf('$')
                    if (lastDollarIdx >= 0 && lastDollarIdx == constrained.length - 1) {
                        expanded = true
                        filterQuery = ""
                    } else if (lastDollarIdx >= 0 && constrained.length > lastDollarIdx + 1 && constrained[lastDollarIdx + 1] == '{') {
                        expanded = true
                        filterQuery = constrained.substring(lastDollarIdx + 2).trimEnd('}')
                    } else if (lastDollarIdx >= 0 && lastDollarIdx < constrained.length && constrained.substring(lastDollarIdx).startsWith("\${")) {
                        expanded = true
                        val braceIdx = constrained.indexOf('}', lastDollarIdx)
                        filterQuery = if (braceIdx > lastDollarIdx + 2) {
                            constrained.substring(lastDollarIdx + 2, braceIdx)
                        } else {
                            constrained.substring(lastDollarIdx + 2)
                        }
                    } else {
                        expanded = false
                    }
                },
                label = { Text(label) },
                interactionSource = interactionSource,
                modifier = Modifier
                    .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                    .fillMaxWidth(),
                minLines = minLines,
                singleLine = singleLine,
                trailingIcon = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier.pointerInput(Unit) {
                                awaitPointerEventScope {
                                    while (true) {
                                        val event = awaitPointerEvent(PointerEventPass.Initial)
                                        if (event.type == PointerEventType.Press) {
                                            event.changes.forEach { it.consume() }
                                            filterQuery = ""
                                            expanded = !expanded
                                        } else if (event.type == PointerEventType.Release) {
                                            event.changes.forEach { it.consume() }
                                        }
                                    }
                                }
                            }
                        ) {
                            IconButton(onClick = {
                                filterQuery = ""
                                expanded = !expanded
                            }) {
                                Icon(
                                    imageVector = Icons.Default.Code,
                                    contentDescription = "Insert Variable",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        if (!tooltipText.isNullOrBlank()) {
                            Box(
                                modifier = Modifier.pointerInput(Unit) {
                                    awaitPointerEventScope {
                                        while (true) {
                                            val event = awaitPointerEvent(PointerEventPass.Initial)
                                            if (event.type == PointerEventType.Press) {
                                                event.changes.forEach { it.consume() }
                                                showTooltip = !showTooltip
                                            } else if (event.type == PointerEventType.Release) {
                                                event.changes.forEach { it.consume() }
                                            }
                                        }
                                    }
                                }
                            ) {
                                IconButton(onClick = { showTooltip = !showTooltip }) {
                                    Icon(
                                        imageVector = Icons.Default.Info,
                                        contentDescription = "Field Information",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                                DropdownMenu(
                                    expanded = showTooltip,
                                    onDismissRequest = { showTooltip = false }
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .widthIn(max = 280.dp)
                                            .padding(12.dp)
                                    ) {
                                        Text(
                                            text = label,
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = tooltipText,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            )

            ExposedDropdownMenu(
                expanded = expanded && filteredVars.isNotEmpty(),
                onDismissRequest = { expanded = false }
            ) {
                filteredVars.forEach { v ->
                    DropdownMenuItem(
                        text = { Text("\${$v}", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold) },
                        onClick = {
                            val lastDollarIdx = value.lastIndexOf('$')
                            val newVal = if (lastDollarIdx >= 0 && lastDollarIdx == value.length - 1) {
                                val prefix = value.substring(0, lastDollarIdx)
                                "$prefix\${$v}"
                            } else {
                                val spacer = if (value.isNotBlank() && !value.endsWith(" ")) " " else ""
                                "$value$spacer\${$v}"
                            }
                            val constrained = if (maxLength != null && newVal.length > maxLength) newVal.take(maxLength) else newVal
                            onValueChange(constrained)
                            expanded = false
                            filterQuery = ""
                        }
                    )
                }
            }
        }
        if (maxLength != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.End
            ) {
                Text(
                    text = "${value.length} / $maxLength",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (value.length >= maxLength) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun RenderDynamicConfigField(
    field: dev.zophop.plugin.api.ConfigFieldSpec,
    currentVal: String,
    onValueChange: (String) -> Unit
) {
    if (field.key == "packageFilter") {
        TargetAppMultiSelectPicker(
            selectedPackagesString = currentVal,
            onPackagesChanged = onValueChange
        )
    } else if (field.key == "testPackageName") {
        SingleAppDropdownSelector(
            selectedPackage = currentVal,
            onPackageSelected = onValueChange
        )
    } else {
        when (field) {
            is dev.zophop.plugin.api.ConfigFieldSpec.TextField -> {
                InfoTooltipField(
                    value = currentVal,
                    onValueChange = onValueChange,
                    label = field.label,
                    tooltipText = field.tooltip ?: field.label,
                    placeholder = field.placeholder,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = field.singleLine,
                    minLines = field.minLines
                )
            }
            is dev.zophop.plugin.api.ConfigFieldSpec.DropdownField -> {
                val selectedLabel = field.options.find { it.first == currentVal }?.second ?: currentVal.ifBlank { field.options.firstOrNull()?.second ?: "" }
                SearchableDropdownSelector(
                    selectedItemText = selectedLabel,
                    label = field.label,
                    searchPlaceholder = "Filter ${field.label}...",
                    items = field.options,
                    itemText = { it.second },
                    onItemSelected = { option ->
                        if (option != null) {
                            onValueChange(option.first)
                        }
                    },
                    isItemSelected = { option -> option.first == currentVal },
                    showNoneOption = false,
                    tooltipText = field.tooltip ?: field.label,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            is dev.zophop.plugin.api.ConfigFieldSpec.CodeTemplateField -> {
                if (field.key.contains("headers", ignoreCase = true)) {
                    HeaderInputEditor(
                        headersString = currentVal,
                        onHeadersChanged = onValueChange
                    )
                } else if (field.isTestField || field.key.startsWith("test", ignoreCase = true)) {
                    InfoTooltipField(
                        value = currentVal,
                        onValueChange = onValueChange,
                        label = field.label,
                        tooltipText = field.tooltip ?: field.label,
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = false,
                        minLines = field.minLines
                    )
                } else {
                    AutocompleteTextField(
                        value = currentVal,
                        onValueChange = onValueChange,
                        label = field.label,
                        tooltipText = field.tooltip ?: field.label,
                        modifier = Modifier.fillMaxWidth(),
                        minLines = field.minLines,
                        singleLine = field.singleLine,
                        maxLength = field.maxLength
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun TargetAppMultiSelectPicker(
    selectedPackagesString: String,
    onPackagesChanged: (String) -> Unit
) {
    val context = LocalContext.current
    val installedApps = remember(context) { AppListHelper.getInstalledApps(context) }
    var selectedPackages by remember(selectedPackagesString) {
        mutableStateOf(
            selectedPackagesString.split(",", "\n").map { it.trim() }.filter { it.isNotEmpty() && it != ".*" }.toSet()
        )
    }
    var showAppPickerSheet by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "Target Apps Selection",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = if (selectedPackages.isEmpty()) "Intercepting notifications from All Apps (.*)" else "Intercepting from ${selectedPackages.size} selected app(s)",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Button(
            onClick = { showAppPickerSheet = true },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Apps, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Select Target Apps (${selectedPackages.size})")
        }

        if (selectedPackages.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                selectedPackages.forEach { pkg ->
                    val appInfo = installedApps.find { it.packageName == pkg }
                    val displayName = appInfo?.appName ?: pkg
                    SuggestionChip(
                        onClick = {
                            val newSet = selectedPackages - pkg
                            selectedPackages = newSet
                            onPackagesChanged(if (newSet.isEmpty()) ".*" else newSet.joinToString(", "))
                        },
                        label = { Text("$displayName ✕") }
                    )
                }
            }
        }
    }

    if (showAppPickerSheet) {
        NoSwipeBottomSheet(onDismissRequest = { showAppPickerSheet = false }) {
            var searchQuery by remember { mutableStateOf("") }
            val filteredApps = remember(installedApps, searchQuery) {
                if (searchQuery.isBlank()) installedApps.filter { it.packageName != ".*" }
                else installedApps.filter {
                    it.packageName != ".*" && (it.appName.contains(searchQuery, ignoreCase = true) || it.packageName.contains(searchQuery, ignoreCase = true))
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Select Target Apps",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = { showAppPickerSheet = false }) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    label = { Text("Search apps by name or package...") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(360.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    filteredApps.forEach { appInfo ->
                        val isSelected = selectedPackages.contains(appInfo.packageName)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    val newSet = if (isSelected) selectedPackages - appInfo.packageName else selectedPackages + appInfo.packageName
                                    selectedPackages = newSet
                                    onPackagesChanged(if (newSet.isEmpty()) ".*" else newSet.joinToString(", "))
                                }
                                .padding(vertical = 8.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Checkbox(
                                checked = isSelected,
                                onCheckedChange = { checked ->
                                    val newSet = if (checked) selectedPackages + appInfo.packageName else selectedPackages - appInfo.packageName
                                    selectedPackages = newSet
                                    onPackagesChanged(if (newSet.isEmpty()) ".*" else newSet.joinToString(", "))
                                }
                            )
                            val bitmap = remember(appInfo.icon) {
                                try {
                                    appInfo.icon?.toBitmap(width = 48, height = 48)?.asImageBitmap()
                                } catch (_: Exception) { null }
                            }
                            if (bitmap != null) {
                                Image(bitmap = bitmap, contentDescription = null, modifier = Modifier.size(24.dp))
                            } else {
                                Box(modifier = Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.Apps, contentDescription = null, modifier = Modifier.size(16.dp))
                                }
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = appInfo.appName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                Text(text = appInfo.packageName, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }

                Button(
                    onClick = { showAppPickerSheet = false },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Done")
                }
            }
        }
    }
}

@Composable
private fun SingleAppDropdownSelector(
    selectedPackage: String,
    onPackageSelected: (String) -> Unit
) {
    val context = LocalContext.current
    val installedApps = remember(context) { AppListHelper.getInstalledApps(context).filter { it.packageName != ".*" } }
    val selectedApp = installedApps.find { it.packageName == selectedPackage }
    val displayLabel = selectedApp?.appName ?: selectedPackage.ifBlank { "Select Target App" }

    SearchableDropdownSelector(
        selectedItemText = displayLabel,
        label = "Simulated Target Package",
        searchPlaceholder = "Search installed apps...",
        items = installedApps,
        itemText = { "${it.appName} (${it.packageName})" },
        onItemSelected = { app ->
            if (app != null) {
                onPackageSelected(app.packageName)
            }
        },
        isItemSelected = { app -> app.packageName == selectedPackage },
        showNoneOption = false,
        tooltipText = "Select installed application to simulate notification origin during test runs.",
        modifier = Modifier.fillMaxWidth()
    )
}

private fun isNotificationListenerEnabled(context: android.content.Context): Boolean {
    val pkg = context.packageName
    val flat = android.provider.Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
    return flat != null && flat.contains(pkg)
}

private fun validateNodeConfig(nodeType: String, config: Map<String, String>): String? {
    when {
        nodeType.contains("SEND_SMS_ACTION", ignoreCase = true) -> {
            val recipient = config["recipient"]?.trim() ?: ""
            val body = config["body"]?.trim() ?: ""
            if (recipient.isBlank()) return "Recipient phone number cannot be blank."
            if (body.isBlank()) return "Message body cannot be blank."
        }
        nodeType.contains("SEND_NOTIFICATION_ACTION", ignoreCase = true) -> {
            val title = config["title"]?.trim() ?: ""
            val body = config["body"]?.trim() ?: ""
            if (title.isBlank()) return "Notification title cannot be blank."
            if (body.isBlank()) return "Notification content body cannot be blank."
        }
        nodeType.contains("HTTP_ACTION", ignoreCase = true) -> {
            val url = config["url"]?.trim() ?: config["serverUrl"]?.trim() ?: ""
            if (url.isBlank()) return "HTTP URL cannot be blank."
            if (!url.startsWith("http://", ignoreCase = true) && !url.startsWith("https://", ignoreCase = true)) {
                return "HTTP URL must start with http:// or https://."
            }
        }
        nodeType.contains("NTFY_ACTION", ignoreCase = true) -> {
            val serverUrl = config["serverUrl"]?.trim() ?: ""
            val topic = config["topic"]?.trim() ?: ""
            if (serverUrl.isBlank()) return "Server URL cannot be blank."
            if (topic.isBlank()) return "Topic name cannot be blank."
        }
        nodeType.contains("GOTIFY_ACTION", ignoreCase = true) -> {
            val serverUrl = config["serverUrl"]?.trim() ?: ""
            val appToken = config["appToken"]?.trim() ?: ""
            if (serverUrl.isBlank()) return "Server URL cannot be blank."
            if (appToken.isBlank()) return "App Token cannot be blank."
        }
        nodeType.contains("TELEGRAM_ACTION", ignoreCase = true) -> {
            val botToken = config["botToken"]?.trim() ?: ""
            val chatId = config["chatId"]?.trim() ?: ""
            val credentialId = config["credentialId"]?.trim() ?: ""
            if (botToken.isBlank() && credentialId.isBlank()) return "Either Telegram Bot Token or an attached credential must be provided."
            if (chatId.isBlank()) return "Telegram Chat ID cannot be blank."
        }
        nodeType.contains("REGEX_PARSER", ignoreCase = true) -> {
            val regex = config["regex"]?.trim() ?: ""
            val outputKey = config["outputKey"]?.trim() ?: ""
            if (regex.isBlank()) return "Regular expression pattern cannot be blank."
            try {
                java.util.regex.Pattern.compile(regex)
            } catch (e: Exception) {
                return "Invalid regular expression: ${e.message}"
            }
            if (outputKey.isBlank()) return "Output variable name cannot be blank."
        }
        nodeType.contains("JSON_PARSER", ignoreCase = true) -> {
            val jsonKey = config["jsonKey"]?.trim() ?: ""
            val outputKey = config["outputKey"]?.trim() ?: ""
            if (jsonKey.isBlank()) return "JSON path key cannot be blank."
            if (outputKey.isBlank()) return "Output variable name cannot be blank."
        }
        nodeType.contains("TEMPLATE_FORMATTER", ignoreCase = true) -> {
            val template = config["template"]?.trim() ?: ""
            if (template.isBlank()) return "Template text cannot be blank."
        }
        nodeType.contains("CONDITION", ignoreCase = true) -> {
            val variable = config["variable"]?.trim() ?: ""
            val operator = config["operator"]?.trim() ?: ""
            if (variable.isBlank()) return "Condition variable cannot be blank."
            if (operator.isBlank()) return "Condition operator must be selected."
        }
        nodeType.contains("CRON_TRIGGER", ignoreCase = true) -> {
            val cronExpression = config["cronExpression"]?.trim() ?: ""
            if (cronExpression.isBlank()) return "Schedule interval or cron expression cannot be blank."
        }
        nodeType.contains("SMS_TRIGGER", ignoreCase = true) -> {
            val allowlist = config["allowlist"]?.trim() ?: ""
            val keywordRegex = config["keywordRegex"]?.trim() ?: ""
            if (allowlist.isNotBlank() && allowlist != ".*") {
                try {
                    java.util.regex.Pattern.compile(allowlist.replace("*", ".*"))
                } catch (e: Exception) {
                    return "Invalid sender allowlist regex: ${e.message}"
                }
            }
            if (keywordRegex.isNotBlank() && keywordRegex != ".*") {
                try {
                    java.util.regex.Pattern.compile(keywordRegex)
                } catch (e: Exception) {
                    return "Invalid keyword regex: ${e.message}"
                }
            }
        }
        nodeType.contains("NOTIFICATION_TRIGGER", ignoreCase = true) -> {
            val textRegex = config["textRegex"]?.trim() ?: ""
            if (textRegex.isNotBlank() && textRegex != ".*") {
                try {
                    java.util.regex.Pattern.compile(textRegex)
                } catch (e: Exception) {
                    return "Invalid text regex filter: ${e.message}"
                }
            }
        }
    }
    return null
}
