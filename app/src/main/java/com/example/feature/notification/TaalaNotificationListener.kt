package com.example.feature.notification

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat
import com.example.R
import com.example.core.data.database.TaalaDatabase
import com.example.feature.lock.LockScreenActivity
import com.example.feature.lock.TaalaAccessibilityService
import com.example.feature.lock.TaalaForegroundService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class TaalaNotificationListener : NotificationListenerService() {

    private val scope = CoroutineScope(Dispatchers.IO)
    private lateinit var database: TaalaDatabase

    override fun onCreate() {
        super.onCreate()
        database = TaalaDatabase.getInstance(this)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return
        val pkg = sbn.packageName ?: return

        // Skip our own notifications
        if (pkg == packageName) return

        scope.launch {
            val lockedApp = database.lockedAppDao().getLockedAppSync(pkg)
            if (lockedApp != null && lockedApp.isLocked && lockedApp.isNotificationShieldEnabled) {
                // If the app is currently locked and session is not active, shield the notification
                val isUnlocked = TaalaAccessibilityService.sessionTracker.isAppSessionValid(pkg)
                if (!isUnlocked) {
                    try {
                        cancelNotification(sbn.key)
                    } catch (_: Exception) {}

                    postShieldedNotification(pkg, lockedApp.appLabel.ifBlank { pkg })
                }
            }
        }
    }

    private fun postShieldedNotification(targetPkg: String, targetLabel: String) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val unlockIntent = Intent(this, LockScreenActivity::class.java).apply {
            putExtra(LockScreenActivity.EXTRA_PACKAGE_NAME, targetPkg)
            putExtra(LockScreenActivity.EXTRA_APP_LABEL, targetLabel)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            targetPkg.hashCode(),
            unlockIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val shieldedNotification = NotificationCompat.Builder(this, TaalaForegroundService.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_padlock_front)
            .setContentTitle(targetLabel)
            .setContentText("Protected content hidden by Taala. Tap to unlock.")
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .build()

        notificationManager.notify("shield_$targetPkg".hashCode(), shieldedNotification)
    }
}
