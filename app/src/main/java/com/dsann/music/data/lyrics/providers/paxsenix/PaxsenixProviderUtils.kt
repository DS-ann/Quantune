package com.dsann.music.data.lyrics.providers.paxsenix

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.JsonPrimitive

/**
 * Tolerant decoding for the response envelopes used by the Paxsenix endpoints.
 *
 * Some endpoints return a plain string, while others wrap a Spotify-style
 * { lyrics: { lines: [...] } } payload inside data/result. Keep the extraction
 * independent of the provider so all Paxsenix sources use the same parsing rules.
 */
object PaxsenixProviderUtils {
    private val lyricKeys = listOf(
        "lyrics", "syncedLyrics", "plainLyrics", "lrc", "content", "text",
        "lyric", "line", "data", "result", "response", "payload"
    )

    fun extractLyrics(element: JsonElement): String? {
        return when {
            element.isJsonNull -> null
            element.isJsonPrimitive -> extractPrimitive(element.asJsonPrimitive)
            element.isJsonArray -> extractFromLinesArray(element.asJsonArray)
            element.isJsonObject -> extractFromObject(element.asJsonObject)
            else -> null
        }
    }

    private fun extractPrimitive(primitive: JsonPrimitive): String? {
        if (!primitive.isString) return null
        val value = primitive.asString.trim()
        if (value.isEmpty()) return null

        // A few proxy versions double-encode their JSON response inside a string.
        if (value.startsWith("{") || value.startsWith("[")) {
            val nested = runCatching { JsonParser.parseString(value) }.getOrNull()
            if (nested != null && !nested.isJsonPrimitive) {
                return extractLyrics(nested) ?: value
            }
        }
        return value
    }

    private fun extractFromObject(obj: JsonObject): String? {
        if (obj.booleanFlag("error") || obj.booleanFlag("isError") ||
            obj.booleanFlag("is_error") || obj.booleanFlag("ok", expected = false) ||
            obj.booleanFlag("success", expected = false)
        ) return null

        // Try lyric-bearing fields recursively, not only primitive strings.
        for (key in lyricKeys) {
            val nested = obj.get(key) ?: continue
            val found = extractLyrics(nested)
            if (!found.isNullOrBlank()) return found
        }

        // Spotify-style line responses commonly put the actual words in "lines".
        obj.get("lines")?.takeIf { it.isJsonArray }?.let {
            extractFromLinesArray(it.asJsonArray)?.takeIf(String::isNotBlank)?.let { text -> return text }
        }
        // Some word-timed responses expose a top-level words array.
        obj.get("words")?.let { words ->
            val joined = extractWordText(words)
            if (!joined.isNullOrBlank()) return joined
        }
        return null
    }

    fun firstSearchResult(element: JsonElement): JsonObject? {
        if (element.isJsonArray) {
            for (item in element.asJsonArray) {
                firstSearchResult(item)?.let { return it }
            }
            return null
        }
        if (!element.isJsonObject) return null

        val obj = element.asJsonObject
        val hasId = listOf("id", "trackId", "track_id", "spotifyId", "videoId")
            .any { obj.has(it) && !obj.get(it).isJsonNull }
        val hasTitle = listOf("name", "title", "trackName", "track_name")
            .any { obj.has(it) && !obj.get(it).isJsonNull }
        if (hasId && hasTitle) return obj

        // Search responses may wrap tracks in { tracks: { items: [...] } } or
        // { data: { results: [...] } }. Walk all nested values as a fallback.
        for ((_, nested) in obj.entrySet()) {
            firstSearchResult(nested)?.let { return it }
        }
        return obj.takeIf { hasId }
    }

    fun string(obj: JsonObject, vararg keys: String): String? {
        for (key in keys) {
            val value = obj.get(key) ?: continue
            if (value.isJsonNull || !value.isJsonPrimitive) continue
            val text = runCatching { value.asString.trim() }.getOrNull()
            if (!text.isNullOrEmpty()) return text
        }
        return null
    }

