package com.dsann.music.data.lyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricsParserInterludeTest {

    @Test
    fun lrcTimestampGapsDoNotInsertSyntheticMusicRows() {
        val parsed = LrcParser.parse(
            "[00:01.00]First line\n[00:10.00]Second line"
        )

        assertEquals(listOf("First line", "Second line"), parsed.map { it.content })
        assertTrue(parsed.none { it.isInterlude })
        assertEquals(listOf(1_000L, 10_000L), parsed.map { it.startTimeMs })
    }

    @Test
    fun karaokeTimestampGapsDoNotInsertSyntheticMusicRows() {
        val raw = "[4000,1000]<0,500,0>First line\n[12000,1000]<0,500,0>Second line"
        val parsed = AdvancedLyricsParser.parse(
            AdvancedLyricsParser.wrapWords(AdvancedLyricsParser.WordKind.KRC, raw)
        ).orEmpty()

        assertEquals(listOf(4_000L, 12_000L), parsed.map { it.startTimeMs })
        assertTrue(parsed.none { it.isInterlude })
    }
}
