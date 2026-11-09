package com.dsann.music.data.lyrics

import android.text.Html
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser

/**
 * Converts karaoke formats from external lyrics catalogues into D.S Music's
 * existing LyricLine/LyricWord model. Raw payloads are wrapped with an internal
 * marker so they survive the existing LyricsResult and cache flow.
 */
object AdvancedLyricsParser {
    const val WORD_PREFIX = "DSMUSIC-WORDS:"
    const val LYRICS_PLUS_PREFIX = "DSMUSIC-LYRICSPLUS\n"
    private val legacyWordPrefix = Regex("""^[A-Z0-9]+-WORDS:""")
    private val legacyLyricsPlusPrefix = Regex("""^[A-Z0-9]+-LYRICSPLUS\n""")

    enum class WordKind { QRC, YRC, KRC }

    private data class TimedToken(val startMs: Long, val durationMs: Long, val text: String)
    private data class WordPiece(val startMs: Long, val endMs: Long, val text: String)

    private val lineHeader = Regex("""^\[(\d+),(\d+)]""")
    private val qrcMarker = Regex("""\((\d+),(\d+)\)""")
    private val yrcMarker = Regex("""\((\d+),(\d+),-?\d+\)""")
    private val krcMarker = Regex("""<(\d+),(\d+),-?\d+>""")

    fun wrapWords(kind: WordKind, content: String): String = "$WORD_PREFIX${kind.name}\n$content"
    fun wrapLyricsPlus(json: String): String = LYRICS_PLUS_PREFIX + json

    fun recognizes(content: String): Boolean =
        content.startsWith(WORD_PREFIX) ||
            content.startsWith(LYRICS_PLUS_PREFIX) ||
            legacyWordPrefix.containsMatchIn(content) ||
            legacyLyricsPlusPrefix.containsMatchIn(content)

    /** Returns null only when this is a format we do not own; a malformed wrapped payload returns an empty list. */
    fun parse(content: String): List<LyricLine>? {
        val oldWordPrefix = legacyWordPrefix.find(content)?.value
        val oldLyricsPrefix = legacyLyricsPlusPrefix.find(content)?.value
        return when {
            content.startsWith(WORD_PREFIX) -> parseWrappedWords(content, WORD_PREFIX)
            oldWordPrefix != null -> parseWrappedWords(content, oldWordPrefix)
            content.startsWith(LYRICS_PLUS_PREFIX) ->
                parseLyricsPlus(content.removePrefix(LYRICS_PLUS_PREFIX))
            oldLyricsPrefix != null ->
                parseLyricsPlus(content.removePrefix(oldLyricsPrefix))
            else -> null
        }
    }

    private fun parseWrappedWords(content: String, prefix: String): List<LyricLine> {
        val kind = runCatching {
            WordKind.valueOf(content.substringAfter(prefix).substringBefore('\n').trim())
        }.getOrNull() ?: return emptyList()
        return parseWordLines(kind, content.substringAfter('\n', ""))
    }

    private fun parseWordLines(kind: WordKind, content: String): List<LyricLine> {
        val lines = mutableListOf<LyricLine>()
        content.lineSequence().forEach { raw ->
            val line = raw.trim()
            val header = lineHeader.find(line) ?: return@forEach
            val lineStart = header.groupValues[1].toLongOrNull() ?: return@forEach
            val lineDuration = header.groupValues[2].toLongOrNull() ?: 0L
            val tail = line.substring(header.range.last + 1)
            val tokens = mutableListOf<TimedToken>()

            when (kind) {
                WordKind.QRC -> {
                    // QQ QRC attaches each timing marker to the lyric segment immediately before it.
                    var lastEnd = 0
                    qrcMarker.findAll(tail).forEach { marker ->
                        tokens += TimedToken(
                            marker.groupValues[1].toLongOrNull() ?: lineStart,
                            marker.groupValues[2].toLongOrNull() ?: 0L,
                            tail.substring(lastEnd, marker.range.first)
                        )
                        lastEnd = marker.range.last + 1
                    }
                }
                WordKind.YRC, WordKind.KRC -> {
                    // NetEase YRC uses absolute milliseconds; KuGou KRC uses offsets from the line start.
                    val regex = if (kind == WordKind.YRC) yrcMarker else krcMarker
                    val markers = regex.findAll(tail).toList()
                    markers.forEachIndexed { index, marker ->
                        val textStart = marker.range.last + 1
                        val textEnd = markers.getOrNull(index + 1)?.range?.first ?: tail.length
                        val rawStart = marker.groupValues[1].toLongOrNull() ?: lineStart
                        val start = if (kind == WordKind.KRC) lineStart + rawStart else rawStart
                        val duration = marker.groupValues[2].toLongOrNull() ?: 0L
                        tokens += TimedToken(start, duration, tail.substring(textStart, textEnd))
                    }
                }
            }

            if (tokens.isEmpty()) return@forEach
            val words = buildWords(tokens, lineStart + lineDuration)
            val text = words.joinToString("") { it.text }.trim()
            if (text.isBlank() || isCreditLine(text)) return@forEach
            // QQ/KuGou may include a title/artist card before the first real lyric line.
            if (lines.isEmpty() && lineStart < 1000L && text.contains(" - ")) return@forEach
            val normalizedWords = alignWordRanges(words, text)
            lines += LyricLine(
                startTimeMs = lineStart,
                content = text,
                isWordByWord = normalizedWords.isNotEmpty(),
                words = normalizedWords
            )
            @Suppress("UNUSED_VARIABLE")
            val ignoredLineDuration = lineDuration
        }
        return lines.sortedBy { it.startTimeMs }
    }

