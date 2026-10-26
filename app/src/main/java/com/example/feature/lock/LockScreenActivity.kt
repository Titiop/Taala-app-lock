package com.example.feature.lock

import android.content.Intent
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.WindowManager
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.biometric.BiometricPrompt
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Pin
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import com.example.core.data.database.TaalaDatabase
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.runtime.collectAsState
import com.example.core.data.datastore.AppSettingsDataStore
import com.example.core.data.repository.AppRepository
import com.example.core.security.AuthManager
import com.example.core.security.PinManager
import com.example.core.security.PinVerificationResult
import com.example.feature.intruder.IntruderManager
import com.example.ui.components.AppIconImage
import com.example.ui.theme.StatusAttentionRed
import com.example.ui.theme.StatusProtectedGreen
import com.example.ui.theme.TaalaTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

class LockScreenActivity : FragmentActivity() {

    companion object {
        const val EXTRA_PACKAGE_NAME = "extra_package_name"
        const val EXTRA_APP_LABEL = "extra_app_label"
    }

    private lateinit var authManager: AuthManager
    private lateinit var pinManager: PinManager
    private lateinit var appRepository: AppRepository
    private lateinit var intruderManager: IntruderManager
    private lateinit var dataStore: AppSettingsDataStore

    private var targetPackage: String = ""
    private var targetLabel: String = ""
    private var targetIcon: Drawable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Security hardening checklist: FLAG_SECURE on every window
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )

        // Hide overlay windows to prevent tapjacking (Section 9)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            window.setHideOverlayWindows(true)
        }

        enableEdgeToEdge()

        authManager = AuthManager(this)
        pinManager = PinManager(this)
        intruderManager = IntruderManager(this)
        dataStore = AppSettingsDataStore(this)
        val database = TaalaDatabase.getInstance(this)
        appRepository = AppRepository(this, database.lockedAppDao())

        targetPackage = intent.getStringExtra(EXTRA_PACKAGE_NAME) ?: ""
        targetLabel = intent.getStringExtra(EXTRA_APP_LABEL) ?: "Application"
        if (targetPackage.isNotEmpty()) {
            targetIcon = appRepository.getAppIcon(targetPackage)
        }

        // Section 5.6: Back press sends the user Home, never to the locked app!
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                sendUserHome()
            }
        })

        setContent {
            val disguiseModeEnabled by dataStore.disguiseModeEnabled.collectAsState(initial = false)

            TaalaTheme {
                LockScreenContent(
                    appLabel = targetLabel,
                    appIcon = targetIcon,
                    packageName = targetPackage,
                    pinManager = pinManager,
                    isDisguiseModeEnabled = disguiseModeEnabled,
                    onTriggerBiometric = { promptBiometricAuth() },
                    onUnlockSuccess = { handleUnlockSuccess() },
                    onUnlockFailed = { intruderManager.onUnlockFailed(targetPackage, targetLabel) },
                    onNavigateHome = { sendUserHome() }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Auto trigger biometric prompt
        promptBiometricAuth()
    }

    private fun promptBiometricAuth() {
        if (!authManager.isAuthenticationAvailable()) return

        authManager.showBiometricPrompt(
            activity = this,
            title = "Unlock $targetLabel",
            subtitle = "Touch sensor or confirm lock",
            onSuccess = {
                handleUnlockSuccess()
            },
            onError = { _, _ ->
                // User dismissed or error occurred; keep lock screen active
            },
            onFailed = {
                // Biometric mismatch; trigger error haptic and notify intruder manager
                window.decorView.performHapticFeedback(HapticFeedbackConstants.REJECT)
                intruderManager.onUnlockFailed(targetPackage, targetLabel)
            }
        )
    }

    private fun handleUnlockSuccess() {
        window.decorView.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
        intruderManager.onUnlockSuccess(targetPackage)
        if (targetPackage.isNotEmpty()) {
            TaalaAccessibilityService.sessionTracker.registerAppUnlocked(
                packageName = targetPackage,
                relockDelaySeconds = 30
            )
        }
        // Set result and finish lock screen to open the app
        setResult(RESULT_OK)
        finish()
    }

    private fun sendUserHome() {
        val homeIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(homeIntent)
        finish()
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LockScreenContent(
    appLabel: String,
    appIcon: Drawable?,
    packageName: String,
    pinManager: PinManager,
    isDisguiseModeEnabled: Boolean = false,
    onTriggerBiometric: () -> Unit,
    onUnlockSuccess: () -> Unit,
    onUnlockFailed: () -> Unit,
    onNavigateHome: () -> Unit
) {
    val view = LocalView.current
    val coroutineScope = rememberCoroutineScope()

    var isDisguised by remember(isDisguiseModeEnabled) { mutableStateOf(isDisguiseModeEnabled) }
    var showPinKeypad by remember { mutableStateOf(false) }
    var pinInput by remember { mutableStateOf("") }
    var pinError by remember { mutableStateOf<String?>(null) }
    var lockoutSeconds by remember { mutableStateOf(pinManager.getRemainingLockoutSeconds()) }
    var isSuccessAnim by remember { mutableStateOf(false) }

    // Shake animation for failure
    val shakeOffset = remember { Animatable(0f) }

    // Lockout countdown timer
    LaunchedEffect(lockoutSeconds) {
        if (lockoutSeconds > 0) {
            delay(1000L)
            lockoutSeconds = pinManager.getRemainingLockoutSeconds()
        }
    }

    if (isDisguised) {
        // Disguise Cover: Authentic Android Fake Crash Dialog
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.88f))
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth(),
                shadowElevation = 8.dp
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.Start
                ) {
                    Text(
                        text = "$appLabel keeps stopping",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = onNavigateHome) {
                            Text("App info")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .combinedClickable(
                                    onClick = onNavigateHome,
                                    onLongClick = {
                                        isDisguised = false
                                        view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                                        onTriggerBiometric()
                                    }
                                )
                        ) {
                            Text(
                                text = "Close app",
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                            )
                        }
                    }
                }
            }
        }
        return
    }

    val backgroundGradient = Brush.verticalGradient(
        colors = listOf(
            Color(0xFF0F172A),
            Color(0xFF070B14),
            Color(0xFF020408)
        )
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundGradient)
            .padding(24.dp)
            .testTag("lock_screen_container")
    ) {
        // Obscured tap protection
        view.filterTouchesWhenObscured = true

        Column(
            modifier = Modifier
                .fillMaxSize()
                .offset { IntOffset(shakeOffset.value.roundToInt(), 0) },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top App Bar Area (Close to Home button)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 28.dp),
                horizontalArrangement = Arrangement.Start
            ) {
                IconButton(
                    onClick = onNavigateHome,
                    modifier = Modifier.testTag("lock_screen_close_home")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Go Home",
                        tint = Color.White.copy(alpha = 0.7f)
                    )
                }
            }

            // Center: App Icon + App Label + Authentication State
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Spring entrance animation for app icon
                AnimatedVisibility(
                    visible = true,
                    enter = scaleIn(spring(dampingRatio = 0.65f, stiffness = 300f)) + fadeIn(tween(100))
                ) {
                    Surface(
                        modifier = Modifier.size(92.dp),
                        shape = RoundedCornerShape(26.dp),
                        color = Color.White.copy(alpha = 0.08f),
                        shadowElevation = 8.dp
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            AppIconImage(
                                drawable = appIcon,
                                contentDescription = appLabel,
                                size = 68.dp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = appLabel,
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    ),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(6.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Protected by Taala",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.65f)
                    )
                }

                Spacer(modifier = Modifier.height(32.dp))

                if (isSuccessAnim) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Success",
                        tint = StatusProtectedGreen,
                        modifier = Modifier.size(56.dp)
                    )
                } else if (!showPinKeypad) {
                    // Biometric primary trigger
                    Button(
                        onClick = onTriggerBiometric,
                        shape = RoundedCornerShape(20.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ),
                        modifier = Modifier
                            .fillMaxWidth(0.7f)
                            .height(52.dp)
                            .testTag("lock_screen_unlock_biometric")
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Fingerprint,
                                contentDescription = null,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Unlock",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    TextButton(
                        onClick = {
                            showPinKeypad = true
                            pinError = null
                        },
                        modifier = Modifier.testTag("lock_screen_use_pin_fallback")
                    ) {
                        Text(
                            text = "Use PIN",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    }
                } else {
                    // PIN Keypad View
                    PinKeypad(
                        pin = pinInput,
                        lockoutSeconds = lockoutSeconds,
                        errorMessage = pinError,
                        onDigitClick = { digit ->
                            if (lockoutSeconds == 0 && pinInput.length < 8) {
                                pinInput += digit
                                pinError = null
                            }
                        },
                        onDeleteClick = {
                            if (pinInput.isNotEmpty()) {
                                pinInput = pinInput.dropLast(1)
                                pinError = null
                            }
                        },
                        onSubmitClick = {
                            if (pinInput.length in 4..8) {
                                val result = pinManager.verifyPin(pinInput)
                                when (result) {
                                    is PinVerificationResult.Success -> {
                                        isSuccessAnim = true
                                        coroutineScope.launch {
                                            delay(300L)
                                            onUnlockSuccess()
                                        }
                                    }
                                    is PinVerificationResult.Decoy -> {
                                        // Decoy passcode: perform subtle haptic and navigate Home
                                        view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                                        coroutineScope.launch {
                                            delay(200L)
                                            onNavigateHome()
                                        }
                                    }
                                    is PinVerificationResult.LockedOut -> {
                                        onUnlockFailed()
                                        lockoutSeconds = result.remainingSeconds
                                        pinError = "Locked out for ${result.remainingSeconds}s"
                                        view.performHapticFeedback(HapticFeedbackConstants.REJECT)
                                        coroutineScope.launch {
                                            shakeOffset.animateTo(20f, tween(50))
                                            shakeOffset.animateTo(-20f, tween(50))
                                            shakeOffset.animateTo(10f, tween(50))
                                            shakeOffset.animateTo(0f, tween(50))
                                        }
                                    }
                                    is PinVerificationResult.Failed -> {
                                        onUnlockFailed()
                                        pinInput = ""
                                        pinError = if (result.lockoutSeconds > 0) {
                                            lockoutSeconds = result.lockoutSeconds
                                            "Too many failed attempts. Locked for ${result.lockoutSeconds}s"
                                        } else {
                                            "Incorrect PIN"
                                        }
                                        view.performHapticFeedback(HapticFeedbackConstants.REJECT)
                                        coroutineScope.launch {
                                            shakeOffset.animateTo(20f, tween(50))
                                            shakeOffset.animateTo(-20f, tween(50))
                                            shakeOffset.animateTo(10f, tween(50))
                                            shakeOffset.animateTo(0f, tween(50))
                                        }
                                    }
                                }
                            }
                        },
                        onBackToBiometric = {
                            showPinKeypad = false
                            pinInput = ""
                            pinError = null
                        }
                    )
                }
            }

            // Bottom Brand Mark (Section 10.7: small Taala mark)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Shield,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.35f),
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Taala",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = Color.White.copy(alpha = 0.35f),
                    letterSpacing = 1.5.sp
                )
            }
        }
    }
}

