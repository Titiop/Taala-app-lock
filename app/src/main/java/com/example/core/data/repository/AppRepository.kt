package com.example.core.data.repository

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.util.LruCache
import com.example.core.data.database.LockedAppDao
import com.example.core.data.model.LockedAppEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

class AppRepository(
    private val context: Context,
    private val lockedAppDao: LockedAppDao
) {
    private val packageManager: PackageManager = context.packageManager
    private val iconCache = object : LruCache<String, Drawable>(150) {}

    // In-memory cache of basic installed apps list
    private var cachedInstalledApps: List<RawAppInfo>? = null

    private data class RawAppInfo(
        val packageName: String,
        val appLabel: String,
        val isSystemApp: Boolean,
        val isLockable: Boolean,
        val lockDisabledReason: String?,
        val category: AppCategory,
        val isSuggested: Boolean,
        val installTime: Long
    )

    fun getAppsStream(): Flow<List<AppItem>> {
        return combine(
            flow { emit(loadInstalledApps()) },
            lockedAppDao.getAllLockedApps()
        ) { installedList, lockedEntities ->
            val lockedMap = lockedEntities.associateBy { it.packageName }

            installedList.map { raw ->
                val lockedEntity = lockedMap[raw.packageName]
                val isLocked = lockedEntity?.isLocked ?: false
                val profileId = lockedEntity?.profileId ?: "default"

                AppItem(
                    packageName = raw.packageName,
                    appLabel = raw.appLabel,
                    isLocked = if (raw.isLockable) isLocked else false,
                    profileId = profileId,
                    customRelockDelaySeconds = lockedEntity?.customRelockDelaySeconds,
                    isNotificationShieldEnabled = lockedEntity?.isNotificationShieldEnabled ?: false,
                    isSystemApp = raw.isSystemApp,
                    isSuggested = raw.isSuggested,
                    category = raw.category,
                    isLockable = raw.isLockable,
                    lockDisabledReason = raw.lockDisabledReason,
                    installTime = raw.installTime,
                    icon = getAppIcon(raw.packageName)
                )
            }
        }.flowOn(Dispatchers.IO)
    }

    fun getLockedCount(): Flow<Int> = lockedAppDao.getLockedCount()

    suspend fun toggleAppLock(app: AppItem, newLockState: Boolean) = withContext(Dispatchers.IO) {
        if (!app.isLockable) return@withContext

        val existing = lockedAppDao.getLockedAppSync(app.packageName)
        if (existing == null) {
            lockedAppDao.insertOrUpdate(
                LockedAppEntity(
                    packageName = app.packageName,
                    appLabel = app.appLabel,
                    isLocked = newLockState,
                    profileId = app.profileId,
                    isSystemApp = app.isSystemApp
                )
            )
        } else {
            lockedAppDao.setLockState(app.packageName, newLockState)
        }
    }

    suspend fun setAppProfile(packageName: String, profileId: String) = withContext(Dispatchers.IO) {
        val existing = lockedAppDao.getLockedAppSync(packageName)
        if (existing != null) {
            lockedAppDao.setAppProfile(packageName, profileId)
        }
    }

    suspend fun setCustomRelockDelay(packageName: String, delaySeconds: Int?) = withContext(Dispatchers.IO) {
        val existing = lockedAppDao.getLockedAppSync(packageName)
        if (existing != null) {
            lockedAppDao.setCustomRelockDelay(packageName, delaySeconds)
        }
    }

    suspend fun setNotificationShield(packageName: String, enabled: Boolean) = withContext(Dispatchers.IO) {
        val existing = lockedAppDao.getLockedAppSync(packageName)
        if (existing != null) {
            lockedAppDao.setNotificationShield(packageName, enabled)
        }
    }

    suspend fun setAllProfileAppsLocked(profileId: String, isLocked: Boolean) = withContext(Dispatchers.IO) {
        lockedAppDao.setAllProfileAppsLocked(profileId, isLocked)
    }

    fun getAppIcon(packageName: String): Drawable? {
        synchronized(iconCache) {
            iconCache.get(packageName)?.let { return it }
        }
        return try {
            val icon = packageManager.getApplicationIcon(packageName)
            synchronized(iconCache) {
                iconCache.put(packageName, icon)
            }
            icon
        } catch (_: Exception) {
            null
        }
    }

    private fun loadInstalledApps(): List<RawAppInfo> {
        cachedInstalledApps?.let { return it }

        val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val resolveInfos = packageManager.queryIntentActivities(mainIntent, 0)

        // Identify default home launcher so it's protected from locking (Section 5.6)
        val homeIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
        }
        val defaultHomeResolve = packageManager.resolveActivity(homeIntent, PackageManager.MATCH_DEFAULT_ONLY)
        val defaultHomePackage = defaultHomeResolve?.activityInfo?.packageName
        val ownPackageName = context.packageName

        val resultList = mutableListOf<RawAppInfo>()

        for (resolveInfo in resolveInfos) {
            val activityInfo = resolveInfo.activityInfo ?: continue
            val pkg = activityInfo.packageName

            // Skip Taala itself from normal target app list
            if (pkg == ownPackageName) continue

            val label = try {
                resolveInfo.loadLabel(packageManager).toString()
            } catch (_: Exception) {
                activityInfo.name
            }

            val appInfo = activityInfo.applicationInfo
            val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0

            val installTime = try {
                packageManager.getPackageInfo(pkg, 0).firstInstallTime
            } catch (_: Exception) {
                0L
            }

            val isLauncher = (pkg == defaultHomePackage)
            val isEmergencyDialer = pkg.contains("telecom") || pkg.contains("emergency")

            val isLockable = !isLauncher && !isEmergencyDialer
            val lockDisabledReason = when {
                isLauncher -> "Home launcher cannot be locked (System safety)"
                isEmergencyDialer -> "Emergency dialer cannot be locked"
                else -> null
            }

            val category = detectAppCategory(pkg, label)
            val isSuggested = category != AppCategory.GENERAL

            resultList.add(
                RawAppInfo(
                    packageName = pkg,
                    appLabel = label,
                    isSystemApp = isSystem,
                    isLockable = isLockable,
                    lockDisabledReason = lockDisabledReason,
                    category = category,
                    isSuggested = isSuggested,
                    installTime = installTime
                )
            )
        }

        // Sort by label initially
        val sorted = resultList.sortedBy { it.appLabel.lowercase() }
        cachedInstalledApps = sorted
        return sorted
    }

    private fun detectAppCategory(packageName: String, label: String): AppCategory {
        val lowerPkg = packageName.lowercase()
        val lowerLabel = label.lowercase()

        return when {
            // Banking & Wallets
            lowerPkg.contains("bank") || lowerPkg.contains("pay") || lowerPkg.contains("wallet") ||
                    lowerPkg.contains("upi") || lowerPkg.contains("crypto") || lowerPkg.contains("finance") ||
                    lowerLabel.contains("bank") || lowerLabel.contains("pay") || lowerLabel.contains("wallet") ->
                AppCategory.BANKING

            // Messengers & Social
            lowerPkg.contains("whatsapp") || lowerPkg.contains("telegram") || lowerPkg.contains("signal") ||
                    lowerPkg.contains("messenger") || lowerPkg.contains("instagram") || lowerPkg.contains("chat") ||
                    lowerPkg.contains("twitter") || lowerPkg.contains("snapchat") || lowerPkg.contains("reddit") ->
                AppCategory.MESSAGING

            // Gallery & Photos
            lowerPkg.contains("gallery") || lowerPkg.contains("photo") || lowerPkg.contains("camera") ||
                    lowerLabel.contains("gallery") || lowerLabel.contains("photos") ->
                AppCategory.GALLERY

            // Settings & System Store
            lowerPkg.contains("settings") || lowerPkg.contains("vending") || lowerPkg.contains("packageinstaller") ||
                    lowerLabel.contains("settings") || lowerLabel.contains("play store") ->
                AppCategory.SETTINGS

            // Browsers
            lowerPkg.contains("chrome") || lowerPkg.contains("browser") || lowerPkg.contains("firefox") ||
                    lowerPkg.contains("edge") || lowerPkg.contains("opera") || lowerPkg.contains("brave") ->
                AppCategory.BROWSER

            // Files
            lowerPkg.contains("file") || lowerPkg.contains("document") || lowerPkg.contains("explorer") ||
                    lowerLabel.contains("files") || lowerLabel.contains("drive") ->
                AppCategory.FILES

            else -> AppCategory.GENERAL
        }
    }
}
