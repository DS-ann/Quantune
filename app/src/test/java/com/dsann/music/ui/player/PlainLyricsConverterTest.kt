package com.dsann.music.ui.player

import com.dsann.music.data.lyrics.LyricLine
import com.dsann.music.data.lyrics.LyricWord
import org.junit.Assert.assertEquals
import org.junit.Test

class PlainLyricsConverterTest {

    @Test
    fun convertsWordTimedLyricsWithoutTimestamps() {
        val lyrics = listOf(
            LyricLine(
                startTimeMs = 1_000L,
                content = "Hello world",
                isWordByWord = true,
                words = listOf(
                    LyricWord("Hello", 1_000L, 500L, 0..4),
                    LyricWord(" world", 1_500L, 500L, 5..10)
                )
            ),
            LyricLine(startTimeMs = 2_500L, content = "Keep going")
        )

        assertEquals(
            "Hello world\nKeep going",
            convertSyncedLyricsToPlainText(lyrics)
        )
    }

    @Test
    fun fallsBackToWordTextAndKeepsBackgroundVocals() {
        val lyrics = listOf(
            LyricLine(
                startTimeMs = 1_000L,
                content = "",
                words = listOf(LyricWord("Main", 1_000L, 400L)),
                backgroundContent = "echo"
            )
        )

        assertEquals(
            "Main\necho",
            convertSyncedLyricsToPlainText(lyrics)
        )
    }

    @Test
    fun removesEmptyRowsAndRetainsInterludeText() {
        val lyrics = listOf(
            LyricLine(startTimeMs = 0L, content = "  "),
            LyricLine(startTimeMs = 1_000L, content = "Instrumental", isInterlude = true)
        )

        assertEquals(
            "Instrumental",
            convertSyncedLyricsToPlainText(lyrics)
        )
    }
}
