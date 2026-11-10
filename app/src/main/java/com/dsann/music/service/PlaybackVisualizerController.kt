package com.dsann.music.service

import android.media.audiofx.Visualizer
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Lazy playback-session FFT capture for the full-screen visualizer.
 *
 * Prefer the platform capture callback. On OEM builds where the callback is accepted but
 * never dispatched, poll Visualizer.getFft() on a dedicated worker thread instead. This
 * avoids depending on vendor callback scheduling and never performs FFT reads on the UI thread.
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
        private const val OUTPUT_MIX_FALLBACK_DELAY_MS = 1_600L
        private const val POLL_INTERVAL_MS = 33L
    }

    private val _levels = MutableStateFlow(FloatArray(BAR_COUNT) { 0f })
    val levels: StateFlow<FloatArray> = _levels.asStateFlow()

    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    private var running = false
    @Volatile
    private var fftCallbackCount = 0
    @Volatile
    private var fftSignalFrameCount = 0
    @Volatile
    private var fftPollFrameCount = 0
    @Volatile
    private var fftPollSignalFrameCount = 0
    private var capture: Visualizer? = null
    private var activeSessionId = 0
    private var outputMixFallback: Runnable? = null
    private var pollingFallback: Runnable? = null
    private var pollingThread: HandlerThread? = null
    private var pollingHandler: Handler? = null
    private var pollingTask: Runnable? = null

    @Synchronized
    fun start(audioSessionId: Int): Boolean {
        stop()
        if (audioSessionId < 0) return false

        // Attach to the player's own session first. Some devices accept the effect but
        // never dispatch capture callbacks; poll getFft() as a fallback before switching
        // to the system output mix.
        val preferredSession = audioSessionId
        val started = startOnSession(preferredSession)
        if (!started) {
            if (preferredSession == 0) return false
            Log.w(TAG, "Player-session capture failed; trying output-mix session 0")
            return startOnSession(0)
        }

        val pollingFallbackTask = Runnable {
            val shouldPoll = synchronized(this) {
                running &&
                    activeSessionId == preferredSession &&
                    fftSignalFrameCount == 0 &&
                    pollingThread == null
            }
            if (shouldPoll) {
                val activeCapture = synchronized(this) {
                    capture?.takeIf { running && activeSessionId == preferredSession }
                }
                if (activeCapture != null) {
                    Log.w(TAG, "No callback FFT frames from session $preferredSession; enabling getFft() polling")
                    startPolling(activeCapture, preferredSession)
                }
            }
        }
        pollingFallback = pollingFallbackTask
        mainHandler.postDelayed(pollingFallbackTask, CALLBACK_GRACE_PERIOD_MS)

        if (preferredSession != 0) {
            val fallback = Runnable {
                val shouldFallback = synchronized(this) {
                    running &&
                        activeSessionId == preferredSession &&
                        fftSignalFrameCount == 0 &&
                        fftPollSignalFrameCount == 0
                }
                if (shouldFallback) {
                    Log.w(TAG, "No FFT frames on player session $preferredSession; switching to output mix")
                    start(0)
                }
            }
            outputMixFallback = fallback
            mainHandler.postDelayed(fallback, OUTPUT_MIX_FALLBACK_DELAY_MS)
        }
        return true
    }

    @Synchronized
    private fun startOnSession(audioSessionId: Int): Boolean {
        if (audioSessionId < 0) return false
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
                    if (!running || capture !== candidate || fft == null || fft.isEmpty()) return
                    fftCallbackCount++
                    if (containsAudioSignal(fft)) {
                        fftSignalFrameCount++
                        if (fftSignalFrameCount == 1) {
                            Log.d(TAG, "Received first non-silent callback FFT frame from session $audioSessionId (${fft.size} bytes)")
                        }
                    } else if (fftCallbackCount == 1) {
                        Log.w(TAG, "FFT callback is active but returned a silent frame for session $audioSessionId")
                    }
                    publishBands(fft)
                }
            }

            val listenerResult = candidate.setDataCaptureListener(listener, captureRate, false, true)
            if (listenerResult != Visualizer.SUCCESS) {
                Log.w(TAG, "Visualizer.setDataCaptureListener() failed: $listenerResult")
                candidate.release()
                return false
            }

            capture = candidate
            activeSessionId = audioSessionId
            fftCallbackCount = 0
            fftSignalFrameCount = 0
            fftPollFrameCount = 0
            fftPollSignalFrameCount = 0
            running = true

            val enableResult = candidate.setEnabled(true)
            if (enableResult != Visualizer.SUCCESS) {
                Log.w(TAG, "Visualizer.setEnabled(true) failed: $enableResult")
                running = false
                capture = null
                activeSessionId = 0
                candidate.release()
                return false
            }

            Log.d(TAG, "Visualizer capture enabled for audio session $audioSessionId at $captureRate mHz")
            true
        } catch (error: Exception) {
            Log.w(TAG, "Visualizer initialization failed for audio session $audioSessionId", error)
            running = false
            capture = null
            activeSessionId = 0
            runCatching { candidate.setEnabled(false) }
            runCatching { candidate.release() }
            publishLevels(FloatArray(BAR_COUNT) { 0f })
            false
        }
    }

    /**
     * Poll the same platform FFT buffer when an OEM accepts the capture listener but does
     * not invoke it. The worker exists only while the fullscreen visualizer is open.
     */
    @Synchronized
    private fun startPolling(candidate: Visualizer, sessionId: Int) {
        if (!running || capture !== candidate || pollingThread != null) return

        val thread = HandlerThread("QuantuneVisualizerFFT").apply { start() }
        val handler = Handler(thread.looper)
        val buffer = ByteArray(candidate.captureSize)

        val task = object : Runnable {
            override fun run() {
                if (!running || capture !== candidate || activeSessionId != sessionId) return

                // Stop polling only after the native callback delivers usable non-silent data.
                // Some OEM implementations call back with an all-zero FFT buffer.
                if (fftSignalFrameCount > 0) return

                try {
                    val result = candidate.getFft(buffer)
                    if (result == Visualizer.SUCCESS) {
                        fftPollFrameCount++
                        if (containsAudioSignal(buffer)) {
                            fftPollSignalFrameCount++
                            if (fftPollSignalFrameCount == 1) {
                                Log.d(TAG, "Received first non-silent polled FFT frame from session $sessionId (${buffer.size} bytes)")
                            }
                        } else if (fftPollFrameCount == 1) {
                            Log.w(TAG, "Polled FFT is silent on session $sessionId")
                        }
                        publishBands(buffer.copyOf())
                    } else if (fftPollFrameCount == 0) {
                        Log.w(TAG, "Visualizer.getFft() returned $result for session $sessionId")
                    }
                } catch (error: Exception) {
                    Log.w(TAG, "Visualizer FFT polling failed for session $sessionId", error)
                }

                if (running && capture === candidate && activeSessionId == sessionId &&
                    fftSignalFrameCount == 0
                ) {
                    handler.postDelayed(this, POLL_INTERVAL_MS)
                }
            }
        }

        pollingThread = thread
        pollingHandler = handler
        pollingTask = task
        handler.post(task)
    }

    /** Rebind to the new primary player after a crossfade handoff, if capture is active. */
    @Synchronized
    fun switchSession(audioSessionId: Int) {
        if (!running || audioSessionId <= 0) return
        start(audioSessionId)
    }

    @Synchronized
    fun stop() {
        running = false
        outputMixFallback?.let(mainHandler::removeCallbacks)
        outputMixFallback = null
        pollingFallback?.let(mainHandler::removeCallbacks)
        pollingFallback = null

        pollingTask?.let { task -> pollingHandler?.removeCallbacks(task) }
        pollingTask = null
        pollingHandler = null
        pollingThread?.quitSafely()
        pollingThread = null

        activeSessionId = 0
        fftCallbackCount = 0
        fftSignalFrameCount = 0
        fftPollFrameCount = 0
        fftPollSignalFrameCount = 0
        val oldCapture = capture
        capture = null
        if (oldCapture != null) {
            runCatching { oldCapture.setEnabled(false) }
            runCatching { oldCapture.setDataCaptureListener(null, 0, false, false) }
            runCatching { oldCapture.release() }
        }
        publishLevels(FloatArray(BAR_COUNT) { 0f })
    }

    private fun publishLevels(levels: FloatArray) {
        _levels.value = levels
        onLevelsChanged(levels)
    }

    private fun containsAudioSignal(fft: ByteArray): Boolean {
        // Ignore the DC bin and tiny byte noise. This distinguishes a usable playback
        // spectrum from OEM callback frames that technically arrive but carry no signal.
        for (index in 2 until fft.size) {
            if (kotlin.math.abs(fft[index].toInt()) > 2) return true
        }
        return false
    }

    private fun publishBands(fft: ByteArray) {
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
                val magnitude = sqrt(
                    (real * real + imaginary * imaginary).toFloat()
                )
                if (magnitude > peak) peak = magnitude
            }

            // The platform FFT is signed 8-bit complex data. Mild compression keeps
            // quieter playback visible without manufacturing movement during silence.
            val target = ((peak - 1f).coerceAtLeast(0f) / 64f)
                .coerceIn(0f, 1f)
                .pow(0.72f)
            val previous = oldLevels.getOrElse(bar) { 0f }
            newLevels[bar] = if (target >= previous) {
                previous * 0.22f + target * 0.78f
            } else {
                previous * 0.68f + target * 0.32f
            }
        }

        if (running) publishLevels(newLevels)
    }
}
