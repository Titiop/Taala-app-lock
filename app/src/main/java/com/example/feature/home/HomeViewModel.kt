package com.example.feature.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.data.database.TaalaDatabase
import com.example.core.data.datastore.AppSettingsDataStore
import com.example.core.data.repository.AppFilter
import com.example.core.data.repository.AppItem
import com.example.core.data.repository.AppRepository
import com.example.core.data.repository.AppSort
import com.example.core.data.repository.ProtectionState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HomeUiState(
    val allApps: List<AppItem> = emptyList(),
    val filteredApps: List<AppItem> = emptyList(),
    val suggestedApps: List<AppItem> = emptyList(),
    val searchQuery: String = "",
    val selectedFilter: AppFilter = AppFilter.ALL,
    val selectedSort: AppSort = AppSort.NAME_ASC,
    val lockedCount: Int = 0,
    val activeEngine: String = "Engine A (Accessibility)",
    val protectionState: ProtectionState = ProtectionState.ACTIVE,
    val isLoading: Boolean = true
)

private data class FilterState(
    val query: String = "",
    val filter: AppFilter = AppFilter.ALL,
    val sort: AppSort = AppSort.NAME_ASC
)

private data class AppCoreState(
    val apps: List<AppItem>,
    val lockedCount: Int,
    val engine: String,
    val isPaused: Boolean,
    val isAccessibilityServiceConnected: Boolean
)

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val database = TaalaDatabase.getInstance(application)
    private val appRepository = AppRepository(application, database.lockedAppDao())
    private val dataStore = AppSettingsDataStore(application)

    private val _searchQuery = MutableStateFlow("")
    private val _selectedFilter = MutableStateFlow(AppFilter.ALL)
    private val _selectedSort = MutableStateFlow(AppSort.NAME_ASC)

    private val _filterState = combine(
        _searchQuery,
        _selectedFilter,
        _selectedSort
    ) { query, filter, sort ->
        FilterState(query, filter, sort)
    }

    private val _appCoreState = combine(
        appRepository.getAppsStream(),
        appRepository.getLockedCount(),
        dataStore.activeEngine,
        dataStore.isProtectionPaused,
        com.example.feature.lock.TaalaAccessibilityService.isServiceConnected
    ) { apps, count, engine, isPaused, isServiceConnected ->
        AppCoreState(apps, count, engine, isPaused, isServiceConnected)
    }

    val uiState: StateFlow<HomeUiState> = combine(
        _appCoreState,
        _filterState
    ) { core, filterState ->
        val protectionState = when {
            core.isPaused -> ProtectionState.PAUSED
            !core.isAccessibilityServiceConnected && core.lockedCount > 0 -> ProtectionState.NEEDS_ATTENTION
            else -> ProtectionState.ACTIVE
        }

        val suggested = core.apps.filter { it.isSuggested && it.isLockable }

        val filtered = core.apps
            .filter { app ->
                val matchesQuery = filterState.query.isEmpty() ||
                        app.appLabel.contains(filterState.query, ignoreCase = true) ||
                        app.packageName.contains(filterState.query, ignoreCase = true)

                val matchesFilter = when (filterState.filter) {
                    AppFilter.ALL -> true
                    AppFilter.LOCKED -> app.isLocked
                    AppFilter.UNLOCKED -> !app.isLocked && app.isLockable
                    AppFilter.USER -> !app.isSystemApp
                    AppFilter.SYSTEM -> app.isSystemApp
                }

                matchesQuery && matchesFilter
            }
            .sortedWith { a, b ->
                when (filterState.sort) {
                    AppSort.NAME_ASC -> a.appLabel.compareTo(b.appLabel, ignoreCase = true)
                    AppSort.LOCKED_FIRST -> {
                        if (a.isLocked != b.isLocked) {
                            if (a.isLocked) -1 else 1
                        } else {
                            a.appLabel.compareTo(b.appLabel, ignoreCase = true)
                        }
                    }
                    AppSort.RECENTLY_INSTALLED -> b.installTime.compareTo(a.installTime)
                }
            }

        HomeUiState(
            allApps = core.apps,
            filteredApps = filtered,
            suggestedApps = suggested,
            searchQuery = filterState.query,
            selectedFilter = filterState.filter,
            selectedSort = filterState.sort,
            lockedCount = core.lockedCount,
            activeEngine = core.engine,
            protectionState = protectionState,
            isLoading = false
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = HomeUiState()
    )

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
    }

    fun onFilterSelect(filter: AppFilter) {
        _selectedFilter.value = filter
    }

    fun onSortSelect(sort: AppSort) {
        _selectedSort.value = sort
    }

    fun onToggleLock(app: AppItem, newLockState: Boolean) {
        viewModelScope.launch {
            appRepository.toggleAppLock(app, newLockState)
        }
    }

    val availableProfiles: StateFlow<List<com.example.core.data.model.ProfileEntity>> = database.profileDao().getAllProfiles()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun onSetAppProfile(packageName: String, profileId: String) {
        viewModelScope.launch {
            appRepository.setAppProfile(packageName, profileId)
        }
    }

    fun onSetCustomRelockDelay(packageName: String, delaySeconds: Int?) {
        viewModelScope.launch {
            appRepository.setCustomRelockDelay(packageName, delaySeconds)
        }
    }

    fun onSetNotificationShield(packageName: String, enabled: Boolean) {
        viewModelScope.launch {
            appRepository.setNotificationShield(packageName, enabled)
        }
    }

    fun onPauseProtection(durationMs: Long) {
        viewModelScope.launch {
            dataStore.setProtectionPaused(true, durationMs)
        }
    }

    fun onResumeProtection() {
        viewModelScope.launch {
            dataStore.setProtectionPaused(false)
        }
    }
}
