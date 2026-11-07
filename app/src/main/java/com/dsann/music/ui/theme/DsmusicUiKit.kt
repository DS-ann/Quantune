package com.dsann.music.ui.theme

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.*
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import com.dsann.music.R
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The fixed D.S Launcher Home Studio text gradient.
 * Keep these stops in sync with BlankActivity.FixedGradientTextView in D.S-Apps.
 */
fun dsmusicHomeStudioGradientBrush(): Brush = Brush.horizontalGradient(
    colors = listOf(
        Color(0xFF53F6FF),
        Color(0xFF699DFF),
        Color(0xFFAE7EFF),
        Color(0xFFFF70CD),
        Color(0xFFFFCF7F)
    )
)

@Immutable
data class DsmusicColorScheme(
    val primary: Color = Color(0xFF7C9CFF),
    val onPrimary: Color = Color(0xFF0B1220),
    val primaryContainer: Color = Color(0xFF263652),
    val onPrimaryContainer: Color = Color(0xFFE6EDFF),
    val secondary: Color = Color(0xFFB8C6DE),
    val onSecondary: Color = Color(0xFF172131),
    val secondaryContainer: Color = Color(0xFF27364A),
    val onSecondaryContainer: Color = Color(0xFFDCE6F7),
    val tertiary: Color = Color(0xFF8CC9D8),
    val onTertiary: Color = Color(0xFF0B2228),
    val tertiaryContainer: Color = Color(0xFF23434C),
    val onTertiaryContainer: Color = Color(0xFFD6F4FA),
    val background: Color = Color(0xFF080B1B),
    val onBackground: Color = Color(0xFFE9EDF5),
    val surface: Color = Color(0xFF0D1126),
    val onSurface: Color = Color(0xFFE9EDF5),
    val surfaceVariant: Color = Color(0xFF192540),
    val onSurfaceVariant: Color = Color(0xFFB7C3D5),
    val surfaceTint: Color = Color(0xFF7C9CFF),
    val outline: Color = Color(0xFF9AADC9),
    val outlineVariant: Color = Color(0xFF61748F),
    val error: Color = Color(0xFFFF8C91),
    val onError: Color = Color(0xFF2B080D),
    val errorContainer: Color = Color(0xFF5B2028),
    val onErrorContainer: Color = Color(0xFFFFDADD),
    val surfaceContainerLowest: Color = Color(0xFF070A16),
    val surfaceContainerLow: Color = Color(0xFF0B0F20),
    val surfaceContainer: Color = Color(0xFF0D1126),
    val surfaceContainerHigh: Color = Color(0xFF121A31),
    val surfaceContainerHighest: Color = Color(0xFF1B2947),
    val inverseSurface: Color = Color(0xFFE9EDF5),
    val inverseOnSurface: Color = Color(0xFF182131),
    val inversePrimary: Color = Color(0xFF526FC1),
    val scrim: Color = Color.Black
)

@Immutable
data class DsmusicTypography(
    val displayLarge: TextStyle = TextStyle(fontSize = 57.sp, lineHeight = 64.sp),
    val displayMedium: TextStyle = TextStyle(fontSize = 45.sp, lineHeight = 52.sp),
    val displaySmall: TextStyle = TextStyle(fontSize = 36.sp, lineHeight = 44.sp),
    val headlineLarge: TextStyle = TextStyle(fontSize = 32.sp, lineHeight = 40.sp),
    val headlineMedium: TextStyle = TextStyle(fontSize = 28.sp, lineHeight = 36.sp, fontWeight = FontWeight.SemiBold),
    val headlineSmall: TextStyle = TextStyle(fontSize = 24.sp, lineHeight = 32.sp),
    val titleLarge: TextStyle = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold),
    val titleMedium: TextStyle = TextStyle(fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.Medium),
    val titleSmall: TextStyle = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
    val titleSmallEmphasized: TextStyle = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Bold),
    val bodyLarge: TextStyle = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
    val bodyMedium: TextStyle = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    val bodySmall: TextStyle = TextStyle(fontSize = 12.sp, lineHeight = 16.sp),
    val labelLarge: TextStyle = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
    val labelMedium: TextStyle = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium),
    val labelSmall: TextStyle = TextStyle(fontSize = 11.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium)
) {
    val headlineMediumEmphasized: TextStyle get() = headlineMedium.copy(fontWeight = FontWeight.Bold)
}

@Immutable
data class DsmusicShapes(
    val extraSmall: androidx.compose.ui.graphics.Shape = RoundedCornerShape(5.dp),
    val small: androidx.compose.ui.graphics.Shape = RoundedCornerShape(9.dp),
    val medium: androidx.compose.ui.graphics.Shape = RoundedCornerShape(13.dp),
    val large: androidx.compose.ui.graphics.Shape = RoundedCornerShape(18.dp),
    val extraLarge: androidx.compose.ui.graphics.Shape = RoundedCornerShape(24.dp)
)

private val LocalDsmusicColors = compositionLocalOf { DsmusicColorScheme() }
private val LocalDsmusicTypography = compositionLocalOf { DsmusicTypography() }
private val LocalDsmusicShapes = compositionLocalOf { DsmusicShapes() }
val LocalDsmusicContentColor = compositionLocalOf { Color(0xFFE9EDF5) }

object DsmusicTheme {
    val colorScheme: DsmusicColorScheme
        @Composable get() = LocalDsmusicColors.current
    val typography: DsmusicTypography
        @Composable get() = LocalDsmusicTypography.current
    val shapes: DsmusicShapes
        @Composable get() = LocalDsmusicShapes.current
}

@Composable
fun DsmusicThemeProvider(
    colorScheme: DsmusicColorScheme,
    typography: DsmusicTypography = Typography,
    shapes: DsmusicShapes = DsmusicShapes(),
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(
        LocalDsmusicColors provides colorScheme,
        LocalDsmusicTypography provides typography,
        LocalDsmusicShapes provides shapes,
        LocalDsmusicContentColor provides colorScheme.onSurface,
        content = content
    )
}

data class DsmusicButtonColors(
    val containerColor: Color,
    val contentColor: Color,
    val disabledContainerColor: Color,
    val disabledContentColor: Color
)

data class DsmusicCardColors(
    val containerColor: Color,
    val contentColor: Color,
    val disabledContainerColor: Color = containerColor,
    val disabledContentColor: Color = contentColor
)

data class DsmusicListItemColors(
    val containerColor: Color,
    val headlineColor: Color,
    val supportingColor: Color,
    val leadingIconColor: Color,
    val trailingIconColor: Color,
    val selectedContainerColor: Color = containerColor,
    val selectedContentColor: Color = headlineColor,
    val selectedLeadingContentColor: Color = leadingIconColor,
    val selectedTrailingContentColor: Color = trailingIconColor,
    val supportingContentColor: Color = supportingColor,
    val selectedSupportingContentColor: Color = supportingColor,
    val disabledContainerColor: Color = containerColor,
    val disabledContentColor: Color = headlineColor.copy(alpha = 0.4f),
    val disabledLeadingContentColor: Color = leadingIconColor.copy(alpha = 0.4f),
    val disabledTrailingContentColor: Color = trailingIconColor.copy(alpha = 0.4f)
)

data class DsmusicTextFieldColors(
    val textColor: Color,
    val containerColor: Color,
    val cursorColor: Color,
    val focusedIndicatorColor: Color,
    val unfocusedIndicatorColor: Color,
    val disabledTextColor: Color,
    val errorIndicatorColor: Color
)

data class DsmusicSliderColors(
    val activeTrackColor: Color,
    val inactiveTrackColor: Color,
    val thumbColor: Color,
    val activeTickColor: Color,
    val inactiveTickColor: Color,
    val disabledActiveTrackColor: Color,
    val disabledInactiveTrackColor: Color
)

data class DsmusicSwitchColors(
    val checkedThumbColor: Color,
    val checkedTrackColor: Color,
    val uncheckedThumbColor: Color,
    val uncheckedTrackColor: Color,
    val disabledCheckedTrackColor: Color,
    val disabledUncheckedTrackColor: Color
)

data class DsmusicListItemShapes(
    val shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(14.dp)
)
typealias ListItemShapes = DsmusicListItemShapes

object ButtonDefaults {
    val shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(14.dp)
    @Composable fun buttonColors(
        containerColor: Color = DsmusicTheme.colorScheme.primary,
        contentColor: Color = DsmusicTheme.colorScheme.onPrimary,
        disabledContainerColor: Color = DsmusicTheme.colorScheme.surfaceVariant,
        disabledContentColor: Color = DsmusicTheme.colorScheme.onSurfaceVariant
    ) = DsmusicButtonColors(containerColor, contentColor, disabledContainerColor, disabledContentColor)
    @Composable fun filledTonalButtonColors(
        containerColor: Color = DsmusicTheme.colorScheme.primaryContainer,
        contentColor: Color = DsmusicTheme.colorScheme.onPrimaryContainer
    ) = DsmusicButtonColors(containerColor, contentColor, containerColor.copy(alpha = 0.45f), contentColor.copy(alpha = 0.55f))
    @Composable fun outlinedButtonColors(
        containerColor: Color = Color.Transparent,
        contentColor: Color = DsmusicTheme.colorScheme.primary,
        disabledContainerColor: Color = Color.Transparent,
        disabledContentColor: Color = DsmusicTheme.colorScheme.onSurfaceVariant
    ) = DsmusicButtonColors(containerColor, contentColor, disabledContainerColor, disabledContentColor)
}

