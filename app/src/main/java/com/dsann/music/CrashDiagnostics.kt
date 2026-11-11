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
import android.os.SystemClock
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
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
    private const val MAX_REPORT_CHARS = 320_000

    private val installed = AtomicBoolean(false)
    private val anrAlreadyRecorded = AtomicBoolean(false)
    private val fileLock = Any()

    @Volatile private var activeActivityClass: String = "Unknown"
    @Volatile private var baseScreenContext: String = "Unknown"
    @Volatile private var overlayScreenContext: String? = null
    @Volatile private var uiDiagnosticDetails: String = "Not available"
    @Volatile private var lyricsRenderingDetails: String = "Not available"

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

        // Android may suspend/freeze a cached background process. A heartbeat gap while
        // no Quantune Activity is visible is not a user-facing main-thread ANR.
        application.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityStarted(activity: Activity) {
                startedActivities.incrementAndGet()
                activeActivityClass = activity.javaClass.name
                // Give the UI a fresh response window when it becomes visible again.
                lastMainResponse.set(SystemClock.uptimeMillis())
            }

            override fun onActivityStopped(activity: Activity) {
                val remaining = startedActivities.decrementAndGet()
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

                // Reset the baseline while backgrounded. Cached/frozen time must not
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
                    val report = buildReport(
                        context = application,
                        type = "ANR — APP NOT RESPONDING",
                        summary = "The main thread did not process an asynchronous heartbeat for approximately $blockedForMs ms while a Quantune activity was visible.",
                        threadName = mainThread?.name ?: "main",
                        body = stackDump
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
        stack.take(80).forEach { frame -> output.appendLine("\tat $frame") }
        if (stack.size > 80) {
            output.appendLine("... ${stack.size - 80} additional frames omitted.")
        }
    }

    private fun captureAllThreadStacks(): String {
        val entries = Thread.getAllStackTraces().entries
            .sortedWith(
                compareBy<Map.Entry<Thread, Array<StackTraceElement>>> {
                    threadDiagnosticPriority(it.key)
                }.thenBy { it.key.name }
            )
            .take(64)

        return buildString {
            appendLine(
                "Thread stacks are ordered with main first, followed by graphics, " +
                    "Binder, media and remaining workers. Up to 64 threads are included."
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
        for (sampleIndex in 0 until 3) {
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
                "===== MAIN / GRAPHICS THREAD SAMPLE ${sampleIndex + 1}/3 " +
                    "(approximately ${sampleIndex * 140} ms after detection) ====="
            )
            if (critical.isEmpty()) {
                appendLine("No main or Java graphics thread stack was available.")
            } else {
                critical.take(12).forEach { entry ->
                    appendThreadSnapshot(this, entry.key, entry.value)
                }
            }

            if (sampleIndex < 2) {
                try {
                    Thread.sleep(140L)
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                    return@buildString
                }
            }
        }
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
        fun mib(bytes: Long): String = String.format(
            Locale.US,
            "%.1f MiB",
            bytes.toDouble() / (1024.0 * 1024.0)
        )

        output.appendLine("Activity: $activeActivityClass")
        output.appendLine("UI context: ${currentScreenContext()}")
        output.appendLine("UI details: $uiDiagnosticDetails")
        output.appendLine("Lyrics rendering details: $lyricsRenderingDetails")
        output.appendLine("Device hardware: ${Build.HARDWARE}; board: ${Build.BOARD}; device code: ${Build.DEVICE}")
        if (memoryAvailable) {
            output.appendLine(
                "System RAM: total=${mib(memoryInfo.totalMem)}, available=${mib(memoryInfo.availMem)}, " +
                    "threshold=${mib(memoryInfo.threshold)}, lowMemory=${memoryInfo.lowMemory}"
            )
            output.appendLine("System low-RAM device flag: ${activityManager?.isLowRamDevice ?: "unknown"}")
        } else {
            output.appendLine("System RAM snapshot: unavailable")
        }
        output.appendLine(
            "App Java heap: used=${mib(heapUsed)}, allocated=${mib(runtime.totalMemory())}, " +
                "max=${mib(runtime.maxMemory())}"
        )
        output.appendLine("Process CPU time: ${Process.getElapsedCpuTime()} ms")
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
