package com.dsann.music.ui.player

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * One persisted frame-rate preference for all continuous app animation loops.
 * 0 means follow the display's real vsync cadence; other values cap updates while
 * still sampling only on display frames. Audio playback timing is never modified.
 */
internal object AppAnimationFrameRateController {
    private const val PREFS_NAME = "AppSettings"
    private const val PREFERENCE_KEY = "player_animation_frame_rate"

    private val mutableFrameRate = MutableStateFlow(0)
    val frameRate: StateFlow<Int> = mutableFrameRate.asStateFlow()

    fun normalize(value: Int): Int =
        value.takeIf { it == 0 || it == 20 || it == 30 || it == 40 || it == 60 } ?: 0

    fun load(context: Context): Int {
        val safe = normalize(
            context.applicationContext
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getInt(PREFERENCE_KEY, 0)
        )
        mutableFrameRate.value = safe
        return safe
    }

    fun set(context: Context, value: Int): Int {
        val safe = normalize(value)
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt(PREFERENCE_KEY, safe)
            .apply()
        mutableFrameRate.value = safe
        return safe
    }
}

/**
 * Publishes a display-vsync timestamp, optionally capped to a selected frame rate.
 *
 * Device mode (the default) publishes every frame delivered by Compose. Fixed modes
 * publish on the first display frame that crosses each target-time interval. This
 * preserves vsync alignment on 60/90/120 Hz displays instead of introducing a timer
 * that drifts away from the display. If a target cannot be represented exactly by
 * the physical refresh cadence, frames are distributed across adjacent vsyncs.
 *
 * All continuous visual components use the same persisted rate and absolute time
 * buckets, so player progress, waveform, visualizer, karaoke and other visual loops
 * remain aligned to the device's presented frames.
 */
@Composable
internal fun rememberAppAnimationFrameTimeNanos(
    active: Boolean,
    forceDisplayRefresh: Boolean = false
): State<Long> {
    val context = LocalContext.current.applicationContext
    val frameRate by AppAnimationFrameRateController.frameRate.collectAsState()
    val frameTimeNanos = remember { mutableLongStateOf(0L) }

    LaunchedEffect(context) {
        AppAnimationFrameRateController.load(context)
    }

    LaunchedEffect(active, frameRate, forceDisplayRefresh) {
        if (!active) {
            frameTimeNanos.longValue = 0L
            return@LaunchedEffect
        }

        // Playback progress can opt out of the decorative-animation FPS cap.
        // This keeps the seek bar and mini-player progress aligned to every display vsync
        // on API 23 without forcing the visualizer or other effects to run at full rate.
        val intervalNanos = if (!forceDisplayRefresh && frameRate > 0) {
            1_000_000_000L / frameRate
        } else {
            0L
        }
        var lastBucket = Long.MIN_VALUE

        while (true) {
            withFrameNanos { displayFrameNanos ->
                if (intervalNanos == 0L) {
                    frameTimeNanos.longValue = displayFrameNanos
                } else {
                    val bucket = displayFrameNanos / intervalNanos
                    if (bucket != lastBucket) {
                        lastBucket = bucket
                        frameTimeNanos.longValue = displayFrameNanos
                    }
                }
            }
        }
    }

    return frameTimeNanos
}
