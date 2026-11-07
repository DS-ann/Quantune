package com.dsann.music.ui.player

import android.annotation.SuppressLint
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import com.dsann.music.ui.theme.DsmusicTheme
import com.dsann.music.ui.theme.Slider
import com.dsann.music.ui.theme.SliderDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import kotlin.math.*
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.setProgress
import kotlin.math.roundToInt

/**
 * A highly optimized, custom Bezier curve wavy slider.
 * Flattens out automatically during user scrub operations to aid tracking visual precision.
 */

@SuppressLint("UnusedBoxWithConstraintsScope")
@Composable
fun WavyMusicSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    onValueChangeFinished: (() -> Unit)? = null,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    trackHeight: Dp = 4.dp,
    thumbRadius: Dp = 10.dp,
    activeTrackColor: Color = DsmusicTheme.colorScheme.primary,
    inactiveTrackColor: Color = DsmusicTheme.colorScheme.surfaceVariant,
    thumbColor: Color = DsmusicTheme.colorScheme.primary,
    waveAmplitudeWhenPlaying: Dp = 2.5.dp,
    waveLength: Dp = 48.dp,
    waveAnimationDuration: Int = 2200,
    hideInactiveTrackPortion: Boolean = true,
    isPlaying: Boolean = true,
    thumbLineHeightWhenInteracting: Dp = 20.dp,
    thumbStyle: String = "Auto",
    isWaveEligible: Boolean = true,
    semanticsLabel: String? = null,
    semanticsProgressStep: Float = 0.01f,
    frameTimeNanos: State<Long>? = null,
    liveProgressProvider: (() -> Float)? = null
) {
    val isDragged by interactionSource.collectIsDraggedAsState()
    val isPressed by interactionSource.collectIsPressedAsState()
    val isInteracting = isDragged || isPressed

    val thumbInteractionFractionState = animateFloatAsState(
        targetValue = if (isInteracting) 1f else 0f,
        animationSpec = tween(250, easing = FastOutSlowInEasing),
        label = "ThumbInteractionAnim"
    )

    val shouldShowWave = isWaveEligible && isPlaying && !isInteracting
    val localFrameTimeNanos = rememberAppAnimationFrameTimeNanos(
        active = frameTimeNanos == null && shouldShowWave
    )
    val activeFrameTimeNanos = frameTimeNanos ?: localFrameTimeNanos

    val animatedWaveAmplitude by animateDpAsState(
        targetValue = if (shouldShowWave) waveAmplitudeWhenPlaying else 0.dp,
        animationSpec = tween(300, easing = FastOutSlowInEasing),
        label = "WaveAmplitudeAnim"
    )

    val trackHeightPx = with(LocalDensity.current) { trackHeight.toPx() }
    val thumbRadiusPx = with(LocalDensity.current) { thumbRadius.toPx() }
    // Keep the ring outline slim and independent of the track's selected thickness.
    val ringStrokeWidthPx = with(LocalDensity.current) { 1.5.dp.toPx() }
    val waveAmplitudePxInternal = with(LocalDensity.current) { animatedWaveAmplitude.toPx() }
    val waveLengthPx = with(LocalDensity.current) { waveLength.toPx() }
    val waveFrequency = if (waveLengthPx > 0f) ((2 * PI) / waveLengthPx).toFloat() else 0f
    
    val thumbLineHeightPxInternal = with(LocalDensity.current) { thumbLineHeightWhenInteracting.toPx() }
    val thumbGapPx = with(LocalDensity.current) { 4.dp.toPx() }

    val wavePath = remember { Path() }

    val sliderVisualHeight = remember(trackHeight, thumbRadius, thumbLineHeightWhenInteracting) {
        max(trackHeight * 2, max(thumbRadius * 2, thumbLineHeightWhenInteracting) + 8.dp)
    }

    val clampedValue = value.coerceIn(valueRange.start, valueRange.endInclusive)
    val normalizedValue = if (valueRange.endInclusive == valueRange.start) 0f
        else ((clampedValue - valueRange.start) / (valueRange.endInclusive - valueRange.start)).coerceIn(0f, 1f)

    val safeSemanticsStep = semanticsProgressStep.coerceIn(0.005f, 0.25f)
    val semanticNormalizedValue = remember(normalizedValue, safeSemanticsStep) {
        ((normalizedValue / safeSemanticsStep).roundToInt() * safeSemanticsStep).coerceIn(0f, 1f)
    }
    val semanticSliderValue = remember(semanticNormalizedValue, valueRange) {
        valueRange.start + semanticNormalizedValue * (valueRange.endInclusive - valueRange.start)
    }

    BoxWithConstraints(modifier = modifier.clipToBounds()) {
        Slider(
            value = clampedValue,
            onValueChange = { newValue ->
                onValueChange(newValue)
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(sliderVisualHeight)
                .clearAndSetSemantics {
                    if (!semanticsLabel.isNullOrBlank()) {
                        contentDescription = semanticsLabel
                    }
                    progressBarRangeInfo = ProgressBarRangeInfo(
                        current = semanticSliderValue,
                        range = valueRange.start..valueRange.endInclusive,
                        steps = 0
                    )
                    if (enabled) {
                        setProgress { requested ->
                            val coerced = requested.coerceIn(valueRange.start, valueRange.endInclusive)
                            onValueChange(coerced)
                            onValueChangeFinished?.invoke()
                            true
                        }
                    }
                },
            enabled = enabled,
            valueRange = valueRange,
            onValueChangeFinished = onValueChangeFinished,
            interactionSource = interactionSource,
            colors = SliderDefaults.colors(
                thumbColor = Color.Transparent,
                activeTrackColor = Color.Transparent,
                inactiveTrackColor = Color.Transparent
            )
        )

        Spacer(
            modifier = Modifier
                .fillMaxWidth()
                .height(sliderVisualHeight)
                .drawWithCache {
                    val canvasWidth = size.width
                    val localCenterY = size.height / 2f
                    val localTrackStart = thumbRadiusPx
                    val localTrackEnd = canvasWidth - thumbRadiusPx
                    val localTrackWidth = (localTrackEnd - localTrackStart).coerceAtLeast(0f)
                    val taperDistancePx = 64.dp.toPx()
                    val activeTrackBrush = Brush.horizontalGradient(
                        colors = listOf(
                            activeTrackColor.copy(alpha = 0.35f),
                            activeTrackColor
                        ),
                        startX = localTrackStart,
                        endX = localTrackEnd.coerceAtLeast(localTrackStart + 1f)
                    )
                    val activeTrackStroke = Stroke(
                        width = trackHeightPx,
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round,
                        miter = 1f
                    )

                    onDrawWithContent {
                        // Read the shared frame timestamp in draw, not composition. While
                        // playing, sample Media3 directly for the visible progress line so the
                        // slider moves every selected frame without recomposing its parent.
                        val frameNanos = activeFrameTimeNanos.value
                        val liveProgress = if (
                            isPlaying && !isInteracting && frameNanos > 0L
                        ) liveProgressProvider?.invoke()?.takeIf { it.isFinite() } else null
                        val renderNormalizedValue = (liveProgress ?: normalizedValue).coerceIn(0f, 1f)
                        val thumbInteractionFraction = thumbInteractionFractionState.value
                        val phasePeriodNanos = waveAnimationDuration.coerceAtLeast(1).toLong() * 1_000_000L
                        val phaseShift = if (shouldShowWave && frameNanos > 0L && waveAnimationDuration > 0) {
                            ((frameNanos % phasePeriodNanos).toDouble() /
                                phasePeriodNanos.toDouble() * (2.0 * PI)).toFloat()
                        } else {
                            0f
                        }
                        val currentProgressPxEndVisual = localTrackStart + localTrackWidth * renderNormalizedValue
                        
                        // 1. Draw Inactive Track
                        if (hideInactiveTrackPortion) {
                            if (currentProgressPxEndVisual < localTrackEnd) {
                                drawLine(
                                    color = inactiveTrackColor,
                                    start = Offset(currentProgressPxEndVisual, localCenterY),
                                    end = Offset(localTrackEnd, localCenterY),
                                    strokeWidth = trackHeightPx,
                                    cap = StrokeCap.Round
                                )
                            }
                        } else {
                            drawLine(
                                color = inactiveTrackColor,
                                start = Offset(localTrackStart, localCenterY),
                                end = Offset(localTrackEnd, localCenterY),
                                strokeWidth = trackHeightPx,
                                cap = StrokeCap.Round
                            )
                        }

                        // 2. Draw Active Track (Sinusoidal Wave / Line) with Spatial Gradient
                        if (renderNormalizedValue > 0f) {
                            val activeTrackVisualEnd = currentProgressPxEndVisual - (thumbGapPx * thumbInteractionFraction)

                            if (waveAmplitudePxInternal > 0.01f && waveFrequency > 0f) {
                                wavePath.reset()
                                val waveStartDrawX = localTrackStart
                                val waveEndDrawX = activeTrackVisualEnd.coerceAtLeast(waveStartDrawX)
                                if (waveEndDrawX > waveStartDrawX) {
                                    val periodPx = ((2 * PI) / waveFrequency).toFloat()
                                    val samplesPerCycle = 20f
                                    val waveStep = (periodPx / samplesPerCycle)
                                        .coerceAtLeast(1.2f)
                                        .coerceAtMost(trackHeightPx)

                                    fun yAt(x: Float): Float {
                                        val s = sin(waveFrequency * x + phaseShift)
                                        val breath = 1f
                                        // Taper active wave smoothly from the very start of the track
                                        val taper = ((x - waveStartDrawX) / taperDistancePx).coerceIn(0f, 1f)
                                        val finalAmplitude = waveAmplitudePxInternal * breath * taper

                                        return (localCenterY + finalAmplitude * s)
                                            .coerceIn(
                                                localCenterY - finalAmplitude - trackHeightPx / 2f,
                                                localCenterY + finalAmplitude + trackHeightPx / 2f
                                            )
                                    }

                                    var prevX = waveStartDrawX
                                    var prevY = yAt(prevX)
                                    wavePath.moveTo(prevX, prevY)

                                    var x = prevX + waveStep
                                    while (x < waveEndDrawX) {
                                        val y = yAt(x)
                                        val mX = (prevX + x) * 0.5f
                                        val mY = (prevY + y) * 0.5f
                                        wavePath.quadraticTo(prevX, prevY, mX, mY)
                                        prevX = x
                                        prevY = y
                                        x += waveStep
                                    }
                                    val endY = yAt(waveEndDrawX)
                                    wavePath.quadraticTo(prevX, prevY, waveEndDrawX, endY)

                                    drawPath(
                                        path = wavePath,
                                        brush = activeTrackBrush,
                                        style = activeTrackStroke
                                    )
                                }
                            } else {
                                if (activeTrackVisualEnd > localTrackStart) {
                                    drawLine(
                                        brush = activeTrackBrush,
                                        start = Offset(localTrackStart, localCenterY),
                                        end = Offset(activeTrackVisualEnd, localCenterY),
                                        strokeWidth = trackHeightPx,
                                        cap = StrokeCap.Round
                                    )
                                }
                            }
                        }

                        // 3. Draw Sleek Hollow Halo Ring / Capsule-morphing Thumb
                        val currentThumbCenterX = localTrackStart + localTrackWidth * renderNormalizedValue
                        
                        fun fastLerp(start: Float, stop: Float, fraction: Float): Float {
                            return start + (stop - start) * fraction
                        }

                        val thumbCurrentWidthPx = fastLerp(thumbRadiusPx * 2f, trackHeightPx * 1.2f, thumbInteractionFraction)
                        val thumbCurrentHeightPx = fastLerp(thumbRadiusPx * 2f, thumbLineHeightPxInternal, thumbInteractionFraction)

                        when (thumbStyle) {
                            "None" -> Unit
                            "Ring" -> drawCircle(
                                color = thumbColor,
                                radius = (thumbRadiusPx - ringStrokeWidthPx / 2f).coerceAtLeast(1f),
                                center = Offset(currentThumbCenterX, localCenterY),
                                style = Stroke(width = ringStrokeWidthPx)
                            )
                            "Line" -> drawRoundRect(
                                color = thumbColor,
                                topLeft = Offset(
                                    currentThumbCenterX - (trackHeightPx * 1.5f) / 2f,
                                    localCenterY - thumbLineHeightPxInternal / 2f
                                ),
                                size = Size(trackHeightPx * 1.5f, thumbLineHeightPxInternal),
                                cornerRadius = CornerRadius(trackHeightPx)
                            )
                            "Dot" -> drawCircle(
                                color = thumbColor,
                                radius = thumbRadiusPx,
                                center = Offset(currentThumbCenterX, localCenterY)
                            )
                            else -> drawRoundRect(
                                color = thumbColor,
                                topLeft = Offset(
                                    currentThumbCenterX - thumbCurrentWidthPx / 2f,
                                    localCenterY - thumbCurrentHeightPx / 2f
                                ),
                                size = Size(thumbCurrentWidthPx, thumbCurrentHeightPx),
                                cornerRadius = CornerRadius(thumbCurrentWidthPx / 2f)
                            )
                        }
                    }
                }
        )
    }
}
