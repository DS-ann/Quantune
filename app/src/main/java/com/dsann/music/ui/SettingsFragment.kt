@file:Suppress("DEPRECATION")
package com.dsann.music.ui

import com.dsann.music.ui.theme.DsmusicPageHeading
import com.dsann.music.ui.theme.DsmusicCard
import com.dsann.music.ui.theme.DsmusicSurface
import com.dsann.music.ui.theme.DsmusicListItem
import com.dsann.music.ui.theme.DsmusicButton
import com.dsann.music.ui.theme.DsmusicOutlinedButton
import com.dsann.music.ui.theme.DsmusicTextButton
import com.dsann.music.ui.theme.DsmusicIconButton
import com.dsann.music.ui.player.AppAnimationFrameRateController
import com.dsann.music.ui.theme.DsmusicFilterChip
import com.dsann.music.ui.theme.DsmusicSwitchControl
import android.annotation.SuppressLint
import android.app.Activity
import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.NightsStay
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.SettingsBackupRestore
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.Update
import com.dsann.music.ui.theme.AlertDialog
import com.dsann.music.ui.theme.Button
import com.dsann.music.ui.theme.ButtonDefaults
import com.dsann.music.ui.theme.Card
import com.dsann.music.ui.theme.CardDefaults
import com.dsann.music.ui.theme.CircularProgressIndicator
import com.dsann.music.ui.theme.DropdownMenu
import com.dsann.music.ui.theme.DropdownMenuItem
import com.dsann.music.ui.theme.Icon
import com.dsann.music.ui.theme.IconButton
import com.dsann.music.ui.theme.LinearProgressIndicator
import com.dsann.music.ui.theme.ListItem
import com.dsann.music.ui.theme.ListItemDefaults
import com.dsann.music.ui.theme.DsmusicTheme
import com.dsann.music.ui.theme.LocalPageTransitionDurationMillis
import com.dsann.music.ui.theme.RadioButton
import com.dsann.music.ui.theme.Scaffold
import com.dsann.music.ui.theme.Slider
import com.dsann.music.ui.theme.Surface
import com.dsann.music.ui.theme.Switch
import com.dsann.music.ui.theme.SwitchDefaults
import com.dsann.music.ui.theme.Text
import com.dsann.music.ui.theme.TextButton
import com.dsann.music.ui.theme.TopAppBar
import com.dsann.music.ui.theme.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.dsann.music.BuildConfig
import com.dsann.music.applyDsmusicNightMode
import com.dsann.music.MainActivity
import com.dsann.music.model.PlayerGestureAction
import com.dsann.music.model.PlayerGestureSettings
import com.dsann.music.R
import com.dsann.music.ui.player.SleepTimerBottomSheet
import com.dsann.music.viewmodel.PlayerSharedViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URLEncoder
import java.text.DecimalFormat
import org.json.JSONObject
import kotlin.math.log10
import kotlin.math.pow

private const val KEY_SAVED_APPEARANCE_LOOKS = "saved_appearance_looks"
private const val MAX_BACKUP_HISTORY_EVENTS = 200

private val SmoothSpring = spring<Float>(
    dampingRatio = Spring.DampingRatioNoBouncy,
    stiffness = Spring.StiffnessMediumLow
)

