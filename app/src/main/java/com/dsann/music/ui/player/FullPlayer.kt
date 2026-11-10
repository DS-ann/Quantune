package com.dsann.music.ui.player

import com.dsann.music.ui.theme.DsmusicIconButton
import com.dsann.music.ui.theme.DsmusicSurface
import com.dsann.music.ui.theme.DsmusicTextButton
import android.annotation.SuppressLint
import android.app.Activity
import android.Manifest
import android.content.pm.PackageManager
import android.content.Context
import android.content.SharedPreferences
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.ThumbDown
import androidx.compose.material.icons.rounded.NightsStay
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import com.dsann.music.ui.theme.Icon
import com.dsann.music.ui.theme.IconButton
import com.dsann.music.ui.theme.DsmusicTheme
import com.dsann.music.ui.theme.dsmusicHomeStudioGradientBrush
import com.dsann.music.ui.theme.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.runtime.produceState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.State
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dsann.music.MainActivity
import com.dsann.music.ui.SettingsViewModel
import com.dsann.music.ui.explore.QueueSongActionsBottomSheet
import com.dsann.music.R
import com.dsann.music.data.lyrics.LyricLine
import com.dsann.music.data.features.AiLyricsTranslationSettingsStore
import com.dsann.music.data.features.AiLyricsTranslationSession
import com.dsann.music.data.lyrics.LineSyncedLyricTimeline
import com.dsann.music.data.lyrics.LyricsResult
import com.dsann.music.model.SongItem
import com.dsann.music.viewmodel.PlayerSharedViewModel
import com.dsann.music.data.innertube.YouTubeMusic
import com.dsann.music.data.innertube.OnlineArtistRef
import androidx.compose.ui.window.Dialog
import androidx.compose.material.icons.rounded.ChevronRight
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow

@Composable
private fun AudioReactivePlayerVisualizer(
    style: String,
    levels: FloatArray,
    color: Color,
    colorMode: String
) {
    androidx.compose.foundation.Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(if (style == "Circular Spectrum") 54.dp else 28.dp)
    ) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val safeLevels = levels.takeIf { it.isNotEmpty() } ?: FloatArray(16)

        fun levelAt(position: Float): Float {
            val scaled = position.coerceIn(0f, 1f) * (safeLevels.size - 1)
            val lower = scaled.toInt().coerceIn(0, safeLevels.lastIndex)
            val upper = (lower + 1).coerceAtMost(safeLevels.lastIndex)
            val fraction = scaled - lower
            return (safeLevels[lower] * (1f - fraction) + safeLevels[upper] * fraction)
                .coerceIn(0f, 1f)
        }

        fun visualizerColor(position: Float): Color =
            if (colorMode == "Spectrum") {
                Color.hsv(position.coerceIn(0f, 1f) * 300f, 0.78f, 0.96f)
            } else {
                color
            }

        when (style) {
            // Classic bar visualizer: retain the original compact bar appearance,
            // but drive its height from the real playback FFT levels.
            "Bars" -> {
                val count = if (android.os.Build.VERSION.SDK_INT <= android.os.Build.VERSION_CODES.M) 8 else 32
                val gap = 3.dp.toPx()
                val barWidth = ((size.width - gap * (count - 1)) / count).coerceAtLeast(1f)
                repeat(count) { index ->
                    val position = if (count <= 1) 0f else index / (count - 1f)
                    val level = levelAt(position)
                    val barHeight = (size.height * (0.18f + level * 0.78f))
                        .coerceIn(2.dp.toPx(), size.height)
                    val x = index * (barWidth + gap)
                    drawRoundRect(
                        color = visualizerColor(position).copy(alpha = 0.35f + level * 0.55f),
                        topLeft = Offset(x, center.y - barHeight / 2f),
                        size = androidx.compose.ui.geometry.Size(barWidth, barHeight),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(
                            x = barWidth / 2f,
                            y = barWidth / 2f
                        )
                    )
                }
            }

            "Spectrum Bars" -> {
                val count = 32
                val gap = 2.dp.toPx()
                val barWidth = ((size.width - gap * (count - 1)) / count).coerceAtLeast(1f)
                val minHeight = 2.dp.toPx()
                repeat(count) { index ->
                    val position = index / (count - 1f)
                    val level = levelAt(position)
                    val barHeight = (size.height * (0.04f + level * 0.94f))
                        .coerceIn(minHeight, size.height)
                    val x = index * (barWidth + gap)
                    drawRoundRect(
                        color = visualizerColor(position).copy(alpha = 0.42f + level * 0.58f),
                        topLeft = Offset(x, center.y - barHeight / 2f),
                        size = androidx.compose.ui.geometry.Size(barWidth, barHeight),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(
                            x = barWidth * 0.45f,
                            y = barWidth * 0.45f
                        )
                    )
                }
            }

            "Circular Spectrum" -> {
                val count = 48
                val minDimension = minOf(size.width, size.height)
                val innerRadius = minDimension * 0.22f
                val maxLength = minDimension * 0.27f
                repeat(count) { index ->
                    val position = index / (count - 1f)
                    val level = levelAt(position)
                    val angle = -1.5707963f + index * (6.2831855f / count)
                    val direction = Offset(kotlin.math.cos(angle), kotlin.math.sin(angle))
                    val start = center + direction * innerRadius
                    val end = center + direction * (innerRadius + maxLength * (0.05f + level * 0.95f))
                    drawLine(
                        color = visualizerColor(position).copy(alpha = 0.38f + level * 0.62f),
                        start = start,
                        end = end,
                        strokeWidth = 2.dp.toPx(),
                        cap = androidx.compose.ui.graphics.StrokeCap.Round
                    )
                }
                drawCircle(
                    color = color.copy(alpha = 0.18f),
                    radius = innerRadius * 0.68f,
                    center = center,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.dp.toPx())
                )
            }
        }
    }
}

/**
 * Compact one-line lyric preview for the portrait full player.
 *
 * Ordinary synced lyrics advance at the next line timestamp. Karaoke lines are
 * presented as whole lines and advance only after the outgoing line's final timed
 * word has completed, reusing the already selected/cached lyric payload.
 */
@Composable
private fun PortraitSyncedLyricPreview(
    lyrics: List<LyricLine>,
    currentSong: SongItem?,
    currentPositionProvider: () -> Int,
    isPlaying: Boolean,
    contentColor: Color,
    lyricsColorStyle: String,
    compact: Boolean,
    onLineBoundsChanged: (Rect?) -> Unit,
    onLineNeedsMoreSpace: () -> Unit
) {
    val displayTimeline = remember(lyrics) {
        LineSyncedLyricTimeline.build(lyrics)
    }
    val displayLines = remember(displayTimeline) {
        displayTimeline.map { it.line }
    }
    val isKaraokeLyrics = remember(displayLines) {
        displayLines.any { it.isWordByWord && it.words.isNotEmpty() }
    }
    val timedPositionProvider: () -> Int = remember(currentPositionProvider, isKaraokeLyrics) {
        if (isKaraokeLyrics) {
            {
                (currentPositionProvider().toLong() + DEFAULT_KARAOKE_TIMING_BASE_OFFSET_MS)
                    .coerceIn(0L, Int.MAX_VALUE.toLong())
                    .toInt()
            }
        } else {
            currentPositionProvider
        }
    }
    val songKey = currentSong?.videoId?.takeIf { it.isNotBlank() }
        ?: currentSong?.path?.takeIf { it.isNotBlank() }
        ?: currentSong?.id?.toString()
    val songDurationMs = currentSong?.duration?.takeIf { it > 0L }
    // Queue, widgets and portrait preview share this same first-word/last-word
    // boundary calculation, so karaoke sources advance as whole synced lines.
    val lineCompletionTimes = remember(displayTimeline) {
        displayTimeline.map { it.completionTimeMs }
    }
    val finalWordEndMs = remember(displayTimeline) {
        displayTimeline.lastOrNull()?.lastWordEndMs
    }

    val activeLineText by produceState<String?>(
        initialValue = null,
        songKey,
        displayLines,
        displayTimeline,
        lineCompletionTimes,
        finalWordEndMs,
        songDurationMs,
        isPlaying,
        timedPositionProvider
    ) {
        if (displayLines.isEmpty()) {
            value = null
            return@produceState
        }

        // Retain the active index across clock polls. Re-scan only after a backwards seek.
        var targetIndex = -1
        var previousPositionMs = -1L
        while (true) {
            val positionMs = timedPositionProvider().coerceAtLeast(0).toLong()
            val hasPassedFinalLine =
                (songDurationMs != null && positionMs >= songDurationMs) ||
                    (finalWordEndMs != null && positionMs > finalWordEndMs + 1000L)

            if (positionMs < displayTimeline.first().startTimeMs || hasPassedFinalLine) {
                targetIndex = -1
            } else {
                if (targetIndex < 0 || positionMs < previousPositionMs) {
                    targetIndex = 0
                }
                while (
                    targetIndex < displayLines.lastIndex &&
                    positionMs >= lineCompletionTimes[targetIndex]
                ) {
                    targetIndex += 1
                }
            }
            previousPositionMs = positionMs

            val nextText = displayLines.getOrNull(targetIndex)?.content?.trim()
                ?.takeIf { it.isNotEmpty() }
            if (value != nextText) value = nextText

            // Poll the native playback clock without driving per-frame recomposition.
            delay(
                if (android.os.Build.VERSION.SDK_INT <= android.os.Build.VERSION_CODES.M) {
                    84L
                } else if (isPlaying) {
                    48L
                } else {
                    84L
                }
            )
        }
    }

    LaunchedEffect(activeLineText) {
        if (activeLineText.isNullOrBlank()) onLineBoundsChanged(null)
    }

    if (!activeLineText.isNullOrBlank()) {
        val baseFontSize = if (compact) 13f else 16f
        // Use one text metric configuration on every Android version. Explicit line
        // height gives the glyphs vertical room without adding API-specific font padding.
        val previewTextStyle = DsmusicTheme.typography.bodyLarge.copy(
            fontWeight = FontWeight.SemiBold,
            fontSize = baseFontSize.sp,
            lineHeight = (baseFontSize * 1.5f).sp,
            lineHeightStyle = androidx.compose.ui.text.style.LineHeightStyle(
                alignment = androidx.compose.ui.text.style.LineHeightStyle.Alignment.Center,
                trim = androidx.compose.ui.text.style.LineHeightStyle.Trim.None
            ),
            platformStyle = androidx.compose.ui.text.PlatformTextStyle(
                includeFontPadding = false
            ),
            brush = if (lyricsColorStyle == "Home Studio Gradient") {
                dsmusicHomeStudioGradientBrush()
            } else {
                null
            }
        )
        BoxWithConstraints(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp),
            contentAlignment = Alignment.Center
        ) {
            val textMeasurer = rememberTextMeasurer()
            val density = LocalDensity.current
            val availableWidthPx = with(density) {
                maxWidth.roundToPx()
            }.coerceAtLeast(1)
            val availableHeightPx = with(density) {
                maxHeight.roundToPx()
            }.coerceAtLeast(1)

            // Check the preferred size before rendering. If the current weighted slot
            // is too short, the parent reallocates space from the spacer above the title.
            val preferredLineHeightPx = remember(
                activeLineText,
                availableWidthPx,
                lyricsColorStyle,
                contentColor,
                compact
            ) {
                textMeasurer.measure(
                    text = activeLineText.orEmpty(),
                    style = previewTextStyle,
                    constraints = Constraints(maxWidth = availableWidthPx),
                    softWrap = false,
                    maxLines = 1,
                    overflow = TextOverflow.Clip
                ).size.height
            }
            LaunchedEffect(activeLineText, availableHeightPx, preferredLineHeightPx, compact) {
                if (
                    !compact &&
                    !activeLineText.isNullOrBlank() &&
                    preferredLineHeightPx > availableHeightPx
                ) {
                    onLineNeedsMoreSpace()
                }
            }

            // Fit to both the real width and height of the lyric slot. This prevents
            // clipping even when the player is shown in a short or resized window.
            val fittedText = remember(
                activeLineText,
                availableWidthPx,
                availableHeightPx,
                lyricsColorStyle,
                contentColor,
                compact
            ) {
                var candidateSize = baseFontSize
                fun measureAt(size: Float) = textMeasurer.measure(
                    text = activeLineText.orEmpty(),
                    style = previewTextStyle.copy(
                        fontSize = size.sp,
                        lineHeight = (size * 1.5f).sp
                    ),
                    constraints = Constraints(
                        maxWidth = availableWidthPx,
                        maxHeight = availableHeightPx
                    ),
                    softWrap = false,
                    maxLines = 1,
                    overflow = TextOverflow.Clip
                )
                var measured = measureAt(candidateSize)
                while (measured.hasVisualOverflow && candidateSize > 7f) {
                    candidateSize = (candidateSize - 0.5f).coerceAtLeast(7f)
                    measured = measureAt(candidateSize)
                }
                candidateSize to !measured.hasVisualOverflow
            }
            LaunchedEffect(activeLineText, fittedText.second) {
                if (activeLineText.isNullOrBlank() || !fittedText.second) {
                    onLineBoundsChanged(null)
                }
            }

            val activeLyricsTextLayout = remember(activeLineText, fittedText.first, availableWidthPx) {
                androidx.compose.runtime.mutableStateOf<androidx.compose.ui.text.TextLayoutResult?>(null)
            }

            if (fittedText.second) LyricsText(
                text = activeLineText.orEmpty(),
                style = previewTextStyle.copy(
                    fontSize = fittedText.first.sp,
                    lineHeight = (fittedText.first * 1.5f).sp
                ),
                color = if (lyricsColorStyle == "Home Studio Gradient") Color.Unspecified else contentColor,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Clip,
                outlineWidth = 0.dp,
                onTextLayout = { activeLyricsTextLayout.value = it },
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth()
                    .onGloballyPositioned { coordinates ->
                    val position = coordinates.positionInRoot()
                    onLineBoundsChanged(
                        Rect(
                            position.x,
                            position.y,
                            position.x + coordinates.size.width.toFloat(),
                            position.y + coordinates.size.height.toFloat()
                        )
                    )
                }
            )
        }
    }
}

