package com.dsann.music.data.features

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

/**
 * Multi-provider lyrics translation.
 *
 * Chat-completions providers share one request path. Claude and DeepL use
 * their native request formats. HttpURLConnection and org.json keep this
 * implementation compatible with Android API 23.
 */
object AiLyricsTranslationService {
    private const val MAX_SOURCE_LENGTH = 24_000
    private const val MAX_ATTEMPTS = 3
    private const val CONNECT_TIMEOUT_MS = 20_000
    private const val READ_TIMEOUT_MS = 90_000

    suspend fun translate(
        lyrics: String,
        settings: AiLyricsTranslationSettings,
        apiKey: String
    ): String = withContext(Dispatchers.IO) {
        // Preserve leading/trailing blank lines: each output line maps to the original
        // synchronized lyric line at the same index.
        val source = lyrics.replace("\r\n", "\n").replace('\r', '\n')
        require(source.isNotBlank()) { "There are no lyrics available to translate." }
        require(source.length <= MAX_SOURCE_LENGTH) {
            "These lyrics are too long for one request. Try a shorter lyric source."
        }
        require(settings.targetLanguage.isNotBlank()) { "Enter a target language." }
        if (settings.provider != "DeepL") {
            require(settings.model.isNotBlank()) { "Enter a model ID for the selected provider." }
        }
        require(apiKey.isNotBlank()) { "Save an API key for the selected provider first." }

        when (settings.provider) {
            "DeepL" -> translateWithDeepL(source, settings, apiKey)
            "Claude" -> translateWithClaude(source, settings, apiKey)
            else -> translateWithChatCompletions(source, settings, apiKey)
        }
    }

    private suspend fun translateWithChatCompletions(
        source: String,
        settings: AiLyricsTranslationSettings,
        apiKey: String
    ): String {
        val endpoint = validatedEndpoint(settings.endpoint())
        val lines = source.lines()
        val prompts = buildPrompts(source, settings)
        val body = JSONObject().apply {
            put("model", settings.model.trim())
            put("messages", JSONArray().apply {
                put(JSONObject().put("role", "system").put("content", prompts.first))
                put(JSONObject().put("role", "user").put("content", prompts.second))
            })
            val outputTokenLimit = (lines.size * 100).coerceIn(1_000, 8_000)
            if (endpoint.contains("api.openai.com", ignoreCase = true)) {
                // Current OpenAI Chat Completions uses max_completion_tokens; max_tokens
                // is deprecated and incompatible with reasoning-series model IDs.
                put("max_completion_tokens", outputTokenLimit)
            } else {
                put("temperature", 0.2)
                put("max_tokens", outputTokenLimit)
            }
        }
        val response = postJson(
            endpoint = endpoint,
            body = body,
            headers = mapOf("Authorization" to "Bearer " + apiKey.trim())
        )
        val choices = response.optJSONArray("choices")
            ?: throw IllegalStateException("The provider returned no completion choices.")
        val message = choices.optJSONObject(0)?.optJSONObject("message")
            ?: throw IllegalStateException("The provider returned an empty completion.")
        val content = extractTextContent(message.opt("content"))
        if (content.isBlank()) {
            throw IllegalStateException("The provider returned an empty translation.")
        }
        return parseTranslatedLines(content, lines.size).joinToString("\n")
    }

    private suspend fun translateWithClaude(
        source: String,
        settings: AiLyricsTranslationSettings,
        apiKey: String
    ): String {
        val endpoint = validatedEndpoint(settings.endpoint())
        val lines = source.lines()
        val prompts = buildPrompts(source, settings)
        val body = JSONObject().apply {
            put("model", settings.model.trim())
            put("max_tokens", (lines.size * 100).coerceIn(1_000, 8_000))
            put("system", prompts.first)
            put("messages", JSONArray().put(
                JSONObject()
                    .put("role", "user")
                    .put("content", prompts.second)
            ))
        }
        val response = postJson(
            endpoint = endpoint,
            body = body,
            headers = mapOf(
                "x-api-key" to apiKey.trim(),
                "anthropic-version" to "2023-06-01"
            )
        )
        val blocks = response.optJSONArray("content")
            ?: throw IllegalStateException("Claude returned no message content.")
        val text = StringBuilder()
        for (index in 0 until blocks.length()) {
            val block = blocks.optJSONObject(index) ?: continue
            if (block.optString("type") == "text") {
                text.append(block.optString("text"))
            }
        }
        if (text.isBlank()) throw IllegalStateException("Claude returned an empty translation.")
        return parseTranslatedLines(text.toString(), lines.size).joinToString("\n")
    }

