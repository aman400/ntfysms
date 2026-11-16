package `in`.ntfysms.service

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import `in`.ntfysms.data.EncryptedSettingsRepository
import `in`.ntfysms.filter.SmsFilter
import `in`.ntfysms.worker.ForwardingManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class NtfyNotificationListenerService : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return

        // Skip notifications from NtfySMS itself
        if (sbn.packageName == packageName) return

        val settingsRepo = EncryptedSettingsRepository(applicationContext)
        val settings = settingsRepo.getSettings()

        if (!settings.isEnabled || !settings.forwardNotifications) return

        val extras = sbn.notification?.extras ?: return

        val rawTitle = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
            ?: extras.getCharSequence(Notification.EXTRA_TITLE_BIG)?.toString()

        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
            ?: extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
            ?: return

        if (text.isBlank()) return

        val sender = if (!rawTitle.isNullOrBlank()) rawTitle else sbn.packageName

        val shouldForward = SmsFilter.shouldForward(
            sender = sender,
            body = text,
            allowlist = settings.allowlistItems,
            keywordRegexStr = settings.keywordRegex
        )

        if (shouldForward) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val manager = ForwardingManager.getInstance(applicationContext)
                    manager.enqueueIncomingSms(sender = sender, body = text)
                } catch (e: Exception) {
                    // Ignore service exceptions
                }
            }
        }
    }
}