@Composable
private fun LandscapePlayerDetailsPane(
    modifier: Modifier = Modifier,
    onOpenFullScreenLyrics: () -> Unit,
    syncedLyrics: List<LyricLine>?,
    plainLyrics: String?,
    isLyricsLoading: Boolean,
    lyricsError: Throwable?,
    currentSong: SongItem?,
    currentPositionProvider: () -> Int,
    playbackSpeedProvider: () -> Float,
    isPlaying: Boolean,
    contentColor: Color,
    secondaryColor: Color,
    accentColor: Color,
    selectedProvider: String?,
    providerResults: Map<String, LyricsResult>,
    sourcePickerBackgroundBrush: Brush,
    lyricsColorStyle: String = "Home Studio Gradient",
    onProviderSelected: (String) -> Unit,
    onSeekTo: (Int) -> Unit,
    onFetchLyrics: () -> Unit,
    onRetryLyrics: () -> Unit
) {
    var showProviderPicker by remember { mutableStateOf(false) }
    val panelShape = RoundedCornerShape(24.dp)

    val context = LocalContext.current
    val translationSettingsStore = remember(context) {
        AiLyricsTranslationSettingsStore(context)
    }
    val translationSettings = remember(translationSettingsStore) {
        translationSettingsStore.load()
    }
    val translationSource = remember(syncedLyrics, plainLyrics) {
        when {
            !syncedLyrics.isNullOrEmpty() -> syncedLyrics.joinToString("\n") { line ->
                line.content.ifBlank { line.words.joinToString("") { word -> word.text } }
            }
            !plainLyrics.isNullOrBlank() -> plainLyrics.orEmpty()
            else -> ""
        }
    }
    val translationKey = remember(currentSong?.id, translationSource, translationSettings) {
        currentSong?.id?.takeIf { translationSource.isNotBlank() }?.let { songId ->
            AiLyricsTranslationSession.cacheKey(songId, translationSource, translationSettings)
        }
    }
    val translationCache by AiLyricsTranslationSession.entries.collectAsStateWithLifecycle()
    val cachedTranslation = translationKey?.let { translationCache[it] }
    val translationPreferences = remember(context) {
        context.applicationContext.getSharedPreferences(
            "lyrics_translation_settings",
            Context.MODE_PRIVATE
        )
    }
    var translationEnabled by remember(translationSettings.enabled) {
        mutableStateOf(translationSettings.enabled)
    }
    var showTranslatedLyrics by remember(currentSong?.id, translationKey) {
        mutableStateOf(cachedTranslation != null)
    }
    var translationBusy by remember { mutableStateOf(false) }
    var translationMessage by remember { mutableStateOf("") }
    val translationScope = rememberCoroutineScope()

    DisposableEffect(translationPreferences) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { preferences, key ->
            if (key == "enabled") {
                translationEnabled = preferences.getBoolean("enabled", true)
                if (!translationEnabled) {
                    showTranslatedLyrics = false
                    translationMessage = ""
                }
            }
        }
        translationPreferences.registerOnSharedPreferenceChangeListener(listener)
        onDispose {
            translationPreferences.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }
    LaunchedEffect(cachedTranslation) {
        if (cachedTranslation != null) {
            showTranslatedLyrics = true
        }
    }
    val requestTranslation: () -> Unit = {
        when {
            !translationEnabled -> translationMessage = "Enable translation in Settings → Playback."
            translationBusy -> Unit
            cachedTranslation != null -> showTranslatedLyrics = !showTranslatedLyrics
            currentSong == null || translationSource.isBlank() ->
                translationMessage = "Load lyrics before translating."
            else -> {
                translationBusy = true
                translationMessage = "Translating…"
                translationScope.launch {
                    try {
                        val result = AiLyricsTranslationSession.translate(
                            context = context,
                            songId = currentSong.id,
                            source = translationSource
                        )
                        showTranslatedLyrics = true
                        translationMessage = "Translated with ${result.provider}."
                    } catch (error: Exception) {
                        translationMessage = error.message ?: "Lyrics translation failed."
                    } finally {
                        translationBusy = false
                    }
                }
            }
        }
    }
    val translatedSyncedLyrics = cachedTranslation?.let {
        AiLyricsTranslationSession.mapToSyncedLines(syncedLyrics, it.translatedText)
    }

    Column(
        modifier = modifier
            .background(contentColor.copy(alpha = 0.035f), panelShape)
            .padding(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "LYRICS",
                color = accentColor,
                fontWeight = FontWeight.Bold,
                style = DsmusicTheme.typography.labelLarge
            )
            Spacer(Modifier.weight(1f))
            if (translationEnabled) {
                DsmusicIconButton(
                    onClick = requestTranslation,
                    enabled = !translationBusy
                ) {
                    Icon(
                        Icons.Rounded.Translate,
                        contentDescription = if (showTranslatedLyrics) "Show original lyrics" else "Translate lyrics",
                        tint = if (showTranslatedLyrics) accentColor else contentColor
                    )
                }
            }
            DsmusicIconButton(onClick = { showProviderPicker = true }) {
                Icon(Icons.Rounded.MoreVert, contentDescription = "Choose lyrics source")
            }
            DsmusicIconButton(onClick = onOpenFullScreenLyrics) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_fullscreen),
                    contentDescription = "Open fullscreen lyrics"
                )
            }
            Text(
                text = currentSong?.artist.orEmpty(),
                color = secondaryColor,
                style = DsmusicTheme.typography.labelSmall,
                maxLines = 1
            )
        }

        if (translationMessage.isNotBlank()) {
            Text(
                translationMessage,
                color = secondaryColor,
                style = DsmusicTheme.typography.labelSmall,
                maxLines = 1
            )
        }
        Spacer(Modifier.height(10.dp))

        PlayerLyricsContent(
            currentSong = currentSong,
            syncedLyrics = if (showTranslatedLyrics) translatedSyncedLyrics else syncedLyrics,
            plainLyrics = if (showTranslatedLyrics && translatedSyncedLyrics == null) {
                cachedTranslation?.translatedText ?: plainLyrics
            } else {
                plainLyrics
            },
            isLoading = isLyricsLoading,
            isPlaying = isPlaying,
            lyricsError = lyricsError,
            currentPositionProvider = currentPositionProvider,
            playbackSpeedProvider = playbackSpeedProvider,
            contentReady = true,
            contentColor = contentColor,
            dynamicAccentColor = accentColor,
            lyricsColorStyle = lyricsColorStyle,
            onRetryLyrics = onRetryLyrics,
            onFetchLyrics = onFetchLyrics,
            onSeekTo = onSeekTo,
            selectedProvider = selectedProvider,
            providerResults = providerResults,
            modifier = Modifier.fillMaxSize()
        )
    }

    if (showProviderPicker) {
        LyricsProviderPickerSheet(
            providerResults = providerResults,
            selectedProvider = selectedProvider,
            onProviderSelected = onProviderSelected,
            onRefindClick = onRetryLyrics,
            accentColor = accentColor,
            contentColor = contentColor,
            backgroundBrush = sourcePickerBackgroundBrush,
            onDismissRequest = { showProviderPicker = false }
        )
    }
}

/**
 * Stateful wrapper for the FullPlayer UI component.
 * Decouples the UI component from ViewModel, Activity, and direct permission handling.
 */

