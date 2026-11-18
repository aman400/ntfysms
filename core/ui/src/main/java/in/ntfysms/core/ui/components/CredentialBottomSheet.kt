package `in`.ntfysms.core.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import `in`.ntfysms.domain.model.Credential
import `in`.ntfysms.domain.model.CredentialType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CredentialBottomSheet(
    credential: Credential?,
    onDismiss: () -> Unit,
    onSave: (Credential) -> Unit
) {
    NoSwipeBottomSheet(
        onDismissRequest = onDismiss
    ) {
        var name by remember { mutableStateOf(credential?.name ?: "") }
        var selectedType by remember { mutableStateOf(credential?.type ?: CredentialType.BEARER_TOKEN) }
        var expandedTypeMenu by remember { mutableStateOf(false) }
        var errorMessage by remember { mutableStateOf<String?>(null) }

        val dataMap = credential?.dataMap ?: emptyMap()
        var bearerToken by remember { mutableStateOf(dataMap["token"] ?: "") }
        var basicUser by remember { mutableStateOf(dataMap["username"] ?: "") }
        var basicPass by remember { mutableStateOf(dataMap["password"] ?: "") }
        var headers by remember { mutableStateOf(dataMap["headers"] ?: "") }
        var ntfyUrl by remember { mutableStateOf(dataMap["serverUrl"] ?: "https://ntfy.sh") }
        var ntfyTopic by remember { mutableStateOf(dataMap["topic"] ?: "") }
        var gotifyUrl by remember { mutableStateOf(dataMap["serverUrl"] ?: "https://gotify.example.com") }
        var gotifyToken by remember { mutableStateOf(dataMap["appToken"] ?: "") }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(if (credential == null) "New Credential" else "Edit Credential") },
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
                                    errorMessage = "Credential name cannot be blank."
                                    return@Button
                                }
                                when (selectedType) {
                                    CredentialType.BEARER_TOKEN -> {
                                        if (bearerToken.isBlank()) {
                                            errorMessage = "API key token cannot be blank."
                                            return@Button
                                        }
                                    }
                                    CredentialType.BASIC_AUTH -> {
                                        if (basicUser.isBlank()) {
                                            errorMessage = "Username cannot be blank."
                                            return@Button
                                        }
                                    }
                                    CredentialType.HEADER_SET -> {
                                        if (headers.isBlank()) {
                                            errorMessage = "Headers string cannot be blank."
                                            return@Button
                                        }
                                    }
                                    CredentialType.NTFY_AUTH -> {
                                        if (ntfyUrl.isBlank() || ntfyTopic.isBlank()) {
                                            errorMessage = "Server URL and topic cannot be blank."
                                            return@Button
                                        }
                                    }
                                    CredentialType.GOTIFY_AUTH -> {
                                        if (gotifyUrl.isBlank() || gotifyToken.isBlank()) {
                                            errorMessage = "Server URL and app token cannot be blank."
                                            return@Button
                                        }
                                    }
                                }
                                errorMessage = null

                                val newDataMap = mutableMapOf<String, String>()
                                when (selectedType) {
                                    CredentialType.BEARER_TOKEN -> newDataMap["token"] = bearerToken
                                    CredentialType.BASIC_AUTH -> {
                                        newDataMap["username"] = basicUser
                                        newDataMap["password"] = basicPass
                                    }
                                    CredentialType.HEADER_SET -> newDataMap["headers"] = headers
                                    CredentialType.NTFY_AUTH -> {
                                        newDataMap["serverUrl"] = ntfyUrl
                                        newDataMap["topic"] = ntfyTopic
                                        newDataMap["token"] = bearerToken
                                    }
                                    CredentialType.GOTIFY_AUTH -> {
                                        newDataMap["serverUrl"] = gotifyUrl
                                        newDataMap["appToken"] = gotifyToken
                                    }
                                }

                                val updated = (credential ?: Credential(name = name, type = selectedType)).copy(
                                    name = name,
                                    type = selectedType,
                                    dataMap = newDataMap,
                                    updatedAt = System.currentTimeMillis()
                                )
                                onSave(updated)
                            }
                        ) {
                            Text("Save Credential")
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
                    label = "Credential Name",
                    tooltipText = "Unique display name for identifying this set of authentication credentials in your vault.",
                    modifier = Modifier.fillMaxWidth()
                )

                ExposedDropdownMenuBox(
                    expanded = expandedTypeMenu,
                    onExpandedChange = { expandedTypeMenu = !expandedTypeMenu }
                ) {
                    OutlinedTextField(
                        value = selectedType.displayName(),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Credential Type") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedTypeMenu) },
                        modifier = Modifier
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                            .fillMaxWidth()
                    )

                    ExposedDropdownMenu(
                        expanded = expandedTypeMenu,
                        onDismissRequest = { expandedTypeMenu = false }
                    ) {
                        CredentialType.values().forEach { typeVal ->
                            DropdownMenuItem(
                                text = { Text(typeVal.displayName()) },
                                onClick = {
                                    selectedType = typeVal
                                    expandedTypeMenu = false
                                }
                            )
                        }
                    }
                }

                when (selectedType) {
                    CredentialType.BEARER_TOKEN -> {
                        SensitiveTextField(
                            value = bearerToken,
                            onValueChange = { bearerToken = it },
                            label = "API Bearer Token / Secret Key",
                            tooltipText = "Secret authentication token or API key sent via HTTP Authorization header.",
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    CredentialType.BASIC_AUTH -> {
                        InfoTooltipField(
                            value = basicUser,
                            onValueChange = { basicUser = it },
                            label = "Username",
                            tooltipText = "HTTP Basic authentication username.",
                            modifier = Modifier.fillMaxWidth()
                        )
                        SensitiveTextField(
                            value = basicPass,
                            onValueChange = { basicPass = it },
                            label = "Password",
                            tooltipText = "HTTP Basic authentication password.",
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    CredentialType.HEADER_SET -> {
                        HeaderInputEditor(
                            headersString = headers,
                            onHeadersChanged = { headers = it }
                        )
                    }
                    CredentialType.NTFY_AUTH -> {
                        InfoTooltipField(
                            value = ntfyUrl,
                            onValueChange = { ntfyUrl = it },
                            label = "ntfy Server URL",
                            tooltipText = "Base URL of your ntfy push server (e.g. https://ntfy.sh).",
                            modifier = Modifier.fillMaxWidth()
                        )
                        InfoTooltipField(
                            value = ntfyTopic,
                            onValueChange = { ntfyTopic = it },
                            label = "ntfy Topic Name",
                            tooltipText = "Destination topic name on ntfy server.",
                            modifier = Modifier.fillMaxWidth()
                        )
                        SensitiveTextField(
                            value = bearerToken,
                            onValueChange = { bearerToken = it },
                            label = "ntfy Auth Token (Optional)",
                            tooltipText = "Optional access token for protected ntfy topics.",
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    CredentialType.GOTIFY_AUTH -> {
                        InfoTooltipField(
                            value = gotifyUrl,
                            onValueChange = { gotifyUrl = it },
                            label = "Gotify Server URL",
                            tooltipText = "Base URL of your Gotify push notification server.",
                            modifier = Modifier.fillMaxWidth()
                        )
                        SensitiveTextField(
                            value = gotifyToken,
                            onValueChange = { gotifyToken = it },
                            label = "Gotify App Token",
                            tooltipText = "Application token generated in your Gotify dashboard.",
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                Spacer(modifier = Modifier.height(36.dp))
            }
        }
    }
}

@Composable
fun SensitiveTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    tooltipText: String,
    modifier: Modifier = Modifier
) {
    var passwordVisible by remember { mutableStateOf(false) }
    var showTooltip by remember { mutableStateOf(false) }

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = modifier,
        singleLine = true,
        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Password
        ),
        trailingIcon = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                    Icon(
                        imageVector = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                        contentDescription = "Toggle Password Visibility"
                    )
                }
                Box {
                    IconButton(onClick = { showTooltip = !showTooltip }) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Field Information",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    androidx.compose.material3.DropdownMenu(
                        expanded = showTooltip,
                        onDismissRequest = { showTooltip = false }
                    ) {
                        Column(
                            modifier = Modifier
                                .widthIn(max = 260.dp)
                                .padding(12.dp)
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
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
    )
}
