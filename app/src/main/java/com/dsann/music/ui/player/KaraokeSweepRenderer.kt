package com.dsann.music.ui.player

import com.dsann.music.BuildConfig
import android.util.Log

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.style.ResolvedTextDirection
import androidx.compose.ui.text.style.TextOverflow
import com.dsann.music.data.lyrics.LyricWord
import com.dsann.music.ui.theme.Text
import kotlin.math.floor
import kotlin.math.max

/**
 * A word's visible range inside the displayed lyric string. The range is half-open so
 * a fractional character offset can represent the moving karaoke frontier.
 */
internal data class KaraokeWordSpan(
    val startOffset: Int,
    val endOffsetExclusive: Int,
    val startTimeMs: Long,
    val durationMs: Long
)

/**
 * Resolve provider offsets once when the text changes. A few older provider payloads
 * leave every charRange at its default value; those are aligned by searching the
 * original word text instead of letting every word point at character zero.
 */
internal fun buildKaraokeWordSpans(
    text: String,
    words: List<LyricWord>,
    wordOffset: Int = 0,
    checkCancellation: (() -> Unit)? = null
): List<KaraokeWordSpan> {
    if (text.isEmpty() || words.isEmpty()) return emptyList()

    val spans = ArrayList<KaraokeWordSpan>(words.size)
    var fallbackCursor = 0

    for ((wordIndex, word) in words.withIndex()) {
        // Allow a superseded song or a dismissed lyrics screen to cancel preparation
        // even when a provider returns an unusually large word list.
        if ((wordIndex and 0x3F) == 0) checkCancellation?.invoke()
        if (word.text.isEmpty()) continue

        val explicitStart = word.charRange.first - wordOffset
        val explicitEnd = word.charRange.last - wordOffset + 1
        val explicitLength = explicitEnd - explicitStart
        val expectedLength = word.text.length
        val hasUsableExplicitRange =
            explicitStart >= 0 &&
                explicitEnd <= text.length &&
                explicitLength > 0 &&
                explicitLength >= expectedLength

        val start = if (hasUsableExplicitRange) {
            explicitStart
        } else {
            val found = text.indexOf(word.text, fallbackCursor)
            if (found >= 0) found else fallbackCursor.coerceAtMost(text.length)
        }
        val end = if (hasUsableExplicitRange) {
            explicitEnd
        } else {
            (start + expectedLength).coerceAtMost(text.length)
        }

        if (end > start) {
            spans += KaraokeWordSpan(
                startOffset = start,
                endOffsetExclusive = end,
                startTimeMs = word.absoluteStartTimeMs,
                durationMs = word.durationMs.coerceAtLeast(1L)
            )
            fallbackCursor = max(fallbackCursor, end)
        }
    }

    return spans.sortedBy { it.startOffset }
}

/**
 * Return the exclusive character offset that has been sung at [positionMs]. This is
 * deliberately a small timeline calculation: all provider range resolution happens
 * once in [buildKaraokeWordSpans], not in the frame draw callback.
 */
internal fun calculateSungTextOffset(
    spans: List<KaraokeWordSpan>,
    positionMs: Long
): Float {
    var completedOffset = 0f

    for (span in spans) {
        if (positionMs < span.startTimeMs) return completedOffset

        val elapsed = (positionMs - span.startTimeMs).coerceAtLeast(0L)
        val progress = (elapsed.toDouble() / span.durationMs.toDouble())
            .toFloat()
            .coerceIn(0f, 1f)
        val wordOffset = span.startOffset +
            (span.endOffsetExclusive - span.startOffset) * progress
        completedOffset = max(completedOffset, wordOffset)

        if (progress < 1f) return completedOffset
    }

    return completedOffset
}

private data class SweptRow(
    val top: Float,
    val bottom: Float,
    val isRtl: Boolean
)

/**
 * Caches line bands for a TextLayoutResult and calculates only the moving word
 * frontier. The sweep itself uses rectangular clips instead of a Path/clipPath:
 * the same two regions cover every fully-sung row plus the active row's prefix.
 */
