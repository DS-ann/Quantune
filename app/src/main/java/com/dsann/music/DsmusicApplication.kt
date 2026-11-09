package com.dsann.music

import android.app.Application
import android.content.ComponentCallbacks2
import android.os.Build
import android.util.Log
import androidx.appcompat.app.AppCompatDelegate
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.request.CachePolicy
import com.bumptech.glide.Glide
import com.bumptech.glide.GlideBuilder
import com.dsann.music.data.innertube.InnerTubeClient
import com.dsann.music.data.innertube.NewPipeStreamExtractor
import com.dsann.music.util.CacheManager
import dagger.hilt.android.HiltAndroidApp

/**
 * Keep Android/AppCompat resources in sync with the app's visible theme choice.
 * MODE_NIGHT_FOLLOW_SYSTEM is essential: resolving System to a saved boolean
 * pins the app to yesterday's device theme and prevents future system changes.
 */
internal fun applyDsmusicNightMode(themeMode: String?) {
    val appCompatMode = when (themeMode?.lowercase()) {
        "dark" -> AppCompatDelegate.MODE_NIGHT_YES
        "light" -> AppCompatDelegate.MODE_NIGHT_NO
        else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
    }
    AppCompatDelegate.setDefaultNightMode(appCompatMode)
}

@HiltAndroidApp
class DsmusicApplication : Application(), ImageLoaderFactory {

    companion object {
        lateinit var instance: DsmusicApplication
            private set

    }

    @Volatile
    private var activeImageLoader: ImageLoader? = null

    override fun newImageLoader(): ImageLoader {
        val builder = ImageLoader.Builder(this)
            .okHttpClient { InnerTubeClient.httpClient }
            .memoryCache {
                MemoryCache.Builder(this)
                    // Keep the decoded-artwork working set bounded on low-memory legacy devices.
                    // Original image files remain in the disk cache and can be decoded on demand.
                    .maxSizePercent(0.06)
                    .strongReferencesEnabled(true)
                    .weakReferencesEnabled(true)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(96L * 1024L * 1024L)
                    .build()
            }
            .allowHardware(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            .allowRgb565(true)
            .diskCachePolicy(CachePolicy.ENABLED)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .networkCachePolicy(CachePolicy.ENABLED)

        // Avoid retaining both old and new artwork bitmaps during every transition.
        builder.crossfade(false)

        return builder.build().also { activeImageLoader = it }
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        // RUNNING_LOW may arrive while the player/feed is still foregrounded.
        // Purging active covers here forces avoidable decode/reload work during scrolling.
        // Release the cache when the UI is hidden, or immediately for critical pressure.
        if (level >= ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN ||
            level == ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL
        ) {
            activeImageLoader?.memoryCache?.clear()
        }
    }

    override fun onLowMemory() {
        super.onLowMemory()
        activeImageLoader?.memoryCache?.clear()
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        // Seed the requested profile before any Settings or player ViewModel reads preferences.
        FreshInstallAppSettings.apply(this)
        // Defer stream extractor and cache maintenance initialization until after first draw.
        val appContext = applicationContext
        Thread({
            try {
                InnerTubeClient.initialize(appContext)
                NewPipeStreamExtractor.init(appContext)
                CacheManager.clearOldCache(appContext)
            } catch (error: Exception) {
                Log.e("DsmusicApplication", "Background startup initialization failed", error)
            }
        }, "D.S-Music-Startup").start()

        val prefs = getSharedPreferences("AppSettings", MODE_PRIVATE)
        // Do not turn "system" into a one-time light/dark snapshot. Android must
        // remain subscribed to device night-mode changes for this preference.
        applyDsmusicNightMode(prefs.getString("theme_mode", "system"))

        // Load saved YouTube Music cookies for account-backed features.
        prefs.getString("yt_cookies", null)?.let { InnerTubeClient.cookie = it }

        // Glide remains available for legacy image call sites; Compose artwork uses Coil above.
        Glide.init(this, GlideBuilder().setLogLevel(Log.ERROR))
    }
}
