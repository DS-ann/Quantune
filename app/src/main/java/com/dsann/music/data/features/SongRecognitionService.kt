package com.dsann.music.data.features

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.SystemClock
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max

data class RecognizedSong(
    val title: String,
    val artist: String,
    val album: String?,
    val releaseDate: String?,
    val songUrl: String?,
    val coverArtUrl: String? = null,
    val shazamUrl: String? = null,
    val appleMusicUrl: String? = null,
    val spotifyUrl: String? = null,
    val genre: String? = null,
    val label: String? = null,
    val isrc: String? = null,
    val youtubeVideoId: String? = null
)

/**
 * Captures audio locally, builds a compact audio fingerprint, and sends only
 * that fingerprint to the matching service. The microphone recording is not uploaded.
 */
object SongRecognitionService {
    private const val SAMPLE_RATE = 44_100
    private const val FINGERPRINT_RATE = 16_000
    private const val RECORDING_DURATION_MS = 12_000L
    private const val MIN_REQUEST_INTERVAL_MS = 1_000L
    private const val REQUEST_TIMEOUT_MS = 30_000
    private const val MAX_ATTEMPTS = 3
    private const val CACHE_DURATION_MS = 5 * 60 * 1000L

    private val requestMutex = Mutex()
    private val cache = ConcurrentHashMap<String, CachedMatch>()
    @Volatile private var lastRequestAt = 0L

    suspend fun recognize(
        context: Context,
        onStage: (String) -> Unit = {}
    ): RecognizedSong {
        check(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        ) { "Microphone permission is required to recognize a song." }

        onStage("Listening for 12 seconds…")
        val recordedPcm = captureAudio()

        onStage("Creating audio fingerprint…")
        val signature = withContext(Dispatchers.Default) {
            val mono16k = resamplePcm(recordedPcm, SAMPLE_RATE, FINGERPRINT_RATE)
            ShazamCompatibleFingerprint.create(mono16k)
        }
        val durationMs = (recordedPcm.size / 2L) * 1000L / SAMPLE_RATE

        onStage("Searching for a match…")
        return requestMatch(signature, durationMs)
    }

