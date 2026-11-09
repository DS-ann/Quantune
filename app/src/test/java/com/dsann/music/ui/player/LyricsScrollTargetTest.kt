package com.dsann.music.ui.player

import com.dsann.music.data.lyrics.LyricLine
import com.dsann.music.data.lyrics.LyricWord
import org.junit.Assert.assertEquals
import org.junit.Test

class LyricsScrollTargetTest {

    @Test
    fun binarySearchPreservesCompletionBoundariesAndSkipsInterludes() {
        val lines = listOf(
            LyricLine(
                startTimeMs = 1_000L,
                content = "First",
                isWordByWord = true,
                words = listOf(LyricWord("First", 1_400L, 600L))
            ),
            LyricLine(startTimeMs = 2_500L, content = "", isInterlude = true),
            LyricLine(
                startTimeMs = 4_000L,
                content = "Second",
                isWordByWord = true,
                words = listOf(LyricWord("Second", 4_200L, 500L))
            ),
            LyricLine(startTimeMs = 6_000L, content = "Third")
        )
        val boundaries = buildLyricsScrollBoundaries(lines)

        assertEquals(0, findLyricsScrollTargetIndex(boundaries, 1_999L))
        assertEquals(2, findLyricsScrollTargetIndex(boundaries, 2_000L))
        assertEquals(2, findLyricsScrollTargetIndex(boundaries, 4_699L))
        assertEquals(3, findLyricsScrollTargetIndex(boundaries, 4_700L))
        assertEquals(3, findLyricsScrollTargetIndex(boundaries, 9_000L))
    }

    @Test
    fun prefixMaximumPreservesSequentialStopForOverlappingTimestamps() {
        val lines = listOf(
            LyricLine(
                startTimeMs = 1_000L,
                content = "First",
                isWordByWord = true,
                words = listOf(LyricWord("First", 1_500L, 1_500L))
            ),
            LyricLine(
                startTimeMs = 2_000L,
                content = "Second",
                isWordByWord = true,
                words = listOf(LyricWord("Second", 2_000L, 500L))
            ),
            LyricLine(startTimeMs = 3_000L, content = "Third")
        )
        val boundaries = buildLyricsScrollBoundaries(lines)

        assertEquals(0, findLyricsScrollTargetIndex(boundaries, 2_500L))
        assertEquals(2, findLyricsScrollTargetIndex(boundaries, 3_000L))
    }

    @Test
    fun emptyAndSingleLineListsHaveSafeTargets() {
        assertEquals(
            -1,
            findLyricsScrollTargetIndex(buildLyricsScrollBoundaries(emptyList()), 1_000L)
        )
        assertEquals(
            0,
            findLyricsScrollTargetIndex(
                buildLyricsScrollBoundaries(listOf(LyricLine(1_000L, "Only line"))),
                1_000L
            )
        )
    }
}
