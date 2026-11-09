package com.dsann.music.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Flat D.S Launcher-inspired surfaces: no tonal elevation or liquid-glass layers. */
@Composable
fun DsmusicCard(
    modifier: Modifier = Modifier,
    shape: Shape = DsmusicTheme.shapes.large,
    colors: DsmusicCardColors? = null,
    elevation: Any? = null,
    border: Any? = null,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val scheme = DsmusicTheme.colorScheme
    val containerColor = colors?.containerColor ?: scheme.surfaceContainerLow
    val contentColor = colors?.contentColor ?: scheme.onSurface
    // A restrained shared treatment keeps card families consistent without expensive visual effects.
    val defaultBorder = BorderStroke(1.dp, scheme.outlineVariant.copy(alpha = 0.92f))
    val resolvedBorder = border as? BorderStroke ?: defaultBorder
    Column(
        modifier = modifier
            .clip(shape)
            .background(containerColor)
            .border(resolvedBorder, shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
    ) {
        CompositionLocalProvider(LocalDsmusicContentColor provides contentColor) {
            content()
        }
    }
}

@Composable
fun DsmusicSurface(
    modifier: Modifier = Modifier,
    shape: Shape = DsmusicTheme.shapes.medium,
    color: Color = DsmusicTheme.colorScheme.surfaceContainer,
    contentColor: Color = DsmusicTheme.colorScheme.onSurface,
    tonalElevation: Any? = null,
    shadowElevation: Any? = null,
    border: Any? = null,
    showBorder: Boolean = true,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val resolvedBorder = border as? BorderStroke ?: BorderStroke(
        1.dp,
        DsmusicTheme.colorScheme.outlineVariant.copy(alpha = 0.9f)
    )
    Box(
        modifier = modifier.clip(shape).background(color)
            .then(if (showBorder) Modifier.border(resolvedBorder, shape) else Modifier)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
    ) {
        CompositionLocalProvider(LocalDsmusicContentColor provides contentColor) {
            content()
        }
    }
}

@Composable
fun DsmusicListItem(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    headlineContent: (@Composable () -> Unit)? = null,
    overlineContent: (@Composable () -> Unit)? = null,
    supportingContent: (@Composable () -> Unit)? = null,
    leadingContent: (@Composable () -> Unit)? = null,
    trailingContent: (@Composable () -> Unit)? = null,
    contentPadding: PaddingValues? = null,
    colors: DsmusicListItemColors? = null,
    shapes: Shape? = null,
    selected: Boolean = false,
    verticalAlignment: Alignment.Vertical = Alignment.CenterVertically,
    checked: Boolean? = null,
    onCheckedChange: ((Boolean) -> Unit)? = null,
    content: (@Composable () -> Unit)? = null
) {
    val paddingValues = contentPadding ?: PaddingValues(horizontal = 13.dp, vertical = 10.dp)
    val itemShape = shapes ?: DsmusicTheme.shapes.medium
    val scheme = DsmusicTheme.colorScheme
    val itemBackground = when {
        colors == null && selected -> scheme.primaryContainer
        colors == null -> scheme.surfaceContainerLow
        selected -> colors.selectedContainerColor
        else -> colors.containerColor
    }
    val itemBorder = if (selected) scheme.primary.copy(alpha = 0.82f)
        else scheme.outlineVariant.copy(alpha = 0.9f)
    Row(
        modifier = modifier.fillMaxWidth().clip(itemShape)
            .background(itemBackground)
            .border(1.dp, itemBorder, itemShape)
            .then(
                when {
                    onClick != null -> Modifier.clickable(onClick = onClick)
                    checked != null && onCheckedChange != null ->
                        Modifier.clickable { onCheckedChange(!checked) }
                    else -> Modifier
                }
            )
            .padding(paddingValues),
        verticalAlignment = verticalAlignment,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        leadingContent?.let {
            val leadingColor = when {
                colors == null && selected -> scheme.onPrimaryContainer
                colors == null -> scheme.onSurfaceVariant
                selected -> colors.selectedLeadingContentColor
                else -> colors.leadingIconColor
            }
            CompositionLocalProvider(LocalDsmusicContentColor provides leadingColor) {
                Box(contentAlignment = Alignment.Center) { it() }
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            overlineContent?.let {
                CompositionLocalProvider(
                    LocalDsmusicContentColor provides if (selected && colors == null) {
                        scheme.onPrimaryContainer.copy(alpha = 0.82f)
                    } else {
                        scheme.onSurfaceVariant
                    }
                ) { it() }
            }
            CompositionLocalProvider(
                LocalDsmusicContentColor provides when {
                    colors == null && selected -> scheme.onPrimaryContainer
                    colors == null -> scheme.onSurface
                    selected -> colors.selectedContentColor
                    else -> colors.headlineColor
                }
            ) {
                when {
                    headlineContent != null -> headlineContent()
                    content != null -> content()
                }
            }
            supportingContent?.let {
                CompositionLocalProvider(
                    LocalDsmusicContentColor provides (
                        if (selected && colors == null) scheme.onPrimaryContainer.copy(alpha = 0.82f)
                        else colors?.supportingContentColor ?: scheme.onSurfaceVariant
                    )
                ) { it() }
            }
        }
        trailingContent?.let {
            val trailingColor = when {
                colors == null && selected -> scheme.onPrimaryContainer
                colors == null -> scheme.onSurfaceVariant
                selected -> colors.selectedTrailingContentColor
                else -> colors.trailingIconColor
            }
            CompositionLocalProvider(LocalDsmusicContentColor provides trailingColor) {
                Box(contentAlignment = Alignment.Center) { it() }
            }
        }
    }
}

@Composable
fun DsmusicButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = RoundedCornerShape(14.dp),
    colors: DsmusicButtonColors? = null,
    border: BorderStroke? = null,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 11.dp),
    elevation: Any? = null,
    interactionSource: Any? = null,
    content: @Composable RowScope.() -> Unit
) {
    val scheme = DsmusicTheme.colorScheme
    // Resolve enabled and disabled colours from the Quantune scheme.
    val backgroundColor = if (enabled) colors?.containerColor ?: scheme.primary
        else colors?.disabledContainerColor ?: scheme.surfaceVariant
    val foregroundColor = if (enabled) colors?.contentColor ?: scheme.onPrimary
        else colors?.disabledContentColor ?: scheme.onSurfaceVariant
    Row(
        modifier = modifier.defaultMinSize(minHeight = 40.dp).clip(shape)
            .background(backgroundColor)
            .then(
                if (border != null) Modifier.border(border, shape)
                else Modifier.border(
                    1.dp,
                    backgroundColor.copy(alpha = if (enabled) 0.92f else 0.36f),
                    shape
                )
            )
            .clickable(enabled = enabled, onClick = onClick).padding(contentPadding),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        CompositionLocalProvider(LocalDsmusicContentColor provides foregroundColor) {
            content()
        }
    }
}

