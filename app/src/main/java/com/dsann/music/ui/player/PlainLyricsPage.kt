package com.dsann.music.ui.player

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.dsann.music.data.lyrics.LyricLine
import com.dsann.music.data.lyrics.LineSyncedLyricTimeline
import com.dsann.music.data.lyrics.LyricsResult
import com.dsann.music.model.SongItem
import com.dsann.music.ui.theme.DsmusicTheme
import com.dsann.music.ui.theme.dsmusicHomeStudioGradientBrush
import com.dsann.music.ui.theme.Icon
import com.dsann.music.ui.theme.IconButton
import com.dsann.music.ui.theme.GoogleSansFlexNonRounded
import com.dsann.music.ui.theme.LoadingIndicator
import com.dsann.music.ui.theme.Text
import com.dsann.music.ui.theme.TextButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Flatten line-synced and word-synced lyric payloads into readable, untimed text.
 * Background-vocal lines are retained as their own lines; timestamps and word
 * timing metadata are intentionally omitted.
 */
internal fun convertSyncedLyricsToPlainText(lyrics: List<LyricLine>): String {
    val output = ArrayList<String>(lyrics.size * 2)
    lyrics.forEach { line ->
        val mainText = line.content.ifBlank {
            line.words.joinToString(separator = "") { word -> word.text }
        }.trim()
        val backgroundText = line.backgroundContent.orEmpty().ifBlank {
            line.backgroundWords.joinToString(separator = "") { word -> word.text }
        }.trim()

        if (mainText.isNotBlank()) output += mainText
        if (backgroundText.isNotBlank() && !backgroundText.equals(mainText, ignoreCase = false)) {
            output += backgroundText
        }
    }
    return output.joinToString(separator = "\n")
}


private data class PreparedSyncedPageLine(
    val entry: LineSyncedLyricTimeline.Entry,
    val mainWordSpans: List<KaraokeWordSpan>,
    val backgroundWordSpans: List<KaraokeWordSpan>
)

private data class PreparedSyncedPageLyrics(
    val lines: List<PreparedSyncedPageLine>
)

/**
 * Lightweight playback-synced lyrics surface for the standalone page.
 *
 * Timeline construction happens off-main. Only the active karaoke line owns a
 * word-sweep renderer; the rest of the list remains ordinary Compose text. The
 * playback clock is shared with the player's vsync publisher and re-anchors to
 * Media3 after seeks, pauses, stalls and track changes.
 */
