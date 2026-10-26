package com.example.feature.lock

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.MediaStore
import android.view.inputmethod.InputMethodManager

class ForegroundRules(private val context: Context) {

    private val packageManager: PackageManager = context.packageManager
    private val ownPackageName: String = context.packageName

    // Cache helper app packages
    @Volatile
    private var helperPackagesCache: Set<String>? = null

    // Cache home launcher packages
    @Volatile
    private var homePackagesCache: Set<String>? = null

    fun isSystemExempt(packageName: String): Boolean {
        if (packageName.isBlank()) return true
        if (packageName == ownPackageName) return true

        // Android System UI and core frameworks
        if (packageName == "com.android.systemui" ||
            packageName == "android" ||
            packageName == "com.google.android.permissioncontroller" ||
            packageName.startsWith("com.android.server.telecom")
        ) {
            return true
        }

        // Active Home Launcher and Recents
        if (getHomeLauncherPackages().contains(packageName)) {
            return true
        }

        // Active Keyboards / Input Method Editors
        if (isInputMethod(packageName)) {
            return true
        }

        return false
    }

    fun isHelperApp(packageName: String): Boolean {
        return getHelperPackages().contains(packageName)
    }

    fun getHomeLauncherPackages(): Set<String> {
        homePackagesCache?.let { return it }

        val homeIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
        }
        val resolveInfos = packageManager.queryIntentActivities(homeIntent, PackageManager.MATCH_DEFAULT_ONLY)
        val homeSet = resolveInfos.mapNotNull { it.activityInfo?.packageName }.toMutableSet()

        // Also query all launcher category resolves in case of fallback launchers
        val allHomeResolves = packageManager.queryIntentActivities(homeIntent, 0)
        homeSet.addAll(allHomeResolves.mapNotNull { it.activityInfo?.packageName })

        homePackagesCache = homeSet
        return homeSet
    }

    /**
     * Section 5.4: Detect picker/camera/file apps dynamically by querying
     * PackageManager for Intent actions rather than hardcoding.
     */
    fun getHelperPackages(): Set<String> {
        helperPackagesCache?.let { return it }

        val helpers = mutableSetOf<String>()

        // 1. Image Capture / Camera
        val cameraIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        packageManager.queryIntentActivities(cameraIntent, 0).forEach {
            it.activityInfo?.packageName?.let { pkg -> helpers.add(pkg) }
        }

        // 2. Content Pickers (Photos / Files)
        val pickIntent = Intent(Intent.ACTION_PICK)
        packageManager.queryIntentActivities(pickIntent, 0).forEach {
            it.activityInfo?.packageName?.let { pkg -> helpers.add(pkg) }
        }

        val getContentIntent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "*/*"
        }
        packageManager.queryIntentActivities(getContentIntent, 0).forEach {
            it.activityInfo?.packageName?.let { pkg -> helpers.add(pkg) }
        }

        // 3. Document Opening
        val docIntent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            type = "*/*"
        }
        packageManager.queryIntentActivities(docIntent, 0).forEach {
            it.activityInfo?.packageName?.let { pkg -> helpers.add(pkg) }
        }

        helperPackagesCache = helpers
        return helpers
    }

    private fun isInputMethod(packageName: String): Boolean {
        return try {
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            val enabledMethods = imm?.enabledInputMethodList ?: emptyList()
            enabledMethods.any { it.packageName == packageName }
        } catch (_: Exception) {
            false
        }
    }
}
