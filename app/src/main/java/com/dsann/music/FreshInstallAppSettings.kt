package com.dsann.music

import android.content.Context
import android.util.Log

/**
 * Applies the supplied Violet appearance/profile values only to a genuinely fresh install.
 * Existing installations and restored preferences are never overwritten.
 * The onboarding-completion flag is intentionally left untouched so first-run onboarding
 * still appears and records its own completion state.
 */
internal object FreshInstallAppSettings {
    private const val STATE_PREFS = "DsmusicInstallState"
    private const val APPLIED_KEY = "fresh_install_profile_defaults_v1_applied"
    private const val APP_SETTINGS_PREFS = "AppSettings"

    private val defaults: Map<String, Any> = linkedMapOf(
        "mini_swipe_right" to "SEEK_AHEAD",
        "ui_density" to "Comfortable",
        "player_times" to "Both",
        "show_synced_lyrics_in_player" to true,
        "amoled_black" to false,
        "mini_swipe_up" to "OPEN",
        "player_visualizer" to "Bars (16)",
        "player_visualizer_color_style" to "Gradient (Vertical)",
        "ui_font_weight" to "Lighter",
        "download_bitrate" to "128 kbps",
        "nav_bar_style" to "Floating",
        "sync_enabled" to true,
        "player_artwork_shape" to "Cookie",
        "custom_accent_color" to "#000000",
        "page_transition_speed" to 0.5f,
        "genre_card_gradient_style" to "Sunset",
        "cover_long_press" to "VIDEO",
        "player_buttons_pill" to true,
        "scrubber_color" to "Cover",
        "mini_player_control_icons" to "Classic",
        "player_title_weight" to "Light",
        "player_buttons" to "share,download,queue,lyrics,video,repeat",
        "player_artwork_scale" to 1.0f,
        "audio_quality" to "160 kbps",
        "mini_player_control_shape" to "Rounded",
        "lyrics_color_style" to "Dynamic (Gradient)",
        "ui_corner_style" to "Soft",
        "player_title_scroll" to true,
        "lyrics_font_family" to "Mono",
        "dynamic_album_theme" to true,
        "lyrics_text_size" to "Standard",
        "mini_player_style" to "Pill",
        "scrubber_thickness" to 1.1481087f,
        "dark_mode" to false,
        "ui_font_scale" to 0.9547876f,
        "ui_font_family" to "Serif",
        "theme_mode" to "system",
        "session_buttons" to "like,repeat",
        "player_button_style" to "Pill",
        "player_animation_frame_rate" to 20,
        "player_background_style" to "Artwork",
        "tapered_song_dividers" to true,
        "video_quality_preferred_height" to 240,
        "player_title_size" to "Large",
        "ui_letter_spacing" to "Normal",
        "appearance_preset" to "Violet",
        "nav_bar_size" to "Standard",
        "scrubber_style" to "Waveform",
        "download_audio_format" to "m4a",
        "nav_label_mode" to "Always",
        "now_playing_cover_animation" to "Bars",
        "mini_player_progress" to "Fill",
        "player_title_align" to "Centered",
        "scrubber_thumb_style" to "Ring",
        "player_layout" to "Standard",
        "video_quality_preferred_label" to "240p",
        "hide_nav_labels" to false
    )

    fun apply(context: Context) {
        val state = context.getSharedPreferences(STATE_PREFS, Context.MODE_PRIVATE)
        if (state.getBoolean(APPLIED_KEY, false)) return

        val appSettings = context.getSharedPreferences(APP_SETTINGS_PREFS, Context.MODE_PRIVATE)
        if (appSettings.all.isNotEmpty()) {
            // This is an existing installation; do not replace its saved preferences.
            state.edit().putBoolean(APPLIED_KEY, true).apply()
            return
        }

        val editor = appSettings.edit()
        defaults.forEach { (key, value) ->
            when (value) {
                is String -> editor.putString(key, value)
                is Boolean -> editor.putBoolean(key, value)
                is Int -> editor.putInt(key, value)
                is Float -> editor.putFloat(key, value)
                is Long -> editor.putLong(key, value)
            }
        }
        if (editor.commit()) {
            state.edit().putBoolean(APPLIED_KEY, true).apply()
        } else {
            Log.w("FreshInstallAppSettings", "Could not persist the fresh-install defaults; will retry on next launch")
        }
    }
}
