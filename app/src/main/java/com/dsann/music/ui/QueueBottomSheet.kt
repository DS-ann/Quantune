package com.dsann.music.ui

import com.dsann.music.ui.theme.DsmusicCard
import com.dsann.music.ui.theme.DsmusicSurface
import com.dsann.music.ui.theme.DsmusicButton
import com.dsann.music.ui.theme.DsmusicOutlinedButton
import com.dsann.music.ui.theme.DsmusicTextButton
import com.dsann.music.ui.theme.DsmusicFilterChip
import com.dsann.music.ui.theme.DsmusicSwitchControl
import android.annotation.SuppressLint
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import kotlinx.coroutines.withTimeoutOrNull
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.PlayArrow
import com.dsann.music.ui.theme.HorizontalDivider
import com.dsann.music.ui.theme.Icon
import com.dsann.music.ui.theme.IconButton
import com.dsann.music.ui.theme.ListItem
import com.dsann.music.ui.theme.ListItemDefaults
import com.dsann.music.ui.theme.ListItemShapes
import com.dsann.music.ui.theme.DsmusicTheme
import com.dsann.music.ui.theme.Surface
import com.dsann.music.ui.theme.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.fragment.app.FragmentActivity
import com.dsann.music.util.FavoritesManager
import coil.compose.AsyncImage
import com.dsann.music.R
import com.dsann.music.model.SongItem
import com.dsann.music.data.lyrics.LineSyncedLyricTimeline
import androidx.compose.runtime.produceState
import kotlinx.coroutines.delay
import com.dsann.music.ui.theme.DsmusicAppThemeFromPreferences
import com.dsann.music.viewmodel.PlayerSharedViewModel
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor


class QueueBottomSheet : BottomSheetDialogFragment() {

    private lateinit var viewModel: PlayerSharedViewModel

    override fun onStart() {
        super.onStart()
        val sheetDialog = dialog as? com.google.android.material.bottomsheet.BottomSheetDialog ?: return
        val sheetContainer = sheetDialog.findViewById<View>(
            com.google.android.material.R.id.design_bottom_sheet
        ) ?: return

        // The Compose queue surface is borderless; keep Material's dialog container
        // transparent so its default drawable cannot draw a second white perimeter.
        sheetDialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        sheetContainer.setBackgroundColor(android.graphics.Color.TRANSPARENT)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel = ViewModelProvider(requireActivity())[PlayerSharedViewModel::class.java]
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setContent {
                val currentSong by viewModel.currentSong.collectAsStateWithLifecycle()
                val playerBackgroundColor by viewModel.playerBackgroundColor.collectAsStateWithLifecycle()
                DsmusicAppThemeFromPreferences(
                    dynamicAlbumColor = if (currentSong != null) playerBackgroundColor else null
                ) {
                    QueueBottomSheetContent(
                        viewModel = viewModel,
                        onSongLongClick = { song ->
                            showQueueSongActions(requireContext(), song, viewModel)
                        }
                    )
                }
            }
        }
    }
}

/**
 * A queue row during drag preview. The dragged song is temporarily replaced by one
 * stable placeholder; all remaining songs keep their own keys and animate into place.
 */
internal data class QueueDisplayEntry(
    val song: SongItem,
    /** Index in the canonical queue. This distinguishes repeated copies of one track. */
    val sourceIndex: Int,
    /** Bundle-saveable, unique key for this occurrence of a track in the queue. */
    val stableKey: String
)