object CardDefaults {
    @Composable fun cardColors(
        containerColor: Color = DsmusicTheme.colorScheme.surfaceContainerLow,
        contentColor: Color = DsmusicTheme.colorScheme.onSurface,
        disabledContainerColor: Color = containerColor,
        disabledContentColor: Color = contentColor
    ) = DsmusicCardColors(containerColor, contentColor, disabledContainerColor, disabledContentColor)
    fun cardElevation(defaultElevation: Dp = 0.dp, pressedElevation: Dp = defaultElevation, focusedElevation: Dp = defaultElevation, hoveredElevation: Dp = defaultElevation, draggedElevation: Dp = defaultElevation, disabledElevation: Dp = defaultElevation): Dp = 0.dp
}

object ListItemDefaults {
    val SegmentedGap: Dp = 4.dp
    @Composable fun colors(
        containerColor: Color = Color.Transparent,
        headlineColor: Color = DsmusicTheme.colorScheme.onSurface,
        supportingColor: Color = DsmusicTheme.colorScheme.onSurfaceVariant,
        leadingIconColor: Color = DsmusicTheme.colorScheme.onSurfaceVariant,
        trailingIconColor: Color = DsmusicTheme.colorScheme.onSurfaceVariant,
        overlineColor: Color = DsmusicTheme.colorScheme.onSurfaceVariant,
        contentColor: Color? = null,
        leadingContentColor: Color? = null,
        trailingContentColor: Color? = null,
        selectedContainerColor: Color = containerColor,
        selectedContentColor: Color = contentColor ?: headlineColor,
        selectedLeadingContentColor: Color = leadingContentColor ?: leadingIconColor,
        selectedTrailingContentColor: Color = trailingContentColor ?: trailingIconColor,
        supportingContentColor: Color = supportingColor,
        selectedSupportingContentColor: Color = supportingColor,
        disabledContainerColor: Color = containerColor,
        disabledContentColor: Color = headlineColor.copy(alpha = 0.4f),
        disabledLeadingContentColor: Color = leadingIconColor.copy(alpha = 0.4f),
        disabledTrailingContentColor: Color = trailingIconColor.copy(alpha = 0.4f)
    ) = DsmusicListItemColors(
        containerColor, contentColor ?: headlineColor, supportingColor, leadingContentColor ?: leadingIconColor, trailingContentColor ?: trailingIconColor,
        selectedContainerColor, selectedContentColor, selectedLeadingContentColor, selectedTrailingContentColor,
        supportingContentColor, selectedSupportingContentColor, disabledContainerColor, disabledContentColor,
        disabledLeadingContentColor, disabledTrailingContentColor
    )
    fun shapes(): DsmusicListItemShapes = DsmusicListItemShapes()
    fun segmentedShapes(index: Int, count: Int): DsmusicListItemShapes = DsmusicListItemShapes(
        RoundedCornerShape(
            topStart = if (index == 0) 14.dp else 4.dp,
            topEnd = if (index == 0) 14.dp else 4.dp,
            bottomStart = if (index == count - 1) 14.dp else 4.dp,
            bottomEnd = if (index == count - 1) 14.dp else 4.dp
        )
    )
}

object FilterChipDefaults {
    @Composable fun filterChipColors(
        containerColor: Color = DsmusicTheme.colorScheme.surfaceContainerLow,
        labelColor: Color = DsmusicTheme.colorScheme.onSurfaceVariant,
        selectedContainerColor: Color = DsmusicTheme.colorScheme.primaryContainer,
        selectedLabelColor: Color = DsmusicTheme.colorScheme.onPrimaryContainer,
        disabledContainerColor: Color = DsmusicTheme.colorScheme.surfaceVariant
    ) = DsmusicButtonColors(selectedContainerColor, selectedLabelColor, disabledContainerColor, labelColor)
}

object TextFieldDefaults {
    @Composable fun colors(
        focusedTextColor: Color = DsmusicTheme.colorScheme.onSurface,
        unfocusedTextColor: Color = DsmusicTheme.colorScheme.onSurface,
        disabledTextColor: Color = DsmusicTheme.colorScheme.onSurfaceVariant,
        errorTextColor: Color = DsmusicTheme.colorScheme.error,
        focusedContainerColor: Color = DsmusicTheme.colorScheme.surfaceContainerLow,
        unfocusedContainerColor: Color = DsmusicTheme.colorScheme.surfaceContainerLow,
        disabledContainerColor: Color = DsmusicTheme.colorScheme.surfaceContainerLow,
        errorContainerColor: Color = DsmusicTheme.colorScheme.surfaceContainerLow,
        cursorColor: Color = DsmusicTheme.colorScheme.primary,
        errorCursorColor: Color = DsmusicTheme.colorScheme.error,
        focusedIndicatorColor: Color = DsmusicTheme.colorScheme.primary,
        unfocusedIndicatorColor: Color = DsmusicTheme.colorScheme.outlineVariant,
        disabledIndicatorColor: Color = DsmusicTheme.colorScheme.outlineVariant,
        errorIndicatorColor: Color = DsmusicTheme.colorScheme.error,
        focusedLabelColor: Color = DsmusicTheme.colorScheme.primary,
        unfocusedLabelColor: Color = DsmusicTheme.colorScheme.onSurfaceVariant,
        disabledLabelColor: Color = DsmusicTheme.colorScheme.onSurfaceVariant,
        errorLabelColor: Color = DsmusicTheme.colorScheme.error
    ) = DsmusicTextFieldColors(focusedTextColor, focusedContainerColor, cursorColor, focusedIndicatorColor, unfocusedIndicatorColor, disabledTextColor, errorIndicatorColor)
}
object OutlinedTextFieldDefaults {
    @Composable fun colors(
        focusedTextColor: Color = DsmusicTheme.colorScheme.onSurface,
        unfocusedTextColor: Color = DsmusicTheme.colorScheme.onSurface,
        disabledTextColor: Color = DsmusicTheme.colorScheme.onSurfaceVariant,
        errorTextColor: Color = DsmusicTheme.colorScheme.error,
        focusedContainerColor: Color = Color.Transparent,
        unfocusedContainerColor: Color = Color.Transparent,
        disabledContainerColor: Color = Color.Transparent,
        errorContainerColor: Color = Color.Transparent,
        cursorColor: Color = DsmusicTheme.colorScheme.primary,
        errorCursorColor: Color = DsmusicTheme.colorScheme.error,
        focusedBorderColor: Color = DsmusicTheme.colorScheme.primary,
        unfocusedBorderColor: Color = DsmusicTheme.colorScheme.outline,
        disabledBorderColor: Color = DsmusicTheme.colorScheme.outlineVariant,
        errorBorderColor: Color = DsmusicTheme.colorScheme.error,
        focusedLabelColor: Color = DsmusicTheme.colorScheme.primary,
        unfocusedLabelColor: Color = DsmusicTheme.colorScheme.onSurfaceVariant
    ) = DsmusicTextFieldColors(focusedTextColor, focusedContainerColor, cursorColor, focusedBorderColor, unfocusedBorderColor, disabledTextColor, errorBorderColor)
}

object SliderDefaults {
    @Composable fun colors(
        thumbColor: Color = DsmusicTheme.colorScheme.primary,
        activeTrackColor: Color = DsmusicTheme.colorScheme.primary,
        inactiveTrackColor: Color = DsmusicTheme.colorScheme.surfaceVariant,
        activeTickColor: Color = activeTrackColor,
        inactiveTickColor: Color = inactiveTrackColor,
        disabledThumbColor: Color = DsmusicTheme.colorScheme.onSurfaceVariant,
        disabledActiveTrackColor: Color = activeTrackColor.copy(alpha = 0.38f),
        disabledInactiveTrackColor: Color = inactiveTrackColor.copy(alpha = 0.38f)
    ) = DsmusicSliderColors(activeTrackColor, inactiveTrackColor, thumbColor, activeTickColor, inactiveTickColor, disabledActiveTrackColor, disabledInactiveTrackColor)
}

object SwitchDefaults {
    @Composable fun colors(
        checkedThumbColor: Color = DsmusicTheme.colorScheme.onPrimaryContainer,
        checkedTrackColor: Color = DsmusicTheme.colorScheme.primaryContainer,
        uncheckedThumbColor: Color = DsmusicTheme.colorScheme.onSurfaceVariant,
        uncheckedTrackColor: Color = DsmusicTheme.colorScheme.surfaceVariant,
        disabledCheckedTrackColor: Color = checkedTrackColor.copy(alpha = 0.3f),
        disabledUncheckedTrackColor: Color = uncheckedTrackColor.copy(alpha = 0.3f)
    ) = DsmusicSwitchColors(checkedThumbColor, checkedTrackColor, uncheckedThumbColor, uncheckedTrackColor, disabledCheckedTrackColor, disabledUncheckedTrackColor)
}