private data class SweptFrontier(
    val rowIndex: Int,
    val boundaryX: Float,
    val isComplete: Boolean = false
)

private class SweptRows {
    private var cachedLayout: TextLayoutResult? = null
    private var rows: List<SweptRow> = emptyList()

    fun rowsFor(layout: TextLayoutResult): List<SweptRow> {
        if (layout === cachedLayout) return rows

        rows = List(layout.lineCount) { index ->
            val lineStart = layout.getLineStart(index)
            SweptRow(
                top = layout.getLineTop(index),
                bottom = layout.getLineBottom(index),
                isRtl = layout.getParagraphDirection(lineStart) ==
                    ResolvedTextDirection.Rtl
            )
        }
        cachedLayout = layout
        return rows
    }

    fun frontierFor(
        layout: TextLayoutResult,
        sungOffset: Float,
        width: Float
    ): SweptFrontier? {
        if (rows.isEmpty() || sungOffset <= 0f || width <= 0f) return null

        val textLength = layout.layoutInput.text.length
        if (sungOffset >= textLength.toFloat()) {
            return SweptFrontier(
                rowIndex = rows.lastIndex,
                boundaryX = if (rows.last().isRtl) 0f else width,
                isComplete = true
            )
        }

        val characterIndex = floor(sungOffset)
            .toInt()
            .coerceIn(0, (textLength - 1).coerceAtLeast(0))
        val partialCharacter = (sungOffset - characterIndex).coerceIn(0f, 1f)
        val lineIndex = layout.getLineForOffset(characterIndex)
            .coerceIn(0, rows.lastIndex)
        val characterBounds = layout.getBoundingBox(characterIndex)
        val characterDirection = layout.getBidiRunDirection(characterIndex)

        val boundaryX = if (characterBounds.width > 0f) {
            if (characterDirection == ResolvedTextDirection.Rtl) {
                characterBounds.right - characterBounds.width * partialCharacter
            } else {
                characterBounds.left + characterBounds.width * partialCharacter
            }
        } else {
            layout.getCursorRect(characterIndex).left
        }.coerceIn(0f, width)

        return SweptFrontier(
            rowIndex = lineIndex,
            boundaryX = boundaryX
        )
    }
}

/**
 * One Compose text node owns layout and semantics. Its draw pass paints the dim line
 * once, then draws the sung portion directly from that same cached layout. No duplicate
 * Text composables, per-character Paths, DstOut masks, or offscreen graphics layers.
 */
