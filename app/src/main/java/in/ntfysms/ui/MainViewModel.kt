package `in`.ntfysms.ui

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import `in`.ntfysms.data.AppDatabase
import `in`.ntfysms.data.AppSettings
import `in`.ntfysms.data.CredentialRepository
import `in`.ntfysms.data.DefaultWorkflowInitializer
import `in`.ntfysms.data.DeliveryNode
import `in`.ntfysms.data.EncryptedSettingsRepository
import `in`.ntfysms.data.MessageEntity
import `in`.ntfysms.data.PreconditionEntity
import `in`.ntfysms.data.PreconditionRepository
import `in`.ntfysms.data.RoomCredentialRepository
import `in`.ntfysms.data.RoomPreconditionRepository
import `in`.ntfysms.data.RoomWorkflowRepository
import `in`.ntfysms.data.toDomain
import `in`.ntfysms.data.WorkflowExecutionLogEntity
import `in`.ntfysms.data.WorkflowRepository
import `in`.ntfysms.domain.engine.ExecutionContext
import `in`.ntfysms.domain.engine.ExecutionJobRegistry
import `in`.ntfysms.domain.engine.NodeExecutionStep
import `in`.ntfysms.domain.engine.PreconditionEvaluator
import org.json.JSONObject
import `in`.ntfysms.domain.engine.WorkflowEngine
import `in`.ntfysms.domain.engine.WorkflowExecutionResult
import `in`.ntfysms.domain.model.Credential
import `in`.ntfysms.domain.model.NodeType
import `in`.ntfysms.domain.model.Workflow
import `in`.ntfysms.filter.SmsFilter
import `in`.ntfysms.service.SmsForwarderService
import `in`.ntfysms.worker.ForwardingManager
import `in`.ntfysms.domain.engine.PreconditionResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

data class ActiveExecutionState(
    val workflowId: String,
    val workflowName: String,
    val steps: List<NodeExecutionStep>,
    val isExecuting: Boolean,
    val result: WorkflowExecutionResult? = null
)