internal fun buildQueueDisplayEntries(
    songs: List<SongItem>,
    draggedEntryKey: String?,
    targetIndex: Int?
): List<QueueDisplayEntry> {
    // SongItem.id is not unique per queue occurrence: online IDs use String.hashCode(),
    // and the same track may intentionally be queued more than once. Give each occurrence
    // a deterministic key while keeping that key attached to its row during drag preview.
    val occurrenceCounts = HashMap<Long, Int>()
    val entries = songs.mapIndexed { index, song ->
        val occurrence = occurrenceCounts[song.id] ?: 0
        occurrenceCounts[song.id] = occurrence + 1
        QueueDisplayEntry(
            song = song,
            sourceIndex = index,
            stableKey = "queue:" + song.id + ":" + occurrence
        )
    }

    if (draggedEntryKey == null || targetIndex == null || entries.isEmpty()) {
        return entries
    }
    val fromIndex = entries.indexOfFirst { it.stableKey == draggedEntryKey }
    if (fromIndex < 0) return entries
    val safeTarget = targetIndex.coerceIn(0, entries.lastIndex)
    return com.dsann.music.util.OrderUtils.move(entries, fromIndex, safeTarget)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun QueueBottomSheetContent(
    viewModel: PlayerSharedViewModel,
    onSongLongClick: (SongItem) -> Unit = {}
) {
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    val songList by viewModel.songListFlow.collectAsStateWithLifecycle()
    val currentSongIndex by viewModel.currentSongIndexFlow.collectAsStateWithLifecycle()

    // The queue stays in its original order while dragging. Only the dragged card
    // follows the finger; the final reorder is applied when the gesture is released.
    val displayedSongs = songList

    val lazyListState = rememberLazyListState()
    val dragHandleWidthPx = with(LocalDensity.current) { 48.dp.toPx() }
    val dragHandleTrailingInsetPx = with(LocalDensity.current) { 26.dp.toPx() }
    val dragDropState = rememberDragDropState(
        lazyListState = lazyListState,
        onDragFinished = { from, to, commit ->
            if (commit && from in songList.indices && to in songList.indices && from != to) {
                viewModel.reorderQueue(from, to, syncPlaybackService = false)
                viewModel.syncQueueWithPlaybackService()
            }
        }
    )
    val draggedEntryKey = dragDropState.draggedItemKey as? String
    val displayEntries = remember(songList, draggedEntryKey, dragDropState.currentIndexOfDraggedItem) {
        buildQueueDisplayEntries(songList, draggedEntryKey, dragDropState.currentIndexOfDraggedItem)
    }
    ClearRecentDraggedItemAnimationSkip(dragDropState)

    // Auto-scroll to currently playing track on initial load
    LaunchedEffect(currentSongIndex) {
        if (dragDropState.currentIndexOfDraggedItem != null) return@LaunchedEffect
        if (currentSongIndex in displayedSongs.indices) {
            val distance = abs(lazyListState.firstVisibleItemIndex - currentSongIndex)
            if (distance > 24) {
                lazyListState.scrollToItem(currentSongIndex)
            } else {
                lazyListState.animateScrollToItem(currentSongIndex)
            }
        }
    }

    DsmusicSurface(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (isLandscape) Modifier.fillMaxHeight(0.9f) else Modifier.wrapContentHeight()),
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
        color = DsmusicTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 6.dp,
        showBorder = false
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp, bottom = 24.dp)
                .then(if (isLandscape) Modifier.padding(start = 88.dp, end = 24.dp) else Modifier),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Drag Handle
            Box(
                modifier = Modifier
                    .width(48.dp)
                    .height(6.dp)
                    .clip(CircleShape)
                    .background(DsmusicTheme.colorScheme.outlineVariant)
            )

            if (!isLandscape) {
                // PORTRAIT: Standard Flow Layout
                Spacer(modifier = Modifier.height(20.dp))

                // Title & Counter Strip
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Up Next",
                        style = DsmusicTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = DsmusicTheme.colorScheme.onSurface
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "${displayedSongs.size} tracks",
                            style = DsmusicTheme.typography.labelMedium,
                            color = DsmusicTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Bold
                        )
                        if (displayedSongs.size > 1 && draggedEntryKey == null) {
                            IconButton(onClick = { viewModel.clearQueueExceptCurrentSong() }) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_delete),
                                    contentDescription = "Clear queue except playing song",
                                    modifier = Modifier.size(20.dp),
                                    tint = DsmusicTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                QueueSyncedLyricPreview(
                    viewModel = viewModel,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 4.dp, bottom = 8.dp)
                )

                HorizontalDivider(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    color = DsmusicTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                )

                // Content Area
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 520.dp)
                        .queueDragHandleInput(
                            dragDropState = dragDropState,
                            handleWidthPx = dragHandleWidthPx,
                            trailingInsetPx = dragHandleTrailingInsetPx
                        )
                ) {
                    if (displayedSongs.isEmpty()) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(vertical = 64.dp),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_queue_music),
                                contentDescription = null,
                                modifier = Modifier.size(72.dp),
                                tint = DsmusicTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                "Your queue is empty",
                                style = DsmusicTheme.typography.titleMedium,
                                color = DsmusicTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        LazyColumn(
                            state = lazyListState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 32.dp),
                            verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)
                        ) {
                            itemsIndexed(
                                items = displayEntries,
                                key = { _, entry -> entry.stableKey },
                                contentType = { _, _ -> "queue-song" }
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
                                    songs = displayedSongs,
                                    currentSongIndex = currentSongIndex,
                                    dragDropState = dragDropState,
                                    itemModifier = animatedModifier,
                                    onLongClick = onSongLongClick,
                                    onSwipeRemove = { song -> viewModel.removeFromQueue(song.id) },
                                    onPlaySongAtIndex = { index -> viewModel.playSongAtIndex(index) }
                                )
                            }
                        }
                        QueueDraggedSongOverlay(
                            songs = displayedSongs,
                            displayEntries = displayEntries,
                            currentSongIndex = currentSongIndex,
                            dragDropState = dragDropState,
                            contentPadding = PaddingValues(start = 20.dp, end = 20.dp),
                            onSongLongClick = onSongLongClick,
                            onSwipeRemove = { song -> viewModel.removeFromQueue(song.id) },
                            onPlaySongAtIndex = { index -> viewModel.playSongAtIndex(index) }
                        )
                    }
                }
            } else {
                // LANDSCAPE: Split-Screen Dashboard
                Spacer(modifier = Modifier.height(24.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(28.dp)
                ) {
                    // LEFT PANE: Sidebar Selector (40%)
                    Column(
                        modifier = Modifier
                            .weight(0.4f)
                            .fillMaxHeight()
                            .padding(bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    text = "Audio Queue",
                                    style = DsmusicTheme.typography.headlineSmall.copy(
                                        fontWeight = FontWeight.ExtraBold,
                                        letterSpacing = (-0.5).sp
                                    ),
                                    color = DsmusicTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "${displayedSongs.size} tracks loaded",
                                    style = DsmusicTheme.typography.bodySmall,
                                    color = DsmusicTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            if (displayedSongs.size > 1 && draggedEntryKey == null) {
                                IconButton(onClick = { viewModel.clearQueueExceptCurrentSong() }) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_delete),
                                        contentDescription = "Clear queue except playing song",
                                        modifier = Modifier.size(20.dp),
                                        tint = DsmusicTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        QueueSyncedLyricPreview(
                            viewModel = viewModel,
                            modifier = Modifier.fillMaxWidth()
                        )

                        HorizontalDivider(color = DsmusicTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                        Text(
                            text = "VIEW SOURCE",
                            style = DsmusicTheme.typography.labelSmall.copy(letterSpacing = 1.5.sp),
                            fontWeight = FontWeight.Bold,
                            color = DsmusicTheme.colorScheme.primary
                        )

                        QueueSidebarItem(
                            label = "Up Next",
                            isSelected = true,
                            icon = { 
                                Icon(
                                    imageVector = Icons.Rounded.PlayArrow, 
                                    contentDescription = null,
                                    modifier = Modifier.size(22.dp)
                                ) 
                            },
                            onClick = {}
                        )
                    }

                    // RIGHT PANE: Native Segmented LazyColumn (60%)
                    Box(
                        modifier = Modifier
                            .weight(0.6f)
                            .fillMaxHeight()
                            .queueDragHandleInput(
                                dragDropState = dragDropState,
                                handleWidthPx = dragHandleWidthPx,
                                trailingInsetPx = dragHandleTrailingInsetPx
                            )
                    ) {
                        if (displayedSongs.isEmpty()) {
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_queue_music),
                                    contentDescription = null,
                                    modifier = Modifier.size(60.dp),
                                    tint = DsmusicTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    "Queue is empty",
                                    style = DsmusicTheme.typography.titleMedium,
                                    color = DsmusicTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else {
                            LazyColumn(
                                state = lazyListState,
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(end = 20.dp, bottom = 24.dp),
                                verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)
                            ) {
                                itemsIndexed(
                                    items = displayEntries,
                                    key = { _, entry -> entry.stableKey },
                                    contentType = { _, _ -> "queue-song" }
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
                                        songs = displayedSongs,
                                        currentSongIndex = currentSongIndex,
                                        dragDropState = dragDropState,
                                        itemModifier = animatedModifier,
                                        onLongClick = onSongLongClick,
                                        onSwipeRemove = { song -> viewModel.removeFromQueue(song.id) },
                                        onPlaySongAtIndex = { index -> viewModel.playSongAtIndex(index) }
                                    )
                                }
                            }
                            QueueDraggedSongOverlay(
                                songs = displayedSongs,
                                displayEntries = displayEntries,
                                currentSongIndex = currentSongIndex,
                                dragDropState = dragDropState,
                                contentPadding = PaddingValues(end = 20.dp),
                                onSongLongClick = onSongLongClick,
                                onSwipeRemove = { song -> viewModel.removeFromQueue(song.id) },
                                onPlaySongAtIndex = { index -> viewModel.playSongAtIndex(index) }
                            )
                        }
                    }
                }
            }
        }
    }
}


