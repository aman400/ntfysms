package `in`.ntfysms.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import `in`.ntfysms.data.EncryptedSettingsRepository
import `in`.ntfysms.service.SmsForwarderService
import `in`.ntfysms.worker.ForwardingManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val settingsRepo = EncryptedSettingsRepository(context)
            val settings = settingsRepo.getSettings()

            if (settings.isEnabled) {
                SmsForwarderService.startService(context)

                val pendingResult = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val manager = ForwardingManager.getInstance(context)
                        manager.scheduleWorkManagerRetry()
                        manager.processPendingQueue()
                    } finally {
                        pendingResult.finish()
                    }
                }
            }
        }
    }
}