@Composable
fun DsmusicOutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = RoundedCornerShape(14.dp),
    colors: DsmusicButtonColors? = null,
    border: BorderStroke? = null,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
    interactionSource: Any? = null,
    content: @Composable RowScope.() -> Unit
) {
    val scheme = DsmusicTheme.colorScheme
    val backgroundColor = if (enabled) colors?.containerColor ?: scheme.surfaceContainerLow
        else colors?.disabledContainerColor ?: scheme.surfaceContainerLow
    val foregroundColor = if (enabled) colors?.contentColor ?: scheme.onSurface
        else colors?.disabledContentColor ?: scheme.onSurfaceVariant
    Row(
        modifier = modifier.defaultMinSize(minHeight = 40.dp).clip(shape)
            .background(backgroundColor)
            .then(
                if (border != null) Modifier.border(border, shape)
                else Modifier.border(
                    1.dp,
                    scheme.outlineVariant.copy(alpha = if (enabled) 0.84f else 0.48f),
                    shape
                )
            )
            .clickable(enabled = enabled, onClick = onClick).padding(contentPadding),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        CompositionLocalProvider(LocalDsmusicContentColor provides foregroundColor) {
            content()
        }
    }
}

@Composable
fun DsmusicTextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: DsmusicButtonColors? = null,
    contentPadding: PaddingValues = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
    interactionSource: Any? = null,
    content: @Composable RowScope.() -> Unit
) {
    val contentColor = if (enabled) colors?.contentColor ?: DsmusicTheme.colorScheme.primary
        else colors?.disabledContentColor ?: DsmusicTheme.colorScheme.onSurfaceVariant
    Row(
        modifier = modifier.clip(RoundedCornerShape(10.dp))
            .clickable(enabled = enabled, onClick = onClick).padding(contentPadding),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        CompositionLocalProvider(LocalDsmusicContentColor provides contentColor) { content() }
    }
}

@Composable
fun DsmusicIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    interactionSource: Any? = null,
    colors: Any? = null,
    shape: Shape = CircleShape,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier.defaultMinSize(minWidth = 40.dp, minHeight = 40.dp)
            .clip(shape).clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
        content = content
    )
}