@Composable
private fun PinKeypad(
    pin: String,
    lockoutSeconds: Int,
    errorMessage: String?,
    onDigitClick: (String) -> Unit,
    onDeleteClick: () -> Unit,
    onSubmitClick: () -> Unit,
    onBackToBiometric: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        // PIN dot indicators
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(vertical = 12.dp)
        ) {
            repeat(6) { index ->
                val isFilled = index < pin.length
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .clip(CircleShape)
                        .background(
                            if (isFilled) MaterialTheme.colorScheme.primary
                            else Color.White.copy(alpha = 0.25f)
                        )
                )
            }
        }

        if (errorMessage != null) {
            Text(
                text = errorMessage,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                color = StatusAttentionRed,
                modifier = Modifier.padding(vertical = 4.dp)
            )
        } else if (lockoutSeconds > 0) {
            Text(
                text = "Try again in ${lockoutSeconds}s",
                style = MaterialTheme.typography.bodyMedium,
                color = StatusAttentionRed,
                modifier = Modifier.padding(vertical = 4.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Keypad grid
        val keys = listOf(
            listOf("1", "2", "3"),
            listOf("4", "5", "6"),
            listOf("7", "8", "9"),
            listOf("del", "0", "ok")
        )

        keys.forEach { row ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                modifier = Modifier.padding(vertical = 6.dp)
            ) {
                row.forEach { key ->
                    when (key) {
                        "del" -> {
                            IconButton(
                                onClick = onDeleteClick,
                                modifier = Modifier.size(56.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Backspace,
                                    contentDescription = "Delete",
                                    tint = Color.White.copy(alpha = 0.8f)
                                )
                            }
                        }
                        "ok" -> {
                            IconButton(
                                onClick = onSubmitClick,
                                modifier = Modifier.size(56.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = "Confirm",
                                    tint = MaterialTheme.colorScheme.tertiary
                                )
                            }
                        }
                        else -> {
                            Surface(
                                shape = CircleShape,
                                color = Color.White.copy(alpha = 0.08f),
                                modifier = Modifier
                                    .size(56.dp)
                                    .clickable(enabled = lockoutSeconds == 0) { onDigitClick(key) }
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = key,
                                        style = MaterialTheme.typography.titleLarge.copy(
                                            fontWeight = FontWeight.SemiBold,
                                            color = Color.White
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        TextButton(onClick = onBackToBiometric) {
            Text("Switch to Biometric", color = Color.White.copy(alpha = 0.7f))
        }
    }
}
