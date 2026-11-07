package com.dsann.music.data.lyrics

/**
 * Whole-line lyric timing shared by compact queue and widget previews.
 * Word-timed karaoke is displayed as one complete line: first word starts it,
 * last word completes it, and a silent gap transitions at its midpoint.
 */
object LineSyncedLyricTimeline {
    data class Entry(
        val line: LyricLine,
        val startTimeMs: Long,
        val completionTimeMs: Long,
        val lastWordEndMs: Long?
    )

    fun build(lines: List<LyricLine>): List<Entry> {
        val sorted = lines.mapIndexedNotNull { index, line ->
            if (line.isInterlude || line.content.isBlank()) null
            else {
                // Standard line-synced providers may attach auxiliary word metadata, but
                // their authoritative boundary is still the line timestamp. Only genuine
                // karaoke lines may shift to the first word timestamp.
                val firstWordStart = if (line.isWordByWord && line.words.isNotEmpty()) {
                    line.words.minOfOrNull { it.absoluteStartTimeMs }
                } else {
                    null
                }
                Triple(index, line, firstWordStart ?: line.startTimeMs)
            }
        }.sortedWith(
            compareBy<Triple<Int, LyricLine, Long>> { it.third }.thenBy { it.first }
        )

        return sorted.mapIndexed { index, (_, line, start) ->
            // Do not let incidental word metadata shorten an ordinary synced line.
            // The widget displays one complete line; word timing is used only for
            // sources explicitly classified as word-by-word karaoke.
            val lastWordEnd = if (line.isWordByWord) line.words.asSequence()
                .filter { it.durationMs > 0L }
                .map { it.absoluteStartTimeMs + it.durationMs }
                .maxOrNull()
            else null
            val nextStart = sorted.getOrNull(index + 1)?.third
            val completion = when {
                nextStart == null -> lastWordEnd ?: Long.MAX_VALUE
                lastWordEnd != null && nextStart > lastWordEnd ->
                    lastWordEnd + (nextStart - lastWordEnd) / 2L
                lastWordEnd != null -> lastWordEnd
                else -> nextStart
            }
            Entry(line, start, completion, lastWordEnd)
        }
    }

    fun activeIndexAt(
        timeline: List<Entry>,
        positionMs: Long,
        durationMs: Long = 0L
    ): Int {
        if (timeline.isEmpty() || positionMs < timeline.first().startTimeMs) return -1
        if (durationMs > 0L && positionMs >= durationMs) return -1
        var index = 0
        while (index < timeline.lastIndex &&
            positionMs >= timeline[index].completionTimeMs
        ) index++
        val finalWordEnd = timeline.last().lastWordEndMs
        if (index == timeline.lastIndex &&
            finalWordEnd != null &&
            positionMs > finalWordEnd + 1_000L
        ) return -1
        return index
    }

    /** Schedules line changes only; it deliberately ignores intermediate word events. */
    fun nextBoundaryAfter(timeline: List<Entry>, positionMs: Long): Long? {
        if (timeline.isEmpty()) return null
        if (positionMs < timeline.first().startTimeMs) return timeline.first().startTimeMs
        val index = activeIndexAt(timeline, positionMs)
        if (index < 0) {
            return timeline.last().lastWordEndMs?.plus(1_001L)?.takeIf { it > positionMs }
        }
        if (index < timeline.lastIndex) {
            return timeline[index].completionTimeMs
                .takeIf { it != Long.MAX_VALUE && it > positionMs }
        }
        return timeline.last().lastWordEndMs?.plus(1_001L)?.takeIf { it > positionMs }
    }
}
