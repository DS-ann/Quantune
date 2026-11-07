@file:Suppress("UNCHECKED_CAST")

package com.dsann.music.ui.theme

import android.app.Activity
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import kotlin.math.max
import kotlin.math.min
import androidx.core.view.WindowCompat.getInsetsController

/**
 * Observe one SharedPreferences key without keeping a listener after the
 * calling composition leaves the screen.
 */
@Composable
fun <T> SharedPreferences.observeKey(key: String, defaultValue: T): State<T> {
    fun readValue(prefs: SharedPreferences): T {
        @Suppress("UNCHECKED_CAST")
        return when (defaultValue) {
            is Boolean -> prefs.getBoolean(key, defaultValue) as T
            is String -> (prefs.getString(key, defaultValue) ?: defaultValue) as T
            is Float -> prefs.getFloat(key, defaultValue) as T
            is Int -> prefs.getInt(key, defaultValue) as T
            is Long -> prefs.getLong(key, defaultValue) as T
            else -> defaultValue
        }
    }

    // Seed from the stored value immediately. Starting with a generic default and
    // correcting it in DisposableEffect caused a visible wrong-theme frame.
    val state = remember(this, key, defaultValue) {
        mutableStateOf(readValue(this@observeKey))
    }
    DisposableEffect(this, key, defaultValue) {
        state.value = readValue(this@observeKey)
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { prefs, changedKey ->
            if (changedKey == key) state.value = readValue(prefs)
        }
        registerOnSharedPreferenceChangeListener(listener)
        onDispose { unregisterOnSharedPreferenceChangeListener(listener) }
    }
    return state
}


/**
 * Keep accent labels and page markers readable even when users choose a very
 * dark, very bright, or album-art-derived accent color.
 */
private fun mixColor(start: Color, end: Color, fraction: Float): Color {
    val t = fraction.coerceIn(0f, 1f)
    return Color(
        red = start.red + (end.red - start.red) * t,
        green = start.green + (end.green - start.green) * t,
        blue = start.blue + (end.blue - start.blue) * t,
        alpha = start.alpha + (end.alpha - start.alpha) * t
    )
}

private fun contrastRatio(first: Color, second: Color): Float {
    val firstLuminance = first.luminance()
    val secondLuminance = second.luminance()
    return (max(firstLuminance, secondLuminance) + 0.05f) /
        (min(firstLuminance, secondLuminance) + 0.05f)
}

private fun readableAccent(
    accent: Color,
    background: Color,
    lighten: Boolean,
    minimumContrast: Float = 4.5f
): Color {
    if (contrastRatio(accent, background) >= minimumContrast) return accent
    val target = if (lighten) Color.White else Color.Black
    var low = 0f
    var high = 1f
    repeat(22) {
        val middle = (low + high) / 2f
        val candidate = mixColor(accent, target, middle)
        if (contrastRatio(candidate, background) >= minimumContrast) high = middle
        else low = middle
    }
    return mixColor(accent, target, high)
}

private fun relatedAccent(accent: Color, hueOffset: Float): Color {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(accent.toArgb(), hsv)
    hsv[0] = (hsv[0] + hueOffset + 360f) % 360f
    // Keep the secondary and tertiary colours in the same visual family,
    // while allowing a restrained hue shift even for muted custom accents.
    hsv[1] = if (hsv[1] >= 0.12f) max(hsv[1], 0.42f).coerceAtMost(0.82f) else 0.18f
    hsv[2] = hsv[2].coerceIn(0.55f, 0.96f)
    return Color(android.graphics.Color.HSVToColor(hsv))
}

private fun accentContainer(baseSurface: Color, accent: Color, darkTheme: Boolean): Color {
    return mixColor(baseSurface, accent, if (darkTheme) 0.30f else 0.17f)
}

private fun readableOnColor(background: Color): Color {
    return if (contrastRatio(Color.White, background) >= contrastRatio(Color.Black, background)) {
        Color.White
    } else {
        Color.Black
    }
}

/**
 * D.S Music skin based on D.S Launcher.
 *
 * Existing user controls are intentionally retained: light/dark/system mode,
 * appearance presets, album-art accent, AMOLED black and density preferences.
 */