@Composable
internal fun QueueSyncedLyricPreview(
    viewModel: PlayerSharedViewModel,
    modifier: Modifier = Modifier
) {
    val lyrics by viewModel.syncedLyrics.collectAsStateWithLifecycle()
    val currentSong by viewModel.currentSong.collectAsStateWithLifecycle()
    val currentPosition by viewModel.currentPosition.collectAsStateWithLifecycle()
    val duration by viewModel.duration.collectAsStateWithLifecycle()
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()
    val audioService by viewModel.audioServiceState.collectAsStateWithLifecycle()
    val timeline = remember(lyrics) {
        LineSyncedLyricTimeline.build(lyrics.orEmpty())
    }
    val songKey = currentSong?.videoId?.takeIf { it.isNotBlank() }
        ?: currentSong?.path?.takeIf { it.isNotBlank() }
        ?: currentSong?.id?.toString()
    val positionProvider = remember(audioService, viewModel) {
        {
            audioService?.getCurrentPositionForLyrics()
                ?: viewModel.currentPosition.value
        }
    }
    val activeLine by produceState<String?>(
        initialValue = null,
        songKey,
        timeline,
        duration,
        isPlaying,
        positionProvider,
        currentPosition
    ) {
        while (true) {
            val positionMs = positionProvider().coerceAtLeast(0).toLong()
            val index = LineSyncedLyricTimeline.activeIndexAt(
                timeline = timeline,
                positionMs = positionMs,
                durationMs = duration.toLong().coerceAtLeast(0L)
            )
            val nextText = timeline.getOrNull(index)?.line?.content?.trim()
                ?.takeIf { it.isNotEmpty() }
            if (value != nextText) value = nextText
            delay(
                if (android.os.Build.VERSION.SDK_INT <= android.os.Build.VERSION_CODES.M) 84L
                else if (isPlaying) 48L else 84L
            )
        }
    }

    if (!activeLine.isNullOrBlank()) {
        Row(
            modifier = modifier
                .clip(RoundedCornerShape(12.dp))
                .background(DsmusicTheme.colorScheme.primaryContainer.copy(alpha = 0.54f))
                .padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_lyrics),
                contentDescription = "Current lyric",
                tint = DsmusicTheme.colorScheme.primary,
                modifier = Modifier.size(15.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = activeLine.orEmpty(),
                style = DsmusicTheme.typography.bodySmall.copy(
                    fontWeight = FontWeight.SemiBold,
                    lineHeight = 17.sp
                ),
                color = DsmusicTheme.colorScheme.onSurface,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun QueueListItem(
    song: SongItem,
    isPlaying: Boolean,
    shapes: ListItemShapes,
    dragDropState: DragDropState? = null,
    index: Int = -1,
    showDivider: Boolean = true,
    onLongClick: () -> Unit = {},
    onSwipeRemove: () -> Unit = {},
    @SuppressLint("ModifierParameter") modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val latestSwipeRemove = rememberUpdatedState(onSwipeRemove)
    val swipeThresholdPx = with(LocalDensity.current) { 88.dp.toPx() }
    val swipeOffset = remember(song.id) { Animatable(0f) }
    var swipeDragOffset by remember(song.id) { mutableFloatStateOf(0f) }
    val swipeAnimationScope = rememberCoroutineScope()
    val rowInteractionSource = remember(song.id) { MutableInteractionSource() }
    val taperedDividersEnabled = rememberTaperedSongDividersEnabled()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                val distance = abs(swipeOffset.value + swipeDragOffset)
                translationX = swipeOffset.value + swipeDragOffset
                // A small fade and compression make the row feel like it is leaving,
                // while the horizontal translation remains the primary dismissal cue.
                val fade = (distance / (swipeThresholdPx * 3f)).coerceIn(0f, 0.65f)
                alpha = 1f - fade
                scaleY = 1f - fade * 0.06f
            }
            .pointerInput(song.id, swipeThresholdPx) {
                val dismissalWidthPx = size.width.toFloat().coerceAtLeast(swipeThresholdPx)
                var horizontalTravel = 0f
                var dismissing = false
                detectHorizontalDragGestures(
                    onDragStart = {
                        horizontalTravel = 0f
                        if (!dismissing) swipeDragOffset = 0f
                    },
                    onHorizontalDrag = { change, dragAmount ->
                        if (!dismissing) {
                            horizontalTravel += dragAmount
                            change.consume()
                            swipeDragOffset = (swipeDragOffset + dragAmount).coerceIn(
                                -dismissalWidthPx,
                                dismissalWidthPx
                            )
                        }
                    },
                    onDragEnd = {
                        val releasedOffset = swipeDragOffset
                        when {
                            !dismissing && abs(horizontalTravel) >= swipeThresholdPx -> {
                                dismissing = true
                                val direction = if (horizontalTravel < 0f) -1f else 1f
                                val targetOffset = direction * (dismissalWidthPx + swipeThresholdPx)
                                swipeAnimationScope.launch {
                                    swipeOffset.snapTo(releasedOffset)
                                    swipeDragOffset = 0f
                                    swipeOffset.animateTo(
                                        targetOffset,
                                        animationSpec = tween(durationMillis = 190)
                                    )
                                    latestSwipeRemove.value()
                                }
                            }
                            !dismissing -> {
                                swipeAnimationScope.launch {
                                    swipeOffset.snapTo(releasedOffset)
                                    swipeDragOffset = 0f
                                    swipeOffset.animateTo(
                                        0f,
                                        animationSpec = spring(
                                            dampingRatio = Spring.DampingRatioNoBouncy,
                                            stiffness = Spring.StiffnessMedium
                                        )
                                    )
                                }
                            }
                        }
                        horizontalTravel = 0f
                    },
                    onDragCancel = {
                        horizontalTravel = 0f
                        if (!dismissing) {
                            val releasedOffset = swipeDragOffset
                            swipeAnimationScope.launch {
                                swipeOffset.snapTo(releasedOffset)
                                swipeDragOffset = 0f
                                swipeOffset.animateTo(
                                    0f,
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioNoBouncy,
                                        stiffness = Spring.StiffnessMedium
                                    )
                                )
                            }
                        }
                    }
                )
            }
            .clip(shapes.shape)
            .background(
                if (isPlaying) DsmusicTheme.colorScheme.primary.copy(alpha = 0.18f)
                else Color.Transparent
            )
            .taperedSongDivider(
                enabled = taperedDividersEnabled && showDivider,
                color = DsmusicTheme.colorScheme.outlineVariant.copy(alpha = 0.24f)
            )
            .padding(start = 12.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .combinedClickable(
                    interactionSource = rowInteractionSource,
                    indication = null,
                    onClick = onClick,
                    onLongClick = onLongClick
                )
                .padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val albumArtModel = remember(song.id, song.thumbnailUrl, song.albumId, song.path) {
                song.getAlbumArtUri() ?: R.drawable.default_album_art
            }
            Box(
                modifier = Modifier.size(52.dp),
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = albumArtModel,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(12.dp))
                )
                if (isPlaying) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.Black.copy(alpha = 0.5f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.PlayArrow,
                            contentDescription = "Playing",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = song.title,
                    fontWeight = if (isPlaying) FontWeight.ExtraBold else FontWeight.SemiBold,
                    fontSize = 16.sp,
                    color = if (isPlaying) DsmusicTheme.colorScheme.primary else DsmusicTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = song.artist,
                    fontSize = 13.sp,
                    color = if (isPlaying) DsmusicTheme.colorScheme.primary.copy(alpha = 0.8f)
                    else DsmusicTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Spacer(modifier = Modifier.width(4.dp))

        // The only trailing queue control: the three-bar drag handle.
        // It has its own pointer region, so long-pressing the song body opens the song menu.
        Box(
            modifier = Modifier
                .size(48.dp)
                // Consume a normal tap here so it cannot reach SlidingQueueDrawer's
                // panel-level close target. Drag gestures below still own long-press moves.
                .clickable(
                    interactionSource = remember(song.id) { MutableInteractionSource() },
                    indication = null,
                    onClick = {}
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "=",
                color = DsmusicTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
                fontSize = 27.sp,
                fontWeight = FontWeight.Bold,
                lineHeight = 27.sp
            )
        }
    }
}

@Composable
internal fun QueueDisplayRow(
    entry: QueueDisplayEntry,
    displayIndex: Int,
    songs: List<SongItem>,
    currentSongIndex: Int,
    dragDropState: DragDropState,
    itemModifier: Modifier,
    onLongClick: (SongItem) -> Unit,
    onSwipeRemove: (SongItem) -> Unit,
    onPlaySongAtIndex: (Int) -> Unit
) {
    val song = entry.song
    val originalIndex = entry.sourceIndex
    val isPlaying = currentSongIndex == originalIndex &&
        songs.getOrNull(currentSongIndex)?.id == song.id
    val isDragged = entry.stableKey == dragDropState.draggedItemKey
    val shapes = ListItemDefaults.segmentedShapes(index = displayIndex, count = songs.size)

    // Keep this keyed item composed so its long-press pointer gesture survives
    // reordering. The visible copy is rendered by QueueDraggedSongOverlay.
    Box(
        itemModifier
            .fillMaxWidth()
            .graphicsLayer { alpha = if (isDragged) 0f else 1f }
    ) {
        QueueListItem(
            song = song,
            isPlaying = isPlaying,
            shapes = shapes,
            dragDropState = dragDropState,
            index = originalIndex,
            showDivider = displayIndex < songs.lastIndex,
            onLongClick = { onLongClick(song) },
            onSwipeRemove = { onSwipeRemove(song) },
            onClick = { if (originalIndex >= 0) onPlaySongAtIndex(originalIndex) }
        )
    }
}

@Composable
internal fun QueueDraggedSongOverlay(
    songs: List<SongItem>,
    displayEntries: List<QueueDisplayEntry>,
    currentSongIndex: Int,
    dragDropState: DragDropState,
    contentPadding: PaddingValues,
    onSongLongClick: (SongItem) -> Unit,
    onSwipeRemove: (SongItem) -> Unit,
    onPlaySongAtIndex: (Int) -> Unit
) {
    val draggedEntryKey = dragDropState.draggedItemKey as? String ?: return
    val entry = displayEntries.firstOrNull { it.stableKey == draggedEntryKey } ?: return
    val song = entry.song
    val y = dragDropState.draggedItemOffset ?: return
    val targetIndex = dragDropState.currentIndexOfDraggedItem
        ?.coerceIn(0, (songs.size - 1).coerceAtLeast(0)) ?: return
    val originalIndex = entry.sourceIndex
    if (originalIndex !in songs.indices) return
    val isPlaying = currentSongIndex == originalIndex &&
        songs.getOrNull(currentSongIndex)?.id == song.id
    val shapes = ListItemDefaults.segmentedShapes(index = targetIndex, count = songs.size)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(contentPadding)
            .zIndex(20f)
            .graphicsLayer {
                translationY = y
                scaleX = 1.035f
                scaleY = 1.035f
                shadowElevation = 0f
                shape = RoundedCornerShape(18.dp)
            }
    ) {
        QueueListItem(
            song = song,
            isPlaying = isPlaying,
            shapes = shapes,
            dragDropState = null,
            index = targetIndex,
            showDivider = targetIndex < songs.lastIndex,
            onLongClick = { onSongLongClick(song) },
            onSwipeRemove = { onSwipeRemove(song) },
            modifier = Modifier.fillMaxWidth(),
            onClick = { onPlaySongAtIndex(originalIndex) }
        )
    }
}