@Composable
internal fun KaraokeSweepText(
    text: String,
    words: List<LyricWord>,
    wordOffset: Int,
    positionState: State<Int>,
    style: TextStyle,
    baseColor: Color,
    highlightBrush: Brush,
    modifier: Modifier = Modifier,
    maxLines: Int = Int.MAX_VALUE,
    outlineWidthPx: Float = 0f,
    highlightShadow: androidx.compose.ui.graphics.Shadow? = null
) {
    // Brush, shadow and color changes do not alter glyph geometry. Keep the measured
    // layout alive across paint-only recompositions; Text replaces it via onTextLayout
    // only when its actual layout changes.
    val layoutState = remember(text, maxLines) {
        mutableStateOf<TextLayoutResult?>(null)
    }
    // Provider word ranges can be large and may require fallback string searches.
    // Never resolve them during composition: prepare them on Default and let the
    // base text render immediately while the karaoke sweep data is being built.
    val spanState = remember(text, words, wordOffset) {
        mutableStateOf<List<KaraokeWordSpan>?>(null)
    }
    LaunchedEffect(text, words, wordOffset) {
        spanState.value = null
        val preparationContext = currentCoroutineContext()
        spanState.value = withContext(Dispatchers.Default) {
            buildKaraokeWordSpans(text, words, wordOffset) {
                preparationContext.ensureActive()
            }
        }
    }
    val spans = spanState.value
    val sweptRows = remember { SweptRows() }
    var hasLoggedFirstKaraokeLayout by remember(text) { mutableStateOf(false) }
    val layoutStyle = remember(style) {
        style.copy(brush = null, shadow = null)
    }

    Text(
        text = text,
        style = layoutStyle,
        color = baseColor,
        modifier = modifier.drawWithCache {
            val layout = layoutState.value
            val cachedRows = layout?.let(sweptRows::rowsFor)

            onDrawWithContent {
                if (layout == null || cachedRows.isNullOrEmpty()) {
                    drawContent()
                    return@onDrawWithContent
                }

                // Base pass: every glyph is drawn once in its unsung colour.
                drawText(
                    textLayoutResult = layout,
                    color = baseColor,
                    topLeft = Offset.Zero
                )

                // Word geometry may still be preparing on a worker. Keep the
                // unsung line visible and begin the sweep as soon as its spans arrive.
                val readySpans = spans ?: return@onDrawWithContent
                val sungOffset = calculateSungTextOffset(
                    spans = readySpans,
                    positionMs = positionState.value.toLong()
                )
                if (sungOffset <= 0f) return@onDrawWithContent

                val frontier = sweptRows.frontierFor(
                    layout = layout,
                    sungOffset = sungOffset,
                    width = size.width
                ) ?: return@onDrawWithContent

                // Hardware-accelerated clipPath can be expensive on some GPU drivers.
                // The prefix is exactly two rectangular regions: complete rows above
                // the frontier and the sung part of the current row.
                if (frontier.isComplete) {
                    if (outlineWidthPx > 0f) {
                        drawText(
                            textLayoutResult = layout,
                            brush = SolidColor(Color.Black),
                            topLeft = Offset.Zero,
                            drawStyle = Stroke(width = outlineWidthPx)
                        )
                    }
                    drawText(
                        textLayoutResult = layout,
                        brush = highlightBrush,
                        topLeft = Offset.Zero,
                        alpha = 1f,
                        shadow = highlightShadow
                    )
                    return@onDrawWithContent
                }

                val row = cachedRows[frontier.rowIndex]
                if (row.top > 0f) {
                    clipRect(left = 0f, top = 0f, right = size.width, bottom = row.top) {
                        if (outlineWidthPx > 0f) {
                            drawText(
                                textLayoutResult = layout,
                                brush = SolidColor(Color.Black),
                                topLeft = Offset.Zero,
                                drawStyle = Stroke(width = outlineWidthPx)
                            )
                        }
                        drawText(
                            textLayoutResult = layout,
                            brush = highlightBrush,
                            topLeft = Offset.Zero,
                            alpha = 1f,
                            shadow = highlightShadow
                        )
                    }
                }

                val left = if (row.isRtl) frontier.boundaryX else 0f
                val right = if (row.isRtl) size.width else frontier.boundaryX
                if (right > left && row.bottom > row.top) {
                    clipRect(left = left, top = row.top, right = right, bottom = row.bottom) {
                        if (outlineWidthPx > 0f) {
                            drawText(
                                textLayoutResult = layout,
                                brush = SolidColor(Color.Black),
                                topLeft = Offset.Zero,
                                drawStyle = Stroke(width = outlineWidthPx)
                            )
                        }
                        drawText(
                            textLayoutResult = layout,
                            brush = highlightBrush,
                            topLeft = Offset.Zero,
                            alpha = 1f,
                            shadow = highlightShadow
                        )
                    }
                }
            }
        },
        maxLines = maxLines,
        softWrap = true,
        overflow = TextOverflow.Clip,
        onTextLayout = {
            layoutState.value = it
            if (BuildConfig.DEBUG && !hasLoggedFirstKaraokeLayout) {
                hasLoggedFirstKaraokeLayout = true
                Log.i(
                    "QuantuneANRTest",
                    "KARAOKE_TEXT_LAYOUT_READY chars=${text.length} rows=${it.lineCount}"
                )
            }
        }
    )
}