object ToggleButtonDefaults {
    @Composable fun toggleButtonColors(
        containerColor: Color = DsmusicTheme.colorScheme.surfaceContainerLow,
        contentColor: Color = DsmusicTheme.colorScheme.onSurface,
        checkedContainerColor: Color = DsmusicTheme.colorScheme.primaryContainer,
        checkedContentColor: Color = DsmusicTheme.colorScheme.onPrimaryContainer,
        disabledContainerColor: Color = DsmusicTheme.colorScheme.surfaceVariant,
        disabledContentColor: Color = DsmusicTheme.colorScheme.onSurfaceVariant
    ) = DsmusicButtonColors(checkedContainerColor, checkedContentColor, disabledContainerColor, disabledContentColor)
}

object ButtonGroupDefaults {
    fun connectedLeadingButtonShapes() = RoundedCornerShape(topStart = 12.dp, bottomStart = 12.dp, topEnd = 4.dp, bottomEnd = 4.dp)
    fun connectedMiddleButtonShapes() = RoundedCornerShape(4.dp)
    fun connectedTrailingButtonShapes() = RoundedCornerShape(topStart = 4.dp, bottomStart = 4.dp, topEnd = 12.dp, bottomEnd = 12.dp)
}

object IconButtonDefaults {
    val filledShape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(12.dp)
}

object FloatingActionButtonDefaults {
    fun elevation(defaultElevation: Dp = 0.dp, pressedElevation: Dp = defaultElevation, focusedElevation: Dp = defaultElevation, hoveredElevation: Dp = defaultElevation): Dp = 0.dp
}

object TopAppBarDefaults {
    @Composable fun topAppBarColors(
        containerColor: Color = DsmusicTheme.colorScheme.surface,
        scrolledContainerColor: Color = containerColor,
        navigationIconContentColor: Color = DsmusicTheme.colorScheme.onSurface,
        titleContentColor: Color = DsmusicTheme.colorScheme.onSurface,
        actionIconContentColor: Color = DsmusicTheme.colorScheme.onSurface
    ) = DsmusicTopAppBarColors(containerColor, titleContentColor, navigationIconContentColor, actionIconContentColor)
    fun exitUntilCollapsedScrollBehavior() = DsmusicTopAppBarScrollBehavior()
}
data class DsmusicTopAppBarColors(val containerColor: Color, val titleContentColor: Color, val navigationIconContentColor: Color, val actionIconContentColor: Color)
class DsmusicTopAppBarScrollBehavior { val nestedScrollConnection: androidx.compose.ui.input.nestedscroll.NestedScrollConnection = object : androidx.compose.ui.input.nestedscroll.NestedScrollConnection {} }

object BottomSheetDefaults {
    @Composable fun DragHandle(modifier: Modifier = Modifier, color: Color = DsmusicTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f), width: Dp = 32.dp, height: Dp = 4.dp) {
        Box(modifier.width(width).height(height).clip(CircleShape).background(color))
    }
}

object WavyProgressIndicatorDefaults {
    val ProgressAnimationSpec: androidx.compose.animation.core.FiniteAnimationSpec<Float> = tween(durationMillis = 240)
    val LinearDeterminateWavelength: Dp = 48.dp
    fun indicatorAmplitude(progress: Float): Float = 0.2f + 0.8f * progress.coerceIn(0f, 1f)
    @Composable fun colors(indicatorColor: Color = DsmusicTheme.colorScheme.primary, trackColor: Color = DsmusicTheme.colorScheme.surfaceVariant) = Pair(indicatorColor, trackColor)
}

@Composable
fun Text(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontStyle: FontStyle? = null,
    fontWeight: FontWeight? = null,
    fontFamily: FontFamily? = null,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    textDecoration: TextDecoration? = null,
    textAlign: TextAlign? = null,
    lineHeight: TextUnit = TextUnit.Unspecified,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    minLines: Int = 1,
    onTextLayout: (TextLayoutResult) -> Unit = {},
    style: TextStyle = DsmusicTheme.typography.bodyLarge
) {
    val resolved = style.copy(
        color = when {
            color != Color.Unspecified -> color
            style.brush != null -> Color.Unspecified
            style.color != Color.Unspecified -> style.color
            else -> LocalDsmusicContentColor.current
        },
        fontSize = fontSize.takeUnless { it == TextUnit.Unspecified } ?: style.fontSize,
        fontStyle = fontStyle ?: style.fontStyle,
        fontWeight = fontWeight ?: style.fontWeight,
        fontFamily = fontFamily ?: style.fontFamily,
        letterSpacing = letterSpacing.takeUnless { it == TextUnit.Unspecified } ?: style.letterSpacing,
        textDecoration = textDecoration ?: style.textDecoration,
        textAlign = textAlign ?: style.textAlign,
        lineHeight = lineHeight.takeUnless { it == TextUnit.Unspecified } ?: style.lineHeight
    )
    BasicText(text = text, modifier = modifier, style = resolved, onTextLayout = onTextLayout, overflow = overflow, softWrap = softWrap, maxLines = maxLines, minLines = minLines)
}

@Composable
fun Text(
    text: AnnotatedString,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontStyle: FontStyle? = null,
    fontWeight: FontWeight? = null,
    fontFamily: FontFamily? = null,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    textDecoration: TextDecoration? = null,
    textAlign: TextAlign? = null,
    lineHeight: TextUnit = TextUnit.Unspecified,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    minLines: Int = 1,
    onTextLayout: (TextLayoutResult) -> Unit = {},
    style: TextStyle = DsmusicTheme.typography.bodyLarge
) {
    val resolved = style.copy(
        color = when {
            color != Color.Unspecified -> color
            style.brush != null -> Color.Unspecified
            style.color != Color.Unspecified -> style.color
            else -> LocalDsmusicContentColor.current
        },
        fontSize = fontSize.takeUnless { it == TextUnit.Unspecified } ?: style.fontSize,
        fontStyle = fontStyle ?: style.fontStyle,
        fontWeight = fontWeight ?: style.fontWeight,
        fontFamily = fontFamily ?: style.fontFamily,
        letterSpacing = letterSpacing.takeUnless { it == TextUnit.Unspecified } ?: style.letterSpacing,
        textDecoration = textDecoration ?: style.textDecoration,
        textAlign = textAlign ?: style.textAlign,
        lineHeight = lineHeight.takeUnless { it == TextUnit.Unspecified } ?: style.lineHeight
    )
    BasicText(text = text, modifier = modifier, style = resolved, onTextLayout = onTextLayout, overflow = overflow, softWrap = softWrap, maxLines = maxLines, minLines = minLines)
}

@Composable
fun Icon(imageVector: ImageVector, contentDescription: String? = null, modifier: Modifier = Modifier, tint: Color = LocalDsmusicContentColor.current) {
    // Use D.S Music's own semantic icon drawings for the common app actions. This
    // keeps every screen on one icon language instead of mixing default Material glyphs.
    val customResource = when (imageVector.name.substringAfterLast('.')) {
        "Search" -> R.drawable.ic_search
        "Explore" -> R.drawable.ic_explore
        "LibraryMusic" -> R.drawable.ic_library_music
        "MusicNote" -> R.drawable.ic_music_note
        "Settings" -> R.drawable.ic_settings
        "MoreVert", "Menu" -> R.drawable.ic_more
        "ChevronRight" -> R.drawable.ic_chevron_right
        "ChevronLeft" -> R.drawable.ic_chevron_left
        "ArrowBack" -> R.drawable.ic_arrow_back
        "Close", "Clear" -> R.drawable.ic_close
        "Delete", "DeleteOutline" -> R.drawable.ic_delete
        "Edit" -> R.drawable.ic_edit
        "Add" -> R.drawable.ic_add
        "Share" -> R.drawable.ic_share
        "Download" -> R.drawable.ic_download
        "PlayArrow" -> R.drawable.ic_play
        "Pause" -> R.drawable.ic_pause
        "PlayCircle" -> R.drawable.ic_play_circle
        "Favorite" -> R.drawable.ic_favorite
        "FavoriteBorder" -> R.drawable.ic_favorite_border
        "ThumbDown" -> R.drawable.ic_thumb_down
        "ThumbUp" -> R.drawable.ic_thumb_up
        "Info" -> R.drawable.ic_info
        "Shuffle" -> R.drawable.ic_shuffle
        "Repeat" -> R.drawable.ic_repeat
        "RepeatOne" -> R.drawable.ic_repeat_one
        "History" -> R.drawable.ic_history
        "Timer", "AccessTime" -> R.drawable.ic_timer
        "Notifications" -> R.drawable.ic_notifications
        "NightsStay", "Bedtime" -> R.drawable.ic_bedtime
        "AccountCircle", "Person", "PersonOutline" -> R.drawable.ic_settings_account
        "PlaylistAdd" -> R.drawable.ic_playlist_add
        "Folder" -> R.drawable.ic_folder
        "Album" -> R.drawable.ic_album
        "Refresh" -> R.drawable.ic_update
        "Done", "Check" -> R.drawable.ic_check
        "Warning" -> R.drawable.ic_warning
        "Cloud" -> R.drawable.ic_cloud
        "CloudSync" -> R.drawable.ic_cloud_sync
        "BugReport" -> R.drawable.ic_settings_feedback
        "Palette" -> R.drawable.ic_settings_customize
        "SettingsBackupRestore" -> R.drawable.ic_settings_backup
        "TouchApp" -> R.drawable.ic_settings_gestures
        "FastForward", "SkipNext" -> R.drawable.ic_skip_next
        "FastRewind", "SkipPrevious" -> R.drawable.ic_skip_previous
        else -> null
    }
    val iconPainter = if (customResource != null) {
        painterResource(customResource)
    } else {
        rememberVectorPainter(imageVector)
    }
    Image(
        painter = iconPainter,
        contentDescription = contentDescription,
        modifier = modifier,
        colorFilter = if (tint == Color.Unspecified) null
            else androidx.compose.ui.graphics.ColorFilter.tint(tint)
    )
}
@Composable
fun Icon(painter: Painter, contentDescription: String? = null, modifier: Modifier = Modifier, tint: Color = LocalDsmusicContentColor.current) {
    Image(painter = painter, contentDescription = contentDescription, modifier = modifier, colorFilter = if (tint == Color.Unspecified) null else androidx.compose.ui.graphics.ColorFilter.tint(tint))
}

