package com.example.feature.lock

import android.graphics.drawable.Icon
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.example.R
import com.example.core.data.datastore.AppSettingsDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class TaalaTileService : TileService() {

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private lateinit var dataStore: AppSettingsDataStore

    override fun onCreate() {
        super.onCreate()
        dataStore = AppSettingsDataStore(this)
    }

    override fun onStartListening() {
        super.onStartListening()
        updateTileState()
    }

    override fun onClick() {
        super.onClick()
        serviceScope.launch {
            val isPaused = dataStore.isProtectionPaused.first()
            if (isPaused) {
                // Resume protection
                dataStore.setProtectionPaused(false)
            } else {
                // Pause protection for 15 minutes
                dataStore.setProtectionPaused(true, 15 * 60 * 1000L)
            }
            updateTileState()
        }
    }

    private fun updateTileState() {
        val tile = qsTile ?: return
        serviceScope.launch {
            val isPaused = dataStore.isProtectionPaused.first()
            if (isPaused) {
                tile.state = Tile.STATE_INACTIVE
                tile.label = getString(R.string.app_name)
                tile.subtitle = "Paused (15m)"
                tile.icon = Icon.createWithResource(this@TaalaTileService, R.drawable.ic_padlock_front)
            } else {
                tile.state = Tile.STATE_ACTIVE
                tile.label = getString(R.string.app_name)
                tile.subtitle = "Protected"
                tile.icon = Icon.createWithResource(this@TaalaTileService, R.drawable.ic_padlock_front)
            }
            tile.updateTile()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }
}