    private suspend fun translateWithDeepL(
        source: String,
        settings: AiLyricsTranslationSettings,
        apiKey: String
    ): String {
        val lines = source.lines()
        val translated = MutableList(lines.size) { "" }
        val nonEmptyIndexes = lines.indices.filter { lines[it].isNotBlank() }
        if (nonEmptyIndexes.isEmpty()) {
            throw IllegalStateException("There are no non-empty lyric lines to translate.")
        }

        // DeepL accepts arrays of text segments, with a maximum of 50 per request.
        // Keep empty source lines locally and restore them at their original positions.
        val endpoint = if (apiKey.trim().endsWith(":fx")) {
            "https://api-free.deepl.com/v2/translate"
        } else {
            "https://api.deepl.com/v2/translate"
        }
        val languageCode = deepLLanguageCode(settings.targetLanguage)
        nonEmptyIndexes.chunked(50).forEach { chunk ->
            val body = JSONObject().apply {
                put("text", JSONArray().apply {
                    chunk.forEach { index -> put(lines[index]) }
                })
                put("target_lang", languageCode)
                put("preserve_formatting", true)
                if (settings.deeplFormality != "default") {
                    put("formality", settings.deeplFormality)
                }
            }
            val response = postJson(
                endpoint = endpoint,
                body = body,
                headers = mapOf("Authorization" to "DeepL-Auth-Key " + apiKey.trim())
            )
            val translations = response.optJSONArray("translations")
                ?: throw IllegalStateException("DeepL returned no translations.")
            if (translations.length() < chunk.size) {
                throw IllegalStateException("DeepL returned fewer translated segments than requested.")
            }
            chunk.forEachIndexed { segmentIndex, sourceIndex ->
                translated[sourceIndex] = translations.optJSONObject(segmentIndex)
                    ?.optString("text")
                    .orEmpty()
            }
        }
        return translated.joinToString("\n")
    }

    private fun buildPrompts(
        source: String,
        settings: AiLyricsTranslationSettings
    ): Pair<String, String> {
        val lineCount = source.lines().size
        val defaultSystemPrompt = (
            "You are a precise song-lyrics translation assistant. Return only a valid JSON object " +
                "with the shape {\"lines\":[\"line 1\",\"line 2\"]}. The lines array must contain " +
                "exactly " + lineCount + " string entries. Preserve empty lines as empty strings. " +
                "Do not add markdown fences, commentary, headings, or explanations."
            )
        val systemPrompt = settings.systemPrompt.trim()
            .takeIf { it.isNotBlank() }
            ?.replace("{lineCount}", lineCount.toString())
            ?: defaultSystemPrompt

        val task = if (settings.mode == "Transcribed") {
            "Transcribe the pronunciation of each line into the native writing system used for " +
                settings.targetLanguage + ". Preserve the original meaning and pronunciation; " +
                "do not translate the meaning. Keep each line separate."
        } else {
            "Translate every lyric line naturally into " + settings.targetLanguage +
                ". Preserve meaning, tone, names, repetitions, punctuation and poetic flow. " +
                "Keep each input line mapped to one output line."
        }
        val userPrompt = task +
            "\nReturn a JSON object with a \"lines\" array containing exactly " + lineCount +
            " strings. Preserve blank lines as empty strings.\n\nLyrics:\n" + source
        return Pair(systemPrompt, userPrompt)
    }

