package com.dsann.music.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.dsann.music.data.features.AiLyricsTranslationCatalog
import com.dsann.music.data.features.AiLyricsTranslationSettings
import com.dsann.music.data.features.AiLyricsTranslationSettingsStore
import com.dsann.music.ui.theme.DsmusicButton
import com.dsann.music.ui.theme.DsmusicCard
import com.dsann.music.ui.theme.DsmusicOutlinedButton
import com.dsann.music.ui.theme.DsmusicTheme
import com.dsann.music.ui.theme.DsmusicSwitchControl
import com.dsann.music.ui.theme.DropdownMenu
import com.dsann.music.ui.theme.DropdownMenuItem
import com.dsann.music.ui.theme.OutlinedTextField
import com.dsann.music.ui.theme.Text
import com.dsann.music.util.EncryptedFeatureSecretStore

@Composable
fun AiLyricsTranslationSettingsSection() {
    val context = LocalContext.current
    val settingsStore = remember(context) { AiLyricsTranslationSettingsStore(context) }
    val secretStore = remember(context) { EncryptedFeatureSecretStore(context) }
    val initialSettings = remember(settingsStore) { settingsStore.load() }

    var provider by rememberSaveable { mutableStateOf(initialSettings.provider) }
    var model by rememberSaveable { mutableStateOf(initialSettings.model) }
    var customEndpoint by rememberSaveable { mutableStateOf(initialSettings.customEndpoint) }
    var targetLanguage by rememberSaveable { mutableStateOf(initialSettings.targetLanguage) }
    var translationMode by rememberSaveable { mutableStateOf(initialSettings.mode) }
    var deeplFormality by rememberSaveable { mutableStateOf(initialSettings.deeplFormality) }
    var systemPrompt by rememberSaveable { mutableStateOf(initialSettings.systemPrompt) }
    var translationEnabled by rememberSaveable { mutableStateOf(initialSettings.enabled) }
    var configurationExpanded by rememberSaveable { mutableStateOf(false) }

    var apiKey by rememberSaveable(provider) {
        mutableStateOf(secretStore.read(AiLyricsTranslationCatalog.secretNameFor(provider)).orEmpty())
    }
    var keySaved by remember(provider) {
        mutableStateOf(
            !secretStore.read(AiLyricsTranslationCatalog.secretNameFor(provider)).isNullOrBlank()
        )
    }
    var providerMenuExpanded by remember { mutableStateOf(false) }
    var modelMenuExpanded by remember { mutableStateOf(false) }
    var modeMenuExpanded by remember { mutableStateOf(false) }
    var formalityMenuExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(
        translationEnabled, provider, model, customEndpoint, targetLanguage,
        translationMode, deeplFormality, systemPrompt
    ) {
        settingsStore.save(
            AiLyricsTranslationSettings(
                provider = provider,
                model = model,
                customEndpoint = customEndpoint,
                targetLanguage = targetLanguage,
                mode = translationMode,
                deeplFormality = deeplFormality,
                systemPrompt = systemPrompt,
                enabled = translationEnabled
            )
        )
    }

    DsmusicCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(DsmusicTheme.colorScheme.primary.copy(alpha = 0.13f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "AI",
                        color = DsmusicTheme.colorScheme.primary,
                        style = DsmusicTheme.typography.labelLarge
                    )
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        "AI lyrics translation",
                        style = DsmusicTheme.typography.titleMedium
                    )
                    Text(
                        "$provider · $targetLanguage",
                        color = DsmusicTheme.colorScheme.onSurfaceVariant,
                        style = DsmusicTheme.typography.bodySmall
                    )
                }
                DsmusicSwitchControl(
                    checked = translationEnabled,
                    onCheckedChange = { translationEnabled = it }
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    if (keySaved) "API key saved securely" else "API key not configured",
                    color = if (keySaved) DsmusicTheme.colorScheme.primary
                    else DsmusicTheme.colorScheme.onSurfaceVariant,
                    style = DsmusicTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f)
                )
                DsmusicOutlinedButton(
                    onClick = { configurationExpanded = !configurationExpanded },
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(if (configurationExpanded) "Done" else "Configure")
                }
            }

            if (configurationExpanded) {
            Text("Translation provider", style = DsmusicTheme.typography.titleMedium)
            Text(
                "Choose the service and model used when you translate lyrics from the player.",
                color = DsmusicTheme.colorScheme.onSurfaceVariant
            )
            androidx.compose.foundation.layout.Box(modifier = Modifier.fillMaxWidth()) {
                DsmusicOutlinedButton(
                    onClick = { providerMenuExpanded = true },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Provider · $provider") }
                DropdownMenu(
                    expanded = providerMenuExpanded,
                    onDismissRequest = { providerMenuExpanded = false },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    AiLyricsTranslationCatalog.providers.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option) },
                            onClick = {
                                provider = option
                                model = AiLyricsTranslationCatalog.defaultModelFor(option)
                                apiKey = secretStore.read(
                                    AiLyricsTranslationCatalog.secretNameFor(option)
                                ).orEmpty()
                                providerMenuExpanded = false
                            }
                        )
                    }
                }
            }

            if (provider == "Custom") {
                OutlinedTextField(
                    value = customEndpoint,
                    onValueChange = { customEndpoint = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Custom API endpoint") },
                    placeholder = { Text("https://host.example/v1/chat/completions") }
                )
            }

            if (provider != "DeepL") {
                OutlinedTextField(
                    value = model,
                    onValueChange = { model = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Model ID") },
                    placeholder = { Text("Enter the exact model ID from your provider") }
                )
                val models = AiLyricsTranslationCatalog.modelsFor(provider)
                if (models.isNotEmpty()) {
                    androidx.compose.foundation.layout.Box(modifier = Modifier.fillMaxWidth()) {
                        DsmusicOutlinedButton(
                            onClick = { modelMenuExpanded = true },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Choose a suggested model") }
                        DropdownMenu(
                            expanded = modelMenuExpanded,
                            onDismissRequest = { modelMenuExpanded = false },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            models.forEach { choice ->
                                DropdownMenuItem(
                                    text = { Text(choice) },
                                    onClick = {
                                        model = choice
                                        modelMenuExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            } else {
                Text(
                    "DeepL uses its own translation model; model selection is not available.",
                    style = DsmusicTheme.typography.bodySmall,
                    color = DsmusicTheme.colorScheme.onSurfaceVariant
                )
            }

            OutlinedTextField(
                value = targetLanguage,
                onValueChange = { targetLanguage = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Translate into") },
                placeholder = { Text("For example, Bengali, Hindi or English") }
            )

            if (provider != "DeepL") {
                androidx.compose.foundation.layout.Box(modifier = Modifier.fillMaxWidth()) {
                    DsmusicOutlinedButton(
                        onClick = { modeMenuExpanded = true },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Translation mode · $translationMode") }
                    DropdownMenu(
                        expanded = modeMenuExpanded,
                        onDismissRequest = { modeMenuExpanded = false },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        listOf("Literal", "Transcribed").forEach { mode ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        if (mode == "Literal") "Literal · translate meaning"
                                        else "Transcribed · reproduce pronunciation"
                                    )
                                },
                                onClick = {
                                    translationMode = mode
                                    modeMenuExpanded = false
                                }
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = systemPrompt,
                    onValueChange = { systemPrompt = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Custom system prompt (optional)") },
                    placeholder = {
                        Text("Leave blank to use the built-in prompt. Use {lineCount} for the number of lines.")
                    },
                    singleLine = false,
                    minLines = 3,
                    maxLines = 7
                )
                DsmusicOutlinedButton(
                    onClick = { systemPrompt = "" },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Reset translation prompt") }
            } else {
                androidx.compose.foundation.layout.Box(modifier = Modifier.fillMaxWidth()) {
                    DsmusicOutlinedButton(
                        onClick = { formalityMenuExpanded = true },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("DeepL formality · $deeplFormality") }
                    DropdownMenu(
                        expanded = formalityMenuExpanded,
                        onDismissRequest = { formalityMenuExpanded = false },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        listOf("default", "more", "less").forEach { value ->
                            DropdownMenuItem(
                                text = { Text(value) },
                                onClick = {
                                    deeplFormality = value
                                    formalityMenuExpanded = false
                                }
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(4.dp))
            Text("API credentials", style = DsmusicTheme.typography.titleMedium)
            OutlinedTextField(
                value = apiKey,
                onValueChange = { apiKey = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("$provider API key") },
                placeholder = {
                    Text(if (keySaved) "Key saved securely on this device" else "Paste your provider API key")
                },
                visualTransformation = PasswordVisualTransformation()
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DsmusicButton(
                    onClick = {
                        keySaved = secretStore.save(
                            AiLyricsTranslationCatalog.secretNameFor(provider),
                            apiKey
                        )
                        if (keySaved) apiKey = ""
                    },
                    enabled = apiKey.isNotBlank(),
                    modifier = Modifier.weight(1f)
                ) { Text("Save key") }
                DsmusicOutlinedButton(
                    onClick = {
                        secretStore.clear(AiLyricsTranslationCatalog.secretNameFor(provider))
                        apiKey = ""
                        keySaved = false
                    },
                    enabled = keySaved,
                    modifier = Modifier.weight(1f)
                ) { Text("Remove key") }
            }

            AiLyricsTranslationCatalog.keyHelpUrl(provider)
                .takeIf { it.isNotBlank() }
                ?.let { helpUrl ->
                    DsmusicOutlinedButton(
                        onClick = {
                            runCatching {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(helpUrl)))
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Get $provider API key") }
                }

            Text(
                if (keySaved) "$provider is configured on this device."
                else "Add an API key to enable translation. Your key is encrypted with Android Keystore.",
                style = DsmusicTheme.typography.bodySmall,
                color = DsmusicTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "Provider prices, free quotas and model availability are controlled by each provider. Lyrics are sent only when you press Translate in the lyrics view.",
                style = DsmusicTheme.typography.bodySmall,
                color = DsmusicTheme.colorScheme.onSurfaceVariant
            )
            }
        }
    }
}