@Composable
fun IconButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, interactionSource: MutableInteractionSource? = null, content: @Composable BoxScope.() -> Unit) {
    DsmusicIconButton(onClick = onClick, modifier = modifier, enabled = enabled, interactionSource = interactionSource, content = content)
}

@Composable
fun Button(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: androidx.compose.ui.graphics.Shape = ButtonDefaults.shape,
    colors: DsmusicButtonColors = ButtonDefaults.buttonColors(),
    elevation: Dp? = null,
    border: androidx.compose.foundation.BorderStroke? = null,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
    interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.() -> Unit
) = DsmusicButton(onClick, modifier, enabled, shape, colors, border, contentPadding, elevation, interactionSource, content)

@Composable
fun OutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: androidx.compose.ui.graphics.Shape = ButtonDefaults.shape,
    colors: DsmusicButtonColors = DsmusicButtonColors(Color.Transparent, DsmusicTheme.colorScheme.primary, Color.Transparent, DsmusicTheme.colorScheme.onSurfaceVariant),
    border: androidx.compose.foundation.BorderStroke? = null,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
    interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.() -> Unit
) = DsmusicOutlinedButton(onClick, modifier, enabled, shape, colors, border, contentPadding, interactionSource, content)

@Composable
fun FilledTonalButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: androidx.compose.ui.graphics.Shape = ButtonDefaults.shape,
    colors: DsmusicButtonColors = ButtonDefaults.filledTonalButtonColors(),
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
    interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.() -> Unit
) = DsmusicButton(onClick, modifier, enabled, shape, colors, null, contentPadding, null, interactionSource, content)

@Composable
fun TextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: DsmusicButtonColors = ButtonDefaults.buttonColors(containerColor = Color.Transparent, contentColor = DsmusicTheme.colorScheme.primary, disabledContainerColor = Color.Transparent),
    contentPadding: PaddingValues = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
    interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.() -> Unit
) = DsmusicTextButton(onClick, modifier, enabled, colors, contentPadding, interactionSource, content)

@Composable
fun Card(
    modifier: Modifier = Modifier,
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(18.dp),
    colors: DsmusicCardColors = CardDefaults.cardColors(),
    elevation: Dp = 0.dp,
    border: androidx.compose.foundation.BorderStroke? = null,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    content: @Composable ColumnScope.() -> Unit
) {
    DsmusicCard(modifier = modifier, shape = shape, colors = colors, elevation = elevation, border = border, onClick = if (enabled) onClick else null, content = content)
}

@Composable
fun Surface(
    modifier: Modifier = Modifier,
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(16.dp),
    color: Color = DsmusicTheme.colorScheme.surfaceContainer,
    contentColor: Color = DsmusicTheme.colorScheme.onSurface,
    tonalElevation: Dp = 0.dp,
    shadowElevation: Dp = 0.dp,
    border: androidx.compose.foundation.BorderStroke? = null,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    content: @Composable BoxScope.() -> Unit
) {
    DsmusicSurface(modifier = modifier, shape = shape, color = color, contentColor = contentColor, tonalElevation = tonalElevation, shadowElevation = shadowElevation, border = border, onClick = if (enabled) onClick else null, content = content)
}

@Composable
fun ListItem(
    modifier: Modifier = Modifier,
    headlineContent: @Composable () -> Unit = {},
    supportingContent: (@Composable () -> Unit)? = null,
    overlineContent: (@Composable () -> Unit)? = null,
    leadingContent: (@Composable () -> Unit)? = null,
    trailingContent: (@Composable () -> Unit)? = null,
    colors: DsmusicListItemColors = ListItemDefaults.colors(),
    shapes: DsmusicListItemShapes = DsmusicListItemShapes(),
    selected: Boolean = false,
    onClick: (() -> Unit)? = null,
    interactionSource: MutableInteractionSource? = null,
    contentPadding: PaddingValues = PaddingValues(horizontal = 13.dp, vertical = 10.dp),
    tonalElevation: Dp = 0.dp,
    shadowElevation: Dp = 0.dp,
    content: @Composable () -> Unit = headlineContent
) {
    val headlineColor = if (selected) colors.selectedContentColor else colors.headlineColor
    CompositionLocalProvider(LocalDsmusicContentColor provides headlineColor) {
        DsmusicListItem(
            modifier = modifier,
            onClick = onClick,
            headlineContent = content,
            overlineContent = overlineContent,
            supportingContent = supportingContent?.let { { CompositionLocalProvider(LocalDsmusicContentColor provides colors.supportingContentColor) { it() } } },
            leadingContent = leadingContent,
            trailingContent = trailingContent,
            contentPadding = contentPadding,
            colors = colors,
            shapes = shapes.shape
        )
    }
}

@Composable
fun FilterChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(10.dp),
    colors: DsmusicButtonColors = FilterChipDefaults.filterChipColors()
) {
    DsmusicFilterChip(selected, onClick, label, modifier, enabled, shape, colors, null, leadingIcon, trailingIcon)
}

@Composable
fun Switch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: DsmusicSwitchColors = SwitchDefaults.colors()
) {
    val thumb = if (checked) colors.checkedThumbColor else colors.uncheckedThumbColor
    val track = if (checked) colors.checkedTrackColor else colors.uncheckedTrackColor
    DsmusicSwitchControl(checked, onCheckedChange, modifier, enabled, colors)
}

@Composable
fun Checkbox(checked: Boolean, onCheckedChange: ((Boolean) -> Unit)?, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val scheme = DsmusicTheme.colorScheme
    Box(
        modifier = modifier.size(24.dp).clip(RoundedCornerShape(5.dp))
            .background(if (checked) scheme.primaryContainer else Color.Transparent)
            .border(1.6.dp, if (checked) scheme.primary else scheme.outline, RoundedCornerShape(5.dp))
            .clickable(enabled = enabled && onCheckedChange != null) { onCheckedChange?.invoke(!checked) },
        contentAlignment = Alignment.Center
    ) {
        if (checked) Canvas(Modifier.size(16.dp)) {
            val path = androidx.compose.ui.graphics.Path().apply {
                moveTo(size.width * 0.18f, size.height * 0.52f)
                lineTo(size.width * 0.42f, size.height * 0.76f)
                lineTo(size.width * 0.84f, size.height * 0.24f)
            }
            drawPath(path, scheme.onPrimaryContainer, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
        }
    }
}

@Composable
fun RadioButton(selected: Boolean, onClick: (() -> Unit)?, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val scheme = DsmusicTheme.colorScheme
    Box(
        modifier = modifier.size(24.dp).clip(CircleShape).clickable(enabled = enabled && onClick != null) { onClick?.invoke() }.padding(4.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(color = if (selected) scheme.primary else scheme.outline, style = Stroke(width = 2.dp.toPx()))
            if (selected) drawCircle(color = scheme.primary, radius = size.minDimension * 0.25f)
        }
    }
}


@Composable
fun HorizontalDivider(modifier: Modifier = Modifier, thickness: Dp = 1.dp, color: Color = DsmusicTheme.colorScheme.outlineVariant.copy(alpha = 0.78f)) {
    Box(modifier.fillMaxWidth().height(thickness).background(color))
}

interface ButtonGroupScope : RowScope {
    @Composable fun customItem(buttonGroupContent: @Composable RowScope.() -> Unit, menuContent: @Composable () -> Unit = {})
}

@Composable
fun ButtonGroup(
    modifier: Modifier = Modifier,
    expandedRatio: Float = 0.3f,
    overflowIndicator: @Composable () -> Unit = {},
    content: @Composable ButtonGroupScope.() -> Unit
) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        val rowScope = this
        val groupScope = object : ButtonGroupScope, RowScope by rowScope {
            @Composable override fun customItem(buttonGroupContent: @Composable RowScope.() -> Unit, menuContent: @Composable () -> Unit) {
                Row(Modifier.weight(1f)) { buttonGroupContent() }
            }
        }
        groupScope.content()
    }
}

fun Modifier.animateWidth(interactionSource: MutableInteractionSource): Modifier = this

