package com.dsann.music.ui.player

import com.dsann.music.CrashDiagnostics

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Translate
import com.dsann.music.ui.theme.LoadingIndicator
import com.dsann.music.ui.theme.Icon
import com.dsann.music.ui.theme.IconButton
import com.dsann.music.ui.theme.ListItem
import com.dsann.music.ui.theme.ListItemDefaults
import com.dsann.music.ui.theme.DsmusicTheme
import com.dsann.music.ui.theme.GoogleSansFlexNonRounded
import com.dsann.music.ui.theme.dsmusicHomeStudioGradientBrush
import com.dsann.music.ui.theme.ModalBottomSheet
import com.dsann.music.ui.theme.Text
import com.dsann.music.ui.theme.TextButton
import com.dsann.music.ui.theme.rememberModalBottomSheetState
import com.dsann.music.ui.theme.rememberDsmusicModalSheetGeometry
import com.dsann.music.ui.theme.observeKey
import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.ui.platform.LocalContext
import com.dsann.music.util.NetworkFailureClassifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import com.dsann.music.R
import com.dsann.music.data.lyrics.LyricLine
import com.dsann.music.data.lyrics.LyricsResult
import com.dsann.music.model.SongItem
import com.dsann.music.data.features.AiLyricsTranslationService
import com.dsann.music.data.features.AiLyricsTranslationSettings
import com.dsann.music.data.features.AiLyricsTranslationSettingsStore
import com.dsann.music.data.features.AiLyricsTranslationSession
import com.dsann.music.data.features.AiLyricsTranslationCatalog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.hypot
import kotlin.math.max
import kotlinx.coroutines.delay
import androidx.lifecycle.compose.collectAsStateWithLifecycle



@Composable
internal fun LyricsProviderPickerSheet(
    providerResults: Map<String, LyricsResult>,
    selectedProvider: String?,
    onProviderSelected: (String) -> Unit,
    onRefindClick: () -> Unit,
    accentColor: Color,
    contentColor: Color,
    backgroundBrush: Brush,
    onDismissRequest: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation ==
        android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val modalGeometry = rememberDsmusicModalSheetGeometry()
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        sheetMaxWidth = if (isLandscape) modalGeometry.width else Dp.Unspecified,
        centeredLandscape = true,
        centerOffsetX = modalGeometry.centerOffsetX,
        containerColor = Color.Transparent,
        dragHandle = null
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .background(backgroundBrush)
                .navigationBarsPadding()
                .padding(top = 16.dp)
        ) {
            Column {
                Box(
                    modifier = Modifier
                        .width(32.dp)
                        .height(4.dp)
                        .clip(CircleShape)
                        .background(contentColor.copy(alpha = 0.4f))
                        .align(Alignment.CenterHorizontally)
                )
                Spacer(modifier = Modifier.height(8.dp))
                ProvidersSelector(
                    providerResults = providerResults,
                    selectedProvider = selectedProvider,
                    onProviderSelected = onProviderSelected,
                    onRefindClick = onRefindClick,
                    accentColor = accentColor,
                    contentColor = contentColor,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }
    }
}


private data class LyricsAppearanceSettings(
    val alignment: String = "Original",
    val textSize: String = "Standard",
    val fontFamily: String = "Rounded"
)

/**
 * Read lyrics appearance preferences away from the UI thread. SharedPreferences
 * getters can wait for the preferences XML to finish loading, so they must not
 * be called from the first composition of the fullscreen lyrics dialog.
 */
@Composable
private fun rememberLyricsAppearanceSettings(
    context: android.content.Context
): LyricsAppearanceSettings {
    val preferences = remember(context) {
        context.applicationContext.getSharedPreferences(
            "AppSettings",
            android.content.Context.MODE_PRIVATE
        )
    }
    val scope = rememberCoroutineScope()
    var preferenceRevision by remember(preferences) { mutableStateOf(0) }
    var settings by remember(preferences) {
        mutableStateOf(LyricsAppearanceSettings())
    }

    DisposableEffect(preferences, scope) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (
                key == null ||
                key == "lyrics_alignment" ||
                key == "lyrics_text_size" ||
                key == "lyrics_font_family"
            ) {
                // Listener callbacks may arrive on a worker; schedule Compose state
                // changes on the remembered main-thread composition scope.
                scope.launch { preferenceRevision += 1 }
            }
        }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        onDispose {
            preferences.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }

    LaunchedEffect(preferences, preferenceRevision) {
        settings = withContext(Dispatchers.IO) {
            LyricsAppearanceSettings(
                alignment = preferences.getString("lyrics_alignment", "Original")
                    ?: "Original",
                textSize = preferences.getString("lyrics_text_size", "Standard")
                    ?: "Standard",
                fontFamily = preferences.getString("lyrics_font_family", "Rounded")
                    ?: "Rounded"
            )
        }
    }

    return settings
}

