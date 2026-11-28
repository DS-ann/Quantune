package com.dsann.music

import android.app.Activity
import android.app.ActivityManager
import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import androidx.core.os.HandlerCompat
import android.os.Looper
import android.os.Process
import android.os.Debug
import android.os.HandlerThread
import android.os.SystemClock
import android.util.Log
import android.view.FrameMetrics
import android.view.Window
import java.io.File
import java.io.FileOutputStream
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.lang.ref.WeakReference
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.system.exitProcess

internal data class DiagnosticReport(
    val type: String,
    val summary: String,
    val timestamp: String,
    val fullText: String
) {
    val isAnr: Boolean
        get() = type.contains("ANR", ignoreCase = true)
}

/**
 * Small, offline-first diagnostic recorder.
 *
 * Fatal exceptions are written before the process is handed back to Android's
 * default uncaught-exception handler. ANRs are detected by a lightweight main
 * looper heartbeat and saved with a snapshot of the process's thread stacks.
 */
internal object CrashDiagnostics {
    const val EXTRA_SKIP_RECOVERY = "com.dsann.music.EXTRA_SKIP_DIAGNOSTIC_RECOVERY"

    private const val TAG = "QuantuneDiagnostics"
    private const val REPORT_DIRECTORY = "diagnostics"
    private const val REPORT_FILE_NAME = "latest-report.txt"
    private const val REPORT_TEMP_NAME = "latest-report.tmp"
    private const val ANR_TIMEOUT_MS = 5_000L
    private const val HEARTBEAT_INTERVAL_MS = 500L
    private const val WATCHDOG_POLL_INTERVAL_MS = 500L
    private const val MAX_REPORT_CHARS = 900_000

    private val installed = AtomicBoolean(false)
    private val anrAlreadyRecorded = AtomicBoolean(false)
    private val fileLock = Any()
    private val lyricsMetricsLock = Any()

    private val lyricsDrawCount = AtomicLong()
    private val lyricsDrawTotalNanos = AtomicLong()
    private val lyricsDrawMaxNanos = AtomicLong()
    private val lyricsLastDrawElapsedNanos = AtomicLong()
    private val lyricsTextLayoutCount = AtomicLong()
    private val lyricsMaxTextLength = AtomicInteger()
    private val lyricsMaxLayoutLines = AtomicInteger()
    private val windowFrameCount = AtomicLong()
    private val windowFrameDropReports = AtomicLong()
    private val windowSlowFrameCount = AtomicLong()
    private val windowJankyFrameCount = AtomicLong()
    private val windowMaxTotalNanos = AtomicLong()
    private val windowMaxDrawNanos = AtomicLong()
    private val windowMaxLayoutNanos = AtomicLong()
    private val windowMaxSyncNanos = AtomicLong()
    private val windowMaxCommandNanos = AtomicLong()
    private val windowMaxSwapNanos = AtomicLong()
    private val windowMaxGpuNanos = AtomicLong()
    private val windowLastTotalNanos = AtomicLong()
    private val windowLastDrawNanos = AtomicLong()
    private val windowLastGpuNanos = AtomicLong()
    @Volatile private var windowGpuMetricAvailable = false
    @Volatile private var windowRefreshRateHz = 0f
    @Volatile private var frameMetricsWindow: Window? = null
    @Volatile private var frameMetricsThread: HandlerThread? = null
    @Volatile private var frameMetricsListener: Window.OnFrameMetricsAvailableListener? = null
    @Volatile private var activeActivityReference: WeakReference<Activity>? = null
    @Volatile private var anrWatchdogDetails: String = "Not available"

    @Volatile private var activeActivityClass: String = "Unknown"
    @Volatile private var baseScreenContext: String = "Unknown"
    @Volatile private var overlayScreenContext: String? = null
    @Volatile private var uiDiagnosticDetails: String = "Not available"
    @Volatile private var lyricsRenderingDetails: String = "Not available"
    @Volatile private var lyricsPreparationDetails: String = "Not started"

    /** Called from the root Compose navigation host; contains labels only, never song data. */
    fun updateBaseScreen(screen: String) {
        baseScreenContext = sanitizeDiagnosticValue(screen)
    }