@Composable
fun DsmusicAppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicAlbumColor: Int? = null,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val prefs = remember(context) {
        context.getSharedPreferences("AppSettings", android.content.Context.MODE_PRIVATE)
    }
    val amoledEnabled by prefs.observeKey("amoled_black", false)
    val albumArtThemeEnabled by prefs.observeKey("dynamic_album_theme", false)
    val appearancePreset by prefs.observeKey("appearance_preset", "Ocean")
    val customAccentHex by prefs.observeKey("custom_accent_color", "#7C9CFF")
    val uiDensity by prefs.observeKey("ui_density", "Comfortable")
    val uiCornerStyle by prefs.observeKey("ui_corner_style", "Soft")
    val uiFontFamily by prefs.observeKey("ui_font_family", "Roboto")
    val uiFontScale by prefs.observeKey("ui_font_scale", 1f)
    val uiFontWeight by prefs.observeKey("ui_font_weight", "Default")
    val uiLetterSpacing by prefs.observeKey("ui_letter_spacing", "Normal")

    val accent = remember(appearancePreset, albumArtThemeEnabled, dynamicAlbumColor, customAccentHex) {
        if (albumArtThemeEnabled && dynamicAlbumColor != null) {
            Color(dynamicAlbumColor)
        } else {
            when (appearancePreset) {
                "Violet" -> Color(0xFFB69CFF)
                "Emerald" -> Color(0xFF62C9A2)
                "Amber" -> Color(0xFFFFC36F)
                "Rose" -> Color(0xFFFF8FA3)
                "Monochrome" -> Color(0xFFC9CDD4)
                "Crimson" -> Color(0xFFD94A65)
                "Teal" -> Color(0xFF36B9A8)
                "Cyan" -> Color(0xFF43B8D2)
                "Indigo" -> Color(0xFF6475E9)
                "Lime" -> Color(0xFF9FBF42)
                "Graphite" -> Color(0xFF8A939F)
                "Custom" -> runCatching { Color(android.graphics.Color.parseColor(customAccentHex)) }.getOrDefault(Color(0xFF7C9CFF))
                else -> Color(0xFF7C9CFF) // D.S Launcher blue
            }
        }
    }

    val surfaceForContrast = if (darkTheme) Color(0xFF0D1126) else Color.White
    val readablePrimary = remember(darkTheme, accent) {
        readableAccent(accent, surfaceForContrast, lighten = darkTheme)
    }
    val onReadablePrimary = remember(readablePrimary) { readableOnColor(readablePrimary) }

    val colorScheme = remember(darkTheme, amoledEnabled, accent, readablePrimary, onReadablePrimary) {
        val secondaryCandidate = relatedAccent(accent, 32f)
        val tertiaryCandidate = relatedAccent(accent, -32f)
        val surfaceContainerBase = if (darkTheme) {
            if (amoledEnabled) Color(0xFF080A0F) else Color(0xFF121A31)
        } else {
            Color(0xFFE5EBF3)
        }
        val secondarySurfaceBase = if (darkTheme) Color(0xFF101827) else Color(0xFFF0F3F8)
        val neutralOutline = if (darkTheme) Color(0xFF9AAAC2) else Color(0xFF687990)
        val neutralOutlineVariant = if (darkTheme) Color(0xFF8294AF) else Color(0xFF93A2B6)

        val primaryContainer = accentContainer(surfaceContainerBase, accent, darkTheme)
        val secondary = readableAccent(
            secondaryCandidate,
            surfaceForContrast,
            lighten = darkTheme
        )
        val tertiary = readableAccent(
            tertiaryCandidate,
            surfaceForContrast,
            lighten = darkTheme
        )
        val secondaryContainer = accentContainer(secondarySurfaceBase, secondaryCandidate, darkTheme)
        val tertiaryContainer = accentContainer(secondarySurfaceBase, tertiaryCandidate, darkTheme)
        val outline = readableAccent(
            mixColor(relatedAccent(accent, 18f), neutralOutline, 0.42f),
            surfaceForContrast,
            lighten = darkTheme,
            minimumContrast = 3f
        )
        val outlineVariant = readableAccent(
            mixColor(relatedAccent(accent, -18f), neutralOutlineVariant, 0.58f),
            if (darkTheme) Color(0xFF0B0F20) else Color(0xFFF7F9FC),
            lighten = darkTheme,
            minimumContrast = 3f
        )
        val surfaceVariant = mixColor(
            if (darkTheme) Color(0xFF192540) else Color(0xFFE5EBF3),
            secondaryCandidate,
            0.09f
        )
        val primaryInverse = readableAccent(
            accent,
            if (darkTheme) Color(0xFFE9EDF5) else Color(0xFF182131),
            lighten = !darkTheme
        )

        if (darkTheme) {
            val base = DsmusicColorScheme(
                primary = readablePrimary,
                onPrimary = onReadablePrimary,
                primaryContainer = primaryContainer,
                onPrimaryContainer = readableOnColor(primaryContainer),
                secondary = secondary,
                onSecondary = readableOnColor(secondary),
                secondaryContainer = secondaryContainer,
                onSecondaryContainer = readableOnColor(secondaryContainer),
                tertiary = tertiary,
                onTertiary = readableOnColor(tertiary),
                tertiaryContainer = tertiaryContainer,
                onTertiaryContainer = readableOnColor(tertiaryContainer),
                background = Color(0xFF080B1B),
                onBackground = Color(0xFFE9EDF5),
                surface = Color(0xFF0D1126),
                onSurface = Color(0xFFE9EDF5),
                surfaceVariant = surfaceVariant,
                onSurfaceVariant = Color(0xFFCAD5E5),
                surfaceTint = readablePrimary,
                outline = outline,
                outlineVariant = outlineVariant,
                error = Color(0xFFFF8C91),
                onError = Color(0xFF2B080D),
                errorContainer = Color(0xFF5B2028),
                onErrorContainer = Color(0xFFFFDADD),
                surfaceContainerLowest = Color(0xFF070A16),
                surfaceContainerLow = Color(0xFF0B0F20),
                surfaceContainer = Color(0xFF0D1126),
                surfaceContainerHigh = if (amoledEnabled) Color(0xFF080A0F) else Color(0xFF121A31),
                surfaceContainerHighest = if (amoledEnabled) Color(0xFF111722) else Color(0xFF1B2947),
                inversePrimary = primaryInverse
            )
            if (amoledEnabled) {
                base.copy(
                    background = Color.Black,
                    surface = Color.Black,
                    surfaceContainerLowest = Color.Black,
                    surfaceContainerLow = Color.Black,
                    surfaceContainer = Color.Black
                )
            } else base
        } else {
            DsmusicColorScheme(
                primary = readablePrimary,
                onPrimary = onReadablePrimary,
                primaryContainer = primaryContainer,
                onPrimaryContainer = readableOnColor(primaryContainer),
                secondary = secondary,
                onSecondary = readableOnColor(secondary),
                secondaryContainer = secondaryContainer,
                onSecondaryContainer = readableOnColor(secondaryContainer),
                tertiary = tertiary,
                onTertiary = readableOnColor(tertiary),
                tertiaryContainer = tertiaryContainer,
                onTertiaryContainer = readableOnColor(tertiaryContainer),
                background = Color(0xFFF3F6FA),
                onBackground = Color(0xFF182131),
                surface = Color.White,
                onSurface = Color(0xFF182131),
                surfaceVariant = surfaceVariant,
                onSurfaceVariant = Color(0xFF46566B),
                surfaceTint = readablePrimary,
                outline = outline,
                outlineVariant = outlineVariant,
                error = Color(0xFFB32635),
                onError = Color.White,
                errorContainer = Color(0xFFF9DADD),
                onErrorContainer = Color(0xFF410E16),
                surfaceContainerLowest = Color.White,
                surfaceContainerLow = Color(0xFFF7F9FC),
                surfaceContainer = Color(0xFFEFF3F8),
                surfaceContainerHigh = Color(0xFFE5EBF3),
                surfaceContainerHighest = Color(0xFFDCE4EF),
                inversePrimary = primaryInverse
            )
        }
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            view.context.findActivity()?.window?.let { window ->
                window.statusBarColor = android.graphics.Color.TRANSPARENT
                getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
            }
        }
    }

    val baseDensity = LocalDensity.current
    // UI density changes actual layout spacing and control dimensions, not just
    // text size. Keep the system font scale intact for accessibility.
    val densityFactor = when (uiDensity) {
        "Compact" -> 0.92f
        "Large" -> 1.08f
        else -> 1f
    }
    val adjustedDensity = remember(baseDensity, densityFactor) {
        object : androidx.compose.ui.unit.Density {
            override val density: Float = baseDensity.density * densityFactor
            override val fontScale: Float = baseDensity.fontScale
        }
    }

    CompositionLocalProvider(LocalDensity provides adjustedDensity) {
        DsmusicThemeProvider(
            colorScheme = colorScheme,
            typography = customizedTypography(uiFontFamily, uiFontScale, uiFontWeight, uiLetterSpacing),
            shapes = remember(uiCornerStyle) {
                val (xs, sm, md, lg, xl) = when (uiCornerStyle) {
                    "Tight" -> listOf(3.dp, 5.dp, 8.dp, 11.dp, 15.dp)
                    "Round" -> listOf(9.dp, 14.dp, 20.dp, 26.dp, 32.dp)
                    else -> listOf(5.dp, 9.dp, 13.dp, 18.dp, 24.dp)
                }
                DsmusicShapes(
                    extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(xs),
                    small = androidx.compose.foundation.shape.RoundedCornerShape(sm),
                    medium = androidx.compose.foundation.shape.RoundedCornerShape(md),
                    large = androidx.compose.foundation.shape.RoundedCornerShape(lg),
                    extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(xl)
                )
            },
            content = content
        )
    }
}

@Composable
fun rememberDsmusicDarkTheme(): Boolean {
    val context = LocalContext.current
    val prefs = remember(context) {
        context.getSharedPreferences("AppSettings", android.content.Context.MODE_PRIVATE)
    }
    val themeMode by prefs.observeKey("theme_mode", "system")
    val systemDark = isSystemInDarkTheme()
    return when (themeMode.lowercase()) {
        "dark" -> true
        "light" -> false
        else -> systemDark
    }
}

@Composable
fun DsmusicAppThemeFromPreferences(
    dynamicAlbumColor: Int? = null,
    content: @Composable () -> Unit
) {
    DsmusicAppTheme(
        darkTheme = rememberDsmusicDarkTheme(),
        dynamicAlbumColor = dynamicAlbumColor,
        content = content
    )
}

internal tailrec fun android.content.Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