/**
 * Single source of truth for lyrics rendering in the landscape side pane and the
 * immersive lyrics screen. Provider state, timing, karaoke highlighting and plain
 * text fallback all flow through the same renderer.
 */
@Composable
internal fun PlayerLyricsContent(
    currentSong: SongItem?,
    syncedLyrics: List<LyricLine>?,
    plainLyrics: String?,
    isLoading: Boolean,
    isPlaying: Boolean,
    lyricsError: Throwable?,
    currentPositionProvider: () -> Int,
    playbackSpeedProvider: () -> Float,
    contentReady: Boolean,
    contentColor: Color,
    dynamicAccentColor: Color,
    lyricsColorStyle: String,
    onRetryLyrics: () -> Unit,
    onFetchLyrics: () -> Unit,
    onSeekTo: (Int) -> Unit,
    selectedProvider: String?,
    providerResults: Map<String, LyricsResult>,
    gradientFontBorderWidth: Dp = 1.dp,
    centerLineLyrics: Boolean = false,
    forceLowCostRendering: Boolean = false,
    modifier: Modifier = Modifier
) {
    val compactLandscapeLyrics = LocalConfiguration.current.orientation ==
        android.content.res.Configuration.ORIENTATION_LANDSCAPE
    var plainLyricsLines by remember(plainLyrics) {
        mutableStateOf<List<String>>(emptyList())
    }
    LaunchedEffect(plainLyrics, syncedLyrics) {
        plainLyricsLines = if (!syncedLyrics.isNullOrEmpty() || plainLyrics.isNullOrBlank()) {
            emptyList()
        } else {
            withContext(Dispatchers.Default) {
                plainLyrics.orEmpty().split('\n').map { it.removeSuffix("\r") }
            }
        }
    }
    val context = LocalContext.current
    var isNoInternetFailure by remember(lyricsError, context) {
        mutableStateOf(false)
    }
    LaunchedEffect(lyricsError, context) {
        isNoInternetFailure = false
        val error = lyricsError ?: return@LaunchedEffect
        isNoInternetFailure = withContext(Dispatchers.IO) {
            runCatching {
                NetworkFailureClassifier.isNoInternetFailure(error, context.applicationContext)
            }.getOrDefault(false)
        }
    }
    // These three values were previously read with observeKey(), whose initial
    // SharedPreferences getters run synchronously during composition. A cold or large
    // AppSettings file can make the lyrics dialog's first frame wait on disk I/O.
    // Load them on IO and keep the listener for live updates without blocking Compose.
    val lyricsAppearance = rememberLyricsAppearanceSettings(context)
    val lyricsAlignment = lyricsAppearance.alignment
    val lyricsTextSize = lyricsAppearance.textSize
    val lyricsFontFamilyChoice = lyricsAppearance.fontFamily
    val configuredLyricsScale = when (lyricsTextSize) {
        "Small" -> 0.85f
        "Large" -> 1.18f
        else -> 1f
    }
    val plainLyricsFontFamily = when (lyricsFontFamilyChoice) {
        "Sans" -> FontFamily.SansSerif
        "Serif" -> FontFamily.Serif
        "Mono" -> FontFamily.Monospace
        else -> GoogleSansFlexNonRounded
    }
    val plainLyricsTextAlign = when (lyricsAlignment) {
        "Left" -> androidx.compose.ui.text.style.TextAlign.Start
        "Center" -> androidx.compose.ui.text.style.TextAlign.Center
        else -> androidx.compose.ui.text.style.TextAlign.Unspecified
    }

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        when {
            isLoading -> LoadingIndicator(
                modifier = Modifier.size(32.dp),
                color = dynamicAccentColor
            )
            !contentReady -> Unit
            !syncedLyrics.isNullOrEmpty() -> SyncedLyricsCompose(
                onSeekTo = onSeekTo,
                lyrics = syncedLyrics,
                currentPositionProvider = currentPositionProvider,
                playbackSpeedProvider = playbackSpeedProvider,
                isPlaying = isPlaying,
                contentColor = contentColor,
                dynamicAccentColor = dynamicAccentColor,
                currentSong = currentSong,
                selectedProvider = selectedProvider,
                providerResults = providerResults,
                lyricsColorStyle = lyricsColorStyle,
                // Reuse the async settings loaded by PlayerLyricsContent; avoid
                // blocking SharedPreferences reads during synced-lyrics composition.
                lyricsTextSize = lyricsTextSize,
                lyricsAlignment = lyricsAlignment,
                lyricsFontFamily = lyricsFontFamilyChoice,
                gradientFontBorderWidth = gradientFontBorderWidth,
                centerLineLyrics = centerLineLyrics,
                // Landscape also uses the cached direct-draw karaoke renderer;
                // only an explicit caller request may select the static fallback.
                forceLowCostRendering = forceLowCostRendering,
                modifier = Modifier.fillMaxSize()
            )
            !plainLyrics.isNullOrBlank() -> {
                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    // Use the same viewport-relative scale and typography metrics as
                    // karaoke/synced lines: 38 sp text with a 50 sp line height.
                    val viewportScale = if (compactLandscapeLyrics && maxHeight != Dp.Infinity) {
                        (maxHeight.value / 420f).coerceIn(0.58f, 0.86f)
                    } else {
                        1f
                    }
                    val plainLyricsScale = (configuredLyricsScale * viewportScale)
                        .coerceIn(0.48f, 1.18f)
                    val plainLyricsBrush = if (lyricsColorStyle == "Home Studio Gradient") {
                        dsmusicHomeStudioGradientBrush()
                    } else {
                        null
                    }
                    val plainLyricsStyle = DsmusicTheme.typography.headlineMedium.copy(
                        brush = plainLyricsBrush,
                        fontFamily = plainLyricsFontFamily,
                        fontSize = 44.sp * plainLyricsScale,
                        lineHeight = 58.sp * plainLyricsScale,
                        fontWeight = FontWeight.ExtraBold,
                        platformStyle = PlatformTextStyle(includeFontPadding = true),
                        textAlign = plainLyricsTextAlign
                    )
                    val plainLyricsColor = when (lyricsColorStyle) {
                        "Home Studio Gradient" -> Color.Unspecified
                        "White" -> Color.White
                        "UI Matching" -> dynamicAccentColor
                        else -> contentColor.copy(alpha = 0.9f)
                    }
                    // Plain (untimed) lyrics do not need a second stroked text layer.
                    // On older mobile GPUs that doubled every visible glyph draw and
                    // forced a second paragraph layout per row. Keep the selected font,
                    // size, alignment and gradient fill, but use one BasicText pass.
                    SideEffect {
                        CrashDiagnostics.updateLyricsRendering(
                            "Plain lyrics; renderer=single-pass text; outline=off; " +
                                "lines=${plainLyricsLines.size}; font=$lyricsFontFamilyChoice; " +
                                "fontScale=${plainLyricsScale}; colorStyle=$lyricsColorStyle"
                        )
                    }
                    // Virtualize one source line per item so off-screen lyrics do not
                    // participate in the initial measure/layout/draw pass.
                    if (plainLyricsLines.isEmpty()) {
                        LoadingIndicator(
                            modifier = Modifier.align(Alignment.Center).size(32.dp),
                            color = dynamicAccentColor
                        )
                    }
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 28.dp),
                        verticalArrangement = Arrangement.spacedBy(0.dp)
                    ) {
                        itemsIndexed(
                            items = plainLyricsLines,
                            key = { index, _ -> "plain-lyrics-$index" }
                        ) { _, lyricLine ->
                            Text(
                                text = lyricLine.ifEmpty { " " },
                                style = plainLyricsStyle,
                                color = plainLyricsColor,
                                modifier = Modifier.fillMaxWidth().drawWithContent {
                                    val drawStartedAt = System.nanoTime()
                                    try {
                                        drawContent()
                                    } finally {
                                        CrashDiagnostics.recordLyricsDraw(System.nanoTime() - drawStartedAt)
                                    }
                                },
                                onTextLayout = {
                                    CrashDiagnostics.recordLyricsTextLayout(lyricLine.length, it.lineCount)
                                }
                            )
                        }
                        item(key = "plain-lyrics-metadata-footer") {
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
            isNoInternetFailure -> {
                com.dsann.music.ui.theme.NoInternetState(
                    modifier = Modifier.fillMaxSize(),
                    onRetry = onRetryLyrics
                )
            }
            lyricsError != null -> LyricsErrorState(
                message = lyricsError.message ?: "Lyrics not found",
                onRetry = onRetryLyrics
            )
            else -> LyricsErrorState(
                message = "Lyrics are not loaded yet",
                onRetry = onFetchLyrics
            )
        }
    }
}


