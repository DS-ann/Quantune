package com.dsann.music.data.innertube

import android.content.Context
import android.content.SharedPreferences
import android.webkit.CookieManager
import android.webkit.WebStorage
import android.webkit.WebViewDatabase
import androidx.core.content.edit

/**
 * Facilitates application authentication state holding user-facing identity tokens 
 * and enabling account sync state tracking.
 */
object AccountManager {

    private const val PREFS_NAME = "AppSettings"
    private const val KEY_COOKIE_DATA = "yt_cookies"
    private const val KEY_SYNC_ENABLED = "sync_enabled"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun isLoggedIn(context: Context): Boolean {
        return !getAuthCookie(context).isNullOrEmpty()
    }

    fun getAccountName(context: Context): String {
        return if (isLoggedIn(context)) "Connected Account" else "Guest User"
    }

    fun getAuthCookie(context: Context): String? {
        return getPrefs(context).getString(KEY_COOKIE_DATA, null)
    }

    fun isSyncEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_SYNC_ENABLED, true)
    }

    fun saveLoginState(context: Context, cookie: String) {
        // Update persistent store
        getPrefs(context).edit {
            putString(KEY_COOKIE_DATA, cookie)
        }
        // Update immediate memory runtime in InnerTube facade
        InnerTubeClient.cookie = cookie
        InnerTubeClient.visitorData = null
    }

    fun setSyncEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit { putBoolean(KEY_SYNC_ENABLED, enabled) }
    }

    fun logout(context: Context) {
        // Remove the app's saved credentials and reset the in-memory InnerTube session.
        getPrefs(context).edit { remove(KEY_COOKIE_DATA) }
        InnerTubeClient.cookie = null
        InnerTubeClient.visitorData = null

        // WebView keeps Google/YouTube authentication independently of app preferences.
        // Remove cookies and origin storage so a later login starts with a signed-out
        // browser session. These APIs are available on the app's Android 6+ baseline.
        runCatching {
            val cookieManager = CookieManager.getInstance()
            cookieManager.removeAllCookies {
                runCatching { cookieManager.flush() }
            }
        }
        runCatching {
            WebStorage.getInstance().deleteAllData()
        }
        runCatching {
            WebViewDatabase.getInstance(context.applicationContext).apply {
                clearHttpAuthUsernamePassword()
                clearFormData()
            }
        }
    }
}
