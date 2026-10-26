package com.example.feature.schedule

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.data.database.TaalaDatabase
import com.example.core.data.model.ProfileEntity
import com.example.core.data.model.ScheduleEntity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

class SchedulesViewModel(application: Application) : AndroidViewModel(application) {

    private val database = TaalaDatabase.getInstance(application)
    private val scheduleDao = database.scheduleDao()
    private val profileDao = database.profileDao()

    val schedules: StateFlow<List<ScheduleEntity>> = scheduleDao.getAllSchedules()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val profiles: StateFlow<List<ProfileEntity>> = profileDao.getAllProfiles()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun createSchedule(
        title: String,
        startHour: Int,
        startMinute: Int,
        endHour: Int,
        endMinute: Int,
        daysOfWeek: List<Int>,
        profileId: String?
    ) {
        viewModelScope.launch {
            val daysMask = ScheduleEvaluator.daysListToMask(daysOfWeek)
            val newSchedule = ScheduleEntity(
                id = UUID.randomUUID().toString(),
                profileId = profileId ?: "default",
                title = title.trim(),
                daysMask = daysMask,
                startHour = startHour,
                startMinute = startMinute,
                endHour = endHour,
                endMinute = endMinute,
                isEnabled = true
            )
            scheduleDao.insertOrUpdate(newSchedule)
        }
    }

    fun toggleSchedule(id: String, isEnabled: Boolean) {
        viewModelScope.launch {
            scheduleDao.setScheduleEnabled(id, isEnabled)
        }
    }

    fun deleteSchedule(id: String) {
        viewModelScope.launch {
            scheduleDao.delete(id)
        }
    }
}