@Composable
fun FullPlayerScreen(
    activity: MainActivity,
    viewModel: PlayerSharedViewModel,
    uiState: PlayerUiState,
    songList: List<SongItem>,
    accentColor: Color,
    context: Context,
    onCollapse: () -> Unit,
    onNavigateToListenTogether: () -> Unit,
    dragModifier: Modifier = Modifier,
    isPlayerVisible: Boolean,
    modifier: Modifier = Modifier
) {
    val isLyricsModeEnabled by viewModel.isLyricsModeEnabled.collectAsStateWithLifecycle()
    val syncedLyrics by viewModel.syncedLyrics.collectAsStateWithLifecycle()
    val plainLyrics by viewModel.plainLyrics.collectAsStateWithLifecycle()
    val isLyricsLoading by viewModel.isLyricsLoading.collectAsStateWithLifecycle()
    val lyricsError by viewModel.lyricsError.collectAsStateWithLifecycle()
    val providerResults by viewModel.providerResults.collectAsStateWithLifecycle()
    val selectedProvider by viewModel.selectedProvider.collectAsStateWithLifecycle()
    // Read the service's live ExoPlayer clock for lyrics only. The general-purpose
    // currentPosition StateFlow remains throttled on older devices to protect UI performance.
    val lyricsAudioService by viewModel.audioServiceState.collectAsStateWithLifecycle()
    val currentPositionProvider: () -> Int = remember(viewModel, lyricsAudioService) {
        { lyricsAudioService?.getCurrentPositionForLyrics() ?: viewModel.currentPosition.value }
    }
    val playbackSpeedProvider: () -> Float = remember(lyricsAudioService) {
        { lyricsAudioService?.getPlaybackSpeedForLyrics() ?: 1f }
    }
    val customizeViewModel: SettingsViewModel =
        androidx.lifecycle.viewmodel.compose.viewModel(viewModelStoreOwner = activity)
    val playerLayout by customizeViewModel.playerLayout.collectAsStateWithLifecycle()
    val playerArtworkScale by customizeViewModel.playerArtworkScale.collectAsStateWithLifecycle()
    val playerButtonsPill by customizeViewModel.playerButtonsPill.collectAsStateWithLifecycle()
    val playerButtons by customizeViewModel.playerButtons.collectAsStateWithLifecycle()
    val playerTitleAlign by customizeViewModel.playerTitleAlign.collectAsStateWithLifecycle()
    val playerTitleSize by customizeViewModel.playerTitleSize.collectAsStateWithLifecycle()
    val playerTitleWeight by customizeViewModel.playerTitleWeight.collectAsStateWithLifecycle()
    val playerTitleScroll by customizeViewModel.playerTitleScroll.collectAsStateWithLifecycle()
    val playerTimes by customizeViewModel.playerTimes.collectAsStateWithLifecycle()
    val scrubberStyle by customizeViewModel.scrubberStyle.collectAsStateWithLifecycle()
    val scrubberColor by customizeViewModel.scrubberColor.collectAsStateWithLifecycle()
    val visualizerStyle by customizeViewModel.visualizerStyle.collectAsStateWithLifecycle()
    val playerBackgroundStyle by customizeViewModel.playerBackgroundStyle.collectAsStateWithLifecycle()
    val playerButtonStyle by customizeViewModel.playerButtonStyle.collectAsStateWithLifecycle()
    val scrubberThickness by customizeViewModel.scrubberThickness.collectAsStateWithLifecycle()
    val scrubberThumbStyle by customizeViewModel.scrubberThumbStyle.collectAsStateWithLifecycle()
    val visualizerColor by customizeViewModel.visualizerColor.collectAsStateWithLifecycle()
    val fullscreenVisualizerEnabled by customizeViewModel.fullscreenVisualizerEnabled.collectAsStateWithLifecycle()
    val lyricsColorStyle by customizeViewModel.lyricsColorStyle.collectAsStateWithLifecycle()
    val showSyncedLyricsInPlayer by customizeViewModel.showSyncedLyricsInPlayer.collectAsStateWithLifecycle()

    // Legacy layout values are normalized to Standard; lyrics are entered only through
    // the explicit Lyrics action, never by opening the mini player.

    // Handle back button when lyrics mode is enabled
    BackHandler(enabled = isLyricsModeEnabled) {
        viewModel.setLyricsModeEnabled(false)
    }

    FullPlayer(
        viewModel = viewModel,
        uiState = uiState,
        songList = songList,
        accentColor = accentColor,
        isLyricsModeEnabled = isLyricsModeEnabled,
        syncedLyrics = syncedLyrics,
        plainLyrics = plainLyrics,
        isLyricsLoading = isLyricsLoading,
        lyricsError = lyricsError,
        providerResults = providerResults,
        selectedProvider = selectedProvider,
        playerLayout = playerLayout,
        playerArtworkScale = playerArtworkScale,
        playerButtonsPill = playerButtonsPill,
        playerButtons = playerButtons,
        playerTitleAlign = playerTitleAlign,
        playerTitleSize = playerTitleSize,
        playerTitleWeight = playerTitleWeight,
        playerTitleScroll = playerTitleScroll,
        playerTimes = playerTimes,
        scrubberStyle = scrubberStyle,
        scrubberColor = scrubberColor,
        visualizerStyle = visualizerStyle,
        playerBackgroundStyle = playerBackgroundStyle,
        playerButtonStyle = playerButtonStyle,
        lyricsColorStyle = lyricsColorStyle,
        scrubberThickness = scrubberThickness,
        scrubberThumbStyle = scrubberThumbStyle,
        visualizerColor = visualizerColor,
        fullscreenVisualizerEnabled = fullscreenVisualizerEnabled,
        showSyncedLyricsInPlayer = showSyncedLyricsInPlayer,
        onProviderSelected = { viewModel.selectLyricsProvider(it) },
        currentPositionProvider = currentPositionProvider,
        playbackSpeedProvider = playbackSpeedProvider,
        currentPositionFlow = viewModel.currentPosition,
        isPlayerVisible = isPlayerVisible,
        onCollapse = onCollapse,
        onNavigateToListenTogether = onNavigateToListenTogether,
        onPlayPauseClick = {
            if (uiState.isPlaying) viewModel.pauseAudio() else viewModel.playAudio()
        },
        onPreviousClick = {
            viewModel.playPreviousSong()
        },
        onNextClick = {
            viewModel.playNextSong(force = true)
        },
        onLyricsModeChanged = { enabled ->
            viewModel.setLyricsModeEnabled(enabled)
        },
        onFetchLyrics = {
            viewModel.fetchLyricsForCurrentSong()
        },
        onRetryLyrics = {
            viewModel.retryLyrics()
        },
        onSeekTo = { position ->
            viewModel.seekTo(position)
        },
        onFavoriteClick = {
            viewModel.toggleFavorite()
        },
        onDislikeClick = {
            viewModel.toggleDislike()
        },
        onArtistClick = { artistId, artistName ->
            onCollapse()
            activity.showArtistPage(artistId, artistName)
        },
        dragModifier = dragModifier,
        modifier = modifier
    )
}

/**
 * Purely stateless UI representation of the Full Player.
 * Does not depend on ViewModels or Activities, ensuring great previewability and testability.
 */
@SuppressLint("ConfigurationScreenWidthHeight")

