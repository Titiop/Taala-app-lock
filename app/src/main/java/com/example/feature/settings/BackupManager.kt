package com.example.feature.settings

import android.content.Context
import com.example.core.data.database.TaalaDatabase
import com.example.core.data.model.LockedAppEntity
import com.example.core.data.model.ProfileEntity
import com.example.core.data.model.ScheduleEntity
import com.example.core.security.KeystoreHelper
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject

class BackupManager(private val context: Context) {

    private val database = TaalaDatabase.getInstance(context)

    suspend fun createBackupJson(): String {
        val root = JSONObject()
        root.put("version", 1)
        root.put("timestamp", System.currentTimeMillis())

        // Export Locked Apps
        val lockedApps = database.lockedAppDao().getAllLockedApps().first()
        val appsArray = JSONArray()
        for (app in lockedApps) {
            val appObj = JSONObject().apply {
                put("packageName", app.packageName)
                put("appLabel", app.appLabel)
                put("isLocked", app.isLocked)
                put("profileId", app.profileId)
                put("customRelockDelaySeconds", app.customRelockDelaySeconds ?: -1)
                put("isNotificationShieldEnabled", app.isNotificationShieldEnabled)
                put("isSystemApp", app.isSystemApp)
            }
            appsArray.put(appObj)
        }
        root.put("lockedApps", appsArray)

        // Export Profiles
        val profiles = database.profileDao().getAllProfiles().first()
        val profilesArray = JSONArray()
        for (profile in profiles) {
            val profObj = JSONObject().apply {
                put("id", profile.id)
                put("name", profile.name)
                put("description", profile.description)
                put("colorHex", profile.colorHex)
                put("iconName", profile.iconName)
                put("relockDelaySeconds", profile.relockDelaySeconds)
                put("isEnabled", profile.isEnabled)
                put("isBuiltIn", profile.isBuiltIn)
            }
            profilesArray.put(profObj)
        }
        root.put("profiles", profilesArray)

        // Export Schedules
        val schedules = database.scheduleDao().getAllSchedules().first()
        val schedulesArray = JSONArray()
        for (schedule in schedules) {
            val schedObj = JSONObject().apply {
                put("id", schedule.id)
                put("profileId", schedule.profileId)
                put("title", schedule.title)
                put("daysMask", schedule.daysMask)
                put("startHour", schedule.startHour)
                put("startMinute", schedule.startMinute)
                put("endHour", schedule.endHour)
                put("endMinute", schedule.endMinute)
                put("isEnabled", schedule.isEnabled)
            }
            schedulesArray.put(schedObj)
        }
        root.put("schedules", schedulesArray)

        val rawJson = root.toString(2)
        // Encrypt with KeystoreHelper singleton (returns Base64 string directly)
        return KeystoreHelper.encrypt(rawJson.toByteArray(Charsets.UTF_8))
    }

    suspend fun restoreBackup(encryptedBase64: String): Boolean {
        return try {
            val decryptedBytes = KeystoreHelper.decrypt(encryptedBase64.trim()) ?: return false
            val jsonString = String(decryptedBytes, Charsets.UTF_8)
            val root = JSONObject(jsonString)

            // Restore Profiles
            if (root.has("profiles")) {
                val profilesArr = root.getJSONArray("profiles")
                for (i in 0 until profilesArr.length()) {
                    val obj = profilesArr.getJSONObject(i)
                    val profile = ProfileEntity(
                        id = obj.getString("id"),
                        name = obj.getString("name"),
                        description = obj.optString("description", ""),
                        colorHex = obj.getString("colorHex"),
                        iconName = obj.optString("iconName", "GroupWork"),
                        relockDelaySeconds = obj.getInt("relockDelaySeconds"),
                        isEnabled = obj.getBoolean("isEnabled"),
                        isBuiltIn = obj.optBoolean("isBuiltIn", false)
                    )
                    database.profileDao().insert(profile)
                }
            }

            // Restore Locked Apps
            if (root.has("lockedApps")) {
                val appsArr = root.getJSONArray("lockedApps")
                val restoredApps = mutableListOf<LockedAppEntity>()
                for (i in 0 until appsArr.length()) {
                    val obj = appsArr.getJSONObject(i)
                    val delay = obj.getInt("customRelockDelaySeconds")
                    val app = LockedAppEntity(
                        packageName = obj.getString("packageName"),
                        appLabel = obj.getString("appLabel"),
                        isLocked = obj.getBoolean("isLocked"),
                        profileId = obj.optString("profileId", "default"),
                        customRelockDelaySeconds = if (delay >= 0) delay else null,
                        isNotificationShieldEnabled = obj.optBoolean("isNotificationShieldEnabled", false),
                        isSystemApp = obj.optBoolean("isSystemApp", false)
                    )
                    restoredApps.add(app)
                }
                database.lockedAppDao().insertAll(restoredApps)
            }

            // Restore Schedules
            if (root.has("schedules")) {
                val schedArr = root.getJSONArray("schedules")
                for (i in 0 until schedArr.length()) {
                    val obj = schedArr.getJSONObject(i)
                    val schedule = ScheduleEntity(
                        id = obj.getString("id"),
                        profileId = obj.optString("profileId", "default"),
                        title = obj.getString("title"),
                        daysMask = obj.optInt("daysMask", 127),
                        startHour = obj.getInt("startHour"),
                        startMinute = obj.getInt("startMinute"),
                        endHour = obj.getInt("endHour"),
                        endMinute = obj.getInt("endMinute"),
                        isEnabled = obj.getBoolean("isEnabled")
                    )
                    database.scheduleDao().insertOrUpdate(schedule)
                }
            }

            true
        } catch (_: Exception) {
            false
        }
    }
}
