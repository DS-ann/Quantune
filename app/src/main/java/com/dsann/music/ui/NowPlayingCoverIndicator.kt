package com.dsann.music.ui

import android.content.Context
import android.graphics.drawable.BitmapDrawable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.palette.graphics.Palette
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.dsann.music.ui.player.rememberAppAnimationFrameTimeNanos
import com.dsann.music.ui.theme.DsmusicTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Shared setting for the small animation shown over the artwork of the current song.
 * A single flow keeps Explore, Search, Library and Local Music in sync.
 */
object NowPlayingCoverAnimationPreference {
    const val KEY = "now_playing_cover_animation"
    private const val PREFS_NAME = "AppSettings"
    val choices = listOf("Bars", "Ring", "Pulse", "Orbit")

    private val styleState = MutableStateFlow("Bars")
    @Volatile private var loaded = false

    fun observe(context: Context): StateFlow<String> {
        if (!loaded) {
            synchronized(this) {
                if (!loaded) {
                    val stored = context.applicationContext
                        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                        .getString(KEY, "Bars")
                    styleState.value = stored?.takeIf { it in choices } ?: "Bars"
                    loaded = true
                }
            }
        }
        return styleState.asStateFlow()
    }

    fun set(context: Context, value: String) {
        val safe = value.takeIf { it in choices } ?: "Bars"
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY, safe)
            .apply()
        styleState.value = safe
        loaded = true
    }
}

/**
 * Extracts a small palette only for the currently playing artwork. Palette work runs
 * off the UI thread and uses the already-decoded Coil bitmap.
 */
@Composable
fun rememberNowPlayingCoverColor(
    artwork: Any?,
    enabled: Boolean,
    sizePx: Int = 96
): Color? {
    val context = LocalContext.current
    var sampledColor by remember(artwork, enabled) { mutableStateOf<Color?>(null) }
    LaunchedEffect(artwork, enabled, sizePx) {
        sampledColor = if (!enabled || artwork == null) {
            null
        } else {
            withContext(Dispatchers.IO) {
                runCatching {
                    val request = ImageRequest.Builder(context)
                        .data(artwork)
                        .size(sizePx, sizePx)
                        .allowHardware(false)
                        .build()
                    val result = context.imageLoader.execute(request)
                    val bitmap = (result as? SuccessResult)?.drawable
                        ?.let { it as? BitmapDrawable }
                        ?.bitmap
                    if (bitmap == null) {
                        null
                    } else {
                        withContext(Dispatchers.Default) {
                            val palette = Palette.from(bitmap).maximumColorCount(12).generate()
                            val colorInt = palette.vibrantSwatch?.rgb
                                ?: palette.lightVibrantSwatch?.rgb
                                ?: palette.darkVibrantSwatch?.rgb
                                ?: palette.mutedSwatch?.rgb
                                ?: palette.swatches.maxByOrNull { it.population }?.rgb
                                ?: android.graphics.Color.GRAY
                            Color(colorInt)
                        }
                    }
                }.getOrNull()
            }
        }
    }
    return sampledColor
}

/**
 * A compact, theme-aware animation badge. Only the playing row on the active tab
 * creates a frame subscription; pausing playback freezes its current visual state.
 */