@Composable
private fun ProvidersSelector(
    providerResults: Map<String, LyricsResult>,
    selectedProvider: String?,
    onProviderSelected: (String) -> Unit,
    onRefindClick: () -> Unit,
    accentColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier
) {
    // Provider order is static. Rank once per result-map update rather than once per row.
    val knownProviders = remember {
        listOf(
            "LyricsPlus",
            "KuGou",
            "Bini",
            "Apple Music (Better Lyrics)",
            "Apple Music",
            "QQ Music",
            "NetEase Music",
            "YouTube Music",
            "YouTube (Paxsenix)",
            "Spotify",
            "Musixmatch",
            "SyncLRC",
            "LRCLIB",
            "EmbeddedID3",
            "Unison",
            "Genius"
        )
    }
    val allProviders = remember(providerResults.keys) {
        (knownProviders + providerResults.keys.sorted()).distinct()
    }

    // Keep failed/no-match providers visible too, so the user can see every searched source.
    val visibleProviders = allProviders
    val bestResult = remember(providerResults) { bestLyricsProviderResult(providerResults.values) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "LYRICS PROVIDERS",
            style = DsmusicTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = contentColor.copy(alpha = 0.5f),
            letterSpacing = 1.sp,
            modifier = Modifier.padding(horizontal = 4.dp)
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false)
                .animateContentSize(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    )
                )
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)
        ) {
            visibleProviders.forEachIndexed { index, provider ->
                val result = providerResults[provider]

                val isSelected = selectedProvider == provider ||
                    (selectedProvider == null && result != null && result == bestResult)

                val hasData = result != null && result.hasLyrics() && result.confidence >= 0f

                val displayName = when (provider) {
                    "EmbeddedID3" -> "Embedded ID3"
                    else -> provider
                }

                val lyricTypeLabel = when {
                    result?.isWordByWord == true -> "Karaoke"
                    result?.isSynced == true -> "Synced"
                    result?.hasLyrics() == true || result?.payloadClearedForMemory == true -> "Plain"
                    else -> null
                }
                val supportingText = when {
                    result == null -> "Not fetched yet · tap to fetch this source"
                    result.payloadClearedForMemory -> buildString {
                        append("Lyrics payload released")
                        lyricTypeLabel?.let { append(" · "); append(it) }
                        append(" · tap to fetch this source")
                    }
                    !result.hasLyrics() -> "No lyrics found · tap to search this source"
                    result.isWordByWord -> "Karaoke (word-by-word)"
                    result.isSynced -> "Synced lyrics"
                    else -> "Plain text"
                }

                val shape = getSegmentedShape(index = index, count = visibleProviders.size)

                val itemBgColor = when {
                    isSelected -> accentColor.copy(alpha = 0.15f)
                    hasData || result == null -> contentColor.copy(alpha = 0.06f)
                    else -> contentColor.copy(alpha = 0.02f)
                }

                ListItem(
                    selected = isSelected,
                    onClick = {
                        // Every row targets exactly this provider. Never restart the full sweep
                        // just because its lyrics payload was released or its initial result is null.
                        onProviderSelected(provider)
                    },
                    supportingContent = {
                        Text(text = supportingText)
                    },
                    leadingContent = {
                        val iconRes = when (provider) {
                            "SyncLRC" -> R.drawable.ic_lyrics
                            "LRCLIB" -> R.drawable.ic_lyrics
                            "YouTube Music" -> R.drawable.ic_youtube_music
                            else -> R.drawable.ic_lyrics
                        }
                        Icon(
                            painter = painterResource(id = iconRes),
                            contentDescription = provider,
                            modifier = Modifier.size(24.dp)
                        )
                    },
                    trailingContent = {
                        if (isSelected) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_check),
                                contentDescription = "Selected",
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    },
                    colors = ListItemDefaults.colors(
                        containerColor = itemBgColor,
                        selectedContainerColor = itemBgColor,
                        contentColor = contentColor.copy(alpha = if (hasData || result == null) 0.9f else 0.4f),
                        selectedContentColor = accentColor,
                        leadingContentColor = contentColor.copy(alpha = if (hasData || result == null) 0.8f else 0.4f),
                        selectedLeadingContentColor = accentColor,
                        trailingContentColor = contentColor.copy(alpha = 0.8f),
                        selectedTrailingContentColor = accentColor,
                        supportingContentColor = contentColor.copy(alpha = 0.6f),
                        selectedSupportingContentColor = accentColor.copy(alpha = 0.7f),
                        disabledContainerColor = itemBgColor,
                        disabledContentColor = contentColor.copy(alpha = 0.3f),
                        disabledLeadingContentColor = contentColor.copy(alpha = 0.3f),
                        disabledTrailingContentColor = contentColor.copy(alpha = 0.3f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(shape)
                ) {
                    Text(
                        text = displayName,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        val buttonInteractionSource = remember { MutableInteractionSource() }
        val buttonIsPressed by buttonInteractionSource.collectIsPressedAsState()

        val buttonCornerRadius by animateDpAsState(
            targetValue = if (buttonIsPressed) 28.dp else 16.dp,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow
            ),
            label = "refind_button_corners"
        )

        com.dsann.music.ui.theme.Button(
            onClick = onRefindClick,
            interactionSource = buttonInteractionSource,
            shape = RoundedCornerShape(buttonCornerRadius),
            colors = com.dsann.music.ui.theme.ButtonDefaults.buttonColors(
                containerColor = accentColor,
                contentColor = Color.Black
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_refresh),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = Color.Black
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Refind Lyrics",
                    style = DsmusicTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                )
            }
        }
    }
}

