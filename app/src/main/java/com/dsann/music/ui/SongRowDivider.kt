package com.dsann.music.ui

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/** A single preference observer updates this option across all song lists. */
object SongRowDividerPreferences {
    private var preferences: SharedPreferences? = null
    private var listener: SharedPreferences.OnSharedPreferenceChangeListener? = null

    var enabled by mutableStateOf(false)
        private set

    @Synchronized
    fun initialize(context: Context) {
        if (preferences != null) return
        val prefs = context.applicationContext.getSharedPreferences(
            SettingsViewModel.PREFS_NAME,
            Context.MODE_PRIVATE
        )
        preferences = prefs
        enabled = prefs.getBoolean(SettingsViewModel.KEY_TAPERED_SONG_DIVIDERS, false)
        val changeListener = SharedPreferences.OnSharedPreferenceChangeListener { changedPrefs, key ->
            if (key == SettingsViewModel.KEY_TAPERED_SONG_DIVIDERS) {
                enabled = changedPrefs.getBoolean(SettingsViewModel.KEY_TAPERED_SONG_DIVIDERS, false)
            }
        }
        listener = changeListener
        prefs.registerOnSharedPreferenceChangeListener(changeListener)
    }

    fun set(context: Context, value: Boolean) {
        initialize(context)
        enabled = value
        preferences?.edit()
            ?.putBoolean(SettingsViewModel.KEY_TAPERED_SONG_DIVIDERS, value)
            ?.apply()
    }
}

@Composable
fun rememberTaperedSongDividersEnabled(): Boolean {
    val context = LocalContext.current.applicationContext
    remember(context) { SongRowDividerPreferences.initialize(context) }
    return SongRowDividerPreferences.enabled
}

/** A lens-shaped line with 0 dp at the edges and 2.2 dp at its midpoint. */
fun Modifier.taperedSongDivider(enabled: Boolean, color: Color): Modifier {
    if (!enabled) return this
    return drawWithContent {
        drawContent()
        if (size.width <= 0f || size.height <= 0f) return@drawWithContent
        val maxThickness = 2.2.dp.toPx()
        val centerY = size.height - maxThickness / 2f
        val path = Path().apply {
            moveTo(0f, centerY)
            quadraticTo(size.width / 2f, centerY - maxThickness, size.width, centerY)
            quadraticTo(size.width / 2f, centerY + maxThickness, 0f, centerY)
            close()
        }
        drawPath(path, color)
    }
}
