package com.dsann.music.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
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
import com.dsann.music.data.lyrics.LyricsResult
import com.dsann.music.model.SongItem
import com.dsann.music.ui.theme.DsmusicTheme
import com.dsann.music.ui.theme.dsmusicHomeStudioGradientBrush
import com.dsann.music.ui.theme.Icon
import com.dsann.music.ui.theme.IconButton
import com.dsann.music.ui.theme.LoadingIndicator
import com.dsann.music.ui.theme.Text
import com.dsann.music.ui.theme.TextButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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

/**
 * A deliberately separate, static lyrics surface. It never instantiates the
 * synchronized/karaoke renderer, so reading lyrics cannot start its frame clock.
 */
@Composable
internal fun PlainLyricsPage(
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
            if (dialogWindow != null) {
                dialogWindow.setLayout(
                    android.view.WindowManager.LayoutParams.MATCH_PARENT,
                    android.view.WindowManager.LayoutParams.MATCH_PARENT
                )
                WindowCompat.setDecorFitsSystemWindows(dialogWindow, false)
                WindowInsetsControllerCompat(dialogWindow, dialogWindow.decorView).apply {
                    systemBarsBehavior =
                        WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    hide(WindowInsetsCompat.Type.systemBars())
                }
            }
            onDispose {
                if (dialogWindow != null) {
                    WindowInsetsControllerCompat(dialogWindow, dialogWindow.decorView)
                        .show(WindowInsetsCompat.Type.systemBars())
                }
            }
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
                            style = DsmusicTheme.typography.titleMedium
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
                        style = DsmusicTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = currentSong?.artist?.takeIf { it.isNotBlank() } ?: "Unknown artist",
                        color = secondaryColor,
                        style = DsmusicTheme.typography.bodyMedium,
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

                if (displayLines.isNotEmpty()) {
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
                                    color = if (lyricsColorStyle == "Home Studio Gradient") Color.Unspecified else contentColor,
                                    style = DsmusicTheme.typography.bodyLarge.copy(
                                        fontSize = 44.sp,
                                        lineHeight = 58.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        brush = if (lyricsColorStyle == "Home Studio Gradient") {
                                            dsmusicHomeStudioGradientBrush()
                                        } else null,
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
    providerResults: Map<String, LyricsResult>,
    selectedProvider: String?,
    onProviderSelected: (String) -> Unit,
    onRetryLyrics: () -> Unit,
    onFetchLyrics: () -> Unit
) {
    var showProviderPicker by remember { mutableStateOf(false) }
    var displayLines by remember(currentSong?.id) { mutableStateOf<List<String>>(emptyList()) }
    var prepared by remember(currentSong?.id) { mutableStateOf(false) }

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
                    style = DsmusicTheme.typography.titleSmall
                )
                Text(
                    text = currentSong?.title ?: "Plain text",
                    color = secondaryColor,
                    style = DsmusicTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(onClick = { showProviderPicker = true }, modifier = Modifier.size(42.dp)) {
                Icon(
                    imageVector = Icons.Rounded.MoreVert,
                    contentDescription = "Choose lyrics provider",
                    tint = contentColor,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
        Text(
            text = selectedProvider ?: "Choose lyrics source",
            color = accentColor,
            style = DsmusicTheme.typography.labelSmall,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        when {
            displayLines.isNotEmpty() -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                itemsIndexed(displayLines, key = { index, _ -> "landscape-plain-lyrics-$index" }) { _, line ->
                    if (line.isNotBlank()) {
                        Text(
                            text = line,
                            color = if (lyricsColorStyle == "Home Studio Gradient") Color.Unspecified else contentColor,
                            style = DsmusicTheme.typography.bodyLarge.copy(
                                fontSize = 44.sp,
                                lineHeight = 58.sp,
                                fontWeight = FontWeight.ExtraBold,
                                brush = if (lyricsColorStyle == "Home Studio Gradient") {
                                    dsmusicHomeStudioGradientBrush()
                                } else null,
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
