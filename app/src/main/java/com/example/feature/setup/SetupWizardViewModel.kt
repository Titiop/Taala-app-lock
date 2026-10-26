package com.example.feature.setup

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.data.datastore.AppSettingsDataStore
import com.example.core.security.PermissionManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class StepStatus {
    DONE,
    NEEDED,
    OPTIONAL
}

data class SetupWizardUiState(
    val biometricStatus: StepStatus = StepStatus.NEEDED,
    val accessibilityStatus: StepStatus = StepStatus.NEEDED,
    val notificationStatus: StepStatus = StepStatus.OPTIONAL,
    val usageStatsStatus: StepStatus = StepStatus.OPTIONAL,
    val isReadyToProtect: Boolean = false
)

class SetupWizardViewModel(application: Application) : AndroidViewModel(application) {

    private val permissionManager = PermissionManager(application)
    private val dataStore = AppSettingsDataStore(application)

    private val _uiState = MutableStateFlow(SetupWizardUiState())
    val uiState: StateFlow<SetupWizardUiState> = _uiState.asStateFlow()

    init {
        refreshPermissions()
    }

    fun refreshPermissions() {
        val bioEnrolled = permissionManager.isBiometricEnrolled()
        val accEnabled = permissionManager.isAccessibilityServiceEnabled()
        val notifGranted = permissionManager.isNotificationPermissionGranted()
        val usageGranted = permissionManager.isUsageStatsPermissionGranted()

        val bioStatus = if (bioEnrolled) StepStatus.DONE else StepStatus.NEEDED
        val accStatus = if (accEnabled) StepStatus.DONE else StepStatus.NEEDED
        val notifStatus = if (notifGranted) StepStatus.DONE else StepStatus.OPTIONAL
        val usageStatus = if (usageGranted) StepStatus.DONE else StepStatus.OPTIONAL

        _uiState.value = SetupWizardUiState(
            biometricStatus = bioStatus,
            accessibilityStatus = accStatus,
            notificationStatus = notifStatus,
            usageStatsStatus = usageStatus,
            isReadyToProtect = bioEnrolled && accEnabled
        )
    }

    fun onFinishSetup() {
        viewModelScope.launch {
            dataStore.setCompletedOnboarding(true)
        }
    }
}