// ═══════════════════════════════════════════════════════════════════════════════
// SETTINGS VIEW MODEL
// ═══════════════════════════════════════════════════════════════════════════════

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    @SuppressLint("StaticFieldLeak")
    private val context: Context = application.applicationContext
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // Keep the active nested Settings page across Activity/configuration recreation.
    // The Settings NavController remains the UI back-stack owner; this route is its
    // restoration anchor if Compose has to rebuild the nested host.
    private val _currentSettingsRoute = MutableStateFlow(SettingsRoute.Main.route)
    val currentSettingsRoute: StateFlow<String> = _currentSettingsRoute.asStateFlow()

    private val _includeListeningHistoryInBackup = MutableStateFlow(
        prefs.getBoolean(KEY_BACKUP_INCLUDE_HISTORY, true)
    )
    val includeListeningHistoryInBackup: StateFlow<Boolean> =
        _includeListeningHistoryInBackup.asStateFlow()

    fun setIncludeListeningHistoryInBackup(include: Boolean) {
        _includeListeningHistoryInBackup.value = include
        prefs.edit { putBoolean(KEY_BACKUP_INCLUDE_HISTORY, include) }
    }

    fun setCurrentSettingsRoute(route: String) {
        if (route.isNotBlank()) _currentSettingsRoute.value = route
    }

    // ── Dark Mode ────────────────────────────────────────────────────────────
    private val _darkMode = MutableStateFlow(
        prefs.getString("theme_mode", "system").let {
            if (it == "system") prefs.getBoolean(KEY_DARK_MODE, true) else it == "dark"
        }
    )
    val darkMode: StateFlow<Boolean> = _darkMode.asStateFlow()
    private val _themeMode = MutableStateFlow(
        prefs.getString("theme_mode", "system")?.lowercase()?.takeIf { it in listOf("system", "light", "dark") } ?: "system"
    )
    val themeMode: StateFlow<String> = _themeMode.asStateFlow()

    fun setThemeMode(mode: String) {
        val safe = mode.lowercase().takeIf { it in listOf("system", "light", "dark") } ?: "system"
        val resolvedDark = when (safe) {
            "dark" -> true
            "light" -> false
            else -> (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES
        }
        // Update both persisted keys together so independently composed surfaces
        // cannot observe a half-updated theme selection.
        prefs.edit {
            putString("theme_mode", safe)
            putBoolean(KEY_DARK_MODE, resolvedDark)
        }
        _themeMode.value = safe
        _darkMode.value = resolvedDark
        applyDsmusicNightMode(safe)
    }

    fun setDarkMode(enabled: Boolean) {
        val mode = if (enabled) "dark" else "light"
        prefs.edit {
            putString("theme_mode", mode)
            putBoolean(KEY_DARK_MODE, enabled)
        }
        _themeMode.value = mode
        _darkMode.value = enabled
        applyDsmusicNightMode(mode)
    }

    /** Refresh the appearance toggle after Android reports a system night-mode change. */
    fun refreshSystemTheme() {
        if (_themeMode.value != "system") return
        val systemDark = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        if (_darkMode.value != systemDark) {
            _darkMode.value = systemDark
            prefs.edit { putBoolean(KEY_DARK_MODE, systemDark) }
        }
    }

    // ── YouTube Music Cookies ──────────────────────────────────────────────────
    private val _ytCookies = MutableStateFlow(prefs.getString("yt_cookies", null))
    val ytCookies: StateFlow<String?> = _ytCookies.asStateFlow()

    fun setYtCookies(cookies: String?) {
        _ytCookies.value = cookies
        if (cookies != null) {
            prefs.edit {putString("yt_cookies", cookies)}
            com.dsann.music.data.innertube.InnerTubeClient.cookie = cookies
        } else {
            prefs.edit {remove("yt_cookies")}
            com.dsann.music.data.innertube.InnerTubeClient.cookie = null
        }
    }

    // ── Crossfade ────────────────────────────────────────────────────────────
    private val _crossfadeEnabled =
        MutableStateFlow(prefs.getBoolean(KEY_CROSSFADE_ENABLED, false))
    val crossfadeEnabled: StateFlow<Boolean> = _crossfadeEnabled.asStateFlow()

    private val _crossfadeDuration =
        MutableStateFlow(prefs.getFloat(KEY_CROSSFADE_DURATION, 3f))
    val crossfadeDuration: StateFlow<Float> = _crossfadeDuration.asStateFlow()

    fun setCrossfadeEnabled(enabled: Boolean) {
        _crossfadeEnabled.value = enabled
        prefs.edit {putBoolean(KEY_CROSSFADE_ENABLED, enabled)}
    }

    fun setCrossfadeDuration(duration: Float) {
        _crossfadeDuration.value = duration
        prefs.edit {putFloat(KEY_CROSSFADE_DURATION, duration)}
    }

    // ── Stream Audio Cache ───────────────────────────────────────────────────
    private val _mediaCacheEnabled = MutableStateFlow(prefs.getBoolean(KEY_MEDIA_CACHE_ENABLED, true))
    val mediaCacheEnabled: StateFlow<Boolean> = _mediaCacheEnabled.asStateFlow()
    private val _mediaCacheLimitGb = MutableStateFlow(prefs.getInt(KEY_MEDIA_CACHE_LIMIT_GB, 1).coerceIn(1, 10))
    val mediaCacheLimitGb: StateFlow<Int> = _mediaCacheLimitGb.asStateFlow()

    fun setMediaCacheEnabled(enabled: Boolean) {
        _mediaCacheEnabled.value = enabled
        prefs.edit { putBoolean(KEY_MEDIA_CACHE_ENABLED, enabled) }
        com.dsann.music.service.AudioPlaybackService.updateMediaCacheSettings(context)
    }
    fun setMediaCacheLimitGb(limitGb: Int) {
        val safeLimit = limitGb.coerceIn(1, 10)
        _mediaCacheLimitGb.value = safeLimit
        prefs.edit { putInt(KEY_MEDIA_CACHE_LIMIT_GB, safeLimit) }
        com.dsann.music.service.AudioPlaybackService.updateMediaCacheSettings(context)
    }

    // ── Audio Focus ──────────────────────────────────────────────────────────
    private val _audioFocus = MutableStateFlow(prefs.getBoolean(KEY_AUDIO_FOCUS, true))
    val audioFocus: StateFlow<Boolean> = _audioFocus.asStateFlow()

    fun setAudioFocus(enabled: Boolean) {
        _audioFocus.value = enabled
        prefs.edit {putBoolean(KEY_AUDIO_FOCUS, enabled)}
    }

    private val _autoplayEnabled = MutableStateFlow(prefs.getBoolean(KEY_AUTOPLAY_ENABLED, true))
    val autoplayEnabled: StateFlow<Boolean> = _autoplayEnabled.asStateFlow()

    fun setAutoplayEnabled(enabled: Boolean) {
        _autoplayEnabled.value = enabled
        prefs.edit { putBoolean(KEY_AUTOPLAY_ENABLED, enabled) }
    }

    // ── Pure AMOLED Black ──────────────────────────────────────────────────
    private val _amoledBlack = MutableStateFlow(prefs.getBoolean(KEY_AMOLED_BLACK, false))
    val amoledBlack: StateFlow<Boolean> = _amoledBlack.asStateFlow()

    fun setAmoledBlack(enabled: Boolean) {
        _amoledBlack.value = enabled
        prefs.edit {putBoolean(KEY_AMOLED_BLACK, enabled)}
    }

    // ── Hide Nav Labels ──────────────────────────────────────────────────
    private val _hideNavLabels = MutableStateFlow(prefs.getBoolean("hide_nav_labels", false))
    val hideNavLabels: StateFlow<Boolean> = _hideNavLabels.asStateFlow()

    // More precise navigation label modes, while retaining the old boolean for existing installs.
    private val _navLabelsMode = MutableStateFlow(
        prefs.getString("nav_label_mode", if (prefs.getBoolean("hide_nav_labels", false)) "Never" else "Always")
            ?.takeIf { it in listOf("Always", "Selected", "Never") } ?: "Always"
    )
    val navLabelsMode: StateFlow<String> = _navLabelsMode.asStateFlow()

    fun setNavLabelsMode(mode: String) {
        val safe = mode.takeIf { it in listOf("Always", "Selected", "Never") } ?: "Always"
        _navLabelsMode.value = safe
        val hide = safe == "Never"
        _hideNavLabels.value = hide
        prefs.edit {
            putString("nav_label_mode", safe)
            putBoolean("hide_nav_labels", hide)
        }
    }

    fun setHideNavLabels(hide: Boolean) {
        setNavLabelsMode(if (hide) "Never" else "Always")
    }

    // ── Dynamic Nav Style ──────────────────────────────────────────────────
    private val _dynamicNavStyle = MutableStateFlow(prefs.getBoolean("dynamic_nav_style", false))
    val dynamicNavStyle: StateFlow<Boolean> = _dynamicNavStyle.asStateFlow()

    fun setDynamicNavStyle(enabled: Boolean) {
        _dynamicNavStyle.value = enabled
        prefs.edit {putBoolean("dynamic_nav_style", enabled)}
    }

    // ── Navigation dock customization ──────────────────────────────────────
    private val _navBarStyle = MutableStateFlow(
        prefs.getString("nav_bar_style", "Floating")?.takeIf {
            it in listOf("Floating", "Docked", "Minimal")
        } ?: "Floating"
    )
    val navBarStyle: StateFlow<String> = _navBarStyle.asStateFlow()
    fun setNavBarStyle(value: String) {
        val safe = value.takeIf { it in listOf("Floating", "Docked", "Minimal") } ?: "Floating"
        _navBarStyle.value = safe
        prefs.edit { putString("nav_bar_style", safe) }
    }

    private val _navBarSize = MutableStateFlow(
        prefs.getString("nav_bar_size", "Standard")?.takeIf {
            it in listOf("Compact", "Standard", "Tall")
        } ?: "Standard"
    )
    val navBarSize: StateFlow<String> = _navBarSize.asStateFlow()
    fun setNavBarSize(value: String) {
        val safe = value.takeIf { it in listOf("Compact", "Standard", "Tall") } ?: "Standard"
        _navBarSize.value = safe
        prefs.edit { putString("nav_bar_size", safe) }
    }

    // ── Dynamic Album Theme ────────────────────────────────────────────────
    private val _dynamicAlbumTheme = MutableStateFlow(prefs.getBoolean(KEY_DYNAMIC_ALBUM_THEME, false))
    val dynamicAlbumTheme: StateFlow<Boolean> = _dynamicAlbumTheme.asStateFlow()

    fun setDynamicAlbumTheme(enabled: Boolean) {
        _dynamicAlbumTheme.value = enabled
        prefs.edit {putBoolean(KEY_DYNAMIC_ALBUM_THEME, enabled)}
    }

    private val _appearancePreset = MutableStateFlow(
        prefs.getString(KEY_APPEARANCE_PRESET, "Ocean") ?: "Ocean"
    )
    val appearancePreset: StateFlow<String> = _appearancePreset.asStateFlow()

    fun setAppearancePreset(preset: String) {
        val safePreset = preset.takeIf {
            it in listOf("Ocean", "Violet", "Emerald", "Amber", "Rose", "Monochrome", "Crimson", "Teal", "Cyan", "Indigo", "Lime", "Graphite", "Custom")
        } ?: "Ocean"
        _appearancePreset.value = safePreset
        prefs.edit { putString(KEY_APPEARANCE_PRESET, safePreset) }
    }

    private val _customAccentColor = MutableStateFlow(
        prefs.getString("custom_accent_color", "#7C9CFF") ?: "#7C9CFF"
    )
    val customAccentColor: StateFlow<String> = _customAccentColor.asStateFlow()
    fun setCustomAccentColor(value: String) {
        val cleaned = value.trim().uppercase().let { if (it.startsWith("#")) it else "#$it" }
        val valid = cleaned.matches(Regex("#[0-9A-F]{6}"))
        if (!valid) return
        runCatching { android.graphics.Color.parseColor(cleaned) }.getOrNull() ?: return
        _customAccentColor.value = cleaned
        prefs.edit { putString("custom_accent_color", cleaned) }
        setAppearancePreset("Custom")
    }

    private val _genreGradientStyle = MutableStateFlow(
        prefs.getString("genre_card_gradient_style", "Theme")
            ?.takeIf { it in listOf("Theme", "Aurora", "Ocean", "Sunset", "Forest", "Berry") }
            ?: "Theme"
    )
    val genreGradientStyle: StateFlow<String> = _genreGradientStyle.asStateFlow()
    fun setGenreGradientStyle(value: String) {
        val safe = value.takeIf {
            it in listOf("Theme", "Aurora", "Ocean", "Sunset", "Forest", "Berry")
        } ?: "Theme"
        _genreGradientStyle.value = safe
        prefs.edit { putString("genre_card_gradient_style", safe) }
    }

    private val _uiCornerStyle = MutableStateFlow(
        prefs.getString("ui_corner_style", "Soft")?.takeIf { it in listOf("Tight", "Soft", "Round") } ?: "Soft"
    )
    val uiCornerStyle: StateFlow<String> = _uiCornerStyle.asStateFlow()
    fun setUiCornerStyle(value: String) {
        val safe = value.takeIf { it in listOf("Tight", "Soft", "Round") } ?: "Soft"
        _uiCornerStyle.value = safe
        prefs.edit { putString("ui_corner_style", safe) }
    }

    private val _playerArtworkShape = MutableStateFlow(
        prefs.getString(KEY_PLAYER_ARTWORK_SHAPE, "Rounded") ?: "Rounded"
    )
    val playerArtworkShape: StateFlow<String> = _playerArtworkShape.asStateFlow()

    fun setPlayerArtworkShape(shape: String) {
        val safeShape = shape.takeIf {
            it in listOf("Rounded", "Square", "Circle", "Squircle", "Flower", "Cookie", "Arch", "Slanted")
        } ?: "Rounded"
        _playerArtworkShape.value = safeShape
        prefs.edit { putString(KEY_PLAYER_ARTWORK_SHAPE, safeShape) }
    }

    private val _playerBackgroundStyle = MutableStateFlow(
        prefs.getString("player_background_style", "Artwork") ?: "Artwork"
    )
    val playerBackgroundStyle: StateFlow<String> = _playerBackgroundStyle.asStateFlow()
    fun setPlayerBackgroundStyle(value: String) {
        val safe = value.takeIf { it in listOf("Artwork", "Theme", "Black", "Gradient") } ?: "Artwork"
        _playerBackgroundStyle.value = safe
        prefs.edit { putString("player_background_style", safe) }
    }

    private val _nowPlayingCoverAnimation = MutableStateFlow(
        prefs.getString(NowPlayingCoverAnimationPreference.KEY, "Bars")
            ?.takeIf { it in NowPlayingCoverAnimationPreference.choices } ?: "Bars"
    )
    val nowPlayingCoverAnimation: StateFlow<String> = _nowPlayingCoverAnimation.asStateFlow()

    fun setNowPlayingCoverAnimation(value: String) {
        val safe = value.takeIf { it in NowPlayingCoverAnimationPreference.choices } ?: "Bars"
        _nowPlayingCoverAnimation.value = safe
        NowPlayingCoverAnimationPreference.set(context, safe)
    }

    private val _miniPlayerStyle = MutableStateFlow(
        prefs.getString("mini_player_style", "Card") ?: "Card"
    )
    val miniPlayerStyle: StateFlow<String> = _miniPlayerStyle.asStateFlow()
    fun setMiniPlayerStyle(value: String) {
        val safe = value.takeIf { it in listOf("Card", "Flat", "Compact", "Pill") } ?: "Card"
        _miniPlayerStyle.value = safe
        prefs.edit { putString("mini_player_style", safe) }
    }

    private val _miniPlayerControlShape = MutableStateFlow(
        prefs.getString("mini_player_control_shape", "Circle") ?: "Circle"
    )
    val miniPlayerControlShape: StateFlow<String> = _miniPlayerControlShape.asStateFlow()
    fun setMiniPlayerControlShape(value: String) {
        val safe = value.takeIf { it in listOf("Circle", "Rounded", "Pill", "None") } ?: "Circle"
        _miniPlayerControlShape.value = safe
        prefs.edit { putString("mini_player_control_shape", safe) }
    }

    private val _miniPlayerControlIcons = MutableStateFlow(
        prefs.getString("mini_player_control_icons", "Classic") ?: "Classic"
    )
    val miniPlayerControlIcons: StateFlow<String> = _miniPlayerControlIcons.asStateFlow()
    fun setMiniPlayerControlIcons(value: String) {
        val safe = value.takeIf { it in listOf("Classic", "Chevron", "Seek") } ?: "Classic"
        _miniPlayerControlIcons.value = safe
        prefs.edit { putString("mini_player_control_icons", safe) }
    }

    private val _miniPlayerProgressStyle = MutableStateFlow(
        prefs.getString("mini_player_progress", "Ring") ?: "Ring"
    )
    val miniPlayerProgressStyle: StateFlow<String> = _miniPlayerProgressStyle.asStateFlow()
    fun setMiniPlayerProgressStyle(value: String) {
        val safe = value.takeIf { it in listOf("Ring", "Line", "Fill", "Matching cover", "None") } ?: "Ring"
        _miniPlayerProgressStyle.value = safe
        prefs.edit { putString("mini_player_progress", safe) }
    }

    // ── Player, your way ────────────────────────────────────────────────────
    private val _playerLayout = MutableStateFlow(
        prefs.getString("player_layout", "Standard")?.takeIf {
            it in listOf("Standard", "Vinyl", "Cassette")
        } ?: "Standard"
    )
    val playerLayout: StateFlow<String> = _playerLayout.asStateFlow()
    fun setPlayerLayout(value: String) {
        val safe = value.takeIf { it in listOf("Standard", "Vinyl", "Cassette") } ?: "Standard"
        _playerLayout.value = safe
        prefs.edit { putString("player_layout", safe) }
    }

    private val _playerArtworkScale = MutableStateFlow(
        prefs.getFloat("player_artwork_scale", 1f).coerceIn(0.65f, 1f)
    )
    val playerArtworkScale: StateFlow<Float> = _playerArtworkScale.asStateFlow()
    fun setPlayerArtworkScale(value: Float) {
        val safe = value.coerceIn(0.65f, 1f)
        _playerArtworkScale.value = safe
        prefs.edit { putFloat("player_artwork_scale", safe) }
    }

    private val availablePlayerButtons = listOf("lyrics", "video", "queue", "share", "download", "like", "dislike", "sleep", "shuffle", "repeat")
    private val defaultPlayerButtons = listOf("lyrics", "video", "queue", "share", "download")
    private fun readPlayerButtons(): List<String> {
        val stored = prefs.getString("player_buttons", null)
        val parsed = stored?.split(",")?.map { it.trim().lowercase() }
            ?.filter { it in availablePlayerButtons }?.distinct().orEmpty()
        return (parsed + defaultPlayerButtons.filter { it !in parsed }).take(6)
            .let { safe -> if (safe.size >= 3) safe else defaultPlayerButtons }
    }
    private val _playerButtons = MutableStateFlow(readPlayerButtons())
    val playerButtons: StateFlow<List<String>> = _playerButtons.asStateFlow()
    fun setPlayerButtons(buttons: List<String>) {
        val safe = buttons.filter { it in availablePlayerButtons }.distinct().take(6)
        if (safe.size < 3) return
        _playerButtons.value = safe
        prefs.edit { putString("player_buttons", safe.joinToString(",")) }
    }
    fun movePlayerButton(from: Int, to: Int) {
        setPlayerButtons(com.dsann.music.util.OrderUtils.move(_playerButtons.value, from, to))
    }

    private val availableSessionButtons = listOf("like", "shuffle", "repeat", "dislike", "back10", "ahead30")
    private val defaultSessionButtons = listOf("like", "shuffle")
    private fun readSessionButtons(): List<String> {
        val stored = prefs.getString("session_buttons", null) ?: return defaultSessionButtons
        return stored.split(",").map { it.trim().lowercase() }
            .filter { it in availableSessionButtons }.distinct().take(2)
    }
    private val _sessionButtons = MutableStateFlow(readSessionButtons())
    val sessionButtons: StateFlow<List<String>> = _sessionButtons.asStateFlow()
    fun setSessionButtons(buttons: List<String>) {
        val safe = buttons.filter { it in availableSessionButtons }.distinct().take(2)
        _sessionButtons.value = safe
        prefs.edit { putString("session_buttons", safe.joinToString(",")) }
    }
    fun moveSessionButton(from: Int, to: Int) {
        setSessionButtons(com.dsann.music.util.OrderUtils.move(_sessionButtons.value, from, to))
    }

    private val _playerTitleAlign = MutableStateFlow(prefs.getString("player_title_align", "Left") ?: "Left")
    val playerTitleAlign: StateFlow<String> = _playerTitleAlign.asStateFlow()
    fun setPlayerTitleAlign(value: String) {
        val safe = value.takeIf { it in listOf("Left", "Centered") } ?: "Left"
        _playerTitleAlign.value = safe
        prefs.edit { putString("player_title_align", safe) }
    }

    private val _playerTitleSize = MutableStateFlow(prefs.getString("player_title_size", "Medium") ?: "Medium")
    val playerTitleSize: StateFlow<String> = _playerTitleSize.asStateFlow()
    fun setPlayerTitleSize(value: String) {
        val safe = value.takeIf { it in listOf("Small", "Medium", "Large") } ?: "Medium"
        _playerTitleSize.value = safe
        prefs.edit { putString("player_title_size", safe) }
    }

    private val _playerTitleWeight = MutableStateFlow(prefs.getString("player_title_weight", "Bold") ?: "Bold")
    val playerTitleWeight: StateFlow<String> = _playerTitleWeight.asStateFlow()
    fun setPlayerTitleWeight(value: String) {
        val safe = value.takeIf { it in listOf("Light", "Regular", "Bold") } ?: "Bold"
        _playerTitleWeight.value = safe
        prefs.edit { putString("player_title_weight", safe) }
    }

    private val _playerTitleScroll = MutableStateFlow(prefs.getBoolean("player_title_scroll", true))
    val playerTitleScroll: StateFlow<Boolean> = _playerTitleScroll.asStateFlow()
    fun setPlayerTitleScroll(value: Boolean) {
        _playerTitleScroll.value = value
        prefs.edit { putBoolean("player_title_scroll", value) }
    }

    private val _playerTimes = MutableStateFlow(prefs.getString("player_times", "Both") ?: "Both")
    val playerTimes: StateFlow<String> = _playerTimes.asStateFlow()
    fun setPlayerTimes(value: String) {
        val safe = value.takeIf { it in listOf("Both", "Elapsed", "Time left", "None") } ?: "Both"
        _playerTimes.value = safe
        prefs.edit { putString("player_times", safe) }
    }

    private val _scrubberStyle = MutableStateFlow(
        prefs.getString("scrubber_style", "Waveform")
            ?.let {
                when (it) {
                    "Wave" -> "Waveform"
                    "Thumb" -> "Medium"
                    else -> it
                }
            }
            ?.takeIf { it in listOf("Hairline", "Bold", "Medium", "Waveform") }
            ?: "Waveform"
    )
    val scrubberStyle: StateFlow<String> = _scrubberStyle.asStateFlow()
    fun setScrubberStyle(value: String) {
        val normalized = if (value == "Thumb") "Medium" else value
        val safe = normalized.takeIf { it in listOf("Hairline", "Bold", "Medium", "Waveform") } ?: "Waveform"
        _scrubberStyle.value = safe
        prefs.edit { putString("scrubber_style", safe) }
    }

    private val _scrubberColor = MutableStateFlow(prefs.getString("scrubber_color", "Accent") ?: "Accent")
    val scrubberColor: StateFlow<String> = _scrubberColor.asStateFlow()
    fun setScrubberColor(value: String) {
        val safe = value.takeIf { it in listOf("Accent", "White", "Cover") } ?: "Accent"
        _scrubberColor.value = safe
        prefs.edit { putString("scrubber_color", safe) }
    }

    private val _visualizerStyle = MutableStateFlow(
        (prefs.getString("player_visualizer", "Off") ?: "Off")
            .let { if (it == "Ring") "Oval" else it }
            .takeIf { it in listOf("Off", "Bars", "Wave", "Oval", "Pulse", "Glow") }
            ?: "Off"
    )
    val visualizerStyle: StateFlow<String> = _visualizerStyle.asStateFlow()
    fun setVisualizerStyle(value: String) {
        val migrated = if (value == "Ring") "Oval" else value
        val safe = migrated.takeIf { it in listOf("Off", "Bars", "Wave", "Oval", "Pulse", "Glow") } ?: "Off"
        _visualizerStyle.value = safe
        prefs.edit { putString("player_visualizer", safe) }
    }

    // Additional player customization.
    private val _playerButtonsPill = MutableStateFlow(prefs.getBoolean("player_buttons_pill", true))
    val playerButtonsPill: StateFlow<Boolean> = _playerButtonsPill.asStateFlow()
    fun setPlayerButtonsPill(enabled: Boolean) {
        _playerButtonsPill.value = enabled
        prefs.edit { putBoolean("player_buttons_pill", enabled) }
    }

    private val _playerButtonStyle = MutableStateFlow(
        when (prefs.getString("player_button_style", "Pill") ?: "Pill") {
            "Rounded" -> "Round"
            "Minimal" -> "None"
            "Round", "Pill", "None", "Square" -> prefs.getString("player_button_style", "Pill") ?: "Pill"
            else -> "Pill"
        }
    )
    val playerButtonStyle: StateFlow<String> = _playerButtonStyle.asStateFlow()
    fun setPlayerButtonStyle(value: String) {
        val safe = value.takeIf { it in listOf("Round", "Pill", "None", "Square") } ?: "Pill"
        _playerButtonStyle.value = safe
        prefs.edit { putString("player_button_style", safe) }
    }

    // Progress thickness is fixed at 1.5×; legacy saved values no longer override it.
    private val _scrubberThickness = MutableStateFlow(1.5f)
    val scrubberThickness: StateFlow<Float> = _scrubberThickness.asStateFlow()
    fun setScrubberThickness(value: Float) {
        _scrubberThickness.value = 1.5f
        prefs.edit { putFloat("scrubber_thickness", 1.5f) }
    }

    private val _scrubberThumbStyle = MutableStateFlow(prefs.getString("scrubber_thumb_style", "Auto") ?: "Auto")
    val scrubberThumbStyle: StateFlow<String> = _scrubberThumbStyle.asStateFlow()
    fun setScrubberThumbStyle(value: String) {
        val safe = value.takeIf { it in listOf("Auto", "Dot", "Line", "Ring", "None") } ?: "Auto"
        _scrubberThumbStyle.value = safe
        prefs.edit { putString("scrubber_thumb_style", safe) }
    }

    private val _visualizerColor = MutableStateFlow(prefs.getString("visualizer_color", "Accent") ?: "Accent")
    val visualizerColor: StateFlow<String> = _visualizerColor.asStateFlow()
    fun setVisualizerColor(value: String) {
        val safe = value.takeIf { it in listOf("Accent", "White", "Cover", "Spectrum") } ?: "Accent"
        _visualizerColor.value = safe
        prefs.edit { putString("visualizer_color", safe) }
    }

    private val _lyricsTextSize = MutableStateFlow(
        prefs.getString("lyrics_text_size", "Standard") ?: "Standard"
    )
    val lyricsTextSize: StateFlow<String> = _lyricsTextSize.asStateFlow()
    fun setLyricsTextSize(value: String) {
        val safe = value.takeIf { it in listOf("Small", "Standard", "Large") } ?: "Standard"
        _lyricsTextSize.value = safe
        prefs.edit { putString("lyrics_text_size", safe) }
    }

    // Show the currently active synced line in the portrait full-player's free control gap.
    private val _showSyncedLyricsInPlayer = MutableStateFlow(
        prefs.getBoolean("show_synced_lyrics_in_player", true)
    )
    val showSyncedLyricsInPlayer: StateFlow<Boolean> = _showSyncedLyricsInPlayer.asStateFlow()
    fun setShowSyncedLyricsInPlayer(enabled: Boolean) {
        _showSyncedLyricsInPlayer.value = enabled
        prefs.edit { putBoolean("show_synced_lyrics_in_player", enabled) }
    }

    private val _lyricsAlignment = MutableStateFlow(
        prefs.getString("lyrics_alignment", "Original") ?: "Original"
    )
    val lyricsAlignment: StateFlow<String> = _lyricsAlignment.asStateFlow()
    fun setLyricsAlignment(value: String) {
        val safe = value.takeIf { it in listOf("Original", "Left", "Center") } ?: "Original"
        _lyricsAlignment.value = safe
        prefs.edit { putString("lyrics_alignment", safe) }
    }

    private val _lyricsColorStyle = MutableStateFlow(
        when (prefs.getString("lyrics_color_style", "Home Studio Gradient")) {
            "Dynamic (Gradient)" -> "Home Studio Gradient"
            "UI Matching" -> "UI Matching"
            "White" -> "White"
            else -> "Home Studio Gradient"
        }
    )
    val lyricsColorStyle: StateFlow<String> = _lyricsColorStyle.asStateFlow()
    fun setLyricsColorStyle(value: String) {
        val safe = value.takeIf { it in listOf("Home Studio Gradient", "UI Matching", "White") }
            ?: "Home Studio Gradient"
        _lyricsColorStyle.value = safe
        prefs.edit { putString("lyrics_color_style", safe) }
    }

    private val _lyricsFontFamily = MutableStateFlow(
        prefs.getString("lyrics_font_family", "Rounded") ?: "Rounded"
    )
    val lyricsFontFamily: StateFlow<String> = _lyricsFontFamily.asStateFlow()
    fun setLyricsFontFamily(value: String) {
        val safe = value.takeIf { it in listOf("Rounded", "Sans", "Serif", "Mono") } ?: "Rounded"
        _lyricsFontFamily.value = safe
        prefs.edit { putString("lyrics_font_family", safe) }
    }

    private val _uiDensity = MutableStateFlow(
        prefs.getString("ui_density", "Comfortable") ?: "Comfortable"
    )
    val uiDensity: StateFlow<String> = _uiDensity.asStateFlow()
    fun setUiDensity(value: String) {
        val safe = value.takeIf { it in listOf("Compact", "Comfortable", "Large") } ?: "Comfortable"
        _uiDensity.value = safe
        prefs.edit { putString("ui_density", safe) }
    }

    private val _taperedSongDividers = MutableStateFlow(
        prefs.getBoolean(KEY_TAPERED_SONG_DIVIDERS, false)
    )
    val taperedSongDividers: StateFlow<Boolean> = _taperedSongDividers.asStateFlow()
    fun setTaperedSongDividers(enabled: Boolean) {
        _taperedSongDividers.value = enabled
        SongRowDividerPreferences.set(context, enabled)
    }

    private val _uiFontFamily = MutableStateFlow(
        when (prefs.getString("ui_font_family", "Roboto") ?: "Roboto") {
            "Cursive" -> "Cursive"
            "Sans", "Google Sans Flex" -> "Sans"
            "Serif" -> "Serif"
            "Monospace" -> "Monospace"
            else -> "Roboto"
        }
    )
    val uiFontFamily: StateFlow<String> = _uiFontFamily.asStateFlow()
    fun setUiFontFamily(value: String) {
        val safe = value.takeIf { it in listOf("Cursive", "Roboto", "Sans", "Serif", "Monospace") } ?: "Roboto"
        _uiFontFamily.value = safe
        prefs.edit { putString("ui_font_family", safe) }
    }

    private val _uiFontScale = MutableStateFlow(prefs.getFloat("ui_font_scale", 1f).coerceIn(0.85f, 1.30f))
    val uiFontScale: StateFlow<Float> = _uiFontScale.asStateFlow()
    fun setUiFontScale(value: Float) {
        val safe = value.takeIf { it.isFinite() }?.coerceIn(0.85f, 1.30f) ?: 1f
        _uiFontScale.value = safe
        prefs.edit { putFloat("ui_font_scale", safe) }
    }

    private val _uiFontWeight = MutableStateFlow(prefs.getString("ui_font_weight", "Default") ?: "Default")
    val uiFontWeight: StateFlow<String> = _uiFontWeight.asStateFlow()
    fun setUiFontWeight(value: String) {
        val safe = value.takeIf { it in listOf("Default", "Lighter", "Bolder") } ?: "Default"
        _uiFontWeight.value = safe
        prefs.edit { putString("ui_font_weight", safe) }
    }

    private val _uiLetterSpacing = MutableStateFlow(prefs.getString("ui_letter_spacing", "Normal") ?: "Normal")
    val uiLetterSpacing: StateFlow<String> = _uiLetterSpacing.asStateFlow()
    fun setUiLetterSpacing(value: String) {
        val safe = value.takeIf { it in listOf("Tight", "Normal", "Wide") } ?: "Normal"
        _uiLetterSpacing.value = safe
        prefs.edit { putString("ui_letter_spacing", safe) }
    }

    // ── Saved appearance Looks ──────────────────────────────────────────────
    // A Look stores only presentation preferences. Playback, downloads, cache,
    // account and library settings are deliberately excluded.
    private val appearanceLookKeys = listOf(
        "theme_mode", KEY_AMOLED_BLACK, KEY_DYNAMIC_ALBUM_THEME, KEY_APPEARANCE_PRESET,
        "custom_accent_color", "genre_card_gradient_style", "ui_corner_style", KEY_PLAYER_ARTWORK_SHAPE, "player_background_style",
        NowPlayingCoverAnimationPreference.KEY, "mini_player_style",
        "mini_player_control_shape", "mini_player_control_icons", "mini_player_progress",
        "player_layout", "player_artwork_scale", "player_buttons_pill", "player_buttons", "session_buttons", "player_title_align", "player_title_size",
        "player_title_weight", "player_title_scroll", "player_times", "scrubber_style",
        "scrubber_color", "player_visualizer", "player_button_style", "scrubber_thickness",
        "scrubber_thumb_style", "visualizer_color", "lyrics_text_size", "lyrics_alignment",
        "lyrics_font_family", "lyrics_color_style", "show_synced_lyrics_in_player",
        "ui_density", "ui_font_family", "ui_font_scale", "tapered_song_dividers",
        "ui_font_weight", "ui_letter_spacing", "nav_label_mode", "hide_nav_labels",
        "dynamic_nav_style", "nav_bar_style", "nav_bar_size"
    )

    private fun readSavedAppearanceLooks(): List<String> = runCatching {
        val json = JSONObject(prefs.getString(KEY_SAVED_APPEARANCE_LOOKS, "{}") ?: "{}")
        json.keys().asSequence().toList()
    }.getOrDefault(emptyList())

    private val _savedAppearanceLooks = MutableStateFlow(readSavedAppearanceLooks())
    val savedAppearanceLooks: StateFlow<List<String>> = _savedAppearanceLooks.asStateFlow()

    fun saveAppearanceLook(rawName: String) {
        val name = rawName.trim().replace(Regex("[\\r\\n]"), " ").take(32)
        if (name.isBlank()) return

        val all = prefs.all
        val snapshot = JSONObject()
        appearanceLookKeys.forEach { key ->
            val value = all[key] ?: return@forEach
            if (value !is Set<*>) snapshot.put(key, value)
        }

        val looks = runCatching {
            JSONObject(prefs.getString(KEY_SAVED_APPEARANCE_LOOKS, "{}") ?: "{}")
        }.getOrElse { JSONObject() }
        looks.put(name, snapshot)
        prefs.edit { putString(KEY_SAVED_APPEARANCE_LOOKS, looks.toString()) }
        _savedAppearanceLooks.value = looks.keys().asSequence().toList()
    }

    fun applyAppearanceLook(name: String) {
        val looks = runCatching {
            JSONObject(prefs.getString(KEY_SAVED_APPEARANCE_LOOKS, "{}") ?: "{}")
        }.getOrNull() ?: return
        val look = looks.optJSONObject(name) ?: return

        setThemeMode(look.optString("theme_mode", _themeMode.value))
        setAmoledBlack(look.optBoolean(KEY_AMOLED_BLACK, _amoledBlack.value))
        setDynamicAlbumTheme(look.optBoolean(KEY_DYNAMIC_ALBUM_THEME, _dynamicAlbumTheme.value))
        setAppearancePreset(look.optString(KEY_APPEARANCE_PRESET, _appearancePreset.value))
        setGenreGradientStyle(look.optString("genre_card_gradient_style", _genreGradientStyle.value))
        val savedCustomAccent = look.optString("custom_accent_color", _customAccentColor.value)
            .trim().uppercase().let { if (it.startsWith("#")) it else "#$it" }
        if (savedCustomAccent.matches(Regex("#[0-9A-F]{6}"))
            && runCatching { android.graphics.Color.parseColor(savedCustomAccent) }.isSuccess
        ) {
            _customAccentColor.value = savedCustomAccent
            prefs.edit { putString("custom_accent_color", savedCustomAccent) }
        }
        setUiCornerStyle(look.optString("ui_corner_style", _uiCornerStyle.value))
        setPlayerArtworkShape(look.optString(KEY_PLAYER_ARTWORK_SHAPE, _playerArtworkShape.value))
        setPlayerBackgroundStyle(look.optString("player_background_style", _playerBackgroundStyle.value))
        setNowPlayingCoverAnimation(look.optString(NowPlayingCoverAnimationPreference.KEY, _nowPlayingCoverAnimation.value))
        setMiniPlayerStyle(look.optString("mini_player_style", _miniPlayerStyle.value))
        setMiniPlayerControlShape(look.optString("mini_player_control_shape", _miniPlayerControlShape.value))
        setMiniPlayerControlIcons(look.optString("mini_player_control_icons", _miniPlayerControlIcons.value))
        setMiniPlayerProgressStyle(look.optString("mini_player_progress", _miniPlayerProgressStyle.value))
        setPlayerLayout(look.optString("player_layout", _playerLayout.value))
        setPlayerArtworkScale(look.optDouble("player_artwork_scale", _playerArtworkScale.value.toDouble()).toFloat())
        setPlayerButtonsPill(look.optBoolean("player_buttons_pill", _playerButtonsPill.value))
        setPlayerButtons(
            look.optString("player_buttons", _playerButtons.value.joinToString(","))
                .split(",").map { it.trim() }.filter { it.isNotEmpty() }
        )
        setSessionButtons(
            look.optString("session_buttons", _sessionButtons.value.joinToString(","))
                .split(",").map { it.trim() }.filter { it.isNotEmpty() }
        )
        setPlayerTitleAlign(look.optString("player_title_align", _playerTitleAlign.value))
        setPlayerTitleSize(look.optString("player_title_size", _playerTitleSize.value))
        setPlayerTitleWeight(look.optString("player_title_weight", _playerTitleWeight.value))
        setPlayerTitleScroll(look.optBoolean("player_title_scroll", _playerTitleScroll.value))
        setPlayerTimes(look.optString("player_times", _playerTimes.value))
        setScrubberStyle(look.optString("scrubber_style", _scrubberStyle.value))
        setScrubberColor(look.optString("scrubber_color", _scrubberColor.value))
        setVisualizerStyle(look.optString("player_visualizer", _visualizerStyle.value))
        setPlayerButtonStyle(look.optString("player_button_style", _playerButtonStyle.value))
        setScrubberThickness(look.optDouble("scrubber_thickness", _scrubberThickness.value.toDouble()).toFloat())
        setScrubberThumbStyle(look.optString("scrubber_thumb_style", _scrubberThumbStyle.value))
        setVisualizerColor(look.optString("visualizer_color", _visualizerColor.value))
        setLyricsTextSize(look.optString("lyrics_text_size", _lyricsTextSize.value))
        setShowSyncedLyricsInPlayer(
            look.optBoolean("show_synced_lyrics_in_player", _showSyncedLyricsInPlayer.value)
        )
        setLyricsAlignment(look.optString("lyrics_alignment", _lyricsAlignment.value))
        setLyricsFontFamily(look.optString("lyrics_font_family", _lyricsFontFamily.value))
        setLyricsColorStyle(look.optString("lyrics_color_style", _lyricsColorStyle.value))
        setUiDensity(look.optString("ui_density", _uiDensity.value))
        setUiFontFamily(look.optString("ui_font_family", _uiFontFamily.value))
        setUiFontScale(look.optDouble("ui_font_scale", _uiFontScale.value.toDouble()).toFloat())
        setUiFontWeight(look.optString("ui_font_weight", _uiFontWeight.value))
        setUiLetterSpacing(look.optString("ui_letter_spacing", _uiLetterSpacing.value))
        setNavLabelsMode(look.optString("nav_label_mode", _navLabelsMode.value))
        setDynamicNavStyle(look.optBoolean("dynamic_nav_style", _dynamicNavStyle.value))
        setNavBarStyle(look.optString("nav_bar_style", _navBarStyle.value))
        setNavBarSize(look.optString("nav_bar_size", _navBarSize.value))
    }

    fun deleteAppearanceLook(name: String) {
        val looks = runCatching {
            JSONObject(prefs.getString(KEY_SAVED_APPEARANCE_LOOKS, "{}") ?: "{}")
        }.getOrNull() ?: return
        looks.remove(name)
        prefs.edit { putString(KEY_SAVED_APPEARANCE_LOOKS, looks.toString()) }
        _savedAppearanceLooks.value = looks.keys().asSequence().toList()
    }

    // ── Ignore Short Audio ──────────────────────────────────────────────────
    private val _ignoreShortAudio = MutableStateFlow(prefs.getBoolean(KEY_IGNORE_SHORT_AUDIO, false))
    val ignoreShortAudio: StateFlow<Boolean> = _ignoreShortAudio.asStateFlow()

    private val _ignoreShortAudioDuration = MutableStateFlow(prefs.getFloat(KEY_IGNORE_SHORT_AUDIO_DURATION, 30f))
    val ignoreShortAudioDuration: StateFlow<Float> = _ignoreShortAudioDuration.asStateFlow()

    fun setIgnoreShortAudio(enabled: Boolean) {
        _ignoreShortAudio.value = enabled
        prefs.edit {putBoolean(KEY_IGNORE_SHORT_AUDIO, enabled)}
    }

    fun setIgnoreShortAudioDuration(duration: Float) {
        _ignoreShortAudioDuration.value = duration
        prefs.edit {putFloat(KEY_IGNORE_SHORT_AUDIO_DURATION, duration)}
    }

    // ── Data Saver ──────────────────────────────────────────────────────────
    private val _dataSaver = MutableStateFlow(prefs.getBoolean(KEY_DATA_SAVER, false))
    val dataSaver: StateFlow<Boolean> = _dataSaver.asStateFlow()

    fun setDataSaver(enabled: Boolean) {
        _dataSaver.value = enabled
        prefs.edit {putBoolean(KEY_DATA_SAVER, enabled)}
        com.dsann.music.data.innertube.NewPipeStreamExtractor.clearCache()
    }

    // ── Playback bitrate and format ───────────────────────────────────────────
    private val _audioQuality = MutableStateFlow(
        com.dsann.music.data.innertube.NewPipeStreamExtractor.normalizeAudioQuality(
            prefs.getString(KEY_AUDIO_QUALITY, "256 kbps")
        )
    )
    val audioQuality: StateFlow<String> = _audioQuality.asStateFlow()

    private val _downloadAudioFormat = MutableStateFlow(
        com.dsann.music.data.innertube.NewPipeStreamExtractor.normalizeDownloadAudioFormat(
            prefs.getString(KEY_DOWNLOAD_FORMAT, "m4a")
        )
    )
    val downloadAudioFormat: StateFlow<String> = _downloadAudioFormat.asStateFlow()

    private val _downloadBitrate = MutableStateFlow(
        com.dsann.music.data.innertube.NewPipeStreamExtractor.normalizeAudioQuality(
            prefs.getString(KEY_DOWNLOAD_BITRATE, "256 kbps")
        )
    )
    val downloadBitrate: StateFlow<String> = _downloadBitrate.asStateFlow()

    // A value of 0 follows the device's actual display refresh cadence.
    private val _playerFrameRate = MutableStateFlow(AppAnimationFrameRateController.load(context))
    val playerFrameRate: StateFlow<Int> = _playerFrameRate.asStateFlow()

    private val _pageTransitionSpeed = MutableStateFlow(
        prefs.getFloat("page_transition_speed", 1f).coerceIn(0.5f, 1.5f)
    )
    val pageTransitionSpeed: StateFlow<Float> = _pageTransitionSpeed.asStateFlow()

    fun setPageTransitionSpeed(value: Float) {
        val safe = if (value.isFinite()) value.coerceIn(0.5f, 1.5f) else 1f
        _pageTransitionSpeed.value = safe
        prefs.edit { putFloat("page_transition_speed", safe) }
    }

    fun setAudioQuality(quality: String) {
        val normalized = com.dsann.music.data.innertube.NewPipeStreamExtractor
            .normalizeAudioQuality(quality)
        _audioQuality.value = normalized
        prefs.edit { putString(KEY_AUDIO_QUALITY, normalized) }
        com.dsann.music.data.innertube.YouTubeMusic.invalidateAllStreamUrls()
    }

    fun setDownloadAudioFormat(format: String) {
        val normalized = com.dsann.music.data.innertube.NewPipeStreamExtractor.normalizeDownloadAudioFormat(format)
        _downloadAudioFormat.value = normalized
        prefs.edit { putString(KEY_DOWNLOAD_FORMAT, normalized) }
        com.dsann.music.data.innertube.NewPipeStreamExtractor.clearCache()
    }

    fun setDownloadBitrate(quality: String) {
        val normalized = com.dsann.music.data.innertube.NewPipeStreamExtractor.normalizeAudioQuality(quality)
        _downloadBitrate.value = normalized
        prefs.edit { putString(KEY_DOWNLOAD_BITRATE, normalized) }
        com.dsann.music.data.innertube.NewPipeStreamExtractor.clearCache()
    }

    fun setPlayerFrameRate(frameRate: Int) {
        _playerFrameRate.value = AppAnimationFrameRateController.set(context, frameRate)
    }

    // ── Pause Listening History ────────────────────────────────────────────────
    private val _pauseHistory = MutableStateFlow(prefs.getBoolean("pause_history", false))
    val pauseHistory: StateFlow<Boolean> = _pauseHistory.asStateFlow()

    fun setPauseHistory(pause: Boolean) {
        _pauseHistory.value = pause
        prefs.edit {putBoolean("pause_history", pause)}
    }

    // ── Library Paths ────────────────────────────────────────────────────────
    private val _libraryPaths = MutableStateFlow(
        (prefs.getString(KEY_LIBRARY_PATHS, "") ?: "").split("||").filter { it.isNotEmpty() }
    )
    val libraryPaths: StateFlow<List<String>> = _libraryPaths.asStateFlow()

    fun addLibraryPath(path: String) {
        val current = _libraryPaths.value
        if (current.contains(path)) return
        val newList = current + path
        _libraryPaths.value = newList
        prefs.edit {putString(KEY_LIBRARY_PATHS, newList.joinToString("||"))}
    }

    fun removeLibraryPath(path: String) {
        val current = _libraryPaths.value
        val newList = current.filter { it != path }
        _libraryPaths.value = newList
        prefs.edit {putString(KEY_LIBRARY_PATHS, newList.joinToString("||"))}
    }

    // ── Hidden Folders ────────────────────────────────────────────────────────
    private val _hiddenFolders = MutableStateFlow(
        (prefs.getString(KEY_HIDDEN_FOLDERS, "") ?: "").split("||").filter { it.isNotEmpty() }
    )
    val hiddenFolders: StateFlow<List<String>> = _hiddenFolders.asStateFlow()

    fun addHiddenFolder(path: String) {
        val current = _hiddenFolders.value
        if (current.contains(path)) return
        val newList = current + path
        _hiddenFolders.value = newList
        prefs.edit {putString(KEY_HIDDEN_FOLDERS, newList.joinToString("||"))}
    }

    fun removeHiddenFolder(path: String) {
        val current = _hiddenFolders.value
        val newList = current.filter { it != path }
        _hiddenFolders.value = newList
        prefs.edit {putString(KEY_HIDDEN_FOLDERS, newList.joinToString("||"))}
    }

    // ── Cache & Downloads ───────────────────────────────────────────────────
    private val _downloadFolder = MutableStateFlow(prefs.getString("download_folder", null))
    val downloadFolder: StateFlow<String?> = _downloadFolder.asStateFlow()

    fun setDownloadFolder(uri: String?) {
        _downloadFolder.value = uri
        if (uri != null) {
            prefs.edit {putString("download_folder", uri)}
        } else {
            prefs.edit {remove("download_folder")}
        }
    }

    // Sizes in MB. -1 or 0 could mean unlimited, or we can use large numbers. We'll use Int (MB). 0 = unlimited.
    private val _songCacheMaxSize = MutableStateFlow(prefs.getInt("song_cache_max_size", 1024)) // Default 1GB
    val songCacheMaxSize: StateFlow<Int> = _songCacheMaxSize.asStateFlow()

    fun setSongCacheMaxSize(sizeMb: Int) {
        _songCacheMaxSize.value = sizeMb
        prefs.edit {putInt("song_cache_max_size", sizeMb)}
    }

    private val _imageCacheMaxSize = MutableStateFlow(prefs.getInt("image_cache_max_size", 512)) // Default 500MB
    val imageCacheMaxSize: StateFlow<Int> = _imageCacheMaxSize.asStateFlow()

    fun setImageCacheMaxSize(sizeMb: Int) {
        _imageCacheMaxSize.value = sizeMb
        prefs.edit {putInt("image_cache_max_size", sizeMb)}
    }

    // ── Backup & Restore ──────────────────────────────────────────────────────
    private data class BackupPreferences(
        val values: com.google.gson.JsonObject,
        val types: com.google.gson.JsonObject
    )

    private fun encodePreferences(
        source: SharedPreferences,
        includeKeys: Set<String>? = null,
        excludedKeys: Set<String> = emptySet()
    ): BackupPreferences {
        val values = com.google.gson.JsonObject()
        val types = com.google.gson.JsonObject()

        source.all.forEach { (key, value) ->
            if (key in excludedKeys || (includeKeys != null && key !in includeKeys)) {
                return@forEach
            }
            when (value) {
                is Boolean -> {
                    values.addProperty(key, value)
                    types.addProperty(key, "boolean")
                }
                is Float -> {
                    values.addProperty(key, value)
                    types.addProperty(key, "float")
                }
                is Int -> {
                    values.addProperty(key, value)
                    types.addProperty(key, "int")
                }
                is Long -> {
                    values.addProperty(key, value)
                    types.addProperty(key, "long")
                }
                is String -> {
                    values.addProperty(key, value)
                    types.addProperty(key, "string")
                }
                is Set<*> -> {
                    val stringValues = value.filterIsInstance<String>()
                    if (stringValues.size == value.size) {
                        val array = com.google.gson.JsonArray()
                        stringValues.sorted().forEach { array.add(it) }
                        values.add(key, array)
                        types.addProperty(key, "string_set")
                    }
                }
            }
        }
        return BackupPreferences(values, types)
    }

    /**
     * Restore typed SharedPreferences values. The type manifest prevents newer Float
     * settings from being silently converted to Int. When importing an old v1 backup,
     * the existing preference type and the JSON number shape are used as fallbacks.
     */
    private fun restorePreferences(
        target: SharedPreferences,
        values: com.google.gson.JsonObject,
        types: com.google.gson.JsonObject?,
        excludedKeys: Set<String> = emptySet()
    ) {
        val editor = target.edit()
        values.entrySet().forEach { (key, element) ->
            if (key in excludedKeys) return@forEach
            if (element.isJsonNull) {
                editor.remove(key)
                return@forEach
            }

            val declaredType = types?.get(key)
                ?.takeIf { !it.isJsonNull && it.isJsonPrimitive }
                ?.asString
            when {
                declaredType == "string_set" || element.isJsonArray -> {
                    if (element.isJsonArray) {
                        val set = element.asJsonArray.mapNotNull { item ->
                            item.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }
                                ?.asString
                        }.toSet()
                        editor.putStringSet(key, set)
                    }
                }
                element.isJsonPrimitive -> {
                    val primitive = element.asJsonPrimitive
                    when (declaredType ?: when {
                        primitive.isBoolean -> "boolean"
                        primitive.isString -> "string"
                        primitive.isNumber -> when (target.all[key]) {
                            is Float -> "float"
                            is Long -> "long"
                            else -> {
                                val numberText = primitive.asNumber.toString()
                                if (numberText.contains('.') || numberText.contains('e', true)) "float" else "int"
                            }
                        }
                        else -> ""
                    }) {
                        "boolean" -> if (primitive.isBoolean) editor.putBoolean(key, primitive.asBoolean)
                        "float" -> if (primitive.isNumber) editor.putFloat(key, primitive.asFloat)
                        "int" -> if (primitive.isNumber) editor.putInt(key, primitive.asInt)
                        "long" -> if (primitive.isNumber) editor.putLong(key, primitive.asLong)
                        "string" -> if (primitive.isString) editor.putString(key, primitive.asString)
                    }
                }
            }
        }
        if (!editor.commit()) {
            throw IllegalStateException("Could not save restored preferences")
        }
    }

    private val excludedBackupPreferenceKeys = setOf(
        // Authentication cookies are credentials. The receiving device must sign in separately.
        "yt_cookies",
        // This controls how future backups are created; it is not a user setting to restore.
        KEY_BACKUP_INCLUDE_HISTORY
    )

    private val exploreBackupPreferenceKeys = setOf(
        "search_history_list",
        "search_history",
        "speed_dial_songs_json",
        "not_interested_ids",
        "recent_artists_list",
        "last_played_artist",
        "custom_profile_name"
    )

    /** Refresh every settings StateFlow after a restore so the open screen reflects it immediately. */
    private fun refreshSettingsFromPreferences() {
        val themeMode = prefs.getString("theme_mode", "system")
            ?.lowercase()?.takeIf { it in listOf("system", "light", "dark") } ?: "system"
        _themeMode.value = themeMode
        _darkMode.value = if (themeMode == "system") {
            (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        } else {
            themeMode == "dark"
        }

        _crossfadeEnabled.value = prefs.getBoolean(KEY_CROSSFADE_ENABLED, false)
        _crossfadeDuration.value = prefs.getFloat(KEY_CROSSFADE_DURATION, 3f)
        _mediaCacheEnabled.value = prefs.getBoolean(KEY_MEDIA_CACHE_ENABLED, true)
        _mediaCacheLimitGb.value = prefs.getInt(KEY_MEDIA_CACHE_LIMIT_GB, 1).coerceIn(1, 10)
        _audioFocus.value = prefs.getBoolean(KEY_AUDIO_FOCUS, true)
        _autoplayEnabled.value = prefs.getBoolean(KEY_AUTOPLAY_ENABLED, true)
        _amoledBlack.value = prefs.getBoolean(KEY_AMOLED_BLACK, false)
        _hideNavLabels.value = prefs.getBoolean("hide_nav_labels", false)
        _navLabelsMode.value = prefs.getString(
            "nav_label_mode",
            if (_hideNavLabels.value) "Never" else "Always"
        )?.takeIf { it in listOf("Always", "Selected", "Never") } ?: "Always"
        _dynamicNavStyle.value = prefs.getBoolean("dynamic_nav_style", false)
        _navBarStyle.value = prefs.getString("nav_bar_style", "Floating")
            ?.takeIf { it in listOf("Floating", "Docked", "Minimal") } ?: "Floating"
        _navBarSize.value = prefs.getString("nav_bar_size", "Standard")
            ?.takeIf { it in listOf("Compact", "Standard", "Tall") } ?: "Standard"
        _dynamicAlbumTheme.value = prefs.getBoolean(KEY_DYNAMIC_ALBUM_THEME, false)
        _appearancePreset.value = prefs.getString(KEY_APPEARANCE_PRESET, "Ocean") ?: "Ocean"
        _customAccentColor.value = prefs.getString("custom_accent_color", "#7C9CFF") ?: "#7C9CFF"
        _genreGradientStyle.value = prefs.getString("genre_card_gradient_style", "Theme")
            ?.takeIf { it in listOf("Theme", "Aurora", "Ocean", "Sunset", "Forest", "Berry") } ?: "Theme"
        _uiCornerStyle.value = prefs.getString("ui_corner_style", "Soft")
            ?.takeIf { it in listOf("Tight", "Soft", "Round") } ?: "Soft"
        _playerArtworkShape.value = prefs.getString(KEY_PLAYER_ARTWORK_SHAPE, "Rounded") ?: "Rounded"
        _playerBackgroundStyle.value = prefs.getString("player_background_style", "Artwork") ?: "Artwork"
        _nowPlayingCoverAnimation.value = prefs.getString(NowPlayingCoverAnimationPreference.KEY, "Bars")
            ?.takeIf { it in NowPlayingCoverAnimationPreference.choices } ?: "Bars"
        NowPlayingCoverAnimationPreference.set(context, _nowPlayingCoverAnimation.value)
        _miniPlayerStyle.value = prefs.getString("mini_player_style", "Card") ?: "Card"
        _miniPlayerControlShape.value = prefs.getString("mini_player_control_shape", "Circle") ?: "Circle"
        _miniPlayerControlIcons.value = prefs.getString("mini_player_control_icons", "Classic") ?: "Classic"
        _miniPlayerProgressStyle.value = prefs.getString("mini_player_progress", "Ring") ?: "Ring"
        _playerLayout.value = prefs.getString("player_layout", "Standard")
            ?.takeIf { it in listOf("Standard", "Vinyl", "Cassette") } ?: "Standard"
        _playerArtworkScale.value = prefs.getFloat("player_artwork_scale", 1f).coerceIn(0.65f, 1f)
        _playerButtons.value = readPlayerButtons()
        _sessionButtons.value = readSessionButtons()
        _playerTitleAlign.value = prefs.getString("player_title_align", "Left")
            ?.takeIf { it in listOf("Left", "Centered") } ?: "Left"
        _playerTitleSize.value = prefs.getString("player_title_size", "Medium")
            ?.takeIf { it in listOf("Small", "Medium", "Large") } ?: "Medium"
        _playerTitleWeight.value = prefs.getString("player_title_weight", "Bold")
            ?.takeIf { it in listOf("Light", "Regular", "Bold") } ?: "Bold"
        _playerTitleScroll.value = prefs.getBoolean("player_title_scroll", true)
        _playerTimes.value = prefs.getString("player_times", "Both")
            ?.takeIf { it in listOf("Both", "Elapsed", "Time left", "None") } ?: "Both"
        _pageTransitionSpeed.value = prefs.getFloat("page_transition_speed", 1f).coerceIn(0.5f, 1.5f)
        _scrubberStyle.value = prefs.getString("scrubber_style", "Waveform")
            ?.let {
                when (it) {
                    "Wave" -> "Waveform"
                    "Thumb" -> "Medium"
                    else -> it
                }
            }
            ?.takeIf { it in listOf("Hairline", "Bold", "Medium", "Waveform") } ?: "Waveform"
        _scrubberColor.value = prefs.getString("scrubber_color", "Accent") ?: "Accent"
        _visualizerStyle.value = (prefs.getString("player_visualizer", "Off") ?: "Off")
            .let { if (it == "Ring") "Oval" else it }
            .takeIf { it in listOf("Off", "Bars", "Wave", "Oval", "Pulse", "Glow") } ?: "Off"
        _playerButtonsPill.value = prefs.getBoolean("player_buttons_pill", true)
        _playerButtonStyle.value = when (prefs.getString("player_button_style", "Pill") ?: "Pill") {
            "Rounded" -> "Round"
            "Minimal" -> "None"
            "Round", "Pill", "None", "Square" -> prefs.getString("player_button_style", "Pill") ?: "Pill"
            else -> "Pill"
        }
        _scrubberThickness.value = 1.5f
        _scrubberThumbStyle.value = prefs.getString("scrubber_thumb_style", "Auto") ?: "Auto"
        _visualizerColor.value = prefs.getString("visualizer_color", "Accent") ?: "Accent"
        _lyricsTextSize.value = prefs.getString("lyrics_text_size", "Standard") ?: "Standard"
        _showSyncedLyricsInPlayer.value = prefs.getBoolean("show_synced_lyrics_in_player", true)
        _lyricsAlignment.value = prefs.getString("lyrics_alignment", "Original") ?: "Original"
        _lyricsColorStyle.value = when (prefs.getString("lyrics_color_style", "Home Studio Gradient")) {
            "Dynamic (Gradient)" -> "Home Studio Gradient"
            "UI Matching" -> "UI Matching"
            "White" -> "White"
            else -> "Home Studio Gradient"
        }
        _lyricsFontFamily.value = prefs.getString("lyrics_font_family", "Rounded") ?: "Rounded"
        _uiDensity.value = prefs.getString("ui_density", "Comfortable") ?: "Comfortable"
        _taperedSongDividers.value = prefs.getBoolean(KEY_TAPERED_SONG_DIVIDERS, false)
        SongRowDividerPreferences.set(context, _taperedSongDividers.value)
        _uiFontFamily.value = when (prefs.getString("ui_font_family", "Roboto") ?: "Roboto") {
            "Cursive" -> "Cursive"
            "Sans", "Google Sans Flex" -> "Sans"
            "Serif" -> "Serif"
            "Monospace" -> "Monospace"
            else -> "Roboto"
        }
        _uiFontScale.value = prefs.getFloat("ui_font_scale", 1f).coerceIn(0.85f, 1.30f)
        _uiFontWeight.value = prefs.getString("ui_font_weight", "Default") ?: "Default"
        _uiLetterSpacing.value = prefs.getString("ui_letter_spacing", "Normal") ?: "Normal"
        _savedAppearanceLooks.value = readSavedAppearanceLooks()
        _ignoreShortAudio.value = prefs.getBoolean(KEY_IGNORE_SHORT_AUDIO, false)
        _ignoreShortAudioDuration.value = prefs.getFloat(KEY_IGNORE_SHORT_AUDIO_DURATION, 30f)
        _dataSaver.value = prefs.getBoolean(KEY_DATA_SAVER, false)
        _audioQuality.value = com.dsann.music.data.innertube.NewPipeStreamExtractor
            .normalizeAudioQuality(prefs.getString(KEY_AUDIO_QUALITY, "256 kbps"))
        _downloadAudioFormat.value = com.dsann.music.data.innertube.NewPipeStreamExtractor
            .normalizeDownloadAudioFormat(prefs.getString(KEY_DOWNLOAD_FORMAT, "m4a"))
        _downloadBitrate.value = com.dsann.music.data.innertube.NewPipeStreamExtractor
            .normalizeAudioQuality(prefs.getString(KEY_DOWNLOAD_BITRATE, "256 kbps"))
        _playerFrameRate.value = AppAnimationFrameRateController.load(context)
        _pauseHistory.value = prefs.getBoolean("pause_history", false)
        _libraryPaths.value = (prefs.getString(KEY_LIBRARY_PATHS, "") ?: "")
            .split("||").filter { it.isNotEmpty() }
        _hiddenFolders.value = (prefs.getString(KEY_HIDDEN_FOLDERS, "") ?: "")
            .split("||").filter { it.isNotEmpty() }
        _downloadFolder.value = prefs.getString("download_folder", null)
        _songCacheMaxSize.value = prefs.getInt("song_cache_max_size", 1024)
        _imageCacheMaxSize.value = prefs.getInt("image_cache_max_size", 512)

        com.dsann.music.service.AudioPlaybackService.updateMediaCacheSettings(context)
        calculateCacheSize()
    }

    fun exportBackup(
        context: Context,
        uri: Uri,
        includeHistory: Boolean = true,
        onResult: (Boolean, String) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val settings = encodePreferences(
                    prefs,
                    excludedKeys = excludedBackupPreferenceKeys
                )
                val explorePrefs = context.getSharedPreferences("explore_prefs", Context.MODE_PRIVATE)
                val explore = encodePreferences(
                    explorePrefs,
                    includeKeys = exploreBackupPreferenceKeys
                )

                val favoritesList = com.dsann.music.util.FavoritesManager(context)
                    .favoriteIds.toList().sorted()

                val db = com.dsann.music.data.db.MusicDatabase.getDatabase(context)
                val playlistDao = db.playlistDao()
                val playlists = playlistDao.getAllPlaylists().first()
                val playlistSongs = mutableListOf<com.dsann.music.data.db.PlaylistSongEntity>()
                playlists.forEach { playlist ->
                    playlistSongs.addAll(playlistDao.getSongsForPlaylist(playlist.id).first())
                }
                // The DAO orders history by timestamp descending, so take() keeps the
                // newest events and avoids exporting hundreds of repeated artwork URLs.
                val historyEvents = if (includeHistory) {
                    playlistDao.getAllHistoryEventsFlow().first().take(MAX_BACKUP_HISTORY_EVENTS)
                } else null

                val gson = com.google.gson.Gson()
                val backupObject = com.google.gson.JsonObject().apply {
                    addProperty("format", "com.dsann.music.backup")
                    addProperty("version", 2)
                    addProperty("exported_at", System.currentTimeMillis())
                    add("settings", settings.values)
                    add("settings_types", settings.types)
                    add("explore_preferences", explore.values)
                    add("explore_preferences_types", explore.types)

                    val favoritesArray = com.google.gson.JsonArray()
                    favoritesList.forEach { favoritesArray.add(it) }
                    add("favorites", favoritesArray)

                    add("playlists", gson.toJsonTree(playlists))
                    add("playlist_songs", gson.toJsonTree(playlistSongs))
                    // Omit the property entirely when history is excluded. Older importers
                    // already interpret a missing history array as "leave local history alone".
                    historyEvents?.let { add("history_events", gson.toJsonTree(it)) }
                }

                val output = context.contentResolver.openOutputStream(uri, "wt")
                    ?: throw IllegalStateException("Could not open the selected backup file")
                output.use { it.write(backupObject.toString().toByteArray(Charsets.UTF_8)) }

                withContext(Dispatchers.Main) {
                    onResult(
                        true,
                        if (includeHistory) {
                            "Backup exported with settings, favourites, playlists and up to $MAX_BACKUP_HISTORY_EVENTS recent history events"
                        } else {
                            "Backup exported without listening history"
                        }
                    )
                }
            } catch (e: Exception) {
                android.util.Log.e("SettingsViewModel", "Failed to export backup", e)
                withContext(Dispatchers.Main) {
                    onResult(false, "Failed to export: ${e.localizedMessage ?: "Unknown error"}")
                }
            }
        }
    }

    fun importBackup(context: Context, uri: Uri, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val jsonString = context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                } ?: throw IllegalStateException("Could not read backup file")
                if (jsonString.length > 50_000_000) {
                    throw IllegalArgumentException("Backup file is too large")
                }

                val gson = com.google.gson.Gson()
                val parsed = com.google.gson.JsonParser().parse(jsonString)
                if (!parsed.isJsonObject) {
                    throw IllegalArgumentException("This is not a valid Quantune backup file")
                }
                val backupObject = parsed.asJsonObject
                val format = backupObject.get("format")?.takeIf { it.isJsonPrimitive }?.asString
                if (format != null && format != "com.dsann.music.backup") {
                    throw IllegalArgumentException("This backup was created by a different app")
                }
                val version = backupObject.get("version")?.takeIf { it.isJsonPrimitive }?.asInt ?: 1
                if (version !in 1..2) {
                    throw IllegalArgumentException("This backup version is not supported")
                }

                // Parse and validate the complete library before modifying any local data.
                val db = com.dsann.music.data.db.MusicDatabase.getDatabase(context)
                val playlistDao = db.playlistDao()
                val hasPlaylists = backupObject.has("playlists")
                val hasPlaylistSongs = backupObject.has("playlist_songs")
                val hasHistory = backupObject.has("history_events")
                val hasLibraryData = hasPlaylists || hasPlaylistSongs || hasHistory

                val existingPlaylists = if (hasLibraryData && (!hasPlaylists || !hasPlaylistSongs)) {
                    playlistDao.getAllPlaylists().first()
                } else emptyList()

                val playlistType = object : com.google.gson.reflect.TypeToken<List<com.dsann.music.data.db.PlaylistEntity>>() {}.type
                val songType = object : com.google.gson.reflect.TypeToken<List<com.dsann.music.data.db.PlaylistSongEntity>>() {}.type
                val historyType = object : com.google.gson.reflect.TypeToken<List<com.dsann.music.data.db.HistoryEventEntity>>() {}.type

                val playlists: List<com.dsann.music.data.db.PlaylistEntity> = if (hasPlaylists) {
                    gson.fromJson(backupObject.get("playlists"), playlistType)
                        ?: throw IllegalArgumentException("The backup playlist list is invalid")
                } else existingPlaylists

                val playlistSongs: List<com.dsann.music.data.db.PlaylistSongEntity> = if (hasPlaylistSongs) {
                    gson.fromJson(backupObject.get("playlist_songs"), songType)
                        ?: throw IllegalArgumentException("The backup playlist songs are invalid")
                } else {
                    existingPlaylists.flatMap { playlist ->
                        playlistDao.getSongsForPlaylist(playlist.id).first()
                    }
                }

                val historyEvents: List<com.dsann.music.data.db.HistoryEventEntity>? = if (hasHistory) {
                    gson.fromJson(backupObject.get("history_events"), historyType)
                        ?: throw IllegalArgumentException("The backup listening history is invalid")
                } else null

                val favoritesToRestore: Set<String>? = if (backupObject.has("favorites")) {
                    val favoritesElement = backupObject.get("favorites")
                    if (!favoritesElement.isJsonArray) {
                        throw IllegalArgumentException("The backup favourites list is invalid")
                    }
                    favoritesElement.asJsonArray.mapNotNull { element ->
                        element.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }
                            ?.asString?.takeIf { it.isNotBlank() }
                    }.toSet()
                } else null

                val settingsObject = backupObject.get("settings")
                    ?.takeIf { it.isJsonObject }?.asJsonObject
                val settingsTypes = backupObject.get("settings_types")
                    ?.takeIf { it.isJsonObject }?.asJsonObject
                val exploreObject = backupObject.get("explore_preferences")
                    ?.takeIf { it.isJsonObject }?.asJsonObject
                val exploreTypes = backupObject.get("explore_preferences_types")
                    ?.takeIf { it.isJsonObject }?.asJsonObject

                if (hasLibraryData) {
                    playlistDao.restoreBackupLibrary(playlists, playlistSongs, historyEvents)
                }

                if (settingsObject != null) {
                    restorePreferences(
                        prefs,
                        settingsObject,
                        settingsTypes,
                        excludedKeys = excludedBackupPreferenceKeys
                    )
                }

                if (exploreObject != null) {
                    val explorePrefs = context.getSharedPreferences("explore_prefs", Context.MODE_PRIVATE)
                    val editor = explorePrefs.edit()
                    exploreBackupPreferenceKeys.forEach { editor.remove(it) }
                    if (!editor.commit()) {
                        throw IllegalStateException("Could not prepare Explore preferences for restore")
                    }
                    restorePreferences(explorePrefs, exploreObject, exploreTypes)
                }

                if (favoritesToRestore != null) {
                    val favoritesPrefs = context.getSharedPreferences("dsmusic_favorites", Context.MODE_PRIVATE)
                    val saved = favoritesPrefs.edit()
                        .putStringSet("favorite_song_ids", favoritesToRestore)
                        .commit()
                    if (!saved) throw IllegalStateException("Could not restore favourites")
                }

                withContext(Dispatchers.Main) {
                    refreshSettingsFromPreferences()
                    onResult(true, "Backup imported: settings, customisation, favourites, playlists and history restored")
                }
            } catch (e: Exception) {
                android.util.Log.e("SettingsViewModel", "Failed to import backup", e)
                withContext(Dispatchers.Main) {
                    onResult(false, "Import failed: ${e.localizedMessage ?: "Unknown error"}")
                }
            }
        }
    }

    // ── Cache ────────────────────────────────────────────────────────────────
    private val _songCacheSize = MutableStateFlow("Calculating...")
    val songCacheSize: StateFlow<String> = _songCacheSize.asStateFlow()

    private val _imageCacheSize = MutableStateFlow("Calculating...")
    val imageCacheSize: StateFlow<String> = _imageCacheSize.asStateFlow()

    fun calculateCacheSize() {
        viewModelScope.launch(Dispatchers.IO) {
            val imageCacheDir = File(context.cacheDir, "image_cache")
            val imageSize = getDirSize(imageCacheDir)
            
            var totalCacheSize = getDirSize(context.cacheDir)
            totalCacheSize += getDirSize(context.codeCacheDir)
            // Online audio and its artwork are deliberately stored under filesDir so Android's
            // cache cleaner cannot evict them. Include those managed stores in the real totals.
            totalCacheSize += getDirSize(File(context.filesDir, "media_cache"))
            totalCacheSize += getDirSize(File(context.filesDir, "media_artwork"))
            val songSize = (totalCacheSize - imageSize).coerceAtLeast(0L)

            _imageCacheSize.value = formatFileSize(imageSize)
            _songCacheSize.value = formatFileSize(songSize)
        }
    }

    fun clearSongCache(onComplete: () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            // Clear the persistent Media3 stream cache and its song artwork before deleting
            // disposable cache directories. Deleting cacheDir alone misses filesDir/media_cache.
            com.dsann.music.service.AudioPlaybackService.clearMediaCache(context.applicationContext)
            context.cacheDir.listFiles()?.forEach { file ->
                if (file.name != "image_cache") {
                    deleteDir(file)
                }
            }
            deleteDir(context.codeCacheDir)
            calculateCacheSize()
            withContext(Dispatchers.Main) { onComplete() }
        }
    }

    fun clearImageCache(onComplete: () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val imageCacheDir = File(context.cacheDir, "image_cache")
            deleteDir(imageCacheDir)
            calculateCacheSize()
            withContext(Dispatchers.Main) { onComplete() }
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────
    private fun getDirSize(dir: File?): Long {
        if (dir == null || !dir.exists()) return 0
        var size = 0L
        dir.listFiles()?.forEach { file ->
            size += if (file.isFile) file.length() else getDirSize(file)
        }
        return size
    }

    private fun formatFileSize(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB")
        val digitGroups =
            (log10(bytes.toDouble()) / log10(1024.0)).toInt()
                .coerceAtMost(units.size - 1)
        return DecimalFormat("#.##")
            .format(bytes / 1024.0.pow(digitGroups.toDouble())) + " " + units[digitGroups]
    }

    private fun deleteDir(dir: File?) {
        if (dir == null || !dir.exists()) return
        dir.listFiles()?.forEach { file ->
            if (file.isDirectory) deleteDir(file)
            file.delete()
        }
    }

    companion object {
        const val PREFS_NAME = "AppSettings"
        const val KEY_BACKUP_INCLUDE_HISTORY = "backup_include_listening_history"
        const val KEY_TAPERED_SONG_DIVIDERS = "tapered_song_dividers"
        const val KEY_DARK_MODE = "dark_mode"
        const val KEY_CROSSFADE_ENABLED = "crossfade_enabled"
        const val KEY_CROSSFADE_DURATION = "crossfade_duration"
        const val KEY_AUDIO_FOCUS = "audio_focus"
        const val KEY_MEDIA_CACHE_ENABLED = "media_cache_enabled"
        const val KEY_MEDIA_CACHE_LIMIT_GB = "media_cache_limit_gb"
        const val KEY_LIBRARY_PATHS = "library_paths"
        const val KEY_HIDDEN_FOLDERS = "hidden_folders"
        const val KEY_AMOLED_BLACK = "amoled_black"
        const val KEY_IGNORE_SHORT_AUDIO = "ignore_short_audio"
        const val KEY_IGNORE_SHORT_AUDIO_DURATION = "ignore_short_audio_duration"
        const val KEY_DATA_SAVER = "data_saver"
        const val KEY_AUDIO_QUALITY = "audio_quality"
        const val KEY_DOWNLOAD_FORMAT = "download_audio_format"
        const val KEY_DOWNLOAD_BITRATE = "download_bitrate"
        const val KEY_SYNC_FREQUENCY = "sync_frequency"
        const val KEY_DYNAMIC_ALBUM_THEME = "dynamic_album_theme"
        const val KEY_APPEARANCE_PRESET = "appearance_preset"
        const val KEY_PLAYER_ARTWORK_SHAPE = "player_artwork_shape"
        const val KEY_AUTOPLAY_ENABLED = "autoplay_enabled"
        const val KEY_VOLUME_NORMALIZATION_ENABLED = "volume_normalization_enabled"
        const val KEY_TARGET_LUFS = "target_lufs"
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// SETTINGS FRAGMENT
// ═══════════════════════════════════════════════════════════════════════════════

// ═══════════════════════════════════════════════════════════════════════════════
// COMPOSE UI — ENTRY POINT
// ═══════════════════════════════════════════════════════════════════════════════

private fun getPathFromUri(uri: Uri): String {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && android.provider.DocumentsContract.isTreeUri(uri)) {
        val docId = android.provider.DocumentsContract.getTreeDocumentId(uri)
        val split = docId.split(":")
        if (split.size >= 2) {
            val type = split[0]
            val relativePath = split[1]
            return if ("primary" == type.lowercase()) {
                android.os.Environment.getExternalStorageDirectory().toString() + "/" + relativePath
            } else {
                "/storage/$type/$relativePath"
            }
        }
    }
    return uri.path ?: uri.toString()
}

private fun Context.findActivity(): Activity? {
    var context = this
    while (context is ContextWrapper) {
        if (context is Activity) return context
        context = context.baseContext
    }
    return null
}

@Composable
fun SettingsScreenContent(
    viewModel: SettingsViewModel = androidx.lifecycle.viewmodel.compose.viewModel(LocalContext.current.findActivity() as androidx.activity.ComponentActivity),
    playerSharedViewModel: PlayerSharedViewModel = androidx.lifecycle.viewmodel.compose.viewModel(LocalContext.current.findActivity() as androidx.activity.ComponentActivity)
) {
    val darkMode by viewModel.darkMode.collectAsStateWithLifecycle()
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val isPlayerExpanded by playerSharedViewModel.isPlayerExpanded.collectAsStateWithLifecycle()
    val playerExpansionFraction by playerSharedViewModel.playerExpansionFraction.collectAsStateWithLifecycle()
    val isPlayerBackActive = isPlayerExpanded || playerExpansionFraction > 0.01f
    val amoledBlack by viewModel.amoledBlack.collectAsStateWithLifecycle()
    val dynamicAlbumTheme by viewModel.dynamicAlbumTheme.collectAsStateWithLifecycle()
    val savedAppearanceLooks by viewModel.savedAppearanceLooks.collectAsStateWithLifecycle()
    val appearancePreset by viewModel.appearancePreset.collectAsStateWithLifecycle()
    val customAccentColor by viewModel.customAccentColor.collectAsStateWithLifecycle()
    val genreGradientStyle by viewModel.genreGradientStyle.collectAsStateWithLifecycle()
    val uiCornerStyle by viewModel.uiCornerStyle.collectAsStateWithLifecycle()
    val playerArtworkShape by viewModel.playerArtworkShape.collectAsStateWithLifecycle()
    val playerArtworkScale by viewModel.playerArtworkScale.collectAsStateWithLifecycle()
    val playerButtonsPill by viewModel.playerButtonsPill.collectAsStateWithLifecycle()
    val playerBackgroundStyle by viewModel.playerBackgroundStyle.collectAsStateWithLifecycle()
    val nowPlayingCoverAnimation by viewModel.nowPlayingCoverAnimation.collectAsStateWithLifecycle()
    val miniPlayerStyle by viewModel.miniPlayerStyle.collectAsStateWithLifecycle()
    val miniPlayerControlShape by viewModel.miniPlayerControlShape.collectAsStateWithLifecycle()
    val miniPlayerControlIcons by viewModel.miniPlayerControlIcons.collectAsStateWithLifecycle()
    val miniPlayerProgressStyle by viewModel.miniPlayerProgressStyle.collectAsStateWithLifecycle()
    val playerLayout by viewModel.playerLayout.collectAsStateWithLifecycle()
    val playerButtons by viewModel.playerButtons.collectAsStateWithLifecycle()
    val sessionButtons by viewModel.sessionButtons.collectAsStateWithLifecycle()
    val playerTitleAlign by viewModel.playerTitleAlign.collectAsStateWithLifecycle()
    val playerTitleSize by viewModel.playerTitleSize.collectAsStateWithLifecycle()
    val playerTitleWeight by viewModel.playerTitleWeight.collectAsStateWithLifecycle()
    val playerTitleScroll by viewModel.playerTitleScroll.collectAsStateWithLifecycle()
    val playerTimes by viewModel.playerTimes.collectAsStateWithLifecycle()
    val scrubberStyle by viewModel.scrubberStyle.collectAsStateWithLifecycle()
    val scrubberColor by viewModel.scrubberColor.collectAsStateWithLifecycle()
    val visualizerStyle by viewModel.visualizerStyle.collectAsStateWithLifecycle()
    val playerButtonStyle by viewModel.playerButtonStyle.collectAsStateWithLifecycle()
    val scrubberThumbStyle by viewModel.scrubberThumbStyle.collectAsStateWithLifecycle()
    val visualizerColor by viewModel.visualizerColor.collectAsStateWithLifecycle()
    val lyricsTextSize by viewModel.lyricsTextSize.collectAsStateWithLifecycle()
    val showSyncedLyricsInPlayer by viewModel.showSyncedLyricsInPlayer.collectAsStateWithLifecycle()
    val lyricsAlignment by viewModel.lyricsAlignment.collectAsStateWithLifecycle()
    val lyricsFontFamily by viewModel.lyricsFontFamily.collectAsStateWithLifecycle()
    val lyricsColorStyle by viewModel.lyricsColorStyle.collectAsStateWithLifecycle()
    val uiDensity by viewModel.uiDensity.collectAsStateWithLifecycle()
    val taperedSongDividers by viewModel.taperedSongDividers.collectAsStateWithLifecycle()
    val uiFontFamily by viewModel.uiFontFamily.collectAsStateWithLifecycle()
    val uiFontScale by viewModel.uiFontScale.collectAsStateWithLifecycle()
    val uiFontWeight by viewModel.uiFontWeight.collectAsStateWithLifecycle()
    val uiLetterSpacing by viewModel.uiLetterSpacing.collectAsStateWithLifecycle()
    val autoplayEnabled by viewModel.autoplayEnabled.collectAsStateWithLifecycle()
    val navLabelsMode by viewModel.navLabelsMode.collectAsStateWithLifecycle()
    val dynamicNavStyle by viewModel.dynamicNavStyle.collectAsStateWithLifecycle()
    val navBarStyle by viewModel.navBarStyle.collectAsStateWithLifecycle()
    val navBarSize by viewModel.navBarSize.collectAsStateWithLifecycle()
    val crossfadeEnabled by viewModel.crossfadeEnabled.collectAsStateWithLifecycle()
    val crossfadeDuration by viewModel.crossfadeDuration.collectAsStateWithLifecycle()
    val audioFocus by viewModel.audioFocus.collectAsStateWithLifecycle()
    val sleepTimerEndTime by playerSharedViewModel.sleepTimerEndTime.collectAsStateWithLifecycle()
    val sleepTimerMode by playerSharedViewModel.sleepTimerMode.collectAsStateWithLifecycle()
    val libraryPaths by viewModel.libraryPaths.collectAsStateWithLifecycle()
    val songCacheSize by viewModel.songCacheSize.collectAsStateWithLifecycle()
    val imageCacheSize by viewModel.imageCacheSize.collectAsStateWithLifecycle()

    val ignoreShortAudio by viewModel.ignoreShortAudio.collectAsStateWithLifecycle()
    val ignoreShortAudioDuration by viewModel.ignoreShortAudioDuration.collectAsStateWithLifecycle()
    val hiddenFolders by viewModel.hiddenFolders.collectAsStateWithLifecycle()

    val dataSaver by viewModel.dataSaver.collectAsStateWithLifecycle()
    val audioQuality by viewModel.audioQuality.collectAsStateWithLifecycle()
    val downloadAudioFormat by viewModel.downloadAudioFormat.collectAsStateWithLifecycle()
    val downloadBitrate by viewModel.downloadBitrate.collectAsStateWithLifecycle()
    val playerFrameRate by viewModel.playerFrameRate.collectAsStateWithLifecycle()
    val pageTransitionSpeed by viewModel.pageTransitionSpeed.collectAsStateWithLifecycle()
    val pauseHistory by viewModel.pauseHistory.collectAsStateWithLifecycle()
    val downloadFolder by viewModel.downloadFolder.collectAsStateWithLifecycle()
    val songCacheMaxSize by viewModel.songCacheMaxSize.collectAsStateWithLifecycle()
    val imageCacheMaxSize by viewModel.imageCacheMaxSize.collectAsStateWithLifecycle()
    val mediaCacheEnabled by viewModel.mediaCacheEnabled.collectAsStateWithLifecycle()
    val mediaCacheLimitGb by viewModel.mediaCacheLimitGb.collectAsStateWithLifecycle()


    val ytCookies by viewModel.ytCookies.collectAsStateWithLifecycle()
    val includeListeningHistoryInBackup by viewModel.includeListeningHistoryInBackup.collectAsStateWithLifecycle()

    val context = LocalContext.current

    val directoryPickerLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let {
            context.contentResolver.takePersistableUriPermission(
                it, Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            viewModel.addLibraryPath(it.toString())
        }
    }

    val downloadFolderPickerLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let {
            context.contentResolver.takePersistableUriPermission(
                it, Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            val path = getPathFromUri(it)
            viewModel.setDownloadFolder(path)
        }
    }

    val exportBackupLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri?.let {
            viewModel.exportBackup(context, it, includeHistory = includeListeningHistoryInBackup) { _, msg ->
                (context as? MainActivity)?.showSnackbar(msg, 0)
            }
        }
    }

    val importBackupLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            viewModel.importBackup(context, it) { success, msg ->
                if (success) {
                    playerSharedViewModel.refreshFavorites()
                    playerSharedViewModel.refreshPlayerGestureSettings()
                    val activity = context.findActivity()
                    if (activity is androidx.fragment.app.FragmentActivity) {
                        androidx.lifecycle.ViewModelProvider(activity)[
                            com.dsann.music.viewmodel.ExploreViewModel::class.java
                        ].reloadRestoredPreferences()
                    }
                }
                (context as? MainActivity)?.showSnackbar(msg, 0)
            }
        }
    }

    val hiddenFolderPickerLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let {
            try {
                context.contentResolver.takePersistableUriPermission(
                    it, Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
                val path = getPathFromUri(it)
                viewModel.addHiddenFolder(path)
            } catch (e: Exception) {
                android.util.Log.e("SettingsScreenContent", "Failed to resolve hidden folder path", e)
            }
        }
    }



    LaunchedEffect(Unit) { viewModel.calculateCacheSize() }

    val pageTransitionDurationMillis = LocalPageTransitionDurationMillis.current
    val navController = rememberNavController()
    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val currentSettingsRoute = currentBackStackEntry?.destination?.route
    val canPop = currentSettingsRoute != SettingsRoute.Main.route
    var settingsRouteRestored by remember(navController) { mutableStateOf(false) }

    // The nested NavController normally restores its complete back stack itself.
    // If the host is rebuilt without that saved entry (for example during an older
    // Android theme/configuration change), restore the last open Settings page.
    LaunchedEffect(navController) {
        val savedRoute = viewModel.currentSettingsRoute.value
        val activeRoute = navController.currentDestination?.route
        if (
            (activeRoute == null || activeRoute == SettingsRoute.Main.route) &&
            savedRoute != SettingsRoute.Main.route
        ) {
            navController.navigate(savedRoute) {
                launchSingleTop = true
            }
        }
        settingsRouteRestored = true
    }

    // Update the restoration anchor only after initial restoration, so the temporary
    // "main" start destination cannot overwrite the page that was previously open.
    LaunchedEffect(currentSettingsRoute, settingsRouteRestored) {
        if (settingsRouteRestored) {
            currentSettingsRoute?.let(viewModel::setCurrentSettingsRoute)
        }
    }

    NavHost(
        navController = navController,
        startDestination = SettingsRoute.Main.route,
        // Open settings sections with a right-to-left slide; Back reverses it.
        enterTransition = {
            androidx.compose.animation.slideInHorizontally(
                androidx.compose.animation.core.tween(
                    durationMillis = pageTransitionDurationMillis,
                    easing = androidx.compose.animation.core.FastOutSlowInEasing
                )
            ) { it }
        },
        exitTransition = {
            androidx.compose.animation.slideOutHorizontally(
                androidx.compose.animation.core.tween(
                    durationMillis = pageTransitionDurationMillis,
                    easing = androidx.compose.animation.core.FastOutSlowInEasing
                )
            ) { -it }
        },
        popEnterTransition = {
            androidx.compose.animation.slideInHorizontally(
                androidx.compose.animation.core.tween(
                    durationMillis = pageTransitionDurationMillis,
                    easing = androidx.compose.animation.core.FastOutSlowInEasing
                )
            ) { -it }
        },
        popExitTransition = {
            androidx.compose.animation.slideOutHorizontally(
                androidx.compose.animation.core.tween(
                    durationMillis = pageTransitionDurationMillis,
                    easing = androidx.compose.animation.core.FastOutSlowInEasing
                )
            ) { it }
        }
    ) {
        composable(SettingsRoute.Main.route) {
            SettingsMainScreen(navController = navController)
        }
        composable(SettingsRoute.MusicManagement.route) {
            MusicManagementScreen(
                navController = navController,
                libraryPaths = libraryPaths,
                onRemovePath = { viewModel.removeLibraryPath(it) },
                onAddPathClick = { directoryPickerLauncher.launch(null) },
                songCacheSize = songCacheSize,
                imageCacheSize = imageCacheSize,
                onClearSongCache = {
                    viewModel.clearSongCache {
                        (context as? MainActivity)?.showSnackbar("Song and audio cache cleared", 0)
                    }
                },
                onClearImageCache = {
                    viewModel.clearImageCache {
                        (context as? MainActivity)?.showSnackbar("Temporary image files cleared", 0)
                    }
                },
                songCacheMaxSize = songCacheMaxSize,
                onSongCacheMaxSizeChange = { viewModel.setSongCacheMaxSize(it) },
                imageCacheMaxSize = imageCacheMaxSize,
                onImageCacheMaxSizeChange = { viewModel.setImageCacheMaxSize(it) },
                mediaCacheEnabled = mediaCacheEnabled,
                onMediaCacheToggle = { viewModel.setMediaCacheEnabled(it) },
                mediaCacheLimitGb = mediaCacheLimitGb,
                onMediaCacheLimitChange = { viewModel.setMediaCacheLimitGb(it) },
                downloadFolder = downloadFolder,
                onDownloadFolderClick = { downloadFolderPickerLauncher.launch(null) },
                ignoreShortAudio = ignoreShortAudio,
                onIgnoreShortAudioChange = { viewModel.setIgnoreShortAudio(it) },
                ignoreShortAudioDuration = ignoreShortAudioDuration,
                onIgnoreShortAudioDurationChange = { viewModel.setIgnoreShortAudioDuration(it) },
                hiddenFolders = hiddenFolders,
                onAddHiddenFolderClick = { hiddenFolderPickerLauncher.launch(null) },
                onRemoveHiddenFolder = { viewModel.removeHiddenFolder(it) }
            )
        }
        composable(SettingsRoute.BackupRestore.route) {
            BackupRestoreScreen(
                navController = navController,
                onBackupClick = { exportBackupLauncher.launch("Quantune-backup.json") },
                onRestoreClick = { importBackupLauncher.launch(arrayOf("application/json")) },
                includeListeningHistory = includeListeningHistoryInBackup,
                onIncludeListeningHistoryChange = viewModel::setIncludeListeningHistoryInBackup
            )
        }
        composable(SettingsRoute.Account.route) {
            AccountScreen(
                navController = navController,
                ytCookies = ytCookies,
                onYtCookiesChange = { viewModel.setYtCookies(it) },
                dataSaver = dataSaver,
                onDataSaverChange = { viewModel.setDataSaver(it) },
                pauseHistory = pauseHistory,
                onPauseHistoryChange = { viewModel.setPauseHistory(it) }
            )
        }
        composable(SettingsRoute.Appearance.route) {
            AppearanceScreen(
                navController = navController,
                darkMode = darkMode,
                themeMode = themeMode,
                onThemeModeChange = { viewModel.setThemeMode(it) },
                onDarkModeChange = { viewModel.setDarkMode(it) },
                amoledBlack = amoledBlack,
                onAmoledBlackChange = { viewModel.setAmoledBlack(it) },
                dynamicAlbumTheme = dynamicAlbumTheme,
                onDynamicAlbumThemeChange = { viewModel.setDynamicAlbumTheme(it) },
                savedLooks = savedAppearanceLooks,
                onSaveLook = { viewModel.saveAppearanceLook(it) },
                onApplyLook = {
                    viewModel.applyAppearanceLook(it)
                    playerSharedViewModel.audioService?.updateMediaSessionCustomLayout()
                },
                onDeleteLook = { viewModel.deleteAppearanceLook(it) },
                appearancePreset = appearancePreset,
                onAppearancePresetChange = { viewModel.setAppearancePreset(it) },
                customAccentColor = customAccentColor,
                onCustomAccentColorChange = { viewModel.setCustomAccentColor(it) },
                genreGradientStyle = genreGradientStyle,
                onGenreGradientStyleChange = { viewModel.setGenreGradientStyle(it) },
                uiCornerStyle = uiCornerStyle,
                onUiCornerStyleChange = { viewModel.setUiCornerStyle(it) },
                playerArtworkShape = playerArtworkShape,
                onPlayerArtworkShapeChange = { viewModel.setPlayerArtworkShape(it) },
                playerArtworkScale = playerArtworkScale,
                onPlayerArtworkScaleChange = { viewModel.setPlayerArtworkScale(it) },
                playerButtonsPill = playerButtonsPill,
                onPlayerButtonsPillChange = { viewModel.setPlayerButtonsPill(it) },
                playerBackgroundStyle = playerBackgroundStyle,
                onPlayerBackgroundStyleChange = { viewModel.setPlayerBackgroundStyle(it) },
                nowPlayingCoverAnimation = nowPlayingCoverAnimation,
                onNowPlayingCoverAnimationChange = { viewModel.setNowPlayingCoverAnimation(it) },
                miniPlayerStyle = miniPlayerStyle,
                onMiniPlayerStyleChange = { viewModel.setMiniPlayerStyle(it) },
                miniPlayerControlShape = miniPlayerControlShape,
                onMiniPlayerControlShapeChange = { viewModel.setMiniPlayerControlShape(it) },
                miniPlayerControlIcons = miniPlayerControlIcons,
                onMiniPlayerControlIconsChange = { viewModel.setMiniPlayerControlIcons(it) },
                miniPlayerProgressStyle = miniPlayerProgressStyle,
                onMiniPlayerProgressStyleChange = { viewModel.setMiniPlayerProgressStyle(it) },
                lyricsTextSize = lyricsTextSize,
                onLyricsTextSizeChange = { viewModel.setLyricsTextSize(it) },
                showSyncedLyricsInPlayer = showSyncedLyricsInPlayer,
                onShowSyncedLyricsInPlayerChange = { viewModel.setShowSyncedLyricsInPlayer(it) },
                lyricsAlignment = lyricsAlignment,
                onLyricsAlignmentChange = { viewModel.setLyricsAlignment(it) },
                lyricsFontFamily = lyricsFontFamily,
                onLyricsFontFamilyChange = { viewModel.setLyricsFontFamily(it) },
                lyricsColorStyle = lyricsColorStyle,
                onLyricsColorStyleChange = { viewModel.setLyricsColorStyle(it) },
                uiDensity = uiDensity,
                onUiDensityChange = { viewModel.setUiDensity(it) },
                taperedSongDividers = taperedSongDividers,
                onTaperedSongDividersChange = { viewModel.setTaperedSongDividers(it) },
                uiFontFamily = uiFontFamily,
                onUiFontFamilyChange = { viewModel.setUiFontFamily(it) },
                uiFontScale = uiFontScale,
                onUiFontScaleChange = { viewModel.setUiFontScale(it) },
                uiFontWeight = uiFontWeight,
                onUiFontWeightChange = { viewModel.setUiFontWeight(it) },
                uiLetterSpacing = uiLetterSpacing,
                onUiLetterSpacingChange = { viewModel.setUiLetterSpacing(it) },
                navLabelsMode = navLabelsMode,
                onNavLabelsModeChange = { viewModel.setNavLabelsMode(it) },
                dynamicNavStyle = dynamicNavStyle,
                onDynamicNavStyleChange = { viewModel.setDynamicNavStyle(it) },
                navBarStyle = navBarStyle,
                onNavBarStyleChange = { viewModel.setNavBarStyle(it) },
                navBarSize = navBarSize,
                onNavBarSizeChange = { viewModel.setNavBarSize(it) },
                playerLayout = playerLayout,
                onPlayerLayoutChange = { viewModel.setPlayerLayout(it) },
                playerButtons = playerButtons,
                onPlayerButtonsChange = { viewModel.setPlayerButtons(it) },
                onMovePlayerButton = { from, to -> viewModel.movePlayerButton(from, to) },
                sessionButtons = sessionButtons,
                onSessionButtonsChange = {
                    viewModel.setSessionButtons(it)
                    playerSharedViewModel.audioService?.updateMediaSessionCustomLayout()
                },
                onMoveSessionButton = { from, to ->
                    viewModel.moveSessionButton(from, to)
                    playerSharedViewModel.audioService?.updateMediaSessionCustomLayout()
                },
                playerTitleAlign = playerTitleAlign,
                onPlayerTitleAlignChange = { viewModel.setPlayerTitleAlign(it) },
                playerTitleSize = playerTitleSize,
                onPlayerTitleSizeChange = { viewModel.setPlayerTitleSize(it) },
                playerTitleWeight = playerTitleWeight,
                onPlayerTitleWeightChange = { viewModel.setPlayerTitleWeight(it) },
                playerTitleScroll = playerTitleScroll,
                onPlayerTitleScrollChange = { viewModel.setPlayerTitleScroll(it) },
                playerTimes = playerTimes,
                onPlayerTimesChange = { viewModel.setPlayerTimes(it) },
                scrubberStyle = scrubberStyle,
                onScrubberStyleChange = { viewModel.setScrubberStyle(it) },
                scrubberColor = scrubberColor,
                onScrubberColorChange = { viewModel.setScrubberColor(it) },
                visualizerStyle = visualizerStyle,
                onVisualizerStyleChange = { viewModel.setVisualizerStyle(it) },
                playerButtonStyle = playerButtonStyle,
                onPlayerButtonStyleChange = { viewModel.setPlayerButtonStyle(it) },
                scrubberThumbStyle = scrubberThumbStyle,
                onScrubberThumbStyleChange = { viewModel.setScrubberThumbStyle(it) },
                visualizerColor = visualizerColor,
                onVisualizerColorChange = { viewModel.setVisualizerColor(it) }
            )
        }
        composable(SettingsRoute.Controls.route) {
            ControlsAndGesturesScreen(
                navController = navController,
                playerViewModel = playerSharedViewModel
            )
        }
        composable(SettingsRoute.Playback.route) {
            PlaybackScreen(
                navController = navController,
                crossfadeEnabled = crossfadeEnabled,
                onCrossfadeToggle = { viewModel.setCrossfadeEnabled(it) },
                crossfadeDuration = crossfadeDuration,
                onCrossfadeDurationChange = { viewModel.setCrossfadeDuration(it) },
                audioFocus = audioFocus,
                onAudioFocusToggle = { viewModel.setAudioFocus(it) },
                autoplayEnabled = autoplayEnabled,
                onAutoplayToggle = { enabled ->
                    viewModel.setAutoplayEnabled(enabled)
                    if (!enabled) playerSharedViewModel.removeAutoplayRecommendationsFromQueue()
                },
                sleepTimerEndTime = sleepTimerEndTime,
                sleepTimerMode = sleepTimerMode,
                onStartSleepTimer = { playerSharedViewModel.startCustomSleepTimer(it) },
                onCancelSleepTimer = { playerSharedViewModel.cancelSleepTimer() },
                onSetEndOfSong = { enable ->
                    if (enable) playerSharedViewModel.setSleepTimerMode(PlayerSharedViewModel.SleepTimerMode.END_OF_SONG)
                    else playerSharedViewModel.cancelSleepTimer()
                },
                audioQuality = audioQuality,
                onAudioQualityChange = { viewModel.setAudioQuality(it) },
                downloadAudioFormat = downloadAudioFormat,
                onDownloadAudioFormatChange = { viewModel.setDownloadAudioFormat(it) },
                downloadBitrate = downloadBitrate,
                onDownloadBitrateChange = { viewModel.setDownloadBitrate(it) },
                playerFrameRate = playerFrameRate,
                onPlayerFrameRateChange = { viewModel.setPlayerFrameRate(it) },
                pageTransitionSpeed = pageTransitionSpeed,
                onPageTransitionSpeedChange = { viewModel.setPageTransitionSpeed(it) },
            )
        }
        composable(SettingsRoute.About.route) {
            AboutScreen(
                navController = navController,
                onCheckUpdate = {
                    (context as? Activity)?.let { activity ->
                        (context as? MainActivity)?.updateManager?.checkForUpdate(
                            activity.findViewById(android.R.id.content),
                            BuildConfig.VERSION_NAME
                        )
                    }
                },
                onWhatsNew = { navController.navigate(SettingsRoute.WhatsNew.route) },
                onOpenUrl = { url ->
                    context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
                }
            )
        }
        composable(SettingsRoute.Feedback.route) {
            FeedbackScreen(navController = navController)
        }
        composable(SettingsRoute.WhatsNew.route) {
            WhatsNewScreen(navController = navController)
        }
    }

    // The Activity/player layer owns Back while the full player is visible.
    // Do not let a Settings subsection consume that same press and pop its route.
    androidx.activity.compose.BackHandler(enabled = canPop && !isPlayerBackActive) {
        navController.popBackStack()
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// COMPOSE UI — MAIN SETTINGS BODY
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun SettingsDetailTopBar(title: String, onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(DsmusicTheme.colorScheme.background)
            .statusBarsPadding()
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        DsmusicPageHeading(
            title = title,
            subtitle = "Settings",
            kicker = "QUANTUNE",
            modifier = Modifier.weight(1f)
        )
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(13.dp))
                .background(DsmusicTheme.colorScheme.surfaceContainerLow)
                .border(
                    1.dp,
                    DsmusicTheme.colorScheme.outlineVariant.copy(alpha = 0.42f),
                    RoundedCornerShape(13.dp)
                )
                .clickable(onClick = onBack),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = "Back",
                tint = DsmusicTheme.colorScheme.onSurface,
                modifier = Modifier.size(21.dp)
            )
        }
    }
}

