package com.dsann.music.ui.player

import com.dsann.music.CrashDiagnostics
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dsann.music.ui.theme.Text

/**
 * Render a true glyph contour beneath gradient lyrics from the same measured layout.
 * The outline is a Canvas draw, not a second Text node or a second paragraph layout.
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
    val drawOutline = (drawOutlineForSolidText || style.brush != null) && outlineWidth > 0.dp
    val strokeWidthPx = if (drawOutline) {
        with(androidx.compose.ui.platform.LocalDensity.current) { outlineWidth.toPx() }
    } else {
        0f
    }
    val layoutState = remember(text, style, maxLines, softWrap, overflow, textAlign) {
        mutableStateOf<TextLayoutResult?>(null)
    }

    val measuredModifier = modifier.then(textWidthModifier).drawWithCache {
        val layout = layoutState.value
        onDrawWithContent {
            val drawStartedAt = System.nanoTime()
            try {
                if (drawOutline && layout != null) {
                    drawText(
                        textLayoutResult = layout,
                        brush = SolidColor(Color.Black),
                        topLeft = Offset.Zero,
                        drawStyle = Stroke(width = strokeWidthPx)
                    )
                }
                drawContent()
            } finally {
                CrashDiagnostics.recordLyricsDraw(System.nanoTime() - drawStartedAt)
            }
        }
    }

    Text(
        text = text,
        style = style,
        color = color,
        modifier = measuredModifier,
        maxLines = maxLines,
        softWrap = softWrap,
        overflow = overflow,
        textAlign = textAlign,
        onTextLayout = { layout ->
            layoutState.value = layout
            CrashDiagnostics.recordLyricsTextLayout(text.length, layout.lineCount)
            onTextLayout(layout)
        }
    )
}
