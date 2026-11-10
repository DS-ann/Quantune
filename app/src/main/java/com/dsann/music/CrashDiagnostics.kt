package com.dsann.music

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
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
    private const val MAX_REPORT_CHARS = 160_000

    private val installed = AtomicBoolean(false)
    private val anrAlreadyRecorded = AtomicBoolean(false)
    private val fileLock = Any()

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
        val mainHandler = Handler.createAsync(Looper.getMainLooper())
        val startedActivities = AtomicInteger(0)
        // Handler.postDelayed uses uptimeMillis. Use the same clock for the heartbeat.
        val lastMainResponse = java.util.concurrent.atomic.AtomicLong(SystemClock.uptimeMillis())

        // Android may suspend/freeze a cached background process. A heartbeat gap while
        // no Quantune Activity is visible is not a user-facing main-thread ANR.
        application.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityStarted(activity: Activity) {
                startedActivities.incrementAndGet()
                // Give the UI a fresh response window when it becomes visible again.
                lastMainResponse.set(SystemClock.uptimeMillis())
            }

            override fun onActivityStopped(activity: Activity) {
                val remaining = startedActivities.decrementAndGet()
                if (remaining <= 0) {
                    startedActivities.set(0)
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
                    val stackDump = captureAllThreadStacks()
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

    private fun captureAllThreadStacks(): String {
        val entries = Thread.getAllStackTraces().entries
            .sortedBy { if (it.key.name == "main") 0 else 1 }
            .take(48)

        return buildString {
            appendLine("Main thread is listed first. Stack traces are captured at the time the watchdog detected the stall.")
            entries.forEach { entry ->
                appendLine()
                appendLine("===== THREAD: ${entry.key.name} | STATE: ${entry.key.state} =====")
                entry.value.forEach { frame -> appendLine("\tat $frame") }
            }
        }
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
