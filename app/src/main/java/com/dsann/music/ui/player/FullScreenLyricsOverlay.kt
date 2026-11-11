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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
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
import com.dsann.music.data.features.AiLyricsTranslationSettingsStore
import com.dsann.music.data.features.AiLyricsTranslationSession
import com.dsann.music.data.features.AiLyricsTranslationCatalog
import com.dsann.music.util.EncryptedFeatureSecretStore
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
    modifier: Modifier = Modifier
) {
    val compactLandscapeLyrics = LocalConfiguration.current.orientation ==
        android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val context = LocalContext.current
    val appearancePreferences = remember(context) {
        context.getSharedPreferences("AppSettings", android.content.Context.MODE_PRIVATE)
    }
    val lyricsAlignment by appearancePreferences.observeKey("lyrics_alignment", "Original")
    val lyricsTextSize by appearancePreferences.observeKey("lyrics_text_size", "Standard")
    val lyricsFontFamilyChoice by appearancePreferences.observeKey("lyrics_font_family", "Rounded")
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
                gradientFontBorderWidth = gradientFontBorderWidth,
                centerLineLyrics = centerLineLyrics,
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
                    // A single Text node containing an entire song makes Compose measure
                    // and rasterize every off-screen line when the lyrics page first opens.
                    // Virtualize one source line per item so only the visible lyric lines
                    // enter the initial layout/draw pass.
                    val plainLyricsLines = remember(plainLyrics) {
                        plainLyrics.orEmpty().split('\n').map { it.removeSuffix("\r") }
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
                            LyricsText(
                                text = lyricLine.ifEmpty { " " },
                                style = plainLyricsStyle,
                                color = plainLyricsColor,
                                modifier = Modifier.fillMaxWidth(),
                                outlineWidth = 1.dp,
                                fillTextWidth = true,
                                drawOutlineForSolidText = true
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
            NetworkFailureClassifier.isNoInternetFailure(lyricsError, context) -> {
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
internal fun FullScreenLyricsOverlay(
    currentSong: SongItem?,
    syncedLyrics: List<LyricLine>?,
    plainLyrics: String?,
    isLoading: Boolean,
    isPlaying: Boolean,
    lyricsError: Throwable?,
    currentPositionProvider: () -> Int,
    playbackSpeedProvider: () -> Float = { 1f },
    contentReady: Boolean,
    backgroundBrush: Brush,
    contentColor: Color,
    contentSecondary: Color,
    dynamicAccentColor: Color,
    lyricsColorStyle: String = "Home Studio Gradient",
    onRetryLyrics: () -> Unit,
    onFetchLyrics: () -> Unit,
    onSeekTo: (Int) -> Unit,
    providerResults: Map<String, LyricsResult>,
    selectedProvider: String?,
    onProviderSelected: (String) -> Unit,
    onDismissLyrics: () -> Unit,
    modifier: Modifier = Modifier
) {
    val consumeClicks = remember { MutableInteractionSource() }
    var showProvidersSheet by remember { mutableStateOf(false) }
    var syncOffsetMs by remember(currentSong?.id) { mutableStateOf(0L) }
    val context = LocalContext.current

    DisposableEffect(Unit) {
        CrashDiagnostics.updateOverlayScreen("Full-screen Lyrics")
        onDispose {
            CrashDiagnostics.updateOverlayScreen(null)
            CrashDiagnostics.updateUiDetails("Not available")
            CrashDiagnostics.updateLyricsRendering("Not available")
        }
    }
    SideEffect {
        CrashDiagnostics.updateUiDetails(
            "Lyrics full-screen dialog; loading=$isLoading; playing=$isPlaying; " +
                "synced=${!syncedLyrics.isNullOrEmpty()}; plain=${!plainLyrics.isNullOrBlank()}; " +
                "providers=${providerResults.size}"
        )
        CrashDiagnostics.updateLyricsRendering(
            "Dialog presentation; lyrics content=${when { !syncedLyrics.isNullOrEmpty() -> "synced"; !plainLyrics.isNullOrBlank() -> "plain"; else -> "none/loading" }}"
        )
    }

    val translationScope = rememberCoroutineScope()
    val translationSettingsStore = remember(context) { AiLyricsTranslationSettingsStore(context) }
    val featureSecretStore = remember(context) { EncryptedFeatureSecretStore(context) }
    val translationSettings = remember(translationSettingsStore) { translationSettingsStore.load() }
    val translationPreferences = remember(context) {
        context.applicationContext.getSharedPreferences(
            "lyrics_translation_settings",
            android.content.Context.MODE_PRIVATE
        )
    }
    var translationEnabled by remember(translationSettings.enabled) {
        mutableStateOf(translationSettings.enabled)
    }

    // Do not build a second whole-song lyrics string or hash it during composition when
    // translation is disabled. When enabled, prepare both values off the UI thread.
    var translationSource by remember { mutableStateOf("") }
    var translationKey by remember { mutableStateOf<String?>(null) }
    var translationSourceReady by remember { mutableStateOf(false) }
    LaunchedEffect(
        currentSong?.id,
        syncedLyrics,
        plainLyrics,
        translationSettings,
        translationEnabled
    ) {
        translationSourceReady = false
        translationSource = ""
        translationKey = null
        if (!translationEnabled) {
            translationSourceReady = true
            return@LaunchedEffect
        }

        val songId = currentSong?.id
        val prepared = withContext(Dispatchers.Default) {
            val source = when {
                !syncedLyrics.isNullOrEmpty() -> syncedLyrics.joinToString("\n") { line ->
                    line.content.ifBlank { line.words.joinToString("") { word -> word.text } }
                }
                !plainLyrics.isNullOrBlank() -> plainLyrics.orEmpty()
                else -> ""
            }
            val key = songId?.takeIf { source.isNotBlank() }?.let {
                AiLyricsTranslationSession.cacheKey(it, source, translationSettings)
            }
            source to key
        }
        translationSource = prepared.first
        translationKey = prepared.second
        translationSourceReady = true
    }

    val translationCache by AiLyricsTranslationSession.entries.collectAsStateWithLifecycle()
    val cachedTranslation = translationKey?.let { translationCache[it] }
    var translatedLyrics by remember(currentSong?.id) { mutableStateOf<String?>(null) }
    var translatedSyncedLyrics by remember(currentSong?.id) {
        mutableStateOf<List<LyricLine>?>(null)
    }
    var showTranslatedLyrics by remember(currentSong?.id) { mutableStateOf(false) }
    var translationBusy by remember { mutableStateOf(false) }
    var translationStatus by remember { mutableStateOf("") }
    var translationJob by remember { mutableStateOf<Job?>(null) }

    DisposableEffect(translationPreferences) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { preferences, key ->
            if (key == "enabled") {
                val isEnabled = preferences.getBoolean("enabled", true)
                translationEnabled = isEnabled
                if (!isEnabled) {
                    translationJob?.cancel()
                    translationJob = null
                    translationBusy = false
                    translatedLyrics = null
                    translatedSyncedLyrics = null
                    showTranslatedLyrics = false
                    translationStatus = ""
                }
            }
        }
        translationPreferences.registerOnSharedPreferenceChangeListener(listener)
        onDispose {
            translationPreferences.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }

    val requestLyricsTranslation: () -> Unit = {
        if (!translationEnabled) {
            translationStatus = "Enable AI lyrics translation in Settings → Playback."
        } else if (!translationBusy) {
            if (!translationSourceReady) {
                translationStatus = "Preparing lyrics for translation…"
            } else if (translatedLyrics != null || translatedSyncedLyrics != null) {
                showTranslatedLyrics = !showTranslatedLyrics
            } else if (translationSource.isBlank()) {
                translationStatus = "Load lyrics before requesting a translation."
            } else {
                translationBusy = true
                translationStatus = "Translating lyrics…"
                translationJob = translationScope.launch {
                    try {
                        val settings = translationSettingsStore.load()
                        if (!settings.enabled) {
                            throw IllegalStateException(
                                "Enable AI lyrics translation in Settings → Playback."
                            )
                        }
                        val result = AiLyricsTranslationSession.translate(
                            context = context,
                            songId = currentSong?.id
                                ?: throw IllegalStateException("No track is currently selected."),
                            source = translationSource
                        )
                        translatedLyrics = result.translatedText
                        translatedSyncedLyrics = withContext(Dispatchers.Default) {
                            AiLyricsTranslationSession.mapToSyncedLines(
                                syncedLyrics,
                                result.translatedText
                            )
                        }
                        showTranslatedLyrics = true
                        translationStatus = "Translated with ${settings.provider} · tap Original to switch back."
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (error: Exception) {
                        translationStatus = error.message ?: "Lyrics translation failed."
                    } finally {
                        translationBusy = false
                        translationJob = null
                    }
                }
            }
        }
    }

    LaunchedEffect(
        currentSong?.id,
        translationSource,
        cachedTranslation,
        translationEnabled,
        translationSourceReady
    ) {
        translationJob?.cancel()
        translationJob = null
        translationBusy = false
        if (!translationEnabled || !translationSourceReady) {
            translatedLyrics = null
            translatedSyncedLyrics = null
            showTranslatedLyrics = false
            translationStatus = ""
            return@LaunchedEffect
        }
        translatedLyrics = cachedTranslation?.translatedText
        translatedSyncedLyrics = if (cachedTranslation != null) {
            withContext(Dispatchers.Default) {
                AiLyricsTranslationSession.mapToSyncedLines(
                    syncedLyrics,
                    cachedTranslation.translatedText
                )
            }
        } else {
            null
        }
        showTranslatedLyrics = cachedTranslation != null
        translationStatus = cachedTranslation?.let {
            "Reused translation · ${it.provider}"
        }.orEmpty()
    }
    var showSyncControls by remember { mutableStateOf(false) }
    var syncControlActivity by remember { mutableStateOf(0) }

    // Hide the adjustment strip after three seconds without a sync-control action.
    LaunchedEffect(showSyncControls, syncControlActivity, currentSong?.id) {
        if (showSyncControls) {
            delay(3000L)
            showSyncControls = false
        }
    }

    val effectivePositionProvider = remember(currentPositionProvider, syncOffsetMs) {
        {
            (currentPositionProvider().toLong() + syncOffsetMs)
                .coerceIn(0L, Int.MAX_VALUE.toLong())
                .toInt()
        }
    }

    fun adjustSyncOffset(deltaMs: Long) {
        syncOffsetMs = (syncOffsetMs + deltaMs).coerceIn(-10_000L, 10_000L)
        showSyncControls = true
        syncControlActivity += 1
    }

    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose {
            view.keepScreenOn = false
        }
    }

    Box(
        modifier = modifier
            .background(backgroundBrush)
            .clickable(
                interactionSource = consumeClicks,
                indication = null,
                onClick = {}
            )
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(vertical = 12.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Centered Title Header Layout
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Spacer(
                    modifier = Modifier
                        .size(48.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onDismissLyrics
                        )
                )

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .weight(1f)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onDismissLyrics
                        )
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.animateContentSize(
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioNoBouncy,
                                stiffness = Spring.StiffnessMedium
                            )
                        )
                    ) {
                        Text(
                            text = "LYRICS",
                            style = DsmusicTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = contentColor.copy(alpha = 0.5f),
                            letterSpacing = 1.sp
                        )

                        val activeResult = providerResults[selectedProvider] ?: bestLyricsProviderResult(providerResults.values)

                        val lyricTypeBadge = when {
                            activeResult == null -> null
                            !activeResult.hasLyrics() -> null
                            activeResult.isWordByWord -> "Karaoke Lyrics"
                            activeResult.isSynced -> "Synced Lyrics"
                            else -> "Plain Lyrics"
                        }

                        AnimatedVisibility(
                            visible = lyricTypeBadge != null,
                            enter = fadeIn(
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                    stiffness = Spring.StiffnessLow
                                )
                            ) + slideInHorizontally(
                                initialOffsetX = { it / 2 },
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                    stiffness = Spring.StiffnessLow
                                )
                            ),
                            exit = fadeOut() + slideOutHorizontally()
                        ) {
                            if (lyricTypeBadge != null) {
                                Text(
                                    text = " • $lyricTypeBadge",
                                    style = DsmusicTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = contentColor.copy(alpha = 0.4f),
                                    letterSpacing = 1.sp
                                )
                            }
                        }
                    }
                    Text(
                        text = currentSong?.title ?: "Unknown Title",
                        style = DsmusicTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = contentColor,
                        maxLines = 1,
                        modifier = Modifier.basicMarqueeWithFadedEdges(edgeWidth = 8.dp)
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (translationEnabled) {
                        IconButton(
                            onClick = requestLyricsTranslation,
                            enabled = !translationBusy,
                            interactionSource = remember { MutableInteractionSource() }
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Translate,
                                contentDescription = when {
                                    translationBusy -> "Translating lyrics"
                                    showTranslatedLyrics -> "Show original lyrics"
                                    else -> "Translate lyrics"
                                },
                                tint = contentColor.copy(alpha = if (translationBusy) 0.4f else 0.9f),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                    IconButton(
                        onClick = { showProvidersSheet = true },
                        interactionSource = remember { MutableInteractionSource() }
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.MoreVert,
                            contentDescription = "Change Lyrics Provider",
                            tint = contentColor.copy(alpha = 0.8f),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }

            if (translationStatus.isNotBlank()) {
                Text(
                    text = translationStatus,
                    style = DsmusicTheme.typography.labelSmall,
                    color = contentSecondary,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)
                )
            }

            PlayerLyricsContent(
                currentSong = currentSong,
                syncedLyrics = if (showTranslatedLyrics) translatedSyncedLyrics else syncedLyrics,
                plainLyrics = if (showTranslatedLyrics && translatedSyncedLyrics == null) {
                    translatedLyrics
                } else {
                    plainLyrics
                },
                isLoading = isLoading,
                isPlaying = isPlaying,
                lyricsError = lyricsError,
                currentPositionProvider = effectivePositionProvider,
                playbackSpeedProvider = playbackSpeedProvider,
                contentReady = contentReady,
                contentColor = contentColor,
                dynamicAccentColor = dynamicAccentColor,
                lyricsColorStyle = lyricsColorStyle,
                onRetryLyrics = onRetryLyrics,
                onFetchLyrics = onFetchLyrics,
                onSeekTo = onSeekTo,
                selectedProvider = selectedProvider,
                providerResults = providerResults,
                // Keep the same 1 dp gradient font contour in fullscreen lyrics.
                gradientFontBorderWidth = 1.dp,
                centerLineLyrics = true,
                modifier = Modifier.fillMaxWidth().weight(1f)
            )

            // Do not tie the source selector to contentReady: provider switching temporarily
            // clears the visible lyrics while a new source is loading.
            if (showProvidersSheet) {
                LyricsProviderPickerSheet(
                    providerResults = providerResults,
                    selectedProvider = selectedProvider,
                    onProviderSelected = onProviderSelected,
                    onRefindClick = onRetryLyrics,
                    accentColor = dynamicAccentColor,
                    contentColor = contentColor,
                    backgroundBrush = backgroundBrush,
                    onDismissRequest = { showProvidersSheet = false }
                )
            }
        }

        if (contentReady && !syncedLyrics.isNullOrEmpty()) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                AnimatedVisibility(visible = showSyncControls) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(28.dp))
                            .background(DsmusicTheme.colorScheme.surface.copy(alpha = 0.94f))
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 4.dp, vertical = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(0.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val adjustments = listOf(
                            -500L to "−0.5s",
                            -50L to "−50ms",
                            0L to "Reset",
                            50L to "+50ms",
                            500L to "+0.5s"
                        )
                        adjustments.forEach { (deltaMs, label) ->
                            TextButton(
                                onClick = {
                                    if (deltaMs == 0L) {
                                        syncOffsetMs = 0L
                                        showSyncControls = true
                                        syncControlActivity += 1
                                    } else {
                                        adjustSyncOffset(deltaMs)
                                    }
                                }
                            ) {
                                Text(
                                    text = label,
                                    color = DsmusicTheme.colorScheme.onSurface,
                                    style = DsmusicTheme.typography.labelMedium,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }

                TextButton(
                    onClick = {
                        showSyncControls = !showSyncControls
                        syncControlActivity += 1
                    },
                    modifier = Modifier
                        .clip(RoundedCornerShape(24.dp))
                        .background(DsmusicTheme.colorScheme.surface.copy(alpha = 0.88f))
                ) {
                    val offsetLabel = String.format(java.util.Locale.US, "%+.1fs", syncOffsetMs / 1000f)
                    Text(
                        text = if (syncOffsetMs == 0L) "LYRICS SYNC" else "SYNC $offsetLabel",
                        color = DsmusicTheme.colorScheme.onSurface,
                        style = DsmusicTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
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
