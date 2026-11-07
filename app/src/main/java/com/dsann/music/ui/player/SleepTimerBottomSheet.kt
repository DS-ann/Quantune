package com.dsann.music.ui.player

import com.dsann.music.ui.theme.DsmusicCard
import com.dsann.music.ui.theme.DsmusicSurface
import com.dsann.music.ui.theme.DsmusicListItem
import com.dsann.music.ui.theme.DsmusicButton
import com.dsann.music.ui.theme.DsmusicOutlinedButton
import com.dsann.music.ui.theme.DsmusicTextButton
import com.dsann.music.ui.theme.DsmusicIconButton
import com.dsann.music.ui.theme.DsmusicFilterChip
import com.dsann.music.ui.theme.DsmusicSwitchControl
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Stop
import com.dsann.music.ui.theme.AlertDialog
import com.dsann.music.ui.theme.BottomSheetDefaults
import com.dsann.music.ui.theme.Button
import com.dsann.music.ui.theme.ButtonDefaults
import com.dsann.music.ui.theme.Card
import com.dsann.music.ui.theme.CardDefaults
import com.dsann.music.ui.theme.ListItem
import com.dsann.music.ui.theme.ListItemDefaults
import com.dsann.music.ui.theme.Icon
import com.dsann.music.ui.theme.DsmusicTheme
import com.dsann.music.ui.theme.ModalBottomSheet
import com.dsann.music.ui.theme.HorizontalDivider
import com.dsann.music.ui.theme.Slider
import com.dsann.music.ui.theme.Text
import com.dsann.music.ui.theme.TextButton
import com.dsann.music.ui.theme.TimePicker
import com.dsann.music.ui.theme.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dsann.music.viewmodel.PlayerSharedViewModel
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds


