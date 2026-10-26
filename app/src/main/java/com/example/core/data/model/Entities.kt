package com.example.core.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "locked_apps")
data class LockedAppEntity(
    @PrimaryKey
    val packageName: String,
    val appLabel: String,
    val isLocked: Boolean = true,
    val profileId: String = "default",
    val lockedAt: Long = System.currentTimeMillis(),
    val customRelockDelaySeconds: Int? = null,
    val isNotificationShieldEnabled: Boolean = false,
    val isSystemApp: Boolean = false
)

@Entity(tableName = "profiles")
data class ProfileEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val description: String,
    val colorHex: String,
    val iconName: String,
    val relockDelaySeconds: Int = 0, // 0 = Immediately, 30, 60, 300, 900
    val isEnabled: Boolean = true,
    val isBuiltIn: Boolean = true
)

@Entity(tableName = "schedules")
data class ScheduleEntity(
    @PrimaryKey
    val id: String,
    val profileId: String,
    val title: String,
    val daysMask: Int = 127, // 7-bit mask for Mon-Sun
    val startHour: Int = 22,
    val startMinute: Int = 30,
    val endHour: Int = 7,
    val endMinute: Int = 0,
    val isEnabled: Boolean = true
)

@Entity(tableName = "intruder_records")
data class IntruderEntity(
    @PrimaryKey
    val id: String,
    val timestamp: Long = System.currentTimeMillis(),
    val packageName: String,
    val appLabel: String,
    val photoUri: String? = null,
    val attemptCount: Int = 3
)
