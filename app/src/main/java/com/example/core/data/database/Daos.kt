package com.example.core.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.core.data.model.IntruderEntity
import com.example.core.data.model.LockedAppEntity
import com.example.core.data.model.ProfileEntity
import com.example.core.data.model.ScheduleEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface LockedAppDao {
    @Query("SELECT * FROM locked_apps ORDER BY appLabel ASC")
    fun getAllLockedApps(): Flow<List<LockedAppEntity>>

    @Query("SELECT * FROM locked_apps WHERE isLocked = 1")
    fun getOnlyLockedApps(): Flow<List<LockedAppEntity>>

    @Query("SELECT COUNT(*) FROM locked_apps WHERE isLocked = 1")
    fun getLockedCount(): Flow<Int>

    @Query("SELECT * FROM locked_apps WHERE packageName = :packageName LIMIT 1")
    fun getLockedApp(packageName: String): Flow<LockedAppEntity?>

    @Query("SELECT * FROM locked_apps WHERE packageName = :packageName LIMIT 1")
    suspend fun getLockedAppSync(packageName: String): LockedAppEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(app: LockedAppEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(apps: List<LockedAppEntity>)

    @Query("UPDATE locked_apps SET isLocked = :isLocked, lockedAt = :timestamp WHERE packageName = :packageName")
    suspend fun setLockState(packageName: String, isLocked: Boolean, timestamp: Long = System.currentTimeMillis())

    @Query("DELETE FROM locked_apps WHERE packageName = :packageName")
    suspend fun delete(packageName: String)

    @Query("SELECT * FROM locked_apps WHERE profileId = :profileId")
    fun getAppsForProfile(profileId: String): Flow<List<LockedAppEntity>>

    @Query("UPDATE locked_apps SET isLocked = :isLocked WHERE profileId = :profileId")
    suspend fun setAllProfileAppsLocked(profileId: String, isLocked: Boolean)

    @Query("UPDATE locked_apps SET profileId = :profileId WHERE packageName = :packageName")
    suspend fun setAppProfile(packageName: String, profileId: String)

    @Query("UPDATE locked_apps SET customRelockDelaySeconds = :delaySeconds WHERE packageName = :packageName")
    suspend fun setCustomRelockDelay(packageName: String, delaySeconds: Int?)

    @Query("UPDATE locked_apps SET isNotificationShieldEnabled = :enabled WHERE packageName = :packageName")
    suspend fun setNotificationShield(packageName: String, enabled: Boolean)

    @Query("DELETE FROM locked_apps WHERE packageName NOT IN (:existingPackages)")
    suspend fun deleteOrphanedPackages(existingPackages: List<String>)
}

@Dao
interface ProfileDao {
    @Query("SELECT * FROM profiles ORDER BY isBuiltIn DESC, name ASC")
    fun getAllProfiles(): Flow<List<ProfileEntity>>

    @Query("SELECT * FROM profiles WHERE id = :id LIMIT 1")
    fun getProfileById(id: String): Flow<ProfileEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(profile: ProfileEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertDefaultProfiles(profiles: List<ProfileEntity>)

    @Update
    suspend fun update(profile: ProfileEntity)

    @Query("UPDATE profiles SET isEnabled = :isEnabled WHERE id = :id")
    suspend fun setProfileEnabled(id: String, isEnabled: Boolean)

    @Query("DELETE FROM profiles WHERE id = :id AND isBuiltIn = 0")
    suspend fun deleteCustomProfile(id: String)
}

@Dao
interface ScheduleDao {
    @Query("SELECT * FROM schedules ORDER BY title ASC")
    fun getAllSchedules(): Flow<List<ScheduleEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(schedule: ScheduleEntity)

    @Query("UPDATE schedules SET isEnabled = :isEnabled WHERE id = :id")
    suspend fun setScheduleEnabled(id: String, isEnabled: Boolean)

    @Query("DELETE FROM schedules WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface IntruderDao {
    @Query("SELECT * FROM intruder_records ORDER BY timestamp DESC")
    fun getAllRecords(): Flow<List<IntruderEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(record: IntruderEntity)

    @Query("DELETE FROM intruder_records WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM intruder_records")
    suspend fun deleteAll()
}
