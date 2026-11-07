package com.dsann.music.ui.player

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import com.dsann.music.ui.theme.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import androidx.compose.ui.zIndex
import com.dsann.music.R
import com.dsann.music.model.SongItem
import com.dsann.music.ui.QueueListItem
import com.dsann.music.ui.QueueSyncedLyricPreview
import com.dsann.music.ui.buildQueueDisplayEntries
import com.dsann.music.ui.QueueDisplayRow
import com.dsann.music.ui.QueueDraggedSongOverlay
import com.dsann.music.ui.ClearRecentDraggedItemAnimationSkip
import com.dsann.music.ui.queueDragHandleInput
import com.dsann.music.ui.rememberDragDropState
import com.dsann.music.viewmodel.PlayerSharedViewModel
import kotlin.math.abs

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SlidingQueueDrawer(
    isQueueExpanded: Boolean,
    onQueueExpandedChange: (Boolean) -> Unit,
    songList: List<SongItem>,
    currentSongIndex: Int,
    isShuffleEnabled: Boolean,
    repeatMode: Int,
    sleepTimerMode: PlayerSharedViewModel.SleepTimerMode,
    onReorderQueue: (Int, Int) -> Unit,
    onReorderFinished: () -> Unit = {},
    onPlaySongAtIndex: (Int) -> Unit,
    onToggleShuffle: () -> Unit,
    onToggleLoopMode: () -> Unit,
    onShowSleepTimerDialog: () -> Unit,
    playerBackgroundColor: Int,
    dynamicAccentColor: Color,
    isDark: Boolean,
    onSongLongClick: (SongItem) -> Unit,
    onRemoveFromQueue: (SongItem) -> Unit,
    playerSharedViewModel: PlayerSharedViewModel,
    modifier: Modifier = Modifier
) {
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val screenHeight = configuration.screenHeightDp.dp
    val screenWidth = configuration.screenWidthDp.dp
    val landscapePanelWidthFraction = 0.42f

    val slidingOffset by animateDpAsState(
        targetValue = if (isQueueExpanded) 0.dp else screenHeight + 100.dp,
        animationSpec = spring(dampingRatio = 0.85f, stiffness = 300f),
        label = "QueueSlidingOffset"
    )
    val landscapeSlidingOffset by animateDpAsState(
        targetValue = if (isQueueExpanded) 0.dp else -(screenWidth * landscapePanelWidthFraction + 100.dp),
        animationSpec = spring(dampingRatio = 0.85f, stiffness = 300f),
        label = "QueueSlidingOffsetX"
    )

    val queueCornerRadius by animateDpAsState(
        targetValue = if (isQueueExpanded) 0.dp else 32.dp,
        animationSpec = spring(dampingRatio = 0.85f, stiffness = 300f),
        label = "QueueCornerRadius"
    )
    val safeCornerRadius = queueCornerRadius.coerceAtLeast(0.dp)

    val queueBgColor = deriveArtworkSurfaceColor(
        sourceColor = Color(playerBackgroundColor),
        isDark = isDark,
        darkLightness = 0.155f,
        lightLightness = 0.835f,
        darkSaturationRange = 0.32f..0.54f,
        lightSaturationRange = 0.30f..0.48f
    )
    val queueTrayBackgroundColor = remember(playerBackgroundColor, isDark) {
        deriveArtworkSurfaceColor(
            sourceColor = Color(playerBackgroundColor),
            isDark = isDark,
            darkLightness = 0.24f,
            lightLightness = 0.73f,
            darkSaturationRange = 0.30f..0.60f,
            lightSaturationRange = 0.24f..0.50f
        )
    }
    val queueTrayInactiveButtonColor = remember(queueTrayBackgroundColor, isDark) {
        val hsl = FloatArray(3)
        androidx.core.graphics.ColorUtils.colorToHSL(queueTrayBackgroundColor.toArgb(), hsl)
        if (hsl[1] < 0.08f) {
            hsl[1] = 0f
        } else {
            hsl[1] = hsl[1].coerceIn(0.24f, 0.55f)
        }
        if (isDark) {
            hsl[2] = 0.33f
        } else {
            hsl[2] = 0.64f
        }
        Color(androidx.core.graphics.ColorUtils.HSLToColor(hsl))
    }
    val queueTrayActiveButtonColor = remember(dynamicAccentColor, isDark) {
        val hsl = FloatArray(3)
        androidx.core.graphics.ColorUtils.colorToHSL(dynamicAccentColor.toArgb(), hsl)
        if (hsl[1] < 0.08f) {
            if (isDark) Color(0xFFE8E8EA) else Color(0xFF1F1E23)
        } else {
            hsl[1] = hsl[1].coerceAtLeast(0.45f)
            hsl[2] = if (isDark) 0.62f else 0.42f
            Color(androidx.core.graphics.ColorUtils.HSLToColor(hsl))
        }
    }
    val queueTrayInactiveContentColor = remember(queueTrayInactiveButtonColor) {
        if (androidx.core.graphics.ColorUtils.calculateLuminance(queueTrayInactiveButtonColor.toArgb()) > 0.5) {
            Color(0xFF1C1B1F)
        } else {
            Color.White
        }
    }
    val queueTrayActiveContentColor = remember(queueTrayActiveButtonColor) {
        if (androidx.core.graphics.ColorUtils.calculateLuminance(queueTrayActiveButtonColor.toArgb()) > 0.5) {
            Color(0xFF1C1B1F)
        } else {
            Color.White
        }
    }

    DsmusicSurface(
        modifier = modifier.then(
            if (isLandscape) {
                Modifier
                    .fillMaxWidth(landscapePanelWidthFraction)
                    .fillMaxHeight()
                    .offset { androidx.compose.ui.unit.IntOffset(landscapeSlidingOffset.roundToPx(), 0) }
            } else {
                Modifier
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .offset { androidx.compose.ui.unit.IntOffset(0, slidingOffset.roundToPx()) }
            }
        ),
        shape = if (isLandscape) {
            RoundedCornerShape(topEnd = safeCornerRadius, bottomEnd = safeCornerRadius)
        } else {
            RoundedCornerShape(topStart = safeCornerRadius, topEnd = safeCornerRadius)
        },
        color = queueBgColor,
        showBorder = false
    ) {
        Box(
            modifier = Modifier.fillMaxSize().clickable { onQueueExpandedChange(false) }
        ) {
            val boxScope = this
            val lazyListState = rememberLazyListState()
            val queueScope = rememberCoroutineScope()
            val dragHandleWidthPx = with(androidx.compose.ui.platform.LocalDensity.current) { 48.dp.toPx() }
            val dragHandleTrailingInsetPx = with(androidx.compose.ui.platform.LocalDensity.current) { 22.dp.toPx() }
            // Keep the rendered queue fixed while the dragged row follows the pointer.
            // The actual reorder is committed only after the user releases the handle.
            val dragDropState = rememberDragDropState(
                lazyListState = lazyListState,
                onDragFinished = { from, to, commit ->
                    if (commit && from in songList.indices && to in songList.indices && from != to) {
                        onReorderQueue(from, to)
                        onReorderFinished()
                    }
                }
            )
            val draggedEntryKey = dragDropState.draggedItemKey as? String
            val displayEntries = remember(songList, draggedEntryKey, dragDropState.currentIndexOfDraggedItem) {
                buildQueueDisplayEntries(songList, draggedEntryKey, dragDropState.currentIndexOfDraggedItem)
            }
            ClearRecentDraggedItemAnimationSkip(dragDropState)

            // Scroll active track into view on first open
            LaunchedEffect(isQueueExpanded) {
                if (isQueueExpanded && currentSongIndex in songList.indices) {
                    val distance = abs(lazyListState.firstVisibleItemIndex - currentSongIndex)
                    if (distance > 24) {
                        lazyListState.scrollToItem(currentSongIndex)
                    } else {
                        lazyListState.animateScrollToItem(currentSongIndex)
                    }
                }
            }

            Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp, bottom = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Title Strip Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Left Side Grouping
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { onQueueExpandedChange(false) }) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_keyboard_arrow_down),
                                    contentDescription = "Collapse Queue",
                                    tint = DsmusicTheme.colorScheme.onSurface
                                )
                            }
                            Spacer(modifier = Modifier.width(4.dp))

                            Column {
                                Text(
                                    text = "Playing From",
                                    style = DsmusicTheme.typography.titleMedium,
                                    color = DsmusicTheme.colorScheme.onSurface.copy(alpha = 0.9f),
                                    letterSpacing = 0.5.sp
                                )
                                Text(
                                    text = "QUEUE",
                                    style = DsmusicTheme.typography.titleSmallEmphasized,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = DsmusicTheme.colorScheme.onSurface.copy(alpha = 0.9f),
                                    letterSpacing = 0.5.sp
                                )
                            }
                        }

                        // A stable shortcut makes the current track easy to find after manual queue edits.
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(18.dp))
                                    .background(DsmusicTheme.colorScheme.primaryContainer)
                                    .clickable(enabled = currentSongIndex in songList.indices) {
                                        queueScope.launch {
                                            if (currentSongIndex in songList.indices) {
                                                lazyListState.animateScrollToItem(currentSongIndex)
                                            }
                                        }
                                    }
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_play),
                                    contentDescription = "Jump to playing song",
                                    tint = DsmusicTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "Jump to playing",
                                    style = DsmusicTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = DsmusicTheme.colorScheme.onPrimaryContainer
                                )
                            }
                            Text(
                                text = "${songList.size} tracks",
                                style = DsmusicTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = DsmusicTheme.colorScheme.onSurfaceVariant
                            )
                            if (songList.size > 1 && draggedEntryKey == null) {
                                IconButton(onClick = { playerSharedViewModel.clearQueueExceptCurrentSong() }) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_delete),
                                        contentDescription = "Clear queue except playing song",
                                        tint = DsmusicTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                QueueSyncedLyricPreview(
                    viewModel = playerSharedViewModel,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 6.dp)
                )

                HorizontalDivider(color = DsmusicTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))

                // Queue List: the placeholder moves through the list while the dragged
                // song itself is drawn independently above the animated rows.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .queueDragHandleInput(
                            dragDropState = dragDropState,
                            handleWidthPx = dragHandleWidthPx,
                            trailingInsetPx = dragHandleTrailingInsetPx
                        )
                ) {
                    LazyColumn(
                        state = lazyListState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 120.dp),
                        verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)
                    ) {
                        itemsIndexed(
                            items = displayEntries,
                            key = { _, entry -> entry.stableKey },
                            contentType = { _, entry -> if (entry.song == null) "queue-drag-placeholder" else "queue-song" }
                        ) { displayIndex, entry ->
                            val recentlyDropped = entry.stableKey == dragDropState.recentlyDraggedItemKey
                            val animatedModifier = if (recentlyDropped) Modifier else Modifier.animateItem(
                                fadeInSpec = null,
                                fadeOutSpec = null,
                                placementSpec = spring(
                                    dampingRatio = Spring.DampingRatioNoBouncy,
                                    stiffness = Spring.StiffnessMediumLow
                                )
                            )
                            QueueDisplayRow(
                                entry = entry,
                                displayIndex = displayIndex,
                                songs = songList,
                                currentSongIndex = currentSongIndex,
                                dragDropState = dragDropState,
                                itemModifier = animatedModifier,
                                onLongClick = onSongLongClick,
                                onSwipeRemove = onRemoveFromQueue,
                                onPlaySongAtIndex = onPlaySongAtIndex
                            )
                        }
                    }
                    QueueDraggedSongOverlay(
                        songs = songList,
                        displayEntries = displayEntries,
                        currentSongIndex = currentSongIndex,
                        dragDropState = dragDropState,
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp),
                        onSongLongClick = onSongLongClick,
                        onSwipeRemove = onRemoveFromQueue,
                        onPlaySongAtIndex = onPlaySongAtIndex
                    )
                }
            } // Column (header + list)

            // Connected ButtonGroup tray with curved top clip
            Surface(
                modifier = with(boxScope) {
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                },
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                color = queueTrayBackgroundColor
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(top = 20.dp, bottom = 16.dp)
                        .navigationBarsPadding(),
                    contentAlignment = Alignment.Center
                ) {
                    com.dsann.music.ui.theme.ButtonGroup(
                        modifier = Modifier.fillMaxWidth(),
                        expandedRatio = 0.3f,
                        overflowIndicator = {}
                    ) {
                        val scope = this

                        // 1. Shuffle Button
                        customItem(
                            buttonGroupContent = {
                                val interactionSource = remember { MutableInteractionSource() }
                                val isPressed by interactionSource.collectIsPressedAsState()
                                val cornerRadius by animateDpAsState(
                                    targetValue = if (isPressed) 12.dp else 28.dp,
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioMediumBouncy,
                                        stiffness = Spring.StiffnessMedium
                                    ),
                                    label = "ShuffleCorner"
                                )
                                com.dsann.music.ui.theme.Button(
                                    onClick = {
                                        onToggleShuffle()
                                    },
                                    modifier = with(scope) {
                                        Modifier
                                            .animateWidth(interactionSource)
                                            .weight(1f)
                                            .height(56.dp)
                                    },
                                    interactionSource = interactionSource,
                                    shape = RoundedCornerShape(cornerRadius),
                                    colors = com.dsann.music.ui.theme.ButtonDefaults.buttonColors(
                                        containerColor = if (isShuffleEnabled) queueTrayActiveButtonColor else queueTrayInactiveButtonColor,
                                        contentColor = if (isShuffleEnabled) queueTrayActiveContentColor else queueTrayInactiveContentColor
                                    ),
                                    contentPadding = PaddingValues(0.dp)
                                ) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_shuffle),
                                        contentDescription = "Shuffle",
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            },
                            menuContent = {}
                        )

                        // 2. Loop Button
                        customItem(
                            buttonGroupContent = {
                                val interactionSource = remember { MutableInteractionSource() }
                                val isPressed by interactionSource.collectIsPressedAsState()
                                val cornerRadius by animateDpAsState(
                                    targetValue = if (isPressed) 12.dp else 28.dp,
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioMediumBouncy,
                                        stiffness = Spring.StiffnessMedium
                                    ),
                                    label = "LoopCorner"
                                )
                                val loopIcon = when (repeatMode) {
                                    PlayerSharedViewModel.REPEAT_ONCE -> R.drawable.ic_repeat_one
                                    PlayerSharedViewModel.REPEAT_INFINITY -> R.drawable.ic_repeat_infinity
                                    else -> R.drawable.ic_repeat
                                }
                                val loopDescription = when (repeatMode) {
                                    PlayerSharedViewModel.REPEAT_ONCE -> "Repeat once"
                                    PlayerSharedViewModel.REPEAT_INFINITY -> "Repeat infinity"
                                    else -> "No repeat"
                                }
                                val loopActive = repeatMode != PlayerSharedViewModel.REPEAT_OFF
                                com.dsann.music.ui.theme.Button(
                                    onClick = {
                                        onToggleLoopMode()
                                    },
                                    modifier = with(scope) {
                                        Modifier
                                            .animateWidth(interactionSource)
                                            .weight(1f)
                                            .height(56.dp)
                                    },
                                    interactionSource = interactionSource,
                                    shape = RoundedCornerShape(cornerRadius),
                                    colors = com.dsann.music.ui.theme.ButtonDefaults.buttonColors(
                                        containerColor = if (loopActive) queueTrayActiveButtonColor else queueTrayInactiveButtonColor,
                                        contentColor = if (loopActive) queueTrayActiveContentColor else queueTrayInactiveContentColor
                                    ),
                                    contentPadding = PaddingValues(0.dp)
                                ) {
                                    Icon(
                                        painter = painterResource(id = loopIcon),
                                        contentDescription = loopDescription,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            },
                            menuContent = {}
                        )

                        // 3. Sleep Timer Button
                        customItem(
                            buttonGroupContent = {
                                val interactionSource = remember { MutableInteractionSource() }
                                val isPressed by interactionSource.collectIsPressedAsState()
                                val cornerRadius by animateDpAsState(
                                    targetValue = if (isPressed) 12.dp else 28.dp,
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioMediumBouncy,
                                        stiffness = Spring.StiffnessMedium
                                    ),
                                    label = "TimerCorner"
                                )
                                val timerActive = sleepTimerMode != PlayerSharedViewModel.SleepTimerMode.OFF
                                com.dsann.music.ui.theme.Button(
                                    onClick = {
                                        onShowSleepTimerDialog()
                                    },
                                    modifier = with(scope) {
                                        Modifier
                                            .animateWidth(interactionSource)
                                            .weight(1f)
                                            .height(56.dp)
                                    },
                                    interactionSource = interactionSource,
                                    shape = RoundedCornerShape(cornerRadius),
                                    colors = com.dsann.music.ui.theme.ButtonDefaults.buttonColors(
                                        containerColor = if (timerActive) queueTrayActiveButtonColor else queueTrayInactiveButtonColor,
                                        contentColor = if (timerActive) queueTrayActiveContentColor else queueTrayInactiveContentColor
                                    ),
                                    contentPadding = PaddingValues(0.dp)
                                ) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_timer),
                                        contentDescription = "Sleep Timer",
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            },
                            menuContent = {}
                        )
                    }
                }
            }
        }
    }
}
