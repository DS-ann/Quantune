package com.dsann.music.ui.player

import com.dsann.music.CrashDiagnostics


import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import com.dsann.music.ui.theme.Button
import com.dsann.music.ui.theme.LoadingIndicator
import com.dsann.music.ui.theme.Icon
import com.dsann.music.ui.theme.DsmusicTheme
import com.dsann.music.ui.theme.dsmusicHomeStudioGradientBrush
import com.dsann.music.ui.theme.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.roundToInt
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import com.dsann.music.R
import com.dsann.music.data.lyrics.LyricLine
import com.dsann.music.data.lyrics.LyricsResult
import com.dsann.music.model.SongItem
import com.dsann.music.ui.theme.GoogleSansFlexNonRounded

// ════════════════════════════════════════════════════════════════════════════════
// ─ Data Classes & Constants
// ════════════════════════════════════════════════════════════════════════════════

private data class LyricDisplayItem(
    val line: LyricLine,
    val originalIndex: Int,
    val isActive: Boolean = false,
    val isPast: Boolean = false
)

private data class PreparedSyncedLyrics(
    val isKaraokeMode: Boolean,
    val displayItems: List<LyricDisplayItem>,
    val scrollBoundaries: LyricsScrollBoundaries,
    val lastWordEndMs: Long?,
    val totalWordCount: Int,
    val maxWordsPerLine: Int,
    val totalTextCharacters: Long,
    val longestLineCharacters: Int,
    val linesWithBackground: Int
)

private fun prepareSyncedLyrics(lyrics: List<LyricLine>): PreparedSyncedLyrics {
    val isKaraokeMode = lyrics.any { !it.isInterlude && it.isWordByWord && it.words.isNotEmpty() }
    val displayItems = lyrics.mapIndexedNotNull { index, line ->
        if (line.isInterlude) null
        else LyricDisplayItem(line = line, originalIndex = index)
    }.sortedWith(
        compareBy<LyricDisplayItem> { it.line.startTimeMs }
            .thenBy { it.originalIndex }
    )
    val displayLines = displayItems.map { it.line }
    val scrollBoundaries = buildLyricsScrollBoundaries(displayLines)
    val lastLyric = displayItems.lastOrNull()?.line
    val lastWordEndMs = lastLyric?.words
        ?.maxOfOrNull { it.absoluteStartTimeMs + it.durationMs }
        ?.takeIf { it > 0L }

    val totalWordCount = displayItems.sumOf { it.line.words.size + it.line.backgroundWords.size }
    val maxWordsPerLine = displayItems.maxOfOrNull {
        maxOf(it.line.words.size, it.line.backgroundWords.size)
    } ?: 0
    val totalTextCharacters = displayItems.sumOf {
        it.line.content.length.toLong() + (it.line.backgroundContent?.length ?: 0).toLong()
    }
    val longestLineCharacters = displayItems.maxOfOrNull {
        maxOf(it.line.content.length, it.line.backgroundContent?.length ?: 0)
    } ?: 0
    val linesWithBackground = displayItems.count {
        !it.line.backgroundContent.isNullOrBlank()
    }

    return PreparedSyncedLyrics(
        isKaraokeMode = isKaraokeMode,
        displayItems = displayItems,
        scrollBoundaries = scrollBoundaries,
        lastWordEndMs = lastWordEndMs,
        totalWordCount = totalWordCount,
        maxWordsPerLine = maxWordsPerLine,
        totalTextCharacters = totalTextCharacters,
        longestLineCharacters = longestLineCharacters,
        linesWithBackground = linesWithBackground
    )
}

internal data class LyricsScrollBoundaries(
    val lyricIndices: IntArray,
    val completionTimes: LongArray
)

/** Precompute the existing line-completion sequence once per lyric payload. */
internal fun buildLyricsScrollBoundaries(lines: List<LyricLine>): LyricsScrollBoundaries {
    val lyricIndices = lines.indices.filter { !lines[it].isInterlude }.toIntArray()
    if (lyricIndices.size < 2) {
        return LyricsScrollBoundaries(lyricIndices, LongArray(0))
    }

    val completionTimes = LongArray(lyricIndices.size - 1)
    var greatestCompletionTime = Long.MIN_VALUE
    for (index in completionTimes.indices) {
        val currentLine = lines[lyricIndices[index]]
        val nextLine = lines[lyricIndices[index + 1]]
        val finalWordEnd = currentLine.takeIf { it.isWordByWord && it.words.isNotEmpty() }
            ?.words
            ?.asSequence()
            ?.filter { it.durationMs > 0L }
            ?.map { it.absoluteStartTimeMs + it.durationMs }
            ?.maxOrNull()
            ?.takeIf { it > 0L }
        val nextStart = if (nextLine.isWordByWord && nextLine.words.isNotEmpty()) {
            nextLine.words.minOfOrNull { it.absoluteStartTimeMs } ?: nextLine.startTimeMs
        } else {
            nextLine.startTimeMs
        }
        // Word-timed lines begin scrolling as soon as their final word ends. Ordinary
        // line-synced sources have no explicit end; reserve the last third of the
        // inter-line interval (up to 500 ms) for a timed transition.
        val interval = (nextStart - currentLine.startTimeMs).coerceAtLeast(0L)
        val inferredLeadWindow = minOf(
            interval,
            (interval / 3L).coerceAtLeast(1L).coerceAtMost(500L)
        )
        val completionTime = if (finalWordEnd != null && finalWordEnd < nextStart) {
            finalWordEnd
        } else {
            (nextStart - inferredLeadWindow).coerceAtLeast(currentLine.startTimeMs)
        }
        greatestCompletionTime = maxOf(greatestCompletionTime, completionTime)
        completionTimes[index] = greatestCompletionTime
    }
    return LyricsScrollBoundaries(lyricIndices, completionTimes)
}

