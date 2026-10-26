package com.example.feature.lock

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.view.accessibility.AccessibilityEvent
import com.example.core.data.database.TaalaDatabase
import com.example.core.data.datastore.AppSettingsDataStore
import com.example.core.data.repository.AppRepository
import com.example.core.security.LockStateTracker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.concurrent.Executors

class TaalaAccessibilityService : AccessibilityService() {

    companion object {
        private val _isServiceConnected = MutableStateFlow(false)
        val isServiceConnected: StateFlow<Boolean> = _isServiceConnected.asStateFlow()

        // Shared session tracker singleton accessible to LockScreenActivity and service
        val sessionTracker = LockStateTracker()
    }

    // Section 6: Use a dedicated single-thread dispatcher for engine decisions
    private val singleThreadExecutor = Executors.newSingleThreadExecutor()
    private val serviceDispatcher = singleThreadExecutor.asCoroutineDispatcher()
    private val serviceScope = CoroutineScope(serviceDispatcher)

    private lateinit var foregroundRules: ForegroundRules
    private lateinit var coverOverlay: CoverOverlay
    private lateinit var lockLauncher: LockLauncher
    private lateinit var database: TaalaDatabase
    private lateinit var appRepository: AppRepository
    private lateinit var dataStore: AppSettingsDataStore

    private var currentForegroundPackage: String? = null
    private var screenReceiver: BroadcastReceiver? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        _isServiceConnected.value = true

        foregroundRules = ForegroundRules(this)
        coverOverlay = CoverOverlay(this)
        lockLauncher = LockLauncher(this, coverOverlay)
        database = TaalaDatabase.getInstance(this)
        appRepository = AppRepository(this, database.lockedAppDao())
        dataStore = AppSettingsDataStore(this)

        registerScreenOffReceiver()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        // Fast filtering: only process window state & windows changed
        val eventType = event.eventType
        if (eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            eventType != AccessibilityEvent.TYPE_WINDOWS_CHANGED
        ) {
            return
        }

        val rawPackage = event.packageName?.toString() ?: return
        if (rawPackage.isBlank()) return

        // Dispatch decision off the main thread to single-threaded worker
        serviceScope.launch {
            processWindowEvent(rawPackage)
        }
    }

    private suspend fun processWindowEvent(packageName: String) {
        // Skip Taala's own internal activities from triggering an outer lock
        if (packageName == this.packageName) {
            return
        }

        // Section 5.6: Check if system exempt (Home launcher, SystemUI, Recents, Keyboards)
        if (foregroundRules.isSystemExempt(packageName)) {
            val previous = currentForegroundPackage
            if (previous != null && previous != packageName) {
                sessionTracker.onAppBackgrounded(previous)
                currentForegroundPackage = null
            }
            return
        }

        // Section 5.4: Check helper-app hop (e.g. Authenticator, photo picker, camera)
        if (foregroundRules.isHelperApp(packageName)) {
            // User hopped to a helper app; maintain previous session
            return
        }

        val previous = currentForegroundPackage
        if (previous != null && previous != packageName) {
            sessionTracker.onAppBackgrounded(previous)
        }
        currentForegroundPackage = packageName

        // Check if protection is currently paused
        val isPaused = dataStore.isProtectionPaused.first()
        if (isPaused) {
            return
        }

        // Section 7: Self-protection against tampering via Settings or PackageInstaller
        val isSelfProtectionOn = dataStore.selfProtectionEnabled.first()
        if (isSelfProtectionOn && (packageName == "com.android.settings" || packageName == "com.google.android.packageinstaller")) {
            if (!sessionTracker.isAppSessionValid(packageName)) {
                val appLabel = if (packageName == "com.android.settings") "Device Settings" else "Package Installer"
                val icon = appRepository.getAppIcon(packageName)
                lockLauncher.triggerLock(packageName, appLabel, icon)
                return
            } else {
                sessionTracker.onAppForegrounded(packageName)
                return
            }
        }

        // Check if app is locked in Room database
        val lockedEntity = database.lockedAppDao().getLockedAppSync(packageName)
        if (lockedEntity == null || !lockedEntity.isLocked) {
            return
        }

        // Check if app currently has an active valid session (Section 5.3)
        if (sessionTracker.isAppSessionValid(packageName)) {
            sessionTracker.onAppForegrounded(packageName)
            return
        }

        // App is locked and needs authentication: Trigger lock cover instantly
        val appLabel = lockedEntity.appLabel.ifBlank { packageName }
        val icon = appRepository.getAppIcon(packageName)

        lockLauncher.triggerLock(packageName, appLabel, icon)
    }

    override fun onInterrupt() {
        // Accessibility service interrupted
        coverOverlay.hide()
    }

    override fun onDestroy() {
        super.onDestroy()
        _isServiceConnected.value = false
        coverOverlay.hide()
        unregisterScreenOffReceiver()
        serviceScope.cancel()
        singleThreadExecutor.shutdown()
    }

    private fun registerScreenOffReceiver() {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        screenReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == Intent.ACTION_SCREEN_OFF) {
                    // Section 5.5: Screen off always ends all sessions
                    sessionTracker.endAllSessions()
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
}
