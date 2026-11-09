package com.dsann.music.data.lyrics.providers

import android.text.Html
import android.util.Base64
import android.util.Log
import com.dsann.music.data.lyrics.AdvancedLyricsParser
import com.dsann.music.data.lyrics.LrcParser
import com.dsann.music.data.lyrics.LyricsResult
import com.dsann.music.data.lyrics.TtmlParser
import com.dsann.music.data.lyrics.TrackMetadata
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.ByteArrayInputStream
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.util.zip.InflaterInputStream
import kotlin.math.abs
import org.w3c.dom.Element

private const val EXTERNAL_LYRICS_UA =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0.0.0 Safari/537.36"

private fun lyricsHttp(client: OkHttpClient): OkHttpClient =
    client.newBuilder().callTimeout(7, TimeUnit.SECONDS).retryOnConnectionFailure(true).build()

private fun lyricsRequest(
    client: OkHttpClient,
    url: HttpUrl,
    headers: Map<String, String> = emptyMap()
): String? = try {
    val request = Request.Builder().url(url).header("User-Agent", EXTERNAL_LYRICS_UA).apply {
        headers.forEach { (key, value) -> header(key, value) }
    }.build()
    client.newCall(request).execute().use { response ->
        if (response.isSuccessful) response.body?.string() else null
    }
} catch (error: Exception) {
    Log.d("ExternalLyrics", "Lyrics request failed: ${error.message}")
    null
}

private fun lyricsJson(client: OkHttpClient, url: HttpUrl, headers: Map<String, String> = emptyMap()): JsonElement? =
    lyricsRequest(client, url, headers)?.let { body ->
        try { JsonParser.parseString(body) } catch (_: Exception) { null }
    }

private fun JsonElement?.asObjectOrNull(): JsonObject? = try {
    if (this != null && this.isJsonObject) this.asJsonObject else null
} catch (_: Exception) { null }

private fun JsonElement?.asArrayOrNull(): JsonArray? = try {
    if (this != null && this.isJsonArray) this.asJsonArray else null
} catch (_: Exception) { null }

private fun JsonElement?.field(name: String): JsonElement? = this.asObjectOrNull()?.get(name)

private fun JsonElement?.textValue(): String? = try {
    if (this == null || this.isJsonNull || !this.isJsonPrimitive) null else this.asString
} catch (_: Exception) { null }

private fun JsonElement?.text(name: String): String? = this.field(name).textValue()

private fun JsonElement?.asNumber(name: String): Double? =
    this.text(name)?.toDoubleOrNull()

private fun cleanLyricTitle(value: String): String = value
    .replace(Regex("""\s*[\[(](?:official|lyrics?|audio|video|visualizer|music video|mv|hd|4k|explicit)[^\])]*[\])]""", RegexOption.IGNORE_CASE), "")
    .replace(Regex("""\s*-\s*(?:official|lyrics?).*$""", RegexOption.IGNORE_CASE), "")
    .trim()

private fun lyricKey(value: String): String =
    java.text.Normalizer.normalize(value.lowercase(Locale.ROOT), java.text.Normalizer.Form.NFD)
        .replace(Regex("""\p{M}+"""), "")
        .replace(Regex("""[^\p{L}\p{N}]"""), "")

private fun matchingTrack(title: String?, artist: String?, track: TrackMetadata): Boolean {
    if (title.isNullOrBlank()) return false
    val actualTitle = lyricKey(cleanLyricTitle(title))
    val wantedTitle = lyricKey(cleanLyricTitle(track.cleanedTitle))
    if (actualTitle.isBlank() || wantedTitle.isBlank()) return false
    val titleMatches = actualTitle == wantedTitle ||
        actualTitle.contains(wantedTitle) || wantedTitle.contains(actualTitle)
    val actualArtist = lyricKey(artist.orEmpty())
    val wantedArtist = lyricKey(track.cleanedArtist)
    val artistMatches = wantedArtist.isBlank() || actualArtist.isBlank() ||
        actualArtist.contains(wantedArtist) || wantedArtist.contains(actualArtist)
    return titleMatches && artistMatches
}

