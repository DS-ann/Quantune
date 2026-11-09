package com.dsann.music.data.lyrics

import android.util.Log
import org.akanework.gramophone.logic.utils.LrcUtils
import org.akanework.gramophone.logic.utils.SemanticLyrics

/**
 * Parses lyrics content into a list of [LyricLine]s.
 * Delegate parsing to Gramophone's high-fidelity LrcUtils parser.
 */
object LrcParser {
    private const val TAG = "LrcParser"
    @JvmStatic
    fun parse(lrcContent: String?): List<LyricLine> {
        if (lrcContent.isNullOrEmpty()) {
            return emptyList()
        }

        val trimmed = lrcContent.trim()
        if (AdvancedLyricsParser.recognizes(trimmed)) {
            return AdvancedLyricsParser.parse(trimmed).orEmpty()
        }
        when {
            Regex("""(?m)^\s*\[\d+,\d+].*<\d+,\d+,-?\d+>""").containsMatchIn(trimmed) ->
                return AdvancedLyricsParser.parse(AdvancedLyricsParser.wrapWords(AdvancedLyricsParser.WordKind.KRC, trimmed)).orEmpty()
            Regex("""(?m)^\s*\[\d+,\d+]\(\d+,\d+,-?\d+\)""").containsMatchIn(trimmed) ->
                return AdvancedLyricsParser.parse(AdvancedLyricsParser.wrapWords(AdvancedLyricsParser.WordKind.YRC, trimmed)).orEmpty()
            Regex("""(?m)^\s*\[\d+,\d+].*\(\d+,\d+\)""").containsMatchIn(trimmed) ->
                return AdvancedLyricsParser.parse(AdvancedLyricsParser.wrapWords(AdvancedLyricsParser.WordKind.QRC, trimmed)).orEmpty()
        }
        val rawTtml = extractTtmlContent(trimmed)
        if (rawTtml != null) {
            val ttmlLines = TtmlParser.parse(rawTtml)
            if (ttmlLines.isNotEmpty()) return ttmlLines
            // TTML/XML failed to produce timed lines.
            // NEVER pass XML content to LrcUtils because it will treat XML tags as unsynced lyrics!
            Log.w(TAG, "Content was identified as TTML/XML but yielded no lines; suppressing raw XML display")
            return emptyList()
        }
        val parserOptions = LrcUtils.LrcParserOptions(trim = true, multiLine = true, errorText = null)
        val semanticLyrics = try {
            LrcUtils.parseLyrics(lrcContent, null, parserOptions, LrcUtils.LyricFormat.LRC)
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing lyrics via Gramophone parser", e)
            null
        } ?: return emptyList()

        val parsedLines = mutableListOf<LyricLine>()
        if (semanticLyrics is SemanticLyrics.SyncedLyrics) {
            val semanticLines = semanticLyrics.text
            semanticLines.forEachIndexed { lineIndex, line ->
                val sourceWords = line.words.orEmpty()
                val nextLineStart = semanticLines.getOrNull(lineIndex + 1)?.start?.toLong()
                val words = sourceWords.mapIndexed { wordIndex, word ->
                    val wordText = if (word.charRange.first in 0 until line.text.length &&
                        word.charRange.last in 0 until line.text.length &&
                        word.charRange.first <= word.charRange.last
                    ) {
                        line.text.substring(word.charRange)
                    } else {
                        ""
                    }
                    val startMs = word.begin.toLong()
                    val explicitEndMs = word.endInclusive?.toLong()
                    val nextWordStartMs = sourceWords.getOrNull(wordIndex + 1)?.begin?.toLong()
                    LyricWord(
                        text = wordText,
                        absoluteStartTimeMs = startMs,
                        durationMs = inferKaraokeWordDurationMs(
                            wordStartMs = startMs,
                            explicitEndMs = explicitEndMs,
                            nextWordStartMs = nextWordStartMs,
                            nextLineStartMs = nextLineStart
                        ),
                        charRange = word.charRange
                    )
                }

                val isRightAligned = line.text.startsWith("[Singer 2", ignoreCase = true) ||
                    line.text.startsWith("[Artist 2", ignoreCase = true) ||
                    line.text.startsWith("(Singer 2", ignoreCase = true) ||
                    line.text.startsWith("(Artist 2", ignoreCase = true) ||
                    line.text.startsWith("[Duet 2", ignoreCase = true)

                parsedLines.add(
                    LyricLine(
                        startTimeMs = line.start.toLong(),
                        content = line.text,
                        isInterlude = false,
                        isWordByWord = words.isNotEmpty(),
                        words = words,
                        isRightAligned = isRightAligned
                    )
                )
            }
        } else if (semanticLyrics is SemanticLyrics.UnsyncedLyrics) {
            // Unsynced lyrics (plain lyrics)
            for (line in semanticLyrics.unsyncedText) {
                val isRightAligned = line.first.startsWith("[Singer 2", ignoreCase = true) ||
                        line.first.startsWith("[Artist 2", ignoreCase = true) ||
                        line.first.startsWith("(Singer 2", ignoreCase = true) ||
                        line.first.startsWith("(Artist 2", ignoreCase = true) ||
                        line.first.startsWith("[Duet 2", ignoreCase = true)

                parsedLines.add(
                    LyricLine(
                        startTimeMs = 0L,
                        content = line.first,
                        isInterlude = false,
                        isWordByWord = false,
                        words = emptyList(),
                        isRightAligned = isRightAligned
                    )
                )
            }
        }

        parsedLines.sort()

        val normalizedLines = parsedLines.map { normalizeLineSpacing(it) }

        // Keep the parsed source timestamps; do not synthesize instrumental rows.
        return normalizedLines
    }

