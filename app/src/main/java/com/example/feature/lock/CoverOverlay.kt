package com.example.feature.lock

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

class CoverOverlay(private val context: Context) {

    private val windowManager: WindowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val mainHandler = Handler(Looper.getMainLooper())

    private var overlayView: View? = null
    @Volatile
    private var isDisplayed = false

    fun isShowing(): Boolean = isDisplayed

    fun show(packageName: String, appLabel: String, icon: Drawable?) {
        mainHandler.post {
            if (isDisplayed) return@post

            val layout = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setBackgroundColor(Color.parseColor("#0B0E17")) // Opaque dark branded background

                // App Icon
                if (icon != null) {
                    val iconView = ImageView(context).apply {
                        setImageDrawable(icon)
                        val size = (72 * context.resources.displayMetrics.density).toInt()
                        layoutParams = LinearLayout.LayoutParams(size, size).apply {
                            bottomMargin = (20 * context.resources.displayMetrics.density).toInt()
                        }
                    }
                    addView(iconView)
                }

                // App Label
                val labelView = TextView(context).apply {
                    text = appLabel
                    setTextColor(Color.WHITE)
                    textSize = 22f
                    typeface = Typeface.DEFAULT_BOLD
                    gravity = Gravity.CENTER
                }
                addView(labelView)

                // Subtitle
                val subtitleView = TextView(context).apply {
                    text = "Taala Security Lock"
                    setTextColor(Color.parseColor("#00D2B4")) // Soft Teal Accent
                    textSize = 14f
                    gravity = Gravity.CENTER
                    val topMargin = (8 * context.resources.displayMetrics.density).toInt()
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        this.topMargin = topMargin
                    }
                }
                addView(subtitleView)
            }

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_SECURE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_FULLSCREEN,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.CENTER
            }

            try {
                windowManager.addView(layout, params)
                overlayView = layout
                isDisplayed = true
            } catch (_: Exception) {
                // Ignore if unable to attach window
            }
        }
    }

    fun hide() {
        mainHandler.post {
            if (!isDisplayed || overlayView == null) return@post
            try {
                windowManager.removeView(overlayView)
            } catch (_: Exception) {
                // Ignore if already removed
            } finally {
                overlayView = null
                isDisplayed = false
            }
        }
    }
}
