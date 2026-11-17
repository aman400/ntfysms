package `in`.ntfysms.ui.workflows

import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import `in`.ntfysms.data.PreconditionEntity
import `in`.ntfysms.domain.model.Credential
import `in`.ntfysms.domain.model.NodeType
import `in`.ntfysms.domain.model.WorkflowNode
import `in`.ntfysms.ui.MainViewModel
import `in`.ntfysms.ui.components.HeaderInputEditor
import `in`.ntfysms.ui.components.KeyValueInputEditor
import `in`.ntfysms.ui.credentials.CredentialBottomSheet
import `in`.ntfysms.ui.preconditions.PreconditionBottomSheet
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
    viewModel: MainViewModel? = null
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

    var template by remember { mutableStateOf(configObj.optString("template", "\${body}")) }

    var condVariable by remember { mutableStateOf(configObj.optString("variable", "code")) }
    var condOperator by remember { mutableStateOf(configObj.optString("operator", "NOT_EMPTY")) }
    var condValue by remember { mutableStateOf(configObj.optString("value", "")) }

    var serverUrl by remember { mutableStateOf(configObj.optString("serverUrl", "https://ntfy.sh")) }
    var topic by remember { mutableStateOf(configObj.optString("topic", "")) }
    var token by remember { mutableStateOf(configObj.optString("token", "")) }
    var appToken by remember { mutableStateOf(configObj.optString("appToken", "")) }
    var title by remember { mutableStateOf(configObj.optString("title", "NtfySMS Notification")) }

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
    var showCreatePrecSheet by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

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
                            if (node.type == NodeType.NOTIFICATION_TRIGGER && textRegex.isNotBlank()) {
                                try {
                                    java.util.regex.Pattern.compile(textRegex)
                                } catch (e: Exception) {
                                    errorMessage = "Invalid notification text regex: ${e.message}"
                                    return@Button
                                }
                            }
                            if (node.type == NodeType.SMS_TRIGGER && keywordRegex.isNotBlank()) {
                                try {
                                    java.util.regex.Pattern.compile(keywordRegex)
                                } catch (e: Exception) {
                                    errorMessage = "Invalid keyword regex: ${e.message}"
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
                                    }
                                    NodeType.GOTIFY_ACTION -> {
                                        put("serverUrl", serverUrl)
                                        put("appToken", appToken)
                                        put("title", title)
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
                .imePadding()
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
                    singleLine = true
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "Node Enabled", fontWeight = FontWeight.Bold)
                    Switch(
                        checked = if (isMissingPerm) false else isEnabled,
                        enabled = !isMissingPerm,
                        onCheckedChange = { isEnabled = it }
                    )
                }

                // Missing Permission Banner in Node Config Sheet
                if (isMissingPerm && missingPermTitle != null && missingPermDesc != null && onGrantClick != null) {
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

                // Credential Selection Dropdown (for Action/HTTP Nodes)
                if (node.type in listOf(NodeType.HTTP_ACTION, NodeType.NTFY_ACTION, NodeType.GOTIFY_ACTION)) {
                    val selectedCredName = credentials.find { it.id == credentialId }?.name ?: "None (Manual Auth)"

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
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
                                    text = { Text("None (Use Manual Configuration)") },
                                    onClick = {
                                        credentialId = ""
                                        expandedCredMenu = false
                                    }
                                )
                                credentials.filter { it.type.isCompatibleWith(node.type) }.forEach { cred ->
                                    DropdownMenuItem(
                                        text = { Text("🔑 ${cred.name} (${cred.type.displayName()})") },
                                        onClick = {
                                            credentialId = cred.id
                                            expandedCredMenu = false
                                        }
                                    )
                                }
                            }
                        }

                        OutlinedButton(
                            onClick = { showCreateCredSheet = true },
                            modifier = Modifier.align(Alignment.End)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Create New Credential", fontSize = 12.sp)
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

                if (showCreatePrecSheet) {
                    if (viewModel != null) {
                        PreconditionBottomSheet(
                            precondition = null,
                            credentials = credentials,
                            viewModel = viewModel,
                            onDismiss = { showCreatePrecSheet = false },
                            onSave = { newPrec ->
                                onSavePrecondition?.invoke(newPrec)
                                attachedVaultPrecIds = attachedVaultPrecIds + newPrec.id
                                showCreatePrecSheet = false
                            }
                        )
                    }
                }

                Text(
                    text = "Parameters",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 13.sp
                )

                when (node.type) {
                    NodeType.MANUAL_TRIGGER -> {
                        Text(
                            text = "This node triggers workflow execution manually on-demand.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    NodeType.SMS_TRIGGER -> {
                        OutlinedTextField(
                            value = allowlist,
                            onValueChange = { allowlist = it },
                            label = { Text("Sender Allowlist Pattern (* pattern)") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = keywordRegex,
                            onValueChange = { keywordRegex = it },
                            label = { Text("Keyword Regex Filter") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    NodeType.NOTIFICATION_TRIGGER -> {
                        val installedApps = remember(context) { AppListHelper.getInstalledApps(context) }
                        var appSearchQuery by remember { mutableStateOf("") }
                        var expandedAppMenu by remember { mutableStateOf(false) }

                        val selectedPackages = remember(packageFilter) {
                            packageFilter.split("|").filter { it.isNotBlank() }.toSet()
                        }

                        val filteredApps = remember(appSearchQuery, installedApps) {
                            if (appSearchQuery.isBlank()) {
                                installedApps
                            } else {
                                installedApps.filter {
                                    it.appName.contains(appSearchQuery, ignoreCase = true) ||
                                    it.packageName.contains(appSearchQuery, ignoreCase = true)
                                }
                            }
                        }
                        
                        val currentAppName = remember(selectedPackages, installedApps) {
                            if (selectedPackages.contains(".*") || selectedPackages.isEmpty()) {
                                "All Apps"
                            } else if (selectedPackages.size == 1) {
                                AppListHelper.getAppNameForPackage(context, selectedPackages.first())
                            } else {
                                "${selectedPackages.size} Apps Selected"
                            }
                        }
                        
                        val currentAppIcon = remember(selectedPackages) {
                            if (selectedPackages.size == 1 && !selectedPackages.contains(".*")) {
                                AppListHelper.getAppIconForPackage(context, selectedPackages.first())
                            } else {
                                null
                            }
                        }

                        ExposedDropdownMenuBox(
                            expanded = expandedAppMenu,
                            onExpandedChange = { expandedAppMenu = !expandedAppMenu }
                        ) {
                            OutlinedTextField(
                                value = if (expandedAppMenu) appSearchQuery else currentAppName,
                                onValueChange = {
                                    appSearchQuery = it
                                    expandedAppMenu = true
                                },
                                label = { Text("📱 Select Target Installed App") },
                                leadingIcon = {
                                    AppIconImage(drawable = currentAppIcon, modifier = Modifier.size(24.dp))
                                },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedAppMenu) },
                                modifier = Modifier
                                    .menuAnchor(MenuAnchorType.PrimaryEditable)
                                    .fillMaxWidth(),
                                singleLine = true
                            )

                            ExposedDropdownMenu(
                                expanded = expandedAppMenu && filteredApps.isNotEmpty(),
                                onDismissRequest = { expandedAppMenu = false }
                            ) {
                                filteredApps.forEach { app ->
                                    val isSelected = selectedPackages.contains(app.packageName)
                                    DropdownMenuItem(
                                        text = {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                                            ) {
                                                Checkbox(
                                                    checked = isSelected,
                                                    onCheckedChange = null
                                                )
                                                AppIconImage(drawable = app.icon, modifier = Modifier.size(24.dp))
                                                Column {
                                                    Text(app.appName, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                                }
                                            }
                                        },
                                        onClick = {
                                            val currentSet = selectedPackages.toMutableSet()
                                            if (app.packageName == ".*") {
                                                currentSet.clear()
                                                currentSet.add(".*")
                                            } else {
                                                currentSet.remove(".*")
                                                if (currentSet.contains(app.packageName)) {
                                                    currentSet.remove(app.packageName)
                                                    if (currentSet.isEmpty()) currentSet.add(".*")
                                                } else {
                                                    currentSet.add(app.packageName)
                                                }
                                            }
                                            packageFilter = currentSet.joinToString("|")
                                            testPackageName = if (currentSet.contains(".*") || currentSet.isEmpty()) "com.example.bankapp" else currentSet.first()
                                        }
                                    )
                                }
                            }
                        }

                        OutlinedTextField(
                            value = textRegex,
                            onValueChange = { textRegex = it },
                            label = { Text("Notification Text Regex Filter") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    NodeType.PARSE_NOTIFICATION, NodeType.REGEX_PARSER -> {
                        OutlinedTextField(
                            value = regexPattern,
                            onValueChange = { regexPattern = it },
                            label = { Text("Extraction Regex Pattern") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = outputKey,
                            onValueChange = { outputKey = it },
                            label = { Text("Target Variable Key (e.g. code)") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    NodeType.JSON_PARSER -> {
                        OutlinedTextField(
                            value = jsonKey,
                            onValueChange = { jsonKey = it },
                            label = { Text("JSON Property Key") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = outputKey,
                            onValueChange = { outputKey = it },
                            label = { Text("Output Variable Key") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    NodeType.TEMPLATE_FORMATTER -> {
                        OutlinedTextField(
                            value = template,
                            onValueChange = { template = it },
                            label = { Text("Payload String Template (\${sender}, \${code})") },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 3
                        )
                    }
                    NodeType.CONDITION -> {
                        OutlinedTextField(
                            value = condVariable,
                            onValueChange = { condVariable = it },
                            label = { Text("Variable to Evaluate (e.g. code)") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = condOperator,
                            onValueChange = { condOperator = it },
                            label = { Text("Operator (NOT_EMPTY, EQUALS, CONTAINS)") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = condValue,
                            onValueChange = { condValue = it },
                            label = { Text("Expected Value") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    NodeType.NTFY_ACTION -> {
                        val selectedCred = credentials.find { it.id == credentialId }
                        val credServerUrl = selectedCred?.dataMap?.get("serverUrl") ?: ""
                        val credTopic = selectedCred?.dataMap?.get("topic") ?: ""
                        val isCredAttached = credentialId.isNotBlank() && selectedCred != null

                        OutlinedTextField(
                            value = serverUrl,
                            onValueChange = { serverUrl = it },
                            label = { Text("ntfy Server URL") },
                            placeholder = { Text(credServerUrl.ifBlank { "https://ntfy.sh" }) },
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = topic,
                            onValueChange = { topic = it },
                            label = { Text("Topic Name") },
                            placeholder = { Text(credTopic.ifBlank { "my-topic" }) },
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = if (isCredAttached) "{{Token from ${selectedCred.name}}}" else token,
                            onValueChange = { token = it },
                            label = { Text("Auth Token") },
                            enabled = !isCredAttached,
                            readOnly = isCredAttached,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    NodeType.GOTIFY_ACTION -> {
                        val selectedCred = credentials.find { it.id == credentialId }
                        val credServerUrl = selectedCred?.dataMap?.get("serverUrl") ?: ""
                        val isCredAttached = credentialId.isNotBlank() && selectedCred != null

                        OutlinedTextField(
                            value = serverUrl,
                            onValueChange = { serverUrl = it },
                            label = { Text("Gotify Server URL") },
                            placeholder = { Text(credServerUrl.ifBlank { "https://gotify.example.com" }) },
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = if (isCredAttached) "{{App Token from ${selectedCred.name}}}" else appToken,
                            onValueChange = { appToken = it },
                            label = { Text("App Token") },
                            enabled = !isCredAttached,
                            readOnly = isCredAttached,
                            modifier = Modifier.fillMaxWidth()
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
                                    .fillMaxWidth()
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
                            modifier = Modifier.fillMaxWidth()
                        )

                        KeyValueInputEditor(
                            title = "Query Parameters",
                            pairSeparator = "=",
                            joinSeparator = "&",
                            exampleText = "Examples:\n• apiKey = abc123xyz\n• format = json",
                            inputString = httpQueryParams,
                            onInputChanged = { httpQueryParams = it }
                        )

                        var expandedBodyTypeMenu by remember { mutableStateOf(false) }
                        ExposedDropdownMenuBox(
                            expanded = expandedBodyTypeMenu,
                            onExpandedChange = { expandedBodyTypeMenu = !expandedBodyTypeMenu }
                        ) {
                            OutlinedTextField(
                                value = httpBodyType,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Body Type") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedBodyTypeMenu) },
                                modifier = Modifier
                                    .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                                    .fillMaxWidth()
                            )
                            ExposedDropdownMenu(
                                expanded = expandedBodyTypeMenu,
                                onDismissRequest = { expandedBodyTypeMenu = false }
                            ) {
                                listOf("JSON", "RAW", "FORM_DATA", "NONE").forEach { type ->
                                    DropdownMenuItem(
                                        text = { Text(type) },
                                        onClick = {
                                            httpBodyType = type
                                            expandedBodyTypeMenu = false
                                        }
                                    )
                                }
                            }
                        }

                        HeaderInputEditor(
                            headersString = httpHeaders,
                            onHeadersChanged = { httpHeaders = it }
                        )
                        OutlinedTextField(
                            value = httpBody,
                            onValueChange = { httpBody = it },
                            label = { Text("Request Body Template") },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 3
                        )
                    }
                    NodeType.SEND_NOTIFICATION_ACTION -> {
                        OutlinedTextField(
                            value = title,
                            onValueChange = { title = it },
                            label = { Text("Notification Title") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = template,
                            onValueChange = { template = it },
                            label = { Text("Message Body Template") },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 2
                        )
                    }
                    NodeType.SEND_SMS_ACTION -> {
                        OutlinedTextField(
                            value = recipient,
                            onValueChange = { recipient = it },
                            label = { Text("Recipient Phone Number") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = smsBody,
                            onValueChange = { smsBody = it },
                            label = { Text("SMS Message Template") },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 2
                        )
                    }
                }

                // Preconditions Searchable Dropdown Card
                OutlinedCard(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Conditions",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            OutlinedButton(
                                onClick = { showCreatePrecSheet = true },
                                modifier = Modifier.height(32.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("New", fontSize = 12.sp)
                            }
                        }

                        if (vaultPreconditions.isEmpty()) {
                            Text(
                                text = "No conditions found. Tap 'New' above to create one on the fly.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            // Searchable ExposedDropdownMenuBox
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
                                    label = { Text("Search conditions...") },
                                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                                    trailingIcon = {
                                        if (precSearchQuery.isNotEmpty()) {
                                            IconButton(onClick = { precSearchQuery = "" }) {
                                                Icon(Icons.Default.Clear, contentDescription = "Clear Search")
                                            }
                                        } else {
                                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedPrecMenu)
                                        }
                                    },
                                    modifier = Modifier
                                        .menuAnchor(MenuAnchorType.PrimaryEditable)
                                        .fillMaxWidth(),
                                    singleLine = true
                                )

                                ExposedDropdownMenu(
                                    expanded = expandedPrecMenu && filteredVaultPreconditions.isNotEmpty(),
                                    onDismissRequest = { expandedPrecMenu = false }
                                ) {
                                    filteredVaultPreconditions.forEach { prec ->
                                        val isAttached = attachedVaultPrecIds.contains(prec.id)
                                        val iconStr = WorkflowUiHelper.getPreconditionIconStr(prec.type)
                                        val typeTitle = WorkflowUiHelper.getPreconditionTitle(prec.type)

                                        DropdownMenuItem(
                                            text = {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                    ) {
                                                        Text(iconStr, fontSize = 16.sp)
                                                        Column {
                                                            Text(prec.name, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                                            Text(typeTitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                        }
                                                    }
                                                    Checkbox(
                                                        checked = isAttached,
                                                        onCheckedChange = null
                                                    )
                                                }
                                            },
                                            onClick = {
                                                attachedVaultPrecIds = if (isAttached) {
                                                    attachedVaultPrecIds - prec.id
                                                } else {
                                                    attachedVaultPrecIds + prec.id
                                                }
                                            }
                                        )
                                    }
                                }
                            }

                            // Attached Precondition Chips
                            if (attachedVaultPrecIds.isNotEmpty()) {
                                Text(
                                    text = "Attached Preconditions (${attachedVaultPrecIds.size}):",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold
                                )

                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    vaultPreconditions.filter { attachedVaultPrecIds.contains(it.id) }.forEach { prec ->
                                        val iconStr = WorkflowUiHelper.getPreconditionIconStr(prec.type)
                                        FilterChip(
                                            selected = true,
                                            onClick = {
                                                attachedVaultPrecIds = attachedVaultPrecIds - prec.id
                                            },
                                            label = { Text("$iconStr ${prec.name}", fontSize = 12.sp) },
                                            trailingIcon = { Icon(Icons.Default.Clear, contentDescription = "Remove", modifier = Modifier.height(14.dp).width(14.dp)) },
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Contextual Test Payload Configuration Card for Trigger Nodes
                if (node.type in listOf(NodeType.SMS_TRIGGER, NodeType.NOTIFICATION_TRIGGER, NodeType.MANUAL_TRIGGER)) {
                    OutlinedCard(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Test Sample Data",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Configure test values for running this step.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            when (node.type) {
                                NodeType.SMS_TRIGGER -> {
                                    OutlinedTextField(
                                        value = testSender,
                                        onValueChange = { testSender = it },
                                        label = { Text("Test Sample Sender") },
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true
                                    )
                                    OutlinedTextField(
                                        value = testBody,
                                        onValueChange = { testBody = it },
                                        label = { Text("Test Sample SMS Text Body") },
                                        modifier = Modifier.fillMaxWidth(),
                                        minLines = 2
                                    )
                                }
                                NodeType.NOTIFICATION_TRIGGER -> {
                                    OutlinedTextField(
                                        value = testPackageName,
                                        onValueChange = { testPackageName = it },
                                        label = { Text("Test App Package Name") },
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true
                                    )
                                    OutlinedTextField(
                                        value = testTitle,
                                        onValueChange = { testTitle = it },
                                        label = { Text("Test Notification Title") },
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true
                                    )
                                    OutlinedTextField(
                                        value = testText,
                                        onValueChange = { testText = it },
                                        label = { Text("Test Notification Text Body") },
                                        modifier = Modifier.fillMaxWidth(),
                                        minLines = 2
                                    )
                                }
                                NodeType.MANUAL_TRIGGER -> {
                                    OutlinedTextField(
                                        value = testBody,
                                        onValueChange = { testBody = it },
                                        label = { Text("Test Manual Input Payload") },
                                        modifier = Modifier.fillMaxWidth(),
                                        minLines = 2
                                    )
                                }
                                else -> {}
                            }
                        }
                    }
                }
            }
        }
    }

@Composable
fun NodeConfigDialog(
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
    onSaveCredential: ((Credential) -> Unit)? = null
) {
    NodeConfigBottomSheet(
        node = node,
        credentials = credentials,
        vaultPreconditions = vaultPreconditions,
        hasNotificationListenerPermission = hasNotificationListenerPermission,
        hasPostNotificationsPermission = hasPostNotificationsPermission,
        hasSmsPermission = hasSmsPermission,
        onRequestSmsPermission = onRequestSmsPermission,
        onRequestPostNotificationsPermission = onRequestPostNotificationsPermission,
        onDismiss = onDismiss,
        onSave = onSave,
        onSaveCredential = onSaveCredential
    )
}
