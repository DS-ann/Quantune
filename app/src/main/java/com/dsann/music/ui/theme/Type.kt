package com.dsann.music.ui.theme

import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.dsann.music.R

/**
 * Google Sans Flex Font Family with ROND axis set to 100%
 */
@OptIn(ExperimentalTextApi::class)
val GoogleSansFlex = FontFamily(
    Font(
        resId = R.font.google_flex,
        variationSettings = FontVariation.Settings(
            FontVariation.Setting("ROND", 100f)
        )
    )
)

/**
 * Google Sans Flex Font Family with ROND axis set to 0% (non-rounded)
 * Used specifically for lyrics to keep layout crisp.
 */
@OptIn(ExperimentalTextApi::class)
val GoogleSansFlexNonRounded = FontFamily(
    Font(
        resId = R.font.google_flex,
        variationSettings = FontVariation.Settings(
            FontVariation.Setting("ROND", 0f)
        )
    )
)

/**
 * Global Typography with Google Sans Flex (Max Roundness)
 */
val Typography = DsmusicTypography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 57.sp,
        lineHeight = 64.sp,
        letterSpacing = (-0.25).sp
    ),
    displayMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 45.sp,
        lineHeight = 52.sp,
        letterSpacing = 0.sp
    ),
    displaySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 36.sp,
        lineHeight = 44.sp,
        letterSpacing = 0.sp
    ),
    headlineLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 32.sp,
        lineHeight = 40.sp,
        letterSpacing = 0.sp
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp,
        lineHeight = 36.sp,
        letterSpacing = 0.sp
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 24.sp,
        lineHeight = 32.sp,
        letterSpacing = 0.sp
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.15.sp
    ),
    titleSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.25.sp
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.4.sp
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    )
)

/**
 * Applies app-wide type settings while retaining each text role's base size and hierarchy.
 */
fun customizedTypography(
    familyChoice: String,
    scaleChoice: Float,
    weightChoice: String,
    spacingChoice: String
): DsmusicTypography {
    val family = when (familyChoice) {
        "Cursive" -> FontFamily.Cursive
        // Android's default sans face is Roboto on supported Android versions.
        "Roboto", "System" -> FontFamily.Default
        "Sans" -> FontFamily.SansSerif
        "Serif" -> FontFamily.Serif
        // Keep compatibility with appearance Looks created by earlier builds.
        "Google Sans Flex" -> GoogleSansFlex
        "Monospace" -> FontFamily.Monospace
        else -> FontFamily.Default
    }
    val scale = scaleChoice.takeIf { it.isFinite() }?.coerceIn(0.85f, 1.30f) ?: 1f
    val spacingAdjustment = when (spacingChoice) {
        "Tight" -> (-0.15).sp
        "Wide" -> 0.2.sp
        else -> 0.sp
    }

    fun tune(style: TextStyle): TextStyle {
        val baseWeight = style.fontWeight ?: FontWeight.Normal
        val weight = when (weightChoice) {
            "Lighter" -> FontWeight((baseWeight.weight - 100).coerceAtLeast(100))
            "Bolder" -> FontWeight((baseWeight.weight + 100).coerceAtMost(900))
            else -> baseWeight
        }
        val baseSpacing = style.letterSpacing.takeUnless { it == TextUnit.Unspecified } ?: 0.sp
        return style.copy(
            fontFamily = family,
            fontSize = style.fontSize * scale,
            lineHeight = style.lineHeight * scale,
            fontWeight = weight,
            letterSpacing = (baseSpacing.value + spacingAdjustment.value).sp
        )
    }

    return Typography.copy(
        displayLarge = tune(Typography.displayLarge),
        displayMedium = tune(Typography.displayMedium),
        displaySmall = tune(Typography.displaySmall),
        headlineLarge = tune(Typography.headlineLarge),
        headlineMedium = tune(Typography.headlineMedium),
        headlineSmall = tune(Typography.headlineSmall),
        titleLarge = tune(Typography.titleLarge),
        titleMedium = tune(Typography.titleMedium),
        titleSmall = tune(Typography.titleSmall),
        titleSmallEmphasized = tune(Typography.titleSmallEmphasized),
        bodyLarge = tune(Typography.bodyLarge),
        bodyMedium = tune(Typography.bodyMedium),
        bodySmall = tune(Typography.bodySmall),
        labelLarge = tune(Typography.labelLarge),
        labelMedium = tune(Typography.labelMedium),
        labelSmall = tune(Typography.labelSmall)
    )
}