    private fun parseLyricsPlus(json: String): List<LyricLine> {
        val root = try {
            JsonParser.parseString(json).asJsonObject
        } catch (_: Exception) {
            return emptyList()
        }
        if (root.string("type")?.equals("None", ignoreCase = true) == true) return emptyList()
        val rawLines = root.array("lyrics") ?: return emptyList()
        val agents = root.obj("metadata")?.obj("agents")
        val multipleVoices = agents != null && agents.size() > 1
        val lines = mutableListOf<LyricLine>()

        rawLines.forEach { element ->
            val line = element.asObject() ?: return@forEach
            val start = line.string("time")?.toLongOrNull() ?: return@forEach
            val duration = line.string("duration")?.toLongOrNull()?.coerceAtLeast(0L) ?: 0L
            val syllables = line.array("syllabus") ?: line.array("syllables") ?: JsonArray()
            val mainTokens = mutableListOf<TimedToken>()
            val backgroundTokens = mutableListOf<TimedToken>()
            syllables.forEach { syllableElement ->
                val syllable = syllableElement.asObject() ?: return@forEach
                val tokenText = syllable.string("text").orEmpty()
                val tokenTime = syllable.string("time")?.toLongOrNull() ?: return@forEach
                val tokenDuration = syllable.string("duration")?.toLongOrNull()?.coerceAtLeast(0L) ?: 0L
                val token = TimedToken(tokenTime, tokenDuration, tokenText)
                val background = syllable.string("isBackground")?.equals("true", true) == true
                if (background) backgroundTokens += token else mainTokens += token
            }
            val words = buildWords(mainTokens, start + duration)
            val bgWordsRaw = buildWords(backgroundTokens, start + duration)
            val fallbackText = line.string("text")?.trim().orEmpty()
            val text = if (words.isNotEmpty()) words.joinToString("") { it.text }.trim() else fallbackText
            if (text.isBlank()) return@forEach
            val finalWords = alignWordRanges(words, text)
            val bgText = bgWordsRaw.joinToString("") { it.text }.trim().ifBlank { null }
            val bgWords = if (bgText == null) emptyList() else alignWordRanges(bgWordsRaw, bgText)
                .map { it.copy(isBackground = true) }
            val singer = line.obj("element")?.string("singer")
            lines += LyricLine(
                startTimeMs = start,
                content = text,
                isWordByWord = finalWords.isNotEmpty(),
                words = finalWords,
                backgroundContent = bgText,
                backgroundWords = bgWords,
                agent = singer,
                isRightAligned = multipleVoices && singer != null && singer != "v1"
            )
            @Suppress("UNUSED_VARIABLE")
            val ignoredLineDuration = duration
        }
        return lines.sortedBy { it.startTimeMs }
    }

