package com.dsann.music.ui.player

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import com.dsann.music.ui.theme.Icon
import com.dsann.music.ui.theme.DsmusicTheme
import com.dsann.music.ui.theme.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import com.dsann.music.R
import com.dsann.music.data.lyrics.LyricsResult
import com.dsann.music.model.SongItem

/**
 * Custom Compose extension to render a marquee with smooth horizontal alpha-faded edges.
 * Uses drawWithCache to avoid allocating Brush and List objects on every frame of the drawing phase.
 */
@OptIn(ExperimentalFoundationApi::class)
fun Modifier.basicMarqueeWithFadedEdges(
    edgeWidth: Dp = 12.dp
): Modifier {
    // Keep the marquee interaction and scrolling, but skip the offscreen alpha-mask
    // compositing pass on API 23. The edge fade is purely decorative.
    if (android.os.Build.VERSION.SDK_INT <= android.os.Build.VERSION_CODES.M) {
        return this.basicMarquee().padding(horizontal = edgeWidth)
    }

    return this
        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithCache {
            val edgeWidthPx = edgeWidth.toPx()
            // Cache the brushes so they aren't recreated every frame
            val leftBrush = Brush.horizontalGradient(
                colors = listOf(Color.Transparent, Color.Black),
                startX = 0f,
                endX = edgeWidthPx
            )
            val rightBrush = Brush.horizontalGradient(
                colors = listOf(Color.Black, Color.Transparent),
                startX = size.width - edgeWidthPx,
                endX = size.width
            )

            onDrawWithContent {
                drawContent()
                drawRect(brush = leftBrush, blendMode = BlendMode.DstIn)
                drawRect(brush = rightBrush, blendMode = BlendMode.DstIn)
            }
        }
        .basicMarquee()
        .padding(horizontal = edgeWidth)
}

internal fun formatDuration(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(java.util.Locale.getDefault(), "%02d:%02d", minutes, seconds)
}

internal fun bestProgressContrastColor(backgroundColor: Color): Color {
    val dark = Color(0xFF1C1B1F)
    val backgroundArgb = backgroundColor.toArgb()
    val darkContrast = androidx.core.graphics.ColorUtils.calculateContrast(
        dark.toArgb(),
        backgroundArgb
    )
    val lightContrast = androidx.core.graphics.ColorUtils.calculateContrast(
        Color.White.toArgb(),
        backgroundArgb
    )
    return if (darkContrast >= lightContrast) dark else Color.White
}

internal fun deriveArtworkSurfaceColor(
    sourceColor: Color,
    isDark: Boolean,
    darkLightness: Float,
    lightLightness: Float,
    darkSaturationRange: ClosedFloatingPointRange<Float>,
    lightSaturationRange: ClosedFloatingPointRange<Float>,
    monochromeSaturationThreshold: Float = 0.06f
): Color {
    val hsl = FloatArray(3)
    androidx.core.graphics.ColorUtils.colorToHSL(sourceColor.toArgb(), hsl)
    val isMonochrome = hsl[1] < monochromeSaturationThreshold
    hsl[2] = if (isDark) darkLightness else lightLightness
    hsl[1] = if (isMonochrome) {
        0f
    } else if (isDark) {
        hsl[1].coerceIn(darkSaturationRange.start, darkSaturationRange.endInclusive)
    } else {
        hsl[1].coerceIn(lightSaturationRange.start, lightSaturationRange.endInclusive)
    }
    return Color(androidx.core.graphics.ColorUtils.HSLToColor(hsl))
}