    private suspend fun postJson(
        endpoint: String,
        body: JSONObject,
        headers: Map<String, String>
    ): JSONObject {
        var lastError: Exception? = null
        for (attempt in 0 until MAX_ATTEMPTS) {
            try {
                val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = CONNECT_TIMEOUT_MS
                    readTimeout = READ_TIMEOUT_MS
                    doOutput = true
                    useCaches = false
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    setRequestProperty("Accept", "application/json")
                    setRequestProperty("User-Agent", "Quantune-Android")
                    headers.forEach { (name, value) -> setRequestProperty(name, value) }
                }
                val code: Int
                val responseText: String
                try {
                    connection.outputStream.use {
                        it.write(body.toString().toByteArray(Charsets.UTF_8))
                    }
                    code = connection.responseCode
                    val stream = if (code in 200..299) {
                        connection.inputStream
                    } else {
                        connection.errorStream
                    }
                    responseText = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
                } finally {
                    connection.disconnect()
                }

                if (code in 200..299) {
                    return try {
                        JSONObject(responseText)
                    } catch (_: Exception) {
                        throw IllegalStateException("The provider returned an unreadable response.")
                    }
                }

                val message = providerErrorMessage(responseText, code)
                val httpError = IllegalStateException(message)
                if (code != 429 && code < 500) throw httpError
                lastError = httpError
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (error is IllegalStateException &&
                    error.message?.startsWith("Translation failed (HTTP 4") == true &&
                    !error.message.orEmpty().contains("HTTP 429")
                ) {
                    throw error
                }
                lastError = error
            }

            if (attempt < MAX_ATTEMPTS - 1) {
                delay(1_000L * (attempt + 1))
            }
        }
        throw lastError ?: IllegalStateException("Translation failed after multiple attempts.")
    }

    private fun providerErrorMessage(responseText: String, code: Int): String {
        val detail = try {
            val root = JSONObject(responseText)
            val error = root.opt("error")
            when (error) {
                is JSONObject -> error.optString("message")
                    .ifBlank { error.optString("type") }
                is String -> error
                else -> root.optString("message")
            }
        } catch (_: Exception) {
            ""
        }
        return if (detail.isNotBlank()) {
            "Translation failed (HTTP " + code + "): " + detail
        } else {
            "Translation failed (HTTP " + code + "). Check the provider, model ID and API key."
        }
    }

    private fun extractTextContent(value: Any?): String {
        return when (value) {
            is String -> value
            is JSONArray -> {
                val result = StringBuilder()
                for (index in 0 until value.length()) {
                    val part = value.optJSONObject(index) ?: continue
                    result.append(part.optString("text"))
                }
                result.toString()
            }
            else -> ""
        }
    }

    private fun parseTranslatedLines(content: String, expectedCount: Int): List<String> {
        val fence = String(charArrayOf(96.toChar(), 96.toChar(), 96.toChar()))
        val cleaned = content
            .replace(fence + "json", "", ignoreCase = true)
            .replace(fence, "")
            .trim()

        val candidates = ArrayList<String>()
        candidates.add(cleaned)
        val objectStart = cleaned.indexOf('{')
        val objectEnd = cleaned.lastIndexOf('}')
        if (objectStart >= 0 && objectEnd > objectStart) {
            candidates.add(cleaned.substring(objectStart, objectEnd + 1))
        }
        val arrayStart = cleaned.indexOf('[')
        val arrayEnd = cleaned.lastIndexOf(']')
        if (arrayStart >= 0 && arrayEnd > arrayStart) {
            candidates.add(cleaned.substring(arrayStart, arrayEnd + 1))
        }

        var parsed: List<String>? = null
        for (candidate in candidates) {
            parsed = try {
                val objectValue = JSONObject(candidate)
                val lines = objectValue.optJSONArray("lines")
                    ?: objectValue.optJSONArray("translations")
                if (lines != null) jsonArrayToStrings(lines) else null
            } catch (_: Exception) {
                try {
                    jsonArrayToStrings(JSONArray(candidate))
                } catch (_: Exception) {
                    null
                }
            }
            if (parsed != null) break
        }

        if (parsed == null) {
            parsed = cleaned
                .lines()
                .map { line ->
                    line.trim()
                        .replace(Regex("^\\d+[.)]\\s*"), "")
                        .replace(Regex("^-\\s*"), "")
                }
                .takeIf { it.isNotEmpty() }
        }

        val result = parsed ?: throw IllegalStateException("Could not parse the translation response.")
        return result.take(expectedCount) + List((expectedCount - result.size).coerceAtLeast(0)) { "" }
    }

    private fun jsonArrayToStrings(array: JSONArray): List<String> {
        val result = ArrayList<String>(array.length())
        for (index in 0 until array.length()) {
            val value = array.opt(index)
            result.add(if (value == null || value == JSONObject.NULL) "" else value.toString())
        }
        return result
    }

    private fun deepLLanguageCode(language: String): String {
        val value = language.trim()
        if (Regex("^[a-zA-Z]{2}(-[a-zA-Z]{2})?$").matches(value)) {
            return value.uppercase(Locale.US)
        }
        val normalized = value.lowercase(Locale.US)
        return when (normalized) {
            "english" -> "EN-US"
            "english (us)" -> "EN-US"
            "english (uk)" -> "EN-GB"
            "german", "deutsch" -> "DE"
            "french", "français" -> "FR"
            "spanish", "español" -> "ES"
            "italian", "italiano" -> "IT"
            "portuguese", "português" -> "PT-PT"
            "dutch", "nederlands" -> "NL"
            "polish", "polski" -> "PL"
            "russian", "русский" -> "RU"
            "japanese", "日本語" -> "JA"
            "korean", "한국어" -> "KO"
            "chinese", "中文" -> "ZH"
            "indonesian", "bahasa indonesia" -> "ID"
            "turkish", "türkçe" -> "TR"
            "ukrainian", "українська" -> "UK"
            "swedish", "svenska" -> "SV"
            "danish", "dansk" -> "DA"
            "norwegian", "norsk" -> "NB"
            "finnish", "suomi" -> "FI"
            "czech", "čeština" -> "CS"
            "greek", "ελληνικά" -> "EL"
            "arabic", "العربية" -> "AR"
            "hindi", "हिन्दी" -> "HI"
            "bengali", "বাংলা" -> "BN"
            else -> value.uppercase(Locale.US).take(2)
        }
    }

    private fun validatedEndpoint(endpoint: String): String {
        val value = endpoint.trim()
        require(value.isNotBlank()) { "Enter an API endpoint for the selected provider." }
        val parsed = try {
            URL(value)
        } catch (_: Exception) {
            throw IllegalArgumentException("The API endpoint URL is invalid.")
        }
        require(parsed.protocol == "https" || parsed.protocol == "http") {
            "The API endpoint must use HTTP or HTTPS."
        }
        return value
    }
}
