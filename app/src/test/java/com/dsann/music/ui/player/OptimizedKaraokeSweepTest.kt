package com.dsann.music.ui.player

import com.dsann.music.data.lyrics.LyricWord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OptimizedKaraokeSweepTest {

    @Test
    fun calculatesFractionalCharacterFrontierAcrossWords() {
        val spans = buildKaraokeWordSpans(
            text = "Hello world",
            words = listOf(
                LyricWord("Hello", 1_000L, 500L, 0..4),
                LyricWord("world", 1_600L, 500L, 6..10)
            )
        )

        assertEquals(0f, calculateSungTextOffset(spans, 999L), 0.001f)
        assertEquals(2.5f, calculateSungTextOffset(spans, 1_250L), 0.001f)
        assertEquals(5f, calculateSungTextOffset(spans, 1_500L), 0.001f)
        assertEquals(8.5f, calculateSungTextOffset(spans, 1_850L), 0.001f)
        assertEquals(11f, calculateSungTextOffset(spans, 2_100L), 0.001f)
    }

    @Test
    fun resolvesMissingProviderRangesByRememberingWordOffsets() {
        val spans = buildKaraokeWordSpans(
            text = "Hello world",
            words = listOf(
                LyricWord("Hello", 1_000L, 500L),
                LyricWord("world", 1_500L, 500L)
            )
        )

        assertEquals(2, spans.size)
        assertEquals(0, spans[0].startOffset)
        assertEquals(5, spans[0].endOffsetExclusive)
        assertEquals(6, spans[1].startOffset)
        assertEquals(11, spans[1].endOffsetExclusive)
        assertEquals(11f, calculateSungTextOffset(spans, 2_000L), 0.001f)
    }

    @Test
    fun offsetsBracketTextRangesToItsLocalLayout() {
        val spans = buildKaraokeWordSpans(
            text = "voice",
            words = listOf(LyricWord("voice", 2_000L, 400L, 8..12)),
            wordOffset = 8
        )

        assertEquals(1, spans.size)
        assertEquals(0, spans.single().startOffset)
        assertEquals(5, spans.single().endOffsetExclusive)
        assertEquals(2.5f, calculateSungTextOffset(spans, 2_200L), 0.001f)
    }

    @Test
    fun ignoresWordsWithoutVisibleTextRanges() {
        val spans = buildKaraokeWordSpans(
            text = "",
            words = listOf(LyricWord("word", 1_000L, 500L))
        )

        assertTrue(spans.isEmpty())
        assertEquals(0f, calculateSungTextOffset(spans, 1_500L), 0.001f)
    }
}