/**
 * Binary search over prefix maxima returns exactly the line the original sequential
 * scan stopped at, including when provider timestamps overlap or arrive out of order.
 */
internal fun findLyricsScrollTargetIndex(
    boundaries: LyricsScrollBoundaries,
    positionMs: Long
): Int {
    val lyricIndices = boundaries.lyricIndices
    if (lyricIndices.isEmpty()) return -1
    if (boundaries.completionTimes.isEmpty()) return lyricIndices.first()

    var low = 0
    var high = boundaries.completionTimes.lastIndex
    var lastCompleted = -1
    while (low <= high) {
        val middle = (low + high) ushr 1
        if (positionMs >= boundaries.completionTimes[middle]) {
            lastCompleted = middle
            low = middle + 1
        } else {
            high = middle - 1
        }
    }
    return lyricIndices[(lastCompleted + 1).coerceIn(0, lyricIndices.lastIndex)]
}

// Apple Music-inspired easing curves
private object AppleMusicEasing {
    val smoothEnter = CubicBezierEasing(0.25f, 0.46f, 0.45f, 0.94f)
    val smoothExit = CubicBezierEasing(0.25f, 0.46f, 0.45f, 0.94f)
    val wordSweep = CubicBezierEasing(0.33f, 0.66f, 0.66f, 1.00f)
    val scaleDown = CubicBezierEasing(0.4f, 0.0f, 1.0f, 1.0f)
}

