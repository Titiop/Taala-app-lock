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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DataLayerUnitTest {

    private lateinit var database: TaalaDatabase
    private lateinit var lockedAppDao: LockedAppDao
    private lateinit var profileDao: ProfileDao

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, TaalaDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        lockedAppDao = database.lockedAppDao()
        profileDao = database.profileDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun testLockAndUnlockApp() = runBlocking {
        val app = LockedAppEntity(
            packageName = "com.example.bank",
            appLabel = "Test Bank",
            isLocked = true,
            profileId = "finance"
        )
        lockedAppDao.insertOrUpdate(app)

        val retrieved = lockedAppDao.getLockedAppSync("com.example.bank")
        assertTrue(retrieved != null)
        assertTrue(retrieved!!.isLocked)
        assertEquals("finance", retrieved.profileId)

        // Toggle to unlocked
        lockedAppDao.setLockState("com.example.bank", false)
        val updated = lockedAppDao.getLockedAppSync("com.example.bank")
        assertTrue(updated != null)
        assertFalse(updated!!.isLocked)

        val count = lockedAppDao.getLockedCount().first()
        assertEquals(0, count)
    }

    @Test
    fun testDefaultProfiles() = runBlocking {
        val profiles = listOf(
            ProfileEntity(
                id = "finance",
                name = "Finance",
                description = "Banking & Wallets",
                colorHex = "#10B981",
                iconName = "savings",
                relockDelaySeconds = 0,
                isEnabled = true,
                isBuiltIn = true
            ),
            ProfileEntity(
                id = "social",
                name = "Social",
                description = "Chat & Social",
                colorHex = "#3B82F6",
                iconName = "chat",
                relockDelaySeconds = 30,
                isEnabled = true,
                isBuiltIn = true
            )
        )
        profileDao.insertDefaultProfiles(profiles)

        val list = profileDao.getAllProfiles().first()
        assertEquals(2, list.size)
    }
}