    fun normalizeSpotifyTrackId(rawId: String): String? {
        val value = rawId.trim()
            .removePrefix("spotify:track:")
            .substringAfterLast("/")
            .substringBefore("?")
            .trim()
        return value.takeIf { it.isNotEmpty() && it != "spotify:track:" }
    }

    fun isWordByWord(lyrics: String): Boolean =
        Regex("""<\d{1,3}:\d{2}[.:]\d{1,3}>""").containsMatchIn(lyrics) ||
            Regex("""\[\d+,\d+].*<\d+,\d+,-?\d+>""").containsMatchIn(lyrics) ||
            Regex("""\[\d+,\d+].*\(\d+,\d+\)""").containsMatchIn(lyrics)

    private fun extractFromLinesArray(lines: JsonArray): String? {
        val output = StringBuilder()
        for (item in lines) {
            if (item.isJsonPrimitive) {
                val text = extractPrimitive(item.asJsonPrimitive) ?: continue
                if (output.isNotEmpty()) output.append('\n')
                output.append(text)
                continue
            }
            if (!item.isJsonObject) continue

            val line = item.asJsonObject
            val rawTime = string(line, "timeTag", "time_tag", "timestamp", "time")
            val time = rawTime?.let(::normalizeTimeTag)
                ?: listOf("startTimeMs", "start_time_ms", "startMs", "start_ms")
                    .firstNotNullOfOrNull { key ->
                        line.get(key)?.takeIf { it.isJsonPrimitive && !it.isJsonNull }
                            ?.let { runCatching { it.asLong }.getOrNull() }
                            ?.let(::formatTimeMs)
                    }
            val wordsValue = listOf("words", "text", "content", "lyric", "line")
                .firstNotNullOfOrNull { line.get(it)?.takeIf { value -> !value.isJsonNull } }
            val words = extractWordText(wordsValue).orEmpty().trim()
            if (words.isEmpty()) continue

            if (output.isNotEmpty()) output.append('\n')
            if (!time.isNullOrBlank()) output.append(time).append(' ')
            output.append(words)
        }
        return output.toString().trim().takeIf { it.isNotEmpty() }
    }

    private fun extractWordText(value: JsonElement?): String? {
        if (value == null || value.isJsonNull) return null
        return when {
            value.isJsonPrimitive -> extractPrimitive(value.asJsonPrimitive)
            value.isJsonArray -> value.asJsonArray.mapNotNull { item ->
                if (item.isJsonObject) {
                    string(item.asJsonObject, "text", "word", "content", "value")
                        ?: extractWordText(item)
                } else {
                    extractWordText(item)
                }
            }.joinToString("").takeIf { it.isNotBlank() }
            value.isJsonObject -> {
                val obj = value.asJsonObject
                string(obj, "text", "word", "content", "value", "words")
                    ?: listOf("words", "items", "tokens").firstNotNullOfOrNull { key ->
                        extractWordText(obj.get(key))
                    }
            }
            else -> null
        }
    }

    private fun normalizeTimeTag(value: String): String? {
        val time = value.trim()
        if (time.isEmpty()) return null
        if (time.startsWith("[") && time.endsWith("]")) return time
        if (Regex("""\d{1,3}:\d{2}(?:[.:]\d{1,3})?""").matches(time)) return "[$time]"
        return null
    }

    private fun formatTimeMs(ms: Long): String {
        val safeMs = ms.coerceAtLeast(0L)
        val minutes = safeMs / 60000L
        val seconds = (safeMs % 60000L) / 1000L
        val hundredths = (safeMs % 1000L) / 10L
        return String.format(java.util.Locale.US, "[%02d:%02d.%02d]", minutes, seconds, hundredths)
    }

    private fun JsonObject.booleanFlag(name: String, expected: Boolean = true): Boolean = try {
        get(name)?.takeIf { it.isJsonPrimitive && !it.isJsonNull }?.asBoolean == expected
    } catch (_: Exception) {
        false
    }
}
