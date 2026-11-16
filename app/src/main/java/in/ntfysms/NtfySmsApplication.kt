package `in`.ntfysms

import android.app.Application
import `in`.ntfysms.data.EncryptedSettingsRepository
import `in`.ntfysms.service.SmsForwarderService

class NtfySmsApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        val settingsRepo = EncryptedSettingsRepository(this)
        val settings = settingsRepo.getSettings()

        if (settings.isEnabled) {
            SmsForwarderService.startService(this)
        }
    }
}