// ════════════════════════════════════════════════════════════════════════════════
// ─ Main Synced Lyrics Composable
// ════════════════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun SyncedLyricsCompose(
    onSeekTo: (Int) -> Unit,
    lyrics: List<LyricLine>,
    currentPositionProvider: () -> Int,
    playbackSpeedProvider: () -> Float = { 1f },
    isPlaying: Boolean,
    contentColor: Color,
    dynamicAccentColor: Color,
    currentSong: SongItem? = null,
    selectedProvider: String? = null,
    providerResults: Map<String, LyricsResult> = emptyMap(),
    lyricsColorStyle: String = "Home Studio Gradient",
    lyricsTextSize: String,
    lyricsAlignment: String,
    lyricsFontFamily: String,
    gradientFontBorderWidth: Dp = 1.dp,
    centerLineLyrics: Boolean = false,
    forceLowCostRendering: Boolean = false,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val configuredLyricsScale = when (lyricsTextSize) {
        "Small" -> 0.85f
        "Large" -> 1.18f
        else -> 1f
    }
    val isLandscape = LocalConfiguration.current.orientation ==
        android.content.res.Configuration.ORIENTATION_LANDSCAPE
    // Compact landscape callers and devices with constrained graphics/memory
    // use the same timed renderer with simpler paint: no glyph outline or glow layer.
    // This deliberately keeps karaoke word timing and the visible sweep intact.

    val currentView = LocalView.current
    DisposableEffect(currentView) {
        currentView.keepScreenOn = true
        onDispose {
            currentView.keepScreenOn = false
        }
    }

    // Sorting, karaoke detection and word-boundary scans can be costly for
    // large karaoke payloads. Prepare them away from the UI thread so activating
    // the lyrics view never blocks the first frame after its entrance transition.
    var preparedSyncedLyrics by remember(lyrics) {
        mutableStateOf<PreparedSyncedLyrics?>(null)
    }
    LaunchedEffect(lyrics) {
        preparedSyncedLyrics = null
        val preparationStartedAt = android.os.SystemClock.elapsedRealtime()
        CrashDiagnostics.updateLyricsPreparation(
            "started; inputLines=${lyrics.size}; callerThread=${Thread.currentThread().name}"
        )
        try {
            val prepared = withContext(Dispatchers.Default) {
                prepareSyncedLyrics(lyrics)
            }
            val elapsedMs = android.os.SystemClock.elapsedRealtime() - preparationStartedAt
            CrashDiagnostics.updateLyricsPreparation(
                "ready; inputLines=${lyrics.size}; displayLines=${prepared.displayItems.size}; " +
                    "words=${prepared.totalWordCount}; elapsed=${elapsedMs}ms; worker=Dispatchers.Default"
            )
            preparedSyncedLyrics = prepared
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            CrashDiagnostics.updateLyricsPreparation(
                "cancelled; inputLines=${lyrics.size}; elapsed=${android.os.SystemClock.elapsedRealtime() - preparationStartedAt}ms"
            )
            throw cancelled
        } catch (failure: Exception) {
            CrashDiagnostics.updateLyricsPreparation(
                "failed; inputLines=${lyrics.size}; elapsed=${android.os.SystemClock.elapsedRealtime() - preparationStartedAt}ms; " +
                    "error=${failure.javaClass.simpleName}: ${failure.message.orEmpty().take(180)}"
            )
            throw failure
        }
    }
    val isLyricsPrepared = preparedSyncedLyrics != null
    val isKaraokeMode = preparedSyncedLyrics?.isKaraokeMode ?: false
    // The UI's neutral timing value is 0 ms; karaoke playback uses a fixed +20 ms
    // internal baseline so this compensation is invisible in the adjustment controls.
    val timedPositionProvider: () -> Int = remember(currentPositionProvider, isKaraokeMode) {
        if (isKaraokeMode) {
            {
                (currentPositionProvider().toLong() + DEFAULT_KARAOKE_TIMING_BASE_OFFSET_MS)
                    .coerceIn(0L, Int.MAX_VALUE.toLong())
                    .toInt()
            }
        } else {
            currentPositionProvider
        }
    }
    // Rendering quality is not reduced based on the device's RAM classification.
    // Only an explicit caller request may select the optional low-cost presentation.
    val useLowCostLyricsRendering = forceLowCostRendering
    // Follow the selected app/device frame rate without a RAM-based FPS ceiling.
    val karaokeFrameTimeNanos = rememberAppAnimationFrameTimeNanos(
        active = isPlaying && isKaraokeMode
    )

    // The prepared row list and all word-time boundaries were built off-main.
    // Keep the empty fallback stable while the worker result is being prepared.
    val displayItems = preparedSyncedLyrics?.displayItems.orEmpty()

    // Interpolate from Media3's live playback position at display-frame cadence. The
    // dedicated clock applies bounded phase correction and re-anchors on seeks, pauses,
    // invalid frame gaps and decoder clock discontinuities.
    // Keep the displayed clock scoped to the active track so a new queue item cannot
    // inherit the previous track's final lyric position.
    // Online SongItem IDs are derived from String.hashCode(). Use the original video ID
    // as the primary identity so a hash collision or repeated queue entry cannot inherit
    // another track's final lyric clock.
    val lyricTrackKey = currentSong?.videoId?.takeIf { it.isNotBlank() }
        ?: currentSong?.path?.takeIf { it.isNotBlank() }
        ?: currentSong?.id?.toString()
    val lyricClock = remember(lyricTrackKey) {
        mutableIntStateOf(timedPositionProvider().coerceAtLeast(0))
    }
    val karaokePlaybackClock = remember(lyricTrackKey) { KaraokePlaybackClock() }
    LaunchedEffect(
        isPlaying, lyricTrackKey, lyrics, timedPositionProvider, playbackSpeedProvider,
        isKaraokeMode, isLyricsPrepared
    ) {
        if (!isLyricsPrepared) return@LaunchedEffect
        val initialPosition = timedPositionProvider().coerceAtLeast(0)
        var previousPosition = initialPosition
        karaokePlaybackClock.reset(initialPosition)
        lyricClock.intValue = initialPosition

        if (isPlaying && isKaraokeMode) {
            // Collect one continuous stream from the shared display-VSYNC clock.
            // No timer or second frame loop competes with the renderer.
            snapshotFlow { karaokeFrameTimeNanos.value }
                .filter { it > 0L }
                .collect { frameNanos ->
                    val actualMs = timedPositionProvider().coerceAtLeast(0)
                    val framePosition = if (
                        actualMs < previousPosition - 750 &&
                        actualMs <= (displayItems.firstOrNull()?.line?.startTimeMs ?: 0L) + 1200L
                    ) {
                        // Re-anchor word timing when a track restarts from the beginning.
                        karaokePlaybackClock.reset(actualMs)
                        actualMs
                    } else {
                        karaokePlaybackClock.update(
                            frameTimeNanos = frameNanos,
                            actualPositionMs = actualMs,
                            playbackSpeed = playbackSpeedProvider(),
                            isPlaying = true
                        )
                    }
                    previousPosition = actualMs
                    if (lyricClock.intValue != framePosition) {
                        lyricClock.intValue = framePosition
                    }
                }
        } else {
            while (true) {
                if (!isPlaying) {
                    // Keep paused seeks responsive without running an idle display-frame loop.
                    val actualMs = timedPositionProvider().coerceAtLeast(0)
                    val rewoundToStart = actualMs < previousPosition - 750 &&
                        actualMs <= (displayItems.firstOrNull()?.line?.startTimeMs ?: 0L) + 1200L
                    karaokePlaybackClock.reset(actualMs)
                    if (rewoundToStart) listState.scrollToItem(0)
                    if (lyricClock.intValue != actualMs) lyricClock.intValue = actualMs
                    previousPosition = actualMs
                    delay(if (android.os.Build.VERSION.SDK_INT <= android.os.Build.VERSION_CODES.M) 80L else 60L)
                } else {
                    // Ordinary synced lyrics only need accurate line changes, not word-sweep FPS.
                    val interval = if (android.os.Build.VERSION.SDK_INT <= android.os.Build.VERSION_CODES.M) 84L else 40L
                    delay(interval)
                    val actualMs = timedPositionProvider().coerceAtLeast(0)
                    val rewoundToStart = actualMs < previousPosition - 750 &&
                        actualMs <= (displayItems.firstOrNull()?.line?.startTimeMs ?: 0L) + 1200L
                    if (rewoundToStart) {
                        // A replay/restart of the same song does not change currentSong.id.
                        // Reset the active clock and list offset together, rather than waiting
                        // for a smooth-scroll animation from the previous song's final line.
                        karaokePlaybackClock.reset(actualMs)
                        listState.scrollToItem(0)
                    }
                    if (lyricClock.intValue != actualMs) lyricClock.intValue = actualMs
                    previousPosition = actualMs
                }
            }
        }
    }
    // Derived line selection now reads precomputed tail timing rather than scanning
    // the final karaoke word list during each playback-clock update.
    val activeIndex by remember(displayItems, lyricClock, currentSong?.duration, preparedSyncedLyrics) {
        derivedStateOf {
            val currentPos = lyricClock.intValue
            val lastWordEndMs = preparedSyncedLyrics?.lastWordEndMs
            val songDurationMs = currentSong?.duration?.takeIf { it > 0L }
            val hasPassedFinalLyric = when {
                songDurationMs != null && currentPos.toLong() >= songDurationMs -> true
                lastWordEndMs != null && currentPos.toLong() > lastWordEndMs + 1000L -> true
                else -> false
            }

            if (!isLyricsPrepared || displayItems.isEmpty() || hasPassedFinalLyric) -1
            else binarySearchActiveIndex(displayItems, currentPos)
        }
    }

    val emptyScrollBoundaries = remember {
        LyricsScrollBoundaries(IntArray(0), LongArray(0))
    }
    val scrollBoundaries = preparedSyncedLyrics?.scrollBoundaries ?: emptyScrollBoundaries
    val scrollTargetIndex by remember(scrollBoundaries, lyricClock) {
        derivedStateOf {
            findLyricsScrollTargetIndex(
                boundaries = scrollBoundaries,
                positionMs = lyricClock.intValue.toLong()
            )
        }
    }

    SideEffect {
        val prepared = preparedSyncedLyrics
        val visibleRows = listState.layoutInfo.visibleItemsInfo.size
        val frameRate = AppAnimationFrameRateController.frameRate.value
        CrashDiagnostics.updateLyricsRendering(
            "Synced/Karaoke; renderer=${if (isKaraokeMode) "cached TextLayoutResult + direct Canvas word sweep" else "single TextLayoutResult + Canvas contour"}; " +
                "prepared=$isLyricsPrepared; lines=${prepared?.displayItems?.size ?: lyrics.size}; " +
                "visibleRows=$visibleRows; totalWords=${prepared?.totalWordCount ?: -1}; " +
                "maxWordsPerLine=${prepared?.maxWordsPerLine ?: -1}; " +
                "totalTextChars=${prepared?.totalTextCharacters ?: -1}; " +
                "longestLineChars=${prepared?.longestLineCharacters ?: -1}; " +
                "backgroundLines=${prepared?.linesWithBackground ?: -1}; " +
                "activeIndex=$activeIndex; scrollTarget=$scrollTargetIndex; " +
                "karaoke=$isKaraokeMode; playing=$isPlaying; frameRatePref=$frameRate; " +
                "rowAlpha=colour modulation; offscreenRowLayers=disabled; karaokeBlur=disabled; " +
                "outlineWidthDp=$gradientFontBorderWidth"
        )
    }
    val density = LocalDensity.current
    // The first frame after opening a long track should position immediately. A long
    // animated jump from item 0 to the current song position can stall weaker devices.
    val hasPositionedInitialLyrics = remember(lyricTrackKey) {
        androidx.compose.runtime.mutableStateOf(false)
    }
    LaunchedEffect(
        lyricTrackKey, displayItems, scrollTargetIndex, density, isLyricsPrepared
    ) {
        if (!isLyricsPrepared) return@LaunchedEffect
        if (scrollTargetIndex == 0) {
            performSmoothedScroll(
                listState,
                0,
                displayItems,
                with(density) { 36.dp.roundToPx() },
                immediateInitialPosition = true
            )
            hasPositionedInitialLyrics.value = true
        } else if (scrollTargetIndex > 0) {
            val isInitialPosition = !hasPositionedInitialLyrics.value
            val nextLine = displayItems[scrollTargetIndex].line
            val nextFirstWordStartMs = if (nextLine.isWordByWord && nextLine.words.isNotEmpty()) {
                nextLine.words.minOfOrNull { it.absoluteStartTimeMs } ?: nextLine.startTimeMs
            } else {
                nextLine.startTimeMs
            }
            val playbackPositionMs = lyricClock.intValue.toLong()
            val remainingUntilNextLineMs =
                (nextFirstWordStartMs - playbackPositionMs).coerceAtLeast(0L)
            val playbackSpeed = playbackSpeedProvider()
                .takeIf { it.isFinite() && it > 0f } ?: 1f
            // Finish 50 ms before the next line starts so that the newly highlighted
            // line is already at the centre anchor when its first word begins.
            val finishLeadMs = if (remainingUntilNextLineMs > 80L) 50L else 0L
            val scrollDurationMs = if (remainingUntilNextLineMs > 0L) {
                ((remainingUntilNextLineMs - finishLeadMs).coerceAtLeast(16L) /
                    playbackSpeed).roundToInt().coerceAtLeast(16)
            } else {
                16
            }

            performSmoothedScroll(
                listState,
                scrollTargetIndex,
                displayItems,
                with(density) { 36.dp.roundToPx() },
                scrollDurationMs = scrollDurationMs,
                immediateInitialPosition = isInitialPosition
            )
            hasPositionedInitialLyrics.value = true
        } else if (lyricClock.intValue <= (displayItems.firstOrNull()?.line?.startTimeMs ?: 0L)) {
            listState.scrollToItem(0)
        }
    }

    // Avoid a full-screen offscreen composition on every karaoke frame.
    // The active-line emphasis and generous edge padding keep the reading focus clear.
    BoxWithConstraints(modifier = modifier) {
        if (!isLyricsPrepared) {
            LoadingIndicator(
                modifier = Modifier.align(Alignment.Center).size(32.dp),
                color = dynamicAccentColor
            )
        } else {
        // The two-pane landscape player has a much shorter lyrics viewport than portrait.
        // Scale against that real viewport instead of keeping the portrait 38sp lyric size.
        val viewportScale = if (isLandscape && maxHeight != Dp.Infinity) {
            (maxHeight.value / 420f).coerceIn(0.58f, 0.86f)
        } else {
            1f
        }
        val lyricsScale = (configuredLyricsScale * viewportScale).coerceIn(0.48f, 1.18f)

        // Reserve half the viewport at both ends so even the first and last lines
        // can occupy the same centred reading position as every line between them.
        val activeLineEdgePadding = (maxHeight / 2f - 36.dp).coerceAtLeast(40.dp)
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = activeLineEdgePadding,
                bottom = activeLineEdgePadding
            ),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            itemsIndexed(
                items = displayItems,
                key = { _, item -> "${item.line.startTimeMs}_${item.originalIndex}" }
            ) { displayIndex, item ->
                val line = item.line
                val isActive = displayIndex == activeIndex
                val isPast = displayIndex < activeIndex

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                ) {
                    if (line.isInterlude) {
                        if (isActive) {
                            InterludeItem(
                                line = line,
                                accentColor = dynamicAccentColor,
                                contentColor = contentColor
                            )
                        }
                    } else {
                        LyricLineItem(
                            line = line,
                            isActive = isActive,
                            isPast = isPast,
                            currentPositionState = lyricClock,
                            lineStartMs = line.startTimeMs,
                            lineEndMs = if (item.originalIndex + 1 < lyrics.size) {
                                lyrics[item.originalIndex + 1].startTimeMs
                            } else {
                                line.startTimeMs + 5000
                            },
                            contentColor = contentColor,
                            accentColor = dynamicAccentColor,
                            lyricsColorStyle = lyricsColorStyle,
                            textScale = lyricsScale,
                            alignment = lyricsAlignment,
                            gradientFontBorderWidth = gradientFontBorderWidth,
                            centerLineLyrics = centerLineLyrics,
                            fontFamilyChoice = lyricsFontFamily,
                            lowCostRendering = useLowCostLyricsRendering,
                            onClick = {
                                onSeekTo(
                                    line.startTimeMs.coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
                                )
                            }
                        )
                    }
                }
            }

            // ── Metadata footer at the end of lyrics ──
            item(key = "lyrics_metadata_footer") {
                LyricsMetadataFooter(
                    currentSong = currentSong,
                    selectedProvider = selectedProvider,
                    providerResults = providerResults,
                    contentColor = contentColor
                )
            }
        }
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════════
// ─ Lyric Line Item Composable - APPLE MUSIC STYLE HIGHLIGHTING
// ════════════════════════════════════════════════════════════════════════════════

