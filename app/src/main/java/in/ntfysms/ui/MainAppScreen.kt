package `in`.ntfysms.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import `in`.ntfysms.data.PreconditionEntity
import `in`.ntfysms.domain.model.Workflow
import `in`.ntfysms.ui.credentials.CredentialsScreen
import `in`.ntfysms.ui.preconditions.PreconditionEditorScreen
import `in`.ntfysms.ui.preconditions.PreconditionsScreen
import `in`.ntfysms.ui.workflows.TestRunLogBottomSheet
import `in`.ntfysms.ui.workflows.WorkflowEditorScreen
import `in`.ntfysms.ui.workflows.WorkflowLogsScreen
import `in`.ntfysms.ui.workflows.WorkflowsListScreen

enum class AppTab {
    WORKFLOWS,
    PRECONDITIONS,
    CREDENTIALS,
    SETTINGS
}

sealed class MainScreenDestination {
    object MainTabs : MainScreenDestination()
    data class EditWorkflow(val workflow: Workflow) : MainScreenDestination()
    data class ViewLogs(val workflow: Workflow) : MainScreenDestination()
    data class EditPrecondition(val precondition: PreconditionEntity?) : MainScreenDestination()
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

    val uiState by viewModel.uiState.collectAsState()
    val workflows by viewModel.workflows.collectAsState()
    val credentials by viewModel.credentials.collectAsState()
    val preconditions by viewModel.preconditions.collectAsState()
    val activeExecutionState by viewModel.activeExecutionState.collectAsState()
    var showActiveExecutionSheet by remember { mutableStateOf(false) }

    val currentDestination: MainScreenDestination = remember(
        activeEditingWorkflow,
        activeLogsWorkflow,
        showAddPreconditionScreen,
        activeEditingPrecondition
    ) {
        when {
            activeEditingWorkflow != null -> MainScreenDestination.EditWorkflow(activeEditingWorkflow!!)
            activeLogsWorkflow != null -> MainScreenDestination.ViewLogs(activeLogsWorkflow!!)
            showAddPreconditionScreen || activeEditingPrecondition != null -> MainScreenDestination.EditPrecondition(activeEditingPrecondition)
            else -> MainScreenDestination.MainTabs
        }
    }

    // Only intercept back navigation at root when on a secondary tab
    BackHandler(enabled = currentDestination is MainScreenDestination.MainTabs && selectedTab != AppTab.WORKFLOWS) {
        selectedTab = AppTab.WORKFLOWS
    }

