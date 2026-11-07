package com.dsann.music.ui.player

import kotlin.math.abs
import kotlin.math.roundToInt

/** Internal karaoke timing baseline; the adjustment UI treats this as 0 ms. */
internal const val DEFAULT_KARAOKE_TIMING_BASE_OFFSET_MS = 0L

/**
 * Frame-paced presentation clock for word-synchronized lyrics.
 *
 * Media3's position is the authority. Between display frames we advance using elapsed
 * monotonic frame time and playback speed, then phase-lock that prediction to the latest
 * Media3 sample. Small timing noise is corrected quickly; seeks, stalls/resumes and long
 * frame gaps re-anchor immediately instead of allowing the lyric sweep to drift.
 *
 * This class contains no Android or Compose dependencies so its timing behaviour is
 * deterministic and unit-testable.
 */
internal fun calculateKaraokeWordProgress(
    wordStartMs: Long,
    wordDurationMs: Long,
    playbackPositionMs: Long
): Float {
    val duration = wordDurationMs.coerceAtLeast(1L)
    if (playbackPositionMs < wordStartMs) return 0f
    val elapsed = playbackPositionMs.toDouble() - wordStartMs.toDouble()
    if (elapsed >= duration.toDouble()) return 1f
    return (elapsed / duration.toDouble()).toFloat().coerceIn(0f, 1f)
}

internal class KaraokePlaybackClock(
    private val discontinuityThresholdMs: Double = 45.0,
    private val correctionFactor: Double = 0.40,
    private val maxCorrectionPerFrameMs: Double = 12.0,
    private val maxFrameGapMs: Double = 120.0
) {
    private var hasAnchor = false
    private var shownPositionMs = 0.0
    private var lastFrameNanos = Long.MIN_VALUE

    fun reset(positionMs: Int) {
        shownPositionMs = positionMs.coerceAtLeast(0).toDouble()
        lastFrameNanos = Long.MIN_VALUE
        hasAnchor = false
    }

    fun update(
        frameTimeNanos: Long,
        actualPositionMs: Int,
        playbackSpeed: Float,
        isPlaying: Boolean
    ): Int {
        val actual = actualPositionMs.coerceAtLeast(0).toDouble()

        // A paused playhead is authoritative. Re-anchor on every paused sample so seeks
        // while paused are reflected immediately and playback resumes from the new point.
        if (!isPlaying) {
            reset(actual.roundToInt())
            return actual.roundToInt()
        }

        if (!hasAnchor || lastFrameNanos == Long.MIN_VALUE || frameTimeNanos <= lastFrameNanos) {
            shownPositionMs = actual
            lastFrameNanos = frameTimeNanos
            hasAnchor = true
            return actual.roundToInt()
        }

        val elapsedMs = (frameTimeNanos - lastFrameNanos) / 1_000_000.0
        if (!elapsedMs.isFinite() || elapsedMs <= 0.0 || elapsedMs > maxFrameGapMs) {
            // A stalled frame clock must not be mistaken for elapsed audio time. Recover
            // from Media3's current playhead rather than animating through a hidden-screen gap.
            shownPositionMs = actual
            lastFrameNanos = frameTimeNanos
            return actual.roundToInt()
        }

        val speed = playbackSpeed.takeIf { it.isFinite() && it > 0f }?.toDouble() ?: 1.0
        val predicted = shownPositionMs + elapsedMs * speed
        val phaseError = actual - predicted

        shownPositionMs = if (!phaseError.isFinite() || abs(phaseError) >= discontinuityThresholdMs) {
            // Covers forward/backward seeks, decoder clock corrections and resumed playback.
            actual
        } else {
            // A stronger bounded phase correction converges small errors in a few frames
            // without turning millisecond-level Media3 quantization into visible jitter.
            predicted + (phaseError * correctionFactor)
                .coerceIn(-maxCorrectionPerFrameMs, maxCorrectionPerFrameMs)
        }

        lastFrameNanos = frameTimeNanos
        return shownPositionMs.roundToInt().coerceAtLeast(0)
    }
}
