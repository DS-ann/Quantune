package com.dsann.music.ui.player

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dsann.music.ui.theme.Text

/**
 * Render a true glyph contour beneath gradient lyrics.
 * The border is TextStyle.drawStyle=Stroke, not a border around a text range or box.
 */
@Composable
internal fun LyricsText(
    text: String,
    style: TextStyle,
    color: Color = Color.Unspecified,
    modifier: Modifier = Modifier,
    maxLines: Int = Int.MAX_VALUE,
    softWrap: Boolean = true,
    overflow: TextOverflow = TextOverflow.Clip,
    textAlign: TextAlign = TextAlign.Unspecified,
    outlineWidth: Dp = 1.dp,
    fillTextWidth: Boolean = false,
    drawOutlineForSolidText: Boolean = false,
    onTextLayout: (TextLayoutResult) -> Unit = {}
) {
    val textWidthModifier = if (fillTextWidth) Modifier.fillMaxWidth() else Modifier
    if ((!drawOutlineForSolidText && style.brush == null) || outlineWidth <= 0.dp) {
        Text(
            text = text,
            style = style,
            color = color,
            modifier = modifier.then(textWidthModifier),
            maxLines = maxLines,
            softWrap = softWrap,
            overflow = overflow,
            textAlign = textAlign,
            onTextLayout = onTextLayout
        )
        return
    }

    val strokeWidthPx = with(LocalDensity.current) { outlineWidth.toPx() }
    val strokeStyle = remember(style, strokeWidthPx) {
        style.copy(
            brush = SolidColor(Color.Black),
            drawStyle = Stroke(width = strokeWidthPx)
        )
    }

    Box(modifier = modifier.then(textWidthModifier)) {
        Text(
            text = text,
            style = strokeStyle,
            modifier = textWidthModifier.clearAndSetSemantics { },
            maxLines = maxLines,
            softWrap = softWrap,
            overflow = overflow,
            textAlign = textAlign
        )
        Text(
            text = text,
            style = style,
            color = color,
            modifier = textWidthModifier,
            maxLines = maxLines,
            softWrap = softWrap,
            overflow = overflow,
            textAlign = textAlign,
            onTextLayout = onTextLayout
        )
    }
}