private fun durationFits(track: TrackMetadata, candidateSeconds: Double?): Boolean {
    if (track.durationMs <= 0L || candidateSeconds == null || candidateSeconds <= 0.0) return true
    return abs(track.durationMs / 1000.0 - candidateSeconds) <= 8.0
}

private fun lyricResult(
    provider: String,
    track: TrackMetadata,
    rawContent: String?,
    matchedTitle: String? = track.cleanedTitle,
    matchedArtist: String? = track.cleanedArtist,
    durationSeconds: Double? = null
): LyricsResult? {
    val raw = rawContent?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    val result = LyricsResult(
        providerName = provider,
        matchedTitle = matchedTitle ?: track.cleanedTitle,
        matchedArtist = matchedArtist ?: track.cleanedArtist,
        matchedDuration = (durationSeconds ?: track.durationMs / 1000.0).toFloat()
    )

    when {
        AdvancedLyricsParser.recognizes(raw) -> {
            result.setSyncedLyrics(raw)
            result.isWordByWord = AdvancedLyricsParser.parse(raw).orEmpty().any {
                it.isWordByWord && it.words.isNotEmpty()
            }
        }
        raw.contains("<tt", ignoreCase = true) ||
            raw.contains("<p begin=", ignoreCase = true) ||
            raw.contains("ttm:begin", ignoreCase = true) -> {
            result.setSyncedLyrics(raw)
            result.isWordByWord = TtmlParser.parse(raw).any { it.isWordByWord && it.words.isNotEmpty() }
        }
        Regex("""\[\d{1,2}:\d{2}(?:[.:]\d{1,3})?]""").containsMatchIn(raw) ||
            Regex("""<\d{1,2}:\d{2}[.:]\d{1,3}>""").containsMatchIn(raw) -> {
            result.setSyncedLyrics(raw)
            result.isWordByWord = LrcParser.parse(raw).any { it.isWordByWord && it.words.isNotEmpty() }
        }
        else -> result.setPlainLyrics(raw)
    }
    return result.takeIf { it.hasLyrics() }
}

private fun trackSearchUrl(base: String, track: TrackMetadata): HttpUrl =
    base.toHttpUrl().newBuilder()
        .addQueryParameter("s", cleanLyricTitle(track.cleanedTitle))
        .addQueryParameter("a", track.cleanedArtist)
        .apply {
            val seconds = track.durationMs / 1000L
            if (seconds > 0) addQueryParameter("d", seconds.toString())
            if (track.album.isNotBlank()) addQueryParameter("al", track.album)
        }
        .build()

/** Apple Music's timed TTML catalogue served through the Better Lyrics API. */
class BetterLyricsAppleProvider(client: OkHttpClient) : LyricsProvider {
    private val http = lyricsHttp(client)

    override fun getName(): String = "Apple Music (Better Lyrics)"
    override fun getPriority(): Int = 2

    override fun search(track: TrackMetadata): LyricsResult? {
        val endpoints = listOf(
            "https://api.betterlyrics.org/getLyrics",
            "https://lyrics-api.boidu.dev/getLyrics"
        )
        for (endpoint in endpoints) {
            val response = lyricsJson(http, trackSearchUrl(endpoint, track))
            val ttml = response.text("ttml")
                ?: response.text("lyrics")?.takeIf { it.contains("<tt", true) }
                ?: response.text("data")?.takeIf { it.contains("<tt", true) }
            val result = lyricResult(getName(), track, ttml)
            if (result != null) return result
        }
        return null
    }
}

/** QQ Music's QRC karaoke timing, exposed by the Better Lyrics QQ endpoint. */
class QqMusicLyricsProvider(client: OkHttpClient) : LyricsProvider {
    private val http = lyricsHttp(client)

    override fun getName(): String = "QQ Music"
    override fun getPriority(): Int = 3

