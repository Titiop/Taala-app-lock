package com.example.feature.lock

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.core.data.database.TaalaDatabase
import com.example.core.data.datastore.AppSettingsDataStore
import com.example.core.data.repository.AppRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class TaalaForegroundService : Service() {

    companion object {
        const val CHANNEL_ID = "taala_protection_channel"
        const val NOTIFICATION_ID = 1001

        const val ACTION_START = "action_start_engine_b"
        const val ACTION_STOP = "action_stop_engine_b"
        const val ACTION_PAUSE_15M = "action_pause_15m"
        const val ACTION_RESUME = "action_resume"

        fun startService(context: Context) {
            val intent = Intent(context, TaalaForegroundService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, TaalaForegroundService::class.java).apply {
                action = ACTION_STOP
            }
            context.stopService(intent)
        }
    }

    private val serviceScope = CoroutineScope(Dispatchers.Default + Job())
    private var pollingJob: Job? = null

    private lateinit var usageStatsManager: UsageStatsManager
    private lateinit var database: TaalaDatabase
    private lateinit var appRepository: AppRepository
    private lateinit var dataStore: AppSettingsDataStore
    private lateinit var foregroundRules: ForegroundRules

    private var currentForegroundPackage: String? = null
    private var screenReceiver: BroadcastReceiver? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        database = TaalaDatabase.getInstance(this)
        appRepository = AppRepository(this, database.lockedAppDao())
        dataStore = AppSettingsDataStore(this)
        foregroundRules = ForegroundRules(this)

        registerScreenOffReceiver()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PAUSE_15M -> {
                serviceScope.launch {
                    dataStore.setProtectionPaused(true, 15 * 60 * 1000L)
                    updateNotification(isPaused = true)
                }
            }
            ACTION_RESUME -> {
                serviceScope.launch {
                    dataStore.setProtectionPaused(false)
                    updateNotification(isPaused = false)
                }
            }
            ACTION_STOP -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            else -> {
                val notification = buildNotification(isPaused = false)
                startForeground(NOTIFICATION_ID, notification)
                startUsagePolling()
            }
        }
        return START_STICKY
    }

    private fun startUsagePolling() {
        if (pollingJob != null && pollingJob?.isActive == true) return

        pollingJob = serviceScope.launch {
            while (isActive) {
                try {
                    checkForegroundPackage()
                } catch (_: Exception) {
                    // Ignore transient exceptions in loop
                }
                delay(350L) // Polling interval 350ms
            }
        }
    }

    private suspend fun checkForegroundPackage() {
        val isPaused = dataStore.isProtectionPaused.first()
        if (isPaused) return

        val endTime = System.currentTimeMillis()
        val beginTime = endTime - 2500L
        val events = usageStatsManager.queryEvents(beginTime, endTime) ?: return

        var latestEventPkg: String? = null
        val event = UsageEvents.Event()

        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED ||
                event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND
            ) {
                latestEventPkg = event.packageName
            }
        }

        if (latestEventPkg.isNullOrBlank() || latestEventPkg == packageName) {
            return
        }

        if (foregroundRules.isSystemExempt(latestEventPkg)) {
            val prev = currentForegroundPackage
            if (prev != null && prev != latestEventPkg) {
                TaalaAccessibilityService.sessionTracker.onAppBackgrounded(prev)
                currentForegroundPackage = null
            }
            return
        }

        if (foregroundRules.isHelperApp(latestEventPkg)) {
            return
        }

        val previous = currentForegroundPackage
        if (previous != null && previous != latestEventPkg) {
            TaalaAccessibilityService.sessionTracker.onAppBackgrounded(previous)
        }
        currentForegroundPackage = latestEventPkg

        // Self protection check for device settings
        val isSelfProtectionOn = dataStore.selfProtectionEnabled.first()
        if (isSelfProtectionOn && latestEventPkg == "com.android.settings") {
            if (!TaalaAccessibilityService.sessionTracker.isAppSessionValid(latestEventPkg)) {
                launchLockActivity(latestEventPkg, "Device Settings")
                return
            }
        }

        // Check if package is locked in database
        val lockedEntity = database.lockedAppDao().getLockedAppSync(latestEventPkg)
        if (lockedEntity == null || !lockedEntity.isLocked) {
            return
        }

        if (TaalaAccessibilityService.sessionTracker.isAppSessionValid(latestEventPkg)) {
            TaalaAccessibilityService.sessionTracker.onAppForegrounded(latestEventPkg)
            return
        }

        val appLabel = lockedEntity.appLabel.ifBlank { latestEventPkg }
        launchLockActivity(latestEventPkg, appLabel)
    }

    private fun launchLockActivity(targetPkg: String, targetLabel: String) {
        val lockIntent = Intent(this, LockScreenActivity::class.java).apply {
            putExtra(LockScreenActivity.EXTRA_PACKAGE_NAME, targetPkg)
            putExtra(LockScreenActivity.EXTRA_APP_LABEL, targetLabel)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_NO_ANIMATION or
                    Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
        }
        startActivity(lockIntent)
    }

    private fun updateNotification(isPaused: Boolean) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, buildNotification(isPaused))
    }

    private fun buildNotification(isPaused: Boolean): Notification {
        val openAppIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_padlock_front)
            .setContentIntent(openAppIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)

        if (isPaused) {
            val resumeIntent = PendingIntent.getService(
                this,
                1,
                Intent(this, TaalaForegroundService::class.java).apply { action = ACTION_RESUME },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.setContentTitle(getString(R.string.notification_title_paused))
                .setContentText(getString(R.string.notification_text_paused))
                .addAction(
                    R.drawable.ic_padlock_front,
                    getString(R.string.action_resume_protection),
                    resumeIntent
                )
        } else {
            val pauseIntent = PendingIntent.getService(
                this,
                2,
                Intent(this, TaalaForegroundService::class.java).apply { action = ACTION_PAUSE_15M },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.setContentTitle(getString(R.string.notification_title_active))
                .setContentText(getString(R.string.notification_text_active))
                .addAction(
                    R.drawable.ic_padlock_front,
                    getString(R.string.action_pause_15m),
                    pauseIntent
                )
        }

        return builder.build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_desc)
                setShowBadge(false)
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun registerScreenOffReceiver() {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        screenReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == Intent.ACTION_SCREEN_OFF) {
                    TaalaAccessibilityService.sessionTracker.endAllSessions()
                    currentForegroundPackage = null
                }
            }
        }
        registerReceiver(screenReceiver, filter)
    }

    private fun unregisterScreenOffReceiver() {
        screenReceiver?.let {
            try {
                unregisterReceiver(it)
            } catch (_: Exception) {}
            screenReceiver = null
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        pollingJob?.cancel()
        serviceScope.cancel()
        unregisterScreenOffReceiver()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