@Composable
fun Scaffold(
    modifier: Modifier = Modifier,
    topBar: @Composable () -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    snackbarHost: @Composable () -> Unit = {},
    floatingActionButton: @Composable BoxScope.() -> Unit = {},
    containerColor: Color = DsmusicTheme.colorScheme.background,
    contentColor: Color = DsmusicTheme.colorScheme.onBackground,
    contentWindowInsets: WindowInsets = WindowInsets(0, 0, 0, 0),
    bottomBarOverlay: Boolean = false,
    content: @Composable (PaddingValues) -> Unit
) {
    Box(modifier.fillMaxSize().background(containerColor)) {
        Column(Modifier.fillMaxSize()) {
            topBar()
            Box(Modifier.weight(1f).fillMaxWidth()) {
                CompositionLocalProvider(LocalDsmusicContentColor provides contentColor) {
                    content(PaddingValues())
                }
                if (bottomBarOverlay) {
                    // Draw the dock after the tab content so its floating surface sits above
                    // the page. The player sheet is kept in the page overlay and hides the
                    // dock while expanding to preserve the full-screen player layering.
                    Box(Modifier.align(Alignment.BottomCenter)) {
                        bottomBar()
                    }
                }
            }
            if (!bottomBarOverlay) {
                bottomBar()
            }
        }
        Box(Modifier.align(Alignment.BottomEnd)) { floatingActionButton() }
        Box(Modifier.align(Alignment.BottomCenter)) { snackbarHost() }
    }
}

@Composable
fun TopAppBar(
    title: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    windowInsets: WindowInsets = WindowInsets(0, 0, 0, 0),
    colors: DsmusicTopAppBarColors = TopAppBarDefaults.topAppBarColors(),
    scrollBehavior: DsmusicTopAppBarScrollBehavior? = null,
    expandedHeight: Dp = 64.dp
) {
    Row(
        modifier = modifier.fillMaxWidth().heightIn(min = expandedHeight).background(colors.containerColor).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        CompositionLocalProvider(LocalDsmusicContentColor provides colors.navigationIconContentColor) { navigationIcon() }
        Box(Modifier.weight(1f)) {
            CompositionLocalProvider(LocalDsmusicContentColor provides colors.titleContentColor) { title() }
        }
        CompositionLocalProvider(LocalDsmusicContentColor provides colors.actionIconContentColor) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), content = actions)
        }
    }
}

@Composable
fun FloatingActionButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: androidx.compose.ui.graphics.Shape = CircleShape,
    containerColor: Color = DsmusicTheme.colorScheme.primaryContainer,
    contentColor: Color = DsmusicTheme.colorScheme.onPrimaryContainer,
    elevation: Dp = 0.dp,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.() -> Unit
) {
    DsmusicButton(onClick, modifier, true, shape, DsmusicButtonColors(containerColor, contentColor, containerColor, contentColor), null, PaddingValues(16.dp), elevation, interactionSource, content)
}

data class DsmusicNavigationBarItemColors(
    val selectedIconColor: Color,
    val unselectedIconColor: Color,
    val selectedTextColor: Color,
    val unselectedTextColor: Color,
    val indicatorColor: Color
)

object NavigationBarItemDefaults {
    @Composable fun colors(
        selectedIconColor: Color = DsmusicTheme.colorScheme.primary,
        unselectedIconColor: Color = DsmusicTheme.colorScheme.onSurfaceVariant,
        selectedTextColor: Color = selectedIconColor,
        unselectedTextColor: Color = unselectedIconColor,
        indicatorColor: Color = DsmusicTheme.colorScheme.primaryContainer
    ) = DsmusicNavigationBarItemColors(selectedIconColor, unselectedIconColor, selectedTextColor, unselectedTextColor, indicatorColor)
}

@Composable
fun NavigationBar(
    modifier: Modifier = Modifier,
    containerColor: Color = DsmusicTheme.colorScheme.surface,
    contentColor: Color = DsmusicTheme.colorScheme.onSurface,
    tonalElevation: Dp = 0.dp,
    windowInsets: WindowInsets = WindowInsets(0, 0, 0, 0),
    content: @Composable RowScope.() -> Unit
) {
    Row(modifier.fillMaxWidth().background(containerColor).padding(horizontal = 8.dp, vertical = 5.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), content = content)
}

@Composable
fun RowScope.NavigationBarItem(
    selected: Boolean,
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    label: (@Composable () -> Unit)? = null,
    alwaysShowLabel: Boolean = true,
    colors: DsmusicNavigationBarItemColors = NavigationBarItemDefaults.colors()
) {
    val iconColor = if (selected) colors.selectedIconColor else colors.unselectedIconColor
    Column(
        modifier = modifier.weight(1f).clip(RoundedCornerShape(12.dp))
            .background(if (selected) colors.indicatorColor.copy(alpha = 0.32f) else Color.Transparent)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        CompositionLocalProvider(LocalDsmusicContentColor provides iconColor) { icon() }
        if (label != null && (selected || alwaysShowLabel)) {
            CompositionLocalProvider(LocalDsmusicContentColor provides if (selected) colors.selectedTextColor else colors.unselectedTextColor) { label() }
        }
    }
}

@Composable
fun AlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: @Composable (() -> Unit)? = null,
    title: @Composable (() -> Unit)? = null,
    text: @Composable (() -> Unit)? = null,
    icon: @Composable (() -> Unit)? = null,
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(20.dp),
    containerColor: Color = DsmusicTheme.colorScheme.surfaceContainerHigh,
    iconContentColor: Color = DsmusicTheme.colorScheme.primary,
    titleContentColor: Color = DsmusicTheme.colorScheme.onSurface,
    textContentColor: Color = DsmusicTheme.colorScheme.onSurfaceVariant,
    tonalElevation: Dp = 0.dp,
    properties: DialogProperties = DialogProperties()
) {
    Dialog(onDismissRequest = onDismissRequest, properties = properties) {
        Column(
            modifier = modifier.fillMaxWidth().clip(shape).background(containerColor).border(1.dp, DsmusicTheme.colorScheme.outlineVariant.copy(alpha = 0.86f), shape).padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (icon != null) CompositionLocalProvider(LocalDsmusicContentColor provides iconContentColor) { Box(Modifier.align(Alignment.CenterHorizontally)) { icon() } }
            if (title != null) CompositionLocalProvider(LocalDsmusicContentColor provides titleContentColor) { title() }
            if (text != null) CompositionLocalProvider(LocalDsmusicContentColor provides textContentColor) { text() }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                dismissButton?.invoke()
                confirmButton()
            }
        }
    }
}

class DsmusicSheetState(val skipPartiallyExpanded: Boolean = true) {
    var isVisible by mutableStateOf(true)
    suspend fun hide() { isVisible = false }
    suspend fun show() { isVisible = true }
    suspend fun expand() { isVisible = true }
}
@Composable
fun rememberModalBottomSheetState(skipPartiallyExpanded: Boolean = true): DsmusicSheetState = remember { DsmusicSheetState(skipPartiallyExpanded) }

data class DsmusicModalSheetGeometry(
    val width: Dp,
    val centerOffsetX: Dp
)

/**
 * Match contextual windows to the landscape mini-player: half of the usable tab area,
 * centered within that area rather than underneath the left navigation rail.
 */
@Composable
fun rememberDsmusicModalSheetGeometry(): DsmusicModalSheetGeometry {
    val configuration = LocalConfiguration.current
    val context = LocalContext.current
    val layoutDirection = LocalLayoutDirection.current
    val preferences = remember(context) {
        context.getSharedPreferences("AppSettings", android.content.Context.MODE_PRIVATE)
    }
    val navSize = remember(preferences) { preferences.getString("nav_bar_size", "Standard") ?: "Standard" }
    val navStyle = remember(preferences) { preferences.getString("nav_bar_style", "Floating") ?: "Floating" }
    val useNavigationRail = configuration.screenWidthDp >= 600
    val railWidth = when (navSize) {
        "Compact" -> 68.dp
        "Tall" -> 84.dp
        else -> 76.dp
    }
    val outerGap = if (navStyle == "Docked") 0.dp else 12.dp
    val systemStartInset = if (useNavigationRail) {
        WindowInsets.navigationBars.asPaddingValues().calculateStartPadding(layoutDirection)
    } else 0.dp
    val totalRailInset = if (useNavigationRail) systemStartInset + railWidth + outerGap else 0.dp
    val usableTabWidth = (configuration.screenWidthDp.dp - totalRailInset).coerceAtLeast(0.dp)
    return DsmusicModalSheetGeometry(
        width = usableTabWidth * 0.5f,
        centerOffsetX = if (layoutDirection == LayoutDirection.Ltr) {
            totalRailInset * 0.5f
        } else {
            -(totalRailInset * 0.5f)
        }
    )
}

@Composable
fun ModalBottomSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: DsmusicSheetState = rememberModalBottomSheetState(),
    sheetMaxWidth: Dp = Dp.Unspecified,
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp),
    containerColor: Color = DsmusicTheme.colorScheme.surfaceContainerHigh,
    contentColor: Color = DsmusicTheme.colorScheme.onSurface,
    tonalElevation: Dp = 0.dp,
    scrimColor: Color = Color.Black.copy(alpha = 0.42f),
    dragHandle: (@Composable () -> Unit)? = { BottomSheetDefaults.DragHandle() },
    windowInsets: WindowInsets = WindowInsets(0, 0, 0, 0),
    properties: DialogProperties = DialogProperties(usePlatformDefaultWidth = false),
    centeredLandscape: Boolean = false,
    centerOffsetX: Dp = 0.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    val isLandscapeWindow = LocalConfiguration.current.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val useCenteredLandscape = centeredLandscape && isLandscapeWindow
    val actualSheetShape = if (useCenteredLandscape) RoundedCornerShape(22.dp) else shape
    if (sheetState.isVisible) {
        Dialog(onDismissRequest = { onDismissRequest(); sheetState.isVisible = false }, properties = properties) {
            Box(
                Modifier.fillMaxSize(),
                contentAlignment = if (useCenteredLandscape) Alignment.Center else Alignment.BottomCenter
            ) {
                Box(Modifier.fillMaxSize().background(scrimColor).clickable { onDismissRequest(); sheetState.isVisible = false })
                Column(
                    modifier = modifier
                        .widthIn(max = sheetMaxWidth)
                        .fillMaxWidth()
                        .then(if (useCenteredLandscape) Modifier.offset(x = centerOffsetX) else Modifier)
                        .clip(actualSheetShape)
                        .background(containerColor)
                        .border(1.25.dp, DsmusicTheme.colorScheme.outlineVariant.copy(alpha = 0.88f), actualSheetShape)
                        .padding(bottom = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    dragHandle?.invoke()
                    CompositionLocalProvider(LocalDsmusicContentColor provides contentColor) { content() }
                }
            }
        }
    }
}

