package `in`.ntfysms.ui

import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import `in`.ntfysms.BuildConfig
import `in`.ntfysms.data.DeliveryNode
import `in`.ntfysms.data.ServerType
import `in`.ntfysms.filter.MessageTemplateParser
import androidx.core.net.toUri

@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    hasSmsPermission: Boolean,
    onRequestSmsPermission: () -> Unit,
    hasNotificationPermission: Boolean,
    onRequestNotificationPermission: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    var showGuideDialog by remember { mutableStateOf(false) }
    var editingNode by remember { mutableStateOf<DeliveryNode?>(null) }
    var showAddNodeDialog by remember { mutableStateOf(false) }

    // Live preview states
    var sampleSender by remember { mutableStateOf("VM-HDFCBK") }
    var sampleBody by remember { mutableStateOf("Your OTP for transaction of Rs 500 is 482910.") }
    val previewOutput = remember(sampleSender, sampleBody, uiState.senderRegex, uiState.bodyParseRegex, uiState.customMessageTemplate) {
        MessageTemplateParser.formatPayload(
            sender = sampleSender,
            body = sampleBody,
            senderRegex = uiState.senderRegex,
            bodyParseRegex = uiState.bodyParseRegex,
            template = uiState.customMessageTemplate
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item { Spacer(modifier = Modifier.height(4.dp)) }

            // System Permissions Section
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "System Permissions & Optimization",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )

                        // SMS Permission Row
                        PermissionStatusRow(
                            title = "SMS Permission (RECEIVE_SMS)",
                            subtitle = "Required to intercept incoming OTP SMS messages",
                            isGranted = hasSmsPermission,
                            onGrantClick = onRequestSmsPermission
                        )

                        // Notification Permission Row
                        if (Build.VERSION.SDK_INT >= 33) {
                            PermissionStatusRow(
                                title = "Notification Permission (POST_NOTIFICATIONS)",
                                subtitle = "Required to display foreground service status notification",
                                isGranted = hasNotificationPermission,
                                onGrantClick = onRequestNotificationPermission
                            )
                        }

                        // Battery Optimization Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Battery Optimization",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = if (!uiState.isBatteryOptimized) "Exempt (Recommended)" else "Active (May delay background forwarding)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (!uiState.isBatteryOptimized) Color(0xFF2E7D32) else Color(0xFFE65100)
                                )
                            }
                            if (uiState.isBatteryOptimized) {
                                OutlinedButton(
                                    onClick = {
                                        try {
                                            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                                data = "package:${context.packageName}".toUri()
                                            }
                                            context.startActivity(intent)
                                        } catch (_: Exception) {
                                            context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                                        }
                                    }
                                ) {
                                    Text("Ignore", fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }

            // Delivery Nodes Management Card (Node-Level Pre-Checks & Credentials)
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Delivery Target Nodes",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Target destination servers (ntfy / Gotify)",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontSize = 11.sp,
                                    color = Color.Gray
                                )
                            }
                            Button(
                                onClick = { showAddNodeDialog = true },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)
                            ) {
                                Text("+ Add Node", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                            }
                        }

                        val activeNodes = uiState.activeNodes
                        if (activeNodes.isEmpty()) {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = "📍 No Delivery Nodes Configured",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                    Text(
                                        text = "Add target nodes for ntfy or Gotify servers with VPN/healthcheck rules.",
                                        fontSize = 11.sp,
                                        color = Color.Gray
                                    )
                                    Button(
                                        onClick = { showAddNodeDialog = true },
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                                    ) {
                                        Text("+ Add Your First Delivery Node", fontSize = 12.sp)
                                    }
                                }
                            }
                        } else {
                            activeNodes.forEach { node ->
                                DeliveryNodeItemRow(
                                    node = node,
                                    onToggle = { enabled -> viewModel.toggleNode(node.id, enabled) },
                                    onEdit = { editingNode = node },
                                    onDelete = { viewModel.deleteNode(node.id) }
                                )
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Allow Insecure Private HTTP",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    text = "Allow HTTP for Tailscale (100.x) and private IPs (192.168.x, 10.x)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.Gray
                                )
                            }
                            Switch(
                                checked = uiState.allowInsecureHttp,
                                onCheckedChange = { viewModel.toggleAllowInsecureHttp(it) }
                            )
                        }
                    }
                }
            }

            // SMS Filtering Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Filter Rules",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )

                        OutlinedTextField(
                            value = uiState.allowlist,
                            onValueChange = { viewModel.updateAllowlist(it) },
                            label = { Text("Sender Allowlist (comma or newline separated)") },
                            placeholder = { Text(".* (matches all senders)") },
                            supportingText = { Text("Default: .* (matches all senders). Supports wildcards.") },
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = uiState.keywordRegex,
                            onValueChange = { viewModel.updateKeywordRegex(it) },
                            label = { Text("Keyword Regex") },
                            isError = !uiState.isRegexValid,
                            supportingText = {
                                if (!uiState.isRegexValid) {
                                    Text("Invalid Regular Expression syntax", color = MaterialTheme.colorScheme.error)
                                } else {
                                    Text("Default: .* (matches all message bodies)")
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            // Message Parsing & Custom Payload Format Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Regex Message Parsing & Payload Format",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )

                        OutlinedTextField(
                            value = uiState.bodyParseRegex,
                            onValueChange = { viewModel.updateBodyParseRegex(it) },
                            label = { Text("Body Extraction Regex (Capture Groups)") },
                            placeholder = { Text("\\b\\d{4,8}\\b") },
                            supportingText = { Text("Default: \\b\\d{4,8}\\b. Group 1 creates \${group1}") },
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = uiState.senderRegex,
                            onValueChange = { viewModel.updateSenderRegex(it) },
                            label = { Text("Sender Header Regex") },
                            placeholder = { Text("^([A-Z]{2})-([A-Z]+)$") },
                            supportingText = { Text("Capture groups map to \${senderGroup1}, \${senderGroup2}") },
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = uiState.customMessageTemplate,
                            onValueChange = { viewModel.updateCustomMessageTemplate(it) },
                            label = { Text("Custom Outgoing Payload Template") },
                            placeholder = { Text("[\${sender}] OTP Code: \${code}") },
                            supportingText = { Text("Available tags: \${sender}, \${body}, \${otp}, \${code}, \${group1}") },
                            modifier = Modifier.fillMaxWidth()
                        )

                        // Interactive Live Preview Box
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "Live Parsing & Formatting Tester",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                OutlinedTextField(
                                    value = sampleSender,
                                    onValueChange = { sampleSender = it },
                                    label = { Text("Sample Sender") },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true
                                )
                                OutlinedTextField(
                                    value = sampleBody,
                                    onValueChange = { sampleBody = it },
                                    label = { Text("Sample SMS Body") },
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Text(
                                    text = "Outgoing Payload Output:",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(MaterialTheme.colorScheme.background, RoundedCornerShape(6.dp))
                                        .padding(8.dp)
                                ) {
                                    Text(
                                        text = previewOutput,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onBackground
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Notification Listener Card (Optional)
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Notification Listener (Optional)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Forward App Notifications",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    text = "Intercept and forward incoming push notifications matching your filter rules",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.Gray
                                )
                            }
                            Switch(
                                checked = uiState.forwardNotifications,
                                onCheckedChange = { viewModel.toggleForwardNotifications(it) }
                            )
                        }

                        if (uiState.forwardNotifications && !uiState.hasNotificationListenerPermission) {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = "Notification Access Required",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                    Text(
                                        text = "Android requires Notification Access permission in System Settings to read notifications.",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                    Button(
                                        onClick = {
                                            try {
                                                val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                                                context.startActivity(intent)
                                            } catch (e: Exception) {
                                                context.startActivity(Intent(Settings.ACTION_SETTINGS))
                                            }
                                        }
                                    ) {
                                        Text("Grant Notification Access")
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Expiry Rules Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Queue & Expiry",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )

                        OutlinedTextField(
                            value = uiState.expiryMinutes,
                            onValueChange = { viewModel.updateExpiryMinutes(it) },
                            label = { Text("Expiry Time (Minutes)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            supportingText = { Text("Unsent pending messages older than this will expire") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                    }
                }
            }

            // Auto-Clear Old Message Logs Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Auto-Clear Old Message Logs",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Auto-Purge Old Log Records",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    text = "Automatically delete old log history records after specified time",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.Gray
                                )
                            }
                            Switch(
                                checked = uiState.autoClearEnabled,
                                onCheckedChange = { viewModel.toggleAutoClear(it) }
                            )
                        }

                        if (uiState.autoClearEnabled) {
                            OutlinedTextField(
                                value = uiState.autoClearMinutes,
                                onValueChange = { viewModel.updateAutoClearMinutes(it) },
                                label = { Text("Purge Records Older Than (Minutes)") },
                                placeholder = { Text("1440 (24 hours)") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                supportingText = { Text("e.g. 60 = 1 hour, 1440 = 24 hours") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                        }
                    }
                }
            }

            // Version Footer
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "NtfySMS v${BuildConfig.VERSION_NAME}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = Color.Gray
                    )
                    Text(
                        text = "Min SDK 26 (Android 8.0+) • Target SDK 37",
                        fontSize = 11.sp,
                        color = Color.Gray
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }
        }

        // Add / Edit Delivery Node Bottom Sheet
        if (showAddNodeDialog || editingNode != null) {
            val target = editingNode ?: DeliveryNode()
            AddEditNodeBottomSheet(
                initialNode = target,
                onDismiss = {
                    showAddNodeDialog = false
                    editingNode = null
                },
                onSave = { node ->
                    if (editingNode != null) {
                        viewModel.updateNode(node)
                    } else {
                        viewModel.addNode(node)
                    }
                    showAddNodeDialog = false
                    editingNode = null
                }
            )
        }

        // Setup Guide Bottom Sheet
        if (showGuideDialog) {
            ProviderGuideBottomSheet(
                onDismiss = { showGuideDialog = false }
            )
        }
    }
}

