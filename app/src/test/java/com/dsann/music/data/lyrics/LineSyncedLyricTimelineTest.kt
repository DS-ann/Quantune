package com.dsann.music.data.lyrics

import org.junit.Assert.assertEquals
import org.junit.Test

class LineSyncedLyricTimelineTest {

    @Test
    fun standardSyncedLinesUseTheirLineTimestamps() {
        // Auxiliary word metadata must not turn ordinary LRC lines into karaoke timing.
        val lines = listOf(
            LyricLine(
                startTimeMs = 1_000L,
                content = "First line",
                isWordByWord = false,
                words = listOf(LyricWord("First", 1_600L, 300L))
            ),
            LyricLine(
                startTimeMs = 3_000L,
                content = "Second line",
                isWordByWord = false,
                words = listOf(LyricWord("Second", 3_400L, 300L))
            )
        )

        val timeline = LineSyncedLyricTimeline.build(lines)

        assertEquals(1_000L, timeline[0].startTimeMs)
        assertEquals(3_000L, timeline[0].completionTimeMs)
        assertEquals(0, LineSyncedLyricTimeline.activeIndexAt(timeline, 1_000L))
        assertEquals(0, LineSyncedLyricTimeline.activeIndexAt(timeline, 2_999L))
        assertEquals(1, LineSyncedLyricTimeline.activeIndexAt(timeline, 3_000L))
    }

    @Test
    fun karaokeLinesKeepTheirWordTiming() {
        val lines = listOf(
            LyricLine(
                startTimeMs = 1_000L,
                content = "Karaoke line",
                isWordByWord = true,
                words = listOf(
                    LyricWord("Karaoke", 1_200L, 400L),
                    LyricWord("line", 1_800L, 400L)
                )
            ),
            LyricLine(
                startTimeMs = 2_500L,
                content = "Next line",
                isWordByWord = true,
                words = listOf(LyricWord("Next", 2_700L, 300L))
            )
        )

        val timeline = LineSyncedLyricTimeline.build(lines)

        assertEquals(1_200L, timeline[0].startTimeMs)
        // The first full line remains visible through the silent gap midpoint.
        assertEquals(2_450L, timeline[0].completionTimeMs)
        assertEquals(0, LineSyncedLyricTimeline.activeIndexAt(timeline, 2_449L))
        assertEquals(1, LineSyncedLyricTimeline.activeIndexAt(timeline, 2_450L))
    }
}