    @SuppressLint("MissingPermission")
    private suspend fun captureAudio(): ByteArray = withContext(Dispatchers.IO) {
        val minimumBuffer = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        require(minimumBuffer > 0) {
            "This device cannot record 44.1 kHz mono audio."
        }

        val bufferSize = max(minimumBuffer, 8_192)
        val recorder = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize
        )
        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            recorder.release()
            throw IllegalStateException("The microphone recorder could not be initialized.")
        }

        val coroutine = currentCoroutineContext()
        val output = ByteArrayOutputStream(SAMPLE_RATE * 2 * 12)
        val buffer = ByteArray(bufferSize.coerceAtMost(32_768))
        try {
            recorder.startRecording()
            val startedAt = SystemClock.elapsedRealtime()
            while (SystemClock.elapsedRealtime() - startedAt < RECORDING_DURATION_MS) {
                coroutine.ensureActive()
                val count = recorder.read(buffer, 0, buffer.size)
                if (count > 0) {
                    output.write(buffer, 0, count)
                } else if (count < 0) {
                    throw IllegalStateException("The microphone stopped while recording (code $count).")
                }
            }
        } finally {
            try {
                if (recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) recorder.stop()
            } catch (_: Exception) {
            }
            recorder.release()
        }

        val captured = output.toByteArray()
        require(captured.size >= SAMPLE_RATE * 2 * 6) {
            "Not enough audio was captured. Please try again."
        }
        captured
    }

    private fun resamplePcm(inputBytes: ByteArray, inputRate: Int, outputRate: Int): ByteArray {
        require(inputBytes.size >= 2 && inputBytes.size % 2 == 0) {
            "The microphone returned an invalid PCM sample."
        }

        val source = ShortArray(inputBytes.size / 2)
        ByteBuffer.wrap(inputBytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(source)
        val ratio = outputRate.toDouble() / inputRate
        val outputCount = (source.size * ratio).toInt()
        val target = ByteArray(outputCount * 2)
        val targetBuffer = ByteBuffer.wrap(target).order(ByteOrder.LITTLE_ENDIAN)

        for (index in 0 until outputCount) {
            val position = index / ratio
            val left = position.toInt().coerceIn(0, source.lastIndex)
            val right = (left + 1).coerceAtMost(source.lastIndex)
            val fraction = position - left
            val sample = (
                source[left] * (1.0 - fraction) + source[right] * fraction
            ).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
            targetBuffer.putShort(sample.toShort())
        }
        return target
    }

    private suspend fun requestMatch(signature: String, durationMs: Long): RecognizedSong =
        withContext(Dispatchers.IO) {
            val cacheKey = sha256(signature)
            readCache(cacheKey)?.let { return@withContext it }

            requestMutex.lock()
            try {
                readCache(cacheKey)?.let { return@withContext it }

                var lastError: Exception? = null
                for (attempt in 0 until MAX_ATTEMPTS) {
                    val waitMs = MIN_REQUEST_INTERVAL_MS - (System.currentTimeMillis() - lastRequestAt)
                    if (waitMs > 0) delay(waitMs)
                    lastRequestAt = System.currentTimeMillis()

                    try {
                        val result = postFingerprint(signature, durationMs)
                        cache[cacheKey] = CachedMatch(System.currentTimeMillis(), result)
                        return@withContext result
                    } catch (error: Exception) {
                        lastError = error
                        val limited = error.message?.contains("429") == true ||
                            error.message?.contains("rate limit", ignoreCase = true) == true
                        if (!limited || attempt == MAX_ATTEMPTS - 1) throw error
                        delay(2_000L shl attempt)
                    }
                }
                throw lastError ?: IllegalStateException("Recognition failed. Please try again.")
            } finally {
                requestMutex.unlock()
            }
        }

    private fun postFingerprint(signature: String, durationMs: Long): RecognizedSong {
        val now = System.currentTimeMillis() / 1000L
        val firstTag = UUID.randomUUID().toString().uppercase(Locale.US)
        val secondTag = UUID.randomUUID().toString()
        val endpoint = (
            "https://amp.shazam.com/discovery/v5/en/US/android/-/tag/" +
                "$firstTag/$secondTag" +
                "?sync=true&webv3=true&sampling=true&connected=" +
                "&shazamapiversion=v3&sharehub=true&video=v3"
        )

        // Neutral coordinates avoid requesting or transmitting the device's location.
        val geo = JSONObject()
            .put("altitude", 0.0)
            .put("latitude", 0.0)
            .put("longitude", 0.0)
        val signatureData = JSONObject()
            .put("samplems", durationMs)
            .put("timestamp", now)
            .put("uri", signature)
        val body = JSONObject()
            .put("geolocation", geo)
            .put("signature", signatureData)
            .put("timestamp", now)
            .put("timezone", "UTC")

        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15_000
            readTimeout = REQUEST_TIMEOUT_MS
            doOutput = true
            useCaches = false
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Content-Language", "en_US")
            setRequestProperty(
                "User-Agent",
                "Dalvik/2.1.0 (Linux; U; Android 6.0; Mobile Build/Marshmallow)"
            )
        }

        try {
            connection.outputStream.use { stream ->
                stream.write(body.toString().toByteArray(Charsets.UTF_8))
                stream.flush()
            }

            val status = connection.responseCode
            val responseStream = if (status in 200..299) connection.inputStream else connection.errorStream
            val responseText = responseStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()

            when {
                status == 404 -> throw IllegalStateException("No match found. Try again with clearer audio.")
                status == 429 -> throw IllegalStateException("Recognition rate limit reached (HTTP 429).")
                status !in 200..299 -> throw IllegalStateException(
                    if (status >= 500) "Recognition service is temporarily unavailable."
                    else "Recognition failed (HTTP $status)."
                )
            }
            return parseMatch(JSONObject(responseText))
        } finally {
            connection.disconnect()
        }
    }

    private fun parseMatch(root: JSONObject): RecognizedSong {
        val track = root.optJSONObject("track")
            ?: throw IllegalStateException("No match found. Try recording closer to the music source.")

        val title = track.text("title")
            ?: throw IllegalStateException("The match did not include a song title.")
        val artist = track.text("subtitle") ?: "Unknown artist"
        val sections = track.optJSONArray("sections")
        var album: String? = null
        var releaseDate: String? = null
        var label: String? = null

        sections.forEachObject { section ->
            if (section.text("type") == "SONG") {
                section.optJSONArray("metadata").forEachObject { metadata ->
                    when (metadata.text("title")?.lowercase(Locale.US)) {
                        "album" -> album = metadata.text("text")
                        "released" -> releaseDate = metadata.text("text")
                        "label" -> label = metadata.text("text")
                    }
                }
            }
        }

        val hub = track.optJSONObject("hub")
        val options = hub?.optJSONArray("options")
        var appleUrl: String? = null
        var videoUrl: String? = null
        options.forEachObject { option ->
            val actionUri = option.optJSONArray("actions").firstObject()?.text("uri")
            if (appleUrl == null &&
                option.text("providername")?.contains("apple", true) == true
            ) appleUrl = actionUri
            if (videoUrl == null &&
                option.text("type")?.contains("video", true) == true
            ) videoUrl = actionUri
        }

        var spotifyUrl: String? = null
        hub?.optJSONArray("providers").forEachObject { provider ->
            if (spotifyUrl == null &&
                provider.text("caption")?.contains("spotify", true) == true
            ) {
                spotifyUrl = provider.optJSONArray("actions").firstObject()?.text("uri")
            }
        }

        val images = track.optJSONObject("images")
        val image = images?.text("coverarthq") ?: images?.text("coverart")
        val cover = image?.replace("{w}", "600")?.replace("{h}", "600")
        val shazamUrl = track.text("url")
        val videoId = videoUrl?.let { uri ->
            uri.substringAfterLast("v=", "").takeIf { it.isNotBlank() }
                ?: uri.substringAfterLast("/", "").takeIf { it.length == 11 }
        }

        return RecognizedSong(
            title = title,
            artist = artist,
            album = album,
            releaseDate = releaseDate,
            songUrl = appleUrl ?: shazamUrl ?: spotifyUrl,
            coverArtUrl = cover,
            shazamUrl = shazamUrl,
            appleMusicUrl = appleUrl,
            spotifyUrl = spotifyUrl,
            genre = track.optJSONObject("genres")?.text("primary"),
            label = label,
            isrc = track.text("isrc"),
            youtubeVideoId = videoId
        )
    }

    private fun readCache(key: String): RecognizedSong? {
        val entry = cache[key] ?: return null
        if (System.currentTimeMillis() - entry.savedAt > CACHE_DURATION_MS) {
            cache.remove(key)
            return null
        }
        return entry.song
    }

    private fun sha256(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { byte -> "%02x".format(byte) }
    }

    private data class CachedMatch(val savedAt: Long, val song: RecognizedSong)

    private fun JSONObject.text(key: String): String? {
        val value = optString(key, "").trim()
        return value.takeIf { it.isNotEmpty() && !it.equals("null", true) }
    }

    private inline fun JSONArray?.forEachObject(block: (JSONObject) -> Unit) {
        if (this == null) return
        for (index in 0 until length()) {
            optJSONObject(index)?.let(block)
        }
    }

    private fun JSONArray?.firstObject(): JSONObject? =
        if (this == null || length() == 0) null else optJSONObject(0)
}