@Composable
fun QueueSidebarItem(
    label: String,
    isSelected: Boolean,
    icon: @Composable () -> Unit,
    onClick: () -> Unit
) {
    DsmusicSurface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = if (isSelected) DsmusicTheme.colorScheme.secondaryContainer.copy(alpha = 0.8f) else Color.Transparent,
        contentColor = if (isSelected) DsmusicTheme.colorScheme.onSecondaryContainer else DsmusicTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth(),
        showBorder = false
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            icon()
            Text(
                text = label,
                style = DsmusicTheme.typography.bodyMedium,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

class DragDropState(
    val lazyListState: LazyListState,
    private val scope: CoroutineScope,
    private val edgeScrollThresholdPx: Float,
    private val maxAutoScrollSpeedPxPerSecond: Float,
    private val onDragFinished: (Int, Int, Boolean) -> Unit
) {
    var draggedDistance by mutableFloatStateOf(0f)
    var initiallyDraggedElement by mutableStateOf<LazyListItemInfo?>(null)
    var currentIndexOfDraggedItem by mutableStateOf<Int?>(null)
    private var pointerY = 0f
    private var slotStepPx = 1f
    private var targetMovementRemainderPx = 0f
    var recentlyDraggedItemKey: Any? by mutableStateOf(null)
        private set
    private var autoScrollDirection = 0f
    private var autoScrollIntensity = 0f
    private var autoScrollJob: Job? = null

    val draggedItemKey: Any?
        get() = initiallyDraggedElement?.key

    val isDragging: Boolean
        get() = currentIndexOfDraggedItem != null

    val draggedItemOffset: Float?
        get() = initiallyDraggedElement?.let { anchor ->
            anchor.offset.toFloat() + draggedDistance
        }

    private fun beginDrag(item: LazyListItemInfo, initialIndex: Int, initialPointerY: Float) {
        stopAutoScroll()
        draggedDistance = 0f
        targetMovementRemainderPx = 0f
        val visibleItems = lazyListState.layoutInfo.visibleItemsInfo
        val next = visibleItems.firstOrNull { it.index == item.index + 1 }
        val previous = visibleItems.firstOrNull { it.index == item.index - 1 }
        slotStepPx = when {
            next != null -> abs(next.offset - item.offset).toFloat()
            previous != null -> abs(item.offset - previous.offset).toFloat()
            else -> item.size.toFloat()
        }.coerceAtLeast(item.size.toFloat()).coerceAtLeast(1f)
        initiallyDraggedElement = item
        currentIndexOfDraggedItem = initialIndex
        pointerY = initialPointerY
    }

    fun hasVisibleRowAt(y: Float): Boolean =
        lazyListState.layoutInfo.visibleItemsInfo.any { y >= it.offset && y < it.offset + it.size }

    fun onDragStartAtPosition(
        position: Offset,
        containerWidthPx: Float,
        handleWidthPx: Float,
        trailingInsetPx: Float
    ): Boolean {
        val handleRight = containerWidthPx - trailingInsetPx
        val handleLeft = handleRight - handleWidthPx
        if (position.x !in handleLeft..handleRight) return false
        val item = lazyListState.layoutInfo.visibleItemsInfo.firstOrNull {
            position.y >= it.offset && position.y < it.offset + it.size
        } ?: return false
        beginDrag(item, item.index, position.y)
        return true
    }

    fun onDragStart(offset: Offset) {
        val item = lazyListState.layoutInfo.visibleItemsInfo.firstOrNull {
            offset.y.toInt() in it.offset..(it.offset + it.size)
        } ?: return
        beginDrag(item, item.index, offset.y)
    }

    /** Start from the row and the exact pointer position inside its drag handle. */
    fun onDragStartAtIndex(index: Int, handleLocalY: Float, handleHeightPx: Float) {
        val item = lazyListState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == index } ?: return
        val handleTopInRow = ((item.size - handleHeightPx) / 2f).coerceAtLeast(0f)
        beginDrag(
            item = item,
            initialIndex = item.index,
            initialPointerY = item.offset + handleTopInRow + handleLocalY
        )
    }

    fun onDragInterrupted(commit: Boolean) {
        val originalIndex = initiallyDraggedElement?.index ?: -1
        val targetIndex = currentIndexOfDraggedItem ?: originalIndex
        val shouldCommit = commit &&
            originalIndex >= 0 &&
            targetIndex >= 0 &&
            originalIndex != targetIndex

        recentlyDraggedItemKey = initiallyDraggedElement?.key.takeIf { shouldCommit }
        stopAutoScroll()

        // The callback commits at most once. On cancellation it only discards the
        // local preview; the canonical queue was never mutated during the drag.
        onDragFinished(originalIndex, targetIndex, shouldCommit)

        initiallyDraggedElement = null
        currentIndexOfDraggedItem = null
        draggedDistance = 0f
        pointerY = 0f
    }

    /**
     * Accumulated movement advances the placeholder after the dragged row crosses
     * roughly half a slot. The floating card is independent of LazyColumn placement.
     */
    fun clearRecentlyDraggedItemKey(key: Any?) {
        if (recentlyDraggedItemKey == key) recentlyDraggedItemKey = null
    }

    private fun advanceTargetBy(deltaPx: Float) {
        val anchor = initiallyDraggedElement ?: return
        targetMovementRemainderPx += deltaPx
        val threshold = slotStepPx * 0.55f
        val lastIndex = (lazyListState.layoutInfo.totalItemsCount - 1).coerceAtLeast(0)
        var target = currentIndexOfDraggedItem ?: anchor.index

        while (targetMovementRemainderPx > threshold && target < lastIndex) {
            target += 1
            targetMovementRemainderPx -= slotStepPx
        }
        while (targetMovementRemainderPx < -threshold && target > 0) {
            target -= 1
            targetMovementRemainderPx += slotStepPx
        }
        currentIndexOfDraggedItem = target
    }

    fun onDrag(dragAmount: Offset) {
        if (initiallyDraggedElement == null) return
        draggedDistance += dragAmount.y
        pointerY += dragAmount.y
        advanceTargetBy(dragAmount.y)
        updateAutoScroll()
    }

    private fun updateAutoScroll() {
        val layoutInfo = lazyListState.layoutInfo
        val viewportStart = layoutInfo.viewportStartOffset.toFloat()
        val viewportEnd = layoutInfo.viewportEndOffset.toFloat()
        val viewportHeight = (viewportEnd - viewportStart).coerceAtLeast(1f)
        val edgeSize = edgeScrollThresholdPx.coerceAtMost(viewportHeight / 3f).coerceAtLeast(1f)

        val topProximity = ((viewportStart + edgeSize - pointerY) / edgeSize).coerceIn(0f, 1f)
        val bottomProximity = ((pointerY - (viewportEnd - edgeSize)) / edgeSize).coerceIn(0f, 1f)
        val direction = when {
            topProximity > 0f -> -1f
            bottomProximity > 0f -> 1f
            else -> 0f
        }
        val intensity = when {
            topProximity > 0f -> 0.12f + 0.88f * topProximity
            bottomProximity > 0f -> 0.12f + 0.88f * bottomProximity
            else -> 0f
        }

        if (direction == 0f || currentIndexOfDraggedItem == null) {
            stopAutoScroll()
            return
        }

        autoScrollDirection = direction
        autoScrollIntensity = intensity
        if (autoScrollJob?.isActive == true) return

        autoScrollJob = scope.launch {
            var lastFrameNanos = 0L
            while (autoScrollDirection != 0f && currentIndexOfDraggedItem != null) {
                // Use display vsync directly. Decorative animation FPS caps must not
                // pause or starve a user-controlled drag scroll.
                val frameNanos = withFrameNanos { it }
                val elapsedNanos = if (lastFrameNanos > 0L && frameNanos > lastFrameNanos) {
                    (frameNanos - lastFrameNanos).coerceAtMost(50_000_000L)
                } else {
                    16_666_667L
                }
                lastFrameNanos = frameNanos

                val pixelsPerSecond = maxAutoScrollSpeedPxPerSecond * autoScrollIntensity
                val requested = autoScrollDirection * pixelsPerSecond *
                    (elapsedNanos / 1_000_000_000f)
                val consumed = lazyListState.scrollBy(requested)
                // Scrolling the list under a stationary finger advances the target too.
                if (kotlin.math.abs(consumed) >= 0.1f) advanceTargetBy(consumed)

                if (kotlin.math.abs(consumed) < 0.1f) {
                    // At the absolute list boundary. A new drag event can restart this
                    // loop if the user moves away from the boundary and re-enters.
                    autoScrollDirection = 0f
                    autoScrollIntensity = 0f
                    autoScrollJob = null
                    break
                }
            }
        }
    }

    private fun stopAutoScroll() {
        autoScrollDirection = 0f
        autoScrollIntensity = 0f
        autoScrollJob?.cancel()
        autoScrollJob = null
    }
}

@Composable
internal fun ClearRecentDraggedItemAnimationSkip(dragDropState: DragDropState) {
    val recentlyDraggedKey = dragDropState.recentlyDraggedItemKey
    LaunchedEffect(dragDropState, recentlyDraggedKey) {
        val key = recentlyDraggedKey ?: return@LaunchedEffect
        // Let the released row snap directly to its final slot for one composed frame.
        // Re-enabling animateItem before that frame can replay its old (pre-drag) position.
        withFrameNanos { _ -> Unit }
        dragDropState.clearRecentlyDraggedItemKey(key)
    }
}

@Composable
fun rememberDragDropState(
    lazyListState: LazyListState,
    onDragFinished: (Int, Int, Boolean) -> Unit = { _, _, _ -> }
): DragDropState {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val edgeScrollThresholdPx = with(density) { 104.dp.toPx() }
    val maxAutoScrollSpeedPxPerSecond = with(density) { 620.dp.toPx() }
    val currentOnDragFinished = rememberUpdatedState(onDragFinished)
    return remember(lazyListState, edgeScrollThresholdPx, maxAutoScrollSpeedPxPerSecond) {
        DragDropState(
            lazyListState = lazyListState,
            scope = scope,
            edgeScrollThresholdPx = edgeScrollThresholdPx,
            maxAutoScrollSpeedPxPerSecond = maxAutoScrollSpeedPxPerSecond,
            onDragFinished = { from, to, commit -> currentOnDragFinished.value(from, to, commit) }
        )
    }
}


/**
 * Keep queue drag pointer ownership outside LazyColumn's virtualized rows.
 *
 * A row-local pointerInput is cancelled when edge auto-scroll moves that row outside
 * LazyColumn's composition window. This stable viewport handler survives that scroll,
 * while accepting long-press starts only inside the trailing 48dp drag handle.
 */
fun Modifier.queueDragHandleInput(
    dragDropState: DragDropState,
    handleWidthPx: Float,
    trailingInsetPx: Float
): Modifier = pointerInput(dragDropState, handleWidthPx, trailingInsetPx) {
    awaitEachGesture {
        val down = awaitFirstDown(
            requireUnconsumed = false,
            pass = PointerEventPass.Initial
        )
        val handleRight = size.width.toFloat() - trailingInsetPx
        val handleLeft = handleRight - handleWidthPx
        val startsOnHandle = down.position.x in handleLeft..handleRight &&
            dragDropState.hasVisibleRowAt(down.position.y)

        if (!startsOnHandle) {
            // Observe but do not consume normal row taps, swipes or parent gestures.
            do {
                val event = awaitPointerEvent(PointerEventPass.Initial)
            } while (event.changes.any { it.pressed })
        } else {
            var lastObservedPosition = down.position
            // Watch in the Initial pass so the child handle's no-op clickable cannot
            // consume the down before the stable parent sees the long-press timeout.
            val longPressReached = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val change = event.changes.firstOrNull { it.id == down.id }
                        ?: return@withTimeoutOrNull false
                    if (!change.pressed) return@withTimeoutOrNull false
                    lastObservedPosition = change.position
                    if ((change.position - down.position).getDistance() > viewConfiguration.touchSlop) {
                        // A pre-long-press move remains available to LazyColumn scrolling.
                        return@withTimeoutOrNull false
                    }
                }
                false
            } == null

            if (longPressReached) {
                val started = dragDropState.onDragStartAtPosition(
                    position = down.position,
                    containerWidthPx = size.width.toFloat(),
                    handleWidthPx = handleWidthPx,
                    trailingInsetPx = trailingInsetPx
                )
                if (started) {
                    // Include any small in-bounds finger drift during the long-press wait.
                    val initialDrift = lastObservedPosition - down.position
                    if (initialDrift != Offset.Zero) dragDropState.onDrag(initialDrift)

                    var pointerStillDown = true
                    while (pointerStillDown) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) {
                            pointerStillDown = false
                        } else {
                            val delta = change.position - change.previousPosition
                            if (delta != Offset.Zero) dragDropState.onDrag(delta)
                            change.consume()
                        }
                    }
                    dragDropState.onDragInterrupted(commit = true)
                }
            } else {
                // The gesture ended or moved before long-press; leave it to the row/list.
                do {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                } while (event.changes.any { it.pressed })
            }
        }
    }
}