@Composable
fun FullPlayer(
    viewModel: PlayerSharedViewModel,
    uiState: PlayerUiState,
    songList: List<SongItem>,
    accentColor: Color,
    isLyricsModeEnabled: Boolean,
    syncedLyrics: List<LyricLine>?,
    plainLyrics: String?,
    isLyricsLoading: Boolean,
    lyricsError: Throwable?,
    currentPositionProvider: () -> Int,
    playbackSpeedProvider: () -> Float,
    currentPositionFlow: StateFlow<Int>,
    isPlayerVisible: Boolean,
    onCollapse: () -> Unit,
    onNavigateToListenTogether: () -> Unit,
    onPlayPauseClick: () -> Unit,
    onPreviousClick: () -> Unit,
    onNextClick: () -> Unit,
    onLyricsModeChanged: (Boolean) -> Unit,
    onFetchLyrics: () -> Unit,
    onRetryLyrics: () -> Unit,
    onSeekTo: (Int) -> Unit,
    onFavoriteClick: () -> Unit,
    onDislikeClick: () -> Unit,
    providerResults: Map<String, LyricsResult>,
    selectedProvider: String?,
    playerLayout: String = "Standard",
    playerArtworkScale: Float = 1f,
    playerButtonsPill: Boolean = true,
    playerButtons: List<String> = listOf(
        "lyrics", "video", "queue", "share", "download", "listen_together", "repeat"
    ),
    playerTitleAlign: String = "Left",
    playerTitleSize: String = "Medium",
    playerTitleWeight: String = "Bold",
    playerTitleScroll: Boolean = true,
    playerTimes: String = "Both",
    scrubberStyle: String = "Waveform",
    scrubberColor: String = "Accent",
    visualizerStyle: String = "Off",
    playerBackgroundStyle: String = "Artwork",
    playerButtonStyle: String = "Pill",
    lyricsColorStyle: String = "Home Studio Gradient",
    scrubberThickness: Float = 1.5f,
    scrubberThumbStyle: String = "Auto",
    visualizerColor: String = "Accent",
    fullscreenVisualizerEnabled: Boolean = true,
    showSyncedLyricsInPlayer: Boolean = true,
    onProviderSelected: (String) -> Unit,
    onArtistClick: (String?, String) -> Unit = { _, _ -> },
    dragModifier: Modifier = Modifier,
    modifier: Modifier = Modifier
) {
    val isDark = DsmusicTheme.colorScheme.background.luminance() < 0.5f
    val contentColor = if (isDark) Color.White else Color.Black
    // Light mode uses black glyphs on white control surfaces; dark mode reverses them.
    val playerButtonColor = if (isDark) Color.Black else Color.White
    val playerButtonContentColor = if (isDark) Color.White else Color.Black
    val contentSecondary = contentColor.copy(alpha = 0.6f)
    val playerFrameTimeNanos = rememberAppAnimationFrameTimeNanos(
        active = isPlayerVisible && uiState.isPlaying
    )
    val artistOptions = remember(
        uiState.currentSong?.artistRefs,
        uiState.currentSong?.artist,
        uiState.currentSong?.artistId
    ) {
        val current = uiState.currentSong
        val linked = current?.artistRefs.orEmpty().filter { it.name.isNotBlank() }
        if (linked.isNotEmpty()) {
            linked.distinctBy { it.id ?: it.name.trim().lowercase() }
        } else {
            val names = current?.artist.orEmpty().split(
                Regex(",\\s*|\\s*•\\s*|\\s*&\\s*|\\s+and\\s+|\\s+feat\\.?\\s+|\\s+ft\\.?\\s+", RegexOption.IGNORE_CASE)
            ).map { it.trim() }.filter { it.isNotBlank() }.distinctBy { it.lowercase() }
            names.mapIndexed { index, name ->
                OnlineArtistRef(name, if (index == 0) current?.artistId else null)
            }
        }
    }
    var showArtistPicker by remember(uiState.currentSong?.id) { mutableStateOf(false) }

    val dynamicAccentColor = remember(accentColor, isDark) {
        val hsl = FloatArray(3)
        androidx.core.graphics.ColorUtils.colorToHSL(accentColor.toArgb(), hsl)
        if (hsl[1] < 0.08f) {
            // Monochromatic / Grayscale
            if (isDark) Color.White else Color(0xFF1C1B1F)
        } else {
            if (isDark) {
                accentColor
            } else {
                hsl[2] = hsl[2].coerceAtMost(0.45f)
                hsl[1] = hsl[1].coerceAtLeast(0.6f)
                Color(androidx.core.graphics.ColorUtils.HSLToColor(hsl))
            }
        }
    }
    val lyricsTextColor = when (lyricsColorStyle) {
        "White" -> Color.White
        "UI Matching" -> DsmusicTheme.colorScheme.onSurface
        else -> contentColor
    }
    val lyricsSecondaryColor = lyricsTextColor.copy(alpha = if (lyricsColorStyle == "White") 0.68f else 0.58f)
    val lyricsAccentColor = when (lyricsColorStyle) {
        "White" -> Color.White
        "UI Matching" -> DsmusicTheme.colorScheme.primary
        else -> dynamicAccentColor
    }

    val themePlayerBackground = DsmusicTheme.colorScheme.background
    val backgroundBrush = remember(
        uiState.playerBackgroundColor,
        isDark,
        playerBackgroundStyle,
        themePlayerBackground
    ) {
        val playerColor = Color(uiState.playerBackgroundColor)
        val finalColor = deriveArtworkSurfaceColor(
            sourceColor = playerColor,
            isDark = isDark,
            darkLightness = 0.155f,
            lightLightness = 0.835f,
            darkSaturationRange = 0.32f..0.54f,
            lightSaturationRange = 0.30f..0.48f
        )
        when (playerBackgroundStyle) {
            "Theme" -> androidx.compose.ui.graphics.SolidColor(themePlayerBackground)
            "Black" -> androidx.compose.ui.graphics.SolidColor(Color.Black)
            "Gradient" -> Brush.linearGradient(
                listOf(
                    finalColor,
                    finalColor.copy(alpha = 1f),
                    themePlayerBackground
                )
            )
            else -> androidx.compose.ui.graphics.SolidColor(finalColor)
        }
    }

    val playerProgressBackgroundColor = remember(
        uiState.playerBackgroundColor,
        isDark,
        playerBackgroundStyle,
        themePlayerBackground
    ) {
        val derivedArtworkSurface = deriveArtworkSurfaceColor(
            sourceColor = Color(uiState.playerBackgroundColor),
            isDark = isDark,
            darkLightness = 0.155f,
            lightLightness = 0.835f,
            darkSaturationRange = 0.32f..0.54f,
            lightSaturationRange = 0.30f..0.48f
        )
        when (playerBackgroundStyle) {
            "Theme" -> themePlayerBackground
            "Black" -> Color.Black
            "Gradient" -> androidx.compose.ui.graphics.lerp(
                derivedArtworkSurface,
                themePlayerBackground,
                0.5f
            )
            else -> derivedArtworkSurface
        }
    }

    val hasLyrics = !syncedLyrics.isNullOrEmpty() || !plainLyrics.isNullOrBlank()
    val currentSongId = uiState.currentSong?.id
    val audioService by viewModel.audioServiceState.collectAsStateWithLifecycle()
    val emptyVisualizerLevels = remember {
        MutableStateFlow(FloatArray(16) { 0f })
    }
    val visualizerLevelsFlow = audioService?.playbackVisualizerLevels ?: emptyVisualizerLevels
    val playbackVisualizerLevels by visualizerLevelsFlow.collectAsStateWithLifecycle()
    val isVideoMode by viewModel.isVideoMode.collectAsStateWithLifecycle()
    val videoPlaybackMetadata by viewModel.videoPlaybackMetadata.collectAsStateWithLifecycle()
    var showSongInfoDialog by remember { mutableStateOf(false) }
    var showFullScreenVisualizer by remember { mutableStateOf(false) }
    var selectedQueueSongForMenu by remember { mutableStateOf<SongItem?>(null) }
    var lastLyricsFetchSongId by remember { mutableStateOf<Long?>(null) }
    val context = LocalContext.current
    var visualizerCaptureRequested by remember(audioService) { mutableStateOf(false) }
    val visualizerPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted && visualizerCaptureRequested) {
            audioService?.startPlaybackVisualizer()
        }
    }
    val needsPlaybackVisualizer = (
        visualizerStyle != "Off" && isPlayerVisible && uiState.isPlaying
    ) || showFullScreenVisualizer

    LaunchedEffect(audioService, needsPlaybackVisualizer) {
        visualizerCaptureRequested = needsPlaybackVisualizer
        if (needsPlaybackVisualizer) {
            delay(250L)
            if (!visualizerCaptureRequested) return@LaunchedEffect
            if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
            ) {
                audioService?.startPlaybackVisualizer()
            } else {
                visualizerPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        } else {
            audioService?.stopPlaybackVisualizer()
        }
    }

    DisposableEffect(audioService) {
        onDispose { audioService?.stopPlaybackVisualizer() }
    }

    // The player may be hosted in a resizable split-screen/freeform window on tablets.
    // Re-evaluate this with the window constraints so the compact layout follows resizing.
    val isMultiWindowMode = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N &&
        (context as? Activity)?.isInMultiWindowMode == true
    
    // Sliding Queue Drawer State
    val isQueueExpanded by viewModel.isQueueExpanded.collectAsStateWithLifecycle()
    val isShuffleEnabled by viewModel.isShuffleEnabled.collectAsStateWithLifecycle()
    val repeatMode by viewModel.repeatMode.collectAsStateWithLifecycle()
    val currentSongIndex by viewModel.currentSongIndex.collectAsStateWithLifecycle()
    // Back dismisses the one shared queue first, then the fullscreen lyrics overlay.
    BackHandler(enabled = isLyricsModeEnabled || isQueueExpanded) {
        if (isQueueExpanded) {
            viewModel.setQueueExpanded(false)
        } else {
            onLyricsModeChanged(false)
        }
    }

    // Sleep Timer State & Controller Dialog state
    var showSleepTimerDialog by remember { mutableStateOf(false) }
    val sleepTimerMode by viewModel.sleepTimerMode.collectAsStateWithLifecycle()
    val sleepTimerEndTime by viewModel.sleepTimerEndTime.collectAsStateWithLifecycle()
    
    val lyricsBackgroundBrush = remember(uiState.playerBackgroundColor, isDark) {
        val playerColor = Color(uiState.playerBackgroundColor)
        val finalColor = deriveArtworkSurfaceColor(
            sourceColor = playerColor,
            isDark = isDark,
            darkLightness = 0.145f,
            lightLightness = 0.825f,
            darkSaturationRange = 0.32f..0.54f,
            lightSaturationRange = 0.30f..0.48f
        )
        androidx.compose.ui.graphics.SolidColor(finalColor)
    }

    if (showArtistPicker && artistOptions.size > 1) {
        Dialog(onDismissRequest = { showArtistPicker = false }) {
            DsmusicSurface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                color = DsmusicTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 8.dp
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "Choose an artist",
                        style = DsmusicTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = DsmusicTheme.colorScheme.onSurface
                    )
                    Text(
                        "Open the artist page directly.",
                        style = DsmusicTheme.typography.bodySmall,
                        color = DsmusicTheme.colorScheme.onSurfaceVariant
                    )
                    artistOptions.forEach { artist ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .clickable {
                                    showArtistPicker = false
                                    onArtistClick(artist.id, artist.name)
                                }
                                .padding(horizontal = 12.dp, vertical = 13.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                artist.name,
                                modifier = Modifier.weight(1f),
                                style = DsmusicTheme.typography.bodyLarge,
                                color = DsmusicTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Icon(
                                Icons.Rounded.ChevronRight,
                                contentDescription = null,
                                tint = DsmusicTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    DsmusicTextButton(
                        onClick = { showArtistPicker = false },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("Cancel")
                    }
                }
            }
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(backgroundBrush)
            .then(if (isQueueExpanded || isLyricsModeEnabled) Modifier else dragModifier) // Drag down anywhere to collapse
    ) {
        // Use the actual player window constraints, not the physical display metrics.
        // Split-screen/freeform windows can be much smaller than LocalConfiguration's device size.
        val screenWidth = maxWidth
        val screenHeight = maxHeight
        // The artwork preference scales only the floating cover layer. The reserved
        // artwork slot remains constant so title, slider and transport controls do not move.
        // Keep the reserved slot identical to the shared artwork layer in
        // PlayerBottomSheetCompose. A mismatch lets the title enter the cover's area.
        val albumArtScale = when (playerLayout) {
            "Cover" -> 0.98f
            "Minimal" -> 0.68f
            "Vinyl", "Cassette" -> 0.82f
            else -> 0.9f
        }
        val isLandscapeWindow = screenWidth > screenHeight
        val useWidePlayer = screenWidth >= 840.dp ||
            (screenWidth >= 600.dp && isLandscapeWindow)
        // A portrait tablet window can be much shorter or narrower than the full display.
        // Keep the standard phone/full-screen layout untouched; compact only resized windows.
        val isCompactPortraitMultiWindow = isMultiWindowMode &&
            !isLandscapeWindow &&
            (screenWidth < 720.dp || screenHeight < 900.dp)

        // Track the visible lyric line and action row in root coordinates. Older devices
        // and unusual display scales can make text metrics exceed the weighted preview
        // slot even when the nominal screen size says the standard layout should fit.
        var portraitLyricLineBounds by remember(screenWidth, screenHeight, currentSongId) {
            mutableStateOf<Rect?>(null)
        }
        var portraitActionRowBounds by remember(screenWidth, screenHeight, currentSongId) {
            mutableStateOf<Rect?>(null)
        }
        var compactPortraitLayout by remember(screenWidth, screenHeight, currentSongId) {
            mutableStateOf(false)
        }
        val collisionClearancePx = with(LocalDensity.current) { 8.dp.toPx() }

        LaunchedEffect(
            portraitLyricLineBounds,
            portraitActionRowBounds,
            isLandscapeWindow,
            isLyricsModeEnabled,
            showSyncedLyricsInPlayer,
            syncedLyrics
        ) {
            if (
                isLandscapeWindow ||
                isLyricsModeEnabled ||
                !showSyncedLyricsInPlayer ||
                syncedLyrics.isNullOrEmpty()
            ) {
                compactPortraitLayout = false
                portraitLyricLineBounds = null
            } else {
                val lyricBounds = portraitLyricLineBounds
                val actionBounds = portraitActionRowBounds
                if (lyricBounds != null && actionBounds != null) {
                    val horizontalOverlap =
                        lyricBounds.left < actionBounds.right &&
                            lyricBounds.right > actionBounds.left
                    val verticalCollision =
                        lyricBounds.bottom + collisionClearancePx > actionBounds.top &&
                            lyricBounds.top < actionBounds.bottom
                    if (horizontalOverlap && verticalCollision) {
                        // Latch the denser layout for this song/window size after either
                        // a height shortage or a lyric/action collision; do not toggle it off
                        // again when the updated geometry clears the original condition.
                        compactPortraitLayout = true
                    }
                }
            }
        }
        val compactPortraitLyrics = !isLandscapeWindow &&
            (isCompactPortraitMultiWindow || compactPortraitLayout)

        // When the queue opens on a tablet-sized landscape layout, reserve its left pane
        // instead of letting the queue cover the player controls.
        val animatedQueueDockInset by animateDpAsState(
            targetValue = if (
                useWidePlayer &&
                isLandscapeWindow &&
                isPlayerVisible &&
                isQueueExpanded
            ) screenWidth * 0.42f else 0.dp,
            animationSpec = spring(dampingRatio = 0.85f, stiffness = 300f),
            label = "QueueDockInset"
        )
        // A spring can undershoot its zero target. Compose padding rejects negative values,
        // so clamp the animated inset before passing it to Modifier.padding.
        val queueDockInset = animatedQueueDockInset.coerceAtLeast(0.dp)
        // Fetch lyrics when any visible lyrics surface needs them. Portrait's inline
        // synced-line preview must prefetch too; otherwise it stays empty until the user
        // manually opens the full-screen Lyrics overlay.
        val landscapeLyricsVisible = isPlayerVisible && useWidePlayer && isLandscapeWindow
        val portraitLyricsPreviewVisible = isPlayerVisible &&
            !isLandscapeWindow &&
            showSyncedLyricsInPlayer &&
            !isLyricsModeEnabled
        LaunchedEffect(
            isLyricsModeEnabled,
            landscapeLyricsVisible,
            portraitLyricsPreviewVisible,
            currentSongId,
            hasLyrics,
            isLyricsLoading
        ) {
            if (
                !isLyricsModeEnabled &&
                !landscapeLyricsVisible &&
                !portraitLyricsPreviewVisible
            ) return@LaunchedEffect
            if (currentSongId == null) {
                lastLyricsFetchSongId = null
                return@LaunchedEffect
            }
            if (!hasLyrics && !isLyricsLoading && lastLyricsFetchSongId != currentSongId) {
                lastLyricsFetchSongId = currentSongId
                onFetchLyrics()
            }
        }

        // Dense controls are used for landscape and short-height resized player windows.
        val compactLandscape = isLandscapeWindow || screenHeight < 760.dp ||
            isCompactPortraitMultiWindow
        // Move the title group down and reduce the gap before the action chips while
        // keeping their shared vertical anchor unchanged. Preserve landscape spacing.
        val titleToActionsSpacerWeight =
            if (isLandscapeWindow || compactPortraitLyrics) 2.25f else 1.5f
        val titleGroupTopSpacerWeight =
            if (isLandscapeWindow || compactPortraitLyrics) 0.75f else 1.5f
        // When the inline lyric line is disabled, move 60% of the blank
        // visualizer-to-controls spacer above the title/visualizer group. Keeping
        // the combined weight constant leaves the action row at its existing anchor.
        val reduceEmptyLyricsGap = !isLandscapeWindow && !showSyncedLyricsInPlayer
        val effectiveTitleToActionsSpacerWeight =
            if (reduceEmptyLyricsGap) titleToActionsSpacerWeight * 0.4f
            else titleToActionsSpacerWeight
        val effectiveTitleGroupTopSpacerWeight =
            if (reduceEmptyLyricsGap) {
                titleGroupTopSpacerWeight + titleToActionsSpacerWeight * 0.6f
            } else {
                titleGroupTopSpacerWeight
            }
        // Match the exact height factor used to position the shared artwork layer.
        // Multi-window mode may compact controls, but must not shrink the artwork slot.
        val portraitArtHeightFactor = when {
            screenHeight < 680.dp -> 0.30f
            screenHeight < 760.dp -> 0.34f
            else -> 0.40f
        }
        val landscapeArtHeightFactor = when {
            screenHeight < 520.dp -> 0.18f
            screenHeight < 640.dp -> 0.22f
            else -> 0.28f
        }
        val widePortraitArtHeightFactor =
            if (screenHeight < 760.dp) 0.34f else 0.50f
        val portraitArtHeightLimit = screenHeight * portraitArtHeightFactor
        val albumArtSlotSize = if (isLandscapeWindow) {
            minOf(screenHeight * landscapeArtHeightFactor, screenWidth * 0.30f) *
                (albumArtScale / 0.9f)
        } else if (useWidePlayer) {
            minOf(screenHeight * widePortraitArtHeightFactor, screenWidth * 0.42f) *
                (albumArtScale / 0.9f)
        } else {
            minOf(screenWidth * albumArtScale, portraitArtHeightLimit)
        }

        val density = androidx.compose.ui.platform.LocalDensity.current
        val statusBarTopDp = with(density) { androidx.compose.foundation.layout.WindowInsets.statusBars.getTop(this).toDp() }
        // Shared-artwork target: keep the portrait and landscape player content
        // slightly lower to avoid the top-heavy layout reported on compact phones.
        val minTopOffset = statusBarTopDp + when {
            isLandscapeWindow && useWidePlayer -> 84.dp
            isLandscapeWindow -> 60.dp
            else -> 76.dp
        }
        // Preserve the shared-artwork vertical anchor; only the transport spacing compacts
        // when a portrait window becomes short.
        val headerHeight = if (isLandscapeWindow) 44.dp else 68.dp

        // Calculate top offset to perfectly match yEndPx in PlayerBottomSheetCompose
        val topOffset = if (useWidePlayer) minTopOffset
            else ((screenHeight - albumArtSlotSize) / 2f - 220.dp).coerceAtLeast(minTopOffset)

        var lyricsButtonCenterInRoot by remember { mutableStateOf<Offset?>(null) }
        val lyricsRevealProgress by animateFloatAsState(
            targetValue = if (isLyricsModeEnabled) 1f else 0f,
            animationSpec = tween(durationMillis = 340, easing = FastOutSlowInEasing),
            label = "LyricsCircularReveal"
        )

        // Finish the existing reveal before composing populated lyrics. This prevents
        // the first text-layout / karaoke-geometry pass from competing with the transition.
        val lyricsContentReady = lyricsRevealProgress >= 1f

        val playerControlsContent: @Composable ColumnScope.() -> Unit = {
            // Header Row (Nav controls + collapse) - Symmetric centering
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(headerHeight)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onCollapse
                    ),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier.size(46.dp)
                        .clip(CircleShape)
                        .background(contentColor.copy(alpha = 0.08f))
                        .clickable(onClick = onCollapse),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_keyboard_arrow_down),
                        contentDescription = "Collapse Player",
                        tint = contentColor.copy(alpha = 0.8f),
                        modifier = Modifier.size(28.dp)
                    )
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = "NOW PLAYING",
                        style = DsmusicTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = contentSecondary
                    )
                    Text(
                        text = uiState.currentSong?.playbackContextName?.takeIf { it.isNotBlank() }
                            ?: uiState.currentSong?.albumName?.takeIf { it.isNotBlank() }
                            ?: "Random",
                        style = DsmusicTheme.typography.labelSmall,
                        color = contentSecondary.copy(alpha = 0.78f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.size(48.dp))
            }

            // Parent adds status-bar inset and 12dp vertical padding before this column.
            // Align the placeholder with the shared artwork layer's root-space Y position.
            Spacer(
                modifier = Modifier.height(
                    (topOffset - (statusBarTopDp + 12.dp + headerHeight)).coerceAtLeast(0.dp)
                )
            )

            // Album Art Container Placeholder (ArtworkPager is rendered at this absolute position)
            Box(
                // Fixed layout reservation: the cover itself is drawn in a separate layer
                // and may be resized without changing the dock's measured position.
                modifier = Modifier.size(albumArtSlotSize)
            )

            Spacer(modifier = Modifier.height(if (compactLandscape) 6.dp else 12.dp))
            // Reallocate one quarter of the title/visualizer-to-action gap above the
            // title group. The total weighted space stays constant, so action buttons
            // retain their exact vertical position while the group moves down.
            Spacer(
                modifier = Modifier.weight(
                    effectiveTitleGroupTopSpacerWeight,
                    fill = true
                )
            )

            // Keep title and artist centered on the player, independently of the trailing info control.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 48.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    val titleSize = when (playerTitleSize) {
                        "Small" -> 23.sp
                        "Large" -> 34.sp
                        else -> 29.sp
                    }
                    val titleWeight = when (playerTitleWeight) {
                        "Light" -> FontWeight.Light
                        "Regular" -> FontWeight.Normal
                        else -> FontWeight.Bold
                    }
                    Text(
                        text = if (isVideoMode) {
                            videoPlaybackMetadata?.title?.takeIf { it.isNotBlank() }
                                ?: uiState.currentSong?.title ?: "Unknown Title"
                        } else {
                            uiState.currentSong?.title ?: "Unknown Title"
                        },
                        style = DsmusicTheme.typography.headlineMediumEmphasized,
                        fontSize = titleSize,
                        fontWeight = titleWeight,
                        color = contentColor,
                        maxLines = 1,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(
                                if (playerTitleScroll && isPlayerVisible) {
                                    Modifier.basicMarqueeWithFadedEdges()
                                } else {
                                    Modifier
                                }
                            )
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (isVideoMode) {
                            videoPlaybackMetadata?.uploader?.takeIf { it.isNotBlank() }
                                ?: uiState.currentSong?.artist ?: "Unknown Artist"
                        } else {
                            uiState.currentSong?.artist ?: "Unknown Artist"
                        },
                        style = DsmusicTheme.typography.bodyMedium,
                        color = contentSecondary,
                        maxLines = 1,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(
                                if (playerTitleScroll && isPlayerVisible) {
                                    Modifier.basicMarqueeWithFadedEdges()
                                } else {
                                    Modifier
                                }
                            )
                            .clickable {
                                val song = uiState.currentSong
                                if (song != null) {
                                    when {
                                        artistOptions.size > 1 -> showArtistPicker = true
                                        artistOptions.size == 1 -> {
                                            val artist = artistOptions.first()
                                            onArtistClick(artist.id ?: song.artistId, artist.name)
                                        }
                                        else -> onArtistClick(song.artistId, song.artist)
                                    }
                                }
                            }
                    )
                }
                DsmusicIconButton(
                    onClick = { showSongInfoDialog = true },
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .size(44.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Info,
                        contentDescription = "Song information",
                        tint = contentSecondary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(if (compactLandscape) 8.dp else 16.dp))

            if (visualizerStyle != "Off") {
                AudioReactivePlayerVisualizer(
                    style = visualizerStyle,
                    levels = playbackVisualizerLevels,
                    color = when (visualizerColor) {
                        "White" -> Color.White
                        "Cover" -> Color(uiState.playerBackgroundColor)
                        else -> dynamicAccentColor
                    },
                    colorMode = visualizerColor
                )
                Spacer(modifier = Modifier.height(if (compactLandscape) 4.dp else 10.dp))
            }

            // Keep the action row at its existing vertical anchor. In portrait mode,
            // use this free gap for one active synced lyric line. Karaoke lines advance
            // only when the outgoing line's final timed word has completed.
            if (
                showSyncedLyricsInPlayer &&
                !isLandscapeWindow &&
                !isLyricsModeEnabled &&
                !syncedLyrics.isNullOrEmpty()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(titleToActionsSpacerWeight, fill = true)
                        // Keep the lyric inside its measured slot on every Android version.
                        // If its preferred height does not fit, the parent reallocates space
                        // before the preview reduces its font size.
                        .clipToBounds(),
                    contentAlignment = Alignment.Center
                ) {
                    PortraitSyncedLyricPreview(
                        lyrics = syncedLyrics,
                        currentSong = uiState.currentSong,
                        currentPositionProvider = currentPositionProvider,
                        isPlaying = uiState.isPlaying,
                        contentColor = lyricsTextColor,
                        lyricsColorStyle = lyricsColorStyle,
                        compact = compactPortraitLyrics,
                        onLineBoundsChanged = { bounds ->
                            if (portraitLyricLineBounds != bounds) {
                                portraitLyricLineBounds = bounds
                            }
                        },
                        onLineNeedsMoreSpace = {
                            if (!compactPortraitLyrics) {
                                compactPortraitLayout = true
                            }
                        }
                    )
                }
            } else {
                Spacer(
                    modifier = Modifier.weight(
                        effectiveTitleToActionsSpacerWeight,
                        fill = true
                    )
                )
            }
            // Keep a consistent clearance between the clipped lyric slot and controls.
            Spacer(modifier = Modifier.height(8.dp))

            // Reorderable action chips. The selected order is saved by Customize.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .onGloballyPositioned { coordinates ->
                        val position = coordinates.positionInRoot()
                        val bounds = Rect(
                            position.x,
                            position.y,
                            position.x + coordinates.size.width.toFloat(),
                            position.y + coordinates.size.height.toFloat()
                        )
                        if (portraitActionRowBounds != bounds) {
                            portraitActionRowBounds = bounds
                        }
                    }
                    .clip(if (playerButtonsPill) CircleShape else RoundedCornerShape(0.dp))
                    .background(
                        if (playerButtonsPill) contentColor.copy(alpha = if (isDark) 0.075f else 0.055f)
                        else Color.Transparent
                    )
                    // Let horizontalScroll own the viewport. The previous custom layout
                    // expanded and negatively offset the row, which could clip the last chip.
                    .horizontalScroll(rememberScrollState())
                    .padding(
                        horizontal = if (playerButtonsPill) 8.dp else 0.dp,
                        vertical = if (playerButtonsPill) 5.dp else 0.dp
                    ),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Spacer(modifier = Modifier.width(12.dp))

                val songForActions = uiState.currentSong
                val downloadedVideoIds by com.dsann.music.util.SongDownloader.downloadedVideoIds.collectAsStateWithLifecycle()
                LaunchedEffect(songForActions?.videoId, songForActions?.title, songForActions?.artist) {
                    songForActions?.let {
                        com.dsann.music.util.SongDownloader.refreshDownloadedStatus(context, it)
                    }
                }
                val realDownloaded = uiState.isCurrentSongDownloaded ||
                    (songForActions?.videoId?.let { it in downloadedVideoIds } == true)
                val realDownloadProgress = uiState.currentSongDownloadProgress
                val isDownloading = realDownloadProgress != null ||
                    com.dsann.music.util.SongDownloader.isDownloading(songForActions?.videoId)

                val orderedButtons = playerButtons
                // Video is a playback-mode control. Keep it visible while video is active,
                // even when it is outside the user-selected action order.
                val showVideoSwitch = isVideoMode || !songForActions?.videoId.isNullOrBlank()
                val playerButtonsWithVideo = if (
                    showVideoSwitch && "video" !in orderedButtons
                ) orderedButtons + "video" else orderedButtons
                val playerButtonsForDisplay = if (
                    fullscreenVisualizerEnabled && "visualizer" !in playerButtonsWithVideo
                ) playerButtonsWithVideo + "visualizer"
                else if (fullscreenVisualizerEnabled) playerButtonsWithVideo
                else playerButtonsWithVideo.filterNot { it == "visualizer" }
                playerButtonsForDisplay.forEach { action ->
                    when (action) {
                        "lyrics" -> PillChip(
                            style = playerButtonStyle,
                            icon = painterResource(id = R.drawable.ic_lyrics),
                            label = "Lyrics",
                            isSelected = isLyricsModeEnabled ||
                                (useWidePlayer && isLandscapeWindow && !isQueueExpanded),
                            onClick = {
                                viewModel.setQueueExpanded(false)
                                onLyricsModeChanged(true)
                            },
                            contentColor = contentColor,
                            accentColor = dynamicAccentColor,
                            isDark = isDark,
                            modifier = Modifier.onGloballyPositioned { coordinates ->
                                val position = coordinates.positionInRoot()
                                lyricsButtonCenterInRoot = Offset(
                                    x = position.x + coordinates.size.width / 2f,
                                    y = position.y + coordinates.size.height / 2f
                                )
                            }
                        )
                        "video" -> if (showVideoSwitch) {
                            PillChip(
                            style = playerButtonStyle,
                                icon = painterResource(id = R.drawable.ic_youtube),
                                label = if (isVideoMode) "Audio" else "Video",
                                isSelected = isVideoMode,
                                onClick = { audioService?.setVideoMode(!isVideoMode) },
                                contentColor = contentColor,
                                accentColor = dynamicAccentColor,
                                isDark = isDark
                            )
                        }
                        "queue" -> PillChip(
                            style = playerButtonStyle,
                            icon = painterResource(id = R.drawable.ic_keyboard_arrow_down),
                            label = "Up next",
                            isSelected = isQueueExpanded,
                            onClick = {
                                if (isQueueExpanded) {
                                    viewModel.setQueueExpanded(false)
                                } else {
                                    onLyricsModeChanged(false)
                                    viewModel.setQueueExpanded(true)
                                }
                            },
                            contentColor = contentColor,
                            accentColor = dynamicAccentColor,
                            isDark = isDark
                        )
                        "share" -> PillChip(
                            style = playerButtonStyle,
                            icon = painterResource(id = R.drawable.ic_share),
                            label = "Share",
                            onClick = {
                                val shareIntent = Intent().apply {
                                    this.action = Intent.ACTION_SEND
                                    putExtra(Intent.EXTRA_TEXT, "Listening on Quantune. Check out: https://music.youtube.com/watch?v=${songForActions?.videoId.orEmpty()}")
                                    type = "text/plain"
                                }
                                context.startActivity(Intent.createChooser(shareIntent, "Share Track"))
                            },
                            contentColor = contentColor,
                            accentColor = dynamicAccentColor,
                            isDark = isDark
                        )
                        "download" -> {
                            val downloadLabel = when {
                                isDownloading -> "Cancel download"
                                realDownloaded -> "Downloaded"
                                else -> "Download"
                            }
                            val downloadIcon: Any = when {
                                isDownloading -> painterResource(id = R.drawable.ic_close)
                                realDownloaded -> painterResource(id = R.drawable.ic_downloaded)
                                else -> painterResource(id = R.drawable.ic_download)
                            }
                            PillChip(
                            style = playerButtonStyle,
                                icon = downloadIcon,
                                label = downloadLabel,
                                isSelected = realDownloaded || isDownloading,
                                progress = if (isDownloading) (realDownloadProgress ?: 0) / 100f else null,
                                onClick = {
                                    val currentSong = songForActions
                                    if (currentSong != null) {
                                        when {
                                            isDownloading ->
                                                com.dsann.music.util.SongDownloader.cancelDownload(context, currentSong)
                                            !realDownloaded ->
                                                com.dsann.music.util.SongDownloader.downloadSong(context, currentSong)
                                            else ->
                                                com.dsann.music.ui.SnackbarController.showMessage("Already downloaded")
                                        }
                                    }
                                },
                                contentColor = contentColor,
                                accentColor = dynamicAccentColor,
                                isDark = isDark
                            )
                        }
                        "like" -> PillChip(
                            style = playerButtonStyle,
                            icon = if (uiState.isCurrentSongFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                            label = "Like",
                            isSelected = uiState.isCurrentSongFavorite,
                            onClick = onFavoriteClick,
                            contentColor = contentColor,
                            accentColor = dynamicAccentColor,
                            isDark = isDark
                        )
                        "dislike" -> PillChip(
                            style = playerButtonStyle,
                            icon = Icons.Rounded.ThumbDown,
                            label = "Not for me",
                            isSelected = uiState.isCurrentSongDisliked,
                            onClick = onDislikeClick,
                            contentColor = contentColor,
                            accentColor = dynamicAccentColor,
                            isDark = isDark
                        )
                        "sleep" -> PillChip(
                            style = playerButtonStyle,
                            icon = Icons.Rounded.NightsStay,
                            label = "Sleep timer",
                            isSelected = sleepTimerEndTime != null,
                            onClick = { showSleepTimerDialog = true },
                            contentColor = contentColor,
                            accentColor = dynamicAccentColor,
                            isDark = isDark
                        )
                        "shuffle" -> PillChip(
                            style = playerButtonStyle,
                            icon = Icons.Rounded.Shuffle,
                            label = "Shuffle",
                            isSelected = isShuffleEnabled,
                            onClick = { viewModel.toggleShuffle() },
                            contentColor = contentColor,
                            accentColor = dynamicAccentColor,
                            isDark = isDark
                        )
                        "listen_together" -> PillChip(
                            style = playerButtonStyle,
                            icon = Icons.Rounded.Groups,
                            label = "Listen Together",
                            onClick = {
                                onCollapse()
                                onNavigateToListenTogether()
                            },
                            contentColor = contentColor,
                            accentColor = dynamicAccentColor,
                            isDark = isDark
                        )
                        "visualizer" -> PillChip(
                            style = playerButtonStyle,
                            icon = Icons.Rounded.GraphicEq,
                            label = "Visualizer",
                            onClick = { showFullScreenVisualizer = true },
                            contentColor = contentColor,
                            accentColor = dynamicAccentColor,
                            isDark = isDark
                        )
                        "repeat" -> PillChip(
                            style = playerButtonStyle,
                            icon = when (repeatMode) {
                                PlayerSharedViewModel.REPEAT_ONCE ->
                                    painterResource(id = R.drawable.ic_repeat_one)
                                PlayerSharedViewModel.REPEAT_INFINITY ->
                                    painterResource(id = R.drawable.ic_repeat_infinity)
                                else -> Icons.Rounded.Repeat
                            },
                            label = when (repeatMode) {
                                PlayerSharedViewModel.REPEAT_ONCE -> "Repeat once"
                                PlayerSharedViewModel.REPEAT_INFINITY -> "Repeat infinity"
                                else -> "No repeat"
                            },
                            isSelected = repeatMode != PlayerSharedViewModel.REPEAT_OFF,
                            onClick = { viewModel.toggleLoopMode() },
                            contentColor = contentColor,
                            accentColor = dynamicAccentColor,
                            isDark = isDark
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))
            }

            Spacer(modifier = Modifier.height(if (compactLandscape) 8.dp else 24.dp))

            // Premium Wavy Seek Bar (Isolated)
            WavySliderWithLabels(
                currentPositionProvider = currentPositionProvider,
                currentPositionFlow = currentPositionFlow,
                frameTimeNanos = playerFrameTimeNanos,
                isPlayerVisible = isPlayerVisible,
                duration = uiState.duration,
                isPlaying = uiState.isPlaying,
                onSeekTo = onSeekTo,
                dynamicAccentColor = dynamicAccentColor,
                contentColor = contentColor,
                contentSecondary = contentSecondary,
                isDark = isDark,
                scrubberStyle = scrubberStyle,
                scrubberColor = scrubberColor,
                playerTimes = playerTimes,
                scrubberThickness = scrubberThickness,
                scrubberThumbStyle = scrubberThumbStyle,
                coverDerivedColor = Color(uiState.playerBackgroundColor),
                backgroundSurfaceColor = playerProgressBackgroundColor
            )

            Spacer(modifier = Modifier.height(if (compactLandscape) 6.dp else 16.dp))

            val noTransportBackground = playerButtonStyle == "None"
            val roundTransport = playerButtonStyle == "Round"
            val transportSize = if (compactLandscape) 60.dp else 76.dp
            val transportShape = when (playerButtonStyle) {
                "Round", "Pill" -> CircleShape
                "Square" -> RoundedCornerShape(5.dp)
                else -> RoundedCornerShape(0.dp)
            }
            val transportSizeModifier = if (roundTransport) {
                Modifier.size(transportSize)
            } else {
                Modifier.height(transportSize)
            }
            val sideControlColor = if (noTransportBackground) Color.Transparent else playerButtonColor
            val sideIconColor = playerButtonContentColor
            val centerControlColor = if (noTransportBackground) Color.Transparent else playerButtonColor
            val centerIconColor = playerButtonContentColor

            // D.S Launcher-style transport controls: flat rounded touch targets with
            // independent sizing and no Material ButtonGroup indicator/elevation layers.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = if (roundTransport) {
                    Arrangement.spacedBy(24.dp, Alignment.CenterHorizontally)
                } else {
                    Arrangement.spacedBy(12.dp)
                },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .then(if (roundTransport) transportSizeModifier else Modifier.weight(1f).then(transportSizeModifier))
                        .clip(transportShape)
                        .background(sideControlColor)
                        .clickable(onClick = onPreviousClick),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_skip_previous),
                        contentDescription = "Previous Song",
                        modifier = Modifier.size(34.dp),
                        tint = sideIconColor
                    )
                }

                Box(
                    modifier = Modifier
                        .then(if (roundTransport) transportSizeModifier else Modifier.weight(1.2f).then(transportSizeModifier))
                        .clip(transportShape)
                        .background(centerControlColor)
                        .clickable(enabled = !uiState.isLoading) { onPlayPauseClick() },
                    contentAlignment = Alignment.Center
                ) {
                    if (uiState.isLoading) {
                        com.dsann.music.ui.theme.LoadingIndicator(
                            modifier = Modifier.size(30.dp),
                            color = centerIconColor
                        )
                    } else {
                        Icon(
                            painter = painterResource(
                                id = if (uiState.isPlaying) R.drawable.ic_pause else R.drawable.ic_play
                            ),
                            contentDescription = if (uiState.isPlaying) "Pause" else "Play",
                            modifier = Modifier.size(40.dp),
                            tint = centerIconColor
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .then(if (roundTransport) transportSizeModifier else Modifier.weight(1f).then(transportSizeModifier))
                        .clip(transportShape)
                        .background(sideControlColor)
                        .clickable(onClick = onNextClick),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_skip_next),
                        contentDescription = "Next Song",
                        modifier = Modifier.size(34.dp),
                        tint = sideIconColor
                    )
                }
            }

            // Keep only one quarter of the former flexible gap between the controls
            // and the queue opener. The 3f spacer above the action row preserves the
            // original total weight above the opener, so its position does not move.
            Spacer(modifier = Modifier.weight(1f, fill = true))
            Spacer(modifier = Modifier.height(if (compactLandscape) 4.dp else 8.dp))

            // Swipe Up / Click Chevron Up Indicator to expand Queue
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (compactLandscape) 36.dp else 48.dp)
                    .pointerInput(Unit) {
                        detectVerticalDragGestures { _, dragAmount ->
                            if (dragAmount < -10f && !isQueueExpanded && !isLyricsModeEnabled) {
                                viewModel.setQueueExpanded(true)
                            }
                        }
                    }
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        viewModel.setQueueExpanded(!isQueueExpanded)
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_keyboard_arrow_down),
                    contentDescription = "Open Queue",
                    tint = contentColor.copy(alpha = 0.5f),
                    modifier = Modifier
                        .size(if (compactLandscape) 27.dp else 32.dp)
                        .graphicsLayer { rotationZ = 180f }
                )
            }
            Spacer(modifier = Modifier.weight(1f, fill = true))
        }

        if (useWidePlayer) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp)
                    .padding(top = if (compactLandscape) 8.dp else 12.dp, bottom = if (compactLandscape) 0.dp else 12.dp)
                    .padding(start = queueDockInset),
                horizontalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                Column(
                    modifier = Modifier
                        .weight(0.54f)
                        .fillMaxHeight()
                        .clipToBounds(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    content = playerControlsContent
                )
                if (isPlayerVisible) {
                    LandscapePlayerDetailsPane(
                        modifier = Modifier.weight(0.46f).fillMaxHeight(),
                        onOpenFullScreenLyrics = {
                            viewModel.setQueueExpanded(false)
                            onLyricsModeChanged(true)
                        },
                        syncedLyrics = syncedLyrics,
                        plainLyrics = plainLyrics,
                        isLyricsLoading = isLyricsLoading,
                        lyricsError = lyricsError,
                        currentSong = uiState.currentSong,
                        currentPositionProvider = currentPositionProvider,
                        playbackSpeedProvider = playbackSpeedProvider,
                        isPlaying = uiState.isPlaying,
                        contentColor = lyricsTextColor,
                        secondaryColor = lyricsSecondaryColor,
                        accentColor = lyricsAccentColor,
                        sourcePickerBackgroundBrush = backgroundBrush,
                        lyricsColorStyle = lyricsColorStyle,
                        selectedProvider = selectedProvider,
                        providerResults = providerResults,
                        onProviderSelected = onProviderSelected,
                        onSeekTo = onSeekTo,
                        onFetchLyrics = onFetchLyrics,
                        onRetryLyrics = onRetryLyrics
                    )
                } else {
                    Spacer(Modifier.weight(0.46f).fillMaxHeight())
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp)
                    .padding(top = if (compactLandscape) 8.dp else 12.dp, bottom = if (compactLandscape) 0.dp else 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                content = playerControlsContent
            )
        }
        if (lyricsRevealProgress > 0f) {
            FullScreenLyricsOverlay(
                currentSong = uiState.currentSong,
                syncedLyrics = syncedLyrics,
                plainLyrics = plainLyrics,
                isLoading = isLyricsLoading,
                isPlaying = uiState.isPlaying,
                lyricsError = lyricsError,
                currentPositionProvider = currentPositionProvider,
                playbackSpeedProvider = playbackSpeedProvider,
                contentReady = lyricsContentReady,
                backgroundBrush = lyricsBackgroundBrush,
                revealProgressProvider = { lyricsRevealProgress },
                revealCenterProvider = { lyricsButtonCenterInRoot },
                contentColor = lyricsTextColor,
                contentSecondary = lyricsSecondaryColor,
                dynamicAccentColor = lyricsAccentColor,
                lyricsColorStyle = lyricsColorStyle,
                onRetryLyrics = onRetryLyrics,
                onFetchLyrics = onFetchLyrics,
                onSeekTo = onSeekTo,
                providerResults = providerResults,
                selectedProvider = selectedProvider,
                onProviderSelected = onProviderSelected,
                onDismissLyrics = { onLyricsModeChanged(false) },
                modifier = Modifier.fillMaxSize()
            )
        }

        // --- CUSTOM EMBEDDED SLIDING PLAY QUEUE ---
        SlidingQueueDrawer(
            // Do not reveal or shift the queue until the player frame has fully settled.
            isQueueExpanded = isQueueExpanded && isPlayerVisible,
            onQueueExpandedChange = { viewModel.setQueueExpanded(it) },
            songList = songList,
            currentSongIndex = currentSongIndex,
            isShuffleEnabled = isShuffleEnabled,
            repeatMode = repeatMode,
            sleepTimerMode = sleepTimerMode,
            onReorderQueue = { from, to ->
                viewModel.reorderQueue(from, to, syncPlaybackService = false)
            },
            onReorderFinished = { viewModel.syncQueueWithPlaybackService() },
            onPlaySongAtIndex = { index -> viewModel.playSongAtIndex(index) },
            onToggleShuffle = { viewModel.toggleShuffle() },
            onToggleLoopMode = { viewModel.toggleLoopMode() },
            onShowSleepTimerDialog = { showSleepTimerDialog = true },
            playerBackgroundColor = uiState.playerBackgroundColor,
            dynamicAccentColor = dynamicAccentColor,
            isDark = isDark,
            onSongLongClick = { song -> selectedQueueSongForMenu = song },
            onRemoveFromQueue = { song -> viewModel.removeFromQueue(song.id) },
            playerSharedViewModel = viewModel
        )

        selectedQueueSongForMenu?.let { queueSong ->
            QueueSongActionsBottomSheet(
                song = queueSong,
                playerSharedViewModel = viewModel,
                onDismissRequest = { selectedQueueSongForMenu = null }
            )
        }

        // --- Standalone Sleep Timer Bottom Sheet ---
        if (showSleepTimerDialog) {
            SleepTimerBottomSheet(
                onDismissRequest = { showSleepTimerDialog = false },
                sleepTimerEndTime = sleepTimerEndTime,
                sleepTimerMode = sleepTimerMode,
                onStartTimer = { mins ->
                    viewModel.startCustomSleepTimer(mins)
                },
                onCancelTimer = {
                    viewModel.cancelSleepTimer()
                },
                onSetEndOfSong = { enable ->
                    if (enable) viewModel.setSleepTimerMode(PlayerSharedViewModel.SleepTimerMode.END_OF_SONG)
                    else viewModel.cancelSleepTimer()
                }
            )
        }

        if (showSongInfoDialog) {
            val song = uiState.currentSong
            if (song != null) {
                val songFileMetadata by produceState(
                    initialValue = SongFileMetadata(isRemote = SongFileMetadataReader.isRemoteSong(song)),
                    key1 = song.id,
                    key2 = song.path,
                    key3 = listOf(song.data, song.contentUri.toString(), song.isCachedTrack)
                ) {
                    value = withContext(Dispatchers.IO) {
                        SongFileMetadataReader.read(context.applicationContext, song)
                    }
                }
                val streamFormat by produceState(
                    initialValue = audioService?.getCurrentAudioFormatDetails(song.id.toString()),
                    key1 = audioService,
                    key2 = song.id
                ) {
                    // Track formats and media duration may arrive shortly after opening the dialog.
                    repeat(20) {
                        val latest = audioService?.getCurrentAudioFormatDetails(song.id.toString())
                        if (latest != null) value = latest
                        if (latest?.bitrateBps != null && latest.durationMs != null) return@produceState
                        delay(250L)
                    }
                }
                val onlineStreamMetadata by produceState<OnlineStreamMetadata?>(
                    initialValue = null,
                    key1 = song.videoId,
                    key2 = isVideoMode,
                    key3 = listOf(song.isCachedTrack, songFileMetadata.isRemote)
                ) {
                    value = if (
                        !isVideoMode &&
                        !song.videoId.isNullOrBlank() &&
                        (!song.isCachedTrack || songFileMetadata.isRemote) &&
                        !song.isDownloadedOffline
                    ) {
                        withContext(Dispatchers.IO) {
                            OnlineStreamMetadataReader.read(song.videoId!!)
                        }
                    } else null
                }
                val streamBitrateBps = if (isVideoMode) null else {
                    streamFormat?.bitrateBps?.takeIf { it > 0 } ?: onlineStreamMetadata?.bitrateBps
                }
                val durationMs = when {
                    isVideoMode && (videoPlaybackMetadata?.durationMs ?: 0L) > 0L ->
                        videoPlaybackMetadata?.durationMs ?: 0L
                    song.duration > 0L -> song.duration
                    (streamFormat?.durationMs ?: 0L) > 0L -> streamFormat?.durationMs ?: 0L
                    (onlineStreamMetadata?.durationMs ?: 0L) > 0L -> onlineStreamMetadata?.durationMs ?: 0L
                    (songFileMetadata.durationMs ?: 0L) > 0L -> songFileMetadata.durationMs ?: 0L
                    else -> 0L
                }
                val estimatedStreamSizeBytes = if (
                    songFileMetadata.isRemote && streamBitrateBps != null && durationMs > 0L
                ) {
                    (streamBitrateBps.toDouble() * durationMs.toDouble() / 8000.0)
                        .takeIf { it > 0.0 && it < Long.MAX_VALUE.toDouble() }
                        ?.toLong()
                } else null
                val reportedOnlineStreamBytes = onlineStreamMetadata?.contentLengthBytes
                val reportedVideoStreamBytes = videoPlaybackMetadata?.contentLengthBytes
                    ?.takeIf { isVideoMode && it > 0L }
                val estimatedVideoStreamBytes = videoPlaybackMetadata?.bitrateBps
                    ?.takeIf { isVideoMode && it > 0L && durationMs > 0L }
                    ?.let { bitrate ->
                        (bitrate.toDouble() * durationMs.toDouble() / 8000.0)
                            .takeIf { it > 0.0 && it.isFinite() && it < Long.MAX_VALUE.toDouble() }
                            ?.toLong()
                    }
                val totalSeconds = durationMs.coerceAtLeast(0L) / 1000L
                val durationText = if (durationMs > 0L) {
                    if (totalSeconds >= 3600L) {
                        java.lang.String.format(java.util.Locale.getDefault(), "%d:%02d:%02d",
                            totalSeconds / 3600L, (totalSeconds % 3600L) / 60L, totalSeconds % 60L)
                    } else {
                        java.lang.String.format(java.util.Locale.getDefault(), "%d:%02d",
                            totalSeconds / 60L, totalSeconds % 60L)
                    }
                } else "Duration unavailable until the stream finishes preparing"
                val fileSizeBytes = songFileMetadata.sizeBytes
                val fileBitrateBps = songFileMetadata.bitrateBps
                val estimatedFileBitrateBps = songFileMetadata.estimatedBitrateBps
                val sizeText = if (isVideoMode) {
                    when {
                        reportedVideoStreamBytes != null ->
                            android.text.format.Formatter.formatFileSize(context, reportedVideoStreamBytes)
                        estimatedVideoStreamBytes != null ->
                            "About " + android.text.format.Formatter.formatFileSize(context, estimatedVideoStreamBytes) +
                                " (estimated from the selected stream bitrate and duration)"
                        else -> "Video stream size is not exposed by the selected source"
                    }
                } else when {
                    fileSizeBytes != null ->
                        android.text.format.Formatter.formatFileSize(context, fileSizeBytes)
                    reportedOnlineStreamBytes != null ->
                        android.text.format.Formatter.formatFileSize(context, reportedOnlineStreamBytes) + " (reported audio stream length)"
                    estimatedStreamSizeBytes != null ->
                        "About " + android.text.format.Formatter.formatFileSize(context, estimatedStreamSizeBytes) +
                            " (estimated from stream bitrate and duration)"
                    songFileMetadata.isRemote ->
                        "Streaming source; full-file size varies with the selected audio stream"
                    else -> "File provider does not expose the byte length"
                }
                val bitrateText = if (isVideoMode) {
                    videoPlaybackMetadata?.bitrateBps?.takeIf { it > 0L }?.let {
                        "${(it + 500L) / 1000L} kb/s (selected video stream)"
                    } ?: "Video bitrate is not exposed by the selected stream metadata"
                } else when {
                    fileBitrateBps != null ->
                        "${(fileBitrateBps + 500L) / 1000L} kb/s (file metadata)"
                    streamBitrateBps != null ->
                        "${(streamBitrateBps + 500) / 1000} kb/s (active stream format)"
                    estimatedFileBitrateBps != null ->
                        "${(estimatedFileBitrateBps + 500L) / 1000L} kb/s (estimated from file size and duration)"
                    songFileMetadata.isRemote -> "Stream bitrate is not exposed by the current audio format"
                    else -> "Bitrate metadata is absent; no estimate can be calculated"
                }
                val formatText = if (isVideoMode) {
                    videoPlaybackMetadata?.mimeType?.takeIf { it.isNotBlank() }
                        ?: "Video format not exposed by selected stream"
                } else {
                    songFileMetadata.mimeType
                        ?: streamFormat?.mimeType
                        ?: onlineStreamMetadata?.mimeType
                        ?: songFileMetadata.fileName?.substringAfterLast('.', "")
                            ?.takeIf { it.isNotBlank() }
                            ?.uppercase(java.util.Locale.ROOT)
                            ?.plus(" (file extension)")
                        ?: if (songFileMetadata.isRemote) "Streaming audio (codec not identified yet)"
                           else "Audio format not exposed by file provider"
                }
                val sourceText = when {
                    song.isDownloadedOffline -> "Downloaded audio file (offline)"
                    song.isCachedTrack && !songFileMetadata.isRemote -> "Quantune cache (cached audio stream)"
                    !song.videoId.isNullOrBlank() && songFileMetadata.isRemote -> "YouTube Music stream"
                    songFileMetadata.isRemote -> "Remote audio stream"
                    else -> "Device audio file"
                }
                val displayTitle = (
                    if (isVideoMode) videoPlaybackMetadata?.title?.takeIf { it.isNotBlank() }
                    else null
                ) ?: song.title.takeIf { it.isNotBlank() && !it.equals("Unknown Title", true) }
                    ?: onlineStreamMetadata?.title
                    ?: "Title not supplied by source"
                val displayArtist = (
                    if (isVideoMode) videoPlaybackMetadata?.uploader?.takeIf { it.isNotBlank() }
                    else null
                ) ?: song.artist.takeIf { it.isNotBlank() && !it.equals("Unknown Artist", true) }
                    ?: onlineStreamMetadata?.artist
                    ?: "Artist not supplied by source"
                val details = mutableListOf(
                    "Title" to displayTitle,
                    (if (isVideoMode) "Uploader" else "Artist") to displayArtist,
                    "Album" to if (isVideoMode) {
                        "Not supplied by the video source"
                    } else {
                        song.albumName?.takeIf { it.isNotBlank() && !it.equals("<unknown>", true) }
                            ?: streamFormat?.albumTitle
                            ?: songFileMetadata.album
                            ?: if (song.albumId > 0L) "Album ID ${song.albumId} (title unavailable)" else "Album title unavailable in source metadata"
                    },
                    "Duration" to durationText,
                    "Source" to if (isVideoMode) "YouTube video stream" else sourceText,
                    (if (isVideoMode) "Video size" else "Song size") to sizeText,
                    (if (isVideoMode) "Video bitrate" else "Bitrate") to bitrateText,
                    (if (isVideoMode) "Video format" else "Format") to formatText
                )
                if (isVideoMode) {
                    videoPlaybackMetadata?.resolution?.takeIf { it.isNotBlank() }?.let {
                        details.add("Resolution" to it)
                    }
                    videoPlaybackMetadata?.viewCount?.takeIf { it > 0L }?.let {
                        details.add("Views" to java.text.NumberFormat.getIntegerInstance()
                            .format(it) + " views")
                    }
                    videoPlaybackMetadata?.likeCount?.takeIf { it > 0L }?.let {
                        details.add("Likes" to java.text.NumberFormat.getIntegerInstance().format(it))
                    }
                    videoPlaybackMetadata?.uploadDate?.takeIf { it.isNotBlank() }?.let {
                        details.add("Uploaded" to it)
                    }
                    videoPlaybackMetadata?.description?.takeIf { it.isNotBlank() }?.let {
                        details.add("Description" to it)
                    }
                    videoPlaybackMetadata?.uploaderUrl?.takeIf { it.isNotBlank() }?.let {
                        details.add("Uploader URL" to it)
                    }
                    song.videoId?.takeIf { it.isNotBlank() }?.let {
                        details.add("Video URL" to "https://www.youtube.com/watch?v=$it")
                    }
                } else {
                    onlineStreamMetadata?.audioQuality?.let { details.add("Source audio quality" to it) }
                    streamFormat?.sampleRateHz?.takeIf { it > 0 }?.let {
                        details.add("Sample rate" to "${it} Hz")
                    }
                }
                details.add((if (isVideoMode) "Queue track ID" else "Song ID") to song.id.toString())
                if (!isVideoMode) {
                    if (song.albumId > 0L) details.add("Album ID" to song.albumId.toString())
                    songFileMetadata.fileName?.let { details.add("File name" to it) }
                    songFileMetadata.filePath?.let { details.add("File path" to it) }
                    if (!songFileMetadata.isRemote && song.dateAdded > 0L) {
                        details.add("Added to device library" to java.text.DateFormat.getDateTimeInstance()
                            .format(java.util.Date(song.dateAdded * 1000L)))
                    }
                }
                if (!song.videoId.isNullOrBlank()) details.add("Video ID" to song.videoId!!)
                if (!isVideoMode) {
                    if (!song.artistId.isNullOrBlank()) details.add("Artist ID" to song.artistId!!)
                    song.lufs?.let { details.add("LUFS" to it.toString()) }
                        ?: details.add("Loudness" to "Not analyzed")
                }
                com.dsann.music.ui.theme.AlertDialog(
                    onDismissRequest = { showSongInfoDialog = false },
                    title = { Text(if (isVideoMode) "Video information" else "Song information", style = DsmusicTheme.typography.titleLarge, fontWeight = FontWeight.Bold) },
                    text = {
                        Column(
                            modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp)
                                .verticalScroll(androidx.compose.foundation.rememberScrollState()),
                            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp)
                        ) {
                            details.forEach { (label, value) ->
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                                    Text(label, modifier = Modifier.width(100.dp),
                                        style = DsmusicTheme.typography.labelMedium,
                                        color = DsmusicTheme.colorScheme.onSurfaceVariant)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(value, modifier = Modifier.weight(1f),
                                        style = DsmusicTheme.typography.bodySmall,
                                        color = DsmusicTheme.colorScheme.onSurface)
                                }
                            }
                        }
                    },
                    confirmButton = {
                        DsmusicTextButton(onClick = { showSongInfoDialog = false }) { Text("Close") }
                    }
                )
            }
        }
    }

    if (showFullScreenVisualizer) {
        FullscreenAudioVisualizerDialog(
            audioService = audioService,
            lyricsAccentColor = lyricsAccentColor,
            lyricsTextColor = lyricsTextColor,
            lyricsColorStyle = lyricsColorStyle,
            onDismissRequest = { showFullScreenVisualizer = false }
        )
    }
}
