package dev.zophop

import android.app.Application
import dev.zophop.data.EncryptedSettingsRepository
import dev.zophop.service.SmsForwarderService

class ZopHopApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        val settingsRepo = EncryptedSettingsRepository(this)
        val settings = settingsRepo.getSettings()

        if (settings.isEnabled) {
            SmsForwarderService.startService(this)
        }
    }
}
