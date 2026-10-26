package com.example

import android.content.Context
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import com.example.core.security.PermissionManager
import com.example.feature.setup.StepStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SetupWizardUnitTest {

    private lateinit var context: Context
    private lateinit var permissionManager: PermissionManager

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        permissionManager = PermissionManager(context)
    }

    @Test
    fun testSettingsIntentGeneration() {
        val accIntent = permissionManager.openAccessibilitySettings()
        assertEquals(Settings.ACTION_ACCESSIBILITY_SETTINGS, accIntent.action)

        val secIntent = permissionManager.openSecuritySettings()
        assertEquals(Settings.ACTION_SECURITY_SETTINGS, secIntent.action)

        val appInfoIntent = permissionManager.openAppInfoForRestrictedSettings()
        assertEquals(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, appInfoIntent.action)
        assertEquals("package:${context.packageName}", appInfoIntent.dataString)

        val usageIntent = permissionManager.openUsageAccessSettings()
        assertEquals(Settings.ACTION_USAGE_ACCESS_SETTINGS, usageIntent.action)
    }

    @Test
    fun testStepStatusValues() {
        val values = StepStatus.entries
        assertTrue(values.contains(StepStatus.DONE))
        assertTrue(values.contains(StepStatus.NEEDED))
        assertTrue(values.contains(StepStatus.OPTIONAL))
    }
}