@Composable
fun NowPlayingCoverIndicator(
    isPlaying: Boolean,
    coverColor: Color?,
    modifier: Modifier = Modifier,
    isAnimating: Boolean = true
) {
    if (!isPlaying) return

    val context = LocalContext.current
    val style by remember(context) {
        NowPlayingCoverAnimationPreference.observe(context)
    }.collectAsState()
    // Keep the current-track badge visible while paused, but stop its frame subscription.
    val isMotionAllowed = isAnimating && LocalRootTabActive.current
    val frameTime = rememberAppAnimationFrameTimeNanos(active = isMotionAllowed)
    // Retain the last rendered phase when playback pauses. A plain StateFlow frame
    // clock resets while inactive, so keep this small value outside Compose state.
    val frozenPhase = remember(coverColor) { floatArrayOf(0.32f) }
    val phase = (if (isMotionAllowed && frameTime.value > 0L) {
        (frameTime.value % 1_600_000_000L).toFloat() / 1_600_000_000f
    } else {
        frozenPhase[0]
    }).also { if (isMotionAllowed) frozenPhase[0] = it }

    val themeColor = DsmusicTheme.colorScheme.primary
    val artworkColor = coverColor ?: themeColor
    val artworkIsLight = colorLuminance(artworkColor) > 0.54f
    val badgeTone = if (artworkIsLight) Color.Black else Color.White
    val themeAndArtwork = mixColors(themeColor, artworkColor, 0.42f)
    val foreground = mixColors(
        themeAndArtwork,
        if (artworkIsLight) Color.White else Color.Black,
        0.38f
    )
    val secondary = mixColors(foreground, themeColor, 0.28f)

    Box(
        modifier = modifier
            .size(28.dp)
            .clip(CircleShape)
            .background(badgeTone.copy(alpha = 0.76f))
            .border(1.dp, badgeTone.copy(alpha = 0.62f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.fillMaxSize().padding(5.dp)) {
            val shortest = size.minDimension
            val center = Offset(size.width / 2f, size.height / 2f)
            val motion = phase
            when (style) {
                "Ring" -> {
                    val stroke = (shortest * 0.13f).coerceAtLeast(1.35f)
                    val radius = shortest / 2f - stroke
                    drawCircle(
                        color = secondary.copy(alpha = 0.42f),
                        radius = radius,
                        center = center,
                        style = Stroke(width = stroke)
                    )
                    drawArc(
                        color = foreground,
                        startAngle = -90f,
                        sweepAngle = 235f + 110f * motion,
                        useCenter = false,
                        topLeft = Offset(center.x - radius, center.y - radius),
                        size = Size(radius * 2f, radius * 2f),
                        style = Stroke(width = stroke, cap = StrokeCap.Round)
                    )
                }
                "Pulse" -> {
                    val pulse = 0.18f + 0.12f * (0.5f + 0.5f * sin(motion * 2f * PI.toFloat()))
                    drawCircle(
                        color = secondary.copy(alpha = 0.42f),
                        radius = shortest * (0.30f + pulse),
                        center = center,
                        style = Stroke(width = (shortest * 0.09f).coerceAtLeast(1f))
                    )
                    drawCircle(
                        color = foreground.copy(alpha = 0.95f),
                        radius = shortest * 0.19f,
                        center = center
                    )
                }
                "Orbit" -> {
                    val orbitRadius = shortest * 0.30f
                    drawCircle(
                        color = secondary.copy(alpha = 0.42f),
                        radius = shortest * 0.12f,
                        center = center
                    )
                    repeat(3) { index ->
                        val angle = (motion * 2f * PI.toFloat()) + (index * 2f * PI.toFloat() / 3f)
                        val point = Offset(
                            x = center.x + cos(angle) * orbitRadius,
                            y = center.y + sin(angle) * orbitRadius
                        )
                        drawCircle(
                            color = if (index == 0) foreground else secondary,
                            radius = shortest * if (index == 0) 0.13f else 0.095f,
                            center = point
                        )
                    }
                }
                else -> {
                    val heights = floatArrayOf(
                        0.38f + 0.46f * (0.5f + 0.5f * sin(motion * 2f * PI.toFloat())),
                        0.42f + 0.50f * (0.5f + 0.5f * sin((motion + 0.34f) * 2f * PI.toFloat())),
                        0.34f + 0.52f * (0.5f + 0.5f * sin((motion + 0.68f) * 2f * PI.toFloat()))
                    )
                    val barWidth = shortest * 0.19f
                    val gap = shortest * 0.10f
                    val totalWidth = barWidth * 3f + gap * 2f
                    val startX = (size.width - totalWidth) / 2f
                    heights.forEachIndexed { index, fraction ->
                        val barHeight = size.height * fraction.coerceIn(0.2f, 1f)
                        val left = startX + index * (barWidth + gap)
                        drawRoundRect(
                            color = if (index == 1) foreground else secondary,
                            topLeft = Offset(left, size.height - barHeight),
                            size = Size(barWidth, barHeight),
                            cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
                        )
                    }
                }
            }
        }
    }
}

private fun mixColors(first: Color, second: Color, secondWeight: Float): Color {
    val weight = secondWeight.coerceIn(0f, 1f)
    val inverse = 1f - weight
    return Color(
        red = first.red * inverse + second.red * weight,
        green = first.green * inverse + second.green * weight,
        blue = first.blue * inverse + second.blue * weight,
        alpha = first.alpha * inverse + second.alpha * weight
    )
}

private fun colorLuminance(color: Color): Float {
    fun channel(value: Float): Float =
        if (value <= 0.03928f) value / 12.92f else ((value + 0.055f) / 1.055f).let { it * it * it }
    return 0.2126f * channel(color.red) +
        0.7152f * channel(color.green) +
        0.0722f * channel(color.blue)
}