    override fun search(track: TrackMetadata): LyricsResult? {
        val response = lyricsJson(http, trackSearchUrl("https://api.betterlyrics.org/qq/getLyrics", track))
        val xml = response.text("lyrics") ?: response.text("data") ?: return null
        val content = Regex("""LyricContent\s*=\s*["'](.*?)["']""", RegexOption.DOT_MATCHES_ALL)
            .find(xml)?.groupValues?.get(1) ?: return null
        val decoded = decodeLyricsEntities(content)
        val raw = when {
            Regex("""\[\d+,\d+]""").containsMatchIn(decoded) ->
                AdvancedLyricsParser.wrapWords(AdvancedLyricsParser.WordKind.QRC, decoded)
            Regex("""\[\d{1,2}:\d{2}""").containsMatchIn(decoded) -> decoded
            else -> return null
        }
        return lyricResult(getName(), track, raw)
    }
}

/** NetEase Music: YRC word timing where available, otherwise conventional LRC. */
class NetEaseLyricsProvider(client: OkHttpClient) : LyricsProvider {
    private val http = lyricsHttp(client)
    private val headers = mapOf("Referer" to "https://music.163.com/")

    override fun getName(): String = "NetEase Music"
    override fun getPriority(): Int = 4

    override fun search(track: TrackMetadata): LyricsResult? {
        val searchUrl = "https://music.163.com/api/search/get".toHttpUrl().newBuilder()
            .addQueryParameter("s", "${cleanLyricTitle(track.cleanedTitle)} ${track.cleanedArtist}")
            .addQueryParameter("type", "1")
            .addQueryParameter("limit", "10")
            .build()
        val songs = lyricsJson(http, searchUrl, headers).field("result").field("songs").asArrayOrNull()
            ?: return null
        val wantedTitle = lyricKey(cleanLyricTitle(track.cleanedTitle))
        val wantedArtist = lyricKey(track.cleanedArtist)
        val candidates = songs.mapNotNull { item ->
            val title = item.text("name") ?: return@mapNotNull null
            val artistArray = (item.field("artists") ?: item.field("ar")).asArrayOrNull()
            val artists = artistArray?.mapNotNull { it.text("name") }.orEmpty()
            val artistText = artists.joinToString(" ")
            val titleKey = lyricKey(title)
            val titleMatches = titleKey == wantedTitle ||
                titleKey.contains(wantedTitle) || wantedTitle.contains(titleKey)
            val artistKey = lyricKey(artistText)
            val artistMatches = wantedArtist.isBlank() || artistKey.isBlank() ||
                artistKey.contains(wantedArtist) || wantedArtist.contains(artistKey)
            val durationMs = item.asNumber("duration") ?: item.asNumber("dt") ?: 0.0
            val seconds = if (durationMs > 10000.0) durationMs / 1000.0 else durationMs
            if (titleMatches && artistMatches && durationFits(track, seconds)) {
                Triple(item, title, Triple(artistText, seconds, abs(track.durationMs / 1000.0 - seconds)))
            } else null
        }.sortedBy { it.third.third }

        for ((item, title, details) in candidates.take(5)) {
            val id = item.text("id") ?: continue
            val lyricUrl = "https://music.163.com/api/song/lyric".toHttpUrl().newBuilder()
                .addQueryParameter("id", id)
                .addQueryParameter("lv", "1")
                .addQueryParameter("kv", "1")
                .addQueryParameter("yv", "1")
                .addQueryParameter("tv", "-1")
                .build()
            val lyric = lyricsJson(http, lyricUrl, headers) ?: continue
            val yrc = lyric.field("yrc").text("lyric")
            if (!yrc.isNullOrBlank() && Regex("""\[\d+,\d+]\(""").containsMatchIn(yrc)) {
                val result = lyricResult(
                    getName(), track,
                    AdvancedLyricsParser.wrapWords(AdvancedLyricsParser.WordKind.YRC, yrc),
                    title, details.first, details.second
                )
                if (result != null) return result
            }
            val lrc = lyric.field("lrc").text("lyric")
            val result = lyricResult(getName(), track, lrc, title, details.first, details.second)
            if (result != null) return result
        }
        return null
    }
}

/** KuGou: decrypts the returned KRC file for word/syllable timing and falls back to LRC. */
class KuGouLyricsProvider(client: OkHttpClient) : LyricsProvider {
    private val http = lyricsHttp(client)

    override fun getName(): String = "KuGou"
    override fun getPriority(): Int = 4

