package com.dsann.music.data.features

import android.content.Context
import java.util.Locale

/**
 * Persistent, non-secret preferences for the lyrics translation feature.
 * API credentials are stored separately through EncryptedFeatureSecretStore.
 */
data class AiLyricsTranslationSettings(
    val provider: String = "OpenRouter",
    val model: String = "~google/gemini-flash-latest",
    val customEndpoint: String = "",
    val targetLanguage: String = "English",
    val mode: String = "Literal",
    val deeplFormality: String = "default",
    val systemPrompt: String = "",
    val enabled: Boolean = true
) {
    fun endpoint(): String {
        return if (provider == "Custom") {
            customEndpoint.trim()
        } else {
            AiLyricsTranslationCatalog.endpointFor(provider)
        }
    }
}

/**
 * Provider catalogue and useful model presets. Presets are editable: providers
 * routinely add, rename, and retire model IDs, so the app never locks users to
 * this list.
 */
object AiLyricsTranslationCatalog {
    val providers = listOf(
        "OpenRouter",
        "OpenAI",
        "Perplexity",
        "Claude",
        "Gemini",
        "xAI",
        "Mistral",
        "Inception",
        "DeepL",
        "Custom"
    )

    private val endpoints = mapOf(
        "OpenRouter" to "https://openrouter.ai/api/v1/chat/completions",
        "OpenAI" to "https://api.openai.com/v1/chat/completions",
        "Perplexity" to "https://api.perplexity.ai/chat/completions",
        "Claude" to "https://api.anthropic.com/v1/messages",
        "Gemini" to "https://generativelanguage.googleapis.com/v1beta/openai/chat/completions",
        "xAI" to "https://api.x.ai/v1/chat/completions",
        "Mistral" to "https://api.mistral.ai/v1/chat/completions",
        "Inception" to "https://api.inceptionlabs.ai/v1/chat/completions",
        "DeepL" to "https://api.deepl.com/v2/translate"
    )

    private val modelPresets = mapOf(
        "OpenRouter" to listOf(
            "~google/gemini-flash-latest",
            "google/gemini-3.8-flash",
            "google/gemini-3.7-flash",
            "openai/gpt-oss-120b",
            "~deepseek/deepseek-v4-flash-latest",
            "~openai/gpt-mini-latest",
            "inception/mercury-2.5-preview",
            "meta/muse-spark-1.3",
            "z-ai/glm-5.3-flash",
            "qwen/qwen3.8-flash"
        ),
        "OpenAI" to listOf(
            "gpt-5.6-sol",
            "gpt-5.6-terra",
            "gpt-5.6-luna",
            "gpt-5.5-2026-04-23",
            "gpt-5.4-2026-03-05"
        ),
        "Perplexity" to listOf(
            "sonar",
            "sonar-pro",
            "sonar-reasoning-pro"
        ),
        "Claude" to listOf(
            "claude-opus-5",
            "claude-sonnet-5",
            "claude-haiku-4-5-20251001",
            "claude-fable-5-1"
        ),
        "Gemini" to listOf(
            "gemini-3.8-flash",
            "gemini-flash-latest",
            "gemini-flash-lite-latest",
            "gemini-pro-latest"
        ),
        "xAI" to listOf(
            "grok-4.3",
            "grok-4.6",
            "grok-4.1-fast"
        ),
        "Mistral" to listOf(
            "mistral-large-latest",
            "mistral-medium-latest",
            "mistral-small-latest",
            "mistral-tiny-latest"
        ),
        "Inception" to listOf("mercury-2"),
        "DeepL" to emptyList(),
        "Custom" to emptyList()
    )

    private val keyHelpUrls = mapOf(
        "OpenRouter" to "https://openrouter.ai/keys",
        "OpenAI" to "https://platform.openai.com/api-keys",
        "Perplexity" to "https://www.perplexity.ai/settings/api",
        "Claude" to "https://console.anthropic.com/settings/keys",
        "Gemini" to "https://aistudio.google.com/apikey",
        "xAI" to "https://console.x.ai",
        "Mistral" to "https://console.mistral.ai/api-keys/",
        "Inception" to "https://platform.inceptionlabs.ai/",
        "DeepL" to "https://www.deepl.com/pro-api"
    )

    fun endpointFor(provider: String): String {
        return endpoints[provider] ?: ""
    }

    fun modelsFor(provider: String): List<String> {
        return modelPresets[provider] ?: emptyList()
    }

    fun defaultModelFor(provider: String): String {
        return modelsFor(provider).firstOrNull() ?: ""
    }

    fun keyHelpUrl(provider: String): String {
        return keyHelpUrls[provider] ?: ""
    }

    fun secretNameFor(provider: String): String {
        if (provider == "OpenRouter") return "openrouter_api_key"
        val safeName = provider.lowercase(Locale.US).replace(Regex("[^a-z0-9]+"), "_")
        return "lyrics_ai_key_" + safeName
    }

    fun isValidProvider(provider: String): Boolean {
        return providers.contains(provider)
    }
}

/**
 * Stores the selected provider, model, endpoint, prompt and translation options.
 * API keys must not be written to this preference file.
 */
class AiLyricsTranslationSettingsStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        "lyrics_translation_settings",
        Context.MODE_PRIVATE
    )

    fun load(): AiLyricsTranslationSettings {
        val providerValue = preferences.getString("provider", "OpenRouter") ?: "OpenRouter"
        val provider = if (AiLyricsTranslationCatalog.isValidProvider(providerValue)) {
            providerValue
        } else {
            "OpenRouter"
        }
        val defaultModel = AiLyricsTranslationCatalog.defaultModelFor(provider)
        val storedModel = preferences.getString("model", null)
        // Gemini 2.5 Flash-Lite was retired on OpenRouter in October 2026. Migrate
        // existing installs to the maintained latest-Flash alias instead of silently
        // continuing to send requests to a discontinued model ID.
        val model = if (
            provider == "OpenRouter" &&
            storedModel == "google/gemini-2.5-flash-lite"
        ) {
            defaultModel
        } else {
            storedModel ?: defaultModel
        }
        if (storedModel != model) {
            preferences.edit().putString("model", model).apply()
        }

        return AiLyricsTranslationSettings(
            provider = provider,
            model = model,
            customEndpoint = preferences.getString("custom_endpoint", "") ?: "",
            targetLanguage = preferences.getString("target_language", "English") ?: "English",
            mode = preferences.getString("mode", "Literal") ?: "Literal",
            deeplFormality = preferences.getString("deepl_formality", "default") ?: "default",
            systemPrompt = preferences.getString("system_prompt", "") ?: "",
            enabled = preferences.getBoolean("enabled", true)
        )
    }

    fun save(settings: AiLyricsTranslationSettings) {
        preferences.edit()
            .putString("provider", settings.provider)
            .putString("model", settings.model)
            .putString("custom_endpoint", settings.customEndpoint)
            .putString("target_language", settings.targetLanguage)
            .putString("mode", settings.mode)
            .putString("deepl_formality", settings.deeplFormality)
            .putString("system_prompt", settings.systemPrompt)
            .putBoolean("enabled", settings.enabled)
            .apply()
    }
}
