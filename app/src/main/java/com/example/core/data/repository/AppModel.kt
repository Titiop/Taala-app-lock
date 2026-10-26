package com.example.core.data.repository

import android.graphics.drawable.Drawable

enum class AppCategory {
    BANKING,
    MESSAGING,
    GALLERY,
    SETTINGS,
    BROWSER,
    FILES,
    GENERAL
}

enum class AppFilter {
    ALL,
    LOCKED,
    UNLOCKED,
    USER,
    SYSTEM
}

enum class AppSort {
    NAME_ASC,
    LOCKED_FIRST,
    RECENTLY_INSTALLED
}

enum class ProtectionState {
    ACTIVE,
    PAUSED,
    NEEDS_ATTENTION
}

data class AppItem(
    val packageName: String,
    val appLabel: String,
    val isLocked: Boolean,
    val profileId: String = "default",
    val customRelockDelaySeconds: Int? = null,
    val isNotificationShieldEnabled: Boolean = false,
    val isSystemApp: Boolean = false,
    val isSuggested: Boolean = false,
    val category: AppCategory = AppCategory.GENERAL,
    val isLockable: Boolean = true, // False for default home launcher, dialer emergency
    val lockDisabledReason: String? = null,
    val installTime: Long = 0L,
    val icon: Drawable? = null
)