@Composable
internal fun SplitLikeDislikeChip(
    isLiked: Boolean,
    isDisliked: Boolean,
    likesCount: String,
    onLikeClick: () -> Unit,
    onDislikeClick: () -> Unit,
    contentColor: Color,
    accentColor: Color,
    isDark: Boolean
) {
    val backgroundColor = contentColor.copy(alpha = if (isDark) 0.08f else 0.06f)
    val displayLikesText = remember(likesCount) {
        likesCount.ifBlank { "Like" }
    }
    
    Row(
        modifier = Modifier
            .height(36.dp)
            .clip(CircleShape)
            .background(backgroundColor),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Like Button
        Row(
            modifier = Modifier
                .fillMaxHeight()
                .clip(RoundedCornerShape(topStart = 18.dp, bottomStart = 18.dp))
                .clickable(onClick = onLikeClick)
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                painter = painterResource(id = if (isLiked) R.drawable.ic_thumbup else R.drawable.ic_outline_thumbup),
                contentDescription = "Like",
                tint = if (isLiked) accentColor else contentColor.copy(alpha = 0.8f),
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = displayLikesText,
                style = DsmusicTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp,
                color = if (isLiked) accentColor else contentColor.copy(alpha = 0.8f)
            )
        }
        
        // Vertical Divider
        Spacer(
            modifier = Modifier
                .width(1.dp)
                .fillMaxHeight(0.5f)
                .background(contentColor.copy(alpha = 0.15f))
        )
        
        // Dislike Button
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .clip(RoundedCornerShape(topEnd = 18.dp, bottomEnd = 18.dp))
                .clickable(onClick = onDislikeClick)
                .padding(horizontal = 14.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(id = if (isDisliked) R.drawable.ic_thumbdown else R.drawable.ic_outline_thumbdown),
                contentDescription = "Dislike",
                tint = if (isDisliked) accentColor else contentColor.copy(alpha = 0.8f),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
internal fun PillChip(
    icon: Any,
    label: String,
    onClick: () -> Unit,
    contentColor: Color,
    accentColor: Color,
    isDark: Boolean,
    isSelected: Boolean = false,
    progress: Float? = null,
    style: String = "Pill",
    modifier: Modifier = Modifier
) {
    val backgroundColor = if (isSelected) {
        accentColor.copy(alpha = if (isDark) 0.25f else 0.18f)
    } else {
        contentColor.copy(alpha = if (isDark) 0.08f else 0.06f)
    }

    val tintColor = if (isSelected) accentColor else contentColor.copy(alpha = 0.8f)
    val progressColor = accentColor.copy(alpha = if (isDark) 0.35f else 0.25f)
    val noBackground = style == "None"
    val chipShape = when (style) {
        "Round", "Pill" -> CircleShape
        "Square" -> RoundedCornerShape(5.dp)
        else -> RoundedCornerShape(0.dp)
    }
    val chipHeight = if (style == "Pill") 36.dp else 40.dp
    val chipBackground = if (noBackground) Color.Transparent else backgroundColor
    
    val animatedFill by animateFloatAsState(
        targetValue = progress ?: 0f,
        animationSpec = spring(dampingRatio = 0.85f, stiffness = 150f),
        label = "DownloadChipProgress"
    )
    
    Box(
        modifier = modifier
            .height(chipHeight)
            .clip(chipShape)
            .background(chipBackground)
            .drawBehind {
                if (progress != null && progress > 0f) {
                    drawRect(
                        color = progressColor,
                        size = size.copy(width = size.width * animatedFill)
                    )
                }
                if (style == "Round" || style == "Square") {
                    val borderRadius = if (style == "Square") 4.dp.toPx() else size.minDimension / 2f
                    drawRoundRect(
                        color = (if (isSelected) accentColor else contentColor).copy(
                            alpha = if (isSelected) 0.78f else 0.28f
                        ),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(borderRadius),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.dp.toPx())
                    )
                }
            }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = if (noBackground) 8.dp else 14.dp
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            when (icon) {
                is ImageVector -> {
                    Icon(
                        imageVector = icon,
                        contentDescription = label,
                        tint = tintColor,
                        modifier = Modifier.size(18.dp)
                    )
                }
                is Painter -> {
                    Icon(
                        painter = icon,
                        contentDescription = label,
                        tint = tintColor,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Text(
                text = label,
                style = DsmusicTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp,
                color = tintColor
            )
        }
    }
}

@Composable
internal fun WavySliderWithLabels(
    currentPositionProvider: () -> Int,
    currentPositionFlow: StateFlow<Int>,
    frameTimeNanos: State<Long>,
    isPlayerVisible: Boolean,
    duration: Int,
    isPlaying: Boolean,
    onSeekTo: (Int) -> Unit,
    dynamicAccentColor: Color,
    contentColor: Color,
    contentSecondary: Color,
    isDark: Boolean,
    scrubberStyle: String = "Waveform",
    scrubberColor: String = "Accent",
    playerTimes: String = "Both",
    scrubberThickness: Float = 1.5f,
    scrubberThumbStyle: String = "Auto",
    coverDerivedColor: Color = dynamicAccentColor,
    backgroundSurfaceColor: Color = Color.Black,
    modifier: Modifier = Modifier
) {
    var isScrubbing by remember { mutableStateOf(false) }
    var sliderScrubPos by remember { mutableFloatStateOf(0f) }

    // API 23 progress is a continuously moving control, not a decorative effect.
    // Give its draw pass a display-vsync clock even when the global animation cap is low.
    val isApi23 = android.os.Build.VERSION.SDK_INT <= android.os.Build.VERSION_CODES.M
    val api23ProgressFrameTimeNanos = rememberAppAnimationFrameTimeNanos(
        active = isApi23 && isPlayerVisible && isPlaying,
        forceDisplayRefresh = true
    )
    val sliderFrameTimeNanos = if (isApi23) {
        api23ProgressFrameTimeNanos
    } else {
        frameTimeNanos
    }

    // The service can publish at 20-60 FPS. Time labels only need a 4 Hz snapshot;
    // the actual slider line samples Media3 in the draw pass below on every selected frame.
    val liveProgressEnabled = isPlayerVisible && isPlaying
    val latestPositionProvider = rememberUpdatedState(currentPositionProvider)
    var labelPositionMs by remember(currentPositionFlow) {
        mutableIntStateOf(currentPositionFlow.value.coerceAtLeast(0))
    }

    LaunchedEffect(isPlayerVisible, isPlaying, currentPositionFlow) {
        if (liveProgressEnabled) {
            var lastDisplayedSecond = Int.MIN_VALUE
            while (true) {
                // One read per display frame, but only one state update per displayed second.
                withFrameNanos {
                    val latest = latestPositionProvider.value().coerceAtLeast(0)
                    val displayedSecond = latest / 1000
                    if (displayedSecond != lastDisplayedSecond) {
                        lastDisplayedSecond = displayedSecond
                        labelPositionMs = latest
                    }
                }
            }
        } else if (isPlayerVisible) {
            // While paused, only seek/track StateFlow events can change the elapsed label.
            currentPositionFlow.collect { position ->
                val latest = position.coerceAtLeast(0)
                if (latest / 1000 != labelPositionMs / 1000) {
                    labelPositionMs = latest
                }
            }
        }
    }

    val currentPosition = if (isPlayerVisible) {
        labelPositionMs
    } else {
        currentPositionProvider().coerceAtLeast(0)
    }
    // A missing duration is not a one-millisecond track. Treat progress as unknown/zero
    // until Media3 publishes a valid duration, otherwise any restored position renders full.
    val safeDur = duration.takeIf { it > 0 }?.toFloat()
    val displayPos = if (isScrubbing && safeDur != null) {
        (sliderScrubPos * safeDur).toInt()
    } else {
        currentPosition
    }
    val progressRatio = safeDur?.let {
        (currentPosition.toFloat() / it).coerceIn(0f, 1f)
    } ?: 0f
    val liveProgressProvider: (() -> Float)? = safeDur
        ?.takeIf { liveProgressEnabled && !isScrubbing }
        ?.let { liveDuration ->
            {
                (currentPositionProvider().coerceAtLeast(0).toFloat() / liveDuration)
                    .coerceIn(0f, 1f)
            }
        }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
    ) {
        val contrastTrackColor = bestProgressContrastColor(backgroundSurfaceColor)
        val requestedActiveColor = when (scrubberColor) {
            "White" -> Color.White
            "Cover" -> coverDerivedColor
            else -> DsmusicTheme.colorScheme.primary
        }
        // The Progress color setting is an explicit visual choice. Do not silently
        // replace Accent or White with a neutral colour based on the current artwork.
        // Keep the inactive track contrast-aware, while the active track and marker
        // both use the exact selected colour.
        val activeColor = requestedActiveColor
        val thickness = when (scrubberStyle) {
            "Hairline" -> 2.dp
            "Bold" -> 6.dp
            else -> 4.dp
        } * scrubberThickness.coerceIn(0.6f, 2f)
        val waveEnabled = scrubberStyle == "Waveform" &&
            android.os.Build.VERSION.SDK_INT > android.os.Build.VERSION_CODES.M
        WavyMusicSlider(
            value = if (isScrubbing && safeDur != null) sliderScrubPos else progressRatio,
            onValueChange = {
                isScrubbing = true
                sliderScrubPos = it
            },
            onValueChangeFinished = {
                isScrubbing = false
                safeDur?.let { onSeekTo((sliderScrubPos * it).toInt()) }
            },
            enabled = safeDur != null,
            activeTrackColor = activeColor,
            inactiveTrackColor = contrastTrackColor.copy(alpha = 0.40f),
            thumbColor = activeColor,
            isPlaying = isPlaying && isPlayerVisible,
            trackHeight = thickness,
            thumbRadius = when (scrubberThumbStyle) {
                "Dot", "Ring" -> 7.dp
                "None" -> 2.dp
                else -> if (scrubberStyle == "Medium") 7.dp else 3.dp
            },
            thumbStyle = scrubberThumbStyle,
            waveAmplitudeWhenPlaying = if (waveEnabled) 6.dp else 0.dp,
            waveLength = 48.dp,
            isWaveEligible = waveEnabled && isPlayerVisible,
            frameTimeNanos = sliderFrameTimeNanos,
            liveProgressProvider = liveProgressProvider
        )

        if (playerTimes != "None") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = if (playerTimes == "Both" || playerTimes == "Elapsed") {
                        formatDuration(displayPos.toLong())
                    } else "",
                    style = DsmusicTheme.typography.labelSmall,
                    color = contentSecondary
                )
                Text(
                    text = when (playerTimes) {
                        "Both" -> formatDuration(duration.toLong())
                        "Time left" -> "-${formatDuration((duration - displayPos).coerceAtLeast(0).toLong())}"
                        else -> ""
                    },
                    style = DsmusicTheme.typography.labelSmall,
                    color = contentSecondary
                )
            }
        }
    }
}