    AnimatedContent(
        targetState = currentDestination,
        transitionSpec = {
            if (targetState is MainScreenDestination.MainTabs) {
                // Popping back to tabs: Slide out to right
                (fadeIn(animationSpec = tween(220)) + slideInHorizontally(animationSpec = tween(220)) { -it / 3 }) togetherWith
                (fadeOut(animationSpec = tween(180)) + slideOutHorizontally(animationSpec = tween(180)) { it })
            } else {
                // Pushing new screen: Slide in from right
                (fadeIn(animationSpec = tween(220)) + slideInHorizontally(animationSpec = tween(220)) { it }) togetherWith
                (fadeOut(animationSpec = tween(180)) + slideOutHorizontally(animationSpec = tween(180)) { -it / 3 })
            }
        },
        label = "ScreenDestinationTransition"
    ) { destination ->
        when (destination) {
            is MainScreenDestination.EditWorkflow -> {
                WorkflowEditorScreen(
                    workflow = destination.workflow,
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
                    onCancelWorkflowExecution = { id ->
                        viewModel.cancelWorkflowExecution(id)
                    },
                    onSaveCredential = { newCred ->
                        viewModel.saveCredential(newCred)
                    },
                    onSavePrecondition = { newPrec ->
                        viewModel.savePrecondition(newPrec)
                    }
                )
            }
            is MainScreenDestination.ViewLogs -> {
                WorkflowLogsScreen(
                    workflow = destination.workflow,
                    logsFlow = viewModel.getLogsForWorkflow(destination.workflow.id),
                    onClearLogs = { viewModel.clearLogsForWorkflow(destination.workflow.id) },
                    onRetryWorkflowFromFailedNode = { wf, failedNodeId, onProgress, onResult ->
                        viewModel.retryWorkflowFromFailedNode(wf, failedNodeId, onProgress, onResult)
                    },
                    onBack = { activeLogsWorkflow = null }
                )
            }
            is MainScreenDestination.EditPrecondition -> {
                PreconditionEditorScreen(
                    precondition = destination.precondition,
                    credentials = credentials,
                    onTestPreconditionWithProgress = { entity, onProgress, onResult ->
                        viewModel.testPreconditionWithProgress(entity, onProgress, onResult)
                    },
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
            }
            is MainScreenDestination.MainTabs -> {
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
                        AnimatedContent(
                            targetState = selectedTab,
                            transitionSpec = {
                                val isForward = targetState.ordinal > initialState.ordinal
                                (fadeIn(animationSpec = tween(220)) + slideInHorizontally(animationSpec = tween(220)) { width -> if (isForward) width / 4 else -width / 4 }) togetherWith
                                (fadeOut(animationSpec = tween(180)) + slideOutHorizontally(animationSpec = tween(180)) { width -> if (isForward) -width / 4 else width / 4 })
                            },
                            label = "TabAnimation"
                        ) { targetTab ->
                            when (targetTab) {
                                AppTab.WORKFLOWS -> {
                                    WorkflowsListScreen(
                                        workflows = workflows,
                                        hasNotificationListenerPermission = uiState.hasNotificationListenerPermission,
                                        hasPostNotificationsPermission = hasNotificationPermission,
                                        hasSmsPermission = hasSmsPermission,
                                        onRequestSmsPermission = onRequestSmsPermission,
                                        onRequestPostNotificationsPermission = onRequestNotificationPermission,
                                        onToggleWorkflow = { id, enabled ->
                                            viewModel.setWorkflowEnabled(id, enabled)
                                        },
                                        onRunManualWorkflow = { wf ->
                                            viewModel.testRunWorkflow(wf) { _ -> }
                                        },
                                        onSaveWorkflow = { wf ->
                                            viewModel.saveWorkflow(wf)
                                        },
                                        onDeleteWorkflow = { id ->
                                            viewModel.deleteWorkflow(id)
                                        },
                                        onEditWorkflow = { wf -> activeEditingWorkflow = wf },
                                        onViewLogs = { wf -> activeLogsWorkflow = wf },
                                        onGetLogsForWorkflowFlow = { id -> viewModel.getLogsForWorkflow(id) },
                                        onClearLogsForWorkflow = { id -> viewModel.clearLogsForWorkflow(id) },
                                        onRetryWorkflowFromFailedNode = { wf, failedNodeId, onProgress, onResult ->
                                            viewModel.retryWorkflowFromFailedNode(wf, failedNodeId, onProgress, onResult)
                                        }
                                    )
                                }
                                AppTab.PRECONDITIONS -> {
                                    PreconditionsScreen(
                                        preconditions = preconditions,
                                        credentials = credentials,
                                        workflows = workflows,
                                        onSavePrecondition = { prec -> viewModel.savePrecondition(prec) },
                                        onDeletePrecondition = { id -> viewModel.deletePrecondition(id) },
                                        onTestPreconditionWithProgress = { entity, onProgress, onResult ->
                                            viewModel.testPreconditionWithProgress(entity, onProgress, onResult)
                                        },
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
                        }

                        // Floating / Sticky Execution Banner across screen tabs
                        activeExecutionState?.let { activeState ->
                            if (activeState.isExecuting) {
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .align(Alignment.TopCenter)
                                        .padding(horizontal = 16.dp, vertical = 8.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    shape = RoundedCornerShape(12.dp),
                                    tonalElevation = 6.dp
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(16.dp),
                                                strokeWidth = 2.dp,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer
                                            )
                                            Text(
                                                text = "⚡ Executing: ${activeState.workflowName}",
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer
                                            )
                                        }

                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            TextButton(
                                                onClick = { showActiveExecutionSheet = true }
                                            ) {
                                                Text("View Stepper", fontSize = 11.sp)
                                            }
                                            TextButton(
                                                onClick = { viewModel.cancelWorkflowExecution(activeState.workflowId) }
                                            ) {
                                                Text("Cancel", fontSize = 11.sp, color = MaterialTheme.colorScheme.error)
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        if (showActiveExecutionSheet && activeExecutionState != null) {
                            TestRunLogBottomSheet(
                                workflowName = activeExecutionState!!.workflowName,
                                steps = activeExecutionState!!.steps,
                                isTesting = activeExecutionState!!.isExecuting,
                                onCancelExecution = { viewModel.cancelWorkflowExecution(activeExecutionState!!.workflowId) },
                                onDismiss = { showActiveExecutionSheet = false }
                            )
                        }
                    }
                }
            }
        }
    }
}