    /** Tracks transient full-screen UI layers such as the Lyrics overlay. */
    fun updateOverlayScreen(screen: String?) {
        overlayScreenContext = screen?.let(::sanitizeDiagnosticValue)
    }

    /** Records compact UI state useful for reproducing a rendering-related ANR. */
    fun updateUiDetails(details: String) {
        uiDiagnosticDetails = sanitizeDiagnosticValue(details)
    }

    /** Records the actual lyrics drawing path selected for this device. */
    fun updateLyricsRendering(details: String) {
        lyricsRenderingDetails = sanitizeDiagnosticValue(details)
    }

    /** Tracks whether synced lyric preprocessing started, completed, or was cancelled. */
    fun updateLyricsPreparation(details: String) {
        lyricsPreparationDetails = sanitizeDiagnosticValue(details)
    }

    fun recordLyricsDraw(durationNanos: Long) {
        if (durationNanos <= 0L) return
        lyricsDrawCount.incrementAndGet()
        lyricsDrawTotalNanos.addAndGet(durationNanos)
        updateMaximum(lyricsDrawMaxNanos, durationNanos)
        lyricsLastDrawElapsedNanos.set(SystemClock.elapsedRealtimeNanos())
    }

    fun recordLyricsTextLayout(textLength: Int, lineCount: Int) {
        lyricsTextLayoutCount.incrementAndGet()
        updateMaximum(lyricsMaxTextLength, textLength.coerceAtLeast(0))
        updateMaximum(lyricsMaxLayoutLines, lineCount.coerceAtLeast(0))
    }

    private fun updateMaximum(target: AtomicLong, value: Long) {
        var previous = target.get()
        while (value > previous) {
            if (target.compareAndSet(previous, value)) return
            previous = target.get()
        }
    }

    private fun updateMaximum(target: AtomicInteger, value: Int) {
        var previous = target.get()
        while (value > previous) {
            if (target.compareAndSet(previous, value)) return
            previous = target.get()
        }
    }

    private fun resetLyricsMetrics() {
        lyricsPreparationDetails = "Not started"
        lyricsDrawCount.set(0L); lyricsDrawTotalNanos.set(0L); lyricsDrawMaxNanos.set(0L)
        lyricsLastDrawElapsedNanos.set(0L); lyricsTextLayoutCount.set(0L)
        lyricsMaxTextLength.set(0); lyricsMaxLayoutLines.set(0)
        windowFrameCount.set(0L); windowFrameDropReports.set(0L)
        windowSlowFrameCount.set(0L); windowJankyFrameCount.set(0L)
        windowMaxTotalNanos.set(0L); windowMaxDrawNanos.set(0L)
        windowMaxLayoutNanos.set(0L); windowMaxSyncNanos.set(0L)
        windowMaxCommandNanos.set(0L); windowMaxSwapNanos.set(0L)
        windowMaxGpuNanos.set(0L); windowLastTotalNanos.set(0L)
        windowLastDrawNanos.set(0L); windowLastGpuNanos.set(0L)
        windowGpuMetricAvailable = false; windowRefreshRateHz = 0f
    }

    fun startLyricsWindowFrameMetrics(window: Window?) {
        stopLyricsWindowFrameMetrics()
        resetLyricsMetrics()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N || window == null) return

