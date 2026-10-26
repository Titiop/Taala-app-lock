package com.example

import androidx.test.core.app.ApplicationProvider
import com.example.core.data.model.ScheduleEntity
import com.example.feature.schedule.ScheduleEvaluator
import com.example.feature.settings.BackupManager
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.Calendar

@RunWith(RobolectricTestRunner::class)
class AdvancedFeaturesUnitTest {

    @Test
    fun scheduleEvaluator_evaluatesNormalWindowCorrectly() {
        val schedule = ScheduleEntity(
            id = "test-sched",
            profileId = "default",
            title = "Work Hours",
            daysMask = ScheduleEvaluator.daysListToMask(listOf(1, 2, 3, 4, 5)), // Mon-Fri = 31
            startHour = 9,
            startMinute = 0,
            endHour = 17,
            endMinute = 0,
            isEnabled = true
        )

        // Monday at 10:30 AM -> Active
        val monMorning = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
            set(Calendar.HOUR_OF_DAY, 10)
            set(Calendar.MINUTE, 30)
        }
        assertTrue(ScheduleEvaluator.isScheduleActiveNow(schedule, monMorning))

        // Monday at 18:00 (6 PM) -> Inactive
        val monEvening = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
            set(Calendar.HOUR_OF_DAY, 18)
            set(Calendar.MINUTE, 0)
        }
        assertFalse(ScheduleEvaluator.isScheduleActiveNow(schedule, monEvening))

        // Saturday at 12:00 PM -> Inactive (Not in daysOfWeek)
        val satNoon = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_WEEK, Calendar.SATURDAY)
            set(Calendar.HOUR_OF_DAY, 12)
            set(Calendar.MINUTE, 0)
        }
        assertFalse(ScheduleEvaluator.isScheduleActiveNow(schedule, satNoon))
    }

    @Test
    fun scheduleEvaluator_evaluatesOvernightWindowCorrectly() {
        val schedule = ScheduleEntity(
            id = "test-night",
            profileId = "default",
            title = "Bedtime Lockdown",
            daysMask = 127, // All days
            startHour = 22,
            startMinute = 30,
            endHour = 6,
            endMinute = 30,
            isEnabled = true
        )

        // 23:00 (11 PM) -> Active
        val lateNight = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 0)
        }
        assertTrue(ScheduleEvaluator.isScheduleActiveNow(schedule, lateNight))

        // 03:00 (3 AM) -> Active
        val earlyMorning = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 3)
            set(Calendar.MINUTE, 0)
        }
        assertTrue(ScheduleEvaluator.isScheduleActiveNow(schedule, earlyMorning))

        // 14:00 (2 PM) -> Inactive
        val afternoon = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 14)
            set(Calendar.MINUTE, 0)
        }
        assertFalse(ScheduleEvaluator.isScheduleActiveNow(schedule, afternoon))
    }

    @Test
    fun backupManager_createsAndRestoresEncryptedBackup() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val backupManager = BackupManager(context)

        // Generate encrypted backup string
        val backupBase64 = backupManager.createBackupJson()
        assertTrue(backupBase64.isNotBlank())

        // Restore backup string
        val success = backupManager.restoreBackup(backupBase64)
        assertTrue(success)
    }

    @Test
    fun pinManager_verifiesDecoyPinCorrectly() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val pinManager = com.example.core.security.PinManager(context)

        pinManager.setPin("1234")
        pinManager.setDecoyPin("9999")

        // Regular PIN -> Success
        val normalResult = pinManager.verifyPin("1234")
        assertTrue(normalResult is com.example.core.security.PinVerificationResult.Success)

        // Decoy PIN -> Decoy
        val decoyResult = pinManager.verifyPin("9999")
        assertTrue(decoyResult is com.example.core.security.PinVerificationResult.Decoy)

        // Wrong PIN -> Failed
        val wrongResult = pinManager.verifyPin("0000")
        assertTrue(wrongResult is com.example.core.security.PinVerificationResult.Failed)

        pinManager.clearPin()
        pinManager.clearDecoyPin()
    }
}
