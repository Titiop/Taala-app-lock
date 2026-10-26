package com.example

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.fragment.app.FragmentActivity
import com.example.core.security.AuthManager
import com.example.core.security.PinManager
import com.example.ui.navigation.TaalaApp
import com.example.ui.theme.TaalaTheme
import com.example.ui.theme.ThemeMode

class MainActivity : FragmentActivity() {

    private lateinit var authManager: AuthManager
    private lateinit var pinManager: PinManager

    override fun onCreate(savedInstanceState: Bundle?) {
        // Android 12+ SplashScreen API (Section 10 specification)
        val splashScreen = installSplashScreen()

        super.onCreate(savedInstanceState)

        // Security checklist: FLAG_SECURE on every Taala window to prevent screenshots & recents leaks
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )

        enableEdgeToEdge()

        authManager = AuthManager(this)
        pinManager = PinManager(this)

        setContent {
            var themeMode by remember { mutableStateOf(ThemeMode.SYSTEM) }

            TaalaTheme(themeMode = themeMode) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    TaalaApp(
                        currentThemeMode = themeMode,
                        onThemeModeChange = { newMode -> themeMode = newMode }
                    )
                }
            }
        }
    }
}