    override fun search(track: TrackMetadata): LyricsResult? {
        val durationMs = track.durationMs.coerceAtLeast(0L).toString()
        val keywords = listOf(
            "${track.cleanedArtist} - ${cleanLyricTitle(track.cleanedTitle)}",
            "${cleanLyricTitle(track.cleanedTitle)} - ${track.cleanedArtist}"
        )
        val candidates = keywords.asSequence().mapNotNull { keyword ->
            val url = "https://lyrics.kugou.com/search".toHttpUrl().newBuilder()
                .addQueryParameter("ver", "1")
                .addQueryParameter("man", "yes")
                .addQueryParameter("client", "pc")
                .addQueryParameter("keyword", keyword)
                .addQueryParameter("duration", durationMs)
                .addQueryParameter("hash", "")
                .build()
            lyricsJson(http, url)?.field("candidates").asArrayOrNull()
        }.firstOrNull { it.size() > 0 } ?: return null

        val sorted = candidates.filter { candidate ->
            val millis = candidate.asNumber("duration") ?: 0.0
            durationFits(track, if (millis > 10000.0) millis / 1000.0 else millis)
        }.sortedBy { candidate ->
            abs((candidate.asNumber("duration") ?: 0.0) - track.durationMs.toDouble())
        }

        for (candidate in sorted.take(5)) {
            val id = candidate.text("id") ?: continue
            val accessKey = candidate.text("accesskey") ?: continue
            fun download(format: String): HttpUrl = "https://lyrics.kugou.com/download".toHttpUrl().newBuilder()
                .addQueryParameter("fmt", format)
                .addQueryParameter("charset", "utf8")
                .addQueryParameter("client", "pc")
                .addQueryParameter("ver", "1")
                .addQueryParameter("id", id)
                .addQueryParameter("accesskey", accessKey)
                .build()

            val encodedKrc = lyricsJson(http, download("krc")).text("content")
            val decodedKrc = encodedKrc?.let(::decodeKrc)
            if (!decodedKrc.isNullOrBlank() && Regex("""\[\d+,\d+]<""").containsMatchIn(decodedKrc)) {
                val result = lyricResult(
                    getName(), track,
                    AdvancedLyricsParser.wrapWords(AdvancedLyricsParser.WordKind.KRC, decodedKrc),
                    matchedTitle = track.cleanedTitle,
                    matchedArtist = track.cleanedArtist
                )
                if (result != null) return result
            }

            val encodedLrc = lyricsJson(http, download("lrc")).text("content") ?: continue
            val lrc = try {
                String(Base64.decode(encodedLrc, Base64.DEFAULT), Charsets.UTF_8)
            } catch (_: Exception) { null }
            val result = lyricResult(getName(), track, lrc)
            if (result != null) return result
        }
        return null
    }

    private fun decodeKrc(encoded: String): String? {
        return try {
            val bytes = Base64.decode(encoded, Base64.DEFAULT)
            if (bytes.size < 5 || String(bytes, 0, 4, Charsets.US_ASCII) != "krc1") {
                null
            } else {
                val key = intArrayOf(64, 71, 97, 119, 94, 50, 116, 71, 81, 54, 49, 45, 206, 210, 110, 105)
                val xored = ByteArray(bytes.size - 4) { index ->
                    (bytes[index + 4].toInt() xor key[index % key.size]).toByte()
                }
                InflaterInputStream(ByteArrayInputStream(xored)).bufferedReader(Charsets.UTF_8).use { it.readText() }
            }
        } catch (_: Exception) {
            null
        }
    }
}

/** LyricsPlus returns syllable-level JSON and includes background-vocal flags for karaoke rendering. */
class LyricsPlusProvider(client: OkHttpClient) : LyricsProvider {
    private val http = lyricsHttp(client)
    private val mirrors = listOf(
        "https://lyricsplus.binimum.org",
        "https://lyricsplus.prjktla.workers.dev",
        "https://lyricsplus.prjktla.my.id",
        "https://lyricsplus.atomix.one",
        "https://lyricsplus-seven.vercel.app",
        "https://lyrics-plus-backend.vercel.app"
    )
    @Volatile private var workingMirror: String? = null

