package com.example.core.data.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "taala_settings")

class AppSettingsDataStore(private val context: Context) {

    private object PreferencesKeys {
        val ACTIVE_ENGINE = stringPreferencesKey("active_engine")
        val IS_PROTECTION_PAUSED = booleanPreferencesKey("is_protection_paused")
        val PAUSE_UNTIL_TIMESTAMP = longPreferencesKey("pause_until_timestamp")
        val DEFAULT_RELOCK_DELAY = intPreferencesKey("default_relock_delay")
        val LOCK_ON_SCREEN_OFF = booleanPreferencesKey("lock_on_screen_off")
        val LOCK_NEW_APPS_AUTO = booleanPreferencesKey("lock_new_apps_auto")
        val SELF_PROTECTION_ENABLED = booleanPreferencesKey("self_protection_enabled")
        val PIN_ENABLED = booleanPreferencesKey("pin_enabled")
        val BIOMETRICS_ENABLED = booleanPreferencesKey("biometrics_enabled")
        val INTRUDER_SELFIE_ENABLED = booleanPreferencesKey("intruder_selfie_enabled")
        val INTRUDER_THRESHOLD = intPreferencesKey("intruder_threshold")
        val INACTIVITY_TIMEOUT_MINS = intPreferencesKey("inactivity_timeout_mins")
        val DISGUISE_MODE_ENABLED = booleanPreferencesKey("disguise_mode_enabled")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val HAS_COMPLETED_ONBOARDING = booleanPreferencesKey("has_completed_onboarding")
    }

    val activeEngine: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[PreferencesKeys.ACTIVE_ENGINE] ?: "Engine A (Accessibility)"
    }

    val isProtectionPaused: Flow<Boolean> = context.dataStore.data.map { prefs ->
        val isPaused = prefs[PreferencesKeys.IS_PROTECTION_PAUSED] ?: false
        val pauseUntil = prefs[PreferencesKeys.PAUSE_UNTIL_TIMESTAMP] ?: 0L
        if (isPaused && pauseUntil > 0L && System.currentTimeMillis() > pauseUntil) {
            false // Auto-resumed
        } else {
            isPaused
        }
    }

    val defaultRelockDelay: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[PreferencesKeys.DEFAULT_RELOCK_DELAY] ?: 0 // 0 = Immediately
    }

    val lockOnScreenOff: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[PreferencesKeys.LOCK_ON_SCREEN_OFF] ?: true
    }

    val lockNewAppsAuto: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[PreferencesKeys.LOCK_NEW_APPS_AUTO] ?: false
    }

    val selfProtectionEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[PreferencesKeys.SELF_PROTECTION_ENABLED] ?: false
    }

    val pinEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[PreferencesKeys.PIN_ENABLED] ?: false
    }

    val biometricsEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[PreferencesKeys.BIOMETRICS_ENABLED] ?: true
    }

    val intruderSelfieEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[PreferencesKeys.INTRUDER_SELFIE_ENABLED] ?: true
    }

    val intruderThreshold: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[PreferencesKeys.INTRUDER_THRESHOLD] ?: 3
    }

    val disguiseModeEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[PreferencesKeys.DISGUISE_MODE_ENABLED] ?: false
    }

    val inactivityTimeoutMins: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[PreferencesKeys.INACTIVITY_TIMEOUT_MINS] ?: 3
    }

    val themeMode: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[PreferencesKeys.THEME_MODE] ?: "SYSTEM"
    }

    val hasCompletedOnboarding: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[PreferencesKeys.HAS_COMPLETED_ONBOARDING] ?: false
    }

    suspend fun setActiveEngine(engine: String) {
        context.dataStore.edit { prefs ->
            prefs[PreferencesKeys.ACTIVE_ENGINE] = engine
        }
    }

    suspend fun setProtectionPaused(paused: Boolean, durationMs: Long = 0L) {
        context.dataStore.edit { prefs ->
            prefs[PreferencesKeys.IS_PROTECTION_PAUSED] = paused
            prefs[PreferencesKeys.PAUSE_UNTIL_TIMESTAMP] = if (paused && durationMs > 0L) {
                System.currentTimeMillis() + durationMs
            } else 0L
        }
    }

    suspend fun setDefaultRelockDelay(delaySeconds: Int) {
        context.dataStore.edit { prefs ->
            prefs[PreferencesKeys.DEFAULT_RELOCK_DELAY] = delaySeconds
        }
    }

    suspend fun setLockOnScreenOff(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[PreferencesKeys.LOCK_ON_SCREEN_OFF] = enabled
        }
    }

    suspend fun setLockNewAppsAuto(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[PreferencesKeys.LOCK_NEW_APPS_AUTO] = enabled
        }
    }

    suspend fun setSelfProtectionEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[PreferencesKeys.SELF_PROTECTION_ENABLED] = enabled
        }
    }

    suspend fun setPinEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[PreferencesKeys.PIN_ENABLED] = enabled
        }
    }

    suspend fun setBiometricsEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[PreferencesKeys.BIOMETRICS_ENABLED] = enabled
        }
    }

    suspend fun setIntruderSelfieEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[PreferencesKeys.INTRUDER_SELFIE_ENABLED] = enabled
        }
    }

    suspend fun setIntruderThreshold(count: Int) {
        context.dataStore.edit { prefs ->
            prefs[PreferencesKeys.INTRUDER_THRESHOLD] = count
        }
    }

    suspend fun setInactivityTimeoutMins(mins: Int) {
        context.dataStore.edit { prefs ->
            prefs[PreferencesKeys.INACTIVITY_TIMEOUT_MINS] = mins
        }
    }

    suspend fun setDisguiseModeEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[PreferencesKeys.DISGUISE_MODE_ENABLED] = enabled
        }
    }

    suspend fun setThemeMode(mode: String) {
        context.dataStore.edit { prefs ->
            prefs[PreferencesKeys.THEME_MODE] = mode
        }
    }

    suspend fun setCompletedOnboarding(completed: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[PreferencesKeys.HAS_COMPLETED_ONBOARDING] = completed
        }
    }
}
