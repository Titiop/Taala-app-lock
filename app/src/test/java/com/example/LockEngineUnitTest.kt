package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.core.security.LockStateTracker
import com.example.feature.lock.ForegroundRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LockEngineUnitTest {

    private lateinit var context: Context
    private lateinit var foregroundRules: ForegroundRules
    private lateinit var sessionTracker: LockStateTracker

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        foregroundRules = ForegroundRules(context)
        sessionTracker = LockStateTracker()
    }

    @Test
    fun testSystemExemptRules() {
        // System UI and Framework must never be covered (Section 5.6)
        assertTrue(foregroundRules.isSystemExempt("com.android.systemui"))
        assertTrue(foregroundRules.isSystemExempt("android"))
        assertTrue(foregroundRules.isSystemExempt(context.packageName))
        assertTrue(foregroundRules.isSystemExempt(""))
        assertTrue(foregroundRules.isSystemExempt("com.android.server.telecom"))

        // Standard user apps must NOT be system exempt
        assertFalse(foregroundRules.isSystemExempt("com.example.bank"))
        assertFalse(foregroundRules.isSystemExempt("com.whatsapp"))
    }

    @Test
    fun testHelperAppHopGraceWindow() {
        val bankPkg = "com.example.bank"

        // Bank is unlocked
        sessionTracker.registerAppUnlocked(bankPkg, relockDelaySeconds = 0) // immediate relock
        assertTrue(sessionTracker.isAppSessionValid(bankPkg))

        // App backgrounded
        sessionTracker.onAppBackgrounded(bankPkg)
        // With delay = 0, once backgrounded it is no longer valid directly
        assertFalse(sessionTracker.isAppSessionValid(bankPkg))

        // But returning from a helper hop within 5 minutes restores valid session (Section 5.4)
        sessionTracker.onAppForegrounded(bankPkg)
        assertTrue(sessionTracker.isAppSessionValid(bankPkg))
    }

    @Test
    fun testScreenOffTerminatesAllSessions() {
        val app1 = "com.example.app1"
        val app2 = "com.example.app2"

        sessionTracker.registerAppUnlocked(app1, relockDelaySeconds = 60)
        sessionTracker.registerAppUnlocked(app2, relockDelaySeconds = 300)

        assertEquals(2, sessionTracker.getActiveSessionCount())

        // Section 5.5: Screen off always ends all sessions
        sessionTracker.endAllSessions()
        assertEquals(0, sessionTracker.getActiveSessionCount())
        assertFalse(sessionTracker.isAppSessionValid(app1))
        assertFalse(sessionTracker.isAppSessionValid(app2))
    }
}
