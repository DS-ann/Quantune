package com.dsann.music.ui.player

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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dsann.music.service.AudioPlaybackService
import com.dsann.music.ui.theme.Icon
import com.dsann.music.ui.theme.dsmusicHomeStudioGradientBrush
import com.dsann.music.ui.theme.Text
import kotlinx.coroutines.flow.MutableStateFlow

@Composable
fun FullscreenAudioVisualizerDialog(
    audioService: AudioPlaybackService?,
    lyricsAccentColor: Color,
    lyricsTextColor: Color,
    lyricsColorStyle: String,
    onDismissRequest: () -> Unit
) {
    val emptyLevels = remember { MutableStateFlow(FloatArray(16) { 0f }) }
    val levelsFlow = audioService?.playbackVisualizerLevels ?: emptyLevels
    val levels by levelsFlow.collectAsStateWithLifecycle()

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        val dialogWindow = (LocalView.current.parent as? DialogWindowProvider)?.window
        // Do not lock the Activity orientation. The visualizer follows the device's
        // current orientation and Compose recalculates the bar geometry after rotation.
        DisposableEffect(dialogWindow) {
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
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF03050A))
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val count = 16
                val sideInset = 8.dp.toPx()
                // Keep spacing balanced on narrow phones and wide tablets in both orientations.
                val gap = (size.width * 0.012f).coerceIn(3.dp.toPx(), 10.dp.toPx())
                val barAreaWidth = (size.width - sideInset * 2f).coerceAtLeast(1f)
                val barWidth = ((barAreaWidth - gap * (count - 1)) / count)
                    .coerceAtLeast(1f)

                // The spectrum baseline stays close to the bottom edge, as before.
                val baselineY = size.height - 6.dp.toPx()
                val blockHeight = 9.dp.toPx()
                val blockGap = 1.5.dp.toPx()
                val blockPitch = blockHeight + blockGap
                val usableHeight = (baselineY - 28.dp.toPx()).coerceAtLeast(blockHeight)
                val maxBlocks = ((usableHeight + blockGap) / blockPitch)
                    .toInt()
                    .coerceAtLeast(1)

                // Reuse the exact brush used by the lyric renderer. The explicit
                // screen-wide endpoints prevent the gradient from restarting inside
                // every small rectangular segment.
                val activeBrush = when (lyricsColorStyle) {
                    "Home Studio Gradient" -> dsmusicHomeStudioGradientBrush(
                        startX = 0f,
                        endX = size.width
                    )
                    "White" -> SolidColor(Color.White)
                    "UI Matching" -> SolidColor(lyricsAccentColor)
                    else -> SolidColor(lyricsTextColor)
                }
                val blockCorner = androidx.compose.ui.geometry.CornerRadius(
                    x = 2.dp.toPx(),
                    y = 2.dp.toPx()
                )

                repeat(count) { index ->
                    val level = levels.getOrElse(index) { 0f }.coerceIn(0f, 1f)
                    val litBlocks = (level * maxBlocks)
                        .toInt()
                        .coerceIn(0, maxBlocks)
                    val x = sideInset + index * (barWidth + gap)

                    // Every column is a stack of independent rectangular segments.
                    // Unlit segments remain faintly visible to give the spectrum structure.
                    repeat(maxBlocks) { blockIndex ->
                        val top = baselineY - blockHeight - blockIndex * blockPitch
                        if (top >= 0f) {
                            val blockSize = androidx.compose.ui.geometry.Size(barWidth, blockHeight)
                            val blockOffset = androidx.compose.ui.geometry.Offset(x, top)
                            if (blockIndex < litBlocks) {
                                drawRoundRect(
                                    brush = activeBrush,
                                    topLeft = blockOffset,
                                    size = blockSize,
                                    cornerRadius = blockCorner
                                )
                            } else {
                                drawRoundRect(
                                    brush = activeBrush,
                                    topLeft = blockOffset,
                                    size = blockSize,
                                    cornerRadius = blockCorner,
                                    alpha = if (blockIndex % 3 == 0) 0.17f else 0.09f
                                )
                            }
                        }
                    }
                }

                // Shared reference line; all columns terminate at this fixed baseline.
                drawLine(
                    color = lyricsTextColor.copy(alpha = 0.62f),
                    start = androidx.compose.ui.geometry.Offset(sideInset, baselineY),
                    end = androidx.compose.ui.geometry.Offset(size.width - sideInset, baselineY),
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
                    .padding(start = 10.dp, end = 10.dp, bottom = 10.dp),
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

        }
    }
}
