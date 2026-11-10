package com.dsann.music

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.content.res.Configuration
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material.icons.rounded.ArrowForward
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.dsann.music.ui.theme.DsmusicAppThemeFromPreferences
import com.dsann.music.ui.theme.SolidSystemNavigationBarBackground
import com.dsann.music.ui.theme.applySolidSystemNavigationBarColor
import com.dsann.music.ui.theme.DsmusicTheme
import com.dsann.music.ui.theme.Icon
import com.dsann.music.ui.theme.Text
import java.io.File

class CrashReportActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        applySolidSystemNavigationBarColor()
        val selectedTheme = getSharedPreferences("AppSettings", MODE_PRIVATE)
            .getString("theme_mode", "system")
        val isDarkTheme = when (selectedTheme) {
            "dark" -> true
            "light" -> false
            else -> (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES
        }
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = !isDarkTheme
            isAppearanceLightNavigationBars = !isDarkTheme
        }

        val report = CrashDiagnostics.readPendingReport(this)
            ?: DiagnosticReport(
                type = "APP ERROR",
                summary = "The diagnostic report could not be read.",
                timestamp = "Unknown time",
                fullText = "The saved diagnostic report is missing or could not be read."
            )
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                returnToQuantune()
            }
        })

        setContent {
            DsmusicAppThemeFromPreferences {
                Box(modifier = Modifier.fillMaxSize()) {
                    CrashReportScreen(
                        report = report,
                        onCopy = { copyReport(report) },
                        onShare = { shareReport(report) },
                        onContinue = { returnToQuantune() }
                    )
                    SolidSystemNavigationBarBackground()
                }
            }
        }
    }

    private fun copyReport(report: DiagnosticReport) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(
            ClipData.newPlainText("Quantune diagnostic report", report.fullText)
        )
        Toast.makeText(this, "Diagnostic report copied", Toast.LENGTH_SHORT).show()
    }

    private fun shareReport(report: DiagnosticReport) {
        try {
            val shareDirectory = File(cacheDir, "diagnostic-share")
            if (!shareDirectory.exists() && !shareDirectory.mkdirs()) {
                throw IllegalStateException("Could not create the temporary report directory.")
            }

            // Keep the share cache small without deleting a file that may still be in use.
            val now = System.currentTimeMillis()
            shareDirectory.listFiles()?.forEach { oldFile ->
                if (oldFile.isFile && now - oldFile.lastModified() > 24L * 60L * 60L * 1000L) {
                    oldFile.delete()
                }
            }

            val reportFile = File(shareDirectory, "quantune-diagnostic-$now.txt")
            reportFile.writeText(report.toCompactShareText(), Charsets.UTF_8)

            val reportUri = FileProvider.getUriForFile(
                this,
                "$packageName.fileprovider",
                reportFile
            )
            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, "Quantune diagnostic report")
                putExtra(Intent.EXTRA_STREAM, reportUri)
                clipData = android.content.ClipData.newUri(
                    contentResolver,
                    reportFile.name,
                    reportUri
                )
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(sendIntent, "Share diagnostic report"))
        } catch (error: Exception) {
            Log.e("QuantuneDiagnostics", "Could not prepare the report for sharing", error)
            Toast.makeText(
                this,
                "Could not prepare the diagnostic report for sharing",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun returnToQuantune() {
        CrashDiagnostics.clearPendingReport(this)
        startActivity(
            Intent(this, MainActivity::class.java).apply {
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
                )
                putExtra(CrashDiagnostics.EXTRA_SKIP_RECOVERY, true)
            }
        )
        finish()
    }
}