@Composable
private fun LyricLineItem(
    line: LyricLine,
    isActive: Boolean,
    isPast: Boolean,
    currentPositionState: androidx.compose.runtime.State<Int>,
    lineStartMs: Long,
    lineEndMs: Long,
    contentColor: Color,
    accentColor: Color,
    lyricsColorStyle: String,
    textScale: Float,
    alignment: String,
    gradientFontBorderWidth: Dp = 1.dp,
    centerLineLyrics: Boolean = false,
    fontFamilyChoice: String,
    onClick: () -> Unit,
    lowCostRendering: Boolean = false
) {
    val isKaraoke = line.isWordByWord && line.words.isNotEmpty()

    if (lowCostRendering) {
        // Keep the compact renderer inexpensive, but do not remove time-synchronized
        // highlighting. The cached single-layout sweep has no offscreen layer or glyph
        // geometry rebuild, so it is suitable for the landscape/low-cost path as well.
        val lowCostAlignment = if (centerLineLyrics) {
            TextAlign.Center
        } else {
            when (alignment) {
                "Left" -> TextAlign.Start
                "Center" -> TextAlign.Center
                else -> if (line.isRightAligned) TextAlign.End else TextAlign.Start
            }
        }
        val lowCostColor = when (lyricsColorStyle) {
            "UI Matching" -> accentColor
            "White", "Home Studio Gradient" -> Color.White
            else -> contentColor
        }
        val lowCostHighlightBrush = if (lyricsColorStyle == "Home Studio Gradient") {
            dsmusicHomeStudioGradientBrush()
        } else {
            SolidColor(lowCostColor)
        }
        // Match the dim text paint used by the full-cost renderer: only the
        // active line is fully lit; surrounding lines stay deliberately subdued.
        val lowCostAlpha = when {
            isActive -> 1f
            isPast -> 0.21f
            else -> 0.13f
        }
        val lowCostFont = when (fontFamilyChoice) {
            "Sans" -> FontFamily.SansSerif
            "Serif" -> FontFamily.Serif
            "Mono" -> FontFamily.Monospace
            else -> GoogleSansFlexNonRounded
        }
        val lowCostStyle = DsmusicTheme.typography.headlineMedium.copy(
            brush = null,
            shadow = null,
            fontFamily = lowCostFont,
            fontSize = 38.sp * textScale,
            fontWeight = if (isActive) FontWeight.ExtraBold else FontWeight.Bold,
            lineHeight = 50.sp * textScale,
            textAlign = lowCostAlignment,
            platformStyle = PlatformTextStyle(includeFontPadding = true)
        )
        val lowCostBackgroundStyle = DsmusicTheme.typography.bodySmall.copy(
            fontFamily = lowCostFont,
            fontSize = 18.sp * textScale,
            lineHeight = 26.sp * textScale,
            textAlign = lowCostAlignment,
            shadow = null,
            brush = null
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .drawBehind {
                    // Keep the existing no-background design: the accent rail is the
                    // persistent, unambiguous indicator for ordinary synced lyrics.
                    if (isActive && !isKaraoke) {
                        val railHeight = (size.height - 20.dp.toPx()).coerceAtLeast(0f)
                        drawRoundRect(
                            color = accentColor.copy(alpha = 0.98f),
                            topLeft = androidx.compose.ui.geometry.Offset(5.dp.toPx(), 10.dp.toPx()),
                            size = androidx.compose.ui.geometry.Size(3.dp.toPx(), railHeight),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(
                                1.5.dp.toPx(),
                                1.5.dp.toPx()
                            )
                        )
                    }
                }
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick
                )
                .padding(
                    start = if (centerLineLyrics) 24.dp else 28.dp,
                    end = 24.dp,
                    top = 12.dp,
                    bottom = 12.dp
                )
        ) {
            if (isKaraoke && isActive) {
                KaraokeSweepText(
                    text = line.content,
                    words = line.words,
                    wordOffset = 0,
                    positionState = currentPositionState,
                    style = lowCostStyle,
                    baseColor = lowCostColor.copy(alpha = 0.22f),
                    highlightBrush = lowCostHighlightBrush,
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = Int.MAX_VALUE
                )
            } else {
                Text(
                    text = line.content,
                    style = lowCostStyle,
                    color = lowCostColor.copy(alpha = lowCostAlpha),
                    maxLines = Int.MAX_VALUE
                )
            }
            if (!line.backgroundContent.isNullOrBlank()) {
                if (isKaraoke && isActive && line.backgroundWords.isNotEmpty()) {
                    KaraokeSweepText(
                        text = line.backgroundContent,
                        words = line.backgroundWords,
                        wordOffset = 0,
                        positionState = currentPositionState,
                        style = lowCostBackgroundStyle,
                        baseColor = lowCostColor.copy(alpha = 0.18f),
                        highlightBrush = lowCostHighlightBrush,
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        maxLines = 2
                    )
                } else {
                    Text(
                        text = line.backgroundContent,
                        style = lowCostBackgroundStyle,
                        color = lowCostColor.copy(alpha = if (isActive) 0.32f else 0.16f),
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        maxLines = 2
                    )
                }
            }
        }
        return
    }
    // Keep the same 1 dp gradient font contour in the fullscreen renderer.
    val lineSyncedGradientBorderWidth = gradientFontBorderWidth
    // Preserve the previous row emphasis without applying alpha to a composited
    // graphics layer. Text alpha is cheaper and avoids an off-screen surface per row.
    val alpha = when {
        isActive -> 1f
        isPast -> 0.60f
        else -> 0.38f
    }


    val bracketMatch = remember(line.content) {
        Regex("""^(.*?)\s*([({\[].+?[)}\]])\s*$""").find(line.content)
    }
    
    val hasValidMainContent = remember(bracketMatch) {
        bracketMatch != null && bracketMatch.groupValues[1].isNotBlank()
    }

    val mainContent = remember(line.content, bracketMatch, hasValidMainContent) {
        if (hasValidMainContent) {
            bracketMatch!!.groupValues[1].trimEnd() + " "
        } else {
            line.content + " "
        }
    }
    
    val bracketContent = remember(line.content, bracketMatch, hasValidMainContent) {
        if (hasValidMainContent) {
            bracketMatch!!.groupValues[2] + " "
        } else {
            null
        }
    }
    
    val bracketStartIndex = remember(line.content, bracketMatch, hasValidMainContent) {
        if (hasValidMainContent) bracketMatch!!.groups[2]?.range?.first ?: -1 else -1
    }

    val lyricFontFamily = when (fontFamilyChoice) {
        "Sans" -> FontFamily.SansSerif
        "Serif" -> FontFamily.Serif
        "Mono" -> FontFamily.Monospace
        else -> GoogleSansFlexNonRounded
    }
    val lyricsBrush = if (lyricsColorStyle == "Home Studio Gradient") {
        dsmusicHomeStudioGradientBrush()
    } else null
    val renderedColor = when (lyricsColorStyle) {
        "UI Matching" -> accentColor
        "White", "Home Studio Gradient" -> Color.White
        else -> contentColor
    }
    // Match the previous layer opacity directly in the text paint.
    val dimColor = renderedColor.copy(alpha = 0.35f * alpha)
    val litColor = renderedColor

    val commonModifier = Modifier
        .fillMaxWidth()
        .drawBehind {
            // Keep only the slim leading indicator. The lyric row itself stays
            // completely transparent so the player artwork/background remains visible.
            if (isActive && !isKaraoke) {
                val railHeight = (size.height - 20.dp.toPx()).coerceAtLeast(0f)
                drawRoundRect(
                    color = accentColor.copy(alpha = 0.98f),
                    topLeft = androidx.compose.ui.geometry.Offset(5.dp.toPx(), 10.dp.toPx()),
                    size = androidx.compose.ui.geometry.Size(3.dp.toPx(), railHeight),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(
                        1.5.dp.toPx(),
                        1.5.dp.toPx()
                    )
                )
            }
        }
        .clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = onClick
        )
        .padding(
            start = if (centerLineLyrics) 24.dp else 28.dp,
            end = 24.dp,
            top = 12.dp,
            bottom = 12.dp
        )
        .offset {
            if (!isActive || !isKaraoke) {
                IntOffset.Zero
            } else {
                val duration = (lineEndMs - lineStartMs).coerceAtLeast(1L)
                val progress = (
                    (currentPositionState.value.toLong() - lineStartMs).toFloat() /
                        duration.toFloat()
                    ).coerceIn(0f, 1f)
                IntOffset(0, (-2f * easeOutCubic(progress)).dp.roundToPx())
            }
        }

    // In the immersive fullscreen player, all lyric lines use a fixed horizontal
    // centre. The inline player continues to respect the saved lyrics alignment.
    val lineAlignment = if (centerLineLyrics) {
        TextAlign.Center
    } else {
        when (alignment) {
            "Left" -> TextAlign.Start
            "Center" -> TextAlign.Center
            else -> if (line.isRightAligned) TextAlign.End else TextAlign.Start
        }
    }
    // Ordinary line-synced lyrics are rendered once, without a backdrop or
    // glow layer. Karaoke keeps its separate word-timing overlay below.
    val mainTextStyle = DsmusicTheme.typography.headlineMedium.copy(
        brush = lyricsBrush,
        fontFamily = lyricFontFamily,
        fontSize = 38.sp * textScale,
        fontWeight = if (isActive && !isKaraoke) FontWeight.ExtraBold else FontWeight.Bold,
        lineHeight = 50.sp * textScale,
        textAlign = lineAlignment,
        shadow = null,
        platformStyle = PlatformTextStyle(includeFontPadding = true)
    )

    val bracketTextStyle = DsmusicTheme.typography.bodyLarge.copy(
        brush = lyricsBrush,
        fontFamily = GoogleSansFlexNonRounded,
        fontSize = 20.sp * textScale,
        fontWeight = FontWeight.Medium,
        lineHeight = 28.sp * textScale,
        textAlign = lineAlignment,
        platformStyle = PlatformTextStyle(includeFontPadding = true)
    )

    val gradientOutlineWidthPx = with(LocalDensity.current) {
        gradientFontBorderWidth.toPx()
    }

    Column(modifier = commonModifier) {
        // ── Main Text Box ──
        // One text layout is reused for the dim line and the animated sung prefix.
        Box(modifier = (if (centerLineLyrics) Modifier.fillMaxWidth() else Modifier)
            .padding(horizontal = 6.dp)) {
            if (isKaraoke && isActive) {
                KaraokeSweepText(
                    text = mainContent,
                    words = line.words,
                    wordOffset = 0,
                    positionState = currentPositionState,
                    style = mainTextStyle,
                    baseColor = dimColor,
                    highlightBrush = lyricsBrush ?: SolidColor(litColor),
                    outlineWidthPx = if (lyricsBrush != null) gradientOutlineWidthPx else 0f
                )
            } else {
                LyricsText(
                    text = mainContent,
                    style = if (isKaraoke || !isActive) mainTextStyle.copy(brush = null) else mainTextStyle,
                    color = if (isKaraoke) dimColor else if (isActive && lyricsBrush != null) Color.Unspecified
                        else if (isActive) renderedColor.copy(alpha = alpha) else dimColor,
                    maxLines = Int.MAX_VALUE,
                    outlineWidth = lineSyncedGradientBorderWidth,
                    textAlign = lineAlignment,
                    fillTextWidth = centerLineLyrics
                )
            }
        }

        // ── Bracket Text Box ──
        if (bracketContent != null) {
            Box(modifier = (if (centerLineLyrics) Modifier.fillMaxWidth() else Modifier)
                .padding(start = 6.dp, end = 6.dp, top = 4.dp)) {
                if (isKaraoke && isActive) {
                    KaraokeSweepText(
                        text = bracketContent,
                        words = line.words,
                        wordOffset = bracketStartIndex,
                        positionState = currentPositionState,
                        style = bracketTextStyle,
                        baseColor = dimColor,
                        highlightBrush = lyricsBrush ?: SolidColor(litColor),
                        maxLines = 1,
                        outlineWidthPx = if (lyricsBrush != null) gradientOutlineWidthPx else 0f
                    )
                } else {
                    LyricsText(
                        text = bracketContent,
                        style = if (isKaraoke || !isActive) bracketTextStyle.copy(brush = null) else bracketTextStyle,
                        color = if (isKaraoke) dimColor else if (isActive && lyricsBrush != null) Color.Unspecified
                            else if (isActive) renderedColor else dimColor,
                        maxLines = 1,
                        outlineWidth = lineSyncedGradientBorderWidth,
                        textAlign = lineAlignment,
                        fillTextWidth = centerLineLyrics
                    )
                }
            }
        }

        if (!line.backgroundContent.isNullOrBlank()) {
            val backgroundText = line.backgroundContent + " "
            val backgroundStyle = bracketTextStyle.copy(
                fontSize = 18.sp,
                lineHeight = 26.sp,
                fontWeight = FontWeight.Medium
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp)
                    .padding(top = 4.dp)
            ) {
                if (isKaraoke && isActive && line.backgroundWords.isNotEmpty()) {
                    KaraokeSweepText(
                        text = backgroundText,
                        words = line.backgroundWords,
                        wordOffset = 0,
                        positionState = currentPositionState,
                        style = backgroundStyle,
                        baseColor = contentColor.copy(alpha = 0.82f),
                        highlightBrush = SolidColor(litColor),
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 2,
                        outlineWidthPx = if (lyricsBrush != null) gradientOutlineWidthPx else 0f
                    )
                } else {
                    LyricsText(
                        text = backgroundText,
                        style = if (isKaraoke || !isActive) backgroundStyle.copy(brush = null) else backgroundStyle,
                        color = if (isActive) contentColor.copy(alpha = 0.82f * alpha) else dimColor,
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 2,
                        outlineWidth = lineSyncedGradientBorderWidth,
                        textAlign = lineAlignment,
                        fillTextWidth = centerLineLyrics
                    )
                }
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════════
// ─ Interlude Item Composable
// ════════════════════════════════════════════════════════════════════════════════

@Composable
private fun InterludeItem(
    line: LyricLine,
    accentColor: Color,
    contentColor: Color
) {
    // A synced-lyrics gap is not a playback progress widget. Keep it static so
    // ordinary line-synchronized lyrics never show a fake loading animation.
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 28.dp, end = 24.dp, top = 18.dp, bottom = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            painter = painterResource(id = R.drawable.ic_music_note),
            contentDescription = null,
            tint = accentColor.copy(alpha = 0.45f),
            modifier = Modifier.size(20.dp)
        )
        Text(
            text = line.content.ifBlank { "Instrumental" },
            color = contentColor.copy(alpha = 0.48f),
            style = DsmusicTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            maxLines = 2
        )
    }
}

