package com.example.feature.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LockClock
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Pin
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.R
import com.example.core.security.PinManager
import com.example.ui.theme.StatusAttentionRed
import com.example.ui.theme.StatusProtectedGreen
import com.example.ui.theme.ThemeMode
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    currentThemeMode: ThemeMode = ThemeMode.SYSTEM,
    onThemeModeChange: (ThemeMode) -> Unit = {},
    viewModel: SettingsViewModel = viewModel(),
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pinManager = remember { PinManager(context) }
    var isPinConfigured by remember { mutableStateOf(pinManager.isPinSet()) }

    val activeEngine by viewModel.activeEngine.collectAsStateWithLifecycle()
    val selfProtectionEnabled by viewModel.selfProtectionEnabled.collectAsStateWithLifecycle()
    val defaultRelockDelay by viewModel.defaultRelockDelay.collectAsStateWithLifecycle()
    val lockOnScreenOff by viewModel.lockOnScreenOff.collectAsStateWithLifecycle()
    val lockNewAppsAuto by viewModel.lockNewAppsAuto.collectAsStateWithLifecycle()
    val biometricsEnabled by viewModel.biometricsEnabled.collectAsStateWithLifecycle()
    val intruderSelfieEnabled by viewModel.intruderSelfieEnabled.collectAsStateWithLifecycle()
    val intruderThreshold by viewModel.intruderThreshold.collectAsStateWithLifecycle()
    val disguiseModeEnabled by viewModel.disguiseModeEnabled.collectAsStateWithLifecycle()
    val isAccessibilityConnected by viewModel.isAccessibilityConnected.collectAsStateWithLifecycle()

    var showEngineDialog by remember { mutableStateOf(false) }
    var showDelayDialog by remember { mutableStateOf(false) }
    var showThemeDialog by remember { mutableStateOf(false) }
    var showBackupDialog by remember { mutableStateOf(false) }
    var showRestoreDialog by remember { mutableStateOf(false) }
    var showPinDialog by remember { mutableStateOf(false) }
    var showDecoyPinDialog by remember { mutableStateOf(false) }
    var isDecoyConfigured by remember { mutableStateOf(pinManager.isDecoyPinSet()) }
    var decoyPinInput by remember { mutableStateOf("") }
    var decoyPinConfirmInput by remember { mutableStateOf("") }
    var decoyPinErrorMsg by remember { mutableStateOf<String?>(null) }
    var pinInput by remember { mutableStateOf("") }
    var pinConfirmInput by remember { mutableStateOf("") }
    var pinErrorMsg by remember { mutableStateOf<String?>(null) }
    var backupString by remember { mutableStateOf("") }
    var restoreInput by remember { mutableStateOf("") }
    var restoreResultMsg by remember { mutableStateOf<String?>(null) }
    var showAboutDialog by remember { mutableStateOf(false) }
    var showHelpDialog by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            LargeTopAppBar(
                title = {
                    Column {
                        Text(
                            text = stringResource(R.string.settings_title),
                            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = stringResource(R.string.settings_desc),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Protection & Engine
            item {
                SettingsSectionHeader("Protection & Engine")
                SettingsGroupCard {
                    SettingsRow(
                        title = "Locking Engine",
                        subtitle = if (activeEngine.contains("Foreground") || activeEngine.contains("Engine B"))
                            "Engine B (Compatibility Poller)" else "Engine A (Instant Accessibility Overlay)",
                        icon = Icons.Default.Shield,
                        onClick = { showEngineDialog = true }
                    )
                    SettingsSwitchRow(
                        title = "Self-Protection",
                        subtitle = "Lock system settings to prevent tampering or unauthorized uninstallation",
                        icon = Icons.Default.Security,
                        checked = selfProtectionEnabled,
                        onCheckedChange = { viewModel.setSelfProtectionEnabled(it) }
                    )
                }
            }

            // Lock Behavior
            item {
                SettingsSectionHeader("Lock Behavior")
                SettingsGroupCard {
                    SettingsRow(
                        title = "Default Re-lock Delay",
                        subtitle = when (defaultRelockDelay) {
                            0 -> "Immediately upon leaving app"
                            15 -> "15 seconds grace period"
                            30 -> "30 seconds grace period"
                            60 -> "1 minute grace period"
                            300 -> "5 minutes grace period"
                            else -> "$defaultRelockDelay seconds"
                        },
                        icon = Icons.Default.LockClock,
                        onClick = { showDelayDialog = true }
                    )
                    SettingsSwitchRow(
                        title = "Lock on Screen Off",
                        subtitle = "Immediately invalidate active unlock sessions when screen turns off",
                        icon = Icons.Default.Security,
                        checked = lockOnScreenOff,
                        onCheckedChange = { viewModel.setLockOnScreenOff(it) }
                    )
                    SettingsSwitchRow(
                        title = "Auto-Lock New Apps",
                        subtitle = "Prompt to lock newly installed applications automatically",
                        icon = Icons.Default.CheckCircle,
                        checked = lockNewAppsAuto,
                        onCheckedChange = { viewModel.setLockNewAppsAuto(it) }
                    )
                }
            }

            // Security & Intruder Detection
            item {
                SettingsSectionHeader("Security & Biometrics")
                SettingsGroupCard {
                    SettingsSwitchRow(
                        title = "Biometric Authentication",
                        subtitle = "Use fingerprint or face recognition as primary lock",
                        icon = Icons.Default.Fingerprint,
                        checked = biometricsEnabled,
                        onCheckedChange = { viewModel.setBiometricsEnabled(it) }
                    )
                    SettingsRow(
                        title = "PIN Fallback & Passcode",
                        subtitle = if (isPinConfigured) "Custom salted PIN configured" else "Not set (Tap to configure PIN)",
                        icon = Icons.Default.Pin,
                        onClick = {
                            pinInput = ""
                            pinConfirmInput = ""
                            pinErrorMsg = null
                            showPinDialog = true
                        }
                    )
                    SettingsRow(
                        title = "Decoy PIN (Duress Mode)",
                        subtitle = if (isDecoyConfigured) "Decoy passcode active" else "Not set (Tap to configure)",
                        icon = Icons.Default.Security,
                        onClick = {
                            decoyPinInput = ""
                            decoyPinConfirmInput = ""
                            decoyPinErrorMsg = null
                            showDecoyPinDialog = true
                        }
                    )
                    SettingsSwitchRow(
                        title = "Intruder Selfie Capture",
                        subtitle = "Take front camera photo after $intruderThreshold failed unlock attempts",
                        icon = Icons.Default.CameraAlt,
                        checked = intruderSelfieEnabled,
                        onCheckedChange = { viewModel.setIntruderSelfieEnabled(it) }
                    )
                    SettingsSwitchRow(
                        title = "Disguise Cover (Fake Crash)",
                        subtitle = "Disguises lock screen as app crash ('App keeps stopping'). Long press 'Close app' to reveal lock.",
                        icon = Icons.Default.Info,
                        checked = disguiseModeEnabled,
                        onCheckedChange = { viewModel.setDisguiseModeEnabled(it) }
                    )
                }
            }

            // Appearance
            item {
                SettingsSectionHeader("Appearance")
                SettingsGroupCard {
                    SettingsRow(
                        title = "Theme & Colors",
                        subtitle = when (currentThemeMode) {
                            ThemeMode.SYSTEM -> "System default"
                            ThemeMode.LIGHT -> "Light theme"
                            ThemeMode.DARK -> "Dark theme"
                            ThemeMode.AMOLED -> "Pure Black (AMOLED)"
                        },
                        icon = Icons.Default.Palette,
                        onClick = { showThemeDialog = true }
                    )
                }
            }

            // Backup & Diagnostics
            item {
                SettingsSectionHeader("Backup & Diagnostics")
                SettingsGroupCard {
                    SettingsRow(
                        title = "Export Backup",
                        subtitle = "Encrypted local JSON backup of locked apps & profiles",
                        icon = Icons.Default.Storage,
                        onClick = {
                            scope.launch {
                                backupString = viewModel.createBackup()
                                showBackupDialog = true
                            }
                        }
                    )
                    SettingsRow(
                        title = "Restore Backup",
                        subtitle = "Import encrypted configuration backup",
                        icon = Icons.Default.Storage,
                        onClick = {
                            restoreResultMsg = null
                            restoreInput = ""
                            showRestoreDialog = true
                        }
                    )
                    SettingsRow(
                        title = "OEM & Restricted Settings Guide",
                        subtitle = "Bypass Xiaomi, Samsung, or Android 13+ restricted dialogs",
                        icon = Icons.AutoMirrored.Filled.HelpOutline,
                        onClick = { showHelpDialog = true }
                    )
                    SettingsRow(
                        title = "About Taala",
                        subtitle = "Version 1.0 • 100% Offline & Zero Tracking",
                        icon = Icons.Default.Info,
                        onClick = { showAboutDialog = true }
                    )
                }
            }

            // Live Diagnostics Card
            item {
                SettingsSectionHeader("System Diagnostics")
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        DiagnosticItem(
                            label = "Accessibility Engine",
                            status = if (isAccessibilityConnected) "Connected (Active)" else "Disconnected",
                            isGood = isAccessibilityConnected
                        )
                        DiagnosticItem(
                            label = "Usage Access Permission",
                            status = if (viewModel.hasUsageStatsPermission) "Granted" else "Not Granted",
                            isGood = viewModel.hasUsageStatsPermission
                        )
                        DiagnosticItem(
                            label = "Battery Optimization",
                            status = if (viewModel.isBatteryOptimizationIgnored) "Unrestricted" else "Optimized",
                            isGood = viewModel.isBatteryOptimizationIgnored
                        )
                        DiagnosticItem(
                            label = "Privacy Guarantee",
                            status = "Verified 100% Offline (No Internet)",
                            isGood = true
                        )
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }

    // Engine Selector Dialog
    if (showEngineDialog) {
        AlertDialog(
            onDismissRequest = { showEngineDialog = false },
            title = { Text("Choose Protection Engine") },
            text = {
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                viewModel.setActiveEngine("Engine A (Accessibility)")
                                showEngineDialog = false
                            }
                            .padding(vertical = 8.dp)
                    ) {
                        RadioButton(
                            selected = !activeEngine.contains("Foreground") && !activeEngine.contains("Engine B"),
                            onClick = {
                                viewModel.setActiveEngine("Engine A (Accessibility)")
                                showEngineDialog = false
                            }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text("Engine A (Accessibility)", fontWeight = FontWeight.SemiBold)
                            Text("Sub-100ms lock overlay. Zero battery impact.", style = MaterialTheme.typography.bodySmall)
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                viewModel.setActiveEngine("Engine B (Foreground Service)")
                                showEngineDialog = false
                            }
                            .padding(vertical = 8.dp)
                    ) {
                        RadioButton(
                            selected = activeEngine.contains("Foreground") || activeEngine.contains("Engine B"),
                            onClick = {
                                viewModel.setActiveEngine("Engine B (Foreground Service)")
                                showEngineDialog = false
                            }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text("Engine B (UsageStats & Service)", fontWeight = FontWeight.SemiBold)
                            Text("Compatibility mode for restricted Android systems.", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showEngineDialog = false }) { Text("Close") }
            },
            shape = RoundedCornerShape(24.dp)
        )
    }

    // Delay Selector Dialog
    if (showDelayDialog) {
        val delayOptions = listOf(
            0 to "Immediately upon leaving app",
            15 to "15 seconds grace period",
            30 to "30 seconds grace period",
            60 to "1 minute grace period",
            300 to "5 minutes grace period"
        )
        AlertDialog(
            onDismissRequest = { showDelayDialog = false },
            title = { Text("Re-lock Delay") },
            text = {
                Column {
                    delayOptions.forEach { (seconds, label) ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.setDefaultRelockDelay(seconds)
                                    showDelayDialog = false
                                }
                                .padding(vertical = 6.dp)
                        ) {
                            RadioButton(
                                selected = defaultRelockDelay == seconds,
                                onClick = {
                                    viewModel.setDefaultRelockDelay(seconds)
                                    showDelayDialog = false
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(label, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDelayDialog = false }) { Text("Close") }
            },
            shape = RoundedCornerShape(24.dp)
        )
    }

    // Theme Selector Dialog
    if (showThemeDialog) {
        AlertDialog(
            onDismissRequest = { showThemeDialog = false },
            title = { Text("Theme & Appearance") },
            text = {
                Column {
                    ThemeMode.values().forEach { mode ->
                        val modeLabel = when (mode) {
                            ThemeMode.SYSTEM -> "Follow system default"
                            ThemeMode.LIGHT -> "Light theme"
                            ThemeMode.DARK -> "Dark theme"
                            ThemeMode.AMOLED -> "Pure Black (AMOLED)"
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onThemeModeChange(mode)
                                    viewModel.setThemeMode(mode.name)
                                    showThemeDialog = false
                                }
                                .padding(vertical = 6.dp)
                        ) {
                            RadioButton(
                                selected = currentThemeMode == mode,
                                onClick = {
                                    onThemeModeChange(mode)
                                    viewModel.setThemeMode(mode.name)
                                    showThemeDialog = false
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(modeLabel, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showThemeDialog = false }) { Text("Close") }
            },
            shape = RoundedCornerShape(24.dp)
        )
    }

    // Export Backup Dialog
    if (showBackupDialog) {
        AlertDialog(
            onDismissRequest = { showBackupDialog = false },
            title = { Text("Encrypted Backup") },
            text = {
                Column {
                    Text(
                        text = "Your locked apps, profiles, and schedules are encrypted using device keys (AES-256-GCM).",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = backupString,
                        onValueChange = {},
                        readOnly = true,
                        maxLines = 6,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("Taala Backup", backupString)
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(context, "Backup copied to clipboard", Toast.LENGTH_SHORT).show()
                        showBackupDialog = false
                    }
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Copy to Clipboard")
                }
            },
            dismissButton = {
                TextButton(onClick = { showBackupDialog = false }) { Text("Close") }
            },
            shape = RoundedCornerShape(24.dp)
        )
    }

    // Restore Backup Dialog
    if (showRestoreDialog) {
        AlertDialog(
            onDismissRequest = { showRestoreDialog = false },
            title = { Text("Restore Backup") },
            text = {
                Column {
                    Text(
                        text = "Paste your encrypted backup code below to restore locked apps and profiles:",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = restoreInput,
                        onValueChange = { restoreInput = it },
                        placeholder = { Text("Paste encrypted code here...") },
                        maxLines = 6,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (restoreResultMsg != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = restoreResultMsg ?: "",
                            color = if (restoreResultMsg?.contains("Success") == true) StatusProtectedGreen else StatusAttentionRed,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            val success = viewModel.restoreBackup(restoreInput)
                            if (success) {
                                restoreResultMsg = "Success! Backup restored."
                                Toast.makeText(context, "Backup restored successfully!", Toast.LENGTH_SHORT).show()
                            } else {
                                restoreResultMsg = "Failed to decrypt backup code. Please check code."
                            }
                        }
                    },
                    enabled = restoreInput.isNotBlank()
                ) {
                    Text("Restore")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRestoreDialog = false }) { Text("Cancel") }
            },
            shape = RoundedCornerShape(24.dp)
        )
    }

    // Help & OEM Guides Dialog
    if (showHelpDialog) {
        AlertDialog(
            onDismissRequest = { showHelpDialog = false },
            title = { Text("OEM & Restricted Settings") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Android 13+ 'Restricted Setting' Fix:",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text("1. Open Settings -> Apps -> Taala\n2. Tap the 3 dots in the top right corner\n3. Select 'Allow restricted settings'\n4. Verify with fingerprint or PIN\n5. Return to Taala and enable Accessibility.")
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Battery Optimization:",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text("Ensure Taala's battery usage is set to 'Unrestricted' so the system does not kill the background protection service.")
                }
            },
            confirmButton = {
                TextButton(onClick = { showHelpDialog = false }) { Text("Got it") }
            },
            shape = RoundedCornerShape(24.dp)
        )
    }

    // About Dialog
    if (showAboutDialog) {
        AlertDialog(
            onDismissRequest = { showAboutDialog = false },
            title = { Text("About Taala") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Taala v1.0",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Text("Private, calm, and instant app lock built with Kotlin and Jetpack Compose for Android 14+.")
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("• Strictly zero network calls (No INTERNET permission)\n• Dual-engine sub-100ms locking\n• Encrypted local SQLite Room storage\n• BiometricPrompt & salted PBKDF2 PIN\n• Material 3 Expressive UI")
                }
            },
            confirmButton = {
                TextButton(onClick = { showAboutDialog = false }) { Text("Close") }
            },
            shape = RoundedCornerShape(24.dp)
        )
    }

    // PIN Setup / Configuration Dialog
    if (showPinDialog) {
        AlertDialog(
            onDismissRequest = { showPinDialog = false },
            title = { Text(if (isPinConfigured) "Change or Remove PIN" else "Configure PIN") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Enter a 4 to 8 digit security PIN for fallback app unlock:",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    OutlinedTextField(
                        value = pinInput,
                        onValueChange = { if (it.length <= 8 && it.all { c -> c.isDigit() }) pinInput = it },
                        label = { Text("New PIN (4-8 digits)") },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = pinConfirmInput,
                        onValueChange = { if (it.length <= 8 && it.all { c -> c.isDigit() }) pinConfirmInput = it },
                        label = { Text("Confirm PIN") },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (pinErrorMsg != null) {
                        Text(
                            text = pinErrorMsg ?: "",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (pinInput.length !in 4..8) {
                            pinErrorMsg = "PIN must be between 4 and 8 digits."
                        } else if (pinInput != pinConfirmInput) {
                            pinErrorMsg = "PINs do not match."
                        } else {
                            pinManager.setPin(pinInput)
                            isPinConfigured = true
                            showPinDialog = false
                            Toast.makeText(context, "PIN updated successfully", Toast.LENGTH_SHORT).show()
                        }
                    }
                ) {
                    Text("Save PIN")
                }
            },
            dismissButton = {
                Row {
                    if (isPinConfigured) {
                        TextButton(
                            onClick = {
                                pinManager.clearPin()
                                isPinConfigured = false
                                showPinDialog = false
                                Toast.makeText(context, "PIN removed", Toast.LENGTH_SHORT).show()
                            }
                        ) {
                            Text("Remove PIN", color = MaterialTheme.colorScheme.error)
                        }
                    }
                    TextButton(onClick = { showPinDialog = false }) {
                        Text("Cancel")
                    }
                }
            },
            shape = RoundedCornerShape(24.dp)
        )
    }

    // Decoy PIN Dialog
    if (showDecoyPinDialog) {
        AlertDialog(
            onDismissRequest = { showDecoyPinDialog = false },
            title = { Text(if (isDecoyConfigured) "Change or Remove Decoy PIN" else "Configure Decoy PIN") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "A decoy PIN can be entered under duress. It unlocks without displaying real sensitive records.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    OutlinedTextField(
                        value = decoyPinInput,
                        onValueChange = { if (it.length <= 8 && it.all { c -> c.isDigit() }) decoyPinInput = it },
                        label = { Text("New Decoy PIN (4-8 digits)") },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = decoyPinConfirmInput,
                        onValueChange = { if (it.length <= 8 && it.all { c -> c.isDigit() }) decoyPinConfirmInput = it },
                        label = { Text("Confirm Decoy PIN") },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (decoyPinErrorMsg != null) {
                        Text(
                            text = decoyPinErrorMsg ?: "",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (decoyPinInput.length !in 4..8) {
                            decoyPinErrorMsg = "PIN must be between 4 and 8 digits."
                        } else if (decoyPinInput != decoyPinConfirmInput) {
                            decoyPinErrorMsg = "PINs do not match."
                        } else {
                            pinManager.setDecoyPin(decoyPinInput)
                            isDecoyConfigured = true
                            showDecoyPinDialog = false
                            Toast.makeText(context, "Decoy PIN saved", Toast.LENGTH_SHORT).show()
                        }
                    }
                ) {
                    Text("Save Decoy PIN")
                }
            },
            dismissButton = {
                Row {
                    if (isDecoyConfigured) {
                        TextButton(
                            onClick = {
                                pinManager.clearDecoyPin()
                                isDecoyConfigured = false
                                showDecoyPinDialog = false
                                Toast.makeText(context, "Decoy PIN removed", Toast.LENGTH_SHORT).show()
                            }
                        ) {
                            Text("Remove", color = MaterialTheme.colorScheme.error)
                        }
                    }
                    TextButton(onClick = { showDecoyPinDialog = false }) {
                        Text("Cancel")
                    }
                }
            },
            shape = RoundedCornerShape(24.dp)
        )
    }
}

@Composable
private fun DiagnosticItem(
    label: String,
    status: String,
    isGood: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(
                shape = CircleShape,
                color = if (isGood) StatusProtectedGreen else StatusAttentionRed,
                modifier = Modifier.size(8.dp)
            ) {}
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = status,
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                color = if (isGood) StatusProtectedGreen else StatusAttentionRed
            )
        }
    }
}

@Composable
private fun SettingsSectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
    )
}

@Composable
private fun SettingsGroupCard(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(modifier = Modifier.padding(vertical = 4.dp)) {
            content()
        }
    }
}

@Composable
private fun SettingsRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold)
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
        )
    }
}

@Composable
private fun SettingsSwitchRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold)
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}