private fun DiagnosticReport.toCompactShareText(): String {
    val lines = fullText.lineSequence().toList()

    fun field(name: String, fallback: String = "Unknown"): String {
        return lines.firstOrNull { it.startsWith("$name:") }
            ?.substringAfter(':')
            ?.trim()
            .orEmpty()
            .ifBlank { fallback }
    }

    val traceMarker = "===== FULL ERROR / THREAD TRACE ====="
    val traceStart = lines.indexOf(traceMarker)
    val traceLines = if (traceStart >= 0) lines.drop(traceStart + 1) else emptyList()

    val relevantTrace = if (isAnr) {
        val selected = ArrayList<String>()
        var threadCount = 0
        var includeCurrentThread = false
        var framesInCurrentThread = 0
        var omittedFrames = false

        // The main thread is captured first. Keep it plus two supporting threads,
        // with a bounded number of frames from each, instead of sharing the full dump.
        traceLines.forEach { line ->
            if (line.startsWith("===== THREAD:")) {
                threadCount++
                includeCurrentThread = threadCount <= 3
                framesInCurrentThread = 0
                if (includeCurrentThread) selected.add(line.take(220))
            } else if (includeCurrentThread) {
                if (line.startsWith("\tat ")) {
                    if (framesInCurrentThread < 12) {
                        selected.add(line.take(240))
                    } else {
                        omittedFrames = true
                    }
                    framesInCurrentThread++
                } else if (line.isNotBlank()) {
                    selected.add(line.take(240))
                } else if (selected.lastOrNull() != "") {
                    selected.add("")
                }
            }
        }

        if (threadCount > 3) selected.add("[Additional thread stacks omitted.]")
        if (omittedFrames) selected.add("[Additional stack frames omitted.]")
        selected
    } else {
        val frameLimit = 45
        val selected = traceLines.take(frameLimit).map { it.take(240) }.toMutableList()
        if (traceLines.size > frameLimit) {
            selected.add("[Further exception trace lines omitted.]")
        }
        selected
    }

    return buildString {
        appendLine("QUANTUNE DIAGNOSTIC REPORT")
        appendLine("Type: " + field("Type", type))
        appendLine("Time: " + field("Time", timestamp))
        appendLine("Summary: " + field("Summary", summary).take(500))
        appendLine("App version: " + field("App version"))
        appendLine("Android: " + field("Android"))
        appendLine("Device: " + field("Device"))
        val abi = field("ABI", "")
        if (abi.isNotBlank()) appendLine("ABI: " + abi)
        val thread = field("Thread", "")
        if (thread.isNotBlank()) appendLine("Thread: " + thread)

        appendLine()
        appendLine(if (isAnr) "Relevant thread snapshot:" else "Exception stack trace:")
        if (relevantTrace.isEmpty()) {
            appendLine("Stack trace is not available in this report.")
        } else {
            relevantTrace.forEach { appendLine(it) }
        }
    }.take(10_000)
}