sealed class SettingsRoute(val route: String) {
    object Main : SettingsRoute("main")
    object MusicManagement : SettingsRoute("music_management")
    object Account : SettingsRoute("account")
    object Appearance : SettingsRoute("appearance")
    object Playback : SettingsRoute("playback")
    object Controls : SettingsRoute("controls")
    object About : SettingsRoute("about")
    object Feedback : SettingsRoute("feedback")
    object WhatsNew : SettingsRoute("whats_new")
    object BackupRestore : SettingsRoute("backup_restore")
}

@Composable
private fun ControlsAndGesturesScreen(
    navController: androidx.navigation.NavController,
    playerViewModel: PlayerSharedViewModel
) {
    val gestures by playerViewModel.playerGestureSettings.collectAsStateWithLifecycle()
    Scaffold(
        topBar = { SettingsDetailTopBar("Controls & Gestures") { navController.popBackStack() } }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp)
                .padding(bottom = 120.dp)
        ) {
            SettingsAccordionSection("Player cover") {
            Text(
                "Choose what happens when you swipe, double-tap or press and hold the album cover in the full player. Swiping down still closes the full player.",
                style = DsmusicTheme.typography.bodySmall,
                color = DsmusicTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            SettingsGroupCard(listOf(
                {
                    GestureSettingRow(
                        "Swipe left",
                        gestures.coverSwipeLeft,
                        PlayerGestureAction.choices(),
                        onSelect = { playerViewModel.setPlayerGesture("cover_swipe_left", it) }
                    )
                },
                {
                    GestureSettingRow(
                        "Swipe right",
                        gestures.coverSwipeRight,
                        PlayerGestureAction.choices(),
                        onSelect = { playerViewModel.setPlayerGesture("cover_swipe_right", it) }
                    )
                },
                {
                    GestureSettingRow(
                        "Swipe up",
                        gestures.coverSwipeUp,
                        PlayerGestureAction.choices(),
                        onSelect = { playerViewModel.setPlayerGesture("cover_swipe_up", it) }
                    )
                },
                {
                    GestureSettingRow(
                        "Double tap",
                        gestures.coverDoubleTap,
                        PlayerGestureAction.choices(doubleTap = true),
                        onSelect = {
                            playerViewModel.setPlayerGesture("cover_double_tap", it)
                            if (it == PlayerGestureAction.SEEK_SIDES && gestures.doubleTapSeekSeconds < 3) {
                                playerViewModel.setDoubleTapSeekSeconds(10)
                            }
                        }
                    )
                },
                {
                    GestureSettingRow(
                        "Long press",
                        gestures.coverLongPress,
                        PlayerGestureAction.choices(),
                        onSelect = { playerViewModel.setPlayerGesture("cover_long_press", it) }
                    )
                }
            ))
            }
            
            if (gestures.coverDoubleTap == PlayerGestureAction.SEEK_SIDES) {
                SettingsAccordionSection("Double-tap seek") {
                SettingsGroupCard(listOf {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                        Text("Seconds to seek", style = DsmusicTheme.typography.bodyLarge)
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(3, 5, 10, 15).forEach { seconds ->
                                val selected = gestures.doubleTapSeekSeconds == seconds
                                DsmusicSurface(
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .clickable { playerViewModel.setDoubleTapSeekSeconds(seconds) },
                                    color = if (selected) DsmusicTheme.colorScheme.primaryContainer else DsmusicTheme.colorScheme.surfaceVariant
                                ) {
                                    Text(
                                        "$seconds s",
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                                        color = if (selected) DsmusicTheme.colorScheme.onPrimaryContainer else DsmusicTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                })
                }
                
            }
            SettingsAccordionSection("Mini player") {
            Text(
                "These actions work while the mini player is showing. Tap it to open the full player.",
                style = DsmusicTheme.typography.bodySmall,
                color = DsmusicTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            SettingsGroupCard(listOf(
                {
                    GestureSettingRow(
                        "Swipe left",
                        gestures.miniSwipeLeft,
                        PlayerGestureAction.choices(mini = true),
                        onSelect = { playerViewModel.setPlayerGesture("mini_swipe_left", it) }
                    )
                },
                {
                    GestureSettingRow(
                        "Swipe right",
                        gestures.miniSwipeRight,
                        PlayerGestureAction.choices(mini = true),
                        onSelect = { playerViewModel.setPlayerGesture("mini_swipe_right", it) }
                    )
                },
                {
                    GestureSettingRow(
                        "Pull down",
                        gestures.miniSwipeDown,
                        PlayerGestureAction.choices(mini = true),
                        onSelect = { playerViewModel.setPlayerGesture("mini_swipe_down", it) }
                    )
                }
            ))
            
            }
            SettingsAccordionSection("Default actions") {
            SettingsGroupCard(listOf {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            playerViewModel.setPlayerGesture("cover_swipe_left", PlayerGestureAction.NEXT)
                            playerViewModel.setPlayerGesture("cover_swipe_right", PlayerGestureAction.PREVIOUS)
                            playerViewModel.setPlayerGesture("cover_swipe_up", PlayerGestureAction.QUEUE)
                            playerViewModel.setPlayerGesture("cover_double_tap", PlayerGestureAction.SEEK_SIDES)
                            playerViewModel.setPlayerGesture("cover_long_press", PlayerGestureAction.NONE)
                            playerViewModel.setPlayerGesture("mini_swipe_left", PlayerGestureAction.NEXT)
                            playerViewModel.setPlayerGesture("mini_swipe_right", PlayerGestureAction.PREVIOUS)
                            playerViewModel.setPlayerGesture("mini_swipe_down", PlayerGestureAction.STOP)
                            playerViewModel.setDoubleTapSeekSeconds(10)
                        }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Restore default actions", modifier = Modifier.weight(1f), color = DsmusicTheme.colorScheme.primary)
                    Icon(Icons.Rounded.Refresh, contentDescription = null, tint = DsmusicTheme.colorScheme.primary)
                }
            })
            }
            
        }
    }
}

@Composable
private fun GestureSettingRow(
    title: String,
    selected: PlayerGestureAction,
    options: List<PlayerGestureAction>,
    onSelect: (PlayerGestureAction) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = true }
                .padding(horizontal = 16.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(title, modifier = Modifier.weight(1f), style = DsmusicTheme.typography.bodyLarge)
            Text(selected.label, color = DsmusicTheme.colorScheme.onSurfaceVariant, style = DsmusicTheme.typography.labelLarge)
            Icon(Icons.Rounded.ChevronRight, contentDescription = "Change $title", tint = DsmusicTheme.colorScheme.onSurface, modifier = Modifier.padding(start = 6.dp).size(20.dp))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { action ->
                DropdownMenuItem(
                    text = { Text(action.label) },
                    onClick = {
                        expanded = false
                        onSelect(action)
                    },
                    trailingIcon = if (action == selected) {
                        { Icon(Icons.Rounded.Refresh, contentDescription = "Selected") }
                    } else null
                )
            }
        }
    }
}