data class MainUiState(
    val nodes: List<DeliveryNode> = emptyList(),
    val allowlist: String = ".*",
    val keywordRegex: String = ".*",
    val senderRegex: String = ".*",
    val bodyParseRegex: String = "\\b\\d{4,8}\\b",
    val customMessageTemplate: String = "\${body}",
    val expiryMinutes: String = "10",
    val isEnabled: Boolean = false,
    val allowInsecureHttp: Boolean = false,
    val forwardNotifications: Boolean = false,
    val showWorkflowNotifications: Boolean = true,
    val maxParallelExecutions: Int = 3,
    val maxExecutionQueueSize: Int = 20,
    val autoClearEnabled: Boolean = false,
    val autoClearMinutes: String = "1440",
    val isRegexValid: Boolean = true,
    val isUrlValid: Boolean = true,
    val isSendingTest: Boolean = false,
    val retryingMessageId: String? = null,
    val testResultMessage: String? = null,
    val isBatteryOptimized: Boolean = true,
    val hasNotificationListenerPermission: Boolean = false
) {
    val activeNodes: List<DeliveryNode>
        get() = nodes.filter { it.isEnabled && it.serverUrl.isNotBlank() }
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val settingsRepo = EncryptedSettingsRepository(application)
    private val db = AppDatabase.getInstance(application)
    private val forwardingManager = ForwardingManager.getInstance(application)
    val workflowRepository: WorkflowRepository = RoomWorkflowRepository(db.workflowDao())
    val credentialRepository: CredentialRepository = RoomCredentialRepository(db.credentialDao())
    val preconditionRepository: PreconditionRepository = RoomPreconditionRepository(db.preconditionDao())
    private val workflowInitializer = DefaultWorkflowInitializer(workflowRepository)

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private val _activeExecutionState = MutableStateFlow<ActiveExecutionState?>(null)
    val activeExecutionState: StateFlow<ActiveExecutionState?> = _activeExecutionState.asStateFlow()

    val workflows: StateFlow<List<Workflow>> = workflowRepository.getAllWorkflowsFlow()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val credentials: StateFlow<List<Credential>> = credentialRepository.getAllCredentialsFlow()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val preconditions: StateFlow<List<PreconditionEntity>> = preconditionRepository.getAllPreconditionsFlow()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val executionLogs: StateFlow<List<WorkflowExecutionLogEntity>> = workflowRepository.getRecentExecutionLogsFlow(50)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val recentEvents: StateFlow<List<MessageEntity>> = db.messageDao()
        .getRecentEventsFlow(20)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val allEvents: StateFlow<List<MessageEntity>> = db.messageDao()
        .getAllEventsFlow()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    init {
        loadSettings()
        checkPermissionsAndStatus()
        viewModelScope.launch(Dispatchers.IO) {
            workflowInitializer.checkAndInitialize()
        }
    }

    fun loadSettings() {
        val s = settingsRepo.getSettings()
        _uiState.value = _uiState.value.copy(
            nodes = s.nodes,
            allowlist = s.allowlist,
            keywordRegex = s.keywordRegex,
            senderRegex = s.senderRegex,
            bodyParseRegex = s.bodyParseRegex,
            customMessageTemplate = s.customMessageTemplate,
            expiryMinutes = s.expiryMinutes.toString(),
            isEnabled = s.isEnabled,
            allowInsecureHttp = s.allowInsecureHttp,
            forwardNotifications = s.forwardNotifications,
            showWorkflowNotifications = s.showWorkflowNotifications,
            maxParallelExecutions = s.maxParallelExecutions,
            maxExecutionQueueSize = s.maxExecutionQueueSize,
            autoClearEnabled = s.autoClearEnabled,
            autoClearMinutes = s.autoClearMinutes.toString(),
            isRegexValid = SmsFilter.isValidRegex(s.keywordRegex),
            isUrlValid = true,
            isBatteryOptimized = checkBatteryOptimizedInternal(),
            hasNotificationListenerPermission = checkNotificationListenerPermissionInternal()
        )
    }

    fun addNode(node: DeliveryNode) {
        val updated = _uiState.value.nodes + node
        _uiState.value = _uiState.value.copy(nodes = updated)
        saveCurrentSettings()
    }

    fun updateNode(node: DeliveryNode) {
        val updated = _uiState.value.nodes.map { if (it.id == node.id) node else it }
        _uiState.value = _uiState.value.copy(nodes = updated)
        saveCurrentSettings()
    }

    fun deleteNode(nodeId: String) {
        val updated = _uiState.value.nodes.filterNot { it.id == nodeId }
        _uiState.value = _uiState.value.copy(nodes = updated)
        saveCurrentSettings()
    }

    fun toggleNode(nodeId: String, enabled: Boolean) {
        val updated = _uiState.value.nodes.map { if (it.id == nodeId) it.copy(isEnabled = enabled) else it }
        _uiState.value = _uiState.value.copy(nodes = updated)
        saveCurrentSettings()
    }

    fun updateAllowlist(allowlist: String) {
        _uiState.value = _uiState.value.copy(allowlist = allowlist)
        saveCurrentSettings()
    }

    fun updateKeywordRegex(regex: String) {
        val isValid = SmsFilter.isValidRegex(regex)
        _uiState.value = _uiState.value.copy(
            keywordRegex = regex,
            isRegexValid = isValid
        )
        if (isValid) {
            saveCurrentSettings()
        }
    }

    fun updateSenderRegex(regex: String) {
        _uiState.value = _uiState.value.copy(senderRegex = regex)
        saveCurrentSettings()
    }

    fun updateBodyParseRegex(regex: String) {
        _uiState.value = _uiState.value.copy(bodyParseRegex = regex)
        saveCurrentSettings()
    }

    fun updateCustomMessageTemplate(template: String) {
        _uiState.value = _uiState.value.copy(customMessageTemplate = template)
        saveCurrentSettings()
    }

    fun updateExpiryMinutes(expiry: String) {
        _uiState.value = _uiState.value.copy(expiryMinutes = expiry)
        saveCurrentSettings()
    }

    fun toggleAutoClear(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(autoClearEnabled = enabled)
        saveCurrentSettings()
    }

    fun updateAutoClearMinutes(minutes: String) {
        _uiState.value = _uiState.value.copy(autoClearMinutes = minutes)
        saveCurrentSettings()
    }

    fun toggleAllowInsecureHttp(allow: Boolean) {
        _uiState.value = _uiState.value.copy(allowInsecureHttp = allow)
        saveCurrentSettings()
    }

    fun toggleForwardNotifications(enable: Boolean) {
        _uiState.value = _uiState.value.copy(
            forwardNotifications = enable,
            hasNotificationListenerPermission = checkNotificationListenerPermissionInternal()
        )
        saveCurrentSettings()
    }

    fun toggleShowWorkflowNotifications(enable: Boolean) {
        _uiState.value = _uiState.value.copy(showWorkflowNotifications = enable)
        saveCurrentSettings()
    }

    fun updateMaxParallelExecutions(count: Int) {
        _uiState.value = _uiState.value.copy(maxParallelExecutions = count.coerceIn(1, 10))
        saveCurrentSettings()
    }

    fun updateMaxExecutionQueueSize(size: Int) {
        _uiState.value = _uiState.value.copy(maxExecutionQueueSize = size.coerceIn(5, 100))
        saveCurrentSettings()
    }

    fun toggleMasterSwitch(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(isEnabled = enabled)
        saveCurrentSettings()
        val context = getApplication<Application>()
        if (enabled) {
            SmsForwarderService.startService(context)
        } else {
            SmsForwarderService.stopService(context)
        }
    }

    fun sendTestMessage() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSendingTest = true, testResultMessage = null)
            val result = forwardingManager.sendTestMessage()
            _uiState.value = _uiState.value.copy(
                isSendingTest = false,
                testResultMessage = if (result.isSuccess) "Test ping sent to active delivery nodes!" else "Failed: ${result.exceptionOrNull()?.message}"
            )
        }
    }

    fun retryMessage(messageId: String, onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(retryingMessageId = messageId)
            val result = forwardingManager.retryMessage(messageId)
            _uiState.value = _uiState.value.copy(retryingMessageId = null)
            if (result.isSuccess) {
                onResult(true, "Message retried successfully!")
            } else {
                onResult(false, result.exceptionOrNull()?.message ?: "Retry failed")
            }
        }
    }

    fun clearAllEvents() {
        viewModelScope.launch {
            forwardingManager.clearAllEvents()
        }
    }

    fun saveWorkflow(workflow: Workflow) {
        viewModelScope.launch(Dispatchers.IO) {
            workflowRepository.saveWorkflow(workflow)
        }
    }

    fun setWorkflowEnabled(id: String, enabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            val workflow = workflowRepository.getWorkflowById(id)
            if (workflow != null && enabled) {
                val hasNotifTrigger = workflow.nodes.any { it.type == NodeType.NOTIFICATION_TRIGGER }
                val hasNotifPerm = checkNotificationListenerPermissionInternal()
                if (hasNotifTrigger && !hasNotifPerm) {
                    workflowRepository.setWorkflowEnabled(id, false)
                    return@launch
                }
            }
            workflowRepository.setWorkflowEnabled(id, enabled)
        }
    }

    fun deleteWorkflow(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            workflowRepository.deleteWorkflow(id)
        }
    }

    fun getLogsForWorkflow(workflowId: String): Flow<List<WorkflowExecutionLogEntity>> {
        return workflowRepository.getLogsForWorkflowFlow(workflowId)
    }

    fun clearLogsForWorkflow(workflowId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            workflowRepository.clearLogsForWorkflow(workflowId)
        }
    }

    fun cancelWorkflowExecution(workflowId: String) {
        ExecutionJobRegistry.cancelJob(workflowId)
    }

    fun clearActiveExecutionState() {
        _activeExecutionState.value = null
    }

    fun saveCredential(credential: Credential) {
        viewModelScope.launch(Dispatchers.IO) {
            credentialRepository.saveCredential(credential)
        }
    }

    fun deleteCredential(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            credentialRepository.deleteCredential(id)
        }
    }

    fun savePrecondition(precondition: PreconditionEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            preconditionRepository.savePrecondition(precondition)
        }
    }

    fun deletePrecondition(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            preconditionRepository.deletePrecondition(id)
        }
    }

    fun testPreconditionWithProgress(
        entity: PreconditionEntity,
        onStepUpdate: (List<NodeExecutionStep>) -> Unit,
        onResult: (PreconditionResult) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val context = getApplication<Application>()
            val evaluator = PreconditionEvaluator(context)
            val domainPrecondition = entity.toDomain()
            val result = evaluator.evaluate(domainPrecondition) { steps ->
                viewModelScope.launch(Dispatchers.Main) {
                    onStepUpdate(steps)
                }
            }
            withContext(Dispatchers.Main) {
                onResult(result)
            }
        }
    }

    fun testPrecondition(entity: PreconditionEntity, onResult: (PreconditionResult) -> Unit) {
        testPreconditionWithProgress(entity, {}, onResult)
    }

    fun testRunWorkflowWithProgress(
        workflow: Workflow,
        onProgressUpdate: (List<NodeExecutionStep>) -> Unit,
        onResult: (WorkflowExecutionResult) -> Unit
    ) {
        retryWorkflowFromFailedNode(workflow, null, onProgressUpdate, onResult)
    }

    fun retryWorkflowFromFailedNode(
        workflow: Workflow,
        failedNodeId: String?,
        onProgressUpdate: (List<NodeExecutionStep>) -> Unit,
        onResult: (WorkflowExecutionResult) -> Unit
    ) {
        val job = viewModelScope.launch(Dispatchers.IO) {
            _activeExecutionState.value = ActiveExecutionState(
                workflowId = workflow.id,
                workflowName = workflow.name,
                steps = emptyList(),
                isExecuting = true
            )

            val context = getApplication<Application>()
            val evaluator = PreconditionEvaluator(context)
            val engine = WorkflowEngine(evaluator, credentialRepository)

            val triggerNode = workflow.nodes.firstOrNull {
                it.type == NodeType.SMS_TRIGGER || it.type == NodeType.NOTIFICATION_TRIGGER || it.type == NodeType.MANUAL_TRIGGER
            }
            val triggerConfig = try { JSONObject(triggerNode?.configJson ?: "{}") } catch (_: Exception) { JSONObject() }

            val testContextMap = mutableMapOf<String, Any>()
            when (triggerNode?.type) {
                NodeType.SMS_TRIGGER -> {
                    testContextMap["sender"] = triggerConfig.optString("testSender", "+15551234567")
                    testContextMap["body"] = triggerConfig.optString("testBody", "Your OTP verification code for bank login is 849201")
                }
                NodeType.NOTIFICATION_TRIGGER -> {
                    testContextMap["package_name"] = triggerConfig.optString("testPackageName", "com.example.bankapp")
                    testContextMap["title"] = triggerConfig.optString("testTitle", "Bank Alert: OTP Code")
                    testContextMap["text"] = triggerConfig.optString("testText", "Your login code is 849201")
                    testContextMap["body"] = triggerConfig.optString("testText", "Your login code is 849201")
                }
                NodeType.MANUAL_TRIGGER -> {
                    testContextMap["sender"] = "ManualTrigger"
                    testContextMap["body"] = triggerConfig.optString("testBody", "Test manual payload data 849201")
                }
                else -> {
                    testContextMap["sender"] = "+15551234567"
                    testContextMap["body"] = "Your OTP verification code for bank login is 849201"
                }
            }
            testContextMap["received_at"] = System.currentTimeMillis()
            testContextMap["show_workflow_notifications"] = _uiState.value.showWorkflowNotifications

            val execContext = ExecutionContext(testContextMap)

            val result = engine.executeWorkflowWithProgress(
                workflow = workflow,
                context = execContext,
                appContext = context,
                startNodeId = failedNodeId,
                onStepUpdate = { steps ->
                    _activeExecutionState.value = ActiveExecutionState(
                        workflowId = workflow.id,
                        workflowName = workflow.name,
                        steps = steps,
                        isExecuting = true
                    )
                    viewModelScope.launch(Dispatchers.Main) {
                        onProgressUpdate(steps)
                    }
                }
            )

            val currentSteps = _activeExecutionState.value?.steps ?: emptyList()
            _activeExecutionState.value = ActiveExecutionState(
                workflowId = workflow.id,
                workflowName = workflow.name,
                steps = currentSteps,
                isExecuting = false,
                result = result
            )

            val logMsg = when (result) {
                is WorkflowExecutionResult.Success -> "✅ Retry Success!\n" + result.logs.joinToString("\n")
                is WorkflowExecutionResult.PreconditionFailed -> "⏳ Retry Precondition Failed: ${result.reason}\n" + result.logs.joinToString("\n")
                is WorkflowExecutionResult.Failed -> "❌ Retry Failed: ${result.reason}\n" + result.logs.joinToString("\n")
                is WorkflowExecutionResult.Cancelled -> "🛑 Cancelled: Workflow manually cancelled by user\n" + result.logs.joinToString("\n")
            }

            val logEntity = WorkflowExecutionLogEntity(
                id = UUID.randomUUID().toString(),
                workflowId = workflow.id,
                status = when (result) {
                    is WorkflowExecutionResult.Success -> "SUCCESS"
                    is WorkflowExecutionResult.PreconditionFailed -> "PRECONDITION_FAILED"
                    is WorkflowExecutionResult.Failed -> "FAILED"
                    is WorkflowExecutionResult.Cancelled -> "CANCELLED"
                },
                timestamp = System.currentTimeMillis(),
                messageId = null,
                logs = logMsg,
                errorReason = when (result) {
                    is WorkflowExecutionResult.Success -> null
                    is WorkflowExecutionResult.PreconditionFailed -> result.reason
                    is WorkflowExecutionResult.Failed -> result.reason
                    is WorkflowExecutionResult.Cancelled -> "Manually cancelled by user"
                }
            )
            workflowRepository.saveExecutionLog(logEntity)

            withContext(Dispatchers.Main) {
                onResult(result)
            }
        }

        ExecutionJobRegistry.registerJob(workflow.id, job)
    }

    fun testRunWorkflow(workflow: Workflow, onResult: (String) -> Unit) {
        testRunWorkflowWithProgress(
            workflow = workflow,
            onProgressUpdate = {},
            onResult = { result ->
                val logMsg = when (result) {
                    is WorkflowExecutionResult.Success -> "✅ Success!\n" + result.logs.joinToString("\n")
                    is WorkflowExecutionResult.PreconditionFailed -> "⏳ Precondition Failed: ${result.reason}\n" + result.logs.joinToString("\n")
                    is WorkflowExecutionResult.Failed -> "❌ Failed: ${result.reason}\n" + result.logs.joinToString("\n")
                    is WorkflowExecutionResult.Cancelled -> "🛑 Cancelled: Workflow manually cancelled by user\n" + result.logs.joinToString("\n")
                }
                onResult(logMsg)
            }
        )
    }

    fun checkPermissionsAndStatus() {
        val context = getApplication<Application>()
        val hasNotifPerm = checkNotificationListenerPermissionInternal()
        _uiState.value = _uiState.value.copy(
            isBatteryOptimized = checkBatteryOptimizedInternal(),
            hasNotificationListenerPermission = hasNotifPerm
        )
        viewModelScope.launch(Dispatchers.IO) {
            val currentWorkflows = workflowRepository.getActiveWorkflows()
            currentWorkflows.forEach { wf ->
                val notifTrigger = wf.nodes.find { it.type == NodeType.NOTIFICATION_TRIGGER }
                if (notifTrigger != null && wf.isEnabled) {
                    val config = try { JSONObject(notifTrigger.configJson) } catch (_: Exception) { JSONObject() }
                    val pkgFilter = config.optString("packageFilter", ".*")
                    val isInstalled = `in`.ntfysms.ui.workflows.AppListHelper.isAppInstalled(context, pkgFilter)

                    if (!hasNotifPerm || !isInstalled) {
                        workflowRepository.setWorkflowEnabled(wf.id, false)
                        if (!isInstalled && pkgFilter != ".*" && !pkgFilter.contains("*")) {
                            postUninstalledAppNotification(context, wf.name, pkgFilter)
                        }
                    }
                }
            }
        }
    }

    private fun postUninstalledAppNotification(context: Context, workflowName: String, packageName: String) {
        try {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channelId = "ntfy_sms_system_alerts"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(channelId, "System & Security Alerts", NotificationManager.IMPORTANCE_HIGH)
                nm.createNotificationChannel(channel)
            }

            val appLabel = `in`.ntfysms.ui.workflows.AppListHelper.getAppNameForPackage(context, packageName)
            val notification = androidx.core.app.NotificationCompat.Builder(context, channelId)
                .setSmallIcon(`in`.ntfysms.R.drawable.ic_notification)
                .setContentTitle("⚠️ Workflow Disabled: App Uninstalled")
                .setContentText("Target app '$appLabel' is uninstalled. Workflow '$workflowName' has been disabled.")
                .setStyle(androidx.core.app.NotificationCompat.BigTextStyle().bigText("Target app '$appLabel' ($packageName) is not installed on this device. Workflow '$workflowName' was automatically disabled to prevent errors."))
                .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .build()

            nm.notify(workflowName.hashCode(), notification)
        } catch (_: Exception) {}
    }

    private fun checkBatteryOptimizedInternal(): Boolean {
        val context = getApplication<Application>()
        val powerManager = context.getSystemService(PowerManager::class.java)
        return powerManager?.isIgnoringBatteryOptimizations(context.packageName) == false
    }

    private fun checkNotificationListenerPermissionInternal(): Boolean {
        val context = getApplication<Application>()
        val packageName = context.packageName
        val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
        return flat != null && (flat.contains(packageName))
    }

    private fun saveCurrentSettings() {
        val current = _uiState.value
        val settings = AppSettings(
            nodes = current.nodes,
            allowlist = current.allowlist,
            keywordRegex = current.keywordRegex,
            senderRegex = current.senderRegex,
            bodyParseRegex = current.bodyParseRegex,
            customMessageTemplate = current.customMessageTemplate,
            expiryMinutes = current.expiryMinutes.toIntOrNull() ?: 10,
            maxRetries = 5,
            isEnabled = current.isEnabled,
            allowInsecureHttp = current.allowInsecureHttp,
            forwardNotifications = current.forwardNotifications,
            showWorkflowNotifications = current.showWorkflowNotifications,
            maxParallelExecutions = current.maxParallelExecutions,
            maxExecutionQueueSize = current.maxExecutionQueueSize,
            autoClearEnabled = current.autoClearEnabled,
            autoClearMinutes = current.autoClearMinutes.toIntOrNull() ?: 1440
        )
        settingsRepo.saveSettings(settings)
    }
}