@Composable
private fun CrashReportScreen(
    report: DiagnosticReport,
    onCopy: () -> Unit,
    onShare: () -> Unit,
    onContinue: () -> Unit
) {
    val colors = DsmusicTheme.colorScheme
    val isAnr = report.isAnr
    val warningColor = colors.error
    val pageScroll = rememberScrollState()
    val reportScroll = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        colors.background,
                        colors.surface,
                        colors.surfaceContainerLow
                    )
                )
            )
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(pageScroll)
            .padding(horizontal = 22.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.primary.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.BugReport,
                    contentDescription = null,
                    tint = colors.primary,
                    modifier = Modifier.size(22.dp)
                )
            }
            Column(
                modifier = Modifier.padding(start = 11.dp),
                verticalArrangement = Arrangement.spacedBy(1.dp)
            ) {
                Text(
                    text = "QUANTUNE",
                    style = DsmusicTheme.typography.labelLarge,
                    color = colors.onSurface,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "DIAGNOSTICS",
                    style = DsmusicTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant,
                    letterSpacing = 1.4.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        Box(
            modifier = Modifier
                .size(76.dp)
                .clip(CircleShape)
                .background(warningColor.copy(alpha = 0.12f))
                .border(1.dp, warningColor.copy(alpha = 0.32f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isAnr) Icons.Rounded.Warning else Icons.Rounded.BugReport,
                contentDescription = null,
                tint = warningColor,
                modifier = Modifier.size(38.dp)
            )
        }

        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Text(
                text = if (isAnr) "App became unresponsive" else "App stopped unexpectedly",
                style = DsmusicTheme.typography.headlineMedium,
                color = colors.onSurface,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = if (isAnr) {
                    "Quantune detected that Android's main UI thread stopped responding. A thread snapshot was saved."
                } else {
                    "Quantune saved the uncaught exception details before Android terminated the app."
                },
                style = DsmusicTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant
            )
        }

        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(50.dp))
                .background(warningColor.copy(alpha = 0.13f))
                .padding(horizontal = 13.dp, vertical = 7.dp)
        ) {
            Text(
                text = report.type.uppercase(),
                style = DsmusicTheme.typography.labelSmall,
                color = warningColor,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(colors.surfaceContainer)
                .border(1.dp, colors.outlineVariant.copy(alpha = 0.7f), RoundedCornerShape(20.dp))
                .padding(17.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "ERROR SUMMARY",
                style = DsmusicTheme.typography.labelSmall,
                color = colors.onSurfaceVariant,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.1.sp
            )
            Text(
                text = report.summary,
                style = DsmusicTheme.typography.titleMedium,
                color = colors.onSurface,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = report.timestamp,
                style = DsmusicTheme.typography.labelSmall,
                color = colors.onSurfaceVariant
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            DiagnosticActionButton(
                modifier = Modifier.weight(1f),
                icon = Icons.Rounded.ContentCopy,
                label = "Copy report",
                color = colors.surfaceContainerHigh,
                contentColor = colors.onSurface,
                onClick = onCopy
            )
            DiagnosticActionButton(
                modifier = Modifier.weight(1f),
                icon = Icons.Rounded.Share,
                label = "Share report",
                color = colors.primaryContainer,
                contentColor = colors.onPrimaryContainer,
                onClick = onShare
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(colors.surfaceContainerLow)
                .border(1.dp, colors.outlineVariant.copy(alpha = 0.7f), RoundedCornerShape(20.dp))
                .padding(15.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "FULL TECHNICAL REPORT",
                style = DsmusicTheme.typography.labelSmall,
                color = colors.onSurfaceVariant,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.1.sp
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 140.dp, max = 340.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.surface)
                    .padding(12.dp)
                    .verticalScroll(reportScroll)
            ) {
                Text(
                    text = report.fullText,
                    style = DsmusicTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        lineHeight = 15.sp
                    ),
                    color = colors.onSurface
                )
            }
            Text(
                text = "The report stays on this device. It is only sent when you choose Share.",
                style = DsmusicTheme.typography.labelSmall,
                color = colors.onSurfaceVariant
            )
            Text(
                text = "Captures uncaught app exceptions and main-thread stalls. Some native crashes and Android force-stops cannot be intercepted.",
                style = DsmusicTheme.typography.labelSmall,
                color = colors.onSurfaceVariant
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(colors.primary)
                .clickable(onClick = onContinue),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "Open Quantune",
                    style = DsmusicTheme.typography.titleMedium,
                    color = colors.onPrimary,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.size(9.dp))
                Icon(
                    imageVector = Icons.Rounded.ArrowForward,
                    contentDescription = null,
                    tint = colors.onPrimary,
                    modifier = Modifier.size(19.dp)
                )
            }
        }
    }
}

@Composable
private fun DiagnosticActionButton(
    modifier: Modifier,
    icon: ImageVector,
    label: String,
    color: Color,
    contentColor: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = modifier
            .height(54.dp)
            .clip(RoundedCornerShape(15.dp))
            .background(color)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.size(7.dp))
        Text(
            text = label,
            style = DsmusicTheme.typography.labelLarge,
            color = contentColor,
            fontWeight = FontWeight.SemiBold
        )
    }
}
