package com.example.core.security

import java.util.concurrent.ConcurrentHashMap

data class AppSession(
    val packageName: String,
    val unlockedAt: Long = System.currentTimeMillis(),
    val relockDelaySeconds: Int = 0,
    var lastActiveTime: Long = System.currentTimeMillis(),
    var isCurrentlyInForeground: Boolean = true
)

class LockStateTracker {

    private val activeSessions = ConcurrentHashMap<String, AppSession>()
    private var lastUnlockedAppPackage: String? = null
    private var lastUnlockedAppTime: Long = 0L

    companion object {
        const val HELPER_HOP_WINDOW_MS = 5 * 60 * 1000L // 5 minutes helper hop grace period
    }

    fun isAppSessionValid(packageName: String): Boolean {
        val session = activeSessions[packageName] ?: return false

        // If the app is currently in foreground, it is valid regardless of elapsed time (Section 5.3)
        if (session.isCurrentlyInForeground) {
            return true
        }

        // If backgrounded, check re-lock delay
        val elapsedSinceLeft = (System.currentTimeMillis() - session.lastActiveTime) / 1000L
        val isStillValid = elapsedSinceLeft < session.relockDelaySeconds

        if (!isStillValid) {
            activeSessions.remove(packageName)
        }
        return isStillValid
    }

    fun registerAppUnlocked(packageName: String, relockDelaySeconds: Int) {
        val now = System.currentTimeMillis()
        val session = AppSession(
            packageName = packageName,
            unlockedAt = now,
            relockDelaySeconds = relockDelaySeconds,
            lastActiveTime = now,
            isCurrentlyInForeground = true
        )
        activeSessions[packageName] = session
        lastUnlockedAppPackage = packageName
        lastUnlockedAppTime = now
    }

    fun onAppForegrounded(packageName: String) {
        val session = activeSessions[packageName]
        if (session != null) {
            session.isCurrentlyInForeground = true
            session.lastActiveTime = System.currentTimeMillis()
        } else {
            // Check if user hopped back from a helper app within 5 minutes
            if (lastUnlockedAppPackage == packageName && (System.currentTimeMillis() - lastUnlockedAppTime) < HELPER_HOP_WINDOW_MS) {
                // Restore session
                activeSessions[packageName] = AppSession(
                    packageName = packageName,
                    lastActiveTime = System.currentTimeMillis(),
                    isCurrentlyInForeground = true
                )
            }
        }
    }

    fun onAppBackgrounded(packageName: String) {
        val session = activeSessions[packageName] ?: return
        session.isCurrentlyInForeground = false
        session.lastActiveTime = System.currentTimeMillis()
    }

    /**
     * Section 5.5: Screen off, device lock, and lockdown always end all sessions.
     */
    fun endAllSessions() {
        activeSessions.clear()
        lastUnlockedAppPackage = null
        lastUnlockedAppTime = 0L
    }

    fun clearSession(packageName: String) {
        activeSessions.remove(packageName)
    }

    fun getActiveSessionCount(): Int = activeSessions.size
}