    /** Preserve timed segments even when a lyric line contains no spaces. */
    private fun buildWords(tokens: List<TimedToken>, fallbackEndMs: Long): List<LyricWord> {
        val decoded = tokens.mapNotNull { token ->
            decodeEntities(token.text).takeIf { it.isNotEmpty() }?.let { it to token }
        }
        val output = mutableListOf<LyricWord>()
        var cursor = 0
        decoded.forEachIndexed { index, (text, token) ->
            val start = cursor
            cursor += text.length
            val nextStart = decoded.getOrNull(index + 1)?.second?.startMs
            val inferredDuration = when {
                nextStart != null && nextStart > token.startMs -> nextStart - token.startMs
                fallbackEndMs > token.startMs -> fallbackEndMs - token.startMs
                else -> 300L
            }
            output += LyricWord(
                text = text,
                absoluteStartTimeMs = token.startMs.coerceAtLeast(0L),
                durationMs = (token.durationMs.takeIf { it > 0L } ?: inferredDuration).coerceAtLeast(50L),
                charRange = start..(cursor - 1)
            )
        }
        return output
    }

    /** Recalculate ranges against the trimmed visible text, including leading/trailing token spaces. */
    private fun alignWordRanges(words: List<LyricWord>, lineText: String): List<LyricWord> {
        if (words.isEmpty() || lineText.isEmpty()) return emptyList()
        val joined = words.joinToString("") { it.text }
        val visibleStart = joined.indexOfFirst { !it.isWhitespace() }.coerceAtLeast(0)
        val visibleEnd = joined.indexOfLast { !it.isWhitespace() } + 1
        val out = mutableListOf<LyricWord>()
        var sourceCursor = 0
        words.forEach { word ->
            val sourceEnd = sourceCursor + word.text.length
            val tokenLeadingTrim = word.text.indexOfFirst { !it.isWhitespace() }
                .takeIf { it >= 0 } ?: word.text.length
            val tokenTrailingEnd = word.text.indexOfLast { !it.isWhitespace() } + 1
            val visibleTokenStart = sourceCursor + tokenLeadingTrim
            val visibleTokenEnd = sourceCursor + tokenTrailingEnd
            val overlapStart = maxOf(visibleTokenStart, visibleStart)
            val overlapEnd = minOf(visibleTokenEnd, visibleEnd)
            if (overlapStart < overlapEnd) {
                val from = overlapStart - sourceCursor
                val until = overlapEnd - sourceCursor
                val visible = word.text.substring(from, until)
                val charStart = overlapStart - visibleStart
                out += word.copy(
                    text = visible,
                    charRange = charStart..(charStart + visible.length - 1)
                )
            }
            sourceCursor = sourceEnd
        }
        // Guard against a malformed provider payload whose token text does not reproduce
        // the line string. Keep original positions, but never emit partial/misaligned ranges.
        val covered = BooleanArray(lineText.length)
        out.forEach { word ->
            for (index in word.charRange.first..word.charRange.last) {
                if (index in covered.indices) covered[index] = true
            }
        }
        return out.takeIf { lineText.indices.all { index ->
            lineText[index].isWhitespace() || covered[index]
        } }.orEmpty()
    }

    private fun isCreditLine(text: String): Boolean =
        Regex("""(?i)^(?:lyric(?:s)?\s*(?:by|:)|作词|作曲|编曲|produced by)\b""").containsMatchIn(text.trim())

    private fun decodeEntities(text: String): String {
        if (!text.contains('&')) return text
        @Suppress("DEPRECATION")
        return Html.fromHtml(text).toString()
    }

    private fun JsonElement?.asObject(): JsonObject? = try {
        if (this != null && this.isJsonObject) this.asJsonObject else null
    } catch (_: Exception) { null }

    private fun JsonElement?.string(key: String): String? = this.asObject()?.string(key)
    private fun JsonElement?.obj(key: String): JsonObject? = this.asObject()?.get(key).asObject()
    private fun JsonElement?.array(key: String): JsonArray? = try {
        val element = this.asObject()?.get(key)
        if (element != null && element.isJsonArray) element.asJsonArray else null
    } catch (_: Exception) { null }

    private fun JsonObject.string(key: String): String? = try {
        val value = get(key)
        if (value == null || value.isJsonNull || !value.isJsonPrimitive) null else value.asString
    } catch (_: Exception) { null }

    private fun JsonObject.obj(key: String): JsonObject? = try {
        val value = get(key)
        if (value != null && value.isJsonObject) value.asJsonObject else null
    } catch (_: Exception) { null }

    private fun JsonObject.array(key: String): JsonArray? = try {
        val value = get(key)
        if (value != null && value.isJsonArray) value.asJsonArray else null
    } catch (_: Exception) { null }
}