@Composable
fun DeliveryNodeItemRow(
    node: DeliveryNode,
    onToggle: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val (icon, typeColor) = if (node.serverType == ServerType.GOTIFY) "🔔" to Color(0xFF0284C7) else "🚀" to Color(0xFF16A34A)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = "$icon ", fontSize = 14.sp)
                    Text(text = node.name, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .background(typeColor, shape = RoundedCornerShape(4.dp))
                            .padding(horizontal = 5.dp, vertical = 1.dp)
                    ) {
                        Text(text = node.serverType, color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Switch(
                    checked = node.isEnabled,
                    onCheckedChange = onToggle,
                    modifier = Modifier.height(28.dp)
                )
            }

            Text(
                text = node.serverUrl.ifBlank { "No URL configured" },
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Pre-Checks Badges & Actions Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    if (node.requireInternet) {
                        Box(
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(10.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "🌐 Net",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }

                    if (node.requireVpn) {
                        Box(
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(10.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "🔒 VPN",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }

                    if (node.requireHealthcheck) {
                        Box(
                            modifier = Modifier
                                .background(Color(0xFFDCFCE7), RoundedCornerShape(10.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "🩺 Health (${node.healthcheckMethod})",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF15803D)
                            )
                        }
                    }

                    if (!node.requireInternet && !node.requireVpn && !node.requireHealthcheck) {
                        Text(text = "No pre-checks", fontSize = 10.sp, color = Color.Gray)
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(
                        onClick = onEdit,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier.height(28.dp)
                    ) {
                        Text("Edit", fontSize = 11.sp)
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    TextButton(
                        onClick = onDelete,
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                        modifier = Modifier.height(28.dp)
                    ) {
                        Text("Del", fontSize = 11.sp, color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditNodeBottomSheet(
    initialNode: DeliveryNode,
    onDismiss: () -> Unit,
    onSave: (DeliveryNode) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var name by remember { mutableStateOf(initialNode.name) }
    var serverType by remember { mutableStateOf(initialNode.serverType) }
    var serverUrl by remember { mutableStateOf(initialNode.serverUrl) }
    var topic by remember { mutableStateOf(initialNode.topic) }
    var token by remember { mutableStateOf(initialNode.token) }
    var showToken by remember { mutableStateOf(false) }

    // Node-level pre-checks
    var requireInternet by remember { mutableStateOf(initialNode.requireInternet) }
    var requireVpn by remember { mutableStateOf(initialNode.requireVpn) }
    var requireHealthcheck by remember { mutableStateOf(initialNode.requireHealthcheck) }
    var customHealthcheckUrl by remember { mutableStateOf(initialNode.customHealthcheckUrl) }
    var healthcheckMethod by remember { mutableStateOf(initialNode.healthcheckMethod) }
    var healthcheckHeaders by remember { mutableStateOf(initialNode.healthcheckHeaders) }
    var healthcheckExpectedStatus by remember { mutableStateOf(initialNode.healthcheckExpectedStatus.toString()) }

    val isEditMode = initialNode.id.isNotBlank() && initialNode.id != "default-node"

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = if (isEditMode) "Edit Target Delivery Node" else "Add Target Delivery Node",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                    Text(
                        text = "Configure destination credentials & healthcheck rules",
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                }
                TextButton(onClick = onDismiss) {
                    Text("Cancel", color = Color.Gray)
                }
            }

            // Section 1: Target Destination Credentials
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "1. Destination Credentials",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.primary
                    )

                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Node Display Name") },
                        placeholder = { Text("e.g. Tailscale ntfy Server") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Text(
                        text = "Target Service Provider",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.Gray
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(
                            ServerType.NTFY to "🚀 ntfy",
                            ServerType.GOTIFY to "🔔 Gotify"
                        ).forEach { (type, label) ->
                            if (serverType == type) {
                                Button(onClick = { serverType = type }, modifier = Modifier.weight(1f)) {
                                    Text(label, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            } else {
                                OutlinedButton(onClick = { serverType = type }, modifier = Modifier.weight(1f)) {
                                    Text(label, fontSize = 12.sp)
                                }
                            }
                        }
                    }

                    OutlinedTextField(
                        value = serverUrl,
                        onValueChange = { serverUrl = it },
                        label = { Text("Server URL") },
                        placeholder = { Text(if (serverType == ServerType.GOTIFY) "https://gotify.example.com" else "https://ntfy.sh") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    if (serverType == ServerType.NTFY) {
                        OutlinedTextField(
                            value = topic,
                            onValueChange = { topic = it },
                            label = { Text("ntfy Topic Name") },
                            placeholder = { Text("my-private-otp-topic") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                    }

                    OutlinedTextField(
                        value = token,
                        onValueChange = { token = it },
                        label = { Text(if (serverType == ServerType.GOTIFY) "App Token (X-Gotify-Key)" else "Access Token (Optional)") },
                        placeholder = { Text(if (serverType == ServerType.GOTIFY) "A1b2C3d4..." else "tk_1a2b3c...") },
                        visualTransformation = if (showToken) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            TextButton(onClick = { showToken = !showToken }) {
                                Text(if (showToken) "Hide" else "Show", fontSize = 11.sp)
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            }

            // Section 2: Node-Level Pre-Checks
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "2. Pre-Checks & Throttling Rules",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = "🌐 Require Internet Connection", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            Text(text = "Verifies network connectivity before delivery", fontSize = 10.sp, color = Color.Gray)
                        }
                        Switch(checked = requireInternet, onCheckedChange = { requireInternet = it })
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = "🔒 Require Active VPN", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            Text(text = "Throttles node if VPN is disconnected", fontSize = 10.sp, color = Color.Gray)
                        }
                        Switch(checked = requireVpn, onCheckedChange = { requireVpn = it })
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = "🩺 Require Server Healthcheck Ping", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            Text(text = "Pings server before attempting payload delivery", fontSize = 10.sp, color = Color.Gray)
                        }
                        Switch(checked = requireHealthcheck, onCheckedChange = { requireHealthcheck = it })
                    }
                }
            }

            // Section 3: Custom Healthcheck Request Settings
            if (requireHealthcheck) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "3. Custom HTTP Healthcheck Request",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.primary
                        )

                        Text(
                            text = "HTTP Method",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.Gray
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf("GET", "HEAD", "POST").forEach { m ->
                                if (healthcheckMethod.equals(m, ignoreCase = true)) {
                                    Button(onClick = { healthcheckMethod = m }, modifier = Modifier.weight(1f)) {
                                        Text(m, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                } else {
                                    OutlinedButton(onClick = { healthcheckMethod = m }, modifier = Modifier.weight(1f)) {
                                        Text(m, fontSize = 11.sp)
                                    }
                                }
                            }
                        }

                        OutlinedTextField(
                            value = customHealthcheckUrl,
                            onValueChange = { customHealthcheckUrl = it },
                            label = { Text("Custom Healthcheck URL") },
                            placeholder = { Text("https://my-server.com/health") },
                            supportingText = { Text("Defaults to main server URL if left blank") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )

                        OutlinedTextField(
                            value = healthcheckExpectedStatus,
                            onValueChange = { healthcheckExpectedStatus = it },
                            label = { Text("Expected HTTP Status Code") },
                            placeholder = { Text("200") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )

                        OutlinedTextField(
                            value = healthcheckHeaders,
                            onValueChange = { healthcheckHeaders = it },
                            label = { Text("Custom HTTP Headers") },
                            placeholder = { Text("X-Health-Key: secret123\nAuthorization: Bearer token") },
                            supportingText = { Text("Separate multiple headers with newlines (Key: Value)") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            // Save Action Button
            Button(
                onClick = {
                    if (serverUrl.isNotBlank()) {
                        val fallbackName = if (serverType == ServerType.GOTIFY) "Gotify Target Node" else "ntfy Target Node"
                        onSave(
                            initialNode.copy(
                                name = name.ifBlank { fallbackName },
                                serverType = serverType,
                                serverUrl = serverUrl.trim(),
                                topic = topic.trim(),
                                token = token.trim(),
                                requireInternet = requireInternet,
                                requireVpn = requireVpn,
                                requireHealthcheck = requireHealthcheck,
                                customHealthcheckUrl = customHealthcheckUrl.trim(),
                                healthcheckMethod = healthcheckMethod,
                                healthcheckHeaders = healthcheckHeaders,
                                healthcheckExpectedStatus = healthcheckExpectedStatus.toIntOrNull() ?: 200
                            )
                        )
                    }
                },
                enabled = serverUrl.isNotBlank(),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp)
            ) {
                Text(
                    text = if (isEditMode) "Save Changes" else "Add Delivery Node",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
fun PermissionStatusRow(
    title: String,
    subtitle: String,
    isGranted: Boolean,
    onGrantClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = if (isGranted) "Granted" else subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = if (isGranted) Color(0xFF2E7D32) else Color.Red
            )
        }
        if (!isGranted) {
            Button(onClick = onGrantClick) {
                Text("Grant", fontSize = 12.sp)
            }
        }
    }
}
