package com.dsann.music.ui.player

import android.Manifest
import android.app.Activity
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dsann.music.service.AudioPlaybackService
import com.dsann.music.ui.theme.Icon
import com.dsann.music.ui.theme.Text
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow

@Composable
fun FullscreenAudioVisualizerDialog(
    activity: Activity,
    audioService: AudioPlaybackService?,
    lyricsAccentColor: Color,
    lyricsTextColor: Color,
    onDismissRequest: () -> Unit
) {
    val context = LocalContext.current
    val previousOrientation = remember(activity) { activity.requestedOrientation }

    var captureAttempted by remember { mutableStateOf(false) }
    var captureAvailable by remember { mutableStateOf(false) }
    var permissionDenied by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        permissionDenied = !granted
        captureAttempted = true
        captureAvailable = granted && audioService?.startPlaybackVisualizer() == true
    }

    LaunchedEffect(audioService) {
        // Let the landscape dialog finish opening before allocating the platform FFT effect.
        delay(450L)
        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            captureAttempted = true
            captureAvailable = audioService?.startPlaybackVisualizer() == true
        } else {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    DisposableEffect(audioService) {
        onDispose { audioService?.stopPlaybackVisualizer() }
    }

    val emptyLevels = remember { MutableStateFlow(FloatArray(16) { 0f }) }
    val levelsFlow = audioService?.playbackVisualizerLevels ?: emptyLevels
    val levels by levelsFlow.collectAsStateWithLifecycle()

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        val dialogWindow = (LocalView.current.parent as? DialogWindowProvider)?.window
        DisposableEffect(activity, dialogWindow) {
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            val controller = dialogWindow?.let { window ->
                WindowCompat.setDecorFitsSystemWindows(window, false)
                WindowCompat.getInsetsController(window, window.decorView).also {
                    it.systemBarsBehavior =
                        WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    it.hide(WindowInsetsCompat.Type.systemBars())
                }
            }
            onDispose {
                controller?.show(WindowInsetsCompat.Type.systemBars())
                activity.requestedOrientation = previousOrientation
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF03050A))
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val count = 16
                val gap = size.width * 0.012f
                val barWidth = ((size.width - gap * (count - 1)) / count)
                    .coerceAtLeast(1f)
                // The spectrum baseline is fixed 6 dp above the physical bottom edge.
                val baselineY = size.height - 6.dp.toPx()
                val gradient = Brush.verticalGradient(
                    colors = listOf(
                        lyricsAccentColor.copy(alpha = 0.52f),
                        lyricsAccentColor.copy(alpha = 0.96f),
                        lyricsTextColor.copy(alpha = 0.92f)
                    ),
                    startY = 0f,
                    endY = baselineY
                )

                repeat(count) { index ->
                    val level = levels.getOrElse(index) { 0f }.coerceIn(0f, 1f)
                    // Keep all 16 bars visible before the first FFT frame arrives.
                    val barHeight = baselineY * (0.035f + level * 0.88f)
                    val x = index * (barWidth + gap)
                    val top = baselineY - barHeight
                    drawRoundRect(
                        brush = gradient,
                        topLeft = androidx.compose.ui.geometry.Offset(x, top),
                        size = androidx.compose.ui.geometry.Size(barWidth, barHeight),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(
                            x = barWidth / 2f,
                            y = barWidth / 2f
                        )
                    )
                }

                // Shared frequency reference line; bar heights all terminate at this line.
                drawLine(
                    color = Color.White.copy(alpha = 0.58f),
                    start = androidx.compose.ui.geometry.Offset(0f, baselineY),
                    end = androidx.compose.ui.geometry.Offset(size.width, baselineY),
                    strokeWidth = 1.dp.toPx(),
                    cap = StrokeCap.Butt
                )
            }

            // Frequency labels sit just above the spectrum baseline.
            // These are logarithmically spaced reference frequencies for the FFT bands.
            Row(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween
            ) {
                listOf("60 Hz", "250 Hz", "1 kHz", "4 kHz", "16 kHz").forEach { frequency ->
                    Text(
                        text = frequency,
                        color = Color.White.copy(alpha = 0.88f),
                        fontSize = 10.sp,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        modifier = Modifier
                            .background(
                                Color(0x9903050A),
                                androidx.compose.foundation.shape.RoundedCornerShape(4.dp)
                            )
                            .padding(horizontal = 3.dp, vertical = 1.dp)
                    )
                }
            }

            // The only persistent control on this screen.
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 12.dp, top = 12.dp)
                    .size(40.dp)
                    .clickable(onClick = onDismissRequest),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White,
                    modifier = Modifier.size(19.dp)
                )
            }

            if (captureAttempted && !captureAvailable) {
                val message = when {
                    permissionDenied ->
                        "Audio capture permission is required to visualize playback."
                    audioService == null ->
                        "Playback audio is not ready."
                    else ->
                        "This audio output does not support the visualizer."
                }
                Text(
                    text = message,
                    color = Color.White.copy(alpha = 0.72f),
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        }
    }
}
