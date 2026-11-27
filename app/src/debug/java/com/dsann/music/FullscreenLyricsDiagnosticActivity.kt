package com.dsann.music

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.runtime.DisposableEffect
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.dsann.music.data.lyrics.LyricLine
import com.dsann.music.data.lyrics.LyricWord
import com.dsann.music.model.SongItem
import com.dsann.music.ui.player.FullScreenLyricsOverlay
import com.dsann.music.ui.theme.DsmusicAppTheme
import kotlinx.coroutines.delay

/**
 * Debug-only isolated harness for the exact fullscreen lyrics composable.
 *
 * It deliberately avoids MainActivity's Explore tree, playback service, cached
 * navigation tabs and lyrics provider network graph. That lets the emulator
 * capture a useful renderer stack rather than a first-launch Explore layout stall.
 */
class FullscreenLyricsDiagnosticActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!BuildConfig.DEBUG) {
            finish()
            return
        }

        val mode = intent.getStringExtra(EXTRA_MODE)
            ?.lowercase()
            ?.takeIf { it in setOf("empty", "synced", "plain", "karaoke") }
            ?: "empty"

        CrashDiagnostics.updateBaseScreen("Fullscreen lyrics diagnostic mode=$mode")
        Log.i(TAG, "DIAGNOSTIC_ACTIVITY_STARTED mode=$mode api=${android.os.Build.VERSION.SDK_INT}")

        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            DsmusicAppTheme(darkTheme = true) {
                var baseLayoutReady by remember { mutableStateOf(false) }
                val openLyricsState = remember { mutableStateOf(false) }
                val rootView = LocalView.current

                // Schedule from Android's main Handler after the first measured frame.
                // This keeps the request independent of the Compose coroutine frame clock.
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF080B1B))
                        .onGloballyPositioned {
                            if (!baseLayoutReady) {
                                baseLayoutReady = true
                                Log.i(TAG, "DIAGNOSTIC_BASE_LAYOUT_READY mode=$mode")
                                rootView.postDelayed({
                                    if (!isFinishing && !isDestroyed) {
                                        openLyricsState.value = true
                                        Log.i(TAG, "FULLSCREEN_LYRICS_REQUESTED mode=$mode")
                                    }
                                }, 1_500L)
                            }
                        }
                ) { }

                val openLyrics = openLyricsState.value

                if (openLyrics) {
                    val testSong = remember {
                        SongItem(
                            id = -23_101L,
                            title = "Quantune Fullscreen Lyrics Diagnostic",
                            artist = "Quantune Emulator",
                            albumId = -1L,
                            path = null,
                            duration = 180_000L,
                            dateAdded = System.currentTimeMillis(),
                            data = null
                        )
                    }

                    // Use a monotonic playhead rather than adding a fixed 16 ms for
                    // every coroutine wake-up. TCG emulators can delay dispatch heavily,
                    // so a tick-count clock under-reports elapsed playback time and makes
                    // the renderer test fail even while frames are being drawn.
                    val karaokePlaybackStartedAtMs = remember(mode) {
                        android.os.SystemClock.elapsedRealtime()
                    }
                    val karaokePositionMs = remember(mode) {
                        mutableIntStateOf(1_000)
                    }
                    val readKaraokePosition: () -> Int = remember(
                        mode,
                        karaokePlaybackStartedAtMs,
                        testSong.duration
                    ) {
                        {
                            if (mode == "karaoke") {
                                (
                                    1_000L +
                                        (android.os.SystemClock.elapsedRealtime() - karaokePlaybackStartedAtMs)
                                            .coerceAtLeast(0L)
                                    ).coerceAtMost(
                                        testSong.duration.coerceAtLeast(1L)
                                            .coerceAtMost(Int.MAX_VALUE.toLong())
                                    ).toInt()
                            } else {
                                1_000
                            }
                        }
                    }
                    val hasLoggedKaraokePlaybackAdvance = remember(mode) {
                        java.util.concurrent.atomic.AtomicBoolean(false)
                    }
                    val rendererPositionProvider: () -> Int = remember(
                        mode,
                        readKaraokePosition,
                        hasLoggedKaraokePlaybackAdvance
                    ) {
                        {
                            val positionMs = readKaraokePosition()
                            // This callback is sampled by the production renderer's
                            // VSYNC collector. Log only when that renderer reads a live
                            // position beyond the initial test point.
                            if (
                                mode == "karaoke" &&
                                positionMs >= 6_000 &&
                                hasLoggedKaraokePlaybackAdvance.compareAndSet(false, true)
                            ) {
                                Log.i(TAG, "KARAOKE_PLAYBACK_ADVANCED position_ms=\$positionMs")
                            }
                            positionMs
                        }
                    }
                    LaunchedEffect(mode, karaokePlaybackStartedAtMs, readKaraokePosition) {
                        if (mode != "karaoke") return@LaunchedEffect

                        val durationMs = testSong.duration.coerceAtLeast(1L)
                            .coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
                        while (karaokePositionMs.intValue < durationMs) {
                            karaokePositionMs.intValue = readKaraokePosition()
                            if (karaokePositionMs.intValue >= durationMs) break
                            delay(100L)
                        }
                    }
                    val testSyncedLyrics = remember(mode) {
                        when (mode) {
                            "synced" -> listOf(
                                LyricLine(0L, "Quantune fullscreen lyrics renderer test"),
                                LyricLine(4_000L, "Second line for layout and timing"),
                                LyricLine(8_000L, "Third line to validate scrolling")
                            )
                            "karaoke" -> createKaraokeDiagnosticLyrics()
                            else -> null
                        }
                    }
                    val testPlainLyrics = remember(mode) {
                        if (mode == "plain") {
                            (1..240).joinToString("\n") { index ->
                                "Quantune fullscreen lyrics renderer stress line $index"
                            }
                        } else {
                            null
                        }
                    }

                    Dialog(
                        onDismissRequest = { finish() },
                        properties = DialogProperties(usePlatformDefaultWidth = false)
                    ) {
                        val dialogWindow =
                            (LocalView.current.parent as? DialogWindowProvider)?.window

                        DisposableEffect(dialogWindow) {
                            val controller = dialogWindow?.let { dialog ->
                                dialog.setLayout(
                                    android.view.WindowManager.LayoutParams.MATCH_PARENT,
                                    android.view.WindowManager.LayoutParams.MATCH_PARENT
                                )
                                WindowCompat.setDecorFitsSystemWindows(dialog, false)
                                WindowCompat.getInsetsController(dialog, dialog.decorView).also {
                                    it.systemBarsBehavior =
                                        WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                                    it.hide(WindowInsetsCompat.Type.systemBars())
                                }
                            }
                            onDispose {
                                controller?.show(WindowInsetsCompat.Type.systemBars())
                            }
                        }

                        FullScreenLyricsOverlay(
                            currentSong = testSong,
                            syncedLyrics = testSyncedLyrics,
                            plainLyrics = testPlainLyrics,
                            isLoading = false,
                            isPlaying = mode == "karaoke",
                            lyricsError = null,
                            currentPositionProvider = rendererPositionProvider,
                            playbackSpeedProvider = { 1f },
                            contentReady = true,
                            backgroundBrush = Brush.verticalGradient(
                                listOf(Color(0xFF101B3B), Color(0xFF070B18))
                            ),
                            contentColor = Color.White,
                            contentSecondary = Color(0xFFB9C5DA),
                            dynamicAccentColor = Color(0xFF8CB5FF),
                            lyricsColorStyle = "White",
                            onRetryLyrics = {
                                Log.i(TAG, "DIAGNOSTIC_RETRY_TAPPED mode=$mode")
                            },
                            onFetchLyrics = {
                                Log.i(TAG, "DIAGNOSTIC_FETCH_TAPPED mode=$mode")
                            },
                            onSeekTo = {},
                            providerResults = emptyMap(),
                            selectedProvider = null,
                            onProviderSelected = {},
                            onDismissLyrics = { finish() },
                            modifier = Modifier
                                .fillMaxSize()
                                .then(
                                    if (mode == "karaoke") {
                                        Modifier.semantics {
                                            contentDescription =
                                                "karaoke-position-${karaokePositionMs.intValue}"
                                        }
                                    } else {
                                        Modifier
                                    }
                                )
                        )
                    }
                }
            }
        }
    }

    private fun createKaraokeDiagnosticLyrics(): List<LyricLine> {
        val phrases = listOf(
            listOf("Quantune", "fullscreen", "lyrics", "renderer", "test"),
            listOf("Second", "line", "validates", "word", "timing"),
            listOf("Cached", "text", "layout", "draws", "smoothly"),
            listOf("Every", "line", "keeps", "its", "own", "word", "ranges")
        )
        return List(64) { lineIndex ->
            val words = phrases[lineIndex % phrases.size]
            val lineStartMs = lineIndex * 5_000L
            val content = words.joinToString(" ")
            var charOffset = 0
            val timedWords = words.mapIndexed { wordIndex, word ->
                val startOffset = charOffset
                charOffset += word.length + 1
                LyricWord(
                    text = word,
                    absoluteStartTimeMs = lineStartMs + wordIndex * 600L,
                    durationMs = 520L,
                    charRange = startOffset until (startOffset + word.length)
                )
            }
            LyricLine(
                startTimeMs = lineStartMs,
                content = content,
                isWordByWord = true,
                words = timedWords
            )
        }
    }

    companion object {
        const val EXTRA_MODE = "com.dsann.music.EXTRA_LYRICS_DIAGNOSTIC_MODE"
        private const val TAG = "QuantuneANRTest"
    }
}
