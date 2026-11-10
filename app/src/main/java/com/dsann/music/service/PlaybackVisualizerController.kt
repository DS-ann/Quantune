package com.dsann.music.service

import android.media.audiofx.Visualizer
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Lazy playback-session FFT capture for the full-screen visualizer.
 *
 * Visualizer creation, enabling, FFT polling, fallback switching and release all run on
 * a dedicated worker looper. This keeps vendor audio-effect calls off the UI thread.
 */
@Suppress("DEPRECATION")
class PlaybackVisualizerController(
    private val onLevelsChanged: (FloatArray) -> Unit = {}
) {

    companion object {
        private const val TAG = "PlaybackVisualizer"
        const val BAR_COUNT = 16
        private const val TARGET_CAPTURE_RATE_MILLI_HZ = 30_000
        private const val CALLBACK_GRACE_PERIOD_MS = 450L
        // The UI already waits 250 ms before requesting capture. If the player session
        // still has no usable signal after this window, try output mix instead of waiting
        // the previous 1.6 seconds. Devices with working player-session capture never switch.
        private const val OUTPUT_MIX_FALLBACK_DELAY_MS = 850L
        private const val POLL_INTERVAL_MS = 33L
    }

    private val _levels = MutableStateFlow(FloatArray(BAR_COUNT) { 0f })
    val levels: StateFlow<FloatArray> = _levels.asStateFlow()

    @Volatile
    private var activeRun: CaptureRun? = null

    private class CaptureRun(
        val thread: HandlerThread,
        val handler: Handler
    ) {
        @Volatile
        var active = true

        var capture: Visualizer? = null
        var activeSessionId = 0
        var fftCallbackCount = 0
        var fftSignalFrameCount = 0
        var fftPollFrameCount = 0
        var fftPollSignalFrameCount = 0
        var pollErrorLogged = false
        var pollingFallbackTask: Runnable? = null
        var outputMixFallbackTask: Runnable? = null
        var pollingTask: Runnable? = null
    }

    /**
     * Schedules capture on a dedicated HandlerThread and returns immediately.
     * The Boolean reports whether the request was accepted, not whether the native
     * Visualizer has finished initializing.
     */
    @Synchronized
    fun start(audioSessionId: Int): Boolean {
        if (audioSessionId < 0) return false

        stop()

        val thread = try {
            HandlerThread("QuantuneVisualizerCapture").apply { start() }
        } catch (error: Exception) {
            Log.w(TAG, "Unable to start visualizer capture thread", error)
            return false
        }
        val handler = Handler(thread.looper)
        val run = CaptureRun(thread, handler)
        activeRun = run

        if (!handler.post { startCapture(run, audioSessionId) }) {
            activeRun = null
            run.active = false
            thread.quitSafely()
            return false
        }
        return true
    }

    /**
     * Stop is non-blocking: native effect teardown is serialized on the capture looper,
     * never on the screen's main thread.
     */
    @Synchronized
    fun stop() {
        val run = activeRun
        activeRun = null

        if (run != null) {
            run.active = false
            run.handler.removeCallbacksAndMessages(null)
            val cleanupPosted = run.handler.post {
                releaseCurrentCapture(run)
                run.thread.quitSafely()
            }
            if (!cleanupPosted) {
                run.thread.quitSafely()
            }
        }

        publishEmptyLevels()
    }

    /** Rebind to the new primary player after a crossfade handoff, if capture is active. */
    @Synchronized
    fun switchSession(audioSessionId: Int) {
        if (activeRun?.active != true || audioSessionId <= 0) return
        start(audioSessionId)
    }

    private fun isCurrent(run: CaptureRun): Boolean =
        run.active && activeRun === run

    private fun startCapture(run: CaptureRun, preferredSessionId: Int) {
        if (!isCurrent(run)) return

        if (!startOnSession(run, preferredSessionId)) {
            if (preferredSessionId == 0) return
            Log.w(TAG, "Player-session capture failed; trying output-mix session 0")
            if (startOnSession(run, 0)) {
                schedulePollingFallback(run, 0)
            }
            return
        }

        schedulePollingFallback(run, preferredSessionId)
        if (preferredSessionId != 0) {
            val fallback = Runnable {
                if (!isCurrent(run) || run.activeSessionId != preferredSessionId) return@Runnable
                run.outputMixFallbackTask = null
                if (run.fftSignalFrameCount == 0 && run.fftPollSignalFrameCount == 0) {
                    Log.w(
                        TAG,
                        "No usable FFT signal from player session $preferredSessionId " +
                            "after ${OUTPUT_MIX_FALLBACK_DELAY_MS}ms; switching to output mix"
                    )
                    switchToOutputMix(run)
                }
            }
            run.outputMixFallbackTask = fallback
            run.handler.postDelayed(fallback, OUTPUT_MIX_FALLBACK_DELAY_MS)
        }
    }

    private fun startOnSession(run: CaptureRun, audioSessionId: Int): Boolean {
        if (!isCurrent(run) || audioSessionId < 0) return false

        val candidate = try {
            Visualizer(audioSessionId)
        } catch (error: Exception) {
            Log.w(TAG, "Unable to create Visualizer for audio session $audioSessionId", error)
            return false
        }

        return try {
            val range = Visualizer.getCaptureSizeRange()
            if (range.size < 2 || range[0] <= 0 || range[1] < range[0]) {
                Log.w(TAG, "Invalid Visualizer capture-size range: ${range.contentToString()}")
                candidate.release()
                return false
            }

            val requestedSize = minOf(1024, range[1])
            val powerOfTwoSize = Integer.highestOneBit(requestedSize)
                .coerceAtLeast(range[0])
            val captureSizeResult = candidate.setCaptureSize(powerOfTwoSize)
            if (captureSizeResult != Visualizer.SUCCESS) {
                Log.w(TAG, "Visualizer.setCaptureSize($powerOfTwoSize) failed: $captureSizeResult")
                candidate.release()
                return false
            }

            candidate.setScalingMode(Visualizer.SCALING_MODE_NORMALIZED)
            val captureRate = minOf(TARGET_CAPTURE_RATE_MILLI_HZ, Visualizer.getMaxCaptureRate())
                .coerceAtLeast(1)

            val listener = object : Visualizer.OnDataCaptureListener {
                override fun onWaveFormDataCapture(
                    visualizer: Visualizer?,
                    waveform: ByteArray?,
                    samplingRate: Int
                ) = Unit

                override fun onFftDataCapture(
                    visualizer: Visualizer?,
                    fft: ByteArray?,
                    samplingRate: Int
                ) {
                    if (!isCurrent(run) || run.capture !== candidate || fft == null || fft.isEmpty()) {
                        return
                    }

                    run.fftCallbackCount++
                    if (containsAudioSignal(fft)) {
                        run.fftSignalFrameCount++
                        if (run.fftSignalFrameCount == 1) {
                            Log.d(
                                TAG,
                                "Received first non-silent callback FFT frame from " +
                                    "session $audioSessionId (${fft.size} bytes)"
                            )
                        }
                        cancelOutputMixFallback(run)
                    } else if (run.fftCallbackCount == 1) {
                        Log.w(TAG, "FFT callback is active but returned a silent frame for session $audioSessionId")
                    }
                    publishBands(run, fft)
                }
            }

            val listenerResult = candidate.setDataCaptureListener(listener, captureRate, false, true)
            if (listenerResult != Visualizer.SUCCESS) {
                Log.w(TAG, "Visualizer.setDataCaptureListener() failed: $listenerResult")
                candidate.release()
                return false
            }

            run.capture = candidate
            run.activeSessionId = audioSessionId
            resetSignalCounters(run)

            val enableResult = candidate.setEnabled(true)
            if (enableResult != Visualizer.SUCCESS) {
                Log.w(TAG, "Visualizer.setEnabled(true) failed: $enableResult")
                run.capture = null
                run.activeSessionId = 0
                candidate.release()
                return false
            }

            Log.d(TAG, "Visualizer capture enabled for audio session $audioSessionId at $captureRate mHz")
            true
        } catch (error: Exception) {
            Log.w(TAG, "Visualizer initialization failed for audio session $audioSessionId", error)
            if (run.capture === candidate) {
                run.capture = null
                run.activeSessionId = 0
            }
            runCatching { candidate.setEnabled(false) }
            runCatching { candidate.release() }
            false
        }
    }

    /**
     * Poll the platform FFT buffer on the capture worker when an OEM accepts the
     * callback listener but does not dispatch useful callback data.
     */
    private fun schedulePollingFallback(run: CaptureRun, sessionId: Int) {
        val fallback = Runnable {
            if (!isCurrent(run) ||
                run.activeSessionId != sessionId ||
                run.fftSignalFrameCount > 0 ||
                run.pollingTask != null
            ) {
                return@Runnable
            }

            val activeCapture = run.capture ?: return@Runnable
            Log.w(TAG, "No callback FFT frames from session $sessionId; enabling getFft() polling")
            startPolling(run, activeCapture, sessionId)
        }
        run.pollingFallbackTask = fallback
        run.handler.postDelayed(fallback, CALLBACK_GRACE_PERIOD_MS)
    }

    private fun startPolling(run: CaptureRun, candidate: Visualizer, sessionId: Int) {
        if (!isCurrent(run) || run.capture !== candidate || run.pollingTask != null) return

        val buffer = ByteArray(candidate.captureSize)
        val task = object : Runnable {
            override fun run() {
                if (!isCurrent(run) || run.capture !== candidate || run.activeSessionId != sessionId) {
                    run.pollingTask = null
                    return
                }

                // Native callback data takes priority as soon as it becomes usable.
                if (run.fftSignalFrameCount > 0) {
                    run.pollingTask = null
                    return
                }

                try {
                    val result = candidate.getFft(buffer)
                    if (result == Visualizer.SUCCESS) {
                        run.fftPollFrameCount++
                        if (containsAudioSignal(buffer)) {
                            run.fftPollSignalFrameCount++
                            if (run.fftPollSignalFrameCount == 1) {
                                Log.d(
                                    TAG,
                                    "Received first non-silent polled FFT frame from " +
                                        "session $sessionId (${buffer.size} bytes)"
                                )
                            }
                            cancelOutputMixFallback(run)
                        } else if (run.fftPollFrameCount == 1) {
                            Log.w(TAG, "Polled FFT is silent on session $sessionId")
                        }
                        publishBands(run, buffer.copyOf())
                    } else if (run.fftPollFrameCount == 0 && !run.pollErrorLogged) {
                        run.pollErrorLogged = true
                        Log.w(TAG, "Visualizer.getFft() returned $result for session $sessionId")
                    }
                } catch (error: Exception) {
                    if (!run.pollErrorLogged) {
                        run.pollErrorLogged = true
                        Log.w(TAG, "Visualizer FFT polling failed for session $sessionId", error)
                    }
                }

                if (isCurrent(run) && run.capture === candidate &&
                    run.activeSessionId == sessionId && run.fftSignalFrameCount == 0
                ) {
                    run.handler.postDelayed(this, POLL_INTERVAL_MS)
                } else {
                    run.pollingTask = null
                }
            }
        }

        run.pollingTask = task
        run.handler.post(task)
    }

    private fun switchToOutputMix(run: CaptureRun) {
        if (!isCurrent(run)) return
        releaseCurrentCapture(run)
        if (!isCurrent(run)) return

        if (startOnSession(run, 0)) {
            schedulePollingFallback(run, 0)
        } else {
            Log.w(TAG, "Output-mix Visualizer session could not be started")
            publishEmptyLevels()
        }
    }

    private fun cancelOutputMixFallback(run: CaptureRun) {
        run.outputMixFallbackTask?.let(run.handler::removeCallbacks)
        run.outputMixFallbackTask = null
    }

    private fun resetSignalCounters(run: CaptureRun) {
        run.fftCallbackCount = 0
        run.fftSignalFrameCount = 0
        run.fftPollFrameCount = 0
        run.fftPollSignalFrameCount = 0
        run.pollErrorLogged = false
    }

    private fun releaseCurrentCapture(run: CaptureRun) {
        run.pollingFallbackTask?.let(run.handler::removeCallbacks)
        run.pollingFallbackTask = null
        cancelOutputMixFallback(run)
        run.pollingTask?.let(run.handler::removeCallbacks)
        run.pollingTask = null

        val oldCapture = run.capture
        run.capture = null
        run.activeSessionId = 0
        resetSignalCounters(run)
        if (oldCapture != null) {
            runCatching { oldCapture.setEnabled(false) }
            runCatching { oldCapture.setDataCaptureListener(null, 0, false, false) }
            runCatching { oldCapture.release() }
        }
    }

    private fun containsAudioSignal(fft: ByteArray): Boolean {
        // Ignore the DC bin and tiny byte noise. This distinguishes usable playback
        // spectrum data from OEM callback frames that technically arrive but carry no signal.
        for (index in 2 until fft.size) {
            if (kotlin.math.abs(fft[index].toInt()) > 2) return true
        }
        return false
    }

    private fun publishBands(run: CaptureRun, fft: ByteArray) {
        val pointCount = fft.size / 2
        if (pointCount <= 2) return

        val firstBin = 1
        val lastBin = (pointCount - 1).coerceAtLeast(firstBin + 1)
        val logRange = ln(lastBin.toFloat() / firstBin.toFloat())
        val oldLevels = _levels.value
        val newLevels = FloatArray(BAR_COUNT)

        for (bar in 0 until BAR_COUNT) {
            val startFraction = bar.toFloat() / BAR_COUNT
            val endFraction = (bar + 1).toFloat() / BAR_COUNT
            val startBin = max(
                firstBin,
                (firstBin * kotlin.math.exp(logRange * startFraction)).toInt()
            )
            val endBin = max(
                startBin + 1,
                (firstBin * kotlin.math.exp(logRange * endFraction)).toInt()
            ).coerceAtMost(lastBin + 1)

            var peak = 0f
            for (bin in startBin until endBin) {
                val offset = bin * 2
                if (offset + 1 >= fft.size) break
                val real = fft[offset].toInt()
                val imaginary = fft[offset + 1].toInt()
                val magnitude = sqrt((real * real + imaginary * imaginary).toFloat())
                if (magnitude > peak) peak = magnitude
            }

            // Use a linear fixed range so loud playback can reach the top without
            // compressing the upper half of the meter. Values around 64 and above fill it.
            val noiseFloor = 4f
            val nearFullScale = 64f
            val target = ((peak - noiseFloor) / (nearFullScale - noiseFloor))
                .coerceIn(0f, 1f)
            val previous = oldLevels.getOrElse(bar) { 0f }
            newLevels[bar] = if (target >= previous) {
                previous * 0.22f + target * 0.78f
            } else {
                previous * 0.68f + target * 0.32f
            }
        }

        if (isCurrent(run)) publishLevels(newLevels)
    }

    private fun publishLevels(levels: FloatArray) {
        _levels.value = levels
        onLevelsChanged(levels)
    }

    private fun publishEmptyLevels() {
        publishLevels(FloatArray(BAR_COUNT) { 0f })
    }
}