class DsmusicPullToRefreshState {
    var progress by mutableFloatStateOf(0f)
    internal var pullDistancePx by mutableFloatStateOf(0f)
    internal var peakPullDistancePx by mutableFloatStateOf(0f)
    internal var reverseDistancePx by mutableFloatStateOf(0f)
    internal var reversedAfterThreshold by mutableStateOf(false)
}
@Composable fun rememberPullToRefreshState(): DsmusicPullToRefreshState = remember { DsmusicPullToRefreshState() }

object PullToRefreshDefaults {
    @Composable fun Indicator(state: DsmusicPullToRefreshState, isRefreshing: Boolean, modifier: Modifier = Modifier) {
        PullRefreshIndicator(modifier, state, isRefreshing)
    }
    @Composable fun LoadingIndicator(state: DsmusicPullToRefreshState, isRefreshing: Boolean, modifier: Modifier = Modifier) {
        PullRefreshIndicator(modifier, state, isRefreshing)
    }
}
@Composable
private fun PullRefreshIndicator(
    modifier: Modifier,
    state: DsmusicPullToRefreshState,
    isRefreshing: Boolean
) {
    val accent = DsmusicTheme.colorScheme.primary
    val isArmed = state.progress >= 1f
    if (isRefreshing || state.progress > 0.05f) {
        Box(
            modifier.padding(8.dp).size(28.dp).clip(CircleShape)
                .background(accent.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center
        ) {
            if (isRefreshing) {
                LoadingIndicator(
                    modifier = Modifier.size(17.dp),
                    color = accent
                )
            } else {
                // The arrow remains visible while pulling. It flips only when the
                // release threshold is reached, and flips back immediately if the
                // user reverses the gesture before lifting their finger.
                Icon(
                    imageVector = Icons.Rounded.KeyboardArrowDown,
                    contentDescription = if (isArmed) "Release to refresh" else "Pull to refresh",
                    modifier = Modifier
                        .size(22.dp)
                        .graphicsLayer { rotationZ = if (isArmed) 180f else 0f },
                    tint = accent
                )
            }
        }
    }
}
@Composable
fun PullToRefreshBox(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    state: DsmusicPullToRefreshState = rememberPullToRefreshState(),
    indicator: @Composable BoxScope.() -> Unit = { PullToRefreshDefaults.Indicator(state, isRefreshing, Modifier.align(Alignment.TopCenter)) },
    content: @Composable BoxScope.() -> Unit
) {
    val density = LocalDensity.current
    val thresholdPx = with(density) { 72.dp.toPx() }
    // A deliberate 8 dp upward reversal after the threshold is crossed cancels
    // this pull, even if the finger remains beyond the refresh threshold.
    val reverseCancelDistancePx = with(density) { 8.dp.toPx() }
    val latestOnRefresh by rememberUpdatedState(onRefresh)
    val latestIsRefreshing by rememberUpdatedState(isRefreshing)
    val refreshConnection = remember(state, thresholdPx, reverseCancelDistancePx) {
        object : NestedScrollConnection {
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                if (source != NestedScrollSource.UserInput || latestIsRefreshing) {
                    return Offset.Zero
                }

                // onPostScroll's consumed value is important here: once the finger
                // reverses, the child list can consume the upward delta itself (leaving
                // available.y == 0). Looking only at available.y misses that reversal and
                // incorrectly refreshes when the gesture is released.
                val gestureDeltaY = consumed.y + available.y

                if (gestureDeltaY < 0f && state.pullDistancePx > 0f) {
                    val reversedBy = -gestureDeltaY
                    if (state.peakPullDistancePx >= thresholdPx) {
                        // A deliberate upward reversal after arming cancels this pull,
                        // even when the child list consumed the upward movement.
                        state.reverseDistancePx += reversedBy
                        if (state.reverseDistancePx >= reverseCancelDistancePx) {
                            state.reversedAfterThreshold = true
                        }
                    }

                    // Reduce the indicator by the complete upward gesture delta, but only
                    // consume the part that the child list did not already handle.
                    val distanceBeforeReverse = state.pullDistancePx
                    state.pullDistancePx =
                        (distanceBeforeReverse - reversedBy).coerceAtLeast(0f)
                    state.progress = (state.pullDistancePx / thresholdPx).coerceIn(0f, 1f)
                    val unconsumedUpward = if (available.y < 0f) {
                        maxOf(available.y, -distanceBeforeReverse)
                    } else {
                        0f
                    }
                    return Offset(0f, unconsumedUpward)
                }

                if (gestureDeltaY > 0f && available.y > 0f) {
                    // Only the unconsumed downward drag reaches here when the list is at
                    // its top. Normal scrolling inside the feed remains owned by LazyColumn.
                    // Once reversed after arming, this same gesture cannot re-arm.
                    if (!state.reversedAfterThreshold) {
                        state.pullDistancePx += available.y
                        state.peakPullDistancePx =
                            maxOf(state.peakPullDistancePx, state.pullDistancePx)
                        state.progress = (state.pullDistancePx / thresholdPx).coerceIn(0f, 1f)
                        return Offset(0f, available.y)
                    }
                }

                return Offset.Zero
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                // This callback runs only after the user's pointer is released.
                // A pull that was deliberately reversed after arming is cancelled,
                // even when its final distance is still above the threshold.
                val shouldRefresh = state.progress >= 1f &&
                    !state.reversedAfterThreshold &&
                    !latestIsRefreshing
                state.pullDistancePx = 0f
                state.peakPullDistancePx = 0f
                state.reverseDistancePx = 0f
                state.reversedAfterThreshold = false
                state.progress = 0f
                if (shouldRefresh) latestOnRefresh()
                return Velocity.Zero
            }
        }
    }

    LaunchedEffect(isRefreshing, state) {
        if (isRefreshing) {
            state.pullDistancePx = 0f
            state.peakPullDistancePx = 0f
            state.reverseDistancePx = 0f
            state.reversedAfterThreshold = false
            state.progress = 0f
        }
    }

    Box(modifier = modifier.nestedScroll(refreshConnection)) {
        content()
        indicator()
    }
}

@Composable
fun DropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    offset: androidx.compose.ui.unit.DpOffset = androidx.compose.ui.unit.DpOffset.Zero,
    properties: PopupProperties = PopupProperties(),
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(14.dp),
    containerColor: Color = DsmusicTheme.colorScheme.surfaceContainerHigh,
    tonalElevation: Dp = 0.dp,
    shadowElevation: Dp = 0.dp,
    border: androidx.compose.foundation.BorderStroke? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    if (expanded) {
        Popup(alignment = Alignment.TopEnd, onDismissRequest = onDismissRequest, properties = properties) {
            Column(
                modifier = modifier.widthIn(min = 160.dp, max = 280.dp).clip(shape).background(containerColor)
                    .border(border ?: androidx.compose.foundation.BorderStroke(1.dp, DsmusicTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)), shape)
                    .padding(vertical = 6.dp),
                content = content
            )
        }
    }
}

@Composable
fun DropdownMenuItem(
    text: @Composable () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    enabled: Boolean = true,
    colors: DsmusicListItemColors = ListItemDefaults.colors(),
    contentPadding: PaddingValues = PaddingValues(horizontal = 14.dp, vertical = 10.dp)
) {
    val itemShape = RoundedCornerShape(10.dp)
    val itemBorder = DsmusicTheme.colorScheme.outlineVariant.copy(
        alpha = if (enabled) 0.72f else 0.38f
    )
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp, vertical = 2.dp)
            .clip(itemShape)
            .background(colors.containerColor)
            .border(1.dp, itemBorder, itemShape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(contentPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        leadingIcon?.invoke()
        Box(Modifier.weight(1f)) { CompositionLocalProvider(LocalDsmusicContentColor provides colors.headlineColor) { text() } }
        trailingIcon?.invoke()
    }
}

