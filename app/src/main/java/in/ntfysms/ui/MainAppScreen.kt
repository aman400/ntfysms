package `in`.ntfysms.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import `in`.ntfysms.data.PreconditionEntity
import `in`.ntfysms.domain.model.Workflow
import `in`.ntfysms.ui.credentials.CredentialsScreen
import `in`.ntfysms.ui.preconditions.PreconditionEditorScreen
import `in`.ntfysms.ui.preconditions.PreconditionsScreen
import `in`.ntfysms.ui.workflows.WorkflowEditorScreen
import `in`.ntfysms.ui.workflows.WorkflowLogsScreen
import `in`.ntfysms.ui.workflows.WorkflowsListScreen

enum class AppTab {
    WORKFLOWS,
    PRECONDITIONS,
    CREDENTIALS,
    SETTINGS
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppScreen(
    viewModel: MainViewModel,
    hasSmsPermission: Boolean,
    onRequestSmsPermission: () -> Unit,
    hasNotificationPermission: Boolean,
    onRequestNotificationPermission: () -> Unit
) {
    var selectedTab by remember { mutableStateOf(AppTab.WORKFLOWS) }
    var activeEditingWorkflow by remember { mutableStateOf<Workflow?>(null) }
    var activeLogsWorkflow by remember { mutableStateOf<Workflow?>(null) }
    var activeEditingPrecondition by remember { mutableStateOf<PreconditionEntity?>(null) }
    var showAddPreconditionScreen by remember { mutableStateOf(false) }
    var showGuideSheet by remember { mutableStateOf(false) }

    val uiState by viewModel.uiState.collectAsState()
    val credentials by viewModel.credentials.collectAsState()
    val preconditions by viewModel.preconditions.collectAsState()

    // Handle Back Navigation
    BackHandler(enabled = activeEditingWorkflow != null) {
        activeEditingWorkflow = null
    }

    BackHandler(enabled = activeLogsWorkflow != null) {
        activeLogsWorkflow = null
    }

    BackHandler(enabled = showAddPreconditionScreen || activeEditingPrecondition != null) {
        showAddPreconditionScreen = false
        activeEditingPrecondition = null
    }

    BackHandler(enabled = activeEditingWorkflow == null && activeLogsWorkflow == null && !showAddPreconditionScreen && activeEditingPrecondition == null && selectedTab != AppTab.WORKFLOWS) {
        selectedTab = AppTab.WORKFLOWS
    }

    if (activeEditingWorkflow != null) {
        WorkflowEditorScreen(
            workflow = activeEditingWorkflow!!,
            credentials = credentials,
            vaultPreconditions = preconditions,
            hasNotificationListenerPermission = uiState.hasNotificationListenerPermission,
            hasPostNotificationsPermission = hasNotificationPermission,
            hasSmsPermission = hasSmsPermission,
            onRequestSmsPermission = onRequestSmsPermission,
            onRequestPostNotificationsPermission = onRequestNotificationPermission,
            onBack = { activeEditingWorkflow = null },
            onSaveWorkflow = { updated ->
                viewModel.saveWorkflow(updated)
                activeEditingWorkflow = null
            },
            onTestRunWorkflow = { wf, callback ->
                viewModel.testRunWorkflow(wf, callback)
            },
            onTestRunWorkflowWithProgress = { wf, onProgress, onResult ->
                viewModel.testRunWorkflowWithProgress(wf, onProgress, onResult)
            },
            onSaveCredential = { newCred ->
                viewModel.saveCredential(newCred)
            },
            onSavePrecondition = { newPrec ->
                viewModel.savePrecondition(newPrec)
            },
            viewModel = viewModel
        )
        return
    }

    if (activeLogsWorkflow != null) {
        WorkflowLogsScreen(
            workflow = activeLogsWorkflow!!,
            viewModel = viewModel,
            onBack = { activeLogsWorkflow = null }
        )
        return
    }

    if (showAddPreconditionScreen || activeEditingPrecondition != null) {
        PreconditionEditorScreen(
            precondition = activeEditingPrecondition,
            credentials = credentials,
            viewModel = viewModel,
            onDismiss = {
                showAddPreconditionScreen = false
                activeEditingPrecondition = null
            },
            onSave = { entity ->
                viewModel.savePrecondition(entity)
                showAddPreconditionScreen = false
                activeEditingPrecondition = null
            }
        )
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = when (selectedTab) {
                            AppTab.WORKFLOWS -> "Workflows Engine"
                            AppTab.PRECONDITIONS -> "Preconditions Vault"
                            AppTab.CREDENTIALS -> "Credential Vault"
                            AppTab.SETTINGS -> "Settings & Permissions"
                        },
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                actions = {
                    IconButton(onClick = { showGuideSheet = true }) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Setup Guide",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surfaceContainer
            ) {
                NavigationBarItem(
                    selected = selectedTab == AppTab.WORKFLOWS,
                    onClick = { selectedTab = AppTab.WORKFLOWS },
                    label = { Text("Workflows", fontSize = 11.sp) },
                    icon = {
                        Icon(
                            imageVector = Icons.Default.AccountTree,
                            contentDescription = "Workflows"
                        )
                    },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        indicatorColor = MaterialTheme.colorScheme.primaryContainer
                    )
                )
                NavigationBarItem(
                    selected = selectedTab == AppTab.PRECONDITIONS,
                    onClick = { selectedTab = AppTab.PRECONDITIONS },
                    label = { Text("Preconditions", fontSize = 11.sp) },
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = "Preconditions"
                        )
                    },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        indicatorColor = MaterialTheme.colorScheme.primaryContainer
                    )
                )
                NavigationBarItem(
                    selected = selectedTab == AppTab.CREDENTIALS,
                    onClick = { selectedTab = AppTab.CREDENTIALS },
                    label = { Text("Credentials", fontSize = 11.sp) },
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Credentials"
                        )
                    },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        indicatorColor = MaterialTheme.colorScheme.primaryContainer
                    )
                )
                NavigationBarItem(
                    selected = selectedTab == AppTab.SETTINGS,
                    onClick = { selectedTab = AppTab.SETTINGS },
                    label = { Text("Settings", fontSize = 11.sp) },
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings"
                        )
                    },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        indicatorColor = MaterialTheme.colorScheme.primaryContainer
                    )
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (selectedTab) {
                AppTab.WORKFLOWS -> {
                    WorkflowsListScreen(
                        viewModel = viewModel,
                        hasPostNotificationsPermission = hasNotificationPermission,
                        onRequestPostNotificationsPermission = onRequestNotificationPermission,
                        hasSmsPermission = hasSmsPermission,
                        onRequestSmsPermission = onRequestSmsPermission,
                        onEditWorkflow = { wf -> activeEditingWorkflow = wf },
                        onViewLogs = { wf -> activeLogsWorkflow = wf }
                    )
                }
                AppTab.PRECONDITIONS -> {
                    PreconditionsScreen(
                        viewModel = viewModel,
                        onAddPrecondition = { showAddPreconditionScreen = true },
                        onEditPrecondition = { prec -> activeEditingPrecondition = prec }
                    )
                }
                AppTab.CREDENTIALS -> {
                    CredentialsScreen(
                        viewModel = viewModel
                    )
                }
                AppTab.SETTINGS -> {
                    SettingsScreen(
                        viewModel = viewModel,
                        hasSmsPermission = hasSmsPermission,
                        onRequestSmsPermission = onRequestSmsPermission,
                        hasNotificationPermission = hasNotificationPermission,
                        onRequestNotificationPermission = onRequestNotificationPermission
                    )
                }
            }

            if (showGuideSheet) {
                ProviderGuideBottomSheet(
                    onDismiss = { showGuideSheet = false }
                )
            }
        }
    }
}
