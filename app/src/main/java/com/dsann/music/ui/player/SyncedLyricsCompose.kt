package com.dsann.music.ui.player


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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import com.dsann.music.ui.theme.Button
import com.dsann.music.ui.theme.Icon
import com.dsann.music.ui.theme.DsmusicTheme
import com.dsann.music.ui.theme.dsmusicHomeStudioGradientBrush
import com.dsann.music.ui.theme.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import com.dsann.music.R
import com.dsann.music.data.lyrics.LyricLine
import com.dsann.music.data.lyrics.LyricWord
import com.dsann.music.data.lyrics.LyricsResult
import com.dsann.music.model.SongItem
import com.dsann.music.ui.theme.GoogleSansFlexNonRounded
import com.dsann.music.ui.theme.observeKey

// ════════════════════════════════════════════════════════════════════════════════
// ─ Data Classes & Constants
// ════════════════════════════════════════════════════════════════════════════════

private data class LyricDisplayItem(
    val line: LyricLine,
    val originalIndex: Int,
    val isActive: Boolean = false,
    val isPast: Boolean = false
)

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
        val finalWordEnd = currentLine.words
            .maxOfOrNull { it.absoluteStartTimeMs + it.durationMs }
            ?.takeIf { it > 0L }
        val completionTime = finalWordEnd ?: nextLine.startTimeMs
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

/**
 * Expensive glyph geometry is created once after text layout, not again on every karaoke frame.
 */
private data class KaraokeWordGeometry(
    val wordIndex: Int,
    val path: Path,
    val bounds: Rect
)

private data class KaraokeCharacterGeometry(
    val wordIndex: Int,
    val bounds: Rect
)

