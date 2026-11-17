package `in`.ntfysms.ui.credentials

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
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
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
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import `in`.ntfysms.domain.model.Credential
import `in`.ntfysms.domain.model.CredentialType
import `in`.ntfysms.ui.MainViewModel
import `in`.ntfysms.ui.components.HeaderInputEditor
import `in`.ntfysms.ui.components.NoSwipeBottomSheet

@Composable
fun CredentialsScreen(
    viewModel: MainViewModel,
    onBack: (() -> Unit)? = null
) {
    val credentials by viewModel.credentials.collectAsState()
    var editingCredential by remember { mutableStateOf<Credential?>(null) }
    var pendingDeleteCredential by remember { mutableStateOf<Credential?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }

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
                            text = "Saved Credentials & Keys",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Securely store passwords, access tokens, and API keys to use in your automation rules.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (credentials.isEmpty()) {
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
                                text = "No saved credentials found. Tap '+ Add Credential' below to add one.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                items(credentials, key = { it.id }) { cred ->
                    CredentialCardItem(
                        credential = cred,
                        onEdit = { editingCredential = cred },
                        onDelete = { pendingDeleteCredential = cred }
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(80.dp)) }
        }

        ExtendedFloatingActionButton(
            onClick = { showAddDialog = true },
            icon = { Icon(Icons.Default.Add, contentDescription = "Add Credential") },
            text = { Text("Add Credential") },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        )
    }

    if (showAddDialog) {
        CredentialDialog(
            credential = null,
            onDismiss = { showAddDialog = false },
            onSave = { newCred ->
                viewModel.saveCredential(newCred)
                showAddDialog = false
            }
        )
    }

    editingCredential?.let { cred ->
        CredentialDialog(
            credential = cred,
            onDismiss = { editingCredential = null },
            onSave = { updated ->
                viewModel.saveCredential(updated)
                editingCredential = null
            }
        )
    }

    // Confirm Delete Credential Dialog
    pendingDeleteCredential?.let { cred ->
        AlertDialog(
            onDismissRequest = { pendingDeleteCredential = null },
            title = { Text("Delete Credential?") },
            text = { Text("Are you sure you want to delete credential '${cred.name}'?") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteCredential(cred.id)
                        pendingDeleteCredential = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteCredential = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CredentialCardItem(
    credential: Credential,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { dismissValue ->
            if (dismissValue == SwipeToDismissBoxValue.EndToStart || dismissValue == SwipeToDismissBoxValue.StartToEnd) {
                onDelete()
                false
            } else false
        }
    )

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
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = credential.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    SuggestionChip(
                        onClick = {},
                        label = {
                            Text(
                                text = credential.type.name,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold
                            )
                        },
                        colors = SuggestionChipDefaults.suggestionChipColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            labelColor = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    )
                }

                // Bottom Right Edit Action
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(onClick = onEdit) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit Credential"
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
fun CredentialBottomSheet(
    credential: Credential?,
    onDismiss: () -> Unit,
    onSave: (Credential) -> Unit
) {

    var name by remember { mutableStateOf(credential?.name ?: "") }
    var selectedType by remember { mutableStateOf(credential?.type ?: CredentialType.BEARER_TOKEN) }
    var expandedMenu by remember { mutableStateOf(false) }

    var bearerToken by remember { mutableStateOf(credential?.dataMap?.get("token") ?: "") }
    var basicUser by remember { mutableStateOf(credential?.dataMap?.get("username") ?: "") }
    var basicPass by remember { mutableStateOf(credential?.dataMap?.get("password") ?: "") }
    var headers by remember { mutableStateOf(credential?.dataMap?.get("headers") ?: "") }
    var ntfyUrl by remember { mutableStateOf(credential?.dataMap?.get("serverUrl") ?: "https://ntfy.sh") }
    var ntfyTopic by remember { mutableStateOf(credential?.dataMap?.get("topic") ?: "") }
    var gotifyUrl by remember { mutableStateOf(credential?.dataMap?.get("serverUrl") ?: "") }
    var gotifyToken by remember { mutableStateOf(credential?.dataMap?.get("appToken") ?: "") }

    var passwordVisible by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val visualTransform = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation()
    val trailingIcon: @Composable () -> Unit = {
        val image = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff
        IconButton(onClick = { passwordVisible = !passwordVisible }) {
            Icon(imageVector = image, contentDescription = if (passwordVisible) "Hide password" else "Show password")
        }
    }

    NoSwipeBottomSheet(
        onDismissRequest = onDismiss
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            // Header Row with Title and Top Right Close Button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(top = 16.dp, bottom = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (credential == null) "New Credential" else "Edit Credential",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Scrollable Content Area
            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .padding(horizontal = 20.dp)
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
                    label = { Text("Credential Name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                ExposedDropdownMenuBox(
                    expanded = expandedMenu,
                    onExpandedChange = { expandedMenu = !expandedMenu }
                ) {
                    OutlinedTextField(
                        value = selectedType.displayName(),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Credential Type") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedMenu) },
                        modifier = Modifier
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                            .fillMaxWidth()
                    )

                    ExposedDropdownMenu(
                        expanded = expandedMenu,
                        onDismissRequest = { expandedMenu = false }
                    ) {
                        CredentialType.entries.forEach { type ->
                            DropdownMenuItem(
                                text = { Text(type.displayName()) },
                                onClick = {
                                    selectedType = type
                                    expandedMenu = false
                                }
                            )
                        }
                    }
                }

                when (selectedType) {
                    CredentialType.BEARER_TOKEN -> {
                        OutlinedTextField(
                            value = bearerToken,
                            onValueChange = { bearerToken = it },
                            label = { Text("Bearer Token") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            visualTransformation = visualTransform,
                            trailingIcon = trailingIcon
                        )
                    }
                    CredentialType.BASIC_AUTH -> {
                        OutlinedTextField(
                            value = basicUser,
                            onValueChange = { basicUser = it },
                            label = { Text("Username") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = basicPass,
                            onValueChange = { basicPass = it },
                            label = { Text("Password") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            visualTransformation = visualTransform,
                            trailingIcon = trailingIcon
                        )
                    }
                    CredentialType.HEADER_SET -> {
                        HeaderInputEditor(
                            headersString = headers,
                            onHeadersChanged = { headers = it }
                        )
                    }
                    CredentialType.NTFY_AUTH -> {
                        OutlinedTextField(
                            value = ntfyUrl,
                            onValueChange = { ntfyUrl = it },
                            label = { Text("ntfy Server URL") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = ntfyTopic,
                            onValueChange = { ntfyTopic = it },
                            label = { Text("Default Topic Name") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = bearerToken,
                            onValueChange = { bearerToken = it },
                            label = { Text("Auth Token (Optional)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            visualTransformation = visualTransform,
                            trailingIcon = trailingIcon
                        )
                    }
                    CredentialType.GOTIFY_AUTH -> {
                        OutlinedTextField(
                            value = gotifyUrl,
                            onValueChange = { gotifyUrl = it },
                            label = { Text("Gotify Server URL") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = gotifyToken,
                            onValueChange = { gotifyToken = it },
                            label = { Text("App Token") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            visualTransformation = visualTransform,
                            trailingIcon = trailingIcon
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Sticky Bottom Action Bar (Sticky from Left, Right, Bottom)
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surfaceContainer,
                tonalElevation = 6.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
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
                                        errorMessage = "Bearer token cannot be blank."
                                        return@Button
                                    }
                                }
                                CredentialType.BASIC_AUTH -> {
                                    if (basicUser.isBlank() || basicPass.isBlank()) {
                                        errorMessage = "Username and password cannot be blank."
                                        return@Button
                                    }
                                }
                                CredentialType.HEADER_SET -> {
                                    if (headers.isBlank()) {
                                        errorMessage = "Custom headers cannot be blank."
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

                            val dataMap = mutableMapOf<String, String>()
                            when (selectedType) {
                                CredentialType.BEARER_TOKEN -> dataMap["token"] = bearerToken
                                CredentialType.BASIC_AUTH -> {
                                    dataMap["username"] = basicUser
                                    dataMap["password"] = basicPass
                                }
                                CredentialType.HEADER_SET -> dataMap["headers"] = headers
                                CredentialType.NTFY_AUTH -> {
                                    dataMap["serverUrl"] = ntfyUrl
                                    dataMap["topic"] = ntfyTopic
                                    dataMap["token"] = bearerToken
                                }
                                CredentialType.GOTIFY_AUTH -> {
                                    dataMap["serverUrl"] = gotifyUrl
                                    dataMap["appToken"] = gotifyToken
                                }
                            }

                            val updated = (credential ?: Credential(name = name, type = selectedType)).copy(
                                name = name,
                                type = selectedType,
                                dataMap = dataMap,
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
    }
}

@Composable
fun CredentialDialog(
    credential: Credential?,
    onDismiss: () -> Unit,
    onSave: (Credential) -> Unit
) {
    CredentialBottomSheet(
        credential = credential,
        onDismiss = onDismiss,
        onSave = onSave
    )
}