@Composable
fun SleepTimerBottomSheet(
    onDismissRequest: () -> Unit,
    sleepTimerEndTime: Long,
    sleepTimerMode: PlayerSharedViewModel.SleepTimerMode,
    onStartTimer: (minutes: Int) -> Unit,
    onCancelTimer: () -> Unit,
    onSetEndOfSong: (Boolean) -> Unit
) {
    var sliderValue by remember { mutableFloatStateOf(1f) }
    var showTimePicker by remember { mutableStateOf(false) }

    // Live countdown calculations
    var remainingMinutes by remember { mutableLongStateOf(0L) }
    var remainingSeconds by remember { mutableLongStateOf(0L) }

    LaunchedEffect(sleepTimerEndTime, sleepTimerMode) {
        if (sleepTimerMode == PlayerSharedViewModel.SleepTimerMode.CUSTOM && sleepTimerEndTime > 0) {
            while (true) {
                val now = System.currentTimeMillis()
                val diffMs = sleepTimerEndTime - now
                if (diffMs <= 0) {
                    remainingMinutes = 0
                    remainingSeconds = 0
                    break
                } else {
                    val totalSecs = diffMs / 1000
                    remainingMinutes = totalSecs / 60
                    remainingSeconds = totalSecs % 60
                }
                kotlinx.coroutines.delay(1000.milliseconds)
            }
        } else {
            remainingMinutes = 0
            remainingSeconds = 0
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Sleep Timer",
                style = DsmusicTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            AnimatedVisibility(
                visible = sleepTimerMode != PlayerSharedViewModel.SleepTimerMode.OFF,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                DsmusicCard(
                    colors = CardDefaults.cardColors(
                        containerColor = DsmusicTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                    ),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Notifications,
                            contentDescription = null,
                            tint = DsmusicTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            val activeText = when (sleepTimerMode) {
                                PlayerSharedViewModel.SleepTimerMode.END_OF_SONG -> "Active: Stop at end of current song"
                                PlayerSharedViewModel.SleepTimerMode.END_OF_QUEUE -> "Active: Stop at end of queue"
                                PlayerSharedViewModel.SleepTimerMode.CUSTOM -> {
                                    val timeStr = String.format(LocalLocale.current.platformLocale, "%02d:%02d", remainingMinutes, remainingSeconds)
                                    "Countdown: $timeStr remaining"
                                }
                                else -> ""
                            }
                            Text(
                                text = activeText,
                                style = DsmusicTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = DsmusicTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
            }

            // Grouped Options using ListItems
            DsmusicCard(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = DsmusicTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .animateContentSize(animationSpec = spring())
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // 1. Hours Slider Section (Hides when END_OF_SONG is toggled)
                    AnimatedVisibility(
                        visible = sleepTimerMode != PlayerSharedViewModel.SleepTimerMode.END_OF_SONG,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            DsmusicListItem(
                                leadingContent = {
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .background(DsmusicTheme.colorScheme.primaryContainer, CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Rounded.Timer, contentDescription = null, tint = DsmusicTheme.colorScheme.onPrimaryContainer)
                                    }
                                },
                                headlineContent = {
                                    Text(
                                        text = "Set Hours (${sliderValue.roundToInt()}h)",
                                        style = DsmusicTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                },
                                supportingContent = {
                                    Column(
                                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                                    ) {
                                        Slider(
                                            value = sliderValue,
                                            onValueChange = { sliderValue = it },
                                            valueRange = 1f..5f,
                                            steps = 3,
                                            onValueChangeFinished = {
                                                onStartTimer(sliderValue.roundToInt() * 60)
                                            },
                                            modifier = Modifier.fillMaxWidth()
                                        )

                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 4.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            listOf(1, 2, 3, 4, 5).forEach { hour ->
                                                Text(
                                                    text = "${hour}h",
                                                    style = DsmusicTheme.typography.bodyMedium,
                                                    fontWeight = if (sliderValue.roundToInt() == hour) FontWeight.Bold else FontWeight.Normal,
                                                    color = if (sliderValue.roundToInt() == hour) DsmusicTheme.colorScheme.primary else DsmusicTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                                    textAlign = TextAlign.Center,
                                                    modifier = Modifier.width(28.dp)
                                                )
                                            }
                                        }
                                    }
                                },
                                colors = ListItemDefaults.colors(
                                    containerColor = androidx.compose.ui.graphics.Color.Transparent,
                                    headlineColor = DsmusicTheme.colorScheme.onSurface
                                )
                            )

                            HorizontalDivider(
                                color = DsmusicTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f),
                                thickness = 1.dp,
                                modifier = Modifier.padding(start = 72.dp, end = 16.dp)
                            )
                        }
                    }

                    // 2. End of Track Toggle Section
                    DsmusicListItem(
                        checked = sleepTimerMode == PlayerSharedViewModel.SleepTimerMode.END_OF_SONG,
                        onCheckedChange = { isChecked ->
                            onSetEndOfSong(isChecked)
                        },
                        leadingContent = {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(DsmusicTheme.colorScheme.primaryContainer, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Rounded.Stop, contentDescription = null, tint = DsmusicTheme.colorScheme.onPrimaryContainer)
                            }
                        },
                        content = {
                            Text(
                                text = "Stop at end of track",
                                style = DsmusicTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        },
                        trailingContent = {
                            DsmusicSwitchControl(
                                checked = sleepTimerMode == PlayerSharedViewModel.SleepTimerMode.END_OF_SONG,
                                onCheckedChange = null
                            )
                        },
                        colors = ListItemDefaults.colors(
                            containerColor = androidx.compose.ui.graphics.Color.Transparent,
                            headlineColor = DsmusicTheme.colorScheme.onSurface
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Connected buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Cancel button on left
                DsmusicButton(
                    onClick = {
                        onCancelTimer()
                        sliderValue = 1f
                    },
                    shape = RoundedCornerShape(
                        topStart = 24.dp,
                        bottomStart = 24.dp,
                        topEnd = 4.dp,
                        bottomEnd = 4.dp
                    ),
                    colors = ButtonDefaults.filledTonalButtonColors(),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                ) {
                    Icon(Icons.Rounded.Close, contentDescription = "Cancel Timer", modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Cancel", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.width(2.dp))

                // Custom button on right
                DsmusicButton(
                    onClick = { showTimePicker = true },
                    shape = RoundedCornerShape(
                        topStart = 4.dp,
                        bottomStart = 4.dp,
                        topEnd = 24.dp,
                        bottomEnd = 24.dp
                    ),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = DsmusicTheme.colorScheme.primary,
                        contentColor = DsmusicTheme.colorScheme.onPrimary
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                ) {
                    Icon(Icons.Rounded.Edit, contentDescription = "Custom Timer", modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Custom", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    if (showTimePicker) {
        val state = rememberTimePickerState(
            initialHour = 0,
            initialMinute = 30,
            is24Hour = true
        )
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            confirmButton = {
                DsmusicTextButton(onClick = {
                    val totalMinutes = state.hour * 60 + state.minute
                    if (totalMinutes > 0) {
                        onStartTimer(totalMinutes)
                    }
                    showTimePicker = false
                }) {
                    Text("Set", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                DsmusicTextButton(onClick = { showTimePicker = false }) {
                    Text("Cancel")
                }
            },
            title = {
                Text("Select Duration", style = DsmusicTheme.typography.titleMedium)
            },
            text = {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    TimePicker(state = state)
                }
            }
        )
    }
}
