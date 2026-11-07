package com.dsann.music.data.innertube

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicLong

/** A concrete stream the source actually offers; choosing it never rewrites the saved format setting. */
data class AvailableAudioFormat(
    val id: String,
    val url: String,
    val mimeType: String,
    val codec: String?,
    val bitrateKbps: Int,
    val contentLength: Long?,
    val formatLabel: String
)

enum class AudioFormatPurpose(val label: String) {
    PLAYBACK("playback"),
    DOWNLOAD("download")
}

class AudioFormatChoiceRequest internal constructor(
    val id: Long,
    val songTitle: String,
    val purpose: AudioFormatPurpose,
    val requestedFormat: String,
    val targetBitrateKbps: Int,
    val options: List<AvailableAudioFormat>,
    internal val response: CompletableDeferred<AvailableAudioFormat?>
)

class AudioFormatSelectionCancelledException :
    Exception("Audio format selection was cancelled by the user")

class RequestedAudioFormatUnavailableException(
    val requestedFormat: String
) : Exception("Requested audio format '$requestedFormat' is not available")

/**
 * Serializes format prompts across playback and downloads. The requesting coroutine waits until
 * the visible player UI returns a concrete, available stream or the user cancels the operation.
 */
object AudioFormatSelectionCoordinator {
    private val requestMutex = Mutex()
    private val requestIds = AtomicLong(0L)
    private val _pending = MutableStateFlow<AudioFormatChoiceRequest?>(null)
    val pending: StateFlow<AudioFormatChoiceRequest?> = _pending.asStateFlow()

    suspend fun choose(
        songTitle: String,
        purpose: AudioFormatPurpose,
        requestedFormat: String,
        targetBitrateKbps: Int,
        options: List<AvailableAudioFormat>
    ): AvailableAudioFormat {
        val uniqueOptions = options.distinctBy { it.id }
        if (uniqueOptions.isEmpty()) throw RequestedAudioFormatUnavailableException(requestedFormat)

        requestMutex.lock()
        var request: AudioFormatChoiceRequest? = null
        try {
            val created = AudioFormatChoiceRequest(
                id = requestIds.incrementAndGet(),
                songTitle = songTitle.ifBlank { "this song" },
                purpose = purpose,
                requestedFormat = requestedFormat,
                targetBitrateKbps = targetBitrateKbps,
                options = uniqueOptions,
                response = CompletableDeferred()
            )
            request = created
            _pending.value = created
            return created.response.await() ?: throw AudioFormatSelectionCancelledException()
        } finally {
            request?.let { active ->
                if (_pending.value?.id == active.id) _pending.value = null
            }
            requestMutex.unlock()
        }
    }

    fun select(request: AudioFormatChoiceRequest, option: AvailableAudioFormat) {
        val current = _pending.value ?: return
        if (current.id != request.id || current.options.none { it.id == option.id }) return
        current.response.complete(option)
    }

    fun cancel(request: AudioFormatChoiceRequest) {
        val current = _pending.value ?: return
        if (current.id != request.id) return
        current.response.complete(null)
    }
}

fun audioFormatDisplayLabel(mimeType: String, codec: String? = null): String {
    val mime = mimeType.substringBefore(';').trim().lowercase()
    val codecName = (codec ?: Regex("""codecs?=["']?([^;"']+)""", RegexOption.IGNORE_CASE)
        .find(mimeType)?.groupValues?.getOrNull(1)).orEmpty().lowercase()
    val container = when {
        mime.contains("mp4") -> "M4A"
        mime.contains("webm") -> "WebM"
        mime.contains("ogg") -> "Ogg"
        mime.contains("mpeg") || mime.contains("mp3") -> "MP3"
        mime.contains("aac") -> "AAC"
        mime.contains("flac") -> "FLAC"
        mime.contains("wav") || mime.contains("wave") -> "WAV"
        mime.isBlank() -> "Unknown audio"
        else -> mime.substringAfter('/').uppercase()
    }
    val codecLabel = when {
        codecName.contains("opus") -> "Opus"
        codecName.contains("vorbis") -> "Vorbis"
        codecName.contains("mp4a") || codecName.contains("aac") -> if (container == "M4A") "AAC" else "AAC"
        codecName.contains("mp3") -> "MP3"
        codecName.contains("flac") -> "FLAC"
        else -> ""
    }
    return if (codecLabel.isBlank() || container.equals(codecLabel, ignoreCase = true)) {
        container
    } else {
        "$container ($codecLabel)"
    }
}