// ════════════════════════════════════════════════════════════════════════════════
// ─ Error State Composable
// ════════════════════════════════════════════════════════════════════════════════

@Composable
internal fun LyricsErrorState(
    message: String,
    onRetry: () -> Unit
) {
    val isDark = DsmusicTheme.colorScheme.background.luminance() < 0.5f
    val textColor = if (isDark) {
        Color.White.copy(alpha = 0.85f)
    } else {
        Color(0xFF1C1B1F).copy(alpha = 0.85f)
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(18.dp),
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp)
    ) {
        Text(
            text = message,
            style = DsmusicTheme.typography.titleMedium,
            color = textColor,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Button(onClick = onRetry) {
            Text("Retry")
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════════
// ─ Helper Functions
// ════════════════════════════════════════════════════════════════════════════════

/**
 * Interpolate between two colors smoothly
 * Used for Apple Music-style word highlighting
 */
private fun interpolateColor(
    startColor: Color,
    endColor: Color,
    progress: Float
): Color {
    val clampedProgress = progress.coerceIn(0f, 1f)

    return Color(
        red = startColor.red + (endColor.red - startColor.red) * clampedProgress,
        green = startColor.green + (endColor.green - startColor.green) * clampedProgress,
        blue = startColor.blue + (endColor.blue - startColor.blue) * clampedProgress,
        alpha = startColor.alpha + (endColor.alpha - startColor.alpha) * clampedProgress
    )
}

/**
 * Binary search to find the index of the active lyric line
 */
private fun binarySearchActiveIndex(
    displayItems: List<LyricDisplayItem>,
    currentPosition: Int
): Int {
    if (displayItems.isEmpty()) return -1

    var lo = 0
    var hi = displayItems.size - 1
    var result = -1

    while (lo <= hi) {
        val mid = (lo + hi) / 2
        val itemStartTime = displayItems[mid].line.startTimeMs

        when {
            itemStartTime <= currentPosition -> {
                result = mid
                lo = mid + 1
            }
            else -> hi = mid - 1
        }
    }

    return result
}

/**
 * Keeps the active karaoke/synced lyric line on a fixed centre anchor.
 *
 * Wait for the LazyColumn to be measured before calculating its target. On first
 * opening, the previous implementation could run while the viewport was still zero,
 * return early, and leave the first highlighted line off-centre.
 *
 * Karaoke word frames do not call this function: activeIndex changes only when the
 * highlighted line changes, so the word sweep stays smooth while the list advances.
 */
private suspend fun performSmoothedScroll(
    listState: androidx.compose.foundation.lazy.LazyListState,
    activeIndex: Int,
    displayItems: List<LyricDisplayItem>,
    estimatedItemHalfHeightPx: Int,
    scrollDurationMs: Int = 140,
    immediateInitialPosition: Boolean = false
) {
    if (activeIndex !in displayItems.indices) return

    // Don't calculate a target until Compose has measured the real lyrics viewport.
    snapshotFlow {
        listState.layoutInfo.viewportEndOffset - listState.layoutInfo.viewportStartOffset
    }.first { it > 0 }

    var layout = listState.layoutInfo
    var item = layout.visibleItemsInfo.firstOrNull { it.index == activeIndex }

    // On the first frame the active row may not yet be composed. Bring it near the
    // centre, then correct from its actual measured bounds.
    if (item == null) {
        val viewportCenter = (layout.viewportStartOffset + layout.viewportEndOffset) / 2
        // animateScrollToItem uses the LazyList's built-in fling animation;
        // its API does not accept an AnimationSpec parameter.
        val initialScrollOffset = -(viewportCenter - estimatedItemHalfHeightPx)
        if (immediateInitialPosition) {
            listState.scrollToItem(
                index = activeIndex,
                scrollOffset = initialScrollOffset
            )
        } else {
            listState.animateScrollToItem(
                index = activeIndex,
                scrollOffset = initialScrollOffset
            )
        }
        withFrameNanos { }
        layout = listState.layoutInfo
        item = layout.visibleItemsInfo.firstOrNull { it.index == activeIndex } ?: return
    }

    // Use item and viewport coordinates from the same LazyListLayoutInfo. One timed
    // correction keeps the entire movement inside the measured inter-line timing window.
    val viewportCenter = (layout.viewportStartOffset + layout.viewportEndOffset) / 2f
    val itemCenter = item!!.offset + item!!.size / 2f
    val correctionPx = itemCenter - viewportCenter
    if (abs(correctionPx) > 1.5f) {
        if (immediateInitialPosition) {
            listState.scrollBy(correctionPx)
        } else {
            listState.animateScrollBy(
                value = correctionPx,
                animationSpec = tween(
                    durationMillis = scrollDurationMs.coerceAtLeast(1),
                    easing = FastOutSlowInEasing
                )
            )
        }
    }
}

/**
 * Cubic easing out curve for smooth animations
 */
private fun easeOutCubic(t: Float): Float {
    val clampedT = t.coerceIn(0f, 1f)
    val f = clampedT - 1f
    return f * f * f + 1f
}