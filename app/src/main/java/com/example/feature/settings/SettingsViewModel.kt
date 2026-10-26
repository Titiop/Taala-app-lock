package com.example.feature.settings

import android.app.Application
import android.app.AppOpsManager
import android.content.Context
import android.os.PowerManager
import android.os.Process
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.data.datastore.AppSettingsDataStore
import com.example.feature.lock.TaalaAccessibilityService
import com.example.feature.lock.TaalaForegroundService
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val dataStore = AppSettingsDataStore(application)
    private val backupManager = BackupManager(application)

    val activeEngine: StateFlow<String> = dataStore.activeEngine
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "Engine A (Accessibility)")

    val themeMode: StateFlow<String> = dataStore.themeMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "SYSTEM")

    val defaultRelockDelay: StateFlow<Int> = dataStore.defaultRelockDelay
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val lockOnScreenOff: StateFlow<Boolean> = dataStore.lockOnScreenOff
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val lockNewAppsAuto: StateFlow<Boolean> = dataStore.lockNewAppsAuto
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val selfProtectionEnabled: StateFlow<Boolean> = dataStore.selfProtectionEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val pinEnabled: StateFlow<Boolean> = dataStore.pinEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val biometricsEnabled: StateFlow<Boolean> = dataStore.biometricsEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val intruderSelfieEnabled: StateFlow<Boolean> = dataStore.intruderSelfieEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val intruderThreshold: StateFlow<Int> = dataStore.intruderThreshold
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 3)

    val disguiseModeEnabled: StateFlow<Boolean> = dataStore.disguiseModeEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val isAccessibilityConnected: StateFlow<Boolean> = TaalaAccessibilityService.isServiceConnected

    val hasUsageStatsPermission: Boolean
        get() {
            val appOps = getApplication<Application>().getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
            val mode = appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                getApplication<Application>().packageName
            )
            return mode == AppOpsManager.MODE_ALLOWED
        }

    val isBatteryOptimizationIgnored: Boolean
        get() {
            val powerManager = getApplication<Application>().getSystemService(Context.POWER_SERVICE) as PowerManager
            return powerManager.isIgnoringBatteryOptimizations(getApplication<Application>().packageName)
        }

    fun setActiveEngine(engine: String) {
        viewModelScope.launch {
            dataStore.setActiveEngine(engine)
            if (engine.contains("Foreground") || engine.contains("Engine B")) {
                TaalaForegroundService.startService(getApplication())
            } else {
                TaalaForegroundService.stopService(getApplication())
            }
        }
    }

    fun setThemeMode(mode: String) {
        viewModelScope.launch {
            dataStore.setThemeMode(mode)
        }
    }

    fun setDefaultRelockDelay(delay: Int) {
        viewModelScope.launch {
            dataStore.setDefaultRelockDelay(delay)
        }
    }

    fun setLockOnScreenOff(enabled: Boolean) {
        viewModelScope.launch {
            dataStore.setLockOnScreenOff(enabled)
        }
    }

    fun setLockNewAppsAuto(enabled: Boolean) {
        viewModelScope.launch {
            dataStore.setLockNewAppsAuto(enabled)
        }
    }

    fun setSelfProtectionEnabled(enabled: Boolean) {
        viewModelScope.launch {
            dataStore.setSelfProtectionEnabled(enabled)
        }
    }

    fun setBiometricsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            dataStore.setBiometricsEnabled(enabled)
        }
    }

    fun setIntruderSelfieEnabled(enabled: Boolean) {
        viewModelScope.launch {
            dataStore.setIntruderSelfieEnabled(enabled)
        }
    }

    fun setIntruderThreshold(threshold: Int) {
        viewModelScope.launch {
            dataStore.setIntruderThreshold(threshold)
        }
    }

    fun setDisguiseModeEnabled(enabled: Boolean) {
        viewModelScope.launch {
            dataStore.setDisguiseModeEnabled(enabled)
        }
    }

    suspend fun createBackup(): String {
        return backupManager.createBackupJson()
    }

    suspend fun restoreBackup(encryptedJson: String): Boolean {
        return backupManager.restoreBackup(encryptedJson)
    }
}