@Composable
private fun SettingsMainScreen(navController: androidx.navigation.NavController) {
    val scrollState = rememberScrollState()
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val context = LocalContext.current
    val mainActivity = context as? MainActivity

    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPostScroll(
                consumed: Offset, available: Offset, source: NestedScrollSource
            ): Offset {
                if (!isLandscape && mainActivity != null) {
                    val delta = consumed.y
                    if (delta < -30f) mainActivity.hideBottomNavWithAnimation()
                    else if (delta > 30f) mainActivity.showBottomNavWithAnimation()
                }
                return super.onPostScroll(consumed, available, source)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DsmusicTheme.colorScheme.surface)
            .statusBarsPadding()
            .nestedScroll(nestedScrollConnection)
            .verticalScroll(scrollState)
            .padding(horizontal = 18.dp)
            .padding(bottom = 120.dp)
    ) {
        DsmusicPageHeading(
            title = "Settings",
            subtitle = "Make Quantune look and sound the way you like.",
            kicker = "YOUR LISTENING SPACE",
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 18.dp, bottom = 20.dp)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(26.dp))
                .background(
                    Brush.linearGradient(
                        listOf(
                            DsmusicTheme.colorScheme.primaryContainer.copy(alpha = 0.92f),
                            DsmusicTheme.colorScheme.surfaceContainerHigh,
                            DsmusicTheme.colorScheme.secondaryContainer.copy(alpha = 0.72f)
                        )
                    )
                )
                .clickable { navController.navigate(SettingsRoute.Appearance.route) }
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                Text(
                    "MAKE IT YOURS",
                    style = DsmusicTheme.typography.labelSmall.copy(
                        letterSpacing = 1.3.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = DsmusicTheme.colorScheme.primary
                )
                Text(
                    "Your sound.\nYour style.",
                    style = DsmusicTheme.typography.headlineMedium.copy(fontWeight = FontWeight.ExtraBold),
                    color = DsmusicTheme.colorScheme.onSurface
                )
                Text(
                    "Themes, player design, lyrics, fonts and navigation.",
                    style = DsmusicTheme.typography.bodySmall,
                    color = DsmusicTheme.colorScheme.onSurfaceVariant
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Open customisation",
                        style = DsmusicTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = DsmusicTheme.colorScheme.primary
                    )
                    Icon(
                        Icons.Rounded.ChevronRight,
                        contentDescription = null,
                        tint = DsmusicTheme.colorScheme.onSurface,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Box(
                modifier = Modifier
                    .size(58.dp)
                    .clip(CircleShape)
                    .background(DsmusicTheme.colorScheme.primary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_settings_customize),
                    contentDescription = null,
                    tint = DsmusicTheme.colorScheme.primary,
                    modifier = Modifier.size(30.dp)
                )
            }
        }

        Text(
            "LISTENING & CONTROL",
            style = DsmusicTheme.typography.labelSmall.copy(letterSpacing = 1.2.sp, fontWeight = FontWeight.Bold),
            color = DsmusicTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 2.dp, top = 24.dp, bottom = 10.dp)
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SettingsDashboardTile(
                title = "Playback",
                subtitle = "Sound quality, autoplay and song transitions",
                iconResId = R.drawable.ic_settings_playback,
                modifier = Modifier.weight(1f),
                onClick = { navController.navigate(SettingsRoute.Playback.route) }
            )
            SettingsDashboardTile(
                title = "Controls",
                subtitle = "Gestures and player actions",
                iconResId = R.drawable.ic_settings_gestures,
                modifier = Modifier.weight(1f),
                onClick = { navController.navigate(SettingsRoute.Controls.route) }
            )
        }

        Text(
            "LIBRARY & ACCOUNT",
            style = DsmusicTheme.typography.labelSmall.copy(letterSpacing = 1.2.sp, fontWeight = FontWeight.Bold),
            color = DsmusicTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 2.dp, top = 20.dp, bottom = 10.dp)
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SettingsDashboardTile(
                title = "Account & playlists",
                subtitle = "YouTube Music and history",
                iconResId = R.drawable.ic_settings_account,
                modifier = Modifier.weight(1f),
                onClick = { navController.navigate(SettingsRoute.Account.route) }
            )
            SettingsDashboardTile(
                title = "Music Management",
                subtitle = "Folders, downloads and saved files",
                iconResId = R.drawable.ic_settings_library,
                modifier = Modifier.weight(1f),
                onClick = { navController.navigate(SettingsRoute.MusicManagement.route) }
            )
        }

        Text(
            "BACKUP & SUPPORT",
            style = DsmusicTheme.typography.labelSmall.copy(letterSpacing = 1.2.sp, fontWeight = FontWeight.Bold),
            color = DsmusicTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 2.dp, top = 20.dp, bottom = 10.dp)
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SettingsDashboardTile(
                title = "Backup & Restore",
                subtitle = "Move your settings and library",
                iconResId = R.drawable.ic_settings_backup,
                modifier = Modifier.weight(1f),
                onClick = { navController.navigate(SettingsRoute.BackupRestore.route) }
            )
            SettingsDashboardTile(
                title = "Feedback",
                subtitle = "Report bugs and request features",
                iconResId = R.drawable.ic_settings_feedback,
                modifier = Modifier.weight(1f),
                onClick = { navController.navigate(SettingsRoute.Feedback.route) }
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SettingsDashboardTile(
                title = "About Quantune",
                subtitle = "Version, updates and credits",
                iconResId = R.drawable.ic_settings_info,
                modifier = Modifier.weight(1f),
                onClick = { navController.navigate(SettingsRoute.About.route) }
            )
            Spacer(Modifier.weight(1f))
        }

        AppLogoSection(isLandscape = isLandscape)
    }
}

