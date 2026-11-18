package `in`.ntfysms.ui.workflows

import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import `in`.ntfysms.data.toDomain
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import `in`.ntfysms.core.ui.components.CredentialBottomSheet
import `in`.ntfysms.core.ui.components.HeaderInputEditor
import `in`.ntfysms.core.ui.components.NoSwipeBottomSheet
import `in`.ntfysms.data.PreconditionEntity
import `in`.ntfysms.domain.engine.NodeExecutionStep
import `in`.ntfysms.domain.engine.PreconditionResult
import `in`.ntfysms.domain.model.Credential
import `in`.ntfysms.domain.model.NodeType
import `in`.ntfysms.domain.model.WorkflowNode
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
    hasNotificationListenerPermission: Boolean = true,
    hasPostNotificationsPermission: Boolean = true,
    hasSmsPermission: Boolean = true,
    onRequestSmsPermission: (() -> Unit)? = null,
    onRequestPostNotificationsPermission: (() -> Unit)? = null,
    onDismiss: () -> Unit,
    onSave: (WorkflowNode) -> Unit,
    onSaveCredential: ((Credential) -> Unit)? = null,
    onSavePrecondition: ((PreconditionEntity) -> Unit)? = null,
    onTestPreconditionWithProgress: ((PreconditionEntity, (List<NodeExecutionStep>) -> Unit, (PreconditionResult) -> Unit) -> Unit)? = null
) {
    val context = LocalContext.current

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
    var title by remember { mutableStateOf(configObj.optString("title", "NtfySMS Notification")) }
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
                node.preconditions.any { it.type.name == prec.type }
            }.map { it.id }.toSet()
        )
    }

    // Searchable Preconditions Dropdown State
    var precSearchQuery by remember { mutableStateOf("") }
    var expandedPrecMenu by remember { mutableStateOf(false) }

    val filteredVaultPreconditions = remember(precSearchQuery, vaultPreconditions) {
        if (precSearchQuery.isBlank()) {
            vaultPreconditions
        } else {
            vaultPreconditions.filter { prec ->
                prec.name.contains(precSearchQuery, ignoreCase = true) ||
                prec.type.contains(precSearchQuery, ignoreCase = true) ||
                WorkflowUiHelper.getPreconditionTitle(prec.type).contains(precSearchQuery, ignoreCase = true)
            }
        }
    }

    var expandedCredMenu by remember { mutableStateOf(false) }
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

                            val attachedPreconditions = vaultPreconditions
                                .filter { attachedVaultPrecIds.contains(it.id) && it.isEnabled }
                                .map { it.toDomain() }

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

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Node Name") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                supportingText = { Text("Unique name identifying this step in the workflow pipeline.", fontSize = 10.sp) }
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
                val credName = selectedCred?.name ?: "None (Optional Credential)"

                ExposedDropdownMenuBox(
                    expanded = expandedCredMenu,
                    onExpandedChange = { expandedCredMenu = !expandedCredMenu }
                ) {
                    OutlinedTextField(
                        value = credName,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("🔑 Attach Saved Credential") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedCredMenu) },
                        modifier = Modifier
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                            .fillMaxWidth(),
                        supportingText = { Text("Attach saved credentials for authenticated requests.", fontSize = 10.sp) }
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
                        DropdownMenuItem(
                            text = { Text("➕ Create New Credential...") },
                            onClick = {
                                expandedCredMenu = false
                                showCreateCredSheet = true
                            }
                        )
                    }
                }
            }

            // Node Specific Config Fields
            when (node.type) {
                NodeType.SMS_TRIGGER -> {
                    OutlinedTextField(
                        value = allowlist,
                        onValueChange = { allowlist = it },
                        label = { Text("Sender Allowlist Regex (e.g. .*BANK.*)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        supportingText = { Text("Regex pattern matching allowed SMS sender addresses.", fontSize = 10.sp) }
                    )
                    OutlinedTextField(
                        value = keywordRegex,
                        onValueChange = { keywordRegex = it },
                        label = { Text("Keyword Filter Regex (e.g. OTP|code)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        supportingText = { Text("Regex filtering SMS body text for target keywords.", fontSize = 10.sp) }
                    )

                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "🧪 Contextual Test Simulation Payload",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    OutlinedTextField(
                        value = testSender,
                        onValueChange = { testSender = it },
                        label = { Text("Simulated Sender") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        supportingText = { Text("Mock sender number used during dry-run test execution.", fontSize = 10.sp) }
                    )
                    OutlinedTextField(
                        value = testBody,
                        onValueChange = { testBody = it },
                        label = { Text("Simulated SMS Body") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                        supportingText = { Text("Mock SMS text body used during dry-run test execution.", fontSize = 10.sp) }
                    )
                }
                NodeType.NOTIFICATION_TRIGGER -> {
                    OutlinedTextField(
                        value = packageFilter,
                        onValueChange = { packageFilter = it },
                        label = { Text("App Package Filter (e.g. com.bank.app)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        supportingText = { Text("Package name filter for incoming notification events.", fontSize = 10.sp) }
                    )
                    OutlinedTextField(
                        value = textRegex,
                        onValueChange = { textRegex = it },
                        label = { Text("Notification Text Regex") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        supportingText = { Text("Regex matching notification text content.", fontSize = 10.sp) }
                    )

                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "🧪 Contextual Test Simulation Payload",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    OutlinedTextField(
                        value = testPackageName,
                        onValueChange = { testPackageName = it },
                        label = { Text("Simulated Package Name") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        supportingText = { Text("Mock app package name for testing.", fontSize = 10.sp) }
                    )
                    OutlinedTextField(
                        value = testTitle,
                        onValueChange = { testTitle = it },
                        label = { Text("Simulated Notification Title") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        supportingText = { Text("Mock notification title for testing.", fontSize = 10.sp) }
                    )
                    OutlinedTextField(
                        value = testText,
                        onValueChange = { testText = it },
                        label = { Text("Simulated Notification Text") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                        supportingText = { Text("Mock notification text for testing.", fontSize = 10.sp) }
                    )
                }
                NodeType.PARSE_NOTIFICATION, NodeType.REGEX_PARSER -> {
                    OutlinedTextField(
                        value = regexPattern,
                        onValueChange = { regexPattern = it },
                        label = { Text("Regular Expression (e.g. \\b\\d{6}\\b)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        supportingText = { Text("Regular expression with capture groups to extract variable data.", fontSize = 10.sp) }
                    )
                    OutlinedTextField(
                        value = outputKey,
                        onValueChange = { outputKey = it },
                        label = { Text("Output Variable Key (e.g. code)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        supportingText = { Text("Context variable key where the parsed value is saved.", fontSize = 10.sp) }
                    )
                }
                NodeType.JSON_PARSER -> {
                    OutlinedTextField(
                        value = jsonKey,
                        onValueChange = { jsonKey = it },
                        label = { Text("JSON Property Path (e.g. data.code)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        supportingText = { Text("Dot-notation path to extract property from JSON response.", fontSize = 10.sp) }
                    )
                    OutlinedTextField(
                        value = outputKey,
                        onValueChange = { outputKey = it },
                        label = { Text("Output Variable Key") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        supportingText = { Text("Context variable key where the extracted value is saved.", fontSize = 10.sp) }
                    )
                }
                NodeType.TEMPLATE_FORMATTER -> {
                    AutocompleteTextField(
                        value = template,
                        onValueChange = { template = it },
                        label = { Text("Message Template (e.g. OTP: \${code})") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3
                    )
                }
                NodeType.CONDITION -> {
                    OutlinedTextField(
                        value = condVariable,
                        onValueChange = { condVariable = it },
                        label = { Text("Variable Name to Check") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        supportingText = { Text("Context variable name evaluated by condition.", fontSize = 10.sp) }
                    )
                    OutlinedTextField(
                        value = condValue,
                        onValueChange = { condValue = it },
                        label = { Text("Expected Target Value") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        supportingText = { Text("Expected target value for conditional evaluation.", fontSize = 10.sp) }
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

                    OutlinedTextField(
                        value = serverUrl,
                        onValueChange = { serverUrl = it },
                        label = { Text("ntfy Server URL (Leave blank for credential default)") },
                        placeholder = { Text(credServerUrl.ifBlank { "https://ntfy.sh" }) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        supportingText = { Text("Target ntfy push notification server base URL.", fontSize = 10.sp) }
                    )
                    OutlinedTextField(
                        value = topic,
                        onValueChange = { topic = it },
                        label = { Text("Topic Name (Leave blank for credential default)") },
                        placeholder = { Text(credTopic.ifBlank { "my-topic" }) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        supportingText = { Text("Secret destination topic name on ntfy server.", fontSize = 10.sp) }
                    )
                    OutlinedTextField(
                        value = token,
                        onValueChange = { token = it },
                        label = { Text("Auth Token (Optional Override)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        supportingText = { Text("Optional access token override for ntfy topic.", fontSize = 10.sp) }
                    )
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Notification Title") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        supportingText = { Text("Notification banner title.", fontSize = 10.sp) }
                    )
                    OutlinedTextField(
                        value = priority,
                        onValueChange = { priority = it },
                        label = { Text("Priority (1=Min, 3=Normal, 5=Max)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        supportingText = { Text("Push notification priority level (1 to 5).", fontSize = 10.sp) }
                    )
                    OutlinedTextField(
                        value = tags,
                        onValueChange = { tags = it },
                        label = { Text("Tags / Emojis (comma separated, e.g. otp,warning)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        supportingText = { Text("Tags or emoji identifiers attached to notification.", fontSize = 10.sp) }
                    )
                    AutocompleteTextField(
                        value = template,
                        onValueChange = { template = it },
                        label = { Text("Custom Message Body / Template (supports \${body}, \${sender})") },
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

                    OutlinedTextField(
                        value = serverUrl,
                        onValueChange = { serverUrl = it },
                        label = { Text("Gotify Server URL (Leave blank for credential default)") },
                        placeholder = { Text(credServerUrl.ifBlank { "https://gotify.example.com" }) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        supportingText = { Text("Base URL of your Gotify server.", fontSize = 10.sp) }
                    )
                    OutlinedTextField(
                        value = appToken,
                        onValueChange = { appToken = it },
                        label = { Text("App Token (Optional Override)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        supportingText = { Text("Application token override for Gotify.", fontSize = 10.sp) }
                    )
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Notification Title") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        supportingText = { Text("Notification message title.", fontSize = 10.sp) }
                    )
                    OutlinedTextField(
                        value = priority,
                        onValueChange = { priority = it },
                        label = { Text("Priority (1 to 10)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        supportingText = { Text("Gotify priority level (1 to 10).", fontSize = 10.sp) }
                    )
                    AutocompleteTextField(
                        value = template,
                        onValueChange = { template = it },
                        label = { Text("Custom Message Body / Template (supports \${body})") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3
                    )
                }
                NodeType.HTTP_ACTION -> {
                    var expandedMethodMenu by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(
                        expanded = expandedMethodMenu,
                        onExpandedChange = { expandedMethodMenu = !expandedMethodMenu }
                    ) {
                        OutlinedTextField(
                            value = httpMethod,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("HTTP Method") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedMethodMenu) },
                            modifier = Modifier
                                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                                .fillMaxWidth(),
                            supportingText = { Text("HTTP method (GET, POST, PUT, etc.).", fontSize = 10.sp) }
                        )
                        ExposedDropdownMenu(
                            expanded = expandedMethodMenu,
                            onDismissRequest = { expandedMethodMenu = false }
                        ) {
                            listOf("GET", "POST", "PUT", "PATCH", "DELETE").forEach { method ->
                                DropdownMenuItem(
                                    text = { Text(method) },
                                    onClick = {
                                        httpMethod = method
                                        expandedMethodMenu = false
                                    }
                                )
                            }
                        }
                    }
                    OutlinedTextField(
                        value = httpUrl,
                        onValueChange = { httpUrl = it },
                        label = { Text("Endpoint URL") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        supportingText = { Text("Target REST API webhook URL.", fontSize = 10.sp) }
                    )
                    OutlinedTextField(
                        value = httpQueryParams,
                        onValueChange = { httpQueryParams = it },
                        label = { Text("Query Parameters (key=value, one per line)") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                        supportingText = { Text("URL query string parameters.", fontSize = 10.sp) }
                    )
                    HeaderInputEditor(
                        headersString = httpHeaders,
                        onHeadersChanged = { httpHeaders = it }
                    )
                    AutocompleteTextField(
                        value = httpBody,
                        onValueChange = { httpBody = it },
                        label = { Text("Request Body / Payload Template") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 4
                    )
                }
                NodeType.SEND_NOTIFICATION_ACTION -> {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Notification Title") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        supportingText = { Text("Local system notification title.", fontSize = 10.sp) }
                    )
                    AutocompleteTextField(
                        value = template,
                        onValueChange = { template = it },
                        label = { Text("Notification Message Template") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3
                    )
                }
                NodeType.SEND_SMS_ACTION -> {
                    OutlinedTextField(
                        value = recipient,
                        onValueChange = { recipient = it },
                        label = { Text("Recipient Phone Number") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        supportingText = { Text("Destination phone number for outgoing SMS.", fontSize = 10.sp) }
                    )
                    AutocompleteTextField(
                        value = smsBody,
                        onValueChange = { smsBody = it },
                        label = { Text("SMS Body Template") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3
                    )
                }
                else -> {}
            }

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
                            SuggestionChip(
                                onClick = {
                                    attachedVaultPrecIds = attachedVaultPrecIds - precId
                                },
                                label = { Text("🔒 ${vaultPrec.name} ✕") },
                                colors = SuggestionChipDefaults.suggestionChipColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                    labelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            )
                        }
                    }
                }
            }

            // Search & Attach Precondition Dropdown
            ExposedDropdownMenuBox(
                expanded = expandedPrecMenu,
                onExpandedChange = { expandedPrecMenu = !expandedPrecMenu }
            ) {
                OutlinedTextField(
                    value = precSearchQuery,
                    onValueChange = {
                        precSearchQuery = it
                        expandedPrecMenu = true
                    },
                    label = { Text("🔍 Search & Attach Precondition Rule...") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedPrecMenu) },
                    modifier = Modifier
                        .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                        .fillMaxWidth(),
                    singleLine = true,
                    supportingText = { Text("Search and attach vault guard rules.", fontSize = 10.sp) }
                )

                ExposedDropdownMenu(
                    expanded = expandedPrecMenu,
                    onDismissRequest = { expandedPrecMenu = false }
                ) {
                    filteredVaultPreconditions.forEach { prec ->
                        val isAttached = attachedVaultPrecIds.contains(prec.id)
                        DropdownMenuItem(
                            text = { Text("${if (isAttached) "✓ " else "+ "} ${prec.name} (${prec.type})") },
                            onClick = {
                                if (isAttached) {
                                    attachedVaultPrecIds = attachedVaultPrecIds - prec.id
                                } else {
                                    attachedVaultPrecIds = attachedVaultPrecIds + prec.id
                                }
                            }
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("➕ Create in Preconditions Vault...") },
                        onClick = {
                            expandedPrecMenu = false
                            onDismiss()
                        }
                    )
                }
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
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    minLines: Int = 1,
    singleLine: Boolean = false
) {
    val commonVariables = listOf("sender", "body", "code", "http_response", "timestamp", "title", "text", "package", "formatted_payload")
    var expanded by remember { mutableStateOf(false) }
    var filterQuery by remember { mutableStateOf("") }

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
            label = label,
            modifier = Modifier
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth(),
            minLines = minLines,
            singleLine = singleLine,
            trailingIcon = {
                IconButton(onClick = { expanded = !expanded }) {
                    Icon(
                        imageVector = Icons.Default.Code,
                        contentDescription = "Insert Variable",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            },
            supportingText = {
                Text(
                    text = "💡 Type '$' or click ⟨/⟩ to insert chain variables (${commonVariables.take(4).joinToString(", ")}...)",
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
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
                        if (lastDollarIdx >= 0) {
                            val prefix = value.substring(0, lastDollarIdx)
                            onValueChange("$prefix\${$v}")
                        } else {
                            onValueChange("$value\${$v}")
                        }
                        expanded = false
                    }
                )
            }
        }
    }
}
