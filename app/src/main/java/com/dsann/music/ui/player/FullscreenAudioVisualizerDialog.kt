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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
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
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun FullscreenAudioVisualizerDialog(
    audioService: AudioPlaybackService?,
    visualizerStyle: String,
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
        // Keep the established immersive behavior: the visualizer follows the device
        // orientation while using the entire available display area.
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
                val sideInset = 8.dp.toPx()
                val safeLevels = levels.takeIf { it.isNotEmpty() } ?: FloatArray(16)

                fun levelAt(position: Float): Float {
                    val scaled = position.coerceIn(0f, 1f) * (safeLevels.size - 1)
                    val lower = scaled.toInt().coerceIn(0, safeLevels.lastIndex)
                    val upper = (lower + 1).coerceAtMost(safeLevels.lastIndex)
                    val fraction = scaled - lower
                    return (
                        safeLevels[lower] * (1f - fraction) +
                            safeLevels[upper] * fraction
                        ).coerceIn(0f, 1f)
                }

                val activeBrush: Brush = when (lyricsColorStyle) {
                    "Home Studio Gradient" -> dsmusicHomeStudioGradientBrush(
                        startX = 0f,
                        endX = size.width
                    )
                    "White" -> SolidColor(Color.White)
                    "UI Matching" -> SolidColor(lyricsAccentColor)
                    else -> SolidColor(lyricsTextColor)
                }

                when (visualizerStyle) {
                    "Spectrum" -> {
                        // A wide, continuous spectrum: more columns than the 16-band
                        // source, interpolated smoothly from the real playback levels.
                        val count = 48
                        val gap = (size.width * 0.006f).coerceIn(1.dp.toPx(), 5.dp.toPx())
                        val barAreaWidth = (size.width - sideInset * 2f).coerceAtLeast(1f)
                        val barWidth = (
                            (barAreaWidth - gap * (count - 1)) / count
                            ).coerceAtLeast(1f)
                        val baselineY = size.height - 30.dp.toPx()
                        val maxHeight = (baselineY - 18.dp.toPx()).coerceAtLeast(4.dp.toPx())
                        val corner = CornerRadius(
                            x = (barWidth * 0.45f).coerceAtLeast(1f),
                            y = (barWidth * 0.45f).coerceAtLeast(1f)
                        )

                        repeat(count) { index ->
                            val level = levelAt(index / (count - 1f))
                            val barHeight = (
                                maxHeight * (0.025f + level * 0.975f)
                                ).coerceIn(2.dp.toPx(), maxHeight)
                            val x = sideInset + index * (barWidth + gap)
                            drawRoundRect(
                                brush = activeBrush,
                                topLeft = Offset(x, baselineY - barHeight),
                                size = Size(barWidth, barHeight),
                                cornerRadius = corner
                            )
                        }

                        drawLine(
                            color = lyricsTextColor.copy(alpha = 0.58f),
                            start = Offset(sideInset, baselineY),
                            end = Offset(size.width - sideInset, baselineY),
                            strokeWidth = 1.dp.toPx(),
                            cap = StrokeCap.Butt
                        )
                    }

                    "Circular Spectrum" -> {
                        // Radial spectrum centered in the screen. The 16 real FFT
                        // bands are interpolated around the ring to keep the motion fluid.
                        val center = Offset(size.width / 2f, size.height / 2f)
                        val minDimension = minOf(size.width, size.height)
                        val innerRadius = minDimension * 0.20f
                        val maxLength = minDimension * 0.24f
                        val count = 48

                        drawCircle(
                            color = lyricsTextColor.copy(alpha = 0.16f),
                            radius = innerRadius,
                            center = center,
                            style = Stroke(width = 1.dp.toPx())
                        )

                        repeat(count) { index ->
                            val position = index / (count - 1f)
                            val level = levelAt(position)
                            val angle = -Math.PI.toFloat() / 2f +
                                index * (2f * Math.PI.toFloat() / count)
                            val direction = Offset(cos(angle), sin(angle))
                            val start = center + direction * innerRadius
                            val end = center + direction * (
                                innerRadius + maxLength * (0.035f + level * 0.965f)
                            )
                            drawLine(
                                brush = activeBrush,
                                start = start,
                                end = end,
                                strokeWidth = 2.4.dp.toPx(),
                                cap = StrokeCap.Round
                            )
                        }

                        drawCircle(
                            color = lyricsAccentColor.copy(alpha = 0.14f),
                            radius = innerRadius * 0.58f,
                            center = center
                        )
                    }

                    else -> {
                        // Bars (16): preserve the original segmented full-screen design.
                        val count = 16
                        val gap = (size.width * 0.012f).coerceIn(3.dp.toPx(), 10.dp.toPx())
                        val barAreaWidth = (size.width - sideInset * 2f).coerceAtLeast(1f)
                        val barWidth = (
                            (barAreaWidth - gap * (count - 1)) / count
                            ).coerceAtLeast(1f)

                        val baselineY = size.height - 6.dp.toPx()
                        val blockHeight = 9.dp.toPx()
                        val blockGap = 1.5.dp.toPx()
                        val blockPitch = blockHeight + blockGap
                        val usableHeight = (baselineY - 28.dp.toPx()).coerceAtLeast(blockHeight)
                        val maxBlocks = ((usableHeight + blockGap) / blockPitch)
                            .toInt()
                            .coerceAtLeast(1)
                        val blockCorner = CornerRadius(
                            x = 2.dp.toPx(),
                            y = 2.dp.toPx()
                        )

                        repeat(count) { index ->
                            val level = safeLevels.getOrElse(index) { 0f }.coerceIn(0f, 1f)
                            val litBlocks = (level * maxBlocks)
                                .toInt()
                                .coerceIn(0, maxBlocks)
                            val x = sideInset + index * (barWidth + gap)

                            repeat(maxBlocks) { blockIndex ->
                                val top = baselineY - blockHeight - blockIndex * blockPitch
                                if (top >= 0f) {
                                    val blockOffset = Offset(x, top)
                                    val blockSize = Size(barWidth, blockHeight)
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

                        drawLine(
                            color = lyricsTextColor.copy(alpha = 0.62f),
                            start = Offset(sideInset, baselineY),
                            end = Offset(size.width - sideInset, baselineY),
                            strokeWidth = 1.dp.toPx(),
                            cap = StrokeCap.Butt
                        )
                    }
                }
            }

            if (visualizerStyle != "Circular Spectrum") {
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