@Composable
private fun SettingsDashboardTile(
    title: String,
    subtitle: String,
    iconResId: Int,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .height(142.dp)
            .clip(RoundedCornerShape(21.dp))
            .background(DsmusicTheme.colorScheme.surfaceContainerLow)
            .border(
                1.dp,
                DsmusicTheme.colorScheme.outlineVariant.copy(alpha = 0.20f),
                RoundedCornerShape(21.dp)
            )
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(DsmusicTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(iconResId),
                    contentDescription = null,
                    tint = DsmusicTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(Modifier.weight(1f))
            Icon(
                Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = DsmusicTheme.colorScheme.onSurface,
                modifier = Modifier.size(20.dp)
            )
        }
        Text(
            title,
            style = DsmusicTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = DsmusicTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            subtitle,
            style = DsmusicTheme.typography.bodySmall,
            color = DsmusicTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun SettingsCategoryItem(
    title: String,
    subtitle: String,
    iconResId: Int,
    onClick: () -> Unit
) {
    DsmusicListItem(
        headlineContent = {
            Text(text = title, style = DsmusicTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        },
        supportingContent = {
            Text(text = subtitle, style = DsmusicTheme.typography.bodyMedium, color = DsmusicTheme.colorScheme.onSurfaceVariant)
        },
        leadingContent = {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(DsmusicTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = androidx.compose.ui.res.painterResource(id = iconResId),
                    contentDescription = null,
                    tint = DsmusicTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(28.dp)
                )
            }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable { onClick() }
    )
}

@Composable
private fun MusicManagementScreen(
    navController: androidx.navigation.NavController,
    libraryPaths: List<String>,
    onRemovePath: (String) -> Unit,
    onAddPathClick: () -> Unit,
    songCacheSize: String,
    imageCacheSize: String,
    onClearSongCache: () -> Unit,
    onClearImageCache: () -> Unit,
    ignoreShortAudio: Boolean,
    onIgnoreShortAudioChange: (Boolean) -> Unit,
    ignoreShortAudioDuration: Float,
    onIgnoreShortAudioDurationChange: (Float) -> Unit,
    songCacheMaxSize: Int,
    onSongCacheMaxSizeChange: (Int) -> Unit,
    imageCacheMaxSize: Int,
    onImageCacheMaxSizeChange: (Int) -> Unit,
    mediaCacheEnabled: Boolean,
    onMediaCacheToggle: (Boolean) -> Unit,
    mediaCacheLimitGb: Int,
    onMediaCacheLimitChange: (Int) -> Unit,
    downloadFolder: String?,
    onDownloadFolderClick: () -> Unit,
    hiddenFolders: List<String>,
    onAddHiddenFolderClick: () -> Unit,
    onRemoveHiddenFolder: (String) -> Unit
) {
    Scaffold(
        topBar = { SettingsDetailTopBar("Music Management") { navController.popBackStack() } }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 120.dp)
        ) {
            SettingsAccordionSection(stringResource(R.string.settings_header_library)) {
            SettingsGroupCard(buildList {
                add { LibrarySourceHeader() }
                libraryPaths.forEach { path ->
                    add { LibraryPathRow(path, onRemovePath) }
                }
                if (libraryPaths.isEmpty()) {
                    add { LibraryPathRow(stringResource(R.string.setting_music_source_default), null) }
                }
                add { AddMorePathRow(onAddPathClick) }
            })

            
            }
            SettingsAccordionSection("Short recordings") {
            SettingsGroupCard(buildList {
                add {
                    IgnoreShortAudioRow(ignoreShortAudio, onIgnoreShortAudioChange)
                }
                if (ignoreShortAudio) {
                    add {
                        IgnoreShortAudioDurationRow(ignoreShortAudioDuration, onIgnoreShortAudioDurationChange)
                    }
                }
            })

            
            }
            SettingsAccordionSection("Hidden Folders") {
            SettingsGroupCard(buildList {
                hiddenFolders.forEach { folder ->
                    add { HiddenFolderRow(folder, onRemoveHiddenFolder) }
                }
                if (hiddenFolders.isEmpty()) {
                    add {
                        DsmusicListItem(
                            headlineContent = { Text("No hidden folders yet", style = DsmusicTheme.typography.bodyLarge) },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                        )
                    }
                }
                add {
                    AddHiddenFolderRow(onAddHiddenFolderClick)
                }
            })

            
            }
            SettingsAccordionSection("Downloads") {
            SettingsGroupCard(listOf(
                { DownloadFolderRow(downloadFolder, onDownloadFolderClick) }
            ))

            
            }
            SettingsAccordionSection(stringResource(R.string.settings_header_storage)) {
            SettingsGroupCard(buildList {
                add { SongCacheSizeRow(songCacheSize, songCacheMaxSize, onSongCacheMaxSizeChange, onClearSongCache) }
                add { ImageCacheSizeRow(imageCacheSize, imageCacheMaxSize, onImageCacheMaxSizeChange, onClearImageCache) }
                add {
                    DsmusicListItem(
                        modifier = Modifier.clickable { onMediaCacheToggle(!mediaCacheEnabled) },
                        headlineContent = { Text("Save online audio", fontWeight = FontWeight.SemiBold) },
                        supportingContent = {
                            Text(if (mediaCacheEnabled) "Temporarily save online songs so they load faster next time."
                                else "Online songs will not be saved for faster repeat playback.",
                                color = DsmusicTheme.colorScheme.onSurfaceVariant)
                        },
                        trailingContent = { DsmusicSwitch(checked = mediaCacheEnabled, onCheckedChange = onMediaCacheToggle) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )
                }
                if (mediaCacheEnabled) {
                    add {
                        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically) {
                                Text("Online audio storage limit", style = DsmusicTheme.typography.bodyLarge)
                                Text(mediaCacheLimitGb.toString() + " GB", style = DsmusicTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold, color = DsmusicTheme.colorScheme.primary)
                            }
                            Slider(value = mediaCacheLimitGb.toFloat(),
                                onValueChange = { onMediaCacheLimitChange(it.toInt().coerceIn(1, 10)) },
                                valueRange = 1f..10f, steps = 8)
                            Text("This limit takes effect right away. When storage is full, the oldest saved songs are removed first.",
                                style = DsmusicTheme.typography.bodySmall, color = DsmusicTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            })
            }
            
        }
    }
}

@Composable
private fun BackupRestoreScreen(
    navController: androidx.navigation.NavController,
    onBackupClick: () -> Unit,
    onRestoreClick: () -> Unit,
    includeListeningHistory: Boolean,
    onIncludeListeningHistoryChange: (Boolean) -> Unit
) {
    Scaffold(
        topBar = { SettingsDetailTopBar("Backup & Restore") { navController.popBackStack() } }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 120.dp)
        ) {
            SettingsAccordionSection("Backup & Restore") {
            SettingsGroupCard(buildList {
                add {
                    DsmusicListItem(
                        onClick = onBackupClick,
                        content = {
                            Column {
                                Text("Export Settings & Library", style = DsmusicTheme.typography.bodyLarge)
                                Text("Save settings, appearance, player customisation, favourites, playlists and Explore preferences", style = DsmusicTheme.typography.bodyMedium, color = DsmusicTheme.colorScheme.onSurfaceVariant)
                            }
                        },
                        leadingContent = {
                            Icon(
                                painter = painterResource(R.drawable.ic_download),
                                contentDescription = null,
                                tint = DsmusicTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(24.dp)
                            )
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )
                }
                add {
                    DsmusicListItem(
                        onClick = { onIncludeListeningHistoryChange(!includeListeningHistory) },
                        content = {
                            Column {
                                Text("Include listening history", style = DsmusicTheme.typography.bodyLarge)
                                Text(
                                    if (includeListeningHistory) "Export only the latest 200 listening events" else "Keep listening history out of the backup",
                                    style = DsmusicTheme.typography.bodyMedium,
                                    color = DsmusicTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        },
                        trailingContent = {
                            Switch(
                                checked = includeListeningHistory,
                                onCheckedChange = onIncludeListeningHistoryChange
                            )
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )
                }
                add {
                    DsmusicListItem(
                        onClick = onRestoreClick,
                        content = {
                            Column {
                                Text("Import Settings & Library", style = DsmusicTheme.typography.bodyLarge)
                                Text("Restore settings and library data. Sign in again on this device; downloaded audio and stream-cache files are not included.", style = DsmusicTheme.typography.bodyMedium, color = DsmusicTheme.colorScheme.onSurfaceVariant)
                            }
                        },
                        leadingContent = {
                            Icon(
                                painter = painterResource(R.drawable.ic_download),
                                contentDescription = null,
                                tint = DsmusicTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(24.dp).graphicsLayer { rotationZ = 180f }
                            )
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )
                }
            })
            }
            
        }
    }
}

@Composable
private fun IgnoreShortAudioRow(checked: Boolean, onToggle: (Boolean) -> Unit) {
    DsmusicListItem(
        onClick = { onToggle(!checked) },
        content = {
            Column {
                Text(
                    text = "Ignore Short Audio (Voice Notes)",
                    style = DsmusicTheme.typography.bodyLarge
                )
                Text(
                    text = "Hide voice notes and short recordings from library",
                    style = DsmusicTheme.typography.bodyMedium,
                    color = DsmusicTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        trailingContent = {
            DsmusicSwitch(checked = checked, onCheckedChange = onToggle)
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
}

@Composable
private fun IgnoreShortAudioDurationRow(
    value: Float, onValueChange: (Float) -> Unit
) {
    DsmusicListItem(
        onClick = { },
        content = {
            Column {
                Text(
                    text = "Minimum song length",
                    style = DsmusicTheme.typography.bodyLarge
                )
                Text(
                    text = "Hide songs shorter than ${value.toInt()} seconds",
                    style = DsmusicTheme.typography.bodyMedium,
                    color = DsmusicTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        supportingContent = {
            Slider(
                value = value.coerceIn(10f, 120f),
                onValueChange = onValueChange,
                valueRange = 10f..120f,
                steps = 10,
                modifier = Modifier.fillMaxWidth()
            )
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
}

@Composable
private fun HiddenFolderRow(path: String, onRemove: (String) -> Unit) {
    DsmusicListItem(
        onClick = { },
        content = {
            Text(
                text = path.substringAfterLast("/"),
                style = DsmusicTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        supportingContent = {
            Text(
                text = path,
                style = DsmusicTheme.typography.bodyMedium,
                color = DsmusicTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        leadingContent = {
            Icon(
                painter = painterResource(R.drawable.ic_folder_open),
                contentDescription = null,
                tint = DsmusicTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
        },
        trailingContent = {
            Text(
                text = "Remove",
                style = DsmusicTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = DsmusicTheme.colorScheme.error,
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable { onRemove(path) }
                    .padding(8.dp)
            )
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
}

@Composable
private fun AddHiddenFolderRow(onClick: () -> Unit) {
    DsmusicListItem(
        onClick = onClick,
        content = {
            Text(
                text = "Add Hidden Folder",
                style = DsmusicTheme.typography.bodyLarge,
                color = DsmusicTheme.colorScheme.primary
            )
        },
        leadingContent = {
            Icon(
                imageVector = Icons.Rounded.Add,
                contentDescription = null,
                tint = DsmusicTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
}

@Composable
private fun AccountScreen(
    navController: androidx.navigation.NavController,
    ytCookies: String?,
    onYtCookiesChange: (String?) -> Unit,
    dataSaver: Boolean,
    onDataSaverChange: (Boolean) -> Unit,
    pauseHistory: Boolean,
    onPauseHistoryChange: (Boolean) -> Unit
) {
    Scaffold(
        topBar = { SettingsDetailTopBar("Account & playlists") { navController.popBackStack() } }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 120.dp)
        ) {
            SettingsAccordionSection("YouTube Music Account") {
            SettingsGroupCard(listOf(
                {
                    var showLoginDialog by remember { mutableStateOf(false) }

                    if (showLoginDialog) {
                        YouTubeMusicLoginDialog(
                            onDismissRequest = { showLoginDialog = false },
                            onLoginSuccess = { cookies ->
                                onYtCookiesChange(cookies)
                                showLoginDialog = false
                            }
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (ytCookies != null) "Logged in to YouTube Music" else "Guest mode",
                                style = DsmusicTheme.typography.titleMedium,
                                color = DsmusicTheme.colorScheme.onSurface
                            )
                            if (ytCookies != null) {
                                Text(
                                    text = "Profile: Quantune User",
                                    style = DsmusicTheme.typography.bodyMedium,
                                    color = DsmusicTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Text(
                                text = if (ytCookies != null) "Your personal playlists, library and recommendations are available." else "No account connected. Sign in to see your playlists and recommendations.",
                                style = DsmusicTheme.typography.bodySmall,
                                color = DsmusicTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        DsmusicButton(
                            onClick = {
                                if (ytCookies != null) {
                                    onYtCookiesChange(null)
                                } else {
                                    showLoginDialog = true
                                }
                            },
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (ytCookies != null) DsmusicTheme.colorScheme.errorContainer else DsmusicTheme.colorScheme.primaryContainer,
                                contentColor = if (ytCookies != null) DsmusicTheme.colorScheme.onErrorContainer else DsmusicTheme.colorScheme.onPrimaryContainer
                            )
                        ) {
                            Text(if (ytCookies != null) "Log Out" else "Log In")
                        }
                    }
                }
            ))

            
            }
            SettingsAccordionSection("Data Saving") {
            SettingsGroupCard(listOf(
                { DataSaverRow(dataSaver, onDataSaverChange) }
            ))

            
            }
            SettingsAccordionSection("Playlists & privacy") {
            SettingsGroupCard(listOf(
                { PauseHistoryRow(pauseHistory, onPauseHistoryChange) },
                { ManualSyncRow() }
            ))
            }
            
        }
    }
}

@Composable
private fun DataSaverRow(checked: Boolean, onToggle: (Boolean) -> Unit) {
    DsmusicListItem(
        onClick = { onToggle(!checked) },
        content = {
            Column {
                Text(
                    text = "Data Saver (Wi-Fi Only)",
                    style = DsmusicTheme.typography.bodyLarge
                )
                Text(
                    text = "Use Wi-Fi only for music streaming and high-quality album covers",
                    style = DsmusicTheme.typography.bodyMedium,
                    color = DsmusicTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        trailingContent = {
            DsmusicSwitch(
                checked = checked,
                onCheckedChange = onToggle
            )
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
}

@Composable
private fun PauseHistoryRow(checked: Boolean, onToggle: (Boolean) -> Unit) {
    DsmusicListItem(
        onClick = { onToggle(!checked) },
        content = {
            Column {
                Text(
                    text = "Pause Listening History",
                    style = DsmusicTheme.typography.bodyLarge
                )
                Text(
                    text = "Stop saving the songs you listen to for recommendations",
                    style = DsmusicTheme.typography.bodyMedium,
                    color = DsmusicTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        trailingContent = {
            DsmusicSwitch(
                checked = checked,
                onCheckedChange = onToggle
            )
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
}

@Composable
private fun ManualSyncRow() {
    var isSyncing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    DsmusicListItem(
        onClick = {
            if (!isSyncing) {
                isSyncing = true
                scope.launch {
                    delay(2000) // Simulate sync
                    isSyncing = false
                }
            }
        },
        content = {
            Column {
                Text(text = "Refresh playlists", style = DsmusicTheme.typography.bodyLarge)
                Text(text = "Update your playlists and favourites", style = DsmusicTheme.typography.bodyMedium, color = DsmusicTheme.colorScheme.onSurfaceVariant)
            }
        },
        trailingContent = {
            if (isSyncing) {
                com.dsann.music.ui.theme.LoadingIndicator(
                    modifier = Modifier.size(24.dp),
                    color = DsmusicTheme.colorScheme.primary
                )
            } else {
                Icon(
                    imageVector = Icons.Rounded.Refresh,
                    contentDescription = "Sync Now",
                    tint = DsmusicTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp)
                )
            }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
}

@Composable
private fun AppearanceScreen(
    navController: androidx.navigation.NavController,
    darkMode: Boolean,
    themeMode: String,
    onThemeModeChange: (String) -> Unit,
    onDarkModeChange: (Boolean) -> Unit,
    amoledBlack: Boolean,
    onAmoledBlackChange: (Boolean) -> Unit,
    dynamicAlbumTheme: Boolean,
    onDynamicAlbumThemeChange: (Boolean) -> Unit,
    savedLooks: List<String>,
    onSaveLook: (String) -> Unit,
    onApplyLook: (String) -> Unit,
    onDeleteLook: (String) -> Unit,
    appearancePreset: String,
    onAppearancePresetChange: (String) -> Unit,
    customAccentColor: String,
    onCustomAccentColorChange: (String) -> Unit,
    genreGradientStyle: String,
    onGenreGradientStyleChange: (String) -> Unit,
    uiCornerStyle: String,
    onUiCornerStyleChange: (String) -> Unit,
    playerArtworkShape: String,
    onPlayerArtworkShapeChange: (String) -> Unit,
    playerArtworkScale: Float,
    onPlayerArtworkScaleChange: (Float) -> Unit,
    playerButtonsPill: Boolean,
    onPlayerButtonsPillChange: (Boolean) -> Unit,
    playerBackgroundStyle: String,
    onPlayerBackgroundStyleChange: (String) -> Unit,
    nowPlayingCoverAnimation: String,
    onNowPlayingCoverAnimationChange: (String) -> Unit,
    miniPlayerStyle: String,
    onMiniPlayerStyleChange: (String) -> Unit,
    miniPlayerControlShape: String,
    onMiniPlayerControlShapeChange: (String) -> Unit,
    miniPlayerControlIcons: String,
    onMiniPlayerControlIconsChange: (String) -> Unit,
    miniPlayerProgressStyle: String,
    onMiniPlayerProgressStyleChange: (String) -> Unit,
    lyricsTextSize: String,
    onLyricsTextSizeChange: (String) -> Unit,
    showSyncedLyricsInPlayer: Boolean,
    onShowSyncedLyricsInPlayerChange: (Boolean) -> Unit,
    lyricsAlignment: String,
    onLyricsAlignmentChange: (String) -> Unit,
    lyricsFontFamily: String,
    onLyricsFontFamilyChange: (String) -> Unit,
    lyricsColorStyle: String,
    onLyricsColorStyleChange: (String) -> Unit,
    uiDensity: String,
    onUiDensityChange: (String) -> Unit,
    taperedSongDividers: Boolean,
    onTaperedSongDividersChange: (Boolean) -> Unit,
    uiFontFamily: String,
    onUiFontFamilyChange: (String) -> Unit,
    uiFontScale: Float,
    onUiFontScaleChange: (Float) -> Unit,
    uiFontWeight: String,
    onUiFontWeightChange: (String) -> Unit,
    uiLetterSpacing: String,
    onUiLetterSpacingChange: (String) -> Unit,
    navLabelsMode: String,
    onNavLabelsModeChange: (String) -> Unit,
    dynamicNavStyle: Boolean,
    onDynamicNavStyleChange: (Boolean) -> Unit,
    navBarStyle: String,
    onNavBarStyleChange: (String) -> Unit,
    navBarSize: String,
    onNavBarSizeChange: (String) -> Unit,
    playerLayout: String,
    onPlayerLayoutChange: (String) -> Unit,
    playerButtons: List<String>,
    onPlayerButtonsChange: (List<String>) -> Unit,
    onMovePlayerButton: (Int, Int) -> Unit,
    sessionButtons: List<String>,
    onSessionButtonsChange: (List<String>) -> Unit,
    onMoveSessionButton: (Int, Int) -> Unit,
    playerTitleAlign: String,
    onPlayerTitleAlignChange: (String) -> Unit,
    playerTitleSize: String,
    onPlayerTitleSizeChange: (String) -> Unit,
    playerTitleWeight: String,
    onPlayerTitleWeightChange: (String) -> Unit,
    playerTitleScroll: Boolean,
    onPlayerTitleScrollChange: (Boolean) -> Unit,
    playerTimes: String,
    onPlayerTimesChange: (String) -> Unit,
    scrubberStyle: String,
    onScrubberStyleChange: (String) -> Unit,
    scrubberColor: String,
    onScrubberColorChange: (String) -> Unit,
    visualizerStyle: String,
    onVisualizerStyleChange: (String) -> Unit,
    playerButtonStyle: String,
    onPlayerButtonStyleChange: (String) -> Unit,
    scrubberThumbStyle: String,
    onScrubberThumbStyleChange: (String) -> Unit,
    visualizerColor: String,
    onVisualizerColorChange: (String) -> Unit
) {
    Scaffold(
        topBar = { SettingsDetailTopBar("Customize") { navController.popBackStack() } }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 120.dp)
        ) {
            Text(
                "Change how the player and app look. Choose layouts, button styles, colours and text settings.",
                style = DsmusicTheme.typography.bodyMedium,
                color = DsmusicTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
            )

            SettingsAccordionSection("Your looks") {

                SavedLooksEditor(
                    looks = savedLooks,
                    onSave = onSaveLook,
                    onApply = onApplyLook,
                    onDelete = onDeleteLook
                )
            }

            SettingsAccordionSection("Theme") {

            SettingsGroupCard(buildList {
                add {
                    SettingsChoiceRow(
                        title = "Theme mode",
                        description = "System matches your phone's light or dark setting. Light and Dark stay as chosen until you change them.",
                        selected = themeMode.replaceFirstChar { it.uppercase() },
                        options = listOf("System", "Light", "Dark"),
                        onSelected = { onThemeModeChange(it.lowercase()) }
                    )
                }
                if (darkMode) add { AmoledBlackRow(amoledBlack, onAmoledBlackChange) }
                add { DynamicAlbumThemeRow(dynamicAlbumTheme, onDynamicAlbumThemeChange) }
            })

            
            


            SettingsGroupCard(listOf {
                Column {
                    ColorPaletteSelector(
                        selected = appearancePreset,
                        onSelected = onAppearancePresetChange
                    )
                    CustomAccentEditor(
                        value = customAccentColor,
                        onApply = onCustomAccentColorChange
                    )
                }
            })
            SettingsGroupCard(listOf {
                SettingsChoiceRow(
                    title = "Genre card colours",
                    description = "Choose the colours used on genre cards. Theme uses your app colours; the other choices use different colour blends.",
                    selected = genreGradientStyle,
                    options = listOf("Theme", "Aurora", "Ocean", "Sunset", "Forest", "Berry"),
                    onSelected = onGenreGradientStyleChange
                )
            })
            }

            SettingsAccordionSection("Full screen player") {

            SettingsGroupCard(listOf {
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Text(
                        "Artwork size · ${(playerArtworkScale * 100).toInt()}%",
                        style = DsmusicTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Slider(
                        value = playerArtworkScale,
                        onValueChange = onPlayerArtworkScaleChange,
                        valueRange = 0.65f..1f
                    )
                    Text(
                        "Adjust the full-player cover without moving the transport controls.",
                        style = DsmusicTheme.typography.bodySmall,
                        color = DsmusicTheme.colorScheme.onSurfaceVariant
                    )
                }
            })

            
            


                SettingsGroupCard(listOf {
                    SettingsChoiceRow(
                        title = "Player layout",
                        description = "Choose the player layout. Vinyl and Cassette give the album cover a different look.",
                        selected = playerLayout,
                        options = listOf("Standard", "Vinyl", "Cassette"),
                        onSelected = onPlayerLayoutChange
                    )
                })
            


            SettingsGroupCard(listOf(
                {
                    SettingsChoiceRow(
                        title = "Action button grouping",
                        description = "Show the player buttons together in one rounded group or keep them separate.",
                        selected = if (playerButtonsPill) "Capsule" else "Separate",
                        options = listOf("Capsule", "Separate"),
                        onSelected = { onPlayerButtonsPillChange(it == "Capsule") }
                    )
                },
                {
                    SettingsChoiceRow(
                        title = "Button presentation",
                        description = "Choose the shape behind the player buttons.",
                        selected = playerButtonStyle,
                        options = listOf("Round", "Pill", "None", "Square"),
                        onSelected = onPlayerButtonStyleChange
                    )
                }
            ))
            PlayerButtonsSettings(
                selected = playerButtons,
                onSelectionChanged = onPlayerButtonsChange,
                onMove = onMovePlayerButton
            )

            
            


            SettingsGroupCard(listOf(
                {
                    SettingsChoiceRow(
                        title = "Title alignment",
                        description = "Place the song title on the left or in the centre.",
                        selected = playerTitleAlign,
                        options = listOf("Left", "Centered"),
                        onSelected = onPlayerTitleAlignChange
                    )
                },
                {
                    SettingsChoiceRow(
                        title = "Title size",
                        description = "Change the size of the song title.",
                        selected = playerTitleSize,
                        options = listOf("Small", "Medium", "Large"),
                        onSelected = onPlayerTitleSizeChange
                    )
                },
                {
                    SettingsChoiceRow(
                        title = "Title weight",
                        description = "Choose how thin or bold the song title looks.",
                        selected = playerTitleWeight,
                        options = listOf("Light", "Regular", "Bold"),
                        onSelected = onPlayerTitleWeightChange
                    )
                },
                {
                    ToggleSettingRow(
                        title = "Scrolling titles",
                        description = "Long song titles move across the screen instead of being cut off.",
                        checked = playerTitleScroll,
                        onCheckedChange = onPlayerTitleScrollChange
                    )
                },
                {
                    SettingsChoiceRow(
                        title = "Time labels",
                        description = "Show the time played, time left, both or neither.",
                        selected = playerTimes,
                        options = listOf("Both", "Elapsed", "Time left", "None"),
                        onSelected = onPlayerTimesChange
                    )
                }
            ))

            
            


            SettingsGroupCard(listOf(
                {
                    SettingsChoiceRow(
                        title = "Progress style",
                        description = "Choose how song progress looks: a thin line, thick line, marker or moving wave.",
                        selected = scrubberStyle,
                        options = listOf("Hairline", "Bold", "Medium", "Waveform"),
                        onSelected = onScrubberStyleChange
                    )
                },
                {
                    SettingsChoiceRow(
                        title = "Progress color",
                        description = "Use the app's main colour, white, or a colour from the album cover.",
                        selected = scrubberColor,
                        options = listOf("Accent", "White", "Cover"),
                        onSelected = onScrubberColorChange
                    )
                },
                {
                    SettingsChoiceRow(
                        title = "Progress marker",
                        description = "Choose the progress marker: dot, line, ring, automatic or none.",
                        selected = scrubberThumbStyle,
                        options = listOf("Auto", "Dot", "Line", "Ring", "None"),
                        onSelected = onScrubberThumbStyleChange
                    )
                },
            ))

            
            

SettingsGroupCard(listOf {
    SettingsChoiceRow(
        title = "Player background",
        description = "Choose the player background: album cover, app theme, black, or a soft blend of colours based on the cover.",
        selected = playerBackgroundStyle,
        options = listOf("Artwork", "Theme", "Black", "Gradient"),
        onSelected = onPlayerBackgroundStyleChange
    )
})

SettingsGroupCard(listOf {
    SettingsChoiceRow(
        title = "Player artwork shape",
        description = "Choose the shape of the album cover in the player.",
        selected = playerArtworkShape,
        options = listOf("Rounded", "Square", "Circle", "Squircle", "Flower", "Cookie", "Arch", "Slanted"),
        onSelected = onPlayerArtworkShapeChange
    )
})


                SettingsGroupCard(listOf {
                    SettingsChoiceRow(
                        title = "Playing animation",
                        description = "Show a small animation over the cover of the song playing. Colours adjust to the album cover and your app theme.",
                        selected = nowPlayingCoverAnimation,
                        options = NowPlayingCoverAnimationPreference.choices,
                        onSelected = onNowPlayingCoverAnimationChange
                    )
                })
            }

            SettingsAccordionSection("Mini player") {
SettingsGroupCard(listOf(
    {
        SettingsChoiceRow(
            title = "Mini player style",
            description = "Choose how the mini player looks.",
            selected = miniPlayerStyle,
            options = listOf("Card", "Flat", "Compact", "Pill"),
            onSelected = onMiniPlayerStyleChange
        )
    },
    {
        SettingsChoiceRow(
            title = "Mini-player button shape",
            description = "Choose the shape behind the mini-player buttons. This is separate from the mini-player background.",
            selected = miniPlayerControlShape,
            options = listOf("Circle", "Rounded", "Pill", "None"),
            onSelected = onMiniPlayerControlShapeChange
        )
    },
    {
        SettingsChoiceRow(
            title = "Mini-player icon set",
            description = "Choose how the previous, play/pause and next buttons look.",
            selected = miniPlayerControlIcons,
            options = listOf("Classic", "Chevron", "Seek"),
            onSelected = onMiniPlayerControlIconsChange
        )
    },
    {
        SettingsChoiceRow(
            title = "Mini-player progress",
            description = "Show song progress as a ring, line, fill, or around the edge of the album cover. Choose None to hide it.",
            selected = miniPlayerProgressStyle,
            options = listOf("Ring", "Line", "Fill", "Matching cover", "None"),
            onSelected = onMiniPlayerProgressStyleChange
        )
    }
))
            }

            SettingsAccordionSection("Visualizer") {

            SettingsGroupCard(listOf {
                SettingsChoiceRow(
                    title = "Player animation",
                    description = "Choose an animation for the player. It follows music playback and never uses your microphone. Turn it off to save battery.",
                    selected = visualizerStyle,
                    options = listOf("Off", "Bars", "Wave", "Oval", "Pulse", "Glow"),
                    onSelected = onVisualizerStyleChange
                )
            })
            SettingsGroupCard(listOf {
                SettingsChoiceRow(
                    title = "Visualizer color",
                    description = "Use the app's main colour, white, a colour from the album cover, or changing colours.",
                    selected = visualizerColor,
                    options = listOf("Accent", "White", "Cover", "Spectrum"),
                    onSelected = onVisualizerColorChange
                )
            })
            }

            SettingsAccordionSection("Notification") {

            SessionButtonsSettings(
                selected = sessionButtons,
                onSelectionChanged = onSessionButtonsChange,
                onMove = onMoveSessionButton
            )
            }

            SettingsAccordionSection("Lyrics") {

            SettingsGroupCard(listOf(
                {
                    DsmusicListItem(
                        onClick = {
                            onShowSyncedLyricsInPlayerChange(!showSyncedLyricsInPlayer)
                        },
                        content = {
                            Column {
                                Text(
                                    "Show synced lyrics in player",
                                    style = DsmusicTheme.typography.bodyLarge
                                )
                                Text(
                                    "Show the current lyric line between the title or moving graphic and the buttons when your phone is upright. Karaoke moves to the next line after the last word finishes.",
                                    style = DsmusicTheme.typography.bodyMedium,
                                    color = DsmusicTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        },
                        trailingContent = {
                            DsmusicSwitch(
                                checked = showSyncedLyricsInPlayer,
                                onCheckedChange = onShowSyncedLyricsInPlayerChange
                            )
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )
                },
                {
                    SettingsChoiceRow(
                        title = "Lyrics text size",
                        description = "Change how large the lyrics appear.",
                        selected = lyricsTextSize,
                        options = listOf("Small", "Standard", "Large"),
                        onSelected = onLyricsTextSizeChange
                    )
                },
                {
                    SettingsChoiceRow(
                        title = "Lyrics colour",
                        description = "Choose between colourful lyrics, app colours, or white lyrics.",
                        selected = lyricsColorStyle,
                        options = listOf("Home Studio Gradient", "UI Matching", "White"),
                        onSelected = onLyricsColorStyleChange
                    )
                },
                {
                    SettingsChoiceRow(
                        title = "Lyrics alignment",
                        description = "Keep the original lyric alignment, or place every line on the left or in the centre.",
                        selected = lyricsAlignment,
                        options = listOf("Original", "Left", "Center"),
                        onSelected = onLyricsAlignmentChange
                    )
                },
                {
                    SettingsChoiceRow(
                        title = "Lyrics font",
                        description = "Choose the lettering style used for lyrics.",
                        selected = lyricsFontFamily,
                        options = listOf("Rounded", "Sans", "Serif", "Mono"),
                        onSelected = onLyricsFontFamilyChange
                    )
                }
            ))
            }

            SettingsAccordionSection("Text and layout") {

            SettingsGroupCard(listOf(
                {
                    SettingsChoiceRow(
                        title = "Interface font",
                        description = "Choose the font used throughout the app.",
                        selected = uiFontFamily,
                        options = listOf("Cursive", "Roboto", "Sans", "Serif", "Monospace"),
                        onSelected = onUiFontFamilyChange
                    )
                },
                {
                    SettingsChoiceRow(
                        title = "Font weight",
                        description = "Choose how thin or bold the app's text looks.",
                        selected = uiFontWeight,
                        options = listOf("Default", "Lighter", "Bolder"),
                        onSelected = onUiFontWeightChange
                    )
                },
                {
                    SettingsChoiceRow(
                        title = "Letter spacing",
                        description = "Make letters closer together or farther apart throughout the app.",
                        selected = uiLetterSpacing,
                        options = listOf("Tight", "Normal", "Wide"),
                        onSelected = onUiLetterSpacingChange
                    )
                }
            ))
            SettingsGroupCard(listOf {
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Text(
                        "Global font size · " + (uiFontScale * 100f).toInt() + "%",
                        style = DsmusicTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Slider(
                        value = uiFontScale,
                        onValueChange = onUiFontScaleChange,
                        valueRange = 0.85f..1.30f
                    )
                    Text(
                        "Live preview · AaBb 123",
                        style = DsmusicTheme.typography.titleMedium,
                        color = DsmusicTheme.colorScheme.onSurfaceVariant
                    )
                }
            })
            SettingsGroupCard(listOf {
                SettingsChoiceRow(
                    title = "Interface corners",
                    description = "Choose how rounded menus, cards, buttons and pop-ups look.",
                    selected = uiCornerStyle,
                    options = listOf("Tight", "Soft", "Round"),
                    onSelected = onUiCornerStyleChange
                )
            })
            SettingsGroupCard(listOf {
                SettingsChoiceRow(
                    title = "Screen spacing",
                    description = "Compact shows more on each screen. Large adds more space around buttons and items.",
                    selected = uiDensity,
                    options = listOf("Compact", "Comfortable", "Large"),
                    onSelected = onUiDensityChange
                )
            })
            SettingsGroupCard(listOf {
                ToggleSettingRow(
                    title = "Thin song dividers",
                    description = "Show a thin line between songs when a row has no border.",
                    checked = taperedSongDividers,
                    onCheckedChange = onTaperedSongDividersChange
                )
            })
            }

            SettingsAccordionSection("Navigation") {

            SettingsGroupCard(buildList {
                add {
                    SettingsChoiceRow(
                        title = "Navigation labels",
                        description = "Always show labels, show a label only for the selected tab, or hide them all.",
                        selected = navLabelsMode,
                        options = listOf("Always", "Selected", "Never"),
                        onSelected = onNavLabelsModeChange
                    )
                }
                add {
                    SettingsChoiceRow(
                        title = "Dock style",
                        description = "Choose how the navigation bar looks: floating, full-width at the bottom, or without a background.",
                        selected = navBarStyle,
                        options = listOf("Floating", "Docked", "Minimal"),
                        onSelected = onNavBarStyleChange
                    )
                }
                add {
                    SettingsChoiceRow(
                        title = "Dock size",
                        description = "Choose the height of the navigation bar and the size of its icons.",
                        selected = navBarSize,
                        options = listOf("Compact", "Standard", "Tall"),
                        onSelected = onNavBarSizeChange
                    )
                }
                add { DynamicNavStyleRow(dynamicNavStyle, onDynamicNavStyleChange) }
            })
            }


            
        }
    }
}

@Composable
private fun CustomAccentEditor(value: String, onApply: (String) -> Unit) {
    var draft by remember(value) { mutableStateOf(value) }
    val valid = draft.trim().let { raw ->
        val normalized = if (raw.startsWith("#")) raw else "#$raw"
        normalized.matches(Regex("#[0-9a-fA-F]{6}"))
    }
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("Custom highlight colour", style = DsmusicTheme.typography.bodyLarge)
        Text(
            "Enter a 6-digit colour code to choose your own app highlight colour.",
            style = DsmusicTheme.typography.bodySmall,
            color = DsmusicTheme.colorScheme.onSurfaceVariant
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            val parsed = if (valid) runCatching {
                Color(android.graphics.Color.parseColor(if (draft.startsWith("#")) draft else "#$draft"))
            }.getOrDefault(DsmusicTheme.colorScheme.primary) else DsmusicTheme.colorScheme.surfaceVariant
            Box(
                Modifier.size(34.dp).clip(CircleShape).background(parsed)
                    .border(1.dp, DsmusicTheme.colorScheme.outlineVariant, CircleShape)
            )
            BasicTextField(
                value = draft,
                onValueChange = { draft = it.take(7).uppercase() },
                singleLine = true,
                textStyle = DsmusicTheme.typography.bodyLarge.copy(color = DsmusicTheme.colorScheme.onSurface),
                modifier = Modifier.weight(1f).clip(RoundedCornerShape(10.dp))
                    .background(DsmusicTheme.colorScheme.surfaceContainerHigh)
                    .padding(horizontal = 12.dp, vertical = 11.dp)
            )
            Box(
                Modifier.clip(RoundedCornerShape(50))
                    .background(if (valid) DsmusicTheme.colorScheme.primary else DsmusicTheme.colorScheme.surfaceVariant)
                    .clickable(enabled = valid) { onApply(if (draft.startsWith("#")) draft else "#$draft") }
                    .padding(horizontal = 15.dp, vertical = 10.dp)
            ) {
                Text("Apply", style = DsmusicTheme.typography.labelLarge, color = if (valid) Color(0xFF0B1220) else DsmusicTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun ColorPaletteSelector(selected: String, onSelected: (String) -> Unit) {
    val palettes = listOf(
        "Ocean" to Color(0xFF7C9CFF),
        "Violet" to Color(0xFFB69CFF),
        "Emerald" to Color(0xFF62C9A2),
        "Amber" to Color(0xFFFFC36F),
        "Rose" to Color(0xFFFF8FA3),
        "Monochrome" to Color(0xFFC9CDD4),
        "Crimson" to Color(0xFFD94A65),
        "Teal" to Color(0xFF36B9A8),
        "Cyan" to Color(0xFF43B8D2),
        "Indigo" to Color(0xFF6475E9),
        "Lime" to Color(0xFF9FBF42),
        "Graphite" to Color(0xFF8A939F)
    )
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("App colour palette", style = DsmusicTheme.typography.bodyLarge)
        Text(
            "Choose the main colour for the app. Colours from the current album cover can also appear in the interface.",
            style = DsmusicTheme.typography.bodySmall,
            color = DsmusicTheme.colorScheme.onSurfaceVariant
        )
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(13.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            palettes.forEach { (name, color) ->
                val active = selected == name
                Column(
                    Modifier.width(56.dp).clickable { onSelected(name) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Box(
                        Modifier.size(34.dp).clip(CircleShape)
                            .background(color)
                            .then(
                                if (active) Modifier.border(3.dp, DsmusicTheme.colorScheme.onSurface, CircleShape)
                                else Modifier.border(1.dp, DsmusicTheme.colorScheme.outlineVariant, CircleShape)
                            )
                    )
                    Text(
                        name,
                        style = DsmusicTheme.typography.labelSmall,
                        color = if (active) DsmusicTheme.colorScheme.onSurface else DsmusicTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
private fun SavedLooksEditor(
    looks: List<String>,
    onSave: (String) -> Unit,
    onApply: (String) -> Unit,
    onDelete: (String) -> Unit
) {
    var showSaveDialog by remember { mutableStateOf(false) }
    var lookName by remember { mutableStateOf("") }
    val accent = DsmusicTheme.colorScheme.primary
    val surface = DsmusicTheme.colorScheme.surfaceContainerLow
    val textColor = DsmusicTheme.colorScheme.onSurface
    val secondary = DsmusicTheme.colorScheme.onSurfaceVariant

    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            "Save your colours, player style, lyrics, text and navigation together. Switch between saved looks whenever you like.",
            style = DsmusicTheme.typography.bodySmall,
            color = secondary
        )
        Row(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
                .background(surface).clickable { lookName = ""; showSaveDialog = true }
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(Modifier.weight(1f)) {
                Text("Save current look", style = DsmusicTheme.typography.titleSmall, color = textColor)
                Text("Save this look so you can use it again", style = DsmusicTheme.typography.bodySmall, color = secondary)
            }
            Text("+", style = DsmusicTheme.typography.headlineSmall, color = accent)
        }
        if (looks.isEmpty()) {
            Text("No saved looks yet. Save your current setup to create your first one.", style = DsmusicTheme.typography.bodySmall, color = secondary)
        } else {
            looks.forEach { name ->
                Row(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                        .background(surface).padding(start = 14.dp, end = 10.dp, top = 10.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(name, style = DsmusicTheme.typography.titleSmall, color = textColor, maxLines = 1)
                        Text("Saved appearance", style = DsmusicTheme.typography.bodySmall, color = secondary)
                    }
                    Box(
                        Modifier.clip(RoundedCornerShape(50)).background(accent.copy(alpha = 0.16f))
                            .clickable { onApply(name) }.padding(horizontal = 13.dp, vertical = 8.dp)
                    ) {
                        Text("Apply", style = DsmusicTheme.typography.labelMedium, color = accent)
                    }
                    Box(
                        Modifier.padding(start = 6.dp).clip(RoundedCornerShape(50))
                            .clickable { onDelete(name) }.padding(horizontal = 10.dp, vertical = 8.dp)
                    ) {
                        Text("Delete", style = DsmusicTheme.typography.labelMedium, color = secondary)
                    }
                }
            }
        }
    }

    if (showSaveDialog) {
        Dialog(onDismissRequest = { showSaveDialog = false }) {
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp))
                    .background(DsmusicTheme.colorScheme.surface)
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text("Save this Look", style = DsmusicTheme.typography.titleLarge, color = textColor)
                Text(
                    "Your current colours, player style, buttons, lyrics and navigation will be saved together.",
                    style = DsmusicTheme.typography.bodySmall,
                    color = secondary
                )
                BasicTextField(
                    value = lookName,
                    onValueChange = { lookName = it.take(32) },
                    singleLine = true,
                    textStyle = DsmusicTheme.typography.bodyLarge.copy(color = textColor),
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                        .background(surface).padding(horizontal = 14.dp, vertical = 13.dp),
                    decorationBox = { inner ->
                        Box {
                            if (lookName.isEmpty()) {
                                Text("Look name", color = secondary, style = DsmusicTheme.typography.bodyLarge)
                            }
                            inner()
                        }
                    }
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.align(Alignment.End)) {
                    Box(
                        Modifier.clip(RoundedCornerShape(50)).clickable { showSaveDialog = false }
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                    ) { Text("Cancel", color = secondary, style = DsmusicTheme.typography.labelLarge) }
                    Box(
                        Modifier.clip(RoundedCornerShape(50))
                            .background(if (lookName.isBlank()) accent.copy(alpha = 0.28f) else accent)
                            .clickable(enabled = lookName.isNotBlank()) {
                                onSave(lookName.trim())
                                showSaveDialog = false
                            }
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                    ) { Text("Save", color = Color(0xFF0B1220), style = DsmusicTheme.typography.labelLarge) }
                }
            }
        }
    }
}

private val sessionButtonNames = linkedMapOf(
    "like" to "Like",
    "shuffle" to "Shuffle",
    "repeat" to "Repeat",
    "dislike" to "Not for me",
    "back10" to "Back 10 seconds",
    "ahead30" to "Forward 30 seconds"
)

@Composable
private fun SessionButtonsSettings(
    selected: List<String>,
    onSelectionChanged: (List<String>) -> Unit,
    onMove: (Int, Int) -> Unit
) {
    SettingsGroupCard(listOf {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                "Choose up to two actions for the notification and lock screen. Selecting another action when two are active replaces the second one.",
                style = DsmusicTheme.typography.bodySmall,
                color = DsmusicTheme.colorScheme.onSurfaceVariant
            )
            val ordered = selected.filter { it in sessionButtonNames.keys }
            ordered.forEachIndexed { index, key ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    com.dsann.music.ui.theme.Checkbox(
                        checked = true,
                        onCheckedChange = { enabled ->
                            if (!enabled) onSelectionChanged(ordered - key)
                        }
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(sessionButtonNames[key] ?: key, Modifier.weight(1f), style = DsmusicTheme.typography.bodyMedium)
                    DsmusicTextButton(onClick = { onMove(index, index - 1) }, enabled = index > 0) { Text("↑") }
                    DsmusicTextButton(onClick = { onMove(index, index + 1) }, enabled = index < ordered.lastIndex) { Text("↓") }
                }
            }
            sessionButtonNames.filterKeys { it !in ordered }.forEach { (key, label) ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    com.dsann.music.ui.theme.Checkbox(
                        checked = false,
                        onCheckedChange = { enabled ->
                            if (enabled) {
                                val nextSelection = if (ordered.size < 2) {
                                    ordered + key
                                } else {
                                    // Keep the first selected action and replace the
                                    // second slot so a full selector never blocks changes.
                                    ordered.dropLast(1) + key
                                }
                                onSelectionChanged(nextSelection)
                            }
                        }
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(label, Modifier.weight(1f), style = DsmusicTheme.typography.bodyMedium,
                        color = DsmusicTheme.colorScheme.onSurfaceVariant)
                }
            }
            Text(ordered.size.toString() + "/2 selected", color = DsmusicTheme.colorScheme.primary, style = DsmusicTheme.typography.labelSmall)
            if (ordered != listOf("like", "shuffle")) {
                DsmusicTextButton(
                    onClick = { onSelectionChanged(listOf("like", "shuffle")) }
                ) { Text("Restore default order") }
            }
        }
    })
}

private val playerButtonNames = linkedMapOf(
    "lyrics" to "Lyrics",
    "video" to "Video",
    "queue" to "Up next",
    "share" to "Share",
    "download" to "Download",
    "like" to "Like",
    "dislike" to "Not for me",
    "sleep" to "Sleep timer",
    "shuffle" to "Shuffle",
    "repeat" to "Repeat"
)

@Composable
private fun PlayerButtonsSettings(
    selected: List<String>,
    onSelectionChanged: (List<String>) -> Unit,
    onMove: (Int, Int) -> Unit
) {
    SettingsGroupCard(listOf {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                "Choose 3 to 6 actions and arrange their order in the player.",
                style = DsmusicTheme.typography.bodySmall,
                color = DsmusicTheme.colorScheme.onSurfaceVariant
            )
            val orderedSelected = selected.filter { it in playerButtonNames.keys }
            Text(
                "Shown in player · in this order",
                style = DsmusicTheme.typography.labelMedium,
                color = DsmusicTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)
            )
            // Render the selected actions from the saved list, not the master lookup map.
            // Iterating playerButtonNames here used to keep the original display order even
            // when the stored order changed, making the reorder controls appear broken.
            orderedSelected.forEachIndexed { index, key ->
                val label = playerButtonNames[key] ?: key
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    com.dsann.music.ui.theme.Checkbox(
                        checked = true,
                        onCheckedChange = { enabled ->
                            if (!enabled && orderedSelected.size > 3) {
                                onSelectionChanged(orderedSelected - key)
                            }
                        }
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(label, Modifier.weight(1f), style = DsmusicTheme.typography.bodyMedium)
                    DsmusicTextButton(
                        onClick = { onMove(index, index - 1) },
                        enabled = index > 0
                    ) { Text("↑") }
                    DsmusicTextButton(
                        onClick = { onMove(index, index + 1) },
                        enabled = index < orderedSelected.lastIndex
                    ) { Text("↓") }
                }
            }

            val availableActions = playerButtonNames.filterKeys { it !in orderedSelected }
            if (availableActions.isNotEmpty()) {
                Text(
                    "Available actions",
                    style = DsmusicTheme.typography.labelMedium,
                    color = DsmusicTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 10.dp, bottom = 2.dp)
                )
                availableActions.forEach { (key, label) ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        com.dsann.music.ui.theme.Checkbox(
                            checked = false,
                            enabled = orderedSelected.size < 6,
                            onCheckedChange = { enabled ->
                                if (enabled && orderedSelected.size < 6) {
                                    onSelectionChanged(orderedSelected + key)
                                }
                            }
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            label,
                            Modifier.weight(1f),
                            style = DsmusicTheme.typography.bodyMedium,
                            color = DsmusicTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            Text(
                "${selected.size}/6 selected",
                color = DsmusicTheme.colorScheme.primary,
                style = DsmusicTheme.typography.labelSmall
            )
            DsmusicTextButton(onClick = {
                onSelectionChanged(listOf("lyrics", "video", "queue", "share", "download"))
            }) { Text("Restore default buttons") }
        }
    })
}

@Composable
private fun ToggleSettingRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = DsmusicTheme.typography.bodyLarge)
            Text(description, style = DsmusicTheme.typography.bodySmall, color = DsmusicTheme.colorScheme.onSurfaceVariant)
        }
        DsmusicSwitch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun SettingsChoiceRow(
    title: String,
    description: String,
    selected: String,
    options: List<String>,
    onSelected: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        BasicText(
            title,
            style = DsmusicTheme.typography.bodyLarge.copy(
                color = DsmusicTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Medium
            )
        )
        BasicText(
            description,
            style = DsmusicTheme.typography.bodySmall.copy(
                color = DsmusicTheme.colorScheme.onSurfaceVariant
            )
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            options.forEach { option ->
                val active = selected == option
                val pillShape = RoundedCornerShape(50)
                Box(
                    modifier = Modifier
                        .clip(pillShape)
                        .background(
                            if (active) DsmusicTheme.colorScheme.primary.copy(alpha = 0.17f)
                            else DsmusicTheme.colorScheme.surfaceContainerHigh
                        )
                        .then(
                            if (active) Modifier.border(
                                1.dp,
                                DsmusicTheme.colorScheme.primary.copy(alpha = 0.65f),
                                pillShape
                            ) else Modifier
                        )
                        .clickable { onSelected(option) }
                        .padding(horizontal = 13.dp, vertical = 9.dp)
                ) {
                    BasicText(
                        text = option,
                        style = DsmusicTheme.typography.labelMedium.copy(
                            color = if (active) DsmusicTheme.colorScheme.primary
                                else DsmusicTheme.colorScheme.onSurfaceVariant,
                            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium
                        ),
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
private fun DsmusicSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier
) {
    val trackColor = if (checked) DsmusicTheme.colorScheme.primary
        else DsmusicTheme.colorScheme.surfaceVariant
    val thumbColor = if (checked) DsmusicTheme.colorScheme.onPrimary
        else DsmusicTheme.colorScheme.onSurfaceVariant

    Box(
        modifier = modifier
            .size(width = 44.dp, height = 26.dp)
            .clip(RoundedCornerShape(50))
            .background(trackColor)
            .clickable(enabled = onCheckedChange != null) {
                onCheckedChange?.invoke(!checked)
            }
            .padding(horizontal = 3.dp),
        contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Box(
            Modifier
                .size(20.dp)
                .clip(CircleShape)
                .background(thumbColor)
        )
    }
}

@Composable
private fun DynamicNavStyleRow(checked: Boolean, onToggle: (Boolean) -> Unit) {
    DsmusicListItem(
        onClick = { onToggle(!checked) },
        content = {
            Column {
                Text(
                    text = "Compact navigation bar",
                    style = DsmusicTheme.typography.bodyLarge
                )
                Text(
                    text = "Use a shorter navigation bar with larger, bolder icons.",
                    style = DsmusicTheme.typography.bodyMedium,
                    color = DsmusicTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        trailingContent = {
            DsmusicSwitch(
                checked = checked,
                onCheckedChange = onToggle
            )
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
}

@Composable
private fun HideNavLabelsRow(checked: Boolean, onToggle: (Boolean) -> Unit) {
    DsmusicListItem(
        onClick = { onToggle(!checked) },
        content = {
            Column {
                Text(
                    text = "Hide Navigation Labels",
                    style = DsmusicTheme.typography.bodyLarge
                )
                Text(
                    text = "Remove text labels from the bottom navigation bar",
                    style = DsmusicTheme.typography.bodyMedium,
                    color = DsmusicTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        trailingContent = {
            DsmusicSwitch(
                checked = checked,
                onCheckedChange = onToggle
            )
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
}

@Composable
private fun DynamicAlbumThemeRow(checked: Boolean, onToggle: (Boolean) -> Unit) {
    DsmusicListItem(
        onClick = { onToggle(!checked) },
        content = {
            Column {
                Text(
                    text = "Album cover colours",
                    style = DsmusicTheme.typography.bodyLarge
                )
                Text(
                    text = "Use colours from the current album cover throughout the app",
                    style = DsmusicTheme.typography.bodyMedium,
                    color = DsmusicTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        leadingContent = {
            Icon(
                imageVector = Icons.Rounded.Palette,
                contentDescription = null,
                tint = if (checked) DsmusicTheme.colorScheme.primary else DsmusicTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
        },
        trailingContent = {
            DsmusicSwitch(
                checked = checked,
                onCheckedChange = onToggle
            )
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
}

@Composable
private fun AmoledBlackRow(checked: Boolean, onToggle: (Boolean) -> Unit) {
    DsmusicListItem(
        onClick = { onToggle(!checked) },
        content = {
            Column {
                Text(
                    text = "Pure black background",
                    style = DsmusicTheme.typography.bodyLarge
                )
                Text(
                    text = "Use a completely black background in dark mode to save battery",
                    style = DsmusicTheme.typography.bodyMedium,
                    color = DsmusicTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        trailingContent = {
            DsmusicSwitch(
                checked = checked,
                onCheckedChange = onToggle
            )
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
}

@Composable
private fun PlaybackScreen(
    navController: androidx.navigation.NavController,
    crossfadeEnabled: Boolean,
    onCrossfadeToggle: (Boolean) -> Unit,
    crossfadeDuration: Float,
    onCrossfadeDurationChange: (Float) -> Unit,
    audioFocus: Boolean,
    onAudioFocusToggle: (Boolean) -> Unit,
    autoplayEnabled: Boolean,
    onAutoplayToggle: (Boolean) -> Unit,
    sleepTimerEndTime: Long,
    sleepTimerMode: PlayerSharedViewModel.SleepTimerMode,
    onStartSleepTimer: (Int) -> Unit,
    onCancelSleepTimer: () -> Unit,
    onSetEndOfSong: (Boolean) -> Unit,
    audioQuality: String,
    onAudioQualityChange: (String) -> Unit,
    downloadAudioFormat: String,
    onDownloadAudioFormatChange: (String) -> Unit,
    downloadBitrate: String,
    onDownloadBitrateChange: (String) -> Unit,
    playerFrameRate: Int,
    onPlayerFrameRateChange: (Int) -> Unit,
    pageTransitionSpeed: Float,
    onPageTransitionSpeedChange: (Float) -> Unit,
) {
    Scaffold(
        topBar = { SettingsDetailTopBar("Playback") { navController.popBackStack() } }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 120.dp)
        ) {
            SettingsAccordionSection("Autoplay") {
            SettingsGroupCard(listOf(
                { AutoplayRow(autoplayEnabled, onAutoplayToggle) }
            ))

            
            }
            SettingsAccordionSection("Song transitions") {
            SettingsGroupCard(listOf(
                { CrossfadeRow(crossfadeEnabled, onCrossfadeToggle, crossfadeDuration, onCrossfadeDurationChange) }
            ))

            
            }
            SettingsAccordionSection("Animation smoothness") {
            SettingsGroupCard(listOf {
                SettingsChoiceRow(
                    title = "Animation smoothness",
                    description = "Choose how smoothly animations appear. Device uses your screen's normal setting; lower values use less battery. Karaoke timing and music playback stay the same.",
                    selected = when (playerFrameRate) {
                        20 -> "Battery saver"
                        30 -> "Balanced"
                        40 -> "Smooth"
                        60 -> "Very smooth"
                        else -> "Device default"
                    },
                    options = listOf("Device default", "Battery saver", "Balanced", "Smooth", "Very smooth"),
                    onSelected = { selected ->
                        onPlayerFrameRateChange(
                            when (selected) {
                                "Battery saver" -> 20
                                "Balanced" -> 30
                                "Smooth" -> 40
                                "Very smooth" -> 60
                                else -> 0
                            }
                        )
                    }
                )
            })
            SettingsGroupCard(listOf {
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Page transition speed", style = DsmusicTheme.typography.bodyMedium)
                            Text(
                                "Control how quickly pages and tabs slide.",
                                style = DsmusicTheme.typography.bodySmall,
                                color = DsmusicTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            String.format(java.util.Locale.US, "%.2f×", pageTransitionSpeed),
                            style = DsmusicTheme.typography.labelLarge,
                            color = DsmusicTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Slider(
                        value = pageTransitionSpeed,
                        onValueChange = onPageTransitionSpeedChange,
                        valueRange = 0.5f..1.5f,
                        steps = 3,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                    )
                    Text(
                        "0.5× is slower · 1.5× is faster",
                        style = DsmusicTheme.typography.bodySmall,
                        color = DsmusicTheme.colorScheme.onSurfaceVariant
                    )
                }
            })

            
            }
            SettingsAccordionSection("Playback quality") {
            SettingsGroupCard(listOf(
                { AudioQualityRow("Playback quality", audioQuality, onAudioQualityChange) }
            ))

            
            }
            SettingsAccordionSection("Download Format") {
            SettingsGroupCard(listOf(
                { AudioFormatRow("Download Format", downloadAudioFormat, onDownloadAudioFormatChange) }
            ))

            
            }
            SettingsAccordionSection("Download quality") {
            SettingsGroupCard(listOf(
                { AudioQualityRow("Download quality", downloadBitrate, onDownloadBitrateChange) }
            ))

            
            }
            SettingsAccordionSection("Other app sounds") {
            SettingsGroupCard(listOf(
                { AudioFocusRow(audioFocus, onAudioFocusToggle) }
            ))

            
            }
            SettingsAccordionSection("Sleep Timer") {
            SettingsGroupCard(listOf(
                { SleepTimerSection(sleepTimerEndTime, sleepTimerMode, onStartSleepTimer, onCancelSleepTimer, onSetEndOfSong) }
            ))
            }
            
        }
    }
}

@Composable
private fun AutoplayRow(checked: Boolean, onToggle: (Boolean) -> Unit) {
    DsmusicListItem(
        onClick = { onToggle(!checked) },
        content = {
            Column {
                Text("Autoplay", style = DsmusicTheme.typography.bodyLarge)
                Text(
                    "Automatically play similar songs when the current list ends.",
                    style = DsmusicTheme.typography.bodyMedium,
                    color = DsmusicTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        trailingContent = {
            DsmusicSwitch(checked = checked, onCheckedChange = onToggle)
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
}


@Composable
private fun AudioQualityRow(
    title: String,
    currentQuality: String,
    onQualityChange: (String) -> Unit
) {
    val options = listOf("48 kbps", "64 kbps", "96 kbps", "128 kbps", "160 kbps", "192 kbps", "256 kbps", "320 kbps")

    DsmusicListItem(
        headlineContent = {
            Column {
                Text(text = title, style = DsmusicTheme.typography.bodyLarge)
                Text(
                    text = "Choose the sound quality. Higher numbers usually give better sound but may use more data or storage.",
                    style = DsmusicTheme.typography.bodyMedium,
                    color = DsmusicTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    options.forEach { option ->
                        val isSelected = option == currentQuality
                        DsmusicSurface(
                            shape = RoundedCornerShape(50),
                            color = if (isSelected) DsmusicTheme.colorScheme.primaryContainer else DsmusicTheme.colorScheme.surfaceVariant,
                            contentColor = if (isSelected) DsmusicTheme.colorScheme.onPrimaryContainer else DsmusicTheme.colorScheme.onSurfaceVariant,
                            onClick = { onQualityChange(option) }
                        ) {
                            Text(
                                text = option,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                style = DsmusicTheme.typography.labelLarge
                            )
                        }
                    }
                }
            }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
}


@Composable
private fun AudioFormatRow(
    title: String,
    currentFormat: String,
    onFormatChange: (String) -> Unit
) {
    val options = listOf(
        "m4a" to "M4A",
        "mp3" to "MP3",
        "opus" to "Opus"
    )
    val selectedFormat = com.dsann.music.data.innertube.NewPipeStreamExtractor
        .normalizeDownloadAudioFormat(currentFormat)

    DsmusicListItem(
        headlineContent = {
            Column {
                Text(text = title, style = DsmusicTheme.typography.bodyLarge)
                Text(
                    text = "Choose the file type for downloads. If a song is not available in one type, choose another.",
                    style = DsmusicTheme.typography.bodyMedium,
                    color = DsmusicTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    options.forEach { (value, label) ->
                        val isSelected = value == selectedFormat
                        DsmusicSurface(
                            shape = RoundedCornerShape(50),
                            color = if (isSelected) DsmusicTheme.colorScheme.primaryContainer else DsmusicTheme.colorScheme.surfaceVariant,
                            contentColor = if (isSelected) DsmusicTheme.colorScheme.onPrimaryContainer else DsmusicTheme.colorScheme.onSurfaceVariant,
                            onClick = { onFormatChange(value) }
                        ) {
                            Text(
                                text = label,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                style = DsmusicTheme.typography.labelLarge
                            )
                        }
                    }
                }
            }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
}


// ── Whats New ───────────────────────────────────────────────────────────────

@Composable
private fun WhatsNewScreen(navController: androidx.navigation.NavController) {
    val context = LocalContext.current
    var retryKey by remember { mutableStateOf(0) }
    val releaseLoad by androidx.compose.runtime.produceState<
        Triple<List<com.dsann.music.update.GitHubReleaseClient.ReleaseInfo>, Boolean, String?>?
    >(initialValue = null, retryKey) {
        value = withContext(Dispatchers.IO) {
            val client = com.dsann.music.update.GitHubReleaseClient("DS-ann", "Quantune")
            val loadedReleases = client.getAllReleases()
            val requestError = client.lastError
            Triple(
                loadedReleases,
                com.dsann.music.util.NetworkFailureClassifier.isNoInternetFailure(
                    requestError,
                    context
                ),
                requestError?.message
            )
        }
    }
    val releases = releaseLoad?.first.orEmpty()

    Scaffold(
        topBar = { SettingsDetailTopBar("What's New") { navController.popBackStack() } }
    ) { paddingValues ->
        if (releaseLoad == null) {
            Box(modifier = Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) {
                com.dsann.music.ui.theme.LoadingIndicator()
            }
        } else if (releaseLoad?.second == true) {
            com.dsann.music.ui.theme.NoInternetState(
                modifier = Modifier.fillMaxSize().padding(paddingValues),
                onRetry = { retryKey++ },
                message = "Release notes couldn't be loaded. Check your connection and retry."
            )
        } else if (!releaseLoad?.third.isNullOrBlank()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(paddingValues).padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        "Couldn't load release notes",
                        style = DsmusicTheme.typography.titleMedium
                    )
                    Text(
                        releaseLoad?.third.orEmpty(),
                        style = DsmusicTheme.typography.bodyMedium,
                        color = DsmusicTheme.colorScheme.onSurfaceVariant
                    )
                    DsmusicTextButton(onClick = { retryKey++ }) { Text("Try again") }
                }
            }
        } else if (releases.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) {
                Text("No releases found.", style = DsmusicTheme.typography.bodyLarge)
            }
        } else {
            androidx.compose.foundation.lazy.LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(top = 24.dp, bottom = 120.dp)
            ) {
                val groupedReleases = releases!!.groupBy { release ->
                    release.tagName.removePrefix("v").removePrefix("V").substringBefore('.')
                }

                groupedReleases.forEach { (majorVersion, majorReleases) ->
                    item(key = "header_$majorVersion") {
                        Text(
                            text = "Version $majorVersion Series",
                            style = DsmusicTheme.typography.titleMedium,
                            color = DsmusicTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
                        )
                    }

                    itemsIndexed(majorReleases, key = { _, rel -> rel.tagName }) { _, release ->
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)
                        ) {
                            DsmusicSurface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = getSettingsSegmentedShape(index = 0, count = 2),
                                color = DsmusicTheme.colorScheme.surfaceContainerHigh
                            ) {
                                DsmusicListItem(
                                    headlineContent = {
                                        Text(
                                            text = release.tagName,
                                            style = DsmusicTheme.typography.titleLarge,
                                            color = DsmusicTheme.colorScheme.primary,
                                            fontWeight = FontWeight.Bold
                                        )
                                    },
                                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                                )
                            }

                            DsmusicSurface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = getSettingsSegmentedShape(index = 1, count = 2),
                                color = DsmusicTheme.colorScheme.surfaceContainerHigh
                            ) {
                                val cleanedBody = release.changelog
                                    .replace(Regex("\\*\\*(.*?)\\*\\*"), "$1") // bold
                                    .replace(Regex("__(.*?)__"), "$1") // bold
                                    .replace(Regex("### (.*)"), "$1") // headers
                                    .replace(Regex("## (.*)"), "$1")
                                    .replace(Regex("# (.*)"), "$1")
                                    .trim()

                                DsmusicListItem(
                                    headlineContent = {
                                        Text(
                                            text = cleanedBody,
                                            style = DsmusicTheme.typography.bodyMedium,
                                            color = DsmusicTheme.colorScheme.onSurfaceVariant
                                        )
                                    },
                                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AboutScreen(
    navController: androidx.navigation.NavController,
    onCheckUpdate: () -> Unit,
    onWhatsNew: () -> Unit,
    onOpenUrl: (String) -> Unit
) {
    Scaffold(
        topBar = { SettingsDetailTopBar("About") { navController.popBackStack() } }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 620.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
                    .padding(top = 12.dp, bottom = 124.dp),
                verticalArrangement = Arrangement.spacedBy(22.dp)
            ) {
                AboutBrandCard()

                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "UPDATES & RELEASES",
                        style = DsmusicTheme.typography.labelLarge,
                        color = DsmusicTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.1.sp
                    )
                    Text(
                        "See what is new or check for the latest version.",
                        style = DsmusicTheme.typography.bodySmall,
                        color = DsmusicTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        DsmusicButton(
                            onClick = onCheckUpdate,
                            modifier = Modifier.weight(1f).height(52.dp),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Icon(Icons.Rounded.Update, null, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Updates", style = DsmusicTheme.typography.labelLarge)
                        }
                        DsmusicOutlinedButton(
                            onClick = onWhatsNew,
                            modifier = Modifier.weight(1f).height(52.dp),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Icon(Icons.Rounded.Info, null, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("What's New", style = DsmusicTheme.typography.labelLarge)
                        }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "THE DEVELOPER",
                        style = DsmusicTheme.typography.labelLarge,
                        color = DsmusicTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.1.sp
                    )
                    CreditsCard(onOpenUrl)
                }
            }
        }
    }
}

@Composable
private fun AboutBrandCard() {
    val shape = RoundedCornerShape(28.dp)
    val primaryContainer = DsmusicTheme.colorScheme.primaryContainer
    val secondaryContainer = DsmusicTheme.colorScheme.secondaryContainer
    val contentColor = DsmusicTheme.colorScheme.onPrimaryContainer

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(
                Brush.linearGradient(
                    colors = listOf(primaryContainer, secondaryContainer)
                )
            )
            .border(1.dp, DsmusicTheme.colorScheme.primary.copy(alpha = 0.22f), shape)
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 8.dp, end = 8.dp)
                .size(112.dp)
                .clip(CircleShape)
                .background(contentColor.copy(alpha = 0.045f))
        )
        Column(
            modifier = Modifier.fillMaxWidth().padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(82.dp)
                        .clip(RoundedCornerShape(23.dp))
                        .background(DsmusicTheme.colorScheme.surface.copy(alpha = 0.82f))
                        .border(
                            1.dp,
                            DsmusicTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
                            RoundedCornerShape(23.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_launcher_art),
                        contentDescription = "Quantune app icon",
                        tint = Color.Unspecified,
                        modifier = Modifier.size(70.dp)
                    )
                }
                Spacer(Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "ABOUT THE APP",
                        style = DsmusicTheme.typography.labelSmall,
                        color = contentColor.copy(alpha = 0.76f),
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp
                    )
                    Spacer(Modifier.height(5.dp))
                    Text(
                        stringResource(R.string.app_name),
                        style = DsmusicTheme.typography.headlineLarge,
                        color = contentColor,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        "Explore, organise and play your music in one place.",
                        style = DsmusicTheme.typography.bodySmall,
                        color = contentColor.copy(alpha = 0.82f)
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                DsmusicSurface(
                    shape = CircleShape,
                    color = contentColor.copy(alpha = 0.11f),
                    contentColor = contentColor,
                    showBorder = false
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 13.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Rounded.Info,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "VERSION ${BuildConfig.VERSION_NAME}",
                            style = DsmusicTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = contentColor
                        )
                    }
                }
                Spacer(Modifier.weight(1f))
                Text(
                    "MADE FOR MUSIC",
                    style = DsmusicTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    color = contentColor.copy(alpha = 0.7f)
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// COMPOSE UI — COMPONENTS
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun SettingsAccordionSection(
    text: String,
    content: @Composable () -> Unit
) {
    var expanded by remember(text) { mutableStateOf(false) }
    val arrowRotation by animateFloatAsState(
        targetValue = if (expanded) 90f else 0f,
        animationSpec = SmoothSpring
    )

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable { expanded = !expanded }
                .padding(start = 4.dp, end = 4.dp, top = 24.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(width = 4.dp, height = 18.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(DsmusicTheme.colorScheme.primary)
            )
            BasicText(
                text = text.uppercase(),
                style = DsmusicTheme.typography.titleMedium.copy(
                    color = DsmusicTheme.colorScheme.primary,
                    fontSize = 15.sp,
                    lineHeight = 20.sp,
                    letterSpacing = 1.15.sp,
                    fontWeight = FontWeight.Bold
                ),
                modifier = Modifier.weight(1f).padding(start = 9.dp),
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
            Icon(
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = if (expanded) "Collapse $text" else "Expand $text",
                tint = DsmusicTheme.colorScheme.onSurface,
                modifier = Modifier
                    .padding(end = 4.dp)
                    .size(20.dp)
                    .graphicsLayer { rotationZ = arrowRotation }
            )
        }

        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(
                expandFrom = Alignment.Top,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            ) + fadeIn(),
            exit = shrinkVertically(
                shrinkTowards = Alignment.Top,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            ) + fadeOut()
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                content()
            }
        }
    }
}

@Composable
private fun SettingsGroupCard(items: List<@Composable () -> Unit>) {
    val shape = DsmusicTheme.shapes.medium
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 7.dp)
            .clip(shape)
            .background(DsmusicTheme.colorScheme.surfaceContainerLow)
            .border(
                1.dp,
                DsmusicTheme.colorScheme.outlineVariant.copy(alpha = 0.52f),
                shape
            ),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        items.forEachIndexed { index, item ->
            item()
            if (index < items.lastIndex) {
                Box(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(1.dp)
                        .background(DsmusicTheme.colorScheme.outlineVariant.copy(alpha = 0.42f))
                )
            }
        }
    }
}

private fun getSettingsSegmentedShape(index: Int, count: Int): androidx.compose.ui.graphics.Shape {
    val outer = 32.dp
    val inner = 4.dp
    return when {
        count <= 1 -> RoundedCornerShape(outer)
        index == 0 -> RoundedCornerShape(topStart = outer, topEnd = outer, bottomStart = inner, bottomEnd = inner)
        index == count - 1 -> RoundedCornerShape(topStart = inner, topEnd = inner, bottomStart = outer, bottomEnd = outer)
        else -> RoundedCornerShape(inner)
    }
}

// ── Theme ────────────────────────────────────────────────────────────────────


@Composable
private fun ThemeRow(isDark: Boolean, onToggle: (Boolean) -> Unit) {
    DsmusicListItem(
        onClick = { onToggle(!isDark) },
        content = {
            Text(
                text = stringResource(R.string.setting_dark_mode),
                style = DsmusicTheme.typography.bodyLarge
            )
        },
        trailingContent = { ThemeDsmusicSwitch(isDark, onToggle) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
}

@Composable
private fun ThemeDsmusicSwitch(checked: Boolean, onCheckedChange: ((Boolean) -> Unit)?) {
    DsmusicSwitch(checked = checked, onCheckedChange = onCheckedChange)
}



// ── Wavy Divider ────────────────────────────────────────────────────────────

// ── Crossfade ───────────────────────────────────────────────────────────────


@Composable
private fun CrossfadeRow(
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
    duration: Float,
    onDurationChange: (Float) -> Unit
) {
    DsmusicListItem(
        onClick = { onToggle(!enabled) },
        verticalAlignment = Alignment.CenterVertically,
        content = {
            Column {
                Text(
                    text = stringResource(R.string.setting_crossfade),
                    style = DsmusicTheme.typography.bodyLarge
                )
                Text(
                    text = "Blend the end of one song into the start of the next.",
                    style = DsmusicTheme.typography.bodyMedium,
                    color = DsmusicTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        supportingContent = {
            Column {
                AnimatedContent(
                    targetState = if (enabled) "${duration.toInt()}s" else "Off",
                    transitionSpec = {
                        fadeIn(spring(stiffness = Spring.StiffnessMedium)) togetherWith
                            fadeOut(spring(stiffness = Spring.StiffnessMedium))
                    },
                    label = "crossfade_label"
                ) { label ->
                    Text(
                        text = label,
                        style = DsmusicTheme.typography.bodyMedium,
                        color = DsmusicTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                AnimatedVisibility(visible = enabled) {
                    Slider(
                        value = duration,
                        onValueChange = onDurationChange,
                        enabled = enabled,
                        valueRange = 0f..12f,
                        steps = 11,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
            }
        },
        trailingContent = { DsmusicSwitch(checked = enabled, onCheckedChange = onToggle) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
}

// ── Audio Focus ─────────────────────────────────────────────────────────────


@Composable
private fun AudioFocusRow(enabled: Boolean, onToggle: (Boolean) -> Unit) {
    DsmusicListItem(
        onClick = { onToggle(!enabled) },
        content = {
            Column {
                Text(
                    text = stringResource(R.string.setting_audio_focus),
                    style = DsmusicTheme.typography.bodyLarge
                )
                Text(
                    text = "Pause music when another app needs to play sound.",
                    style = DsmusicTheme.typography.bodyMedium,
                    color = DsmusicTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        leadingContent = {
            Icon(
                painter = painterResource(R.drawable.ic_audio_focus),
                contentDescription = null,
                tint = DsmusicTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
        },
        trailingContent = { DsmusicSwitch(checked = enabled, onCheckedChange = onToggle) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
}

// ── Sleep Timer ─────────────────────────────────────────────────────────────


@Composable
private fun SleepTimerSection(
    endTime: Long, 
    mode: PlayerSharedViewModel.SleepTimerMode,
    onStart: (Int) -> Unit, 
    onCancel: () -> Unit,
    onSetEndOfSong: (Boolean) -> Unit
) {
    var showBottomSheet by remember { mutableStateOf(false) }

    val remaining by remember(endTime) {
        derivedStateOf { endTime - System.currentTimeMillis() }
    }

    Column {
        DsmusicListItem(
            onClick = { showBottomSheet = true },
            content = {
                Text(
                    text = stringResource(R.string.setting_sleep_timer),
                    style = DsmusicTheme.typography.bodyLarge
                )
            },
            supportingContent = {
                val supportingText = when (mode) {
                    PlayerSharedViewModel.SleepTimerMode.OFF -> stringResource(R.string.setting_sleep_timer_off)
                    PlayerSharedViewModel.SleepTimerMode.END_OF_SONG -> "Stop at end of current song"
                    PlayerSharedViewModel.SleepTimerMode.END_OF_QUEUE -> "Stop after the current list finishes"
                    PlayerSharedViewModel.SleepTimerMode.CUSTOM -> {
                        if (remaining > 0) "${remaining / 60000} min remaining"
                        else stringResource(R.string.setting_sleep_timer_off)
                    }
                }
                Text(
                    text = supportingText,
                    style = DsmusicTheme.typography.bodyMedium,
                    color = DsmusicTheme.colorScheme.onSurfaceVariant
                )
            },
            leadingContent = {
                Icon(
                    painter = painterResource(R.drawable.ic_timer),
                    contentDescription = null,
                    tint = DsmusicTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp)
                )
            },
            trailingContent = {
                Icon(
                    imageVector = Icons.Rounded.ChevronRight,
                    contentDescription = null,
                    tint = DsmusicTheme.colorScheme.onSurface
                )
            },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
        )

        if (showBottomSheet) {
            SleepTimerBottomSheet(
                onDismissRequest = { showBottomSheet = false },
                sleepTimerEndTime = endTime,
                sleepTimerMode = mode,
                onStartTimer = { mins ->
                    onStart(mins)
                },
                onCancelTimer = {
                    onCancel()
                },
                onSetEndOfSong = { enable ->
                    onSetEndOfSong(enable)
                }
            )
        }
    }
}

// ── Library ─────────────────────────────────────────────────────────────────

@Composable
private fun LibrarySourceHeader() {
    DsmusicListItem(
        headlineContent = {
            Text(
                text = stringResource(R.string.setting_music_source),
                style = DsmusicTheme.typography.bodyLarge
            )
        },
        leadingContent = {
            Icon(
                painter = painterResource(R.drawable.ic_folder_open),
                contentDescription = null,
                tint = DsmusicTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
}


@Composable
private fun LibraryPathRow(path: String, onRemove: ((String) -> Unit)?) {
    DsmusicListItem(
        onClick = { },
        content = {
            Text(
                text = path.substringAfterLast(":"),
                style = DsmusicTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        leadingContent = {
            Icon(
                painter = painterResource(
                    if (onRemove != null) R.drawable.ic_folder_music else R.drawable.ic_folder_open
                ),
                contentDescription = null,
                tint = DsmusicTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
        },
        trailingContent = if (onRemove != null) {
            {
                Text(
                    text = "Remove",
                    style = DsmusicTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = DsmusicTheme.colorScheme.error,
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable { onRemove(path) }
                        .padding(8.dp)
                )
            }
        } else null,
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
}


@Composable
private fun AddMorePathRow(onClick: () -> Unit) {
    DsmusicListItem(
        onClick = onClick,
        content = {
            Text(
                text = stringResource(R.string.setting_add_more_path),
                style = DsmusicTheme.typography.bodyLarge,
                color = DsmusicTheme.colorScheme.primary
            )
        },
        leadingContent = {
            Icon(
                imageVector = Icons.Rounded.Add,
                contentDescription = null,
                tint = DsmusicTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
}

// ── Storage ─────────────────────────────────────────────────────────────────

@Composable
private fun DownloadFolderRow(path: String?, onClick: () -> Unit) {
    DsmusicListItem(
        onClick = onClick,
        content = {
            Column {
                Text(
                    text = "Download Location",
                    style = DsmusicTheme.typography.bodyLarge
                )
                Text(
                    text = path ?: "Not Set",
                    style = DsmusicTheme.typography.bodyMedium,
                    color = DsmusicTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        },
        leadingContent = {
            Icon(
                painter = painterResource(R.drawable.ic_folder_open),
                contentDescription = null,
                tint = DsmusicTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
}

@Composable
private fun CacheSizeRow(
    title: String,
    currentSize: String, // from calculation, e.g. "120 MB"
    maxSize: Int,
    onMaxSizeChange: (Int) -> Unit,
    onClear: () -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("Storage limit for $title") },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    listOf(100, 500, 1024, 2048, 0).forEach { sizeOption ->
                        val text = if (sizeOption == 0) "Unlimited" else if (sizeOption >= 1024) "${sizeOption / 1024} GB" else "$sizeOption MB"
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onMaxSizeChange(sizeOption)
                                    showDialog = false
                                }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = (maxSize == sizeOption),
                                onClick = {
                                    onMaxSizeChange(sizeOption)
                                    showDialog = false
                                }
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(text = text, style = DsmusicTheme.typography.bodyLarge)
                        }
                    }
                }
            },
            confirmButton = {
                DsmusicTextButton(onClick = { showDialog = false }) { Text("Cancel") }
            }
        )
    }

    // Extract numerical value roughly to show progress
    val currentBytes = parseCacheSize(currentSize)
    val maxBytes = if (maxSize == 0) 0L else maxSize * 1024L * 1024L
    val progress = if (maxBytes > 0) (currentBytes.toFloat() / maxBytes).coerceIn(0f, 1f) else 0f

    DsmusicListItem(
        onClick = { showDialog = true },
        content = {
            Column {
                Text(
                    text = title,
                    style = DsmusicTheme.typography.bodyLarge
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AnimatedContent(
                        targetState = currentSize,
                        transitionSpec = {
                            fadeIn(spring(stiffness = Spring.StiffnessMedium)) togetherWith
                                fadeOut(spring(stiffness = Spring.StiffnessMedium))
                        },
                        label = "cache_size"
                    ) { size ->
                        Text(
                            text = size,
                            style = DsmusicTheme.typography.bodyMedium,
                            color = DsmusicTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    
                    val maxText = if (maxSize == 0) "Unlimited" else if (maxSize >= 1024) "${maxSize / 1024} GB" else "$maxSize MB"
                    Text(
                        text = "Limit: $maxText",
                        style = DsmusicTheme.typography.bodyMedium,
                        color = DsmusicTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (maxBytes > 0) {
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(50)),
                        color = DsmusicTheme.colorScheme.primary,
                        trackColor = DsmusicTheme.colorScheme.surfaceVariant
                    )
                }
            }
        },
        trailingContent = {
            DsmusicIconButton(onClick = onClear) {
                Icon(
                    imageVector = Icons.Rounded.Delete,
                    contentDescription = "Clear temporary files",
                    tint = DsmusicTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
}

// Very basic string parsing for cache size
private fun parseCacheSize(sizeStr: String): Long {
    try {
        val split = sizeStr.trim().split(" ")
        if (split.size != 2) return 0L
        val value = split[0].toFloatOrNull() ?: return 0L
        val unit = split[1].uppercase()
        return when (unit) {
            "KB" -> (value * 1024).toLong()
            "MB" -> (value * 1024 * 1024).toLong()
            "GB" -> (value * 1024 * 1024 * 1024).toLong()
            "B" -> value.toLong()
            else -> 0L
        }
    } catch (e: Exception) {
        return 0L
    }
}

@Composable
private fun SongCacheSizeRow(
    cacheSize: String,
    maxSize: Int,
    onMaxSizeChange: (Int) -> Unit,
    onClear: () -> Unit
) {
    CacheSizeRow(
        title = "Temporary songs",
        currentSize = cacheSize,
        maxSize = maxSize,
        onMaxSizeChange = onMaxSizeChange,
        onClear = onClear
    )
}

@Composable
private fun ImageCacheSizeRow(
    cacheSize: String,
    maxSize: Int,
    onMaxSizeChange: (Int) -> Unit,
    onClear: () -> Unit
) {
    CacheSizeRow(
        title = "Temporary images",
        currentSize = cacheSize,
        maxSize = maxSize,
        onMaxSizeChange = onMaxSizeChange,
        onClear = onClear
    )
}

// ── Credits ─────────────────────────────────────────────────────────────────


@Composable
private fun CreditsCard(onOpenUrl: (String) -> Unit) {
    DsmusicCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = DsmusicTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                coil.compose.AsyncImage(
                    model = "https://github.com/DS-ann.png",
                    contentDescription = "Debarghya Sannigrahi",
                    modifier = Modifier
                        .size(66.dp)
                        .clip(CircleShape)
                        .border(
                            1.dp,
                            DsmusicTheme.colorScheme.outlineVariant,
                            CircleShape
                        ),
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop
                )
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Debarghya Sannigrahi",
                        style = DsmusicTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = DsmusicTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        "Quantune Developer",
                        style = DsmusicTheme.typography.bodySmall,
                        color = DsmusicTheme.colorScheme.onSurfaceVariant
                    )
                }
                Icon(
                    painter = painterResource(R.drawable.ic_github),
                    contentDescription = "GitHub",
                    tint = DsmusicTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(25.dp)
                )
            }

            DsmusicOutlinedButton(
                onClick = { onOpenUrl("https://github.com/DS-ann/Quantune") },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_github),
                    contentDescription = null,
                    modifier = Modifier.size(19.dp),
                    tint = DsmusicTheme.colorScheme.onSurface
                )
                Spacer(Modifier.width(9.dp))
                Text("View GitHub project", style = DsmusicTheme.typography.labelLarge)
                Spacer(Modifier.weight(1f))
                Icon(
                    Icons.Rounded.OpenInNew,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = DsmusicTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// ── App Logo ────────────────────────────────────────────────────────────────

@Composable
private fun AppLogoSection(isLandscape: Boolean) {
    val iconSize by animateDpAsState(
        targetValue = if (isLandscape) 160.dp else 250.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "logo_size"
    )
    val fontSize by animateFloatAsState(
        targetValue = if (isLandscape) 32f else 44f,
        animationSpec = SmoothSpring,
        label = "logo_text_size"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = if (isLandscape) 8.dp else 16.dp, bottom = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_launcher_art),
            contentDescription = null,
            tint = androidx.compose.ui.graphics.Color.Unspecified,
            modifier = Modifier.size(iconSize)
        )
        Text(
            text = stringResource(R.string.app_name),
            style = DsmusicTheme.typography.displayLarge,
            color = DsmusicTheme.colorScheme.primary,
            fontSize = fontSize.sp,
            letterSpacing = 0.sp
        )
        Text(
            text = "© 2026 Debarghya Sannigrahi",
            style = DsmusicTheme.typography.bodySmall,
            color = DsmusicTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun YouTubeMusicLoginDialog(
    onDismissRequest: () -> Unit,
    onLoginSuccess: (String) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = {},
        dismissButton = {
            DsmusicTextButton(onClick = onDismissRequest) {
                Text("Cancel", color = DsmusicTheme.colorScheme.primary)
            }
        },
        title = {
            Text(
                text = "Log In to YouTube Music",
                style = DsmusicTheme.typography.titleLarge,
                color = DsmusicTheme.colorScheme.onSurface
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth().height(420.dp)) {
                Text(
                    text = "Sign in to see your playlists, music library and recommendations.",
                    style = DsmusicTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 8.dp),
                    color = DsmusicTheme.colorScheme.onSurfaceVariant
                )
                
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.White)
                ) {
                    AndroidView(
                        factory = { context ->
                            WebView(context).apply {
                                settings.javaScriptEnabled = true
                                settings.domStorageEnabled = true
                                settings.userAgentString = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
                                
                                webViewClient = object : WebViewClient() {
                                    override fun onPageFinished(view: WebView?, url: String?) {
                                        super.onPageFinished(view, url)
                                        val cookieManager = CookieManager.getInstance()
                                        val cookies = cookieManager.getCookie("https://music.youtube.com")
                                        if (cookies != null && cookies.contains("SAPISID") && cookies.contains("HSID")) {
                                            onLoginSuccess(cookies)
                                        }
                                    }
                                }
                                
                                // Load Google login continuing to YouTube Music
                                loadUrl("https://accounts.google.com/ServiceLogin?service=youtube&passive=true&continue=https://music.youtube.com/")
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        },
        shape = RoundedCornerShape(28.dp),
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier.padding(horizontal = 24.dp)
    )
}

// ── Feedback & Bug Reports ──────────────────────────────────────────────────

@Composable
private fun FeedbackScreen(navController: androidx.navigation.NavController) {
    val context = LocalContext.current
    val debugInfo = remember {
        """
        Device: ${Build.MANUFACTURER} ${Build.MODEL}
        Android: ${Build.VERSION.RELEASE}
        SDK: ${Build.VERSION.SDK_INT}
        App Version: ${BuildConfig.VERSION_NAME}
        """.trimIndent()
    }

    Scaffold(
        topBar = { SettingsDetailTopBar("Feedback & Bug Reports") { navController.popBackStack() } }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 120.dp)
        ) {
            SettingsAccordionSection("Report & Request") {
            SettingsGroupCard(
                items = listOf(
                    {
                        DsmusicListItem(
                            headlineContent = { Text("Report a Bug", style = DsmusicTheme.typography.bodyLarge) },
                            supportingContent = { Text("Open a form to report a problem", style = DsmusicTheme.typography.bodyMedium) },
                            leadingContent = { Icon(Icons.Rounded.BugReport, null, tint = DsmusicTheme.colorScheme.error) },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            modifier = Modifier.clickable {
                                val title = URLEncoder.encode("[Bug] ", "UTF-8")
                                val body = URLEncoder.encode("## Description\n\n\n## Device Information\n$debugInfo\n\n## Steps to Reproduce\n1.\n2.\n3.\n", "UTF-8")
                                val url = "https://github.com/DS-ann/Quantune/issues/new?title=$title&body=$body"
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                            }
                        )
                    },
                    {
                        DsmusicListItem(
                            headlineContent = { Text("Request Feature", style = DsmusicTheme.typography.bodyLarge) },
                            supportingContent = { Text("Suggest a new feature for Quantune", style = DsmusicTheme.typography.bodyMedium) },
                            leadingContent = { Icon(Icons.Rounded.OpenInNew, null, tint = DsmusicTheme.colorScheme.primary) },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            modifier = Modifier.clickable {
                                val title = URLEncoder.encode("[Feature] ", "UTF-8")
                                val body = URLEncoder.encode("## Feature Description\n\n\n## Why is this needed?\n\n", "UTF-8")
                                val url = "https://github.com/DS-ann/Quantune/issues/new?title=$title&body=$body"
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                            }
                        )
                    }
                )
            )

            
            }
            SettingsAccordionSection("Debug Information") {
            SettingsGroupCard(
                items = listOf(
                    {
                        DsmusicListItem(
                            headlineContent = { Text("Copy Debug Information", style = DsmusicTheme.typography.bodyLarge) },
                            supportingContent = { Text("Copy your device and app version", style = DsmusicTheme.typography.bodyMedium) },
                            leadingContent = { Icon(Icons.Rounded.ContentCopy, null, tint = DsmusicTheme.colorScheme.onSurfaceVariant) },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            modifier = Modifier.clickable {
                                val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = ClipData.newPlainText("Debug Info", debugInfo)
                                clipboardManager.setPrimaryClip(clip)
                            }
                        )
                    },
                    {
                        DsmusicListItem(
                            headlineContent = { Text("Export Logs", style = DsmusicTheme.typography.bodyLarge) },
                            supportingContent = { Text("Share app details to help investigate a problem", style = DsmusicTheme.typography.bodyMedium) },
                            leadingContent = { Icon(Icons.Rounded.Description, null, tint = DsmusicTheme.colorScheme.onSurfaceVariant) },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            modifier = Modifier.clickable {
                                val sendIntent: Intent = Intent().apply {
                                    action = Intent.ACTION_SEND
                                    putExtra(Intent.EXTRA_TEXT, debugInfo)
                                    type = "text/plain"
                                }
                                context.startActivity(Intent.createChooser(sendIntent, "Export Logs"))
                            }
                        )
                    }
                )
            )
            }
            
        }
    }
}
