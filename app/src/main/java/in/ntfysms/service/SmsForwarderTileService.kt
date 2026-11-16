package `in`.ntfysms.service

import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.annotation.RequiresApi
import `in`.ntfysms.data.EncryptedSettingsRepository

@RequiresApi(Build.VERSION_CODES.N)
class SmsForwarderTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        updateTileState()
    }

    override fun onClick() {
        super.onClick()
        val repo = EncryptedSettingsRepository(applicationContext)
        val current = repo.getSettings()
        val newState = !current.isEnabled
        repo.setEnabled(newState)

        if (newState) {
            SmsForwarderService.startService(applicationContext)
        } else {
            SmsForwarderService.stopService(applicationContext)
        }

        updateTileState()
    }

    private fun updateTileState() {
        val tile = qsTile ?: return
        val repo = EncryptedSettingsRepository(applicationContext)
        val isEnabled = repo.getSettings().isEnabled

        tile.state = if (isEnabled) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = "NtfySMS"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = if (isEnabled) "Active" else "Paused"
        }
        tile.updateTile()
    }
}