    override fun getName(): String = "LyricsPlus"
    override fun getPriority(): Int = 3

    override fun search(track: TrackMetadata): LyricsResult? {
        val preferred = workingMirror
        val ordered = if (preferred == null) mirrors else listOf(preferred) + mirrors.filter { it != preferred }
        for (host in ordered) {
            val url = host.trimEnd('/').plus("/v2/lyrics/get").toHttpUrl().newBuilder()
                .addQueryParameter("title", cleanLyricTitle(track.cleanedTitle))
                .addQueryParameter("artist", track.cleanedArtist)
                .apply {
                    val seconds = track.durationMs / 1000L
                    if (seconds > 0L) addQueryParameter("duration", seconds.toString())
                    if (track.album.isNotBlank()) addQueryParameter("album", track.album)
                }.build()
            val body = lyricsRequest(http, url) ?: continue
            val parsed = AdvancedLyricsParser.parse(AdvancedLyricsParser.wrapLyricsPlus(body)).orEmpty()
            if (parsed.isEmpty()) continue
            workingMirror = host
            val result = lyricResult(getName(), track, AdvancedLyricsParser.wrapLyricsPlus(body))
            if (result != null) return result
        }
        return null
    }
}

/** A second Apple Music timed-lyrics catalogue; useful when Better Lyrics has no match. */
class BiniLyricsProvider(client: OkHttpClient) : LyricsProvider {
    private val http = lyricsHttp(client)

    override fun getName(): String = "Bini"
    override fun getPriority(): Int = 5

    override fun search(track: TrackMetadata): LyricsResult? {
        fun query(includeDuration: Boolean): HttpUrl =
            "https://lyrics-api.binimum.org/".toHttpUrl().newBuilder()
                .addQueryParameter("track", cleanLyricTitle(track.cleanedTitle))
                .addQueryParameter("artist", track.cleanedArtist)
                .apply {
                    val seconds = track.durationMs / 1000L
                    if (includeDuration && seconds > 0L) addQueryParameter("duration", seconds.toString())
                }.build()

        val first = lyricsJson(http, query(true)).field("results").asArrayOrNull()
        val results = if (first == null || first.size() == 0) {
            lyricsJson(http, query(false)).field("results").asArrayOrNull()
        } else first
        if (results == null || results.size() == 0) return null
        val titleKey = lyricKey(cleanLyricTitle(track.cleanedTitle))
        val candidates = results.filter { item ->
            val candidateTitle = item.text("track_name")
            val seconds = item.asNumber("duration")
            durationFits(track, seconds) &&
                (candidateTitle.isNullOrBlank() || lyricKey(candidateTitle).let { it == titleKey || it.startsWith(titleKey) })
        }.sortedWith(compareBy<JsonElement>(
            { when (it.text("timing_type")) { "word" -> 0; "line" -> 1; else -> 2 } },
            { abs((it.asNumber("duration") ?: 0.0) - track.durationMs / 1000.0) }
        ))
        for (candidate in candidates) {
            val url = candidate.text("lyricsUrl")?.takeIf { it.startsWith("https://") } ?: continue
            val ttml = try { lyricsRequest(http, url.toHttpUrl()) } catch (_: Exception) { null }
            val result = lyricResult(getName(), track, ttml)
            if (result != null) return result
        }
        return null
    }
}

/** Community-submitted lyrics from Unison; response formats may be TTML, LRC, or plain text. */
class UnisonLyricsProvider(client: OkHttpClient) : LyricsProvider {
    // Unison lookups may need a metadata-search fallback after a stale video ID or
    // an exact title/artist miss; allow the service a little more time than generic APIs.
    private val http = client.newBuilder()
        .callTimeout(15, TimeUnit.SECONDS)
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    override fun getName(): String = "Unison"
    override fun getPriority(): Int = 6

    override fun search(track: TrackMetadata): LyricsResult? {
        // Prefer the active video ID, but never let a stale/mismatched ID block the
        // metadata catalogue lookup.
        if (!track.videoId.isNullOrBlank()) {
            fetchByVideoId(track.videoId!!, track)?.let { return it }
        }

        fetchByMetadata(track)?.let { return it }
        return searchMetadataIndex(track)
    }