@Composable
fun OutlinedTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    textStyle: TextStyle = DsmusicTheme.typography.bodyLarge,
    label: (@Composable () -> Unit)? = null,
    placeholder: (@Composable () -> Unit)? = null,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    prefix: (@Composable () -> Unit)? = null,
    suffix: (@Composable () -> Unit)? = null,
    supportingText: (@Composable () -> Unit)? = null,
    isError: Boolean = false,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    singleLine: Boolean = false,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    minLines: Int = 1,
    interactionSource: androidx.compose.foundation.interaction.MutableInteractionSource? = null,
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(13.dp),
    colors: DsmusicTextFieldColors = OutlinedTextFieldDefaults.colors()
) = DsmusicInputField(value, onValueChange, modifier, enabled, readOnly, textStyle, label, placeholder, leadingIcon, trailingIcon, prefix, suffix, supportingText, isError, visualTransformation, keyboardOptions, keyboardActions, singleLine, maxLines, minLines, shape, colors, true)

@Composable
fun TextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    textStyle: TextStyle = DsmusicTheme.typography.bodyLarge,
    label: (@Composable () -> Unit)? = null,
    placeholder: (@Composable () -> Unit)? = null,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    prefix: (@Composable () -> Unit)? = null,
    suffix: (@Composable () -> Unit)? = null,
    supportingText: (@Composable () -> Unit)? = null,
    isError: Boolean = false,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    singleLine: Boolean = false,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    minLines: Int = 1,
    interactionSource: androidx.compose.foundation.interaction.MutableInteractionSource? = null,
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(13.dp),
    colors: DsmusicTextFieldColors = TextFieldDefaults.colors()
) = DsmusicInputField(value, onValueChange, modifier, enabled, readOnly, textStyle, label, placeholder, leadingIcon, trailingIcon, prefix, suffix, supportingText, isError, visualTransformation, keyboardOptions, keyboardActions, singleLine, maxLines, minLines, shape, colors, false)

@Composable
private fun DsmusicInputField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier,
    enabled: Boolean,
    readOnly: Boolean,
    textStyle: TextStyle,
    label: (@Composable () -> Unit)?,
    placeholder: (@Composable () -> Unit)?,
    leadingIcon: (@Composable () -> Unit)?,
    trailingIcon: (@Composable () -> Unit)?,
    prefix: (@Composable () -> Unit)?,
    suffix: (@Composable () -> Unit)?,
    supportingText: (@Composable () -> Unit)?,
    isError: Boolean,
    visualTransformation: VisualTransformation,
    keyboardOptions: KeyboardOptions,
    keyboardActions: KeyboardActions,
    singleLine: Boolean,
    maxLines: Int,
    minLines: Int,
    shape: androidx.compose.ui.graphics.Shape,
    colors: DsmusicTextFieldColors,
    outlined: Boolean
) {
    Column(modifier) {
        if (label != null) Box(Modifier.padding(bottom = 4.dp)) { CompositionLocalProvider(LocalDsmusicContentColor provides if (isError) DsmusicTheme.colorScheme.error else DsmusicTheme.colorScheme.onSurfaceVariant) { label() } }
        Row(
            modifier = Modifier.fillMaxWidth().clip(shape).background(colors.containerColor)
                .border(1.dp, if (isError) colors.errorIndicatorColor else colors.unfocusedIndicatorColor, shape)
                .padding(horizontal = 13.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            leadingIcon?.invoke()
            prefix?.invoke()
            Box(Modifier.weight(1f)) {
                if (value.isEmpty() && placeholder != null) {
                    CompositionLocalProvider(LocalDsmusicContentColor provides DsmusicTheme.colorScheme.onSurfaceVariant) { placeholder() }
                }
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    enabled = enabled,
                    readOnly = readOnly,
                    textStyle = textStyle.copy(color = colors.textColor),
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(colors.cursorColor),
                    visualTransformation = visualTransformation,
                    keyboardOptions = keyboardOptions,
                    keyboardActions = keyboardActions,
                    singleLine = singleLine,
                    maxLines = maxLines,
                    minLines = minLines,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            suffix?.invoke()
            trailingIcon?.invoke()
        }
        supportingText?.let { Box(Modifier.padding(top = 4.dp)) { it() } }
    }
}

@Composable
fun Slider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    onValueChangeFinished: (() -> Unit)? = null,
    interactionSource: androidx.compose.foundation.interaction.MutableInteractionSource? = null,
    colors: DsmusicSliderColors = SliderDefaults.colors()
) {
    val density = LocalDensity.current
    val fraction = if (valueRange.endInclusive <= valueRange.start) 0f else ((value - valueRange.start) / (valueRange.endInclusive - valueRange.start)).coerceIn(0f, 1f)
    Box(
        modifier = modifier.height(34.dp).pointerInput(enabled, valueRange) {
            if (enabled) {
                detectTapGestures { point ->
                    val f = (point.x / size.width).coerceIn(0f, 1f)
                    onValueChange(valueRange.start + f * (valueRange.endInclusive - valueRange.start))
                    onValueChangeFinished?.invoke()
                }
            }
        }.pointerInput(enabled, valueRange) {
            if (enabled) detectDragGestures(
                onDrag = { change, _ ->
                    change.consume()
                    val f = (change.position.x / size.width).coerceIn(0f, 1f)
                    onValueChange(valueRange.start + f * (valueRange.endInclusive - valueRange.start))
                },
                onDragEnd = { onValueChangeFinished?.invoke() }
            )
        }
    ) {
        Canvas(Modifier.fillMaxWidth().height(24.dp).align(Alignment.Center)) {
            val y = size.height / 2f
            val start = 4.dp.toPx()
            val end = size.width - start
            val x = start + (end - start) * fraction
            drawLine(colors.inactiveTrackColor, Offset(start, y), Offset(end, y), 4.dp.toPx(), cap = StrokeCap.Round)
            drawLine(colors.activeTrackColor, Offset(start, y), Offset(x, y), 4.dp.toPx(), cap = StrokeCap.Round)
            drawCircle(colors.thumbColor, 7.dp.toPx(), Offset(x, y))
        }
    }
}

@Composable
fun LinearProgressIndicator(
    progress: Float,
    modifier: Modifier = Modifier,
    color: Color = DsmusicTheme.colorScheme.primary,
    trackColor: Color = DsmusicTheme.colorScheme.surfaceVariant
) {
    Box(modifier.height(4.dp).clip(CircleShape).background(trackColor)) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(progress.coerceIn(0f, 1f)).background(color))
    }
}
@Composable
fun LinearProgressIndicator(
    progress: () -> Float,
    modifier: Modifier = Modifier,
    color: Color = DsmusicTheme.colorScheme.primary,
    trackColor: Color = DsmusicTheme.colorScheme.surfaceVariant
) = LinearProgressIndicator(progress(), modifier, color, trackColor)

@Composable
fun CircularProgressIndicator(
    progress: Float? = null,
    modifier: Modifier = Modifier,
    color: Color = DsmusicTheme.colorScheme.primary,
    trackColor: Color = Color.Transparent,
    strokeWidth: Dp = 3.dp,
    strokeCap: StrokeCap = StrokeCap.Round
) {
    val rotation = if (progress == null) {
        val transition = rememberInfiniteTransition(label = "DsmusicIndeterminateLoader")
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 850, easing = LinearEasing)
            ),
            label = "DsmusicLoaderRotation"
        ).value
    } else 0f
    Canvas(modifier.size(32.dp)) {
        val width = strokeWidth.toPx()
        if (trackColor != Color.Transparent) {
            drawArc(trackColor, -90f, 360f, false, style = Stroke(width, cap = strokeCap))
        }
        drawArc(
            color,
            -90f + rotation,
            if (progress == null) 265f else progress.coerceIn(0f, 1f) * 360f,
            false,
            style = Stroke(width, cap = strokeCap)
        )
    }
}
/**
 * Shared D.S Music loading treatment. All indeterminate loading states use this
 * compact orbit ring with a muted track, soft surface and center point.
 */
@Composable
fun LoadingIndicator(
    modifier: Modifier = Modifier.size(32.dp),
    color: Color = DsmusicTheme.colorScheme.primary,
    containerColor: Color = Color.Transparent
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .background(
                    if (containerColor != Color.Transparent) containerColor
                    else color.copy(alpha = 0.055f)
                )
        )
        CircularProgressIndicator(
            modifier = Modifier.fillMaxSize().padding(2.dp),
            color = color,
            trackColor = color.copy(alpha = 0.22f),
            strokeWidth = 2.6.dp
        )
        Box(
            modifier = Modifier
                .size(4.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = 0.88f))
        )
    }
}

/**
 * Reusable empty-state for network-backed content that failed specifically because
 * the device has no validated internet connection.
 */
