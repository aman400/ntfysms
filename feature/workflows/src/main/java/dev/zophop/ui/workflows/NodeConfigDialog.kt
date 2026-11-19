package dev.zophop.ui.workflows

import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Checkbox
import androidx.compose.material3.AlertDialog
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

private data class Quadruple<A, B, C, D>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
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

    val sendSmsPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ -> }

    val hasSendSmsPermission = remember(context) {
        androidx.core.content.ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.SEND_SMS
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    val (isMissingPerm, missingPermTitle, missingPermDesc, onGrantClick) = when {
        node.type == NodeType.NOTIFICATION_TRIGGER && !hasNotificationListenerPermission -> Quadruple(
            true,
            "⚠️ Notification Listener Permission Missing",
            "Node is disabled. Grant Notification Access in system settings to enable.",
            {
                try {
                    context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                } catch (_: Exception) {}
            }
        )
        node.type == NodeType.SMS_TRIGGER && !hasSmsPermission -> Quadruple(
            true,
            "⚠️ SMS Permission Missing (RECEIVE_SMS)",
            "Node is disabled. Grant SMS permission to intercept incoming text messages.",
            { onRequestSmsPermission?.invoke() }
        )
        node.type == NodeType.SEND_SMS_ACTION && !hasSendSmsPermission -> Quadruple(
            true,
            "⚠️ Send SMS Permission Missing (SEND_SMS)",
            "Node is disabled. Grant SEND_SMS permission to send text messages via SIM card.",
            { sendSmsPermissionLauncher.launch(android.Manifest.permission.SEND_SMS) }
        )
        node.type == NodeType.SEND_NOTIFICATION_ACTION && android.os.Build.VERSION.SDK_INT >= 33 && !hasPostNotificationsPermission -> Quadruple(
            true,
            "⚠️ Notification Permission Missing (POST_NOTIFICATIONS)",
            "Node is disabled. Grant POST_NOTIFICATIONS permission to display notifications.",
            { onRequestPostNotificationsPermission?.invoke() }
        )
        else -> Quadruple(false, null, null, null)
    }

    var name by remember { mutableStateOf(node.name) }
    var isEnabled by remember { mutableStateOf(node.isEnabled) }

    val configObj = remember {
        try { JSONObject(node.configJson) } catch (_: Exception) { JSONObject() }
    }

    // Config state parameters
    var allowlist by remember { mutableStateOf(configObj.optString("allowlist", ".*")) }
    var keywordRegex by remember { mutableStateOf(configObj.optString("keywordRegex", ".*")) }

    var packageFilter by remember { mutableStateOf(configObj.optString("packageFilter", ".*")) }
    var textRegex by remember { mutableStateOf(configObj.optString("textRegex", ".*")) }

    // Contextual Test Run Payload Config for Triggers
    var testSender by remember { mutableStateOf(configObj.optString("testSender", "+15551234567")) }
    var testBody by remember { mutableStateOf(configObj.optString("testBody", "Your OTP verification code for bank login is 849201")) }
    var testPackageName by remember { mutableStateOf(configObj.optString("testPackageName", "com.example.bankapp")) }
    var testTitle by remember { mutableStateOf(configObj.optString("testTitle", "Bank Alert: OTP Code")) }
    var testText by remember { mutableStateOf(configObj.optString("testText", "Your login code is 849201")) }

    var regexPattern by remember { mutableStateOf(configObj.optString("regex", "\\b\\d{4,8}\\b")) }
    var outputKey by remember { mutableStateOf(configObj.optString("outputKey", "code")) }

    var jsonKey by remember { mutableStateOf(configObj.optString("jsonKey", "code")) }
    var targetType by remember { mutableStateOf(configObj.optString("targetType", "AUTO")) }
    var defaultValue by remember { mutableStateOf(configObj.optString("defaultValue", "")) }
    var inputVariable by remember { mutableStateOf(configObj.optString("inputVariable", "http_response")) }

    var template by remember { mutableStateOf(configObj.optString("template", "\${formatted_payload}")) }

    var condVariable by remember { mutableStateOf(configObj.optString("variable", "code")) }
    var condOperator by remember { mutableStateOf(configObj.optString("operator", "NOT_EMPTY")) }
    var condValue by remember { mutableStateOf(configObj.optString("value", "")) }

    var serverUrl by remember { mutableStateOf(configObj.optString("serverUrl", "")) }
    var topic by remember { mutableStateOf(configObj.optString("topic", "")) }
    var token by remember { mutableStateOf(configObj.optString("token", "")) }
    var appToken by remember { mutableStateOf(configObj.optString("appToken", "")) }
    var title by remember { mutableStateOf(configObj.optString("title", "ZopHop Notification")) }
    var priority by remember { mutableStateOf(configObj.optString("priority", "3")) }
    var tags by remember { mutableStateOf(configObj.optString("tags", "otp,sms")) }

    var httpUrl by remember { mutableStateOf(configObj.optString("url", "https://api.example.com/webhook")) }
    var httpMethod by remember { mutableStateOf(configObj.optString("method", "POST")) }
    var httpQueryParams by remember { mutableStateOf(configObj.optString("queryParams", "")) }
    var httpAuthType by remember { mutableStateOf(configObj.optString("authType", "NONE")) }
    var httpHeaders by remember { mutableStateOf(configObj.optString("headers", "Content-Type: application/json")) }
    var httpBodyType by remember { mutableStateOf(configObj.optString("bodyType", "JSON")) }
    var httpBody by remember { mutableStateOf(configObj.optString("bodyTemplate", "{\"sender\": \"\${sender}\", \"code\": \"\${code}\"}")) }
    var credentialId by remember { mutableStateOf(configObj.optString("credentialId", "")) }

    var recipient by remember { mutableStateOf(configObj.optString("recipient", "")) }
    var smsBody by remember { mutableStateOf(configObj.optString("body", "\${formatted_payload}")) }

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
                            if (node.type == NodeType.HTTP_ACTION && httpUrl.isBlank()) {
                                errorMessage = "HTTP endpoint URL cannot be blank."
                                return@Button
                            }
                            if (node.type in listOf(NodeType.PARSE_NOTIFICATION, NodeType.REGEX_PARSER)) {
                                try {
                                    java.util.regex.Pattern.compile(regexPattern)
                                } catch (e: Exception) {
                                    errorMessage = "Invalid regular expression: ${e.message}"
                                    return@Button
                                }
                            }
                            errorMessage = null

                            val newConfig = JSONObject().apply {
                                put("credentialId", credentialId)
                                when (node.type) {
                                    NodeType.MANUAL_TRIGGER -> {
                                        put("testBody", testBody)
                                    }
                                    NodeType.SMS_TRIGGER -> {
                                        put("allowlist", allowlist)
                                        put("keywordRegex", keywordRegex)
                                        put("testSender", testSender)
                                        put("testBody", testBody)
                                    }
                                    NodeType.NOTIFICATION_TRIGGER -> {
                                        put("packageFilter", packageFilter)
                                        put("textRegex", textRegex)
                                        put("testPackageName", testPackageName)
                                        put("testTitle", testTitle)
                                        put("testText", testText)
                                    }
                                    NodeType.PARSE_NOTIFICATION, NodeType.REGEX_PARSER -> {
                                        put("regex", regexPattern)
                                        put("outputKey", outputKey)
                                    }
                                    NodeType.JSON_PARSER -> {
                                        put("jsonKey", jsonKey)
                                        put("outputKey", outputKey)
                                        put("targetType", targetType)
                                        put("defaultValue", defaultValue)
                                        put("inputVariable", inputVariable)
                                    }
                                    NodeType.TEMPLATE_FORMATTER -> {
                                        put("template", template)
                                    }
                                    NodeType.CONDITION -> {
                                        put("variable", condVariable)
                                        put("operator", condOperator)
                                        put("value", condValue)
                                    }
                                    NodeType.NTFY_ACTION -> {
                                        put("serverUrl", serverUrl)
                                        put("topic", topic)
                                        put("token", token)
                                        put("title", title)
                                        put("priority", priority)
                                        put("tags", tags)
                                        put("template", template)
                                    }
                                    NodeType.GOTIFY_ACTION -> {
                                        put("serverUrl", serverUrl)
                                        put("appToken", appToken)
                                        put("title", title)
                                        put("priority", priority)
                                        put("template", template)
                                    }
                                    NodeType.HTTP_ACTION -> {
                                        put("url", httpUrl)
                                        put("method", httpMethod)
                                        put("queryParams", httpQueryParams)
                                        put("authType", httpAuthType)
                                        put("headers", httpHeaders)
                                        put("bodyType", httpBodyType)
                                        put("bodyTemplate", httpBody)
                                    }
                                    NodeType.SEND_NOTIFICATION_ACTION -> {
                                        put("title", title)
                                        put("message", template)
                                    }
                                    NodeType.SEND_SMS_ACTION -> {
                                        put("recipient", recipient)
                                        put("body", smsBody)
                                    }
                                }
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
            if (node.type in listOf(NodeType.HTTP_ACTION, NodeType.NTFY_ACTION, NodeType.GOTIFY_ACTION)) {
                val selectedCred = credentials.find { it.id == credentialId }
                val credName = selectedCred?.let { "🔑 ${it.name} (${it.type.name})" } ?: "None (Optional Credential)"

                SearchableDropdownSelector(
                    selectedItemText = credName,
                    label = "🔑 Attach Saved Credential",
                    searchPlaceholder = "Search credentials...",
                    items = credentials,
                    itemText = { cred -> "${cred.name} (${cred.type.name})" },
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

            // Node Specific Config Fields
            when (node.type) {
                NodeType.SMS_TRIGGER -> {
                    InfoTooltipField(
                        value = allowlist,
                        onValueChange = { allowlist = it },
                        label = "Sender Allowlist Regex (e.g. .*BANK.*)",
                        tooltipText = "Regex pattern or comma-separated list matching allowed SMS sender addresses.",
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    InfoTooltipField(
                        value = keywordRegex,
                        onValueChange = { keywordRegex = it },
                        label = "Keyword Filter Regex (e.g. OTP|code)",
                        tooltipText = "Regex filtering SMS body text for target keywords.",
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "🧪 Contextual Test Simulation Payload",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    InfoTooltipField(
                        value = testSender,
                        onValueChange = { testSender = it },
                        label = "Simulated Sender",
                        tooltipText = "Mock sender number used during dry-run test execution.",
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    InfoTooltipField(
                        value = testBody,
                        onValueChange = { testBody = it },
                        label = "Simulated SMS Body",
                        tooltipText = "Mock SMS text body used during dry-run test execution.",
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = false,
                        minLines = 2
                    )
                }
                NodeType.NOTIFICATION_TRIGGER -> {
                    val context = LocalContext.current
                    val installedApps = remember {
                        try {
                            val pm = context.packageManager
                            val intent = Intent(Intent.ACTION_MAIN, null).apply {
                                addCategory(Intent.CATEGORY_LAUNCHER)
                            }
                            pm.queryIntentActivities(intent, 0).map { resolveInfo ->
                                val packageName = resolveInfo.activityInfo.packageName
                                val appName = resolveInfo.loadLabel(pm).toString()
                                val icon = resolveInfo.loadIcon(pm)
                                Triple(packageName, appName, icon)
                            }.distinctBy { it.first }.sortedBy { it.second }
                        } catch (_: Exception) {
                            emptyList()
                        }
                    }

                    var selectedPackages by remember {
                        mutableStateOf(
                            packageFilter.split(",", "\n").map { it.trim() }.filter { it.isNotEmpty() && it != ".*" }.toSet()
                        )
                    }
                    var showAppPickerModal by remember { mutableStateOf(false) }

                    androidx.compose.runtime.LaunchedEffect(selectedPackages) {
                        packageFilter = if (selectedPackages.isEmpty()) ".*" else selectedPackages.joinToString(", ")
                    }

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
                            onClick = { showAppPickerModal = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Apps, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Select Installed Apps (${selectedPackages.size})")
                        }

                        if (selectedPackages.isNotEmpty()) {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                selectedPackages.forEach { pkg ->
                                    val appInfo = installedApps.find { it.first == pkg }
                                    val displayName = appInfo?.second ?: pkg
                                    SuggestionChip(
                                        onClick = {
                                            selectedPackages = selectedPackages - pkg
                                        },
                                        label = { Text("$displayName ✕") }
                                    )
                                }
                            }
                        }
                    }

                    if (showAppPickerModal) {
                        AlertDialog(
                            onDismissRequest = { showAppPickerModal = false },
                            title = { Text("Select Target Apps") },
                            text = {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(360.dp)
                                        .verticalScroll(rememberScrollState()),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    installedApps.forEach { (pkg, appName, icon) ->
                                        val isSelected = selectedPackages.contains(pkg)
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    selectedPackages = if (isSelected) {
                                                        selectedPackages - pkg
                                                    } else {
                                                        selectedPackages + pkg
                                                    }
                                                }
                                                .padding(vertical = 8.dp, horizontal = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            Checkbox(
                                                checked = isSelected,
                                                onCheckedChange = { checked ->
                                                    selectedPackages = if (checked) {
                                                        selectedPackages + pkg
                                                    } else {
                                                        selectedPackages - pkg
                                                    }
                                                }
                                            )
                                            val bitmap = remember(icon) {
                                                try {
                                                    icon?.toBitmap(width = 48, height = 48)?.asImageBitmap()
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
                                                Text(text = appName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                                Text(text = pkg, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                        }
                                    }
                                }
                            },
                            confirmButton = {
                                Button(onClick = { showAppPickerModal = false }) {
                                    Text("Done")
                                }
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    InfoTooltipField(
                        value = textRegex,
                        onValueChange = { textRegex = it },
                        label = "Notification Text Regex",
                        tooltipText = "Regex matching notification text content.",
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "🧪 Contextual Test Simulation Payload",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    InfoTooltipField(
                        value = testPackageName,
                        onValueChange = { testPackageName = it },
                        label = "Simulated Package Name",
                        tooltipText = "Mock app package name for testing.",
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    InfoTooltipField(
                        value = testTitle,
                        onValueChange = { testTitle = it },
                        label = "Simulated Notification Title",
                        tooltipText = "Mock notification title for testing.",
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    InfoTooltipField(
                        value = testText,
                        onValueChange = { testText = it },
                        label = "Simulated Notification Text",
                        tooltipText = "Mock notification text for testing.",
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = false,
                        minLines = 2
                    )
                }
                NodeType.PARSE_NOTIFICATION, NodeType.REGEX_PARSER -> {
                    InfoTooltipField(
                        value = regexPattern,
                        onValueChange = { regexPattern = it },
                        label = "Regular Expression (e.g. \\b\\d{6}\\b)",
                        tooltipText = "Regular expression with capture groups to extract variable data.",
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    InfoTooltipField(
                        value = outputKey,
                        onValueChange = { outputKey = it },
                        label = "Output Variable Key (e.g. code)",
                        tooltipText = "Context variable key where the parsed value is saved.",
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
                NodeType.JSON_PARSER -> {
                    InfoTooltipField(
                        value = jsonKey,
                        onValueChange = { jsonKey = it },
                        label = "JSON Property Path (e.g. data.code)",
                        tooltipText = "Dot-notation path to extract property from JSON response.",
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    InfoTooltipField(
                        value = outputKey,
                        onValueChange = { outputKey = it },
                        label = "Output Variable Key",
                        tooltipText = "Context variable key where the extracted value is saved.",
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
                NodeType.TEMPLATE_FORMATTER -> {
                    AutocompleteTextField(
                        value = template,
                        onValueChange = { template = it },
                        label = "Message Template (e.g. OTP: \${code})",
                        tooltipText = "Message template formatting rules. Supports variables like \${body}, \${sender}, \${code}.",
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3
                    )
                }
                NodeType.CONDITION -> {
                    InfoTooltipField(
                        value = condVariable,
                        onValueChange = { condVariable = it },
                        label = "Variable Name to Check",
                        tooltipText = "Context variable name evaluated by condition.",
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    InfoTooltipField(
                        value = condValue,
                        onValueChange = { condValue = it },
                        label = "Expected Target Value",
                        tooltipText = "Expected target value for conditional evaluation.",
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
                NodeType.NTFY_ACTION -> {
                    val selectedCred = credentials.find { it.id == credentialId }
                    val credServerUrl = selectedCred?.dataMap?.get("serverUrl") ?: ""
                    val credTopic = selectedCred?.dataMap?.get("topic") ?: ""
                    val isCredAttached = credentialId.isNotBlank() && selectedCred != null

                    if (isCredAttached) {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    text = "🔑 Attached Credential: ${selectedCred.name}",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                if (credServerUrl.isNotBlank()) {
                                    Text(
                                        text = "Default URL: $credServerUrl",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                                if (credTopic.isNotBlank()) {
                                    Text(
                                        text = "Default Topic: $credTopic",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }
                        }
                    }

                    InfoTooltipField(
                        value = serverUrl,
                        onValueChange = { serverUrl = it },
                        label = "ntfy Server URL (Leave blank for credential default)",
                        tooltipText = "Target ntfy push notification server base URL (e.g. https://ntfy.sh). Leave blank for credential default.",
                        placeholder = credServerUrl.ifBlank { "https://ntfy.sh" },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    InfoTooltipField(
                        value = topic,
                        onValueChange = { topic = it },
                        label = "Topic Name (Leave blank for credential default)",
                        tooltipText = "Secret destination topic name on ntfy server (e.g. my-topic). Leave blank for credential default.",
                        placeholder = credTopic.ifBlank { "my-topic" },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    InfoTooltipField(
                        value = token,
                        onValueChange = { token = it },
                        label = "Auth Token (Optional Override)",
                        tooltipText = "Optional access token override for protected ntfy topics.",
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    InfoTooltipField(
                        value = title,
                        onValueChange = { title = it },
                        label = "Notification Title",
                        tooltipText = "The bold title header displayed at the top of the ntfy push notification banner on your phone or web app.",
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    
                    val ntfyPriorityOptions = remember {
                        listOf(
                            "5" to "Max / Urgent (5)",
                            "4" to "High (4)",
                            "3" to "Default / Normal (3)",
                            "2" to "Low (2)",
                            "1" to "Min / Silent (1)"
                        )
                    }
                    val currentNtfyPriorityLabel = remember(priority) {
                        ntfyPriorityOptions.find { it.first == priority }?.second ?: "Priority $priority"
                    }

                    SearchableDropdownSelector(
                        selectedItemText = currentNtfyPriorityLabel,
                        label = "Notification Priority",
                        searchPlaceholder = "Filter priority...",
                        items = ntfyPriorityOptions,
                        itemText = { it.second },
                        onItemSelected = { option ->
                            if (option != null) priority = option.first
                        },
                        isItemSelected = { option -> option.first == priority },
                        showNoneOption = false,
                        tooltipText = "Push notification priority level on ntfy server. Higher priority triggers sound, vibration, and banner pop-ups.",
                        modifier = Modifier.fillMaxWidth()
                    )

                    InfoTooltipField(
                        value = tags,
                        onValueChange = { tags = it },
                        label = "Tags / Emojis (comma separated, e.g. otp,warning)",
                        tooltipText = "Comma-separated tag or emoji names attached to notification (e.g. otp, warning).",
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    AutocompleteTextField(
                        value = template,
                        onValueChange = { template = it },
                        label = "Custom Message Body / Template (supports \${body}, \${sender})",
                        tooltipText = "Custom push notification message body. Supports variables like \${body}, \${sender}, \${code}.",
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3
                    )
                }
                NodeType.GOTIFY_ACTION -> {
                    val selectedCred = credentials.find { it.id == credentialId }
                    val credServerUrl = selectedCred?.dataMap?.get("serverUrl") ?: ""
                    val isCredAttached = credentialId.isNotBlank() && selectedCred != null

                    if (isCredAttached) {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    text = "🔑 Attached Credential: ${selectedCred.name}",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                if (credServerUrl.isNotBlank()) {
                                    Text(
                                        text = "Default URL: $credServerUrl",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }
                        }
                    }

                    InfoTooltipField(
                        value = serverUrl,
                        onValueChange = { serverUrl = it },
                        label = "Gotify Server URL (Leave blank for credential default)",
                        tooltipText = "Base URL of your Gotify server (e.g. https://gotify.example.com). Leave blank for credential default.",
                        placeholder = credServerUrl.ifBlank { "https://gotify.example.com" },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    InfoTooltipField(
                        value = appToken,
                        onValueChange = { appToken = it },
                        label = "App Token (Optional Override)",
                        tooltipText = "Application token override (X-Gotify-Key) for Gotify server.",
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    InfoTooltipField(
                        value = title,
                        onValueChange = { title = it },
                        label = "Notification Title",
                        tooltipText = "The notification header title displayed at the top of the Gotify push notification card in the Gotify mobile app & web interface.",
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    val gotifyPriorityOptions = remember {
                        listOf(
                            "10" to "Max / Urgent (10)",
                            "8" to "High (8)",
                            "5" to "Default / Normal (5)",
                            "3" to "Low (3)",
                            "1" to "Min (1)"
                        )
                    }
                    val currentGotifyPriorityLabel = remember(priority) {
                        gotifyPriorityOptions.find { it.first == priority }?.second ?: "Priority $priority"
                    }

                    SearchableDropdownSelector(
                        selectedItemText = currentGotifyPriorityLabel,
                        label = "Notification Priority",
                        searchPlaceholder = "Filter priority...",
                        items = gotifyPriorityOptions,
                        itemText = { it.second },
                        onItemSelected = { option ->
                            if (option != null) priority = option.first
                        },
                        isItemSelected = { option -> option.first == priority },
                        showNoneOption = false,
                        tooltipText = "Gotify push notification priority scale from 1 (lowest) to 10 (highest). Controls alert sounds and card placement.",
                        modifier = Modifier.fillMaxWidth()
                    )
                    AutocompleteTextField(
                        value = template,
                        onValueChange = { template = it },
                        label = "Custom Message Body / Template (supports \${body})",
                        tooltipText = "Custom Gotify message body template. Supports variables like \${body}, \${code}.",
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3
                    )
                }
                NodeType.HTTP_ACTION -> {
                    SearchableDropdownSelector(
                        selectedItemText = httpMethod,
                        label = "HTTP Method",
                        searchPlaceholder = "Filter method...",
                        items = listOf("GET", "POST", "PUT", "PATCH", "DELETE"),
                        itemText = { it },
                        onItemSelected = { method ->
                            if (method != null) httpMethod = method
                        },
                        isItemSelected = { method -> method == httpMethod },
                        showNoneOption = false,
                        tooltipText = "HTTP request method (GET, POST, PUT, etc.).",
                        modifier = Modifier.fillMaxWidth()
                    )
                    InfoTooltipField(
                        value = httpUrl,
                        onValueChange = { httpUrl = it },
                        label = "Endpoint URL",
                        tooltipText = "Target REST API webhook URL.",
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    InfoTooltipField(
                        value = httpQueryParams,
                        onValueChange = { httpQueryParams = it },
                        label = "Query Parameters (key=value, one per line)",
                        tooltipText = "URL query string parameters (key=value format, one per line).",
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = false,
                        minLines = 2
                    )
                    HeaderInputEditor(
                        headersString = httpHeaders,
                        onHeadersChanged = { httpHeaders = it }
                    )
                    AutocompleteTextField(
                        value = httpBody,
                        onValueChange = { httpBody = it },
                        label = "Request Body / Payload Template",
                        tooltipText = "Request payload body template. Supports JSON string format and variable interpolation \${code}.",
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 4
                    )
                }
                NodeType.SEND_NOTIFICATION_ACTION -> {
                    InfoTooltipField(
                        value = title,
                        onValueChange = { title = it },
                        label = "Notification Title",
                        tooltipText = "The bold header title displayed on the local Android status bar notification shade on this device.",
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    AutocompleteTextField(
                        value = template,
                        onValueChange = { template = it },
                        label = "Notification Message Template",
                        tooltipText = "System notification message template. Supports variable interpolation \${body}.",
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3
                    )
                }
                NodeType.SEND_SMS_ACTION -> {
                    InfoTooltipField(
                        value = recipient,
                        onValueChange = { recipient = it },
                        label = "Recipient Phone Number",
                        tooltipText = "Destination phone number (including country code) for outgoing SMS.",
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    AutocompleteTextField(
                        value = smsBody,
                        onValueChange = { smsBody = it },
                        label = "SMS Body Template",
                        tooltipText = "Outgoing SMS body text template. Supports variable interpolation \${code}.",
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3
                    )
                }
                else -> {}
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
    singleLine: Boolean = false
) {
    val commonVariables = listOf("sender", "body", "code", "http_response", "timestamp", "title", "text", "package", "formatted_payload")
    var expanded by remember { mutableStateOf(false) }
    var showTooltip by remember { mutableStateOf(false) }
    var filterQuery by remember { mutableStateOf("") }
    val interactionSource = rememberAlwaysFloatingInteractionSource()

    val filteredVars = remember(filterQuery, commonVariables) {
        if (filterQuery.isBlank()) commonVariables else commonVariables.filter { it.contains(filterQuery, ignoreCase = true) }
    }

    ExposedDropdownMenuBox(
        expanded = expanded && filteredVars.isNotEmpty(),
        onExpandedChange = { expanded = it },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = { newValue ->
                onValueChange(newValue)
                val lastDollarIdx = newValue.lastIndexOf('$')
                if (lastDollarIdx >= 0 && lastDollarIdx == newValue.length - 1) {
                    expanded = true
                    filterQuery = ""
                } else if (lastDollarIdx >= 0 && newValue.length > lastDollarIdx + 1 && newValue[lastDollarIdx + 1] == '{') {
                    expanded = true
                    filterQuery = newValue.substring(lastDollarIdx + 2).trimEnd('}')
                } else if (lastDollarIdx >= 0 && lastDollarIdx < newValue.length && newValue.substring(lastDollarIdx).startsWith("\${")) {
                    expanded = true
                    val braceIdx = newValue.indexOf('}', lastDollarIdx)
                    filterQuery = if (braceIdx > lastDollarIdx + 2) {
                        newValue.substring(lastDollarIdx + 2, braceIdx)
                    } else {
                        newValue.substring(lastDollarIdx + 2)
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
                        if (lastDollarIdx >= 0 && lastDollarIdx == value.length - 1) {
                            val prefix = value.substring(0, lastDollarIdx)
                            onValueChange("$prefix\${$v}")
                        } else {
                            val spacer = if (value.isNotBlank() && !value.endsWith(" ")) " " else ""
                            onValueChange("$value$spacer\${$v}")
                        }
                        expanded = false
                        filterQuery = ""
                    }
                )
            }
        }
    }
}
