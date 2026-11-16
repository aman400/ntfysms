package `in`.ntfysms.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import `in`.ntfysms.data.ServerType
import `in`.ntfysms.filter.MessageTemplateParser

@OptIn(ExperimentalMaterial3Api::class)
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

    var showToken by remember { mutableStateOf(false) }
    var showGuideDialog by remember { mutableStateOf(false) }

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

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Settings & Permissions",
                        fontWeight = FontWeight.Bold
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
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
                                                data = Uri.parse("package:${context.packageName}")
                                            }
                                            context.startActivity(intent)
                                        } catch (e: Exception) {
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

            // Target Delivery Service Selector Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Active Delivery Service Provider",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            TextButton(onClick = { showGuideDialog = true }) {
                                Text("📖 Setup Guide", fontSize = 12.sp)
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(
                                ServerType.NTFY to "🚀 ntfy",
                                ServerType.GOTIFY to "🔔 Gotify"
                            ).forEach { (type, label) ->
                                val isSelected = uiState.serverType == type
                                if (isSelected) {
                                    Button(
                                        onClick = { viewModel.updateServerType(type) },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text(label, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                    }
                                } else {
                                    OutlinedButton(
                                        onClick = { viewModel.updateServerType(type) },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text(label, fontSize = 13.sp)
                                    }
                                }
                            }
                        }

                        // Dedicated ntfy Settings Inputs Card
                        if (uiState.serverType == ServerType.NTFY) {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
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
                                            text = "🚀 ntfy Destination Credentials",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        TextButton(onClick = { showGuideDialog = true }) {
                                            Text("How to setup?", fontSize = 11.sp)
                                        }
                                    }

                                    OutlinedTextField(
                                        value = uiState.ntfyServerUrl,
                                        onValueChange = { viewModel.updateNtfyServerUrl(it) },
                                        label = { Text("ntfy Server URL") },
                                        placeholder = { Text("https://ntfy.sh") },
                                        isError = !uiState.isUrlValid,
                                        supportingText = {
                                            if (!uiState.isUrlValid) {
                                                Text("Must be HTTPS (or localhost / private IP with opt-in enabled)", color = MaterialTheme.colorScheme.error)
                                            } else {
                                                Text("e.g. https://ntfy.sh or self-hosted server")
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true
                                    )

                                    OutlinedTextField(
                                        value = uiState.ntfyTopic,
                                        onValueChange = { viewModel.updateNtfyTopic(it) },
                                        label = { Text("ntfy Topic Name") },
                                        placeholder = { Text("my-secret-otp-topic") },
                                        supportingText = { Text("Required topic target path") },
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true
                                    )

                                    OutlinedTextField(
                                        value = uiState.ntfyToken,
                                        onValueChange = { viewModel.updateNtfyToken(it) },
                                        label = { Text("ntfy Access Token (Optional)") },
                                        placeholder = { Text("tk_1a2b3c4d...") },
                                        supportingText = { Text("Optional Bearer token for protected ntfy topics") },
                                        visualTransformation = if (showToken) VisualTransformation.None else PasswordVisualTransformation(),
                                        trailingIcon = {
                                            TextButton(onClick = { showToken = !showToken }) {
                                                Text(if (showToken) "Hide" else "Show")
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true
                                    )
                                }
                            }
                        }

                        // Dedicated Gotify Settings Inputs Card
                        if (uiState.serverType == ServerType.GOTIFY) {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
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
                                            text = "🔔 Gotify Destination Credentials",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp,
                                            color = Color(0xFF0284C7)
                                        )
                                        TextButton(onClick = { showGuideDialog = true }) {
                                            Text("How to setup?", fontSize = 11.sp)
                                        }
                                    }

                                    OutlinedTextField(
                                        value = uiState.gotifyServerUrl,
                                        onValueChange = { viewModel.updateGotifyServerUrl(it) },
                                        label = { Text("Gotify Server URL") },
                                        placeholder = { Text("https://gotify.example.com") },
                                        isError = !uiState.isUrlValid,
                                        supportingText = {
                                            if (!uiState.isUrlValid) {
                                                Text("Must be HTTPS (or localhost / private IP with opt-in enabled)", color = MaterialTheme.colorScheme.error)
                                            } else {
                                                Text("e.g. https://gotify.example.com")
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true
                                    )

                                    OutlinedTextField(
                                        value = uiState.gotifyToken,
                                        onValueChange = { viewModel.updateGotifyToken(it) },
                                        label = { Text("Gotify App Token (X-Gotify-Key)") },
                                        placeholder = { Text("A1b2C3d4E5f6g7") },
                                        supportingText = { Text("Application token generated in Gotify web UI") },
                                        visualTransformation = if (showToken) VisualTransformation.None else PasswordVisualTransformation(),
                                        trailingIcon = {
                                            TextButton(onClick = { showToken = !showToken }) {
                                                Text(if (showToken) "Hide" else "Show")
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true
                                    )
                                }
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

            item { Spacer(modifier = Modifier.height(36.dp)) }
        }

        // Setup Guide Dialog
        if (showGuideDialog) {
            ProviderGuideDialog(
                serverType = uiState.serverType,
                onDismiss = { showGuideDialog = false }
            )
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
