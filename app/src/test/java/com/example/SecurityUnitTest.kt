package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.core.security.LockStateTracker
import com.example.core.security.PinManager
import com.example.core.security.PinVerificationResult
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
class SecurityUnitTest {

    private lateinit var context: Context
    private lateinit var pinManager: PinManager
    private lateinit var lockStateTracker: LockStateTracker

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        pinManager = PinManager(context)
        pinManager.clearPin()
        lockStateTracker = LockStateTracker()
    }

    @Test
    fun testPinLifecycleAndEscalatingLockout() {
        assertFalse(pinManager.isPinSet())

        // Set valid 6-digit PIN
        assertTrue(pinManager.setPin("123456"))
        assertTrue(pinManager.isPinSet())

        // Test correct PIN
        val successResult = pinManager.verifyPin("123456")
        assertTrue(successResult is PinVerificationResult.Success)

        // 1st wrong attempt -> no lockout
        val fail1 = pinManager.verifyPin("000000") as PinVerificationResult.Failed
        assertEquals(1, fail1.failedAttempts)
        assertEquals(0, fail1.lockoutSeconds)

        // 2nd wrong attempt -> no lockout
        val fail2 = pinManager.verifyPin("000000") as PinVerificationResult.Failed
        assertEquals(2, fail2.failedAttempts)
        assertEquals(0, fail2.lockoutSeconds)

        // 3rd wrong attempt -> 30s lockout (Section 5.2 specification)
        val fail3 = pinManager.verifyPin("000000") as PinVerificationResult.Failed
        assertEquals(3, fail3.failedAttempts)
        assertEquals(30, fail3.lockoutSeconds)
        assertTrue(pinManager.getRemainingLockoutSeconds() > 0)

        // While locked out, verifyPin returns LockedOut
        val lockoutCheck = pinManager.verifyPin("123456")
        assertTrue(lockoutCheck is PinVerificationResult.LockedOut)
    }

    @Test
    fun testLockoutScheduleCalculation() {
        assertEquals(0, pinManager.calculateLockoutSeconds(1))
        assertEquals(0, pinManager.calculateLockoutSeconds(2))
        assertEquals(30, pinManager.calculateLockoutSeconds(3))
        assertEquals(60, pinManager.calculateLockoutSeconds(4))
        assertEquals(300, pinManager.calculateLockoutSeconds(5))
        assertEquals(900, pinManager.calculateLockoutSeconds(6))
        assertEquals(900, pinManager.calculateLockoutSeconds(10))
    }

    @Test
    fun testSessionRulesAndScreenOff() {
        val testPkg = "com.example.bank"

        // Unlocked with 30s relock delay
        lockStateTracker.registerAppUnlocked(testPkg, relockDelaySeconds = 30)
        assertTrue(lockStateTracker.isAppSessionValid(testPkg))

        // While foregrounded, always valid
        lockStateTracker.onAppForegrounded(testPkg)
        assertTrue(lockStateTracker.isAppSessionValid(testPkg))

        // Screen off: all sessions terminated immediately (Section 5.5)
        lockStateTracker.endAllSessions()
        assertFalse(lockStateTracker.isAppSessionValid(testPkg))
        assertEquals(0, lockStateTracker.getActiveSessionCount())
    }
}
