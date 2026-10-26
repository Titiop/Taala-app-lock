package com.example.feature.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.core.data.datastore.AppSettingsDataStore
import com.example.feature.lock.TaalaForegroundService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return

        val dataStore = AppSettingsDataStore(context)
        CoroutineScope(Dispatchers.IO).launch {
            val engine = dataStore.activeEngine.first()
            if (engine.contains("Foreground") || engine.contains("Engine B")) {
                TaalaForegroundService.startService(context)
            }
        }
    }
}