@Composable
fun DsmusicFilterChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = RoundedCornerShape(10.dp),
    colors: DsmusicButtonColors? = null,
    border: Any? = null,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null
) {
    val scheme = DsmusicTheme.colorScheme
    val background = when {
        !enabled -> colors?.disabledContainerColor ?: scheme.surfaceVariant
        selected -> colors?.containerColor ?: scheme.primaryContainer
        else -> scheme.surfaceContainerLow
    }
    val foreground = when {
        !enabled -> colors?.disabledContentColor ?: scheme.onSurfaceVariant
        selected -> colors?.contentColor ?: scheme.onPrimaryContainer
        else -> scheme.onSurfaceVariant
    }
    Row(
        modifier = modifier.clip(shape)
            .background(background)
            .border(
                1.dp,
                if (selected && enabled) scheme.primary else scheme.outlineVariant.copy(alpha = 0.82f),
                shape
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 13.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CompositionLocalProvider(LocalDsmusicContentColor provides foreground) {
            leadingIcon?.invoke()
            label()
            trailingIcon?.invoke()
        }
    }
}

@Composable
fun DsmusicSwitchControl(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: DsmusicSwitchColors? = null
) {
    val scheme = DsmusicTheme.colorScheme
    val trackColor = when {
        checked && enabled -> colors?.checkedTrackColor ?: scheme.primaryContainer
        checked -> colors?.disabledCheckedTrackColor ?: scheme.surfaceVariant
        else -> colors?.uncheckedTrackColor ?: scheme.surfaceVariant
    }
    val thumbColor = when {
        checked && enabled -> colors?.checkedThumbColor ?: scheme.onPrimaryContainer
        else -> colors?.uncheckedThumbColor ?: scheme.onSurfaceVariant
    }
    Box(
        modifier = modifier.size(width = 44.dp, height = 26.dp).clip(CircleShape)
            .background(trackColor)
            .border(1.dp, if (checked && enabled) scheme.primary else scheme.outlineVariant, CircleShape)
            .clickable(enabled = enabled && onCheckedChange != null) { onCheckedChange?.invoke(!checked) }
            .padding(horizontal = 3.dp),
        contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Box(Modifier.size(if (checked) 20.dp else 18.dp).clip(CircleShape).background(thumbColor))
    }
}

/**
 * Shared D.S Launcher / Home Studio page heading used by the main tabs.
 * The larger title, accent rail and deliberate vertical rhythm replace compact
 * Material-style top bars while keeping the screen's own trailing actions.
 */
@Composable
fun DsmusicPageHeading(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    kicker: String = "QUANTUNE"
) {
    val titleSize = when {
        title.length >= 19 -> 22.sp
        title.length >= 15 -> 26.sp
        else -> 31.sp
    }
    val titleLineHeight = when {
        title.length >= 19 -> 27.sp
        title.length >= 15 -> 32.sp
        else -> 37.sp
    }
    // The responsive title sizes above are design baselines. Derive the active global
    // font scale from the already-customized headline role before applying those sizes;
    // otherwise this explicit fontSize/lineHeight override bypasses the app-wide slider.
    val typography = DsmusicTheme.typography
    val globalFontScale = (
        typography.headlineMedium.fontSize.value /
            Typography.headlineMedium.fontSize.value
        ).takeIf { it.isFinite() && it > 0f } ?: 1f
    val scaledTitleSize = titleSize * globalFontScale
    val scaledTitleLineHeight = titleLineHeight * globalFontScale
    val colorScheme = DsmusicTheme.colorScheme
    val headingShape = RoundedCornerShape(22.dp)
    val headingGradient = remember(colorScheme.primary) {
        // Use the selected settings accent at every stop; only opacity changes
        // across the heading, fading smoothly from left to transparent right.
        Brush.horizontalGradient(
            colorStops = arrayOf(
                0f to colorScheme.primary.copy(alpha = 0.20f),
                0.36f to colorScheme.primary.copy(alpha = 0.14f),
                0.72f to colorScheme.primary.copy(alpha = 0.07f),
                1f to colorScheme.primary.copy(alpha = 0f)
            )
        )
    }
    Row(
        modifier = modifier
            .clip(headingShape)
            .background(headingGradient, headingShape)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .width(4.dp)
                .height((if (subtitle == null) 42.dp else 48.dp) * globalFontScale)
                .clip(RoundedCornerShape(4.dp))
                .background(colorScheme.primary)
        )
        Column(
            modifier = Modifier.padding(start = 12.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(
                text = kicker.uppercase(),
                style = DsmusicTheme.typography.labelMedium.copy(
                    color = colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.7.sp
                ),
                maxLines = 1
            )
            Text(
                text = title,
                style = typography.headlineMedium.copy(
                    fontSize = scaledTitleSize,
                    lineHeight = scaledTitleLineHeight,
                    color = colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.sp
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = typography.bodyMedium.copy(
                        color = DsmusicTheme.colorScheme.onSurfaceVariant
                    ),
                    maxLines = 1
                )
            }
        }
    }
}
