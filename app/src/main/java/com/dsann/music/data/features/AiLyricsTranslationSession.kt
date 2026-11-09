package com.dsann.music.data.features

import android.content.Context
import com.dsann.music.data.lyrics.LyricLine
import com.dsann.music.util.EncryptedFeatureSecretStore
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.MessageDigest

data class CachedLyricsTranslation(
    val cacheKey: String,
    val songId: Long,
    val translatedText: String,
    val provider: String,
    val targetLanguage: String,
    val createdAtMs: Long
)

/** Shared song-scoped translation cache for compact landscape and full-screen lyrics. */
object AiLyricsTranslationSession {
    private const val MAX_CACHED_TRANSLATIONS = 32
    private val _entries = MutableStateFlow<Map<String, CachedLyricsTranslation>>(emptyMap())
    val entries: StateFlow<Map<String, CachedLyricsTranslation>> = _entries.asStateFlow()
    private val requestLock = Mutex()

    fun cacheKey(songId: Long, source: String, settings: AiLyricsTranslationSettings): String {
        val configuration = listOf(
            songId.toString(), source, settings.provider, settings.model,
            settings.customEndpoint, settings.targetLanguage, settings.mode,
            settings.deeplFormality, settings.systemPrompt
        ).joinToString("\u001f")
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(configuration.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { byte -> "%02x".format(byte) }
    }

    suspend fun translate(context: Context, songId: Long, source: String): CachedLyricsTranslation {
        val appContext = context.applicationContext
        val settingsStore = AiLyricsTranslationSettingsStore(appContext)
        val secretStore = EncryptedFeatureSecretStore(appContext)
        val settings = settingsStore.load()
        check(settings.enabled) { "Enable AI lyrics translation in Settings → Playback." }

        val normalizedSource = source.replace("\r\n", "\n").replace('\r', '\n')
        require(normalizedSource.isNotBlank()) { "Load lyrics before requesting a translation." }
        val key = cacheKey(songId, normalizedSource, settings)

        return requestLock.withLock {
            _entries.value[key]?.let { return@withLock it }
            val apiKey = secretStore.read(
                AiLyricsTranslationCatalog.secretNameFor(settings.provider)
            ).orEmpty()
            require(apiKey.isNotBlank()) {
                "Add the API key for ${settings.provider} in Settings → Playback."
            }

            val result = AiLyricsTranslationService.translate(
                lyrics = normalizedSource,
                settings = settings,
                apiKey = apiKey
            )
            val entry = CachedLyricsTranslation(
                cacheKey = key,
                songId = songId,
                translatedText = result,
                provider = settings.provider,
                targetLanguage = settings.targetLanguage,
                createdAtMs = System.currentTimeMillis()
            )
            synchronized(_entries) {
                val next = LinkedHashMap(_entries.value)
                next[key] = entry
                while (next.size > MAX_CACHED_TRANSLATIONS) {
                    next.remove(next.keys.firstOrNull() ?: break)
                }
                _entries.value = next
            }
            entry
        }
    }

    fun mapToSyncedLines(originalLines: List<LyricLine>?, translatedText: String): List<LyricLine>? {
        if (originalLines.isNullOrEmpty()) return null
        val translatedLines = translatedText.replace("\r\n", "\n")
            .replace('\r', '\n')
            .split('\n')
        return originalLines.mapIndexed { index, line ->
            val fallback = line.content.ifBlank {
                line.words.joinToString("") { word -> word.text }
            }
            val translated = translatedLines.getOrNull(index)?.takeIf { it.isNotBlank() } ?: fallback
            line.copy(
                content = translated,
                isWordByWord = false,
                words = emptyList(),
                backgroundContent = null,
                backgroundWords = emptyList()
            )
        }
    }
}
