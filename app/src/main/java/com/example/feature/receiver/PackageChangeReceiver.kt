package com.example.feature.receiver

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.example.R
import com.example.core.data.database.TaalaDatabase
import com.example.core.data.datastore.AppSettingsDataStore
import com.example.core.data.model.LockedAppEntity
import com.example.feature.lock.TaalaForegroundService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class PackageChangeReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val packageName = intent?.data?.schemeSpecificPart ?: return

        val database = TaalaDatabase.getInstance(context)
        val dataStore = AppSettingsDataStore(context)

        when (intent.action) {
            Intent.ACTION_PACKAGE_FULLY_REMOVED, Intent.ACTION_PACKAGE_REMOVED -> {
                CoroutineScope(Dispatchers.IO).launch {
                    database.lockedAppDao().delete(packageName)
                }
            }
            Intent.ACTION_PACKAGE_ADDED -> {
                CoroutineScope(Dispatchers.IO).launch {
                    val autoLock = dataStore.lockNewAppsAuto.first()
                    val pm = context.packageManager
                    val appInfo = try {
                        pm.getApplicationInfo(packageName, 0)
                    } catch (_: Exception) {
                        null
                    }
                    val label = appInfo?.let { pm.getApplicationLabel(it).toString() } ?: packageName

                    if (autoLock) {
                        val newApp = LockedAppEntity(
                            packageName = packageName,
                            appLabel = label,
                            isLocked = true,
                            profileId = "default"
                        )
                        database.lockedAppDao().insertOrUpdate(newApp)

                        // Post notification alerting user
                        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                        val notification = NotificationCompat.Builder(context, TaalaForegroundService.CHANNEL_ID)
                            .setSmallIcon(R.drawable.ic_padlock_front)
                            .setContentTitle("New App Auto-Locked")
                            .setContentText("$label was automatically locked for protection.")
                            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                            .setAutoCancel(true)
                            .build()
                        notificationManager.notify(packageName.hashCode(), notification)
                    }
                }
            }
        }
    }
}