fun Modifier.dragContainer(dragDropState: DragDropState, enabled: Boolean): Modifier {
    if (!enabled) return this
    return this.then(
        Modifier.pointerInput(dragDropState) {
            detectDragGesturesAfterLongPress(
                onDragStart = { offset -> dragDropState.onDragStart(offset) },
                onDragEnd = { dragDropState.onDragInterrupted(commit = true) },
                onDragCancel = { dragDropState.onDragInterrupted(commit = false) },
                onDrag = { change, dragAmount ->
                    change.consume()
                    dragDropState.onDrag(dragAmount)
                }
            )
        }
    )
}


/**
 * Opens the regular song action sheet from a queue row, excluding the redundant
 * "Add to queue" action because the track is already in the active queue.
 */
fun showQueueSongActions(
    context: Context,
    song: SongItem,
    viewModel: PlayerSharedViewModel
) {
    val activity = context.findFragmentActivity() ?: return
    val fragmentManager = activity.supportFragmentManager
    if (fragmentManager.isStateSaved) return

    val sheet = SongActionsBottomSheet.newInstance(song, showAddToQueue = false, showRemoveFromQueue = true, showDelete = false, queueSongMode = true)
    sheet.setActionListener(object : SongActionsBottomSheet.ActionListener {
        override fun onPlay(song: SongItem) {
            viewModel.playSong(song)
        }

        override fun onPlayNext(song: SongItem) {
            viewModel.addToQueueNext(song)
        }

        override fun onAddToQueue(song: SongItem) = Unit
        override fun onDelete(song: SongItem) { viewModel.removeFromQueue(song.id) }
        override fun onEdit(song: SongItem) = Unit

        override fun onFavorite(song: SongItem, isFav: Boolean) {
            FavoritesManager(context).setFavorite(song.id, isFav)
        }

        override fun onShare(song: SongItem) {
            runCatching {
                val shareText = song.videoId?.takeIf { it.isNotBlank() }
                    ?.let { "https://music.youtube.com/watch?v=$it" }
                    ?: song.contentUri.toString()
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, "Listen to ${song.title}: $shareText")
                }
                context.startActivity(Intent.createChooser(intent, "Share Song"))
            }
        }
    })
    sheet.show(fragmentManager, "QueueSongActionsBottomSheet")
}

private fun Context.findFragmentActivity(): FragmentActivity? {
    var current: Context = this
    while (current is ContextWrapper) {
        if (current is FragmentActivity) return current
        current = current.baseContext
    }
    return current as? FragmentActivity
}
