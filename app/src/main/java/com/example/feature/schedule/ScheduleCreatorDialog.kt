package com.example.feature.schedule

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.GroupWork
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.core.data.model.ProfileEntity

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ScheduleCreatorDialog(
    profiles: List<ProfileEntity>,
    onDismiss: () -> Unit,
    onConfirm: (
        title: String,
        startHour: Int,
        startMinute: Int,
        endHour: Int,
        endMinute: Int,
        daysOfWeek: List<Int>,
        profileId: String?
    ) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var selectedProfileId by remember { mutableStateOf<String?>(null) }
    var profileMenuExpanded by remember { mutableStateOf(false) }

    var startHour by remember { mutableIntStateOf(9) }
    var startMinute by remember { mutableIntStateOf(0) }
    var endHour by remember { mutableIntStateOf(17) }
    var endMinute by remember { mutableIntStateOf(0) }

    // Days 1 (Mon) to 7 (Sun)
    val selectedDays = remember { mutableStateListOf(1, 2, 3, 4, 5) } // Default weekdays

    val dayNames = listOf(
        1 to "Mon",
        2 to "Tue",
        3 to "Wed",
        4 to "Thu",
        5 to "Fri",
        6 to "Sat",
        7 to "Sun"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "New Schedule",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Schedule Title
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Schedule Name") },
                    placeholder = { Text("e.g., Work Hours, Sleep") },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_schedule_name")
                )

                // Target Profile Selector
                ExposedDropdownMenuBox(
                    expanded = profileMenuExpanded,
                    onExpandedChange = { profileMenuExpanded = !profileMenuExpanded },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val currentProfileName = profiles.find { it.id == selectedProfileId }?.name ?: "All Locked Apps"
                    OutlinedTextField(
                        value = currentProfileName,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Enforce on Profile") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = profileMenuExpanded) },
                        leadingIcon = { Icon(Icons.Default.GroupWork, contentDescription = null) },
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = profileMenuExpanded,
                        onDismissRequest = { profileMenuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("All Locked Apps") },
                            onClick = {
                                selectedProfileId = null
                                profileMenuExpanded = false
                            }
                        )
                        profiles.forEach { profile ->
                            DropdownMenuItem(
                                text = { Text(profile.name) },
                                onClick = {
                                    selectedProfileId = profile.id
                                    profileMenuExpanded = false
                                }
                            )
                        }
                    }
                }

                // Time Range Rows
                Text(
                    text = "Active Time Window",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    TimePickerChip(
                        label = "Starts",
                        hour = startHour,
                        minute = startMinute,
                        modifier = Modifier.weight(1f),
                        onTimeChanged = { h, m ->
                            startHour = h
                            startMinute = m
                        }
                    )
                    TimePickerChip(
                        label = "Ends",
                        hour = endHour,
                        minute = endMinute,
                        modifier = Modifier.weight(1f),
                        onTimeChanged = { h, m ->
                            endHour = h
                            endMinute = m
                        }
                    )
                }

                // Repeat Days
                Text(
                    text = "Repeat on Days",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    dayNames.forEach { (dayIndex, dayLabel) ->
                        val isSelected = selectedDays.contains(dayIndex)
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                if (isSelected) {
                                    if (selectedDays.size > 1) selectedDays.remove(dayIndex)
                                } else {
                                    selectedDays.add(dayIndex)
                                }
                            },
                            label = { Text(dayLabel) },
                            shape = CircleShape,
                            leadingIcon = if (isSelected) {
                                { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                            } else null,
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (title.isNotBlank() && selectedDays.isNotEmpty()) {
                        onConfirm(
                            title,
                            startHour,
                            startMinute,
                            endHour,
                            endMinute,
                            selectedDays.toList(),
                            selectedProfileId
                        )
                    }
                },
                enabled = title.isNotBlank() && selectedDays.isNotEmpty(),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.testTag("btn_confirm_schedule")
            ) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("btn_cancel_schedule")
            ) {
                Text("Cancel")
            }
        },
        shape = RoundedCornerShape(28.dp)
    )
}

@Composable
private fun TimePickerChip(
    label: String,
    hour: Int,
    minute: Int,
    onTimeChanged: (Int, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var showDialog by remember { mutableStateOf(false) }

    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable { showDialog = true },
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.AccessTime,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(6.dp))
                val formattedTime = "%02d:%02d".format(hour, minute)
                Text(
                    text = formattedTime,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            }
        }
    }

    if (showDialog) {
        SimpleTimeSelectDialog(
            initialHour = hour,
            initialMinute = minute,
            onDismiss = { showDialog = false },
            onSelect = { h, m ->
                onTimeChanged(h, m)
                showDialog = false
            }
        )
    }
}

@Composable
private fun SimpleTimeSelectDialog(
    initialHour: Int,
    initialMinute: Int,
    onDismiss: () -> Unit,
    onSelect: (Int, Int) -> Unit
) {
    var hour by remember { mutableIntStateOf(initialHour) }
    var minute by remember { mutableIntStateOf(initialMinute) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select Time", style = MaterialTheme.typography.titleMedium) },
        text = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Hour Selector
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    TextButton(onClick = { hour = (hour + 1) % 24 }) { Text("+", style = MaterialTheme.typography.titleLarge) }
                    Text(
                        text = "%02d".format(hour),
                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    TextButton(onClick = { hour = if (hour == 0) 23 else hour - 1 }) { Text("-", style = MaterialTheme.typography.titleLarge) }
                }

                Text(
                    text = ":",
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier.padding(horizontal = 16.dp)
                )

                // Minute Selector
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    TextButton(onClick = { minute = (minute + 5) % 60 }) { Text("+", style = MaterialTheme.typography.titleLarge) }
                    Text(
                        text = "%02d".format(minute),
                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    TextButton(onClick = { minute = if (minute < 5) 55 else minute - 5 }) { Text("-", style = MaterialTheme.typography.titleLarge) }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onSelect(hour, minute) }) {
                Text("Set")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
