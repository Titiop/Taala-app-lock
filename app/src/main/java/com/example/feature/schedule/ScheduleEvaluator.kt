package com.example.feature.schedule

import com.example.core.data.model.ScheduleEntity
import java.util.Calendar

object ScheduleEvaluator {

    fun daysListToMask(days: List<Int>): Int {
        var mask = 0
        for (day in days) {
            if (day in 1..7) {
                mask = mask or (1 shl (day - 1))
            }
        }
        return mask
    }

    fun maskToDaysList(mask: Int): List<Int> {
        val list = mutableListOf<Int>()
        for (day in 1..7) {
            val bit = 1 shl (day - 1)
            if ((mask and bit) != 0) {
                list.add(day)
            }
        }
        return list
    }

    /**
     * Checks if a specific schedule is currently active based on current day and time.
     */
    fun isScheduleActiveNow(schedule: ScheduleEntity, now: Calendar = Calendar.getInstance()): Boolean {
        if (!schedule.isEnabled) return false

        // In Calendar, SUNDAY=1, MONDAY=2, ... SATURDAY=7
        // In our entity, we map 1=Monday, 2=Tuesday, ... 7=Sunday
        val currentDayOfWeek = when (now.get(Calendar.DAY_OF_WEEK)) {
            Calendar.MONDAY -> 1
            Calendar.TUESDAY -> 2
            Calendar.WEDNESDAY -> 3
            Calendar.THURSDAY -> 4
            Calendar.FRIDAY -> 5
            Calendar.SATURDAY -> 6
            Calendar.SUNDAY -> 7
            else -> 1
        }

        val dayBit = 1 shl (currentDayOfWeek - 1)
        if ((schedule.daysMask and dayBit) == 0) {
            return false
        }

        val currentHour = now.get(Calendar.HOUR_OF_DAY)
        val currentMinute = now.get(Calendar.MINUTE)
        val currentTimeMinutes = currentHour * 60 + currentMinute

        val startMinutes = schedule.startHour * 60 + schedule.startMinute
        val endMinutes = schedule.endHour * 60 + schedule.endMinute

        return if (startMinutes <= endMinutes) {
            currentTimeMinutes in startMinutes..endMinutes
        } else {
            currentTimeMinutes >= startMinutes || currentTimeMinutes <= endMinutes
        }
    }

    /**
     * Checks if any schedule targeting [profileId] is currently active.
     */
    fun isProfileLockedBySchedule(
        profileId: String?,
        schedules: List<ScheduleEntity>,
        now: Calendar = Calendar.getInstance()
    ): Boolean {
        return schedules.any { schedule ->
            schedule.isEnabled &&
                    (schedule.profileId == "default" || schedule.profileId == profileId) &&
                    isScheduleActiveNow(schedule, now)
        }
    }
}