    private fun fetchByVideoId(videoId: String, track: TrackMetadata): LyricsResult? {
        val url = "https://unison.betterlyrics.org/lyrics".toHttpUrl().newBuilder()
            .addQueryParameter("v", videoId)
            .build()
        return parseUnisonResponse(lyricsJson(http, url), track)
    }

    private fun fetchByMetadata(track: TrackMetadata): LyricsResult? {
        val url = "https://unison.betterlyrics.org/lyrics".toHttpUrl().newBuilder()
            .addQueryParameter("song", cleanLyricTitle(track.cleanedTitle))
            .addQueryParameter("artist", track.cleanedArtist)
            .apply {
                if (track.album.isNotBlank()) addQueryParameter("album", track.album)
                val seconds = track.durationMs / 1000L
                if (seconds > 0L) addQueryParameter("duration", seconds.toString())
            }
            .build()
        return parseUnisonResponse(lyricsJson(http, url), track)
    }

    private fun searchMetadataIndex(track: TrackMetadata): LyricsResult? {
        val url = "https://unison.betterlyrics.org/lyrics/search".toHttpUrl().newBuilder()
            .addQueryParameter("song", cleanLyricTitle(track.cleanedTitle))
            .addQueryParameter("artist", track.cleanedArtist)
            .apply {
                if (track.album.isNotBlank()) addQueryParameter("album", track.album)
                val seconds = track.durationMs / 1000L
                if (seconds > 0L) addQueryParameter("duration", seconds.toString())
                addQueryParameter("limit", "5")
            }
            .build()
        val response = lyricsJson(http, url) ?: return null
        if (response.text("success")?.equals("true", true) != true) {
            Log.d("UnisonLyrics", "Search had no candidates: ${response.text("code") ?: response.text("error") ?: "unknown"}")
            return null
        }

        val candidates = response.field("data").asArrayOrNull()
            ?: response.field("results").asArrayOrNull()
            ?: return null

        for (candidate in candidates) {
            val entry = candidate.asObjectOrNull() ?: continue
            // Some deployments include the full lyric body in search; others return
            // summaries and require a second lookup by video ID or entry ID.
            parseUnisonData(entry, track)?.let { return it }

            val videoId = entry.text("videoId")?.takeIf { it.isNotBlank() }
            if (videoId != null) {
                fetchByVideoId(videoId, track)?.let { return it }
            }

            val id = entry.text("id")?.takeIf { it.isNotBlank() }
            if (id != null) {
                val byIdUrl = "https://unison.betterlyrics.org/lyrics".toHttpUrl()
                    .newBuilder()
                    .addPathSegment(id)
                    .build()
                parseUnisonResponse(lyricsJson(http, byIdUrl), track)?.let { return it }
            }
        }
        return null
    }

    private fun parseUnisonResponse(
        response: JsonElement?,
        track: TrackMetadata
    ): LyricsResult? {
        if (response == null) return null
        val success = response.text("success")
        if (success != null && !success.equals("true", true)) {
            Log.d("UnisonLyrics", "Lookup miss: ${response.text("code") ?: response.text("error") ?: "unknown"}")
            return null
        }
        val data = response.field("data").asObjectOrNull()
            ?: response.asObjectOrNull()?.takeIf { it.has("lyrics") }
            ?: return null
        return parseUnisonData(data, track)
    }

    private fun parseUnisonData(data: JsonObject, track: TrackMetadata): LyricsResult? {
        val raw = data.text("lyrics") ?: data.text("content") ?: return null
        val videoId = data.text("videoId")?.takeIf { it.isNotBlank() } ?: track.videoId
        val result = lyricResult(
            getName(),
            track,
            raw,
            matchedTitle = data.text("song") ?: track.cleanedTitle,
            matchedArtist = data.text("artist") ?: track.cleanedArtist,
            durationSeconds = data.asNumber("duration")
        ) ?: return null

        // Unison asks integrating apps to credit both the database and the submitter.
        if (!videoId.isNullOrBlank() && videoId.matches(Regex("""[A-Za-z0-9_-]+"""))) {
            result.sourceCreditText = "Lyrics from Unison"
            result.sourceCreditUrl = "https://unison.betterlyrics.org/song/$videoId"
        }
        val submitter = data.field("submitter")
        val displayName = submitter.text("displayName")?.takeIf { it.isNotBlank() }
        val keyId = submitter.text("keyId")?.takeIf { it.isNotBlank() }
        if (displayName != null && keyId != null) {
            result.sourceSubmitterText = "Submitted by $displayName"
            result.sourceSubmitterUrl =
                "https://unison.betterlyrics.org/curator/${android.net.Uri.encode(keyId)}"
        }
        return result
    }
}