    /**
     * Semantic LRC words occasionally carry only a start timestamp. Use the next
     * word or next line boundary as the end instead of rendering that word in a
     * single frame. A short fallback is reserved for a final word with no boundary.
     */
    internal fun inferKaraokeWordDurationMs(
        wordStartMs: Long,
        explicitEndMs: Long?,
        nextWordStartMs: Long?,
        nextLineStartMs: Long?,
        fallbackDurationMs: Long = 300L
    ): Long {
        val explicitDuration = explicitEndMs
            ?.minus(wordStartMs)
            ?.takeIf { it > 0L }
        val inferredEnd = nextWordStartMs?.takeIf { it > wordStartMs }
            ?: nextLineStartMs?.takeIf { it > wordStartMs }
        val inferredDuration = inferredEnd?.minus(wordStartMs)
        return (explicitDuration ?: inferredDuration ?: fallbackDurationMs).coerceAtLeast(50L)
    }

    private fun normalizeLineSpacing(line: LyricLine): LyricLine {
        if (!line.content.contains(Regex("[ \\t]{2,}")) && !line.words.any { it.text.contains(Regex("[ \\t]{2,}")) }) {
            return line
        }
        if (line.words.isEmpty()) {
            return line.copy(content = line.content.replace(Regex("[ \\t]+"), " ").trim())
        }
        val sb = StringBuilder()
        var lastIdx = 0
        val sortedWords = line.words.sortedBy { it.charRange.first }
        val newWords = mutableListOf<LyricWord>()
        for (w in sortedWords) {
            val wStart = w.charRange.first.coerceIn(0, line.content.length)
            val wEnd = (w.charRange.last + 1).coerceIn(0, line.content.length)
            if (wStart > lastIdx) {
                val gap = line.content.substring(lastIdx, wStart)
                val cleanGap = if (gap.all { it.isWhitespace() }) " " else gap.replace(Regex("[ \\t]+"), " ")
                sb.append(cleanGap)
            }
            val newWordStart = sb.length
            val wordText = if (wEnd > wStart) {
                line.content.substring(wStart, wEnd).replace(Regex("[ \\t]+"), " ")
            } else {
                w.text.replace(Regex("[ \\t]+"), " ")
            }
            sb.append(wordText)
            val newWordEnd = (sb.length - 1).coerceAtLeast(newWordStart)
            newWords.add(
                w.copy(
                    text = wordText,
                    charRange = newWordStart..newWordEnd
                )
            )
            lastIdx = wEnd
        }
        if (lastIdx < line.content.length) {
            val trailing = line.content.substring(lastIdx).replace(Regex("[ \\t]+"), " ")
            sb.append(trailing)
        }
        return line.copy(content = sb.toString().trimEnd(), words = newWords)
    }

    private fun extractTtmlContent(text: String): String? {
        if (text.isBlank()) return null

        val trimmed = text.trim()

        // 1. Check if the string is wrapped in a JSON response
        if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
            val jsonExtracted = extractTtmlFromJson(trimmed)
            if (jsonExtracted != null) return jsonExtracted
        }

        // 2. Direct XML/TTML format checks
        if (trimmed.contains("<tt", ignoreCase = true) ||
            trimmed.contains("<p begin=", ignoreCase = true) ||
            trimmed.contains("ttm:begin", ignoreCase = true) ||
            (trimmed.contains("<?xml", ignoreCase = true) && !trimmed.contains("<Qrc", ignoreCase = true) && !trimmed.contains("LyricContent", ignoreCase = true))
        ) {
            return trimmed
        }

        return null
    }

    private fun extractTtmlFromJson(jsonStr: String): String? {
        return try {
            val parsed = com.google.gson.JsonParser.parseString(jsonStr)
            if (!parsed.isJsonObject) return null
            val obj = parsed.asJsonObject
            val candidateKeys = listOf("lyrics", "ttml", "content", "ttmlContent", "data", "syncedLyrics", "lrc", "body", "response")
            for (key in candidateKeys) {
                if (obj.has(key)) {
                    val elem = obj.get(key)
                    if (elem.isJsonPrimitive && elem.asJsonPrimitive.isString) {
                        val strVal = elem.asString.trim()
                        if (strVal.startsWith("{") && strVal.endsWith("}")) {
                            val nested = extractTtmlFromJson(strVal)
                            if (nested != null) return nested
                        }
                        if (strVal.contains("<tt", ignoreCase = true) || strVal.contains("<p begin=", ignoreCase = true) || strVal.contains("<?xml", ignoreCase = true)) {
                            return strVal
                        }
                    } else if (elem.isJsonObject) {
                        val nested = extractTtmlFromJson(elem.toString())
                        if (nested != null) return nested
                    }
                }
            }
            null
        } catch (_: Exception) {
            null
        }
    }
}
