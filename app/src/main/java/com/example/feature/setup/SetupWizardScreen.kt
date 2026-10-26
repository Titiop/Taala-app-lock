package com.example.feature.setup

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Accessibility
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DataUsage
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.R
import com.example.core.security.PermissionManager
import com.example.ui.theme.StatusAttentionRed
import com.example.ui.theme.StatusPausedAmber
import com.example.ui.theme.StatusProtectedGreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupWizardScreen(
    onNavigateBack: () -> Unit,
    onFinishSetup: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SetupWizardViewModel = viewModel()
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val permissionManager = remember { PermissionManager(context) }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    var showAccessibilityDisclosure by remember { mutableStateOf(false) }
    var showRestrictedSettingsHelp by remember { mutableStateOf(false) }

    // Recheck live status whenever user returns from System Settings (Section 10.3)
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshPermissions()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Notification permission launcher for Android 13+
    val notificationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) {
        viewModel.refreshPermissions()
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            LargeTopAppBar(
                title = {
                    Column {
                        Text(
                            text = stringResource(R.string.setup_wizard_title),
                            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "Configure permissions with full transparency",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        bottomBar = {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.background,
                shadowElevation = 8.dp
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Button(
                        onClick = {
                            viewModel.onFinishSetup()
                            onFinishSetup()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("finish_setup_button"),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Text(
                            text = if (uiState.isReadyToProtect) "Enter Taala" else "Continue to Apps",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Summary Banner
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (uiState.isReadyToProtect) {
                            StatusProtectedGreen.copy(alpha = 0.15f)
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                        }
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (uiState.isReadyToProtect) Icons.Default.Shield else Icons.Default.Info,
                            contentDescription = null,
                            tint = if (uiState.isReadyToProtect) StatusProtectedGreen else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(14.dp))
                        Column {
                            Text(
                                text = if (uiState.isReadyToProtect) "Protection Ready" else "Setup in Progress",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = if (uiState.isReadyToProtect) StatusProtectedGreen else MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (uiState.isReadyToProtect) {
                                    "Engine A is configured and ready to lock your sensitive apps."
                                } else {
                                    "Enable the Accessibility Service below for instant app locking."
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Step 1: Biometrics & Screen Lock
            item {
                WizardStepCard(
                    title = "Biometrics & Device Lock",
                    description = "Uses system BiometricPrompt (fingerprint or Class 3 face unlock). Weak face unlock is never accepted.",
                    icon = Icons.Default.Fingerprint,
                    status = uiState.biometricStatus,
                    actionText = "Setup Lock",
                    onActionClick = {
                        try {
                            context.startActivity(permissionManager.openSecuritySettings())
                        } catch (_: Exception) {}
                    }
                )
            }

            // Step 2: Accessibility Service (Engine A)
            item {
                WizardStepCard(
                    title = "Accessibility Engine (Engine A)",
                    description = "Listens for locked apps opening and instantly displays the opaque shield. Taala never reads keystrokes or text.",
                    icon = Icons.Default.Accessibility,
                    status = uiState.accessibilityStatus,
                    actionText = "Grant Permission",
                    onActionClick = {
                        showAccessibilityDisclosure = true
                    },
                    secondaryActionText = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) "Sideloaded help" else null,
                    onSecondaryActionClick = {
                        showRestrictedSettingsHelp = true
                    }
                )
            }

            // Step 3: Notifications
            item {
                WizardStepCard(
                    title = "Notifications & Alerts",
                    description = "Required on Android 13+ to post protection alert notifications and notification-shield stand-ins.",
                    icon = Icons.Default.Notifications,
                    status = uiState.notificationStatus,
                    actionText = "Enable",
                    onActionClick = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            notificationLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            try {
                                context.startActivity(permissionManager.openNotificationSettings())
                            } catch (_: Exception) {}
                        }
                    }
                )
            }

            // Step 4: Compatibility Engine (Usage Access fallback)
            item {
                WizardStepCard(
                    title = "Usage Access (Engine B Fallback)",
                    description = "Optional fallback mode used if Accessibility is restricted by Android Advanced Protection.",
                    icon = Icons.Default.DataUsage,
                    status = uiState.usageStatsStatus,
                    actionText = "Enable Fallback",
                    onActionClick = {
                        try {
                            context.startActivity(permissionManager.openUsageAccessSettings())
                        } catch (_: Exception) {}
                    }
                )
            }

            // Step 5: Notification Shield Listener (Optional)
            item {
                WizardStepCard(
                    title = "Notification Shield Listener",
                    description = "Hides confidential incoming messages from locked apps until you unlock them.",
                    icon = Icons.Default.Shield,
                    status = if (permissionManager.isNotificationListenerEnabled()) StepStatus.DONE else StepStatus.OPTIONAL,
                    actionText = "Enable Shield",
                    onActionClick = {
                        try {
                            context.startActivity(permissionManager.openNotificationListenerSettings())
                        } catch (_: Exception) {}
                    }
                )
            }

            // Step 6: Battery Exemption (Optional)
            item {
                WizardStepCard(
                    title = "Battery Unrestricted Mode",
                    description = "Prevents Android from stopping background protection after hours of inactivity.",
                    icon = Icons.Default.Security,
                    status = StepStatus.OPTIONAL,
                    actionText = "Set Unrestricted",
                    onActionClick = {
                        try {
                            context.startActivity(permissionManager.openBatteryOptimizationSettings())
                        } catch (_: Exception) {}
                    }
                )
            }

            item {
                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }

    // Prominent Disclosure Dialog (Section 8)
    if (showAccessibilityDisclosure) {
        AlertDialog(
            onDismissRequest = { showAccessibilityDisclosure = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.Accessibility,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(36.dp)
                )
            },
            title = {
                Text(
                    text = "Accessibility Service Disclosure",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Why Taala needs Accessibility:",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Taala listens to window state change events to detect when a locked application is about to open, and instantly covers it with a secure opaque lock screen.",
                        style = MaterialTheme.typography.bodyMedium
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "What is NOT collected:",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.tertiary
                    )
                    Text(
                        text = "• Taala never reads keystrokes or passwords you type.\n• Taala never inspects personal messages or screen content.\n• Taala has no INTERNET permission and sends no data off-device.",
                        style = MaterialTheme.typography.bodyMedium
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "How to revoke:",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
                    )
                    Text(
                        text = "You can disable Taala anytime in Settings > Accessibility.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showAccessibilityDisclosure = false
                        try {
                            context.startActivity(permissionManager.openAccessibilitySettings())
                        } catch (_: Exception) {}
                    },
                    modifier = Modifier.testTag("agree_accessibility_disclosure_button"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Agree & Continue")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showAccessibilityDisclosure = false },
                    modifier = Modifier.testTag("dismiss_accessibility_disclosure_button")
                ) {
                    Text("Cancel")
                }
            },
            shape = RoundedCornerShape(28.dp)
        )
    }

    // Sideloaded Restricted Settings Help (Section 8)
    if (showRestrictedSettingsHelp) {
        AlertDialog(
            onDismissRequest = { showRestrictedSettingsHelp = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.HelpOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(36.dp)
                )
            },
            title = {
                Text(
                    text = "Restricted Settings on Android 13+",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "If the accessibility switch is grayed out or says 'Restricted setting', follow these steps:",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "1. Tap 'Open App Info' below to go to Taala's system page.\n2. Tap the ⋮ (three dots) menu in the top-right corner.\n3. Tap 'Allow restricted settings' and confirm your screen lock.\n4. Return here and tap 'Grant Permission'.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showRestrictedSettingsHelp = false
                        try {
                            context.startActivity(permissionManager.openAppInfoForRestrictedSettings())
                        } catch (_: Exception) {}
                    },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Open App Info")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRestrictedSettingsHelp = false }) {
                    Text("Got it")
                }
            },
            shape = RoundedCornerShape(28.dp)
        )
    }
}

@Composable
private fun WizardStepCard(
    title: String,
    description: String,
    icon: ImageVector,
    status: StepStatus,
    actionText: String,
    onActionClick: () -> Unit = {},
    secondaryActionText: String? = null,
    onSecondaryActionClick: () -> Unit = {}
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
        )
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        modifier = Modifier.size(44.dp),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }

                StatusChip(status = status)
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (secondaryActionText != null) {
                    TextButton(onClick = onSecondaryActionClick) {
                        Text(
                            text = secondaryActionText,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.width(1.dp))
                }

                if (status == StepStatus.DONE) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = StatusProtectedGreen,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Configured",
                            style = MaterialTheme.typography.labelLarge,
                            color = StatusProtectedGreen
                        )
                    }
                } else {
                    FilledTonalButton(
                        onClick = onActionClick,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(actionText)
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusChip(status: StepStatus) {
    val (bgColor, textColor, label) = when (status) {
        StepStatus.DONE -> Triple(StatusProtectedGreen.copy(alpha = 0.2f), StatusProtectedGreen, "Done")
        StepStatus.NEEDED -> Triple(StatusAttentionRed.copy(alpha = 0.2f), StatusAttentionRed, "Needed")
        StepStatus.OPTIONAL -> Triple(StatusPausedAmber.copy(alpha = 0.2f), StatusPausedAmber, "Optional")
    }

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = bgColor
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = textColor
        )
    }
}
