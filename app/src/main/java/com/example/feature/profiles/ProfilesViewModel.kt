package com.example.feature.profiles

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.data.database.TaalaDatabase
import com.example.core.data.model.ProfileEntity
import com.example.core.data.repository.AppItem
import com.example.core.data.repository.AppRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

data class ProfileUiItem(
    val profile: ProfileEntity,
    val assignedAppsCount: Int,
    val lockedAppsCount: Int,
    val isMasterLocked: Boolean
)

data class ProfilesUiState(
    val profiles: List<ProfileUiItem> = emptyList(),
    val isLoading: Boolean = true
)

class ProfilesViewModel(application: Application) : AndroidViewModel(application) {

    private val database = TaalaDatabase.getInstance(application)
    private val profileDao = database.profileDao()
    private val appRepository = AppRepository(application, database.lockedAppDao())

    val uiState: StateFlow<ProfilesUiState> = combine(
        profileDao.getAllProfiles(),
        appRepository.getAppsStream()
    ) { profiles, apps ->
        val uiItems = profiles.map { profile ->
            val profileApps = apps.filter { it.profileId == profile.id }
            val lockedCount = profileApps.count { it.isLocked }
            val isMasterActive = profile.isEnabled && (lockedCount > 0 || profileApps.isEmpty())

            ProfileUiItem(
                profile = profile,
                assignedAppsCount = profileApps.size,
                lockedAppsCount = lockedCount,
                isMasterLocked = profile.isEnabled
            )
        }

        ProfilesUiState(
            profiles = uiItems,
            isLoading = false
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ProfilesUiState()
    )

    fun onToggleProfileMaster(profile: ProfileEntity, isEnabled: Boolean) {
        viewModelScope.launch {
            profileDao.setProfileEnabled(profile.id, isEnabled)
            // Enable or disable all apps in this profile at once (Section 7.2)
            appRepository.setAllProfileAppsLocked(profile.id, isEnabled)
        }
    }

    fun onCreateCustomProfile(
        name: String,
        description: String,
        colorHex: String,
        iconName: String,
        delaySeconds: Int
    ) {
        viewModelScope.launch {
            val id = "custom_${UUID.randomUUID().toString().take(8)}"
            val entity = ProfileEntity(
                id = id,
                name = name,
                description = description,
                colorHex = colorHex,
                iconName = iconName,
                relockDelaySeconds = delaySeconds,
                isEnabled = true,
                isBuiltIn = false
            )
            profileDao.insert(entity)
        }
    }

    fun onUpdateProfile(profile: ProfileEntity) {
        viewModelScope.launch {
            profileDao.update(profile)
        }
    }

    fun onDeleteCustomProfile(profileId: String) {
        viewModelScope.launch {
            profileDao.deleteCustomProfile(profileId)
        }
    }
}