private fun getSegmentedShape(index: Int, count: Int): androidx.compose.ui.graphics.Shape {
    val outer = 24.dp
    val inner = 4.dp
    return when {
        count <= 1 -> RoundedCornerShape(outer)
        index == 0 -> RoundedCornerShape(topStart = outer, topEnd = outer, bottomStart = inner, bottomEnd = inner)
        index == count - 1 -> RoundedCornerShape(topStart = inner, topEnd = inner, bottomStart = outer, bottomEnd = outer)
        else -> RoundedCornerShape(inner)
    }
}


/** Karaoke > line-synchronized > plain; karaoke priority is LyricsPlus, KuGou, then Bini. */
private fun bestLyricsProviderResult(results: Collection<LyricsResult>): LyricsResult? =
    results.asSequence()
        .filter { it.hasLyrics() && it.confidence >= 0f }
        .maxWithOrNull(
            compareBy<LyricsResult> {
                when {
                    it.isWordByWord -> 3
                    it.isSynced -> 2
                    !it.isInstrumental && it.hasLyrics() -> 1
                    else -> 0
                }
            }.thenBy {
                when {
                    it.isWordByWord && it.providerName.equals("LyricsPlus", ignoreCase = true) -> 3
                    it.isWordByWord && it.providerName.equals("KuGou", ignoreCase = true) -> 2
                    it.isWordByWord && it.providerName.equals("Bini", ignoreCase = true) -> 1
                    !it.isWordByWord && it.providerName.equals("KuGou", ignoreCase = true) -> 1
                    else -> 0
                }
            }.thenBy { it.confidence }
        )