        val thread = HandlerThread("Quantune-Lyrics-FrameMetrics").apply { start() }
        val refreshRate = runCatching { window.decorView.display?.refreshRate ?: 0f }.getOrDefault(0f)
        windowRefreshRateHz = refreshRate
        val listener = Window.OnFrameMetricsAvailableListener { _, metrics, droppedReports ->
            runCatching {
                val total = metrics.getMetric(FrameMetrics.TOTAL_DURATION).coerceAtLeast(0L)
                val draw = metrics.getMetric(FrameMetrics.DRAW_DURATION).coerceAtLeast(0L)
                val layout = metrics.getMetric(FrameMetrics.LAYOUT_MEASURE_DURATION).coerceAtLeast(0L)
                val sync = metrics.getMetric(FrameMetrics.SYNC_DURATION).coerceAtLeast(0L)
                val command = metrics.getMetric(FrameMetrics.COMMAND_ISSUE_DURATION).coerceAtLeast(0L)
                val swap = metrics.getMetric(FrameMetrics.SWAP_BUFFERS_DURATION).coerceAtLeast(0L)
                val gpu = if (Build.VERSION.SDK_INT >= 31) {
                    windowGpuMetricAvailable = true
                    metrics.getMetric(FrameMetrics.GPU_DURATION).coerceAtLeast(0L)
                } else 0L
                windowFrameCount.incrementAndGet()
                if (droppedReports > 0) windowFrameDropReports.addAndGet(droppedReports.toLong())
                val budget = if (refreshRate > 1f) (2_000_000_000.0 / refreshRate).toLong() else 33_333_333L
                if (total > budget) windowSlowFrameCount.incrementAndGet()
                if (total >= 50_000_000L) windowJankyFrameCount.incrementAndGet()
                updateMaximum(windowMaxTotalNanos, total); updateMaximum(windowMaxDrawNanos, draw)
                updateMaximum(windowMaxLayoutNanos, layout); updateMaximum(windowMaxSyncNanos, sync)
                updateMaximum(windowMaxCommandNanos, command); updateMaximum(windowMaxSwapNanos, swap)
                if (gpu > 0L) updateMaximum(windowMaxGpuNanos, gpu)
                windowLastTotalNanos.set(total); windowLastDrawNanos.set(draw); windowLastGpuNanos.set(gpu)
            }
        }
        synchronized(lyricsMetricsLock) {
            frameMetricsWindow = window; frameMetricsThread = thread; frameMetricsListener = listener
            runCatching {
                window.addOnFrameMetricsAvailableListener(listener, Handler(thread.looper))
            }.onFailure {
                frameMetricsWindow = null; frameMetricsThread = null; frameMetricsListener = null
                thread.quitSafely()
                Log.w(TAG, "Could not attach Window FrameMetrics listener", it)
            }
        }
    }

    fun stopLyricsWindowFrameMetrics(window: Window? = null) {
        val oldWindow: Window?
        val oldListener: Window.OnFrameMetricsAvailableListener?
        val oldThread: HandlerThread?
        synchronized(lyricsMetricsLock) {
            oldWindow = frameMetricsWindow; oldListener = frameMetricsListener; oldThread = frameMetricsThread
            frameMetricsWindow = null; frameMetricsListener = null; frameMetricsThread = null
        }
        if (oldWindow != null && oldListener != null) {
            runCatching { oldWindow.removeOnFrameMetricsAvailableListener(oldListener) }
        }
        oldThread?.quitSafely()
    }

    private fun sanitizeDiagnosticValue(value: String): String =
        value.replace('\n', ' ').replace('\r', ' ').take(600)

    private fun currentScreenContext(): String = buildString {
        append(baseScreenContext)
        overlayScreenContext?.takeIf { it.isNotBlank() }?.let {
            append(" | Overlay: ")
            append(it)
        }
    }

    fun install(application: Application) {
        if (!installed.compareAndSet(false, true)) return

        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val trace = StringWriter().also { writer ->
                    throwable.printStackTrace(PrintWriter(writer))
                }.toString()
                val summary = "${throwable.javaClass.name}: ${throwable.message.orEmpty()}"
                val report = buildReport(
                    context = application,
                    type = "FATAL CRASH",
                    summary = summary,
                    threadName = thread.name,
                    body = trace
                )
                persistReport(application, report)
            } catch (reportError: Throwable) {
                Log.e(TAG, "Could not persist fatal crash report", reportError)
            }

            if (previousHandler != null) {
                previousHandler.uncaughtException(thread, throwable)
            } else {
                Process.killProcess(Process.myPid())
                exitProcess(10)
            }
        }

        startAnrWatchdog(application)
    }

    fun hasPendingReport(context: Context): Boolean {
        val file = reportFile(context)
        return file.isFile && file.length() > 0L
    }

    fun readPendingReport(context: Context): DiagnosticReport? {
        return try {
            val file = reportFile(context)
            if (!file.isFile || file.length() <= 0L) return null

            val fullText = file.readText(Charsets.UTF_8)
            fun field(name: String): String {
                return fullText.lineSequence()
                    .firstOrNull { it.startsWith("$name:") }
                    ?.substringAfter(":")
                    ?.trim()
                    .orEmpty()
            }

            DiagnosticReport(
                type = field("Type").ifBlank { "APP ERROR" },
                summary = field("Summary").ifBlank { "The app stopped unexpectedly." },
                timestamp = field("Time").ifBlank { "Unknown time" },
                fullText = fullText
            )
        } catch (error: Exception) {
            Log.e(TAG, "Could not read diagnostic report", error)
            null
        }
    }

    fun clearPendingReport(context: Context) {
        synchronized(fileLock) {
            runCatching { reportFile(context).delete() }
            runCatching { File(reportDirectory(context), REPORT_TEMP_NAME).delete() }
        }
        anrAlreadyRecorded.set(false)
    }

    private fun startAnrWatchdog(application: Application) {
        // An asynchronous message bypasses synchronization barriers in the main
        // MessageQueue, so animation/frame barriers cannot make a healthy UI look stuck.
        val mainHandler = HandlerCompat.createAsync(Looper.getMainLooper())
        val startedActivities = AtomicInteger(0)
        // Handler.postDelayed uses uptimeMillis. Use the same clock for the heartbeat.
        val lastMainResponse = java.util.concurrent.atomic.AtomicLong(SystemClock.uptimeMillis())
        val lastProcessCpuTime = AtomicLong(Process.getElapsedCpuTime())
        val lastPollCpuDelta = AtomicLong(0L)

        // Android may suspend/freeze a cached background process. A heartbeat gap while
        // no Quantune Activity is visible is not a user-facing main-thread ANR.
        application.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityStarted(activity: Activity) {
                startedActivities.incrementAndGet()
                activeActivityReference = WeakReference(activity)
                activeActivityClass = activity.javaClass.name
                // Give the UI a fresh response window when it becomes visible again.
                lastMainResponse.set(SystemClock.uptimeMillis())
            }

            override fun onActivityStopped(activity: Activity) {
                val remaining = startedActivities.decrementAndGet()
                if (activeActivityReference?.get() === activity) activeActivityReference = null
                if (remaining <= 0) {
                    startedActivities.set(0)
                    activeActivityClass = "None (background)"
                    lastMainResponse.set(SystemClock.uptimeMillis())
                }
            }

            override fun onActivityCreated(activity: Activity, savedInstanceState: android.os.Bundle?) = Unit
            override fun onActivityResumed(activity: Activity) = Unit
            override fun onActivityPaused(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, outState: android.os.Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
        })

        val heartbeat = object : Runnable {
            override fun run() {
                lastMainResponse.set(SystemClock.uptimeMillis())
                mainHandler.postDelayed(this, HEARTBEAT_INTERVAL_MS)
            }
        }
        mainHandler.post(heartbeat)

        Thread({
            while (!Thread.currentThread().isInterrupted) {
                try {
                    Thread.sleep(WATCHDOG_POLL_INTERVAL_MS)
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                    break
                }

                // Sample process CPU at watchdog cadence to separate active CPU work
                // from a mostly blocked main/UI thread.
                val cpuNow = Process.getElapsedCpuTime()
                val cpuBefore = lastProcessCpuTime.getAndSet(cpuNow)
                lastPollCpuDelta.set((cpuNow - cpuBefore).coerceAtLeast(0L))

                // Reset the baseline while backgrounded; cached/frozen time must not
                // accumulate into a false ANR when Android schedules the process again.
                if (startedActivities.get() <= 0) {
                    lastMainResponse.set(SystemClock.uptimeMillis())
                    continue
                }
                if (hasPendingReport(application)) continue

                val now = SystemClock.uptimeMillis()
                val blockedForMs = now - lastMainResponse.get()
                if (blockedForMs < ANR_TIMEOUT_MS) continue
                if (!anrAlreadyRecorded.compareAndSet(false, true)) continue

                try {
                    val mainThread = Thread.getAllStackTraces().keys
                        .firstOrNull { it.name == "main" }
                    // Capture all threads, then sample the main and graphics threads
                    // again to see whether a native renderer wait persists.
                    val stackDump = buildString {
                        append(captureAllThreadStacks())
                        appendLine()
                        append(captureCriticalThreadSamples())
                    }
                    anrWatchdogDetails =
                        "blockedFor=${blockedForMs}ms; watchdogPoll=${WATCHDOG_POLL_INTERVAL_MS}ms; " +
                            "visibleActivities=${startedActivities.get()}; processCpuLastPoll=${lastPollCpuDelta.get()}ms"
                    val report = buildReport(
                        context = application,
                        type = "ANR — APP NOT RESPONDING",
                        summary = "The main thread missed its asynchronous heartbeat for approximately $blockedForMs ms while Quantune was visible. Process CPU consumed in the last watchdog poll: ${lastPollCpuDelta.get()} ms.",
                        threadName = mainThread?.name ?: "main",
                        body = buildString {
                            appendLine(stackDump)
                            appendLine()
                            append(captureNativeThreadInventory())
                        }
                    )
                    persistReport(application, report)

                    // Show the report only if Quantune is still visible when the main
                    // looper handles this callback. Otherwise MainActivity routes to it
                    // on the user's next launch.
                    mainHandler.post {
                        if (startedActivities.get() <= 0) return@post
                        try {
                            application.startActivity(
                                Intent(application, CrashReportActivity::class.java).apply {
                                    addFlags(
                                        Intent.FLAG_ACTIVITY_NEW_TASK or
                                            Intent.FLAG_ACTIVITY_CLEAR_TOP or
                                            Intent.FLAG_ACTIVITY_SINGLE_TOP
                                    )
                                }
                            )
                        } catch (launchError: Exception) {
                            Log.e(TAG, "Could not open the ANR report screen", launchError)
                        }
                    }
                } catch (error: Throwable) {
                    Log.e(TAG, "ANR report capture failed", error)
                }
            }
        }, "Quantune-ANR-Watchdog").apply {
            isDaemon = true
            start()
        }
    }

    private fun isGraphicsThread(thread: Thread): Boolean {
        val name = thread.name.lowercase(Locale.US)
        return name.contains("render") ||
            name.contains("hwui") ||
            name.contains("gpu") ||
            name.contains("glthread") ||
            name.contains("graphics") ||
            name.contains("crrenderer")
    }

    private fun threadDiagnosticPriority(thread: Thread): Int {
        val name = thread.name.lowercase(Locale.US)
        return when {
            name == "main" -> 0
            isGraphicsThread(thread) -> 1
            name.contains("binder") -> 2
            name.contains("audio") || name.contains("media") || name.contains("exo") -> 3
            name.contains("okhttp") || name.contains("okio") -> 5
            else -> 4
        }
    }

    private fun appendThreadSnapshot(
        output: StringBuilder,
        thread: Thread,
        stack: Array<StackTraceElement>
    ) {
        output.appendLine()
        output.appendLine(
            "===== THREAD: ${thread.name} | STATE: ${thread.state} | " +
                "ID: ${thread.id} | PRIORITY: ${thread.priority} ====="
        )
        stack.take(120).forEach { frame -> output.appendLine("\tat $frame") }
        if (stack.size > 120) {
            output.appendLine("... ${stack.size - 120} additional frames omitted.")
        }
    }

    private fun captureAllThreadStacks(): String {
        val entries = Thread.getAllStackTraces().entries
            .sortedWith(
                compareBy<Map.Entry<Thread, Array<StackTraceElement>>> {
                    threadDiagnosticPriority(it.key)
                }.thenBy { it.key.name }
            )
            .take(96)

        return buildString {
            appendLine(
                "Thread stacks are ordered with main first, followed by graphics, " +
                    "Binder, media and remaining workers. Up to 96 Java threads are included."
            )
            val graphicsFound = entries.any { isGraphicsThread(it.key) }
            appendLine("Java graphics/render thread present in this snapshot: $graphicsFound")
            entries.forEach { entry ->
                appendThreadSnapshot(this, entry.key, entry.value)
            }
        }
    }

    /**
     * Sample the main and graphics threads three times. Repeated identical native
     * frames help identify a persistent RenderThread/driver synchronization wait.
     */
    private fun captureCriticalThreadSamples(): String = buildString {
        for (sampleIndex in 0 until 5) {
            val stacks = Thread.getAllStackTraces()
            val critical = stacks.entries
                .filter { it.key.name == "main" || isGraphicsThread(it.key) }
                .sortedWith(
                    compareBy<Map.Entry<Thread, Array<StackTraceElement>>> {
                        if (it.key.name == "main") 0 else 1
                    }.thenBy { it.key.name }
                )

            appendLine()
            appendLine(
                "===== MAIN / GRAPHICS THREAD SAMPLE ${sampleIndex + 1}/5 " +
                    "(approximately ${sampleIndex * 180} ms after detection) ====="
            )
            if (critical.isEmpty()) {
                appendLine("No main or Java graphics thread stack was available.")
            } else {
                critical.take(24).forEach { entry ->
                    appendThreadSnapshot(this, entry.key, entry.value)
                }
            }

            if (sampleIndex < 4) {
                try {
                    Thread.sleep(180L)
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                    return@buildString
                }
            }
        }
    }

    private fun captureNativeThreadInventory(): String = buildString {
        val taskDirectory = File("/proc/self/task")
        val tasks = runCatching { taskDirectory.listFiles()?.sortedBy { it.name.toLongOrNull() ?: Long.MAX_VALUE } }
            .getOrNull().orEmpty()
        appendLine("===== NATIVE PROCESS THREAD INVENTORY =====")
        appendLine("Linux task count: ${tasks.size}; details are read from /proc/self/task.")
        tasks.take(128).forEach { task ->
            val tid = task.name
            val name = runCatching { File(task, "comm").readText().trim() }.getOrDefault("unavailable")
            val stat = runCatching { File(task, "stat").readText() }.getOrDefault("")
            val closeParen = stat.lastIndexOf(')')
            val state = if (closeParen >= 0) stat.substring(closeParen + 1).trim().firstOrNull()?.toString()
                ?: "unknown" else "unknown"
            appendLine("tid=$tid; name=$name; state=$state")
        }
        if (tasks.size > 128) appendLine("... ${tasks.size - 128} further native thread names omitted.")
    }

    private fun appendRuntimeDiagnostics(output: StringBuilder, context: Context) {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val memoryInfo = ActivityManager.MemoryInfo()
        val memoryAvailable = runCatching {
            activityManager?.getMemoryInfo(memoryInfo)
            activityManager != null
        }.getOrDefault(false)
        val runtime = Runtime.getRuntime()
        val heapUsed = (runtime.totalMemory() - runtime.freeMemory()).coerceAtLeast(0L)
        fun mib(bytes: Long): String = String.format(Locale.US, "%.1f MiB", bytes.toDouble() / (1024.0 * 1024.0))
        fun ms(nanos: Long): String = String.format(Locale.US, "%.2f ms", nanos / 1_000_000.0)

        output.appendLine("Activity: $activeActivityClass")
        output.appendLine("UI context: ${currentScreenContext()}")
        output.appendLine("UI details: $uiDiagnosticDetails")
        output.appendLine("Lyrics rendering details: $lyricsRenderingDetails")
        output.appendLine("Lyrics preparation: $lyricsPreparationDetails")
        output.appendLine("ANR watchdog details: $anrWatchdogDetails")
        output.appendLine("Device hardware: ${Build.HARDWARE}; board: ${Build.BOARD}; device code: ${Build.DEVICE}")
        if (Build.VERSION.SDK_INT >= 31) {
            output.appendLine("Device SoC model: ${runCatching { Build.SOC_MODEL }.getOrDefault("unavailable")}")
        }
        if (memoryAvailable) {
            output.appendLine(
                "System RAM: total=${mib(memoryInfo.totalMem)}, available=${mib(memoryInfo.availMem)}, " +
                    "threshold=${mib(memoryInfo.threshold)}, lowMemory=${memoryInfo.lowMemory}"
            )
            output.appendLine("System low-RAM device flag: ${activityManager?.isLowRamDevice ?: "unknown"}")
        } else output.appendLine("System RAM snapshot: unavailable")

        output.appendLine(
            "App Java heap: used=${mib(heapUsed)}, allocated=${mib(runtime.totalMemory())}, " +
                "free=${mib(runtime.freeMemory())}, max=${mib(runtime.maxMemory())}"
        )
        val processMemory = Debug.MemoryInfo()
        val processMemoryAvailable = runCatching { Debug.getMemoryInfo(processMemory); true }.getOrDefault(false)
        if (processMemoryAvailable) {
            output.appendLine(
                "App process PSS: total=${processMemory.totalPss} KiB, dalvik=${processMemory.dalvikPss} KiB, " +
                    "native=${processMemory.nativePss} KiB, other=${processMemory.otherPss} KiB"
            )
        } else output.appendLine("App process PSS: unavailable")
        output.appendLine(
            "Native heap: allocated=${mib(Debug.getNativeHeapAllocatedSize())}, " +
                "free=${mib(Debug.getNativeHeapFreeSize())}, size=${mib(Debug.getNativeHeapSize())}"
        )
        output.appendLine("Process CPU time: ${Process.getElapsedCpuTime()} ms")
        output.appendLine("Runtime GC/alloc stats: " + runCatching {
            Debug.getRuntimeStats().entries.take(24).joinToString("; ") { "${it.key}=${it.value}" }
        }.getOrDefault("unavailable"))

        val activity = activeActivityReference?.get()
        val window = activity?.window
        val decor = window?.decorView
        val display = runCatching {
            if (Build.VERSION.SDK_INT >= 30) activity?.display
            else {
                @Suppress("DEPRECATION")
                activity?.windowManager?.defaultDisplay
            }
        }.getOrNull()
        output.appendLine(
            "Visible window: activity=${activity?.javaClass?.name ?: activeActivityClass}; " +
                "root=${decor?.javaClass?.name ?: "unavailable"}; " +
                "size=${decor?.width ?: -1}x${decor?.height ?: -1}px; " +
                "hardwareAccelerated=${decor?.isHardwareAccelerated ?: "unknown"}; " +
                "rootLayerType=${decor?.layerType ?: -1}; attached=${decor?.isAttachedToWindow ?: false}"
        )
        output.appendLine(
            "Display: refreshRateHz=${display?.refreshRate ?: windowRefreshRateHz}; " +
                "widthPx=${runCatching { display?.mode?.physicalWidth }.getOrNull() ?: -1}; " +
                "heightPx=${runCatching { display?.mode?.physicalHeight }.getOrNull() ?: -1}; " +
                "densityDpi=${runCatching { activity?.resources?.displayMetrics?.densityDpi }.getOrNull() ?: -1}"
        )

        val lastDrawAt = lyricsLastDrawElapsedNanos.get()
        val drawAge = if (lastDrawAt > 0L) ms(SystemClock.elapsedRealtimeNanos() - lastDrawAt) else "unavailable"
        output.appendLine(
            "Lyrics Canvas draw calls: count=${lyricsDrawCount.get()}, total=${ms(lyricsDrawTotalNanos.get())}, " +
                "max=${ms(lyricsDrawMaxNanos.get())}, textLayouts=${lyricsTextLayoutCount.get()}, " +
                "maxTextChars=${lyricsMaxTextLength.get()}, maxLayoutLines=${lyricsMaxLayoutLines.get()}, lastDrawAge=$drawAge"
        )
        output.appendLine(
            "Dialog Window FrameMetrics: frames=${windowFrameCount.get()}, droppedReports=${windowFrameDropReports.get()}, " +
                "slowFrames(>2vsync)=${windowSlowFrameCount.get()}, jankyFrames(>=50ms)=${windowJankyFrameCount.get()}, " +
                "refreshRateHz=$windowRefreshRateHz, maxTotal=${ms(windowMaxTotalNanos.get())}, " +
                "maxDraw=${ms(windowMaxDrawNanos.get())}, maxLayoutMeasure=${ms(windowMaxLayoutNanos.get())}, " +
                "maxSync=${ms(windowMaxSyncNanos.get())}, maxCommandIssue=${ms(windowMaxCommandNanos.get())}, " +
                "maxSwap=${ms(windowMaxSwapNanos.get())}, GPUmetricAvailable=$windowGpuMetricAvailable, " +
                "maxGPU=${ms(windowMaxGpuNanos.get())}, lastTotal=${ms(windowLastTotalNanos.get())}, " +
                "lastDraw=${ms(windowLastDrawNanos.get())}, lastGPU=${ms(windowLastGpuNanos.get())}"
        )
        val procStatus = runCatching {
            File("/proc/self/status").readLines().filter { line ->
                line.startsWith("VmPeak:") || line.startsWith("VmSize:") || line.startsWith("VmHWM:") ||
                    line.startsWith("VmRSS:") || line.startsWith("VmData:") || line.startsWith("VmSwap:") ||
                    line.startsWith("Threads:") || line.startsWith("voluntary_ctxt_switches:") ||
                    line.startsWith("nonvoluntary_ctxt_switches:")
            }
        }.getOrDefault(emptyList())
        output.appendLine("Process /proc/self/status:")
        if (procStatus.isEmpty()) output.appendLine("unavailable") else procStatus.forEach { output.appendLine(it) }
        output.appendLine("Open file descriptor count: ${runCatching { File("/proc/self/fd").list()?.size ?: -1 }.getOrDefault(-1)}")
        output.appendLine("Build fingerprint: ${Build.FINGERPRINT}")
    }

    private fun buildReport(
        context: Context,
        type: String,
        summary: String,
        threadName: String,
        body: String
    ): String {
        val packageInfo = runCatching {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, 0)
        }.getOrNull()
        val versionName = packageInfo?.versionName ?: "Unknown"
        @Suppress("DEPRECATION")
        val versionCode = packageInfo?.versionCode?.toString() ?: "Unknown"
        val device = "${Build.MANUFACTURER} ${Build.MODEL}".trim()
        val androidVersion = "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z", Locale.US).format(Date())
        val safeSummary = summary.replace('\n', ' ').replace('\r', ' ').take(500)

        return buildString {
            appendLine("QUANTUNE DIAGNOSTIC REPORT")
            appendLine("Type: $type")
            appendLine("Time: $timestamp")
            appendLine("Summary: $safeSummary")
            appendLine("App version: $versionName ($versionCode)")
            appendLine("Android: $androidVersion")
            appendLine("Device: $device")
            appendLine("ABI: ${Build.SUPPORTED_ABIS?.joinToString().orEmpty()}")
            appendLine("Process ID: ${Process.myPid()}")
            appendLine("Thread: $threadName")
            appendRuntimeDiagnostics(this, context)
            appendLine()
            appendLine("===== FULL ERROR / THREAD TRACE =====")
            append(body)
        }.take(MAX_REPORT_CHARS)
    }

    private fun persistReport(context: Context, report: String) {
        synchronized(fileLock) {
            val directory = reportDirectory(context)
            if (!directory.exists() && !directory.mkdirs()) {
                throw IllegalStateException("Could not create diagnostic report directory.")
            }

            val temporary = File(directory, REPORT_TEMP_NAME)
            val target = File(directory, REPORT_FILE_NAME)
            FileOutputStream(temporary).use { output ->
                output.write(report.toByteArray(Charsets.UTF_8))
                output.fd.sync()
            }

            if (target.exists() && !target.delete()) {
                throw IllegalStateException("Could not replace the previous diagnostic report.")
            }
            if (!temporary.renameTo(target)) {
                FileOutputStream(target).use { output ->
                    output.write(report.toByteArray(Charsets.UTF_8))
                    output.fd.sync()
                }
                temporary.delete()
            }
        }
    }

    private fun reportDirectory(context: Context): File =
        File(context.filesDir, REPORT_DIRECTORY)

    private fun reportFile(context: Context): File =
        File(reportDirectory(context), REPORT_FILE_NAME)
}
