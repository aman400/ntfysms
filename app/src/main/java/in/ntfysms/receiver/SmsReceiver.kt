package `in`.ntfysms.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import `in`.ntfysms.data.EncryptedSettingsRepository
import `in`.ntfysms.filter.SmsFilter
import `in`.ntfysms.worker.ForwardingManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            return
        }

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (messages.isNullOrEmpty()) return

        val sender = messages.firstOrNull()?.originatingAddress ?: "Unknown"
        val fullBody = messages.joinToString("") { it.displayMessageBody ?: it.messageBody ?: "" }

        if (sender.isBlank() || fullBody.isBlank()) return

        val settingsRepo = EncryptedSettingsRepository(context)
        val settings = settingsRepo.getSettings()

        if (!settings.isEnabled) return

        val shouldForward = SmsFilter.shouldForward(
            sender = sender,
            body = fullBody,
            allowlist = settings.allowlistItems,
            keywordRegexStr = settings.keywordRegex
        )

        if (shouldForward) {
            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val manager = ForwardingManager.getInstance(context)
                    manager.enqueueIncomingSms(sender, fullBody)
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }
}
