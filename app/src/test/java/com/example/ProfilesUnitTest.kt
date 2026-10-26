package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.core.data.database.LockedAppDao
import com.example.core.data.database.ProfileDao
import com.example.core.data.database.TaalaDatabase
import com.example.core.data.model.LockedAppEntity
import com.example.core.data.model.ProfileEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ProfilesUnitTest {

    private lateinit var database: TaalaDatabase
    private lateinit var profileDao: ProfileDao
    private lateinit var lockedAppDao: LockedAppDao

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, TaalaDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        profileDao = database.profileDao()
        lockedAppDao = database.lockedAppDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun testBuiltInAndCustomProfiles() = runBlocking {
        // Built in profiles
        profileDao.insert(
            ProfileEntity(
                id = "finance",
                name = "Finance",
                description = "Banking & Wallets",
                colorHex = "#10B981",
                iconName = "savings",
                relockDelaySeconds = 0,
                isEnabled = true,
                isBuiltIn = true
            )
        )

        // Custom profile
        profileDao.insert(
            ProfileEntity(
                id = "custom_games",
                name = "Games",
                description = "Gaming apps",
                colorHex = "#F59E0B",
                iconName = "game",
                relockDelaySeconds = 300,
                isEnabled = true,
                isBuiltIn = false
            )
        )

        var all = profileDao.getAllProfiles().first()
        assertEquals(2, all.size)

        // Attempt to delete built-in profile: should NOT be deleted
        profileDao.deleteCustomProfile("finance")
        all = profileDao.getAllProfiles().first()
        assertEquals(2, all.size)

        // Delete custom profile: should be deleted
        profileDao.deleteCustomProfile("custom_games")
        all = profileDao.getAllProfiles().first()
        assertEquals(1, all.size)
        assertEquals("finance", all[0].id)
    }

    @Test
    fun testBulkProfileLockAndUnlock() = runBlocking {
        // 2 apps in finance profile
        lockedAppDao.insertOrUpdate(
            LockedAppEntity(
                packageName = "com.bank.one",
                appLabel = "Bank One",
                isLocked = true,
                profileId = "finance"
            )
        )
        lockedAppDao.insertOrUpdate(
            LockedAppEntity(
                packageName = "com.bank.two",
                appLabel = "Bank Two",
                isLocked = true,
                profileId = "finance"
            )
        )

        val initialFinanceApps = lockedAppDao.getAppsForProfile("finance").first()
        assertEquals(2, initialFinanceApps.size)
        assertTrue(initialFinanceApps.all { it.isLocked })

        // Master switch toggle: unlock all apps in finance profile at once
        lockedAppDao.setAllProfileAppsLocked("finance", isLocked = false)
        val updatedFinanceApps = lockedAppDao.getAppsForProfile("finance").first()
        assertTrue(updatedFinanceApps.all { !it.isLocked })

        // Master switch toggle: lock all apps in finance profile at once
        lockedAppDao.setAllProfileAppsLocked("finance", isLocked = true)
        val relockedFinanceApps = lockedAppDao.getAppsForProfile("finance").first()
        assertTrue(relockedFinanceApps.all { it.isLocked })
    }

    @Test
    fun testPerAppOverrides() = runBlocking {
        lockedAppDao.insertOrUpdate(
            LockedAppEntity(
                packageName = "com.chat.app",
                appLabel = "Chat App",
                isLocked = true,
                profileId = "social"
            )
        )

        // Assign to finance
        lockedAppDao.setAppProfile("com.chat.app", "finance")
        var app = lockedAppDao.getLockedAppSync("com.chat.app")
        assertNotNull(app)
        assertEquals("finance", app!!.profileId)

        // Set custom re-lock delay override (60s)
        lockedAppDao.setCustomRelockDelay("com.chat.app", 60)
        app = lockedAppDao.getLockedAppSync("com.chat.app")
        assertEquals(60, app!!.customRelockDelaySeconds)

        // Enable notification shield
        lockedAppDao.setNotificationShield("com.chat.app", true)
        app = lockedAppDao.getLockedAppSync("com.chat.app")
        assertTrue(app!!.isNotificationShieldEnabled)
    }
}
