package `in`.ntfysms.ui

import android.app.Application
import android.os.PowerManager
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import `in`.ntfysms.data.AppDatabase
import `in`.ntfysms.data.AppSettings
import `in`.ntfysms.data.DeliveryNode
import `in`.ntfysms.data.EncryptedSettingsRepository
import `in`.ntfysms.data.MessageEntity
import `in`.ntfysms.filter.SmsFilter
import `in`.ntfysms.service.SmsForwarderService
import `in`.ntfysms.worker.ForwardingManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

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

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

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

    fun checkPermissionsAndStatus() {
        _uiState.value = _uiState.value.copy(
            isBatteryOptimized = checkBatteryOptimizedInternal(),
            hasNotificationListenerPermission = checkNotificationListenerPermissionInternal()
        )
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
            autoClearEnabled = current.autoClearEnabled,
            autoClearMinutes = current.autoClearMinutes.toIntOrNull() ?: 1440
        )
        settingsRepo.saveSettings(settings)
    }
}