@Composable
fun NoInternetState(
    modifier: Modifier = Modifier.fillMaxSize(),
    onRetry: (() -> Unit)? = null,
    compact: Boolean = false,
    title: String = "No internet connection",
    message: String = "Connect to Wi-Fi or mobile data, then try again."
) {
    Column(
        modifier = modifier.padding(if (compact) 12.dp else 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(if (compact) 44.dp else 64.dp)
                .clip(CircleShape)
                .background(DsmusicTheme.colorScheme.primary.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.CloudOff,
                contentDescription = null,
                modifier = Modifier.size(if (compact) 23.dp else 32.dp),
                tint = DsmusicTheme.colorScheme.primary
            )
        }
        Text(
            text = title,
            style = if (compact) DsmusicTheme.typography.titleSmall else DsmusicTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = DsmusicTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
        Text(
            text = message,
            style = DsmusicTheme.typography.bodyMedium,
            color = DsmusicTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        if (onRetry != null) {
            TextButton(onClick = onRetry) {
                Text("Try again")
            }
        }
    }
}
@Composable
fun CircularWavyProgressIndicator(progress: Float, modifier: Modifier = Modifier, colors: Pair<Color, Color> = WavyProgressIndicatorDefaults.colors()) {
    CircularProgressIndicator(progress = progress, modifier = modifier, color = colors.first, trackColor = colors.second, strokeWidth = 3.dp)
}
@Composable
fun LinearWavyProgressIndicator(progress: Float, modifier: Modifier = Modifier, colors: Pair<Color, Color> = WavyProgressIndicatorDefaults.colors()) {
    LinearProgressIndicator(progress, modifier, colors.first, colors.second)
}
@Composable
fun LinearWavyProgressIndicator(
    progress: () -> Float,
    modifier: Modifier = Modifier,
    color: Color = DsmusicTheme.colorScheme.primary,
    trackColor: Color = DsmusicTheme.colorScheme.surfaceVariant,
    stroke: Stroke = Stroke(3f),
    trackStroke: Stroke = stroke,
    wavelength: Dp = 48.dp,
    amplitude: (Float) -> Float = { 1f }
) {
    LinearProgressIndicator(progress(), modifier, color, trackColor)
}

@Composable
fun ToggleButton(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource? = null,
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(10.dp),
    shapes: androidx.compose.ui.graphics.Shape? = null,
    colors: DsmusicButtonColors = ToggleButtonDefaults.toggleButtonColors(),
    contentPadding: PaddingValues = PaddingValues(horizontal = 12.dp, vertical = 9.dp),
    content: @Composable RowScope.() -> Unit
) {
    val actualShape = shapes ?: shape
    val background = if (checked) colors.containerColor else Color.Transparent
    val foreground = if (checked) colors.contentColor else DsmusicTheme.colorScheme.onSurface
    Row(
        modifier = modifier.clip(actualShape)
            .background(background)
            .border(1.dp, DsmusicTheme.colorScheme.outlineVariant.copy(alpha = 0.86f), actualShape)
            .clickable(enabled = enabled, interactionSource = interactionSource, indication = null) { onCheckedChange(!checked) }
            .padding(contentPadding),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        CompositionLocalProvider(LocalDsmusicContentColor provides foreground) { content() }
    }
}

data class DsmusicSnackbarData(val visuals: SnackbarVisuals)
enum class SnackbarDuration { Short, Long, Indefinite }
enum class SnackbarResult { Dismissed, ActionPerformed }
interface SnackbarVisuals {
    val message: String
    val actionLabel: String?
    val withDismissAction: Boolean
    val duration: SnackbarDuration
}
class SnackbarHostState {
    var currentSnackbarData by mutableStateOf<DsmusicSnackbarData?>(null)
        private set
    suspend fun showSnackbar(message: String, actionLabel: String? = null, withDismissAction: Boolean = false, duration: SnackbarDuration = SnackbarDuration.Short): SnackbarResult =
        showSnackbar(object : SnackbarVisuals {
            override val message = message
            override val actionLabel = actionLabel
            override val withDismissAction = withDismissAction
            override val duration = duration
        })
    suspend fun showSnackbar(visuals: SnackbarVisuals): SnackbarResult {
        currentSnackbarData = DsmusicSnackbarData(visuals)
        if (visuals.duration != SnackbarDuration.Indefinite) delay(if (visuals.duration == SnackbarDuration.Long) 3500L else 2000L)
        currentSnackbarData = null
        return SnackbarResult.Dismissed
    }
    fun dismiss() { currentSnackbarData = null }
}
@Composable
fun Snackbar(
    modifier: Modifier = Modifier,
    action: @Composable (() -> Unit)? = null,
    dismissAction: @Composable (() -> Unit)? = null,
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(12.dp),
    containerColor: Color = DsmusicTheme.colorScheme.surfaceContainerHighest,
    contentColor: Color = DsmusicTheme.colorScheme.onSurface,
    actionColor: Color = DsmusicTheme.colorScheme.primary,
    content: @Composable () -> Unit
) {
    Row(modifier.clip(shape).background(containerColor).border(1.dp, DsmusicTheme.colorScheme.outlineVariant.copy(alpha = 0.82f), shape).padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f)) { CompositionLocalProvider(LocalDsmusicContentColor provides contentColor) { content() } }
        action?.invoke()
        dismissAction?.invoke()
    }
}
@Composable
fun SnackbarHost(hostState: SnackbarHostState, modifier: Modifier = Modifier, snackbar: @Composable (DsmusicSnackbarData) -> Unit = { data -> Snackbar { Text(data.visuals.message) } }) {
    hostState.currentSnackbarData?.let { snackbarData -> Box(modifier) { snackbar(snackbarData) } }
}

enum class SwipeToDismissBoxValue { Settled, StartToEnd, EndToStart }
class SwipeToDismissBoxState(private val confirmValueChange: (SwipeToDismissBoxValue) -> Boolean) {
    var currentValue by mutableStateOf(SwipeToDismissBoxValue.Settled)
        private set
    var dismissDirection by mutableStateOf(SwipeToDismissBoxValue.Settled)
    var dragOffset by mutableFloatStateOf(0f)
    var dragWidth by mutableFloatStateOf(1f)
    val progress: Float get() = (kotlin.math.abs(dragOffset) / dragWidth.coerceAtLeast(1f)).coerceIn(0f, 1f)
    fun settle(direction: SwipeToDismissBoxValue) {
        dismissDirection = direction
        if (confirmValueChange(direction)) currentValue = direction
        dragOffset = 0f
    }
}
@Composable
fun rememberSwipeToDismissBoxState(confirmValueChange: (SwipeToDismissBoxValue) -> Boolean = { true }): SwipeToDismissBoxState = remember { SwipeToDismissBoxState(confirmValueChange) }
@Composable
fun SwipeToDismissBox(
    state: SwipeToDismissBoxState,
    modifier: Modifier = Modifier,
    enableDismissFromStartToEnd: Boolean = true,
    enableDismissFromEndToStart: Boolean = true,
    backgroundContent: @Composable RowScope.() -> Unit,
    content: @Composable RowScope.() -> Unit
) {
    val density = LocalDensity.current
    Box(
        modifier = modifier.pointerInput(state, enableDismissFromStartToEnd, enableDismissFromEndToStart) {
            detectHorizontalDragGestures(
                onHorizontalDrag = { change, dragAmount ->
                    change.consume()
                    state.dragWidth = size.width.toFloat().coerceAtLeast(1f)
                    state.dragOffset = (state.dragOffset + dragAmount)
                        .coerceIn(-size.width.toFloat(), size.width.toFloat())
                },
                onDragEnd = {
                    val threshold = with(density) { 72.dp.toPx() }
                    val direction = when {
                        state.dragOffset > threshold && enableDismissFromStartToEnd -> SwipeToDismissBoxValue.StartToEnd
                        state.dragOffset < -threshold && enableDismissFromEndToStart -> SwipeToDismissBoxValue.EndToStart
                        else -> SwipeToDismissBoxValue.Settled
                    }
                    state.settle(direction)
                },
                onDragCancel = { state.dragOffset = 0f }
            )
        }
    ) {
        Row(Modifier.fillMaxWidth()) { backgroundContent() }
        Row(Modifier.fillMaxWidth().offset(x = with(density) { state.dragOffset.toDp() })) { content() }
    }
}

class DsmusicTimePickerState(initialHour: Int, initialMinute: Int, val is24Hour: Boolean) {
    var hour by mutableIntStateOf(initialHour.coerceIn(0, 23))
    var minute by mutableIntStateOf(initialMinute.coerceIn(0, 59))
}
@Composable
fun rememberTimePickerState(initialHour: Int = 0, initialMinute: Int = 0, is24Hour: Boolean = true): DsmusicTimePickerState = remember { DsmusicTimePickerState(initialHour, initialMinute, is24Hour) }
@Composable
fun TimePicker(state: DsmusicTimePickerState, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(value = state.hour.toString().padStart(2, '0'), onValueChange = { state.hour = it.filter(Char::isDigit).take(2).toIntOrNull()?.coerceIn(0, 23) ?: state.hour }, modifier = Modifier.width(88.dp), singleLine = true, label = { Text("Hour") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next))
        Text(":")
        OutlinedTextField(value = state.minute.toString().padStart(2, '0'), onValueChange = { state.minute = it.filter(Char::isDigit).take(2).toIntOrNull()?.coerceIn(0, 59) ?: state.minute }, modifier = Modifier.width(88.dp), singleLine = true, label = { Text("Minute") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done))
    }
}

@Composable
fun ExtendedFloatingActionButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(18.dp),
    icon: @Composable () -> Unit = {},
    text: (@Composable () -> Unit)? = null,
    containerColor: Color = DsmusicTheme.colorScheme.primaryContainer,
    contentColor: Color = DsmusicTheme.colorScheme.onPrimaryContainer,
    elevation: Dp = 0.dp,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.() -> Unit = {}
) {
    DsmusicButton(
        onClick, modifier, true, shape,
        DsmusicButtonColors(containerColor, contentColor, containerColor, contentColor),
        null, PaddingValues(horizontal = 18.dp, vertical = 12.dp), elevation, interactionSource
    ) {
        if (text != null) {
            icon()
            Spacer(Modifier.width(8.dp))
            text()
        } else {
            content()
        }
    }
}