private data class KaraokeTextGeometry(
    val words: List<KaraokeWordGeometry>,
    val characters: List<KaraokeCharacterGeometry>
)

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
    gradientFontBorderWidth: Dp = 1.dp,
    centerLineLyrics: Boolean = false,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val context = LocalContext.current
    val appearancePreferences = remember(context) {
        context.getSharedPreferences("AppSettings", android.content.Context.MODE_PRIVATE)
    }
    val lyricsTextSize by appearancePreferences.observeKey("lyrics_text_size", "Standard")
    val lyricsAlignment by appearancePreferences.observeKey("lyrics_alignment", "Original")
    val lyricsFontFamily by appearancePreferences.observeKey("lyrics_font_family", "Rounded")
    val configuredLyricsScale = when (lyricsTextSize) {
        "Small" -> 0.85f
        "Large" -> 1.18f
        else -> 1f
    }
    val isLandscape = LocalConfiguration.current.orientation ==
        android.content.res.Configuration.ORIENTATION_LANDSCAPE

    val currentView = LocalView.current
    DisposableEffect(currentView) {
        currentView.keepScreenOn = true
        onDispose {
            currentView.keepScreenOn = false
        }
    }

    // ── Detect karaoke mode ──
    val isKaraokeMode = remember(lyrics) {
        lyrics.any { !it.isInterlude && it.isWordByWord && it.words.isNotEmpty() }
    }
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
    // Word highlights stay aligned to display VSYNC, but fullscreen karaoke does not
    // need a state update on every panel refresh. Respect the user's global frame cap and
    // cap lower-memory devices at 30 fps to avoid overloading their first lyrics render.
    val lyricsFrameContext = LocalContext.current
    val karaokeFrameRateCeiling = remember(lyricsFrameContext) {
        if (isLowCostLyricsDevice(lyricsFrameContext)) 30 else null
    }
    val karaokeFrameTimeNanos = rememberAppAnimationFrameTimeNanos(
        active = isPlaying && isKaraokeMode,
        maximumFrameRate = karaokeFrameRateCeiling
    )

    // ── Filter out interludes when in karaoke mode ──
    val displayItems = remember(lyrics, isKaraokeMode) {
        // Some lyric providers return blocks in source order rather than playback order.
        // The active-line binary search requires monotonic timestamps; explicitly sort
        // each track so an early playback position can never resolve to the final line.
        lyrics.mapIndexedNotNull { index, line ->
            if (line.isInterlude) null
            else LyricDisplayItem(
                line = line,
                originalIndex = index
            )
        }.sortedWith(
            compareBy<LyricDisplayItem> { it.line.startTimeMs }
                .thenBy { it.originalIndex }
        )
    }

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
        isPlaying, lyricTrackKey, lyrics, timedPositionProvider, playbackSpeedProvider, isKaraokeMode
    ) {
        val initialPosition = timedPositionProvider().coerceAtLeast(0)
        var previousPosition = initialPosition
        karaokePlaybackClock.reset(initialPosition)
        lyricClock.intValue = initialPosition

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
            } else if (!isKaraokeMode) {
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
            } else {
                withFrameNanos {
                    val frameNanos = karaokeFrameTimeNanos.value
                    if (frameNanos <= 0L) return@withFrameNanos

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
            }
        }
    }
    val lyricPositionProvider = remember(lyricClock) { { lyricClock.intValue } }

    // derivedStateOf means this parent/list selection changes only when the active lyric line changes.
    val activeIndex by remember(displayItems, lyricClock, currentSong?.duration) {
        derivedStateOf {
            val currentPos = lyricClock.intValue
            val lastLyric = displayItems.lastOrNull { !it.line.isInterlude }?.line
            val lastWordEndMs = lastLyric?.words
                ?.maxOfOrNull { it.absoluteStartTimeMs + it.durationMs }
                ?.takeIf { it > 0L }
            val songDurationMs = currentSong?.duration?.takeIf { it > 0L }
            val hasPassedFinalLyric = when {
                songDurationMs != null && currentPos.toLong() >= songDurationMs -> true
                lastWordEndMs != null && currentPos.toLong() > lastWordEndMs + 1000L -> true
                else -> false
            }

            if (displayItems.isEmpty() || hasPassedFinalLyric) -1
            else binarySearchActiveIndex(displayItems, currentPos)
        }
    }

    // Precompute completion boundaries once. Per-frame target lookup is now O(log n)
    // instead of repeatedly scanning forward through the lyrics list.
    val scrollBoundaries = remember(displayItems) {
        buildLyricsScrollBoundaries(displayItems.map { it.line })
    }
    val scrollTargetIndex by remember(scrollBoundaries, lyricClock) {
        derivedStateOf {
            findLyricsScrollTargetIndex(
                boundaries = scrollBoundaries,
                positionMs = lyricClock.intValue.toLong()
            )
        }
    }
    val density = LocalDensity.current
    // The first frame after opening a long track should position immediately. A long
    // animated jump from item 0 to the current song position can stall weaker devices.
    val hasPositionedInitialLyrics = remember(lyricTrackKey) {
        androidx.compose.runtime.mutableStateOf(false)
    }
    LaunchedEffect(lyricTrackKey, displayItems, scrollTargetIndex, density) {
        if (scrollTargetIndex == 0) {
            listState.scrollToItem(0)
            hasPositionedInitialLyrics.value = true
        } else if (scrollTargetIndex > 0) {
            val isInitialPosition = !hasPositionedInitialLyrics.value
            val previousLineIndex = (scrollTargetIndex - 1 downTo 0)
                .firstOrNull { !displayItems[it].line.isInterlude }
            val previousLine = previousLineIndex?.let { displayItems[it].line }
            val nextLine = displayItems[scrollTargetIndex].line
            val lastWordEndMs = previousLine?.words
                ?.maxOfOrNull { it.absoluteStartTimeMs + it.durationMs }
            val nextFirstWordStartMs = nextLine.words
                .minOfOrNull { it.absoluteStartTimeMs }
                ?: nextLine.startTimeMs

            val gapMs = lastWordEndMs?.let {
                (nextFirstWordStartMs - it).coerceAtLeast(0L)
            } ?: 0L
            val playbackPositionMs = lyricClock.intValue.toLong()
            val remainingGapMs = (nextFirstWordStartMs - playbackPositionMs).coerceAtLeast(0L)
            val playbackSpeed = playbackSpeedProvider()
                .takeIf { it.isFinite() && it > 0f } ?: 1f
            val scrollDurationMs = if (gapMs > 0L && remainingGapMs > 0L) {
                (remainingGapMs.toDouble() / playbackSpeed.toDouble())
                    .roundToInt()
                    .coerceAtLeast(16)
            } else {
                // No timed gap exists (ordinary LRC or overlapping karaoke words).
                140
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
                            currentPositionProvider = lyricPositionProvider,
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

// ════════════════════════════════════════════════════════════════════════════════
// ─ Lyric Line Item Composable - APPLE MUSIC STYLE HIGHLIGHTING
// ════════════════════════════════════════════════════════════════════════════════

@Composable
private fun LyricLineItem(
    line: LyricLine,
    isActive: Boolean,
    isPast: Boolean,
    currentPositionProvider: () -> Int,
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
    onClick: () -> Unit
) {
    val isKaraoke = line.isWordByWord && line.words.isNotEmpty()
    // Keep the same 1 dp gradient font contour in the fullscreen renderer.
    val lineSyncedGradientBorderWidth = gradientFontBorderWidth
    val scale = 1.0f

    // The active synced line must be fully legible on the exact frame its timestamp
    // becomes active. Animating its alpha from the previous line's dim value made
    // ordinary line-synced lyrics look like a delayed/loading word highlight.
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
        "White" -> Color.White
        // UI Matching uses the app accent for the active lyric line, so it remains
        // visually distinct from the explicit pure-white mode on dark themes.
        "UI Matching" -> accentColor
        else -> contentColor
    }
    // Keep the original dim treatment for non-highlighted lyric lines.
    val dimColor = renderedColor.copy(alpha = 0.35f)
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
        .graphicsLayer {
            this.alpha = alpha
            scaleX = scale
            scaleY = scale
            transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0.5f)

            val driftProgress = if (isActive && isKaraoke) {
                val pos = currentPositionProvider()
                val duration = (lineEndMs - lineStartMs).coerceAtLeast(1)
                ((pos - lineStartMs).toFloat() / duration).coerceIn(0f, 1f)
            } else 0f

            val driftDp = if (isActive && isKaraoke) {
                val eased = easeOutCubic(driftProgress)
                (-2f * eased)
            } else 0f

            translationY = driftDp * density
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
        shadow = if (isActive && !isKaraoke && lyricsColorStyle == "White") {
            androidx.compose.ui.graphics.Shadow(
                color = Color.White.copy(alpha = 0.9f),
                offset = androidx.compose.ui.geometry.Offset(0f, 0f),
                blurRadius = 8f
            )
        } else null,
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

    val mainTextLayoutResult = remember { androidx.compose.runtime.mutableStateOf<androidx.compose.ui.text.TextLayoutResult?>(null) }
    val bracketTextLayoutResult = remember { androidx.compose.runtime.mutableStateOf<androidx.compose.ui.text.TextLayoutResult?>(null) }
    val gradientOutlineWidthPx = with(LocalDensity.current) {
        gradientFontBorderWidth.toPx()
    }

    Column(modifier = commonModifier) {
        // ── Main Text Box ──
        // Keep glyph overhangs inside the visible row on every Android version.
        // Apply the same inset to the dim base and karaoke overlay so their geometry stays aligned.
        Box(modifier = (if (centerLineLyrics) Modifier.fillMaxWidth() else Modifier)
            .padding(horizontal = 6.dp)) {
            // Base Dim Text
            LyricsText(
                text = mainContent,
                style = if (isKaraoke || !isActive) mainTextStyle.copy(brush = null) else mainTextStyle,
                color = if (isKaraoke) dimColor else if (isActive && lyricsBrush != null) Color.Unspecified
                    else if (isActive) renderedColor else dimColor,
                onTextLayout = { mainTextLayoutResult.value = it },
                maxLines = Int.MAX_VALUE,
                outlineWidth = lineSyncedGradientBorderWidth,
                textAlign = lineAlignment,
                fillTextWidth = centerLineLyrics
            )

            // Karaoke font border: render with a stroked font style, then remove
            // the border from words whose gradient highlight has not started yet.
            if (isKaraoke && isActive && lyricsBrush != null && gradientOutlineWidthPx > 0f) {
                Text(
                    text = mainContent,
                    style = mainTextStyle.copy(
                        brush = SolidColor(Color.Black),
                        drawStyle = Stroke(width = gradientOutlineWidthPx)
                    ),
                    color = Color.Unspecified,
                    maxLines = Int.MAX_VALUE,
                    modifier = Modifier
                        .then(if (centerLineLyrics) Modifier.fillMaxWidth() else Modifier)
                        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                        .drawWithCache {
                            val layout = mainTextLayoutResult.value
                            val geometry = layout?.let { buildKaraokeTextGeometry(it, line.words, 0) }
                            onDrawWithContent {
                                if (geometry != null) {
                                    drawProgressiveKaraokeFontBorder(
                                        geometry = geometry,
                                        words = line.words,
                                        pos = currentPositionProvider().toLong(),
                                        drawScope = this,
                                        strokeWidthPx = gradientOutlineWidthPx
                                    )
                                }
                            }
                        }
                )
            }

            // Overlay Lit Text
            if (isKaraoke && isActive) {
                Text(
                    text = mainContent,
                    style = mainTextStyle.copy(
                        shadow = androidx.compose.ui.graphics.Shadow(
                            color = if (lyricsColorStyle == "White") {
                                Color.White.copy(alpha = 0.9f)
                            } else {
                                accentColor.copy(alpha = 0.5f)
                            },
                            offset = androidx.compose.ui.geometry.Offset(0f, 0f),
                            blurRadius = if (lyricsColorStyle == "White") 8f else 7f
                        )
                    ),
                    color = if (lyricsBrush != null) Color.Unspecified else litColor,
                    maxLines = Int.MAX_VALUE,
                    modifier = Modifier
                        .then(if (centerLineLyrics) Modifier.fillMaxWidth() else Modifier)
                        .graphicsLayer { compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen }
                        .drawWithCache {
                            val layout = mainTextLayoutResult.value
                            val geometry = layout?.let { buildKaraokeTextGeometry(it, line.words, 0) }
                            onDrawWithContent {
                                drawContent()
                                if (geometry != null) {
                                    val playbackPositionMs = currentPositionProvider().toLong()
                                    eraseFutureText(
                                        geometry = geometry,
                                        words = line.words,
                                        pos = playbackPositionMs,
                                        drawScope = this
                                    )
                                }
                            }
                        }
                )
            }
        }

        // ── Bracket Text Box ──
        if (bracketContent != null) {
            Box(modifier = (if (centerLineLyrics) Modifier.fillMaxWidth() else Modifier)
                .padding(start = 6.dp, end = 6.dp, top = 4.dp)) {
                // Base Dim Text
                LyricsText(
                    text = bracketContent,
                    style = if (isKaraoke || !isActive) bracketTextStyle.copy(brush = null) else bracketTextStyle,
                    color = if (isKaraoke) dimColor else if (isActive && lyricsBrush != null) Color.Unspecified
                    else if (isActive) renderedColor else dimColor,
                    onTextLayout = { bracketTextLayoutResult.value = it },
                    maxLines = 1,
                    outlineWidth = lineSyncedGradientBorderWidth,
                    textAlign = lineAlignment,
                    fillTextWidth = centerLineLyrics
                )

                // Karaoke font border, limited to words with an active gradient.
                if (isKaraoke && isActive && lyricsBrush != null && gradientOutlineWidthPx > 0f) {
                    Text(
                        text = bracketContent,
                        style = bracketTextStyle.copy(
                            brush = SolidColor(Color.Black),
                            drawStyle = Stroke(width = gradientOutlineWidthPx)
                        ),
                        color = Color.Unspecified,
                        maxLines = 1,
                        modifier = Modifier
                            .then(if (centerLineLyrics) Modifier.fillMaxWidth() else Modifier)
                            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                            .drawWithCache {
                                val layout = bracketTextLayoutResult.value
                                val geometry = layout?.let {
                                    buildKaraokeTextGeometry(it, line.words, bracketStartIndex)
                                }
                                onDrawWithContent {
                                    if (geometry != null) {
                                        drawProgressiveKaraokeFontBorder(
                                            geometry = geometry,
                                            words = line.words,
                                            pos = currentPositionProvider().toLong(),
                                            drawScope = this,
                                            strokeWidthPx = gradientOutlineWidthPx
                                        )
                                }
                                }
                            }
                    )
                }

                // Overlay Lit Text
                if (isKaraoke && isActive) {
                    Text(
                        text = bracketContent,
                        style = bracketTextStyle.copy(
                            shadow = androidx.compose.ui.graphics.Shadow(
                                color = if (lyricsColorStyle == "White") {
                                    Color.White.copy(alpha = 0.9f)
                                } else {
                                    accentColor.copy(alpha = 0.4f)
                                },
                                offset = androidx.compose.ui.geometry.Offset(0f, 0f),
                                blurRadius = if (lyricsColorStyle == "White") 8f else 7f
                            )
                        ),
                        color = if (lyricsBrush != null) Color.Unspecified else litColor,
                        maxLines = 1,
                        modifier = Modifier
                            .then(if (centerLineLyrics) Modifier.fillMaxWidth() else Modifier)
                            .graphicsLayer { compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen }
                            .drawWithCache {
                                val layout = bracketTextLayoutResult.value
                                val geometry = layout?.let { buildKaraokeTextGeometry(it, line.words, bracketStartIndex) }
                                onDrawWithContent {
                                    drawContent()
                                    if (geometry != null) {
                                        val playbackPositionMs = currentPositionProvider().toLong()
                                        eraseFutureText(
                                            geometry = geometry,
                                            words = line.words,
                                            pos = playbackPositionMs,
                                            drawScope = this
                                        )
                                    }
                                }
                            }
                    )
                }
            }
        }
        if (!line.backgroundContent.isNullOrBlank()) {
            val backgroundText = line.backgroundContent + " "
            val backgroundLayoutResult = remember(line.startTimeMs, line.backgroundContent) {
                androidx.compose.runtime.mutableStateOf<androidx.compose.ui.text.TextLayoutResult?>(null)
            }
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
                LyricsText(
                    text = backgroundText,
                    style = if (isKaraoke || !isActive) backgroundStyle.copy(brush = null) else backgroundStyle,
                    color = if (isActive) contentColor.copy(alpha = 0.82f) else dimColor,
                    modifier = Modifier.fillMaxWidth(),
                    onTextLayout = { backgroundLayoutResult.value = it },
                    maxLines = 2,
                    outlineWidth = lineSyncedGradientBorderWidth,
                    textAlign = lineAlignment,
                    fillTextWidth = centerLineLyrics
                )

                // Karaoke background-vocal font border: reveal only the glyph stroke
                // covered by the same timed gradient sweep as the background-vocal fill.
                if (isKaraoke && isActive && lyricsBrush != null && gradientOutlineWidthPx > 0f && line.backgroundWords.isNotEmpty()) {
                    Text(
                        text = backgroundText,
                        style = backgroundStyle.copy(
                            brush = SolidColor(Color.Black),
                            drawStyle = Stroke(width = gradientOutlineWidthPx)
                        ),
                        color = Color.Unspecified,
                        maxLines = 2,
                        modifier = Modifier
                            .fillMaxWidth()
                            .graphicsLayer {
                                compositingStrategy = CompositingStrategy.Offscreen
                            }
                            .drawWithCache {
                                val layout = backgroundLayoutResult.value
                                val geometry = layout?.let {
                                    buildKaraokeTextGeometry(it, line.backgroundWords, 0)
                                }
                                onDrawWithContent {
                                    if (geometry != null) {
                                        drawProgressiveKaraokeFontBorder(
                                            geometry = geometry,
                                            words = line.backgroundWords,
                                            pos = currentPositionProvider().toLong(),
                                            drawScope = this,
                                            strokeWidthPx = gradientOutlineWidthPx
                                        )
                                }
                                }
                            }
                    )
                }

                if (line.backgroundWords.isNotEmpty() && isActive) {
                    Text(
                        text = backgroundText,
                        style = backgroundStyle.copy(
                            shadow = androidx.compose.ui.graphics.Shadow(
                                color = accentColor.copy(alpha = 0.35f),
                                offset = androidx.compose.ui.geometry.Offset(0f, 0f),
                                blurRadius = 5f
                            )
                        ),
                        color = litColor,
                        modifier = Modifier
                            .fillMaxWidth()
                            .graphicsLayer {
                                compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen
                            }
                            .drawWithCache {
                                val layout = backgroundLayoutResult.value
                                val geometry = layout?.let { buildKaraokeTextGeometry(it, line.backgroundWords, 0) }
                                onDrawWithContent {
                                    drawContent()
                                    if (geometry != null) {
                                        eraseFutureText(
                                            geometry = geometry,
                                            words = line.backgroundWords,
                                            pos = currentPositionProvider().toLong(),
                                            drawScope = this
                                        )
                                    }
                                }
                            },
                        maxLines = 2
                    )
                }
            }
        }
    }
}

private fun buildKaraokeTextGeometry(
    layout: androidx.compose.ui.text.TextLayoutResult,
    words: List<LyricWord>,
    wordOffset: Int
): KaraokeTextGeometry {
    val text = layout.layoutInput.text.text
    val geometries = ArrayList<KaraokeWordGeometry>(words.size)
    val characterGeometries = ArrayList<KaraokeCharacterGeometry>()

    words.forEachIndexed { wordIndex, word ->
        val first = max(0, word.charRange.first - wordOffset)
        val endExclusive = min(text.length, word.charRange.last - wordOffset + 1)
        if (first >= endExclusive) return@forEachIndexed

        // Keep this enclosing path only for masking the karaoke fill. Font borders
        // below are native TextStyle strokes clipped with cached character bounds.
        val wordPath = layout.getPathForRange(first, endExclusive)
        val visibleCharacters = ArrayList<Rect>()
        var left = Float.POSITIVE_INFINITY
        var top = Float.POSITIVE_INFINITY
        var right = Float.NEGATIVE_INFINITY
        var bottom = Float.NEGATIVE_INFINITY

        for (charIndex in first until endExclusive) {
            if (text[charIndex].isWhitespace()) continue
            val bounds = layout.getBoundingBox(charIndex)
            if (bounds.width <= 0f || bounds.height <= 0f) continue
            visibleCharacters.add(bounds)
            left = min(left, bounds.left)
            top = min(top, bounds.top)
            right = max(right, bounds.right)
            bottom = max(bottom, bounds.bottom)
        }

        val characterCount = visibleCharacters.size
        visibleCharacters.forEachIndexed { characterIndex, bounds ->
            characterGeometries.add(
                KaraokeCharacterGeometry(
                    wordIndex = wordIndex,
                    bounds = bounds
                )
            )
        }

        if (left.isFinite() && top.isFinite() && right > left && bottom > top) {
            geometries.add(
                KaraokeWordGeometry(
                    wordIndex = wordIndex,
                    path = wordPath,
                    bounds = Rect(left, top, right, bottom)
                )
            )
        }
    }

    return KaraokeTextGeometry(words = geometries, characters = characterGeometries)
}

private fun eraseFutureText(
    geometry: KaraokeTextGeometry,
    words: List<LyricWord>,
    pos: Long,
    drawScope: androidx.compose.ui.graphics.drawscope.DrawScope
) {
    if (geometry.words.isEmpty()) return

    // Keep the feather narrow and centred on the true timestamp boundary. A broad
    // sweep or easing curve makes words appear ahead of their source timing.
    val sweepWidthFraction = 0.12f
    geometry.words.forEach { wordGeometry ->
        val word = words.getOrNull(wordGeometry.wordIndex) ?: return@forEach
        val progress = calculateWordProgress(word, pos)
        if (progress >= 1f) return@forEach

        if (progress <= 0f) {
            drawScope.drawPath(
                wordGeometry.path,
                color = Color.Black,
                blendMode = BlendMode.DstOut
            )
            return@forEach
        }

        val bounds = wordGeometry.bounds
        val gradientWidth = (bounds.width * sweepWidthFraction).coerceAtLeast(1f)
        // Word time maps linearly to glyph position: 50% elapsed means 50% highlighted.
        val sweepBoundary = bounds.left + bounds.width * progress
        val brush = Brush.horizontalGradient(
            0.0f to Color.Transparent,
            1.0f to Color.Black,
            startX = sweepBoundary - gradientWidth / 2f,
            endX = sweepBoundary + gradientWidth / 2f
        )
        drawScope.drawPath(wordGeometry.path, brush = brush, blendMode = BlendMode.DstOut)
    }
}

private fun calculateWordProgress(word: LyricWord, pos: Long): Float =
    calculateKaraokeWordProgress(
        wordStartMs = word.absoluteStartTimeMs,
        wordDurationMs = word.durationMs,
        playbackPositionMs = pos
    )
 
/**
 * Clear the stroked font border from words whose gradient has not started.
 * The border itself is rendered by TextStyle.drawStyle on a text layer.
 */
private fun drawProgressiveKaraokeFontBorder(
    geometry: KaraokeTextGeometry,
    words: List<LyricWord>,
    pos: Long,
    drawScope: androidx.compose.ui.graphics.drawscope.ContentDrawScope,
    strokeWidthPx: Float
) {
    val revealPath = Path()
    val wordGeometryByIndex = geometry.words.associateBy { it.wordIndex }
    geometry.characters.forEach { character ->
        val word = words.getOrNull(character.wordIndex) ?: return@forEach
        val wordGeometry = wordGeometryByIndex[character.wordIndex] ?: return@forEach
        val progress = calculateWordProgress(word, pos)
        if (progress <= 0f) return@forEach

        // As soon as the gradient frontier enters a glyph, reveal that glyph's
        // complete font stroke. Do not progressively crop the stroke across the letter.
        val revealBoundary = wordGeometry.bounds.left + wordGeometry.bounds.width * progress
        val bounds = character.bounds
        if (revealBoundary <= bounds.left) return@forEach

        val halfStroke = strokeWidthPx / 2f
        revealPath.addRect(
            Rect(
                left = bounds.left - halfStroke,
                top = bounds.top - halfStroke,
                right = bounds.right + halfStroke,
                bottom = bounds.bottom + halfStroke
            )
        )
    }

    if (!revealPath.isEmpty) {
        drawScope.clipPath(revealPath) {
            drawScope.drawContent()
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