@Composable
private fun SyncedLyricsPageContent(
    lyrics: List<LyricLine>,
    currentSong: SongItem?,
    currentPositionProvider: () -> Int,
    playbackSpeedProvider: () -> Float,
    isPlaying: Boolean,
    contentColor: Color,
    lyricsColorStyle: String,
    lyricsFontFamily: String,
    onSeekTo: (Int) -> Unit
) {
    var preparedPageLyrics by remember(lyrics) {
        mutableStateOf<PreparedSyncedPageLyrics?>(null)
    }
    LaunchedEffect(lyrics) {
        preparedPageLyrics = null
        val preparationContext = currentCoroutineContext()
        preparedPageLyrics = withContext(Dispatchers.Default) {
            val entries = LineSyncedLyricTimeline.build(lyrics)
            val preparedLines = ArrayList<PreparedSyncedPageLine>(entries.size)
            entries.forEach { entry ->
                preparationContext.ensureActive()
                val line = entry.line
                val mainSpans = if (line.isWordByWord && line.words.isNotEmpty()) {
                    buildKaraokeWordSpans(line.content, line.words, 0) {
                        preparationContext.ensureActive()
                    }
                } else {
                    emptyList()
                }
                val backgroundSpans = if (
                    !line.backgroundContent.isNullOrBlank() && line.backgroundWords.isNotEmpty()
                ) {
                    buildKaraokeWordSpans(line.backgroundContent, line.backgroundWords, 0) {
                        preparationContext.ensureActive()
                    }
                } else {
                    emptyList()
                }
                preparedLines += PreparedSyncedPageLine(
                    entry = entry,
                    mainWordSpans = mainSpans,
                    backgroundWordSpans = backgroundSpans
                )
            }
            PreparedSyncedPageLyrics(preparedLines)
        }
    }

    val preparedLines = preparedPageLyrics?.lines.orEmpty()
    val timeline = remember(preparedLines) { preparedLines.map { it.entry } }
    val hasKaraoke = remember(preparedLines) {
        preparedLines.any {
            it.mainWordSpans.isNotEmpty() || it.backgroundWordSpans.isNotEmpty()
        }
    }
    val trackKey = currentSong?.videoId?.takeIf { it.isNotBlank() }
        ?: currentSong?.path?.takeIf { it.isNotBlank() }
        ?: currentSong?.id?.toString()
    val positionState = remember(trackKey) {
        mutableIntStateOf(currentPositionProvider().coerceAtLeast(0))
    }
    val playbackClock = remember(trackKey) { KaraokePlaybackClock() }
    val frameTimeNanos = rememberAppAnimationFrameTimeNanos(
        active = isPlaying && hasKaraoke
    )

    LaunchedEffect(
        trackKey,
        timeline,
        isPlaying,
        hasKaraoke,
        currentPositionProvider,
        playbackSpeedProvider
    ) {
        if (preparedPageLyrics == null) return@LaunchedEffect
        val initialPosition = currentPositionProvider().coerceAtLeast(0)
        playbackClock.reset(initialPosition)
        positionState.intValue = initialPosition

        if (isPlaying && hasKaraoke) {
            snapshotFlow { frameTimeNanos.value }
                .filter { it > 0L }
                .collect { frameNanos ->
                    val actualPosition = currentPositionProvider().coerceAtLeast(0)
                    val correctedPosition = playbackClock.update(
                        frameTimeNanos = frameNanos,
                        actualPositionMs = actualPosition,
                        playbackSpeed = playbackSpeedProvider(),
                        isPlaying = true
                    )
                    if (positionState.intValue != correctedPosition) {
                        positionState.intValue = correctedPosition
                    }
                }
        } else {
            while (true) {
                val actualPosition = currentPositionProvider().coerceAtLeast(0)
                playbackClock.reset(actualPosition)
                if (positionState.intValue != actualPosition) {
                    positionState.intValue = actualPosition
                }
                delay(
                    if (android.os.Build.VERSION.SDK_INT <= android.os.Build.VERSION_CODES.M) {
                        84L
                    } else {
                        40L
                    }
                )
            }
        }
    }

    // Karaoke rows become eligible to advance as soon as their final timed word
    // ends. Standard line-synced sources have no explicit end timestamp, so reserve
    // the final quarter of the line interval (capped at 500 ms) for the scroll.
    // Prefix maxima keep the target stable for overlapping/out-of-order timestamps.
    val scrollBoundaries = remember(timeline) {
        LongArray(timeline.size).also { boundaries ->
            var greatest = Long.MIN_VALUE
            timeline.forEachIndexed { index, entry ->
                val next = timeline.getOrNull(index + 1)
                val nextStart = next?.startTimeMs ?: Long.MAX_VALUE
                val lineEnd = entry.lastWordEndMs
                val boundary = when {
                    next == null -> Long.MAX_VALUE
                    lineEnd != null && lineEnd < nextStart -> lineEnd
                    else -> {
                        val interval = (nextStart - entry.startTimeMs).coerceAtLeast(0L)
                        val leadWindow = minOf(
                            interval,
                            (interval / 3L).coerceIn(120L, 500L)
                        )
                        (nextStart - leadWindow).coerceAtLeast(entry.startTimeMs)
                    }
                }
                greatest = maxOf(greatest, boundary)
                boundaries[index] = greatest
            }
        }
    }
    val songDurationMs = currentSong?.duration?.takeIf { it > 0L }
    val finalWordEndMs = timeline.lastOrNull()?.lastWordEndMs
    val activeIndex by remember(timeline, scrollBoundaries, positionState, songDurationMs, finalWordEndMs) {
        derivedStateOf {
            val position = positionState.intValue.toLong()
            if (
                timeline.isEmpty() ||
                position < timeline.first().startTimeMs ||
                (songDurationMs != null && position >= songDurationMs) ||
                (finalWordEndMs != null && position > finalWordEndMs + 1_000L)
            ) {
                -1
            } else {
                var low = 0
                var high = scrollBoundaries.lastIndex
                var lastCompleted = -1
                while (low <= high) {
                    val middle = (low + high) ushr 1
                    if (position >= scrollBoundaries[middle]) {
                        lastCompleted = middle
                        low = middle + 1
                    } else {
                        high = middle - 1
                    }
                }
                (lastCompleted + 1).coerceIn(0, timeline.lastIndex)
            }
        }
    }

    val listState = rememberLazyListState()
    val density = LocalDensity.current
    val lastCenteredIndex = remember(trackKey) { mutableIntStateOf(-1) }
    LaunchedEffect(activeIndex, timeline, density) {
        if (activeIndex !in timeline.indices) return@LaunchedEffect
        val previousIndex = lastCenteredIndex.intValue
        val isInitialOrSeek = previousIndex < 0 || abs(activeIndex - previousIndex) > 1
        val nextStartMs = timeline.getOrNull(activeIndex)?.startTimeMs ?: Long.MAX_VALUE
        val remainingPlaybackMs = (nextStartMs - positionState.intValue.toLong()).coerceAtLeast(0L)
        val speed = playbackSpeedProvider().takeIf { it.isFinite() && it > 0f } ?: 1f
        // Leave a small 50 ms safety margin so the target line is already centred
        // immediately before its first word/line timestamp.
        val finishLeadMs = if (remainingPlaybackMs > 80L) 50L else 0L
        val scrollDurationMs = if (isInitialOrSeek || nextStartMs == Long.MAX_VALUE) {
            0
        } else {
            ((remainingPlaybackMs - finishLeadMs).coerceAtLeast(16L) / speed)
                .roundToInt()
                .coerceIn(16, 1_200)
        }
        scrollLyricLineToCenter(
            listState = listState,
            index = activeIndex,
            estimatedItemHalfHeightPx = with(density) { 36.dp.roundToPx() },
            durationMs = scrollDurationMs,
            immediate = isInitialOrSeek
        )
        lastCenteredIndex.intValue = activeIndex
    }

    val gradientEnabled = lyricsColorStyle == "Home Studio Gradient"
    // A gradient is reserved for the highlighted line/word sweep. Inactive lines
    // use a neutral, low-opacity white just like the White colour mode.
    val lyricBaseColor = if (
        gradientEnabled || lyricsColorStyle == "White" || lyricsColorStyle == "UI Matching"
    ) Color.White else contentColor
    val highlightBrush = if (gradientEnabled) {
        dsmusicHomeStudioGradientBrush()
    } else {
        SolidColor(lyricBaseColor)
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 24.dp,
            end = 24.dp,
            top = 12.dp,
            bottom = 36.dp
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        itemsIndexed(
            items = preparedLines,
            key = { index, prepared -> "synced-page-${prepared.entry.line.startTimeMs}-$index" }
        ) { index, prepared ->
            val entry = prepared.entry
            val line = entry.line
            val active = index == activeIndex
            val lineTextStyle = DsmusicTheme.typography.bodyLarge.copy(
                fontSize = 44.sp,
                lineHeight = 58.sp,
                fontFamily = when (lyricsFontFamily) {
                    "Serif" -> FontFamily.Serif
                    "Mono" -> FontFamily.Monospace
                    else -> GoogleSansFlexNonRounded
                },
                fontWeight = FontWeight.ExtraBold
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (active) Modifier
                            .background(contentColor.copy(alpha = 0.055f), RoundedCornerShape(16.dp))
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                        else Modifier
                    )
                    .clickable {
                        onSeekTo(
                            entry.startTimeMs.coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
                        )
                    },
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (active && line.isWordByWord && line.words.isNotEmpty()) {
                    KaraokeSweepText(
                        text = line.content,
                        words = line.words,
                        wordOffset = 0,
                        positionState = positionState,
                        preparedSpans = prepared.mainWordSpans,
                        style = lineTextStyle,
                        baseColor = lyricBaseColor.copy(alpha = 0.22f),
                        highlightBrush = highlightBrush,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    Text(
                        text = line.content,
                        color = when {
                            active && gradientEnabled -> Color.Unspecified
                            active -> lyricBaseColor
                            else -> lyricBaseColor.copy(alpha = 0.22f)
                        },
                        style = if (active && gradientEnabled) {
                            lineTextStyle.copy(brush = dsmusicHomeStudioGradientBrush())
                        } else {
                            lineTextStyle.copy(brush = null)
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                if (!line.backgroundContent.isNullOrBlank()) {
                    val backgroundStyle = lineTextStyle.copy(
                        fontSize = 32.sp,
                        lineHeight = 42.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (active && line.backgroundWords.isNotEmpty()) {
                        KaraokeSweepText(
                            text = line.backgroundContent,
                            words = line.backgroundWords,
                            wordOffset = 0,
                            positionState = positionState,
                            preparedSpans = prepared.backgroundWordSpans,
                            style = backgroundStyle,
                            baseColor = lyricBaseColor.copy(alpha = 0.18f),
                            highlightBrush = highlightBrush,
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        Text(
                            text = line.backgroundContent,
                            color = if (active && gradientEnabled) {
                                Color.Unspecified
                            } else if (active) {
                                lyricBaseColor.copy(alpha = 0.82f)
                            } else {
                                lyricBaseColor.copy(alpha = 0.18f)
                            },
                            style = if (active && gradientEnabled) {
                                backgroundStyle.copy(brush = dsmusicHomeStudioGradientBrush())
                            } else {
                                backgroundStyle.copy(brush = null)
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}

/**
 * A separate lyrics surface. Unsynced text remains static; timed sources use a
 * lightweight active-line and active-word renderer scoped to the visible page.
 */
@Composable
internal fun PlainLyricsPage(
    currentSong: SongItem?,
    syncedLyrics: List<LyricLine>?,
    currentPositionProvider: () -> Int,
    playbackSpeedProvider: () -> Float,
    isPlaying: Boolean,
    onSeekTo: (Int) -> Unit,
    plainLyrics: String?,
    isLoading: Boolean,
    lyricsError: Throwable?,
    backgroundBrush: Brush,
    contentColor: Color,
    secondaryColor: Color,
    accentColor: Color,
    lyricsColorStyle: String,
    lyricsFontFamily: String = "Sans",
    showTranslatedLyrics: Boolean,
    isTranslating: Boolean,
    translationMessage: String,
    onTranslateLyrics: () -> Unit,
    providerResults: Map<String, LyricsResult>,
    selectedProvider: String?,
    onProviderSelected: (String) -> Unit,
    onRetryLyrics: () -> Unit,
    onFetchLyrics: () -> Unit,
    onDismiss: () -> Unit
) {
    var showProviderPicker by remember { mutableStateOf(false) }
    // Keep the current track's text mounted while same-track provider updates arrive.
    // The first frame still shows a loader, but subsequent payload changes do not
    // tear down the visible list or flash the loading state again.
    var displayLines by remember(currentSong?.id) { mutableStateOf<List<String>>(emptyList()) }
    var hasPreparedContent by remember(currentSong?.id) { mutableStateOf(false) }
    var linesLocked by remember(currentSong?.id) { mutableStateOf(false) }
    var lastTranslationMode by remember(currentSong?.id) { mutableStateOf(showTranslatedLyrics) }

    LaunchedEffect(plainLyrics, syncedLyrics, showTranslatedLyrics) {
        if (lastTranslationMode != showTranslatedLyrics) {
            lastTranslationMode = showTranslatedLyrics
            linesLocked = false
        }
        // Provider searches can return several results for the same song. Keep the first
        // usable payload mounted; a manual provider selection explicitly unlocks it.
        if (linesLocked) return@LaunchedEffect

        val preparedLines = withContext(Dispatchers.Default) {
            val source = plainLyrics?.takeIf { it.isNotBlank() }
                ?: convertSyncedLyricsToPlainText(syncedLyrics.orEmpty())
            if (source.isBlank()) {
                emptyList()
            } else {
                source.replace("\r\n", "\n")
                    .replace('\r', '\n')
                    .split('\n')
                    .map { it.trimEnd() }
            }
        }
        if (preparedLines != displayLines) displayLines = preparedLines
        if (preparedLines.isNotEmpty()) linesLocked = true
        hasPreparedContent = true
    }

    val activeResult = selectedProvider?.let { providerResults[it] }
        ?: providerResults.values.firstOrNull { it.hasLyrics() }
    val sourceLabel = activeResult?.providerName
        ?.takeIf { it.isNotBlank() }
        ?: selectedProvider?.takeIf { it.isNotBlank() }
        ?: "Choose lyrics source"

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        val dialogWindow = (LocalView.current.parent as? DialogWindowProvider)?.window
        DisposableEffect(dialogWindow) {
            dialogWindow?.setLayout(
                android.view.WindowManager.LayoutParams.MATCH_PARENT,
                android.view.WindowManager.LayoutParams.MATCH_PARENT
            )
            onDispose { }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(backgroundBrush)
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(58.dp)
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.ArrowBack,
                            contentDescription = "Close lyrics",
                            tint = contentColor,
                            modifier = Modifier.size(23.dp)
                        )
                    }
                    Column(
                        modifier = Modifier.weight(1f).padding(start = 8.dp),
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "LYRICS",
                            color = contentColor,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.4.sp,
                            style = DsmusicTheme.typography.titleMedium.copy(fontSize = 10.sp)
                        )
                        Text(
                            text = "PLAIN TEXT",
                            color = secondaryColor,
                            style = DsmusicTheme.typography.labelSmall,
                            letterSpacing = 1.2.sp
                        )
                    }
                    IconButton(
                        onClick = { if (!isTranslating) onTranslateLyrics() },
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Translate,
                            contentDescription = if (showTranslatedLyrics) "Show original lyrics" else "Translate lyrics",
                            tint = if (showTranslatedLyrics) accentColor else contentColor,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    IconButton(
                        onClick = { showProviderPicker = true },
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.MoreVert,
                            contentDescription = "Choose lyrics provider",
                            tint = contentColor,
                            modifier = Modifier.size(23.dp)
                        )
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                        .background(
                            color = contentColor.copy(alpha = 0.055f),
                            shape = RoundedCornerShape(20.dp)
                        )
                        .padding(horizontal = 18.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Text(
                        text = currentSong?.title?.takeIf { it.isNotBlank() } ?: "Unknown track",
                        color = contentColor,
                        style = DsmusicTheme.typography.titleLarge.copy(fontSize = 11.sp, lineHeight = 14.sp),
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = currentSong?.artist?.takeIf { it.isNotBlank() } ?: "Unknown artist",
                        color = secondaryColor,
                        style = DsmusicTheme.typography.bodyMedium.copy(fontSize = 10.sp, lineHeight = 12.sp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (translationMessage.isNotBlank()) {
                        Text(
                            text = translationMessage,
                            color = secondaryColor,
                            style = DsmusicTheme.typography.labelSmall,
                            maxLines = 2,
                            modifier = Modifier.padding(top = 5.dp)
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = sourceLabel,
                            color = accentColor,
                            style = DsmusicTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            modifier = Modifier.weight(1f),
                            overflow = TextOverflow.Ellipsis
                        )
                        TextButton(
                            onClick = { showProviderPicker = true },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 5.dp)
                        ) {
                            Text(
                                text = "Change source",
                                color = accentColor,
                                style = DsmusicTheme.typography.labelMedium
                            )
                        }
                    }
                }

                if (!syncedLyrics.isNullOrEmpty()) {
                    SyncedLyricsPageContent(
                        lyrics = syncedLyrics,
                        currentSong = currentSong,
                        currentPositionProvider = currentPositionProvider,
                        playbackSpeedProvider = playbackSpeedProvider,
                        isPlaying = isPlaying,
                        contentColor = contentColor,
                        lyricsColorStyle = lyricsColorStyle,
                        lyricsFontFamily = lyricsFontFamily,
                        onSeekTo = onSeekTo
                    )
                } else if (displayLines.isNotEmpty()) {
                    Text(
                        text = "${displayLines.size} lines",
                        color = secondaryColor.copy(alpha = 0.8f),
                        style = DsmusicTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 22.dp, vertical = 4.dp)
                    )
                    LazyColumn(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        contentPadding = PaddingValues(
                            start = 24.dp,
                            end = 24.dp,
                            top = 12.dp,
                            bottom = 36.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        itemsIndexed(
                            items = displayLines,
                            key = { index, _ -> "plain-lyrics-line-$index" }
                        ) { _, line ->
                            if (line.isBlank()) {
                                Spacer(modifier = Modifier.height(8.dp))
                            } else {
                                Text(
                                    text = line,
                                    color = when (lyricsColorStyle) {
                                    "Home Studio Gradient" -> Color.White.copy(alpha = 0.22f)
                                    "White", "UI Matching" -> Color.White
                                    else -> contentColor
                                },
                                    style = DsmusicTheme.typography.bodyLarge.copy(
                                        fontSize = 44.sp,
                                        lineHeight = 58.sp,
                                        fontFamily = when (lyricsFontFamily) {
                                            "Serif" -> FontFamily.Serif
                                            "Mono" -> FontFamily.Monospace
                                            else -> GoogleSansFlexNonRounded
                                        },
                                        fontWeight = FontWeight.ExtraBold,
                                        brush = null,
                                        platformStyle = androidx.compose.ui.text.PlatformTextStyle(
                                            includeFontPadding = false
                                        )
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                } else if (isLoading || !hasPreparedContent) {
                    Box(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        LoadingIndicator(color = accentColor)
                    }
                } else {
                    Box(
                        modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 28.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = if (lyricsError != null) "Lyrics could not be loaded" else "No lyrics available",
                                color = contentColor,
                                style = DsmusicTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = lyricsError?.message
                                    ?.takeIf { it.isNotBlank() }
                                    ?: "Try another provider or search for lyrics again.",
                                color = secondaryColor,
                                style = DsmusicTheme.typography.bodyMedium
                            )
                            TextButton(
                                onClick = if (lyricsError != null) onRetryLyrics else onFetchLyrics
                            ) {
                                Text(
                                    text = if (lyricsError != null) "Retry" else "Find lyrics",
                                    color = accentColor
                                )
                            }
                        }
                    }
                }
            }
        }

        if (showProviderPicker) {
            LyricsProviderPickerSheet(
                providerResults = providerResults,
                selectedProvider = selectedProvider,
                onProviderSelected = { provider ->
                    linesLocked = false
                    onProviderSelected(provider)
                    showProviderPicker = false
                },
                onRefindClick = onRetryLyrics,
                accentColor = accentColor,
                contentColor = contentColor,
                backgroundBrush = backgroundBrush,
                onDismissRequest = { showProviderPicker = false }
            )
        }
    }
}



/** Landscape right-pane affordance; tapping opens the standalone lyrics page. */
@Composable
internal fun LandscapeLyricsOpener(
    modifier: Modifier = Modifier,
    contentColor: Color,
    secondaryColor: Color,
    accentColor: Color,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(24.dp)
    Column(
        modifier = modifier
            .clip(shape)
            .background(contentColor.copy(alpha = 0.035f), shape)
            .clickable(onClick = onClick)
            .padding(18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(68.dp)
                .background(accentColor.copy(alpha = 0.14f), RoundedCornerShape(22.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.Fullscreen,
                contentDescription = "Open full-screen lyrics",
                tint = accentColor,
                modifier = Modifier.size(34.dp)
            )
        }
        Spacer(Modifier.height(16.dp))
        Text(
            text = "Open full-screen lyrics",
            color = contentColor,
            style = DsmusicTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = "View the lyrics on the full screen",
            color = secondaryColor,
            style = DsmusicTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 5.dp)
        )
    }
}

/** Compact embedded variant of the standalone plain-lyrics page for landscape players. */
@Composable
internal fun PlainLyricsSideView(
    modifier: Modifier = Modifier,
    currentSong: SongItem?,
    syncedLyrics: List<LyricLine>?,
    plainLyrics: String?,
    isLoading: Boolean,
    lyricsError: Throwable?,
    backgroundBrush: Brush,
    contentColor: Color,
    secondaryColor: Color,
    accentColor: Color,
    lyricsColorStyle: String,
    lyricsFontFamily: String,
    currentPositionProvider: () -> Int,
    playbackSpeedProvider: () -> Float,
    isPlaying: Boolean,
    onSeekTo: (Int) -> Unit,
    onZoom: () -> Unit,
    providerResults: Map<String, LyricsResult>,
    selectedProvider: String?,
    onProviderSelected: (String) -> Unit,
    onRetryLyrics: () -> Unit,
    onFetchLyrics: () -> Unit
) {
    var showProviderPicker by remember { mutableStateOf(false) }
    var displayLines by remember(currentSong?.id) { mutableStateOf<List<String>>(emptyList()) }
    var prepared by remember(currentSong?.id) { mutableStateOf(false) }
    val activeResult = selectedProvider?.let { providerResults[it] }
        ?: providerResults.values.firstOrNull { it.hasLyrics() }
    val sourceLabel = activeResult?.providerName?.takeIf { it.isNotBlank() }
        ?: selectedProvider?.takeIf { it.isNotBlank() }
        ?: "Choose lyrics source"

    LaunchedEffect(plainLyrics, syncedLyrics) {
        val nextLines = withContext(Dispatchers.Default) {
            val source = plainLyrics?.takeIf { it.isNotBlank() }
                ?: convertSyncedLyricsToPlainText(syncedLyrics.orEmpty())
            source.lineSequence().map { it.trimEnd() }.toList()
        }
        if (nextLines != displayLines) displayLines = nextLines
        prepared = true
    }

    Column(
        modifier = modifier
            .background(backgroundBrush, RoundedCornerShape(22.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().height(48.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "LYRICS",
                    color = contentColor,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp,
                    style = DsmusicTheme.typography.titleSmall.copy(fontSize = 10.sp)
                )
                Text(
                    text = currentSong?.title ?: "Plain text",
                    color = secondaryColor,
                    style = DsmusicTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(onClick = onZoom, modifier = Modifier.size(36.dp)) {
                Icon(
                    imageVector = Icons.Rounded.Fullscreen,
                    contentDescription = "Expand lyrics",
                    tint = accentColor,
                    modifier = Modifier.size(20.dp)
                )
            }
            IconButton(onClick = { showProviderPicker = true }, modifier = Modifier.size(36.dp)) {
                Icon(
                    imageVector = Icons.Rounded.MoreVert,
                    contentDescription = "Choose lyrics provider",
                    tint = contentColor,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = sourceLabel,
                color = accentColor,
                style = DsmusicTheme.typography.labelSmall.copy(fontSize = 10.sp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            TextButton(
                onClick = { showProviderPicker = true },
                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text("Change source", color = accentColor, style = DsmusicTheme.typography.labelSmall.copy(fontSize = 10.sp))
            }
        }
        when {
            !syncedLyrics.isNullOrEmpty() -> SyncedLyricsPageContent(
                lyrics = syncedLyrics,
                currentSong = currentSong,
                currentPositionProvider = currentPositionProvider,
                playbackSpeedProvider = playbackSpeedProvider,
                isPlaying = isPlaying,
                contentColor = contentColor,
                lyricsColorStyle = lyricsColorStyle,
                lyricsFontFamily = lyricsFontFamily,
                onSeekTo = onSeekTo
            )
            displayLines.isNotEmpty() -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                itemsIndexed(displayLines, key = { index, _ -> "landscape-plain-lyrics-$index" }) { _, line ->
                    if (line.isNotBlank()) {
                        Text(
                            text = line,
                            color = when (lyricsColorStyle) {
                                "Home Studio Gradient" -> Color.White.copy(alpha = 0.22f)
                                "White", "UI Matching" -> Color.White
                                else -> contentColor
                            },
                            style = DsmusicTheme.typography.bodyLarge.copy(
                                fontSize = 22.sp,
                                lineHeight = 29.sp,
                                fontFamily = when (lyricsFontFamily) {
                                    "Serif" -> FontFamily.Serif
                                    "Mono" -> FontFamily.Monospace
                                    else -> GoogleSansFlexNonRounded
                                },
                                fontWeight = FontWeight.ExtraBold,
                                brush = null,
                                platformStyle = androidx.compose.ui.text.PlatformTextStyle(
                                    includeFontPadding = false
                                )
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        Spacer(Modifier.height(4.dp))
                    }
                }
            }
            isLoading || !prepared -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                LoadingIndicator(color = accentColor)
            }
            else -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = if (lyricsError != null) "Lyrics could not be loaded" else "No lyrics available",
                        color = contentColor,
                        style = DsmusicTheme.typography.titleSmall
                    )
                    TextButton(onClick = if (lyricsError != null) onRetryLyrics else onFetchLyrics) {
                        Text(if (lyricsError != null) "Retry" else "Find lyrics", color = accentColor)
                    }
                }
            }
        }
    }

    if (showProviderPicker) {
        LyricsProviderPickerSheet(
            providerResults = providerResults,
            selectedProvider = selectedProvider,
            onProviderSelected = { provider ->
                onProviderSelected(provider)
                showProviderPicker = false
            },
            onRefindClick = onRetryLyrics,
            accentColor = accentColor,
            contentColor = contentColor,
            backgroundBrush = backgroundBrush,
            onDismissRequest = { showProviderPicker = false }
        )
    }
}


/**
 * Keeps the highlighted line on the vertical centre anchor. Normal line changes
 * animate only the measured correction distance; initial placement and large
 * seeks re-anchor immediately to avoid long off-screen animations.
 */
@OptIn(ExperimentalFoundationApi::class)
private suspend fun scrollLyricLineToCenter(
    listState: androidx.compose.foundation.lazy.LazyListState,
    index: Int,
    estimatedItemHalfHeightPx: Int,
    durationMs: Int,
    immediate: Boolean
) {
    if (index < 0) return

    snapshotFlow {
        listState.layoutInfo.viewportEndOffset - listState.layoutInfo.viewportStartOffset
    }.first { it > 0 }

    var layout = listState.layoutInfo
    var item = layout.visibleItemsInfo.firstOrNull { it.index == index }
    if (item == null) {
        // This is normally only reached for the initial mid-song placement or a seek.
        val viewportCenter = (layout.viewportStartOffset + layout.viewportEndOffset) / 2f
        val initialOffset = -(viewportCenter - estimatedItemHalfHeightPx).roundToInt()
        listState.scrollToItem(index, scrollOffset = initialOffset)
        withFrameNanos { }
        layout = listState.layoutInfo
        item = layout.visibleItemsInfo.firstOrNull { it.index == index } ?: return
    }

    val viewportCenter = (layout.viewportStartOffset + layout.viewportEndOffset) / 2f
    val itemCenter = item.offset + item.size / 2f
    val correctionPx = itemCenter - viewportCenter
    if (abs(correctionPx) > 1.5f) {
        if (immediate || durationMs <= 0) {
            listState.scrollBy(correctionPx)
        } else {
            listState.animateScrollBy(
                value = correctionPx,
                animationSpec = tween(
                    durationMillis = durationMs.coerceAtLeast(1),
                    easing = FastOutSlowInEasing
                )
            )
        }
    }
}