/**
 * Footer showing song metadata at the bottom of lyrics.
 * Displays song name, artist, album, and lyrics provider — only when values are present.
 * Styled to look "always inactive" with small text and low opacity.
 */
@Composable
internal fun LyricsMetadataFooter(
    currentSong: SongItem?,
    selectedProvider: String?,
    providerResults: Map<String, LyricsResult>,
    contentColor: Color,
    modifier: Modifier = Modifier
) {
    if (currentSong == null) return

    // Resolve album from the active provider's matched result
    val activeResult = selectedProvider?.let { providerResults[it] }
    val albumName = activeResult?.matchedAlbum
    val uriHandler = LocalUriHandler.current

    val mutedColor = contentColor.copy(alpha = 0.35f)
    val metaStyle = DsmusicTheme.typography.labelSmall.copy(
        fontWeight = FontWeight.Normal,
        letterSpacing = 0.3.sp,
        color = mutedColor
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // Song title
        if (currentSong.title.isNotBlank()) {
            Text(text = currentSong.title, style = metaStyle, maxLines = 1)
        }
        // Artist
        if (currentSong.artist.isNotBlank() &&
            !currentSong.artist.equals("Unknown Artist", ignoreCase = true)
        ) {
            Text(text = currentSong.artist, style = metaStyle, maxLines = 1)
        }
        // Album (from lyrics provider match)
        if (!albumName.isNullOrBlank()) {
            Text(text = albumName, style = metaStyle, maxLines = 1)
        }
        // Lyrics provider
        if (!selectedProvider.isNullOrBlank()) {
            Text(
                text = "Lyrics by $selectedProvider",
                style = metaStyle,
                maxLines = 1
            )
        }
        // Unison requires visible links to both its lyric entry and the contributor.
        val sourceCredit = activeResult?.sourceCreditText
        val sourceCreditUrl = activeResult?.sourceCreditUrl
        if (!sourceCredit.isNullOrBlank()) {
            Text(
                text = sourceCredit,
                style = metaStyle,
                maxLines = 1,
                modifier = if (!sourceCreditUrl.isNullOrBlank()) {
                    Modifier.clickable {
                        runCatching { uriHandler.openUri(sourceCreditUrl) }
                    }
                } else Modifier
            )
        }
        val submitterCredit = activeResult?.sourceSubmitterText
        val submitterUrl = activeResult?.sourceSubmitterUrl
        if (!submitterCredit.isNullOrBlank()) {
            Text(
                text = submitterCredit,
                style = metaStyle,
                maxLines = 1,
                modifier = if (!submitterUrl.isNullOrBlank()) {
                    Modifier.clickable {
                        runCatching { uriHandler.openUri(submitterUrl) }
                    }
                } else Modifier
            )
        }
    }
}
