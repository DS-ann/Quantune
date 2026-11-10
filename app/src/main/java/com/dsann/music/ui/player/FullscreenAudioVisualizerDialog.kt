package com.dsann.music.ui.player

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.DonutLarge
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
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
import com.dsann.music.service.PlaybackVisualizerController
import com.dsann.music.ui.theme.DsmusicTheme
import com.dsann.music.ui.theme.DropdownMenu
import com.dsann.music.ui.theme.DropdownMenuItem
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
    visualizerColorStyle: String,
    onVisualizerStyleChange: (String) -> Unit,
    onVisualizerColorStyleChange: (String) -> Unit,
    onDismissRequest: () -> Unit
) {
    val emptyLevels = remember { MutableStateFlow(FloatArray(PlaybackVisualizerController.BAR_COUNT) { 0f }) }
    val levelsFlow = audioService?.playbackVisualizerLevels ?: emptyLevels
    val levels by levelsFlow.collectAsStateWithLifecycle()
    var showColorMenu by remember { mutableStateOf(false) }

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
                val safeLevels = levels.takeIf { it.isNotEmpty() }
                    ?: FloatArray(PlaybackVisualizerController.BAR_COUNT)

                fun levelForSixteenBar(index: Int): Float {
                    val firstBand = index * safeLevels.size / 16
                    val endBand = ((index + 1) * safeLevels.size / 16)
                        .coerceAtLeast(firstBand + 1)
                    var sum = 0f
                    var peak = 0f
                    var count = 0
                    for (band in firstBand until endBand) {
                        val value = safeLevels.getOrElse(band) { 0f }.coerceIn(0f, 1f)
                        sum += value
                        if (value > peak) peak = value
                        count++
                    }
                    val average = if (count > 0) sum / count else 0f
                    return (average * 0.78f + peak * 0.22f).coerceIn(0f, 1f)
                }

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

                val homeStudioGradientColors = listOf(
                    Color(0xFF53F6FF),
                    Color(0xFF699DFF),
                    Color(0xFFAE7EFF),
                    Color(0xFFFF70CD),
                    Color(0xFFFFCF7F)
                )
                val activeBrush: Brush = when (visualizerColorStyle) {
                    "Gradient (Horizontal)" -> dsmusicHomeStudioGradientBrush(
                        startX = 0f,
                        endX = size.width
                    )
                    "White" -> SolidColor(Color.White)
                    "UI Matching" -> SolidColor(lyricsAccentColor)
                    else -> Brush.verticalGradient(
                        colors = homeStudioGradientColors,
                        startY = 0f,
                        endY = size.height
                    )
                }

                when (visualizerStyle) {
                    "Spectrum" -> {
                        // 48 independently measured logarithmic FFT bands, rendered directly.
                        val count = 48
                        val gap = (size.width * 0.006f).coerceIn(1.dp.toPx(), 5.dp.toPx())
                        val barAreaWidth = (size.width - sideInset * 2f).coerceAtLeast(1f)
                        val barWidth = (
                            (barAreaWidth - gap * (count - 1)) / count
                            ).coerceAtLeast(1f)
                        val baselineY = size.height * 0.5f
                        val maxHeight = (size.height * 0.5f - 28.dp.toPx())
                            .coerceAtLeast(4.dp.toPx())
                        val corner = CornerRadius(
                            x = (barWidth * 0.45f).coerceAtLeast(1f),
                            y = (barWidth * 0.45f).coerceAtLeast(1f)
                        )

                        repeat(count) { index ->
                            // Keep frequency order left-to-right (bass to treble), but
                            // mirror each bar vertically around the screen's midpoint.
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
                            drawRoundRect(
                                brush = activeBrush,
                                topLeft = Offset(x, baselineY),
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
                        // 48 independently measured logarithmic FFT bands arranged radially.
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
                        // Bars (16): preserve the segmented design while combining each
                        // three-band group from the shared 48-band spectrum.
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
                            val level = levelForSixteenBar(index)
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

            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 10.dp, end = 10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .background(Color(0xAA03050A), CircleShape)
                        .clickable { showColorMenu = true },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.MoreVert,
                        contentDescription = "Visualizer colour options",
                        tint = Color.White,
                        modifier = Modifier.size(21.dp)
                    )
                }
                DropdownMenu(
                    expanded = showColorMenu,
                    onDismissRequest = { showColorMenu = false }
                ) {
                    listOf(
                        "Gradient (Vertical)",
                        "Gradient (Horizontal)",
                        "White",
                        "UI Matching"
                    ).forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option, color = DsmusicTheme.colorScheme.onSurface) },
                            trailingIcon = {
                                if (visualizerColorStyle == option) {
                                    Text("✓", color = DsmusicTheme.colorScheme.primary)
                                }
                            },
                            onClick = {
                                onVisualizerColorStyleChange(option)
                                showColorMenu = false
                            }
                        )
                    }
                }
            }

            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 58.dp, end = 12.dp)
                    .background(Color(0xAA03050A), RoundedCornerShape(24.dp))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                listOf(
                    Triple("Bars (16)", Icons.Rounded.Equalizer, "16-bar visualizer"),
                    Triple("Spectrum", Icons.Rounded.GraphicEq, "Spectrum visualizer"),
                    Triple("Circular Spectrum", Icons.Rounded.DonutLarge, "Circular spectrum visualizer")
                ).forEach { (style, icon, description) ->
                    Box(
                        modifier = Modifier
                            .background(
                                color = if (visualizerStyle == style) {
                                    lyricsAccentColor.copy(alpha = 0.92f)
                                } else {
                                    Color.White.copy(alpha = 0.08f)
                                },
                                shape = RoundedCornerShape(18.dp)
                            )
                            .clickable { onVisualizerStyleChange(style) }
                            .padding(10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = description,
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
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
                    val frequencyLabels = listOf("60 Hz", "250 Hz", "1 kHz", "4 kHz", "16 kHz")
                    frequencyLabels.forEach { frequency ->
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