/** Geniusius provides useful uncensored/plain lyrics when a timed source is unavailable. */
class GeniusLyricsProvider(client: OkHttpClient) : LyricsProvider {
    private val http = lyricsHttp(client)

    override fun getName(): String = "Genius"
    override fun getPriority(): Int = 8

    override fun search(track: TrackMetadata): LyricsResult? {
        val bareTitle = cleanLyricTitle(track.cleanedTitle)
            .replace(Regex("""\s*[\[(](?:feat|ft|with)\.?\s[^)\]]*[)\]]""", RegexOption.IGNORE_CASE), "")
            .trim()
        val searchUrl = "https://genius.com/api/search/multi".toHttpUrl().newBuilder()
            .addQueryParameter("q", "${bareTitle} ${track.cleanedArtist}")
            .build()
        val sections = lyricsJson(http, searchUrl).field("response").field("sections").asArrayOrNull()
            ?: return null
        val hits = sections.firstOrNull { it.text("type") == "song" }
            .field("hits").asArrayOrNull() ?: return null
        for (hit in hits) {
            val result = hit.field("result") ?: continue
            val title = result.text("title") ?: continue
            val artist = result.text("primary_artist_names")
                ?: result.field("primary_artist").text("name").orEmpty()
            if (!matchingTrack(title, artist, track)) continue
            val pageUrl = result.text("url")
                ?.takeIf { it.startsWith("https://genius.com/") } ?: continue
            val html = lyricsRequest(http, pageUrl.toHttpUrl()) ?: continue
            val lyrics = extractGeniusLyrics(html)
            val parsed = lyricResult(getName(), track, lyrics, title, artist)
            if (parsed != null) return parsed
        }
        return null
    }

    private fun extractGeniusLyrics(html: String): String? {
        val startRegex = Regex("""(?is)<div\b(?=[^>]*data-lyrics-container\s*=\s*["']true["'])[^>]*>""")
        val divTags = Regex("""(?is)</?div\b[^>]*>""")
        val regions = mutableListOf<String>()
        for (opening in startRegex.findAll(html)) {
            var depth = 1
            var endStart = -1
            val after = opening.range.last + 1
            for (tag in divTags.findAll(html, after)) {
                if (tag.value.startsWith("</", true)) {
                    depth--
                    if (depth == 0) {
                        endStart = tag.range.first
                        break
                    }
                } else if (!tag.value.endsWith("/>")) {
                    depth++
                }
            }
            if (endStart >= after) regions += html.substring(after, endStart)
        }
        if (regions.isEmpty()) return null
        val cleanHtml = regions.joinToString("\n")
            .replace(Regex("""(?is)<script\b.*?</script>"""), "")
            .replace(Regex("""(?is)<style\b.*?</style>"""), "")
            .replace(Regex("""(?i)<br\s*/?>"""), "\n")
            .replace(Regex("""(?i)</(?:p|li|div|h[1-6])\s*>"""), "\n")
            .replace(Regex("""<[^>]+>"""), "")
        @Suppress("DEPRECATION")
        val decoded = Html.fromHtml(cleanHtml).toString()
        return decoded
            .replace('\u00a0', ' ')
            .lines().joinToString("\n") { it.trim() }
            .replace(Regex("""\n{3,}"""), "\n\n")
            .trim()
            .takeIf { it.isNotBlank() }
    }
}

private fun decodeLyricsEntities(value: String): String {
    @Suppress("DEPRECATION")
    return Html.fromHtml(value).toString()
}
