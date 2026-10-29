package com.example.feature.lock

import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper

class LockLauncher(
    private val context: Context,
    private val coverOverlay: CoverOverlay
) {
    private val mainHandler = Handler(Looper.getMainLooper())

    fun triggerLock(packageName: String, appLabel: String, icon: Drawable?) {
        // 1. Immediately display opaque cover window (<100ms)
        coverOverlay.show(packageName, appLabel, icon)

        // 2. Launch full-screen LockScreenActivity
        val intent = Intent(context, LockScreenActivity::class.java).apply {
            putExtra(LockScreenActivity.EXTRA_PACKAGE_NAME, packageName)
            putExtra(LockScreenActivity.EXTRA_APP_LABEL, appLabel)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_NO_ANIMATION or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        try {
            context.startActivity(intent)
        } catch (_: Exception) {
            // Context launch fallback
        }

        // 3. Gracefully hide the immediate cover after activity is given enough time to render
        mainHandler.postDelayed({
            coverOverlay.hide()
        }, 350L)
    }

    fun dismissCover() {
        coverOverlay.hide()
    }
}
