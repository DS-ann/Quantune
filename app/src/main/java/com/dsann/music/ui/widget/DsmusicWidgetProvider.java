package com.dsann.music.ui.widget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.util.TypedValue;
import android.widget.RemoteViews;
import android.view.Gravity;

import androidx.annotation.NonNull;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.target.AppWidgetTarget;
import com.dsann.music.R;
import com.dsann.music.model.SongItem;

import java.io.File;
import java.util.Map;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Base Widget Provider for D.S Music.
 * Handles common playback control intents and updates all 3 widget sizes.
 */
public class DsmusicWidgetProvider extends AppWidgetProvider {

    private static final String TAG = "DsmusicWidget";
    // Intent Actions from AudioPlaybackService (must match exactly)
    private static final String ACTION_PLAY = "com.dsann.music.ACTION_PLAY";
    private static final String ACTION_PAUSE = "com.dsann.music.ACTION_PAUSE";
    private static final String ACTION_PREVIOUS = "com.dsann.music.ACTION_PREVIOUS";
    private static final String ACTION_NEXT = "com.dsann.music.ACTION_NEXT";
    private static final String ACTION_TOGGLE_LYRICS = "com.dsann.music.ACTION_TOGGLE_LYRICS";
    private static final String ACTION_WIDGET_TOGGLE_PLAY_PAUSE =
            "com.dsann.music.ACTION_WIDGET_TOGGLE_PLAY_PAUSE";
    private static final String ACTION_SMALL_LEFT_TAP = "com.dsann.music.ACTION_SMALL_LEFT_TAP";
    private static final String ACTION_SMALL_RIGHT_TAP = "com.dsann.music.ACTION_SMALL_RIGHT_TAP";
    private static final String PREF_ARTWORK_SONG_PREFIX = "small_art_song_";
    private static final String PREF_ARTWORK_CACHE_FILE_PREFIX = "widget_artwork_file_";
    private static final String PREF_ARTWORK_LOADED_KEY_PREFIX = "widget_artwork_loaded_key_";
    private static final String PREF_SMALL_ART_DIRECTION = "small_art_transition_direction";
    private static final int MAX_RETAINED_WIDGET_ARTWORKS = 8;
    private static final long[] WIDGET_ARTWORK_RETRY_DELAYS_MS =
            new long[]{2_000L, 5_000L, 12_000L, 25_000L, 45_000L, 60_000L};

    private static final Object WIDGET_ARTWORK_LOCK = new Object();
    private static final LinkedHashMap<Integer, Bitmap> LAST_WIDGET_ARTWORKS =
            new LinkedHashMap<Integer, Bitmap>(MAX_RETAINED_WIDGET_ARTWORKS, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<Integer, Bitmap> eldest) {
                    return size() > MAX_RETAINED_WIDGET_ARTWORKS;
                }
            };
    private static final Map<Integer, String> LAST_WIDGET_ARTWORK_KEYS = new HashMap<>();
    private static final Map<Integer, String> ACTIVE_WIDGET_ARTWORK_REQUESTS = new HashMap<>();
    private static final Map<Integer, Runnable> PENDING_WIDGET_ARTWORK_RETRIES = new HashMap<>();
    private static final Handler WIDGET_ARTWORK_RETRY_HANDLER =
            new Handler(Looper.getMainLooper());
    private static final long SMALL_WIDGET_DOUBLE_TAP_MS = 300L;

    private static final Handler SMALL_WIDGET_TAP_HANDLER = new Handler(Looper.getMainLooper());
    private static final Map<Integer, PendingSmallArtworkTap> PENDING_SMALL_ART_TAPS = new HashMap<>();

    private static final class PendingSmallArtworkTap {
        final int direction;
        final long firstTapAtMs;
        final Runnable singleTapAction;

        PendingSmallArtworkTap(int direction, long firstTapAtMs, Runnable singleTapAction) {
            this.direction = direction;
            this.firstTapAtMs = firstTapAtMs;
            this.singleTapAction = singleTapAction;
        }
    }
    
    private static final String PREFS_NAME = "dsmusic_widget_prefs";
    private static final String PREF_LYRICS_MODE = "lyrics_mode_";

    /**
     * Carries widget lyrics-page choices to the current preference file.
     * The previous file is located by its own widget setting keys.
     */
    private static void migrateWidgetPreferences(Context context) {
        SharedPreferences target = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        if (target.getBoolean("_dsmusic_storage_migrated", false)) return;

        File directory = new File(context.getApplicationInfo().dataDir, "shared_prefs");
        File[] files = directory.listFiles();
        if (files != null) {
            for (File file : files) {
                String filename = file.getName();
                if (!filename.endsWith(".xml") || filename.equals(PREFS_NAME + ".xml")) continue;

                String sourceName = filename.substring(0, filename.length() - 4);
                SharedPreferences source = context.getSharedPreferences(sourceName, Context.MODE_PRIVATE);
                SharedPreferences.Editor destination = target.edit();
                SharedPreferences.Editor cleanup = source.edit();
                boolean copied = false;

                for (Map.Entry<String, ?> entry : source.getAll().entrySet()) {
                    String key = entry.getKey();
                    Object value = entry.getValue();
                    if (!key.startsWith(PREF_LYRICS_MODE) || !(value instanceof Boolean)) continue;
                    if (!target.contains(key)) destination.putBoolean(key, (Boolean) value);
                    cleanup.remove(key);
                    copied = true;
                }

                if (copied && destination.commit()) {
                    cleanup.commit();
                    if (source.getAll().isEmpty()) file.delete();
                }
            }
        }
        target.edit().putBoolean("_dsmusic_storage_migrated", true).apply();
    }

    // Keep RemoteViews/AppWidgetManager Binder work away from the activity's main looper.
    // A single worker also preserves update order across rapid playback/lyrics callbacks.
    private static final ExecutorService PARTIAL_WIDGET_UPDATE_EXECUTOR =
            Executors.newSingleThreadExecutor(runnable -> {
                Thread worker = new Thread(runnable, "DsmusicWidgetUpdates");
                worker.setPriority(Thread.NORM_PRIORITY);
                return worker;
            });
    // Progress ticks arrive once per second. Keep only the newest pending snapshot so a
    // slow AppWidgetManager/Binder call cannot build an unbounded queue of stale positions.
    private static final AtomicReference<WidgetProgressUpdate> PENDING_PARTIAL_WIDGET_UPDATE =
            new AtomicReference<>();
    private static final AtomicReference<WidgetLyricsUpdate> PENDING_WIDGET_LYRICS_UPDATE =
            new AtomicReference<>();
    private static final AtomicBoolean PARTIAL_WIDGET_UPDATE_SCHEDULED = new AtomicBoolean(false);

    private static final class WidgetProgressUpdate {
        final Context context;
        final int position;
        final int duration;

        WidgetProgressUpdate(Context context, int position, int duration) {
            this.context = context;
            this.position = position;
            this.duration = duration;
        }
    }

    private static final class WidgetLyricsUpdate {
        final Context context;
        final CharSequence currentLyric;

        WidgetLyricsUpdate(Context context, CharSequence currentLyric) {
            this.context = context;
            this.currentLyric = currentLyric;
        }
    }

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        try {
            migrateWidgetPreferences(context);
        } catch (RuntimeException migrationError) {
            Log.w(TAG, "Widget preference migration failed; using current defaults", migrationError);
        }
        int layoutId = R.layout.widget_medium;
        Class<?> providerClass = WidgetMediumProvider.class;
        if (this instanceof WidgetSmallProvider) {
            layoutId = R.layout.widget_small;
            providerClass = WidgetSmallProvider.class;
        } else if (this instanceof WidgetLargeProvider) {
            layoutId = R.layout.widget_large;
            providerClass = WidgetLargeProvider.class;
        }
        for (int appWidgetId : appWidgetIds) {
            updateWidget(context, appWidgetManager, appWidgetId, null, false, 0, 0, null, R.drawable.ic_repeat, layoutId, providerClass);
        }

        // onUpdate has no access to the current playback item itself. Ask the running playback
        // service to replace these initial placeholders with the real track/position immediately;
        // otherwise widgets added while a song is already playing can remain apparently blank.
        try {
            Intent refreshIntent = new Intent(context, com.dsann.music.service.AudioPlaybackService.class);
            refreshIntent.setAction("com.dsann.music.ACTION_WIDGET_REFRESH_STATE");
            context.startService(refreshIntent);
        } catch (Exception e) {
            Log.w(TAG, "Could not refresh widget state after adding a widget", e);
        }
    }

    /** Returns whether any D.S Music widget is installed on the current launcher. */
    public static boolean hasAnyWidgets(Context context) {
        try {
            AppWidgetManager manager = AppWidgetManager.getInstance(context.getApplicationContext());
            return manager.getAppWidgetIds(
                    new ComponentName(context, WidgetSmallProvider.class)).length > 0
                    || manager.getAppWidgetIds(
                    new ComponentName(context, WidgetMediumProvider.class)).length > 0
                    || manager.getAppWidgetIds(
                    new ComponentName(context, WidgetLargeProvider.class)).length > 0;
        } catch (RuntimeException error) {
            Log.w(TAG, "Could not query installed widgets", error);
            // Preserve the playback service if launcher state cannot be queried safely.
            return true;
        }
    }

    public static void updateAllWidgets(Context context, SongItem currentSong, boolean isPlaying, int position,
            int duration, CharSequence currentLyric) {
        updateAllWidgets(context, currentSong, isPlaying, position, duration, currentLyric, R.drawable.ic_repeat);
    }

    public static void updateAllWidgets(Context context, SongItem currentSong, boolean isPlaying, int position,
            int duration, CharSequence currentLyric, int repeatIconResId) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        new Handler(Looper.getMainLooper()).post(() -> {
            // Update Small Widgets
            int[] smallIds = manager.getAppWidgetIds(new ComponentName(context, WidgetSmallProvider.class));
            for (int id : smallIds) {
                updateWidget(context, manager, id, currentSong, isPlaying, position, duration, currentLyric, repeatIconResId, R.layout.widget_small, WidgetSmallProvider.class);
            }

            // Update Medium Widgets
            int[] mediumIds = manager.getAppWidgetIds(new ComponentName(context, WidgetMediumProvider.class));
            for (int id : mediumIds) {
                updateWidget(context, manager, id, currentSong, isPlaying, position, duration, currentLyric, repeatIconResId, R.layout.widget_medium, WidgetMediumProvider.class);
            }

            // Update Large Widgets
            int[] largeIds = manager.getAppWidgetIds(new ComponentName(context, WidgetLargeProvider.class));
            for (int id : largeIds) {
                updateWidget(context, manager, id, currentSong, isPlaying, position, duration, currentLyric, repeatIconResId, R.layout.widget_large, WidgetLargeProvider.class);
            }
        });
    }

    /**
     * True when any widget capable of displaying lyrics is installed.
     *
     * Refresh eligibility is deliberately independent of the switchable 4x2 widget's
     * current ViewFlipper page. The always-visible lyrics widget must keep its own line
     * updates even while every switchable 4x2 instance is showing its song page.
     * Updating the hidden lyrics page is cheap and means it is current when opened.
     * The 2x2 artwork-only widget is excluded.
     */
    public static boolean hasLyricsWidgetInstalled(Context context) {
        try {
            AppWidgetManager manager = AppWidgetManager.getInstance(context.getApplicationContext());
            int[] largeIds = manager.getAppWidgetIds(
                    new ComponentName(context, WidgetLargeProvider.class));
            if (largeIds.length > 0) return true;

            int[] mediumIds = manager.getAppWidgetIds(
                    new ComponentName(context, WidgetMediumProvider.class));
            return mediumIds.length > 0;
        } catch (RuntimeException error) {
            Log.w(TAG, "Could not inspect installed lyrics widgets", error);
        }
        return false;
    }

    private static void applyLyricsPresentation(Context context, RemoteViews views, CharSequence lyrics, int layoutId) {
        views.setTextViewText(R.id.widget_lyrics_text, lyrics != null ? lyrics : "...");
        SharedPreferences appearance = context.getSharedPreferences("AppSettings", Context.MODE_PRIVATE);
        String size = appearance.getString("lyrics_text_size", "Standard");
        float textSize;
        if (layoutId == R.layout.widget_medium) {
            // The switchable 4x2 widget uses oversized lyrics; its TextView consumes
            // the available text-column height and wraps the full lyric without a card.
            textSize = ("Small".equals(size) ? 18.5f : "Large".equals(size) ? 23.5f : 21f) * 2f;
        } else {
            textSize = "Small".equals(size) ? 16.5f : "Large".equals(size) ? 21.5f : 19f;
        }
        views.setTextViewTextSize(
                R.id.widget_lyrics_text, TypedValue.COMPLEX_UNIT_SP, textSize);
        String alignment = appearance.getString("lyrics_alignment", "Original");
        int horizontalGravity = "Center".equals(alignment) ? Gravity.CENTER_HORIZONTAL : Gravity.START;
        views.setInt(
                R.id.widget_lyrics_text, "setGravity",
                horizontalGravity | Gravity.CENTER_VERTICAL);
    }

    /** Queues a progress-only update. It never changes the displayed lyric line. */
    public static void updateAllWidgetsProgress(Context context, int position, int duration) {
        final Context appContext = context.getApplicationContext();
        PENDING_PARTIAL_WIDGET_UPDATE.set(
                new WidgetProgressUpdate(appContext, position, duration));
        schedulePartialWidgetUpdateDrain();
    }

    /** Queues a lyric-boundary update independently from periodic progress ticks. */
    public static void updateAllWidgetsLyrics(Context context, CharSequence currentLyric) {
        final Context appContext = context.getApplicationContext();
        PENDING_WIDGET_LYRICS_UPDATE.set(
                new WidgetLyricsUpdate(appContext, currentLyric != null ? currentLyric : "..."));
        schedulePartialWidgetUpdateDrain();
    }

    /** Compatibility entry point for callers that need both values refreshed together. */
    public static void updateAllWidgetsPartial(
            Context context, int position, int duration, CharSequence currentLyric) {
        updateAllWidgetsProgress(context, position, duration);
        updateAllWidgetsLyrics(context, currentLyric);
    }

    /**
     * Re-apply the transport controls after the device wakes. Some launchers defer a
     * RemoteViews update while keyguard is closing; this small partial update restores
     * current play/pause PendingIntents without reloading or blurring artwork.
     */
    public static void refreshAllWidgetControls(Context context, boolean isPlaying) {
        final Context appContext = context.getApplicationContext();
        try {
            PARTIAL_WIDGET_UPDATE_EXECUTOR.execute(() -> {
                AppWidgetManager manager = AppWidgetManager.getInstance(appContext);
                refreshWidgetControls(
                        appContext, manager, WidgetSmallProvider.class,
                        R.layout.widget_small, isPlaying);
                refreshWidgetControls(
                        appContext, manager, WidgetMediumProvider.class,
                        R.layout.widget_medium, isPlaying);
                refreshWidgetControls(
                        appContext, manager, WidgetLargeProvider.class,
                        R.layout.widget_large, isPlaying);
            });
        } catch (RuntimeException error) {
            Log.w(TAG, "Could not schedule widget control refresh", error);
        }
    }

    private static void refreshWidgetControls(
            Context context,
            AppWidgetManager manager,
            Class<?> providerClass,
            int layoutId,
            boolean isPlaying
    ) {
        int[] ids = manager.getAppWidgetIds(new ComponentName(context, providerClass));
        for (int id : ids) {
            try {
                RemoteViews views = new RemoteViews(context.getPackageName(), layoutId);
                applyWidgetActionIcons(
                        views,
                        isPlaying,
                        layoutId,
                        0
                );
                setupPendingIntents(context, views, isPlaying, layoutId, providerClass, id);
                manager.partiallyUpdateAppWidget(id, views);
            } catch (Exception error) {
                Log.w(TAG, "Could not refresh widget controls for " + id, error);
            }
        }
    }

    private static void schedulePartialWidgetUpdateDrain() {
        if (!PARTIAL_WIDGET_UPDATE_SCHEDULED.compareAndSet(false, true)) {
            return;
        }

        try {
            PARTIAL_WIDGET_UPDATE_EXECUTOR.execute(() -> {
                while (true) {
                    // Apply lyric changes first. They are independent from progress and cannot
                    // be overwritten by a later 2-second progress snapshot.
                    WidgetLyricsUpdate lyricsUpdate =
                            PENDING_WIDGET_LYRICS_UPDATE.getAndSet(null);
                    if (lyricsUpdate != null) {
                        applyPartialWidgetLyrics(lyricsUpdate);
                    }

                    WidgetProgressUpdate progressUpdate =
                            PENDING_PARTIAL_WIDGET_UPDATE.getAndSet(null);
                    if (progressUpdate != null) {
                        applyPartialWidgetProgress(progressUpdate);
                    }

                    PARTIAL_WIDGET_UPDATE_SCHEDULED.set(false);
                    // Handle updates arriving during Binder work without losing either channel.
                    if ((PENDING_PARTIAL_WIDGET_UPDATE.get() == null &&
                            PENDING_WIDGET_LYRICS_UPDATE.get() == null) ||
                            !PARTIAL_WIDGET_UPDATE_SCHEDULED.compareAndSet(false, true)) {
                        return;
                    }
                }
            });
        } catch (RuntimeException error) {
            PARTIAL_WIDGET_UPDATE_SCHEDULED.set(false);
            Log.w(TAG, "Could not schedule widget progress update", error);
        }
    }

    private static void applyPartialWidgetProgress(WidgetProgressUpdate update) {
        Context appContext = update.context;
        AppWidgetManager manager = AppWidgetManager.getInstance(appContext);
        try {
            int[] mediumIds = manager.getAppWidgetIds(
                    new ComponentName(appContext, WidgetMediumProvider.class));
            int[] largeIds = manager.getAppWidgetIds(
                    new ComponentName(appContext, WidgetLargeProvider.class));
            if (mediumIds.length == 0 && largeIds.length == 0) {
                return;
            }

            for (int id : mediumIds) {
                updateWidgetProgress(
                        appContext, manager, id, update.position, update.duration,
                        R.layout.widget_medium);
            }
            for (int id : largeIds) {
                updateWidgetProgress(
                        appContext, manager, id, update.position, update.duration,
                        R.layout.widget_large);
            }
        } catch (Exception error) {
            Log.w(TAG, "Widget progress update failed", error);
        }
    }

    private static void updateWidgetProgress(
            Context context,
            AppWidgetManager manager,
            int id,
            int position,
            int duration,
            int layoutId
    ) {
        try {
            RemoteViews views = new RemoteViews(context.getPackageName(), layoutId);
            int progress = duration > 0
                    ? (int) (((float) Math.max(0, position) / duration) * 100f)
                    : 0;
            views.setProgressBar(R.id.widget_progress, 100, Math.max(0, Math.min(100, progress)), false);
            manager.partiallyUpdateAppWidget(id, views);
        } catch (Exception e) {
            Log.e(TAG, "Error in partial widget update: " + e.getMessage());
        }
    }

    private static void applyPartialWidgetLyrics(WidgetLyricsUpdate update) {
        Context appContext = update.context;
        AppWidgetManager manager = AppWidgetManager.getInstance(appContext);
        try {
            int[] mediumIds = manager.getAppWidgetIds(
                    new ComponentName(appContext, WidgetMediumProvider.class));
            int[] largeIds = manager.getAppWidgetIds(
                    new ComponentName(appContext, WidgetLargeProvider.class));

            for (int id : mediumIds) {
                updateWidgetLyrics(
                        appContext, manager, id, update.currentLyric, R.layout.widget_medium);
            }
            for (int id : largeIds) {
                updateWidgetLyrics(
                        appContext, manager, id, update.currentLyric, R.layout.widget_large);
            }
        } catch (Exception error) {
            Log.w(TAG, "Widget lyric-boundary update failed", error);
        }
    }

    private static void updateWidgetLyrics(
            Context context,
            AppWidgetManager manager,
            int id,
            CharSequence currentLyric,
            int layoutId
    ) {
        try {
            RemoteViews views = new RemoteViews(context.getPackageName(), layoutId);
            applyLyricsPresentation(context, views, currentLyric, layoutId);
            manager.partiallyUpdateAppWidget(id, views);
        } catch (Exception error) {
            Log.w(TAG, "Error applying widget lyrics for " + id, error);
        }
    }

    private static void setWidgetIcon(RemoteViews views, int viewId, int drawableId) {
        views.setImageViewResource(viewId, drawableId);
        views.setInt(viewId, "setColorFilter", Color.WHITE);
    }

    private static void applyWidgetActionIcons(
            RemoteViews views,
            boolean isPlaying,
            int layoutId,
            int repeatIconResId
    ) {
        // The 2x2 layout is artwork-only; its click action toggles playback directly.
        if (layoutId == R.layout.widget_small) return;

        // Set drawable resources explicitly instead of relying on the launcher's
        // interpretation of XML ImageButton defaults or inherited theme tints.
        setWidgetIcon(views, R.id.widget_play_pause,
                isPlaying ? R.drawable.widget_ic_pause : R.drawable.widget_ic_play);

        if (layoutId == R.layout.widget_medium || layoutId == R.layout.widget_large) {
            setWidgetIcon(views, R.id.widget_prev, R.drawable.widget_ic_previous);
            setWidgetIcon(views, R.id.widget_next, R.drawable.widget_ic_next);
        }

        if (layoutId == R.layout.widget_medium) {
            setWidgetIcon(views, R.id.widget_toggle_lyrics, R.drawable.widget_ic_lyrics);
        } else if (layoutId == R.layout.widget_large) {
            setWidgetIcon(views, R.id.widget_favorite, R.drawable.widget_ic_favorite);
            if (repeatIconResId != 0) {
                setWidgetIcon(views, R.id.widget_repeat, repeatIconResId);
            }
        }
    }

    private static void updateWidget(
            Context context,
            AppWidgetManager manager,
            int id,
            SongItem song,
            boolean isPlaying,
            int position,
            int duration,
            CharSequence currentLyric,
            int repeatIconResId,
            int layoutId,
            Class<?> providerClass
    ) {
        try {
            RemoteViews views = hideSkeleton(context, layoutId);

            if (layoutId != R.layout.widget_small) {
                views.setTextViewText(
                        R.id.widget_song_title,
                        song != null && song.title != null ? song.title : "D.S Music"
                );
                views.setTextViewText(
                        R.id.widget_artist_name,
                        song != null && song.artist != null ? song.artist : "Select a song"
                );

                int progress = duration > 0
                        ? (int) (((float) Math.max(0, position) / duration) * 100f)
                        : 0;
                views.setProgressBar(
                        R.id.widget_progress, 100, Math.max(0, Math.min(100, progress)), false);
            }

            if (layoutId == R.layout.widget_medium) {
                applyLyricsPresentation(context, views, currentLyric, layoutId);
                boolean isLyricsMode = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                        .getBoolean(PREF_LYRICS_MODE + id, false);
                views.setDisplayedChild(R.id.widget_flipper, isLyricsMode ? 1 : 0);
            } else if (layoutId == R.layout.widget_large) {
                applyLyricsPresentation(context, views, currentLyric, layoutId);
            }

            // Apply icon resources, tint, and click actions on every full refresh,
            // including the initial empty-state refresh before a song is selected.
            applyWidgetActionIcons(views, isPlaying, layoutId, repeatIconResId);
            setupPendingIntents(context, views, isPlaying, layoutId, providerClass, id);
            Uri artworkUri = null;
            if (song != null) {
                try {
                    artworkUri = song.getAlbumArtUri();
                } catch (Exception artworkError) {
                    Log.w(TAG, "Could not resolve widget artwork for " + song.title, artworkError);
                }
            }

            String artworkKey = song != null
                    ? song.id + "|" + (artworkUri != null ? artworkUri.toString() : "__no_art_uri__")
                    : "__no_art__";
            boolean animateSmallCover = false;
            int smallCoverDirection = 1;
            SharedPreferences widgetPrefs =
                    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            String artworkPrefKey = PREF_ARTWORK_SONG_PREFIX + id;
            String previousArtworkKey = widgetPrefs.getString(artworkPrefKey, null);
            boolean artworkRequestKeyChanged = !Objects.equals(previousArtworkKey, artworkKey);

            if (artworkUri != null && song != null && layoutId == R.layout.widget_small) {
                animateSmallCover = previousArtworkKey != null &&
                        !previousArtworkKey.equals(artworkKey);
                smallCoverDirection = widgetPrefs.getInt(PREF_SMALL_ART_DIRECTION, 1);
            }

            if (artworkRequestKeyChanged) {
                cancelWidgetArtworkRetry(id);
                clearActiveWidgetArtworkRequest(id);
            }
            // The current request key rejects late responses from a previous song. Keep the
            // last successfully rendered bitmap visible while a new cover is loading.
            widgetPrefs.edit().putString(artworkPrefKey, artworkKey).apply();

            if (song == null) {
                clearRetainedWidgetArtwork(context, id);
                if (layoutId == R.layout.widget_small) {
                    applySmallWidgetIdleArtwork(context, views, id);
                } else {
                    views.setImageViewResource(R.id.widget_album_art, R.drawable.widget_default_art);
                }
            } else {
                Bitmap retainedArtwork = getWidgetArtworkForSnapshot(
                        context, id, String.valueOf(song.id), artworkKey);
                if (retainedArtwork != null) {
                    views.setImageViewBitmap(R.id.widget_album_art, retainedArtwork);
                } else if (layoutId == R.layout.widget_small) {
                    views.setImageViewResource(R.id.widget_album_art, R.drawable.widget_idle_art);
                } else {
                    views.setImageViewResource(R.id.widget_album_art, R.drawable.widget_default_art);
                }
            }

            manager.updateAppWidget(id, views);

            if (artworkUri != null && song != null) {
                boolean hasSongArtworkCache = hasWidgetArtworkCache(context, String.valueOf(song.id), artworkKey);
                boolean requestInFlight = isWidgetArtworkRequestActive(id, artworkKey)
                        || isWidgetArtworkRetryPending(id, artworkKey);
                // Avoid restarting the same image request on every pause/resume or metadata
                // refresh. A failed network request has its own bounded retry schedule.
                if (!hasSongArtworkCache && !requestInFlight) {
                    try {
                        loadAlbumArt(
                                context, artworkUri, id,
                                layoutId == R.layout.widget_small,
                                animateSmallCover, smallCoverDirection,
                                layoutId, artworkKey, String.valueOf(song.id), 0, false);
                    } catch (Exception artworkError) {
                        clearActiveWidgetArtworkRequest(id);
                        Log.w(TAG, "Could not load widget album art for " + song.title, artworkError);
                    }
                }
                if (layoutId != R.layout.widget_small) {
                    try {
                        Object artworkModel = resolveWidgetArtworkModel(
                                context, String.valueOf(song.id), artworkKey, artworkUri);
                        loadBlurredAura(context, artworkModel, id, layoutId, artworkKey);
                    } catch (Exception artworkError) {
                        Log.w(TAG, "Could not load widget aura for " + song.title, artworkError);
                    }
                }
            }
        } catch (Exception error) {
            Log.e(TAG, "Failed to update widget " + id, error);
            try {
                RemoteViews fallback = new RemoteViews(context.getPackageName(), layoutId);
                if (layoutId != R.layout.widget_small) {
                    fallback.setTextViewText(
                            R.id.widget_song_title,
                            song != null && song.title != null ? song.title : "D.S Music"
                    );
                    fallback.setTextViewText(
                            R.id.widget_artist_name,
                            song != null && song.artist != null ? song.artist : "Select a song"
                    );
                    int progress = duration > 0
                            ? (int) (((float) Math.max(0, position) / duration) * 100f)
                            : 0;
                    fallback.setProgressBar(
                            R.id.widget_progress, 100, Math.max(0, Math.min(100, progress)), false);
                }
                if (layoutId == R.layout.widget_medium) {
                    applyLyricsPresentation(context, fallback, currentLyric, R.layout.widget_medium);
                    boolean isLyricsMode = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                            .getBoolean(PREF_LYRICS_MODE + id, false);
                    fallback.setDisplayedChild(R.id.widget_flipper, isLyricsMode ? 1 : 0);
                } else if (layoutId == R.layout.widget_large) {
                    applyLyricsPresentation(context, fallback, currentLyric, R.layout.widget_large);
                }
                applyWidgetActionIcons(fallback, isPlaying, layoutId, repeatIconResId);
                setupPendingIntents(context, fallback, isPlaying, layoutId, providerClass, id);
                if (song != null) {
                    Bitmap retainedArtwork = getRetainedWidgetArtwork(context, id);
                    if (retainedArtwork != null) {
                        fallback.setImageViewBitmap(R.id.widget_album_art, retainedArtwork);
                    }
                }
                manager.updateAppWidget(id, fallback);
            } catch (Exception fallbackError) {
                Log.e(TAG, "Widget fallback rendering failed for " + id, fallbackError);
            }
        }
    }

    public static String findCurrentLyricLine(java.util.List<com.dsann.music.data.lyrics.LyricLine> lyrics,
            int position) {
        if (lyrics == null || lyrics.isEmpty()) return null;

        // Providers do not all return timestamps in ascending order. The full-screen
        // player sorts its display list; use the same order-independent lookup here.
        com.dsann.music.data.lyrics.LyricLine current = null;
        com.dsann.music.data.lyrics.LyricLine first = null;
        long currentStartMs = Long.MIN_VALUE;
        long firstStartMs = Long.MAX_VALUE;
        for (com.dsann.music.data.lyrics.LyricLine line : lyrics) {
            if (line == null || line.isInterlude || line.content == null || line.content.trim().isEmpty()) {
                continue;
            }
            long lineStartMs = line.startTimeMs;
            if (line.words != null && !line.words.isEmpty()) {
                for (com.dsann.music.data.lyrics.LyricWord word : line.words) {
                    lineStartMs = Math.min(lineStartMs, word.getAbsoluteStartTimeMs());
                }
            }
            if (lineStartMs < firstStartMs) {
                first = line;
                firstStartMs = lineStartMs;
            }
            if (lineStartMs <= position && lineStartMs > currentStartMs) {
                current = line;
                currentStartMs = lineStartMs;
            }
        }
        if (current != null) return current.content;
        return first != null ? first.content : null;
    }

    private static RemoteViews hideSkeleton(Context context, int layoutId) {
        return new RemoteViews(context.getPackageName(), layoutId);
    }

    private static File getWidgetArtworkCacheFile(Context context, String artworkKey) {
        try {
            byte[] digest = java.security.MessageDigest.getInstance("SHA-256")
                    .digest(artworkKey.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder name = new StringBuilder(digest.length * 2);
            final char[] hex = "0123456789abcdef".toCharArray();
            for (byte value : digest) {
                int unsigned = value & 0xff;
                name.append(hex[unsigned >>> 4]).append(hex[unsigned & 0x0f]);
            }
            File directory = new File(context.getFilesDir(), "widget_artwork");
            if (!directory.exists()) directory.mkdirs();
            return new File(directory, name + ".jpg");
        } catch (Exception error) {
            Log.w(TAG, "Could not create widget artwork cache key", error);
            return new File(context.getCacheDir(), "widget_artwork_fallback.jpg");
        }
    }

    private static File getServiceArtworkCacheFile(Context context, String songId) {
        try {
            return com.dsann.music.service.AudioPlaybackService.getCachedMediaArtworkFile(
                    context.getApplicationContext(), songId);
        } catch (Exception error) {
            Log.w(TAG, "Could not resolve the shared artwork cache", error);
            return null;
        }
    }

    private static Object resolveWidgetArtworkModel(
            Context context, String songId, String artworkKey, Uri fallbackUri) {
        File widgetCache = getWidgetArtworkCacheFile(context, artworkKey);
        if (widgetCache.isFile() && widgetCache.length() > 0L) return widgetCache;
        File serviceCache = getServiceArtworkCacheFile(context, songId);
        if (serviceCache != null && serviceCache.isFile() && serviceCache.length() > 0L) {
            return serviceCache;
        }
        return fallbackUri;
    }

    private static Bitmap decodeWidgetArtwork(File file) {
        if (file == null || !file.isFile() || file.length() <= 0L) return null;
        Bitmap bitmap = BitmapFactory.decodeFile(file.getAbsolutePath());
        if (bitmap == null) {
            // A truncated cache entry must not permanently block a fresh network fetch.
            file.delete();
        }
        return bitmap;
    }

    private static void rememberWidgetArtwork(int widgetId, String artworkKey, Bitmap bitmap) {
        if (bitmap == null || bitmap.isRecycled()) return;
        synchronized (WIDGET_ARTWORK_LOCK) {
            LAST_WIDGET_ARTWORKS.put(widgetId, bitmap);
            LAST_WIDGET_ARTWORK_KEYS.put(widgetId, artworkKey);
        }
    }

    private static Bitmap getRetainedWidgetArtwork(Context context, int widgetId) {
        synchronized (WIDGET_ARTWORK_LOCK) {
            Bitmap memory = LAST_WIDGET_ARTWORKS.get(widgetId);
            if (memory != null && !memory.isRecycled()) return memory;
        }
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String cachedPath = prefs.getString(PREF_ARTWORK_CACHE_FILE_PREFIX + widgetId, null);
        if (cachedPath == null) return null;
        Bitmap decoded = decodeWidgetArtwork(new File(cachedPath));
        if (decoded != null) {
            String loadedKey = prefs.getString(PREF_ARTWORK_LOADED_KEY_PREFIX + widgetId, "");
            rememberWidgetArtwork(widgetId, loadedKey, decoded);
        }
        return decoded;
    }

    private static Bitmap getWidgetArtworkForSnapshot(
            Context context, int widgetId, String songId, String artworkKey) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        synchronized (WIDGET_ARTWORK_LOCK) {
            Bitmap memory = LAST_WIDGET_ARTWORKS.get(widgetId);
            String memoryKey = LAST_WIDGET_ARTWORK_KEYS.get(widgetId);
            if (memory != null && !memory.isRecycled() && artworkKey.equals(memoryKey)) {
                return memory;
            }
        }

        File exactTrackCache = getWidgetArtworkCacheFile(context, artworkKey);
        File cacheFileUsed = exactTrackCache;
        Bitmap exactTrackBitmap = decodeWidgetArtwork(exactTrackCache);
        if (exactTrackBitmap == null) {
            cacheFileUsed = getServiceArtworkCacheFile(context, songId);
            exactTrackBitmap = decodeWidgetArtwork(cacheFileUsed);
        }
        if (exactTrackBitmap != null && cacheFileUsed != null) {
            rememberWidgetArtwork(widgetId, artworkKey, exactTrackBitmap);
            prefs.edit()
                    .putString(PREF_ARTWORK_CACHE_FILE_PREFIX + widgetId, cacheFileUsed.getAbsolutePath())
                    .putString(PREF_ARTWORK_LOADED_KEY_PREFIX + widgetId, artworkKey)
                    .apply();
            return exactTrackBitmap;
        }

        // A new track's artwork may still be downloading. Keep the previous successfully
        // rendered cover rather than flashing the no-song placeholder.
        return getRetainedWidgetArtwork(context, widgetId);
    }

    private static boolean hasWidgetArtworkCache(Context context, String songId, String artworkKey) {
        File widgetCache = getWidgetArtworkCacheFile(context, artworkKey);
        if (widgetCache.isFile() && widgetCache.length() > 0L) return true;
        File serviceCache = getServiceArtworkCacheFile(context, songId);
        return serviceCache != null && serviceCache.isFile() && serviceCache.length() > 0L;
    }

    private static boolean isWidgetArtworkRequestActive(int widgetId, String artworkKey) {
        synchronized (WIDGET_ARTWORK_LOCK) {
            return artworkKey.equals(ACTIVE_WIDGET_ARTWORK_REQUESTS.get(widgetId));
        }
    }

    private static void markWidgetArtworkRequestActive(int widgetId, String artworkKey) {
        synchronized (WIDGET_ARTWORK_LOCK) {
            ACTIVE_WIDGET_ARTWORK_REQUESTS.put(widgetId, artworkKey);
        }
    }

    private static void clearActiveWidgetArtworkRequest(int widgetId) {
        synchronized (WIDGET_ARTWORK_LOCK) {
            ACTIVE_WIDGET_ARTWORK_REQUESTS.remove(widgetId);
        }
    }

    private static void clearActiveWidgetArtworkRequest(int widgetId, String artworkKey) {
        synchronized (WIDGET_ARTWORK_LOCK) {
            if (artworkKey.equals(ACTIVE_WIDGET_ARTWORK_REQUESTS.get(widgetId))) {
                ACTIVE_WIDGET_ARTWORK_REQUESTS.remove(widgetId);
            }
        }
    }

    private static boolean isWidgetArtworkRetryPending(int widgetId, String artworkKey) {
        synchronized (WIDGET_ARTWORK_LOCK) {
            Runnable pending = PENDING_WIDGET_ARTWORK_RETRIES.get(widgetId);
            String current = contextFreeRetryKey(widgetId);
            return pending != null && artworkKey.equals(current);
        }
    }

    private static String contextFreeRetryKey(int widgetId) {
        synchronized (WIDGET_ARTWORK_LOCK) {
            return PENDING_WIDGET_ARTWORK_RETRY_KEYS.get(widgetId);
        }
    }

    private static final Map<Integer, String> PENDING_WIDGET_ARTWORK_RETRY_KEYS = new HashMap<>();

    private static void cancelWidgetArtworkRetry(int widgetId) {
        Runnable pending;
        synchronized (WIDGET_ARTWORK_LOCK) {
            pending = PENDING_WIDGET_ARTWORK_RETRIES.remove(widgetId);
            PENDING_WIDGET_ARTWORK_RETRY_KEYS.remove(widgetId);
        }
        if (pending != null) WIDGET_ARTWORK_RETRY_HANDLER.removeCallbacks(pending);
    }

    private static void scheduleWidgetArtworkRetry(
            Context context, Uri artUri, int widgetId, String songId,
            boolean smallWidget, boolean animateSmallCover, int slideDirection,
            int layoutId, String artworkKey, int nextAttempt) {
        if (nextAttempt > WIDGET_ARTWORK_RETRY_DELAYS_MS.length ||
                !isWidgetArtworkCurrent(context, widgetId, artworkKey)) return;

        cancelWidgetArtworkRetry(widgetId);
        Runnable retry = () -> {
            synchronized (WIDGET_ARTWORK_LOCK) {
                PENDING_WIDGET_ARTWORK_RETRIES.remove(widgetId);
                PENDING_WIDGET_ARTWORK_RETRY_KEYS.remove(widgetId);
            }
            if (!isWidgetArtworkCurrent(context, widgetId, artworkKey)) return;
            loadAlbumArt(
                    context, artUri, widgetId, smallWidget, animateSmallCover,
                    slideDirection, layoutId, artworkKey, songId, nextAttempt, true);
        };
        synchronized (WIDGET_ARTWORK_LOCK) {
            PENDING_WIDGET_ARTWORK_RETRIES.put(widgetId, retry);
            PENDING_WIDGET_ARTWORK_RETRY_KEYS.put(widgetId, artworkKey);
        }
        WIDGET_ARTWORK_RETRY_HANDLER.postDelayed(
                retry, WIDGET_ARTWORK_RETRY_DELAYS_MS[nextAttempt - 1]);
    }

    private static void persistWidgetArtwork(
            Context context, int widgetId, String songId, String artworkKey, Bitmap bitmap) {
        if (!isWidgetArtworkCurrent(context, widgetId, artworkKey) ||
                bitmap == null || bitmap.isRecycled()) return;
        File file = getWidgetArtworkCacheFile(context, artworkKey);
        File staged = new File(file.getParentFile(), file.getName() + ".tmp");
        try {
            if (file.getParentFile() != null && !file.getParentFile().exists()) {
                file.getParentFile().mkdirs();
            }
            try (java.io.FileOutputStream output = new java.io.FileOutputStream(staged)) {
                if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 90, output)) {
                    staged.delete();
                    return;
                }
                output.flush();
                output.getFD().sync();
            }
            if (!isWidgetArtworkCurrent(context, widgetId, artworkKey)) {
                staged.delete();
                return;
            }
            if (file.exists() && !file.delete()) {
                staged.delete();
                return;
            }
            if (!staged.renameTo(file)) {
                staged.delete();
                return;
            }
            SharedPreferences.Editor editor = context.getSharedPreferences(
                    PREFS_NAME, Context.MODE_PRIVATE).edit();
            editor.putString(PREF_ARTWORK_CACHE_FILE_PREFIX + widgetId, file.getAbsolutePath());
            editor.putString(PREF_ARTWORK_LOADED_KEY_PREFIX + widgetId, artworkKey);
            editor.apply();

            // Share the same offline artwork snapshot with the playback service/notifications.
            if (songId != null && !songId.isEmpty()) {
                com.dsann.music.service.AudioPlaybackService.storeCachedMediaArtwork(
                        context.getApplicationContext(), songId, bitmap);
            }
        } catch (Exception error) {
            staged.delete();
            Log.w(TAG, "Could not persist widget artwork for " + songId, error);
        }
    }

    private static void clearRetainedWidgetArtwork(Context context, int widgetId) {
        synchronized (WIDGET_ARTWORK_LOCK) {
            LAST_WIDGET_ARTWORKS.remove(widgetId);
            LAST_WIDGET_ARTWORK_KEYS.remove(widgetId);
            ACTIVE_WIDGET_ARTWORK_REQUESTS.remove(widgetId);
        }
        cancelWidgetArtworkRetry(widgetId);
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .remove(PREF_ARTWORK_CACHE_FILE_PREFIX + widgetId)
                .remove(PREF_ARTWORK_LOADED_KEY_PREFIX + widgetId)
                .apply();
    }

    private static void applySmallWidgetIdleArtwork(
            Context context, RemoteViews existingViews, int widgetId) {
        RemoteViews idle = existingViews != null
                ? existingViews
                : new RemoteViews(context.getPackageName(), R.layout.widget_small);
        clearRetainedWidgetArtwork(context, widgetId);
        idle.setImageViewResource(R.id.widget_album_art, R.drawable.widget_idle_art);
        idle.setFloat(R.id.widget_album_art, "setTranslationX", 0f);
        idle.setFloat(R.id.widget_album_art, "setAlpha", 1f);
        idle.setFloat(R.id.widget_album_art, "setScaleX", 1f);
        idle.setFloat(R.id.widget_album_art, "setScaleY", 1f);
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit().putString(PREF_ARTWORK_SONG_PREFIX + widgetId, "__idle__").apply();
        try {
            AppWidgetManager.getInstance(context).partiallyUpdateAppWidget(widgetId, idle);
        } catch (Exception error) {
            Log.w(TAG, "Could not apply the 2x2 idle artwork", error);
        }
    }

    private static boolean isWidgetArtworkCurrent(
            Context context, int widgetId, String expectedArtworkKey) {
        if (expectedArtworkKey == null) return false;
        String currentKey = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(PREF_ARTWORK_SONG_PREFIX + widgetId, null);
        return expectedArtworkKey.equals(currentKey);
    }

    private static void loadAlbumArt(
            Context context,
            Uri artUri,
            int widgetId,
            boolean smallWidget,
            boolean animateSmallCover,
            int slideDirection,
            int layoutId,
            String expectedArtworkKey,
            String songId,
            int retryAttempt,
            boolean forceRemote
    ) {
        final Context appContext = context.getApplicationContext();
        final int direction = slideDirection < 0 ? -1 : 1;
        final Object artworkModel = forceRemote
                ? artUri
                : resolveWidgetArtworkModel(appContext, songId, expectedArtworkKey, artUri);
        markWidgetArtworkRequestActive(widgetId, expectedArtworkKey);
        com.bumptech.glide.request.target.CustomTarget<Bitmap> target =
                new com.bumptech.glide.request.target.CustomTarget<Bitmap>() {
            @Override
            public void onResourceReady(
                    @NonNull Bitmap resource,
                    com.bumptech.glide.request.transition.Transition<? super Bitmap> transition
            ) {
                if (!isWidgetArtworkCurrent(appContext, widgetId, expectedArtworkKey)) return;
                clearActiveWidgetArtworkRequest(widgetId, expectedArtworkKey);
                cancelWidgetArtworkRetry(widgetId);
                rememberWidgetArtwork(widgetId, expectedArtworkKey, resource);

                if (smallWidget) {
                    if (animateSmallCover) {
                        animateSmallArtworkArrival(
                                appContext, widgetId, resource, direction, expectedArtworkKey);
                    } else {
                        RemoteViews cover = new RemoteViews(
                                appContext.getPackageName(), R.layout.widget_small);
                        cover.setImageViewBitmap(R.id.widget_album_art, resource);
                        AppWidgetManager.getInstance(appContext)
                                .partiallyUpdateAppWidget(widgetId, cover);
                    }
                } else {
                    RemoteViews cover = new RemoteViews(appContext.getPackageName(), layoutId);
                    cover.setImageViewBitmap(R.id.widget_album_art, resource);
                    AppWidgetManager.getInstance(appContext)
                            .partiallyUpdateAppWidget(widgetId, cover);
                }

                try {
                    PARTIAL_WIDGET_UPDATE_EXECUTOR.execute(() ->
                            persistWidgetArtwork(appContext, widgetId, songId,
                                    expectedArtworkKey, resource));
                } catch (RuntimeException persistError) {
                    Log.w(TAG, "Could not schedule widget artwork persistence", persistError);
                }
            }

            @Override
            public void onLoadFailed(android.graphics.drawable.Drawable errorDrawable) {
                clearActiveWidgetArtworkRequest(widgetId, expectedArtworkKey);
                // Keep the previous good bitmap in place. Retry remote retrieval later, and
                // never replace the widget cover with a no-song drawable because of a timeout.
                scheduleWidgetArtworkRetry(
                        appContext, artUri, widgetId, songId,
                        smallWidget, animateSmallCover, direction,
                        layoutId, expectedArtworkKey, retryAttempt + 1);
            }

            @Override
            public void onLoadCleared(android.graphics.drawable.Drawable placeholder) {
                // A request cancellation must not erase the last successfully rendered cover.
            }
        };

        try {
            Glide.with(appContext)
                    .asBitmap()
                    .load(artworkModel)
                    .diskCacheStrategy(com.bumptech.glide.load.engine.DiskCacheStrategy.ALL)
                    .override(512, 512)
                    .centerCrop()
                    .into(target);
        } catch (RuntimeException loadError) {
            clearActiveWidgetArtworkRequest(widgetId, expectedArtworkKey);
            scheduleWidgetArtworkRetry(
                    appContext, artUri, widgetId, songId,
                    smallWidget, animateSmallCover, direction,
                    layoutId, expectedArtworkKey, retryAttempt + 1);
            Log.w(TAG, "Widget artwork load failed to start", loadError);
        }
    }

    private static void animateSmallArtworkArrival(
            Context context, int widgetId, Bitmap artwork, int direction, String expectedArtworkKey) {
        final Context appContext = context.getApplicationContext();
        if (!isWidgetArtworkCurrent(appContext, widgetId, expectedArtworkKey)) return;
        final float offsetPx = 28f * appContext.getResources().getDisplayMetrics().density;
        try {
            AppWidgetManager manager = AppWidgetManager.getInstance(appContext);
            RemoteViews entering = new RemoteViews(
                    appContext.getPackageName(), R.layout.widget_small);
            entering.setImageViewBitmap(R.id.widget_album_art, artwork);
            entering.setFloat(R.id.widget_album_art, "setTranslationX", direction * offsetPx);
            entering.setFloat(R.id.widget_album_art, "setAlpha", 0.30f);
            entering.setFloat(R.id.widget_album_art, "setScaleX", 0.94f);
            entering.setFloat(R.id.widget_album_art, "setScaleY", 0.94f);
            manager.partiallyUpdateAppWidget(widgetId, entering);

            Handler animationHandler = new Handler(Looper.getMainLooper());
            animationHandler.postDelayed(
                    () -> applySmallArtworkArrivalFrame(appContext, widgetId,
                            direction * offsetPx * 0.45f, 0.72f, 0.98f, expectedArtworkKey),
                    48L);
            animationHandler.postDelayed(
                    () -> applySmallArtworkArrivalFrame(appContext, widgetId,
                            0f, 1f, 1f, expectedArtworkKey),
                    105L);
        } catch (Exception error) {
            Log.w(TAG, "Could not animate the incoming 2x2 artwork", error);
            if (!isWidgetArtworkCurrent(appContext, widgetId, expectedArtworkKey)) return;
            RemoteViews fallback = new RemoteViews(
                    appContext.getPackageName(), R.layout.widget_small);
            fallback.setImageViewBitmap(R.id.widget_album_art, artwork);
            AppWidgetManager.getInstance(appContext).partiallyUpdateAppWidget(widgetId, fallback);
        }
    }

    private static void applySmallArtworkArrivalFrame(
            Context context, int widgetId, float translationX, float alpha, float scale,
            String expectedArtworkKey) {
        if (!isWidgetArtworkCurrent(context, widgetId, expectedArtworkKey)) return;
        try {
            RemoteViews frame = new RemoteViews(
                    context.getPackageName(), R.layout.widget_small);
            frame.setFloat(R.id.widget_album_art, "setTranslationX", translationX);
            frame.setFloat(R.id.widget_album_art, "setAlpha", alpha);
            frame.setFloat(R.id.widget_album_art, "setScaleX", scale);
            frame.setFloat(R.id.widget_album_art, "setScaleY", scale);
            AppWidgetManager.getInstance(context).partiallyUpdateAppWidget(widgetId, frame);
        } catch (Exception error) {
            Log.w(TAG, "Could not advance the 2x2 artwork transition", error);
        }
    }

    private static void loadBlurredAura(
            Context context, Object artworkModel, int widgetId, int layoutId, String expectedArtworkKey) {
        final Context appContext = context.getApplicationContext();
        com.bumptech.glide.request.target.CustomTarget<Bitmap> auraTarget =
                new com.bumptech.glide.request.target.CustomTarget<Bitmap>() {
            @Override
            public void onResourceReady(
                    @NonNull Bitmap resource,
                    com.bumptech.glide.request.transition.Transition<? super Bitmap> transition
            ) {
                if (!isWidgetArtworkCurrent(appContext, widgetId, expectedArtworkKey)) return;
                RemoteViews aura = new RemoteViews(appContext.getPackageName(), layoutId);
                aura.setImageViewBitmap(R.id.widget_aura, resource);
                AppWidgetManager.getInstance(appContext)
                        .partiallyUpdateAppWidget(widgetId, aura);
            }

            @Override
            public void onLoadCleared(android.graphics.drawable.Drawable placeholder) {
                // Ignore cleared requests; the current widget state remains visible.
            }
        };

        Glide.with(appContext)
                .asBitmap()
                .load(artworkModel)
                .diskCacheStrategy(com.bumptech.glide.load.engine.DiskCacheStrategy.ALL)
                .centerCrop()
                .override(150, 150) // Scale down for performance
                .transform(new com.bumptech.glide.load.resource.bitmap.BitmapTransformation() {
                    @Override
                    protected Bitmap transform(@NonNull com.bumptech.glide.load.engine.bitmap_recycle.BitmapPool pool,
                            @NonNull Bitmap toTransform, int outWidth, int outHeight) {
                        // Cache a vivid artwork-derived accent for the widget's UI-Matching
                        // lyrics mode before blurring the image for the background.
                        int lyricAccent = sampleWidgetAccent(toTransform);
                        context.getApplicationContext()
                                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                                .edit().putInt("widget_lyrics_dynamic_accent", lyricAccent).apply();

                        // 1. Zoom in (2x)
                        int zoomWidth = toTransform.getWidth() / 2;
                        int zoomHeight = toTransform.getHeight() / 2;
                        Bitmap zoomed = Bitmap.createBitmap(toTransform,
                                zoomWidth / 2, zoomHeight / 2, zoomWidth, zoomHeight);

                        // 2. Multi-pass deep blur
                        return blur(zoomed);
                    }

                    @Override
                    public void updateDiskCacheKey(@NonNull java.security.MessageDigest messageDigest) {
                    }
                })
                .into(auraTarget);
    }

    private static int sampleWidgetAccent(Bitmap bitmap) {
        if (bitmap == null || bitmap.getWidth() <= 0 || bitmap.getHeight() <= 0) {
            return Color.rgb(124, 156, 255);
        }
        long red = 0L, green = 0L, blue = 0L, count = 0L;
        int stepX = Math.max(1, bitmap.getWidth() / 24);
        int stepY = Math.max(1, bitmap.getHeight() / 24);
        for (int y = 0; y < bitmap.getHeight(); y += stepY) {
            for (int x = 0; x < bitmap.getWidth(); x += stepX) {
                int pixel = bitmap.getPixel(x, y);
                red += Color.red(pixel);
                green += Color.green(pixel);
                blue += Color.blue(pixel);
                count++;
            }
        }
        if (count == 0L) return Color.rgb(124, 156, 255);
        int average = Color.rgb((int) (red / count), (int) (green / count), (int) (blue / count));
        float[] hsv = new float[3];
        Color.colorToHSV(average, hsv);
        hsv[1] = Math.max(0.46f, Math.min(0.86f, hsv[1]));
        hsv[2] = Math.max(0.78f, Math.min(0.94f, hsv[2]));
        return Color.HSVToColor(hsv);
    }

    private static Bitmap blur(Bitmap image) {
        if (null == image)
            return null;

        Bitmap outputBitmap = Bitmap.createBitmap(image);
        // Simple fast box blur for widgets (RenderScript is deprecated and sometimes
        // unstable in background)
        return fastblur(outputBitmap);
    }

    private static Bitmap fastblur(Bitmap sentBitmap) {
        int width = Math.round(sentBitmap.getWidth() * (float) 1.0);
        int height = Math.round(sentBitmap.getHeight() * (float) 1.0);
        sentBitmap = Bitmap.createScaledBitmap(sentBitmap, width, height, false);

        Bitmap bitmap = sentBitmap.copy(Objects.requireNonNull(sentBitmap.getConfig()), true);

        int w = bitmap.getWidth();
        int h = bitmap.getHeight();

        int[] pix = new int[w * h];
        bitmap.getPixels(pix, 0, w, 0, 0, w, h);

        int wm = w - 1;
        int hm = h - 1;
        int wh = w * h;
        int div = 25 + 25 + 1;

        int[] r = new int[wh];
        int[] g = new int[wh];
        int[] b = new int[wh];
        int rsum, gsum, bsum, x, y, i, p, yp, yi, yw;
        int[] vmin = new int[Math.max(w, h)];

        int divsum = (div + 1) >> 1;
        divsum *= divsum;
        int[] temp = new int[256 * divsum];
        for (i = 0; i < 256 * divsum; i++) {
            temp[i] = (i / divsum);
        }

        yw = yi = 0;

        int[][] stack = new int[div][3];
        int stackpointer;
        int stackstart;
        int[] sir;
        int rbs;
        int r1 = 25 + 1;
        int routsum, goutsum, boutsum;
        int rinsum, ginsum, binsum;

        for (y = 0; y < h; y++) {
            rinsum = ginsum = binsum = routsum = goutsum = boutsum = rsum = gsum = bsum = 0;
            for (i = -25; i <= 25; i++) {
                p = pix[yi + Math.min(wm, Math.max(i, 0))];
                sir = stack[i + 25];
                sir[0] = (p & 0xff0000) >> 16;
                sir[1] = (p & 0x00ff00) >> 8;
                sir[2] = (p & 0x0000ff);
                rbs = r1 - Math.abs(i);
                rsum += sir[0] * rbs;
                gsum += sir[1] * rbs;
                bsum += sir[2] * rbs;
                if (i > 0) {
                    rinsum += sir[0];
                    ginsum += sir[1];
                    binsum += sir[2];
                } else {
                    routsum += sir[0];
                    goutsum += sir[1];
                    boutsum += sir[2];
                }
            }
            stackpointer = 25;

            for (x = 0; x < w; x++) {

                r[yi] = temp[rsum];
                g[yi] = temp[gsum];
                b[yi] = temp[bsum];

                rsum -= routsum;
                gsum -= goutsum;
                bsum -= boutsum;

                stackstart = stackpointer - 25 + div;
                sir = stack[stackstart % div];

                routsum -= sir[0];
                goutsum -= sir[1];
                boutsum -= sir[2];

                if (y == 0) {
                    vmin[x] = Math.min(x + 25 + 1, wm);
                }
                p = pix[yw + vmin[x]];

                sir[0] = (p & 0xff0000) >> 16;
                sir[1] = (p & 0x00ff00) >> 8;
                sir[2] = (p & 0x0000ff);

                rinsum += sir[0];
                ginsum += sir[1];
                binsum += sir[2];

                rsum += rinsum;
                gsum += ginsum;
                bsum += binsum;

                stackpointer = (stackpointer + 1) % div;
                sir = stack[(stackpointer) % div];

                rinsum -= sir[0];
                ginsum -= sir[1];
                binsum -= sir[2];

                routsum += sir[0];
                goutsum += sir[1];
                boutsum += sir[2];

                yi++;
            }
            yw += w;
        }
        for (x = 0; x < w; x++) {
            rinsum = ginsum = binsum = routsum = goutsum = boutsum = rsum = gsum = bsum = 0;
            yp = -25 * w;
            for (i = -25; i <= 25; i++) {
                yi = Math.max(0, yp) + x;

                sir = stack[i + 25];

                sir[0] = r[yi];
                sir[1] = g[yi];
                sir[2] = b[yi];

                rbs = r1 - Math.abs(i);

                rsum += r[yi] * rbs;
                gsum += g[yi] * rbs;
                bsum += b[yi] * rbs;

                if (i > 0) {
                    rinsum += sir[0];
                    ginsum += sir[1];
                    binsum += sir[2];
                } else {
                    routsum += sir[0];
                    goutsum += sir[1];
                    boutsum += sir[2];
                }

                if (i < hm) {
                    yp += w;
                }
            }
            yi = x;
            stackpointer = 25;
            for (y = 0; y < h; y++) {
                // Preserve alpha or set fully opaque? Background aura should probably be opaque
                // but alpha is handled by layout
                pix[yi] = (0xff000000 & pix[yi]) | (temp[rsum] << 16) | (temp[gsum] << 8) | temp[bsum];

                rsum -= routsum;
                gsum -= goutsum;
                bsum -= boutsum;

                stackstart = stackpointer - 25 + div;
                sir = stack[stackstart % div];

                routsum -= sir[0];
                goutsum -= sir[1];
                boutsum -= sir[2];

                if (x == 0) {
                    vmin[y] = Math.min(y + 25 + 1, hm) * w;
                }
                p = x + vmin[y];

                sir[0] = r[p];
                sir[1] = g[p];
                sir[2] = b[p];

                rinsum += sir[0];
                ginsum += sir[1];
                binsum += sir[2];

                rsum += rinsum;
                gsum += ginsum;
                bsum += binsum;

                stackpointer = (stackpointer + 1) % div;
                sir = stack[stackpointer];

                rinsum -= sir[0];
                ginsum -= sir[1];
                binsum -= sir[2];

                routsum += sir[0];
                goutsum += sir[1];
                boutsum += sir[2];

                yi += w;
            }
        }

        bitmap.setPixels(pix, 0, w, 0, 0, w, h);

        return (bitmap);
    }

    private static void setupPendingIntents(Context context, RemoteViews views, boolean isPlaying, int layoutId, Class<?> providerClass, int widgetId) {
        if (layoutId == R.layout.widget_small) {
            Intent leftTap = new Intent(context, providerClass);
            leftTap.setAction(ACTION_SMALL_LEFT_TAP);
            leftTap.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId);
            PendingIntent leftTapIntent = PendingIntent.getBroadcast(
                    context, 30_000 + widgetId * 2, leftTap,
                    PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

            Intent rightTap = new Intent(context, providerClass);
            rightTap.setAction(ACTION_SMALL_RIGHT_TAP);
            rightTap.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId);
            PendingIntent rightTapIntent = PendingIntent.getBroadcast(
                    context, 30_001 + widgetId * 2, rightTap,
                    PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

            views.setOnClickPendingIntent(R.id.widget_art_left_zone, leftTapIntent);
            views.setOnClickPendingIntent(R.id.widget_art_right_zone, rightTapIntent);
            return;
        }

        // Larger widgets retain the existing app-launch behavior on the artwork.
        Intent launchIntent = new Intent(context, com.dsann.music.MainActivity.class);
        launchIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent piLaunch = PendingIntent.getActivity(context, 0, launchIntent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        views.setOnClickPendingIntent(R.id.widget_album_art, piLaunch);

        // Lyrics Toggle (Medium Widget only)
        if (layoutId == R.layout.widget_medium) {
            Intent toggleIntent = new Intent(context, providerClass);
            toggleIntent.setAction(ACTION_TOGGLE_LYRICS);
            toggleIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId);
            PendingIntent piToggle = PendingIntent.getBroadcast(
                    context, 20_000 + widgetId, toggleIntent,
                    PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
            views.setOnClickPendingIntent(R.id.widget_toggle_lyrics, piToggle);
            views.setOnClickPendingIntent(R.id.widget_lyrics_text, piToggle);
        }
        
        // Playback Controls
        PendingIntent piPlayPause = getServicePendingIntent(context, isPlaying ? ACTION_PAUSE : ACTION_PLAY, 10);
        views.setOnClickPendingIntent(R.id.widget_play_pause, piPlayPause);
        
        if (layoutId == R.layout.widget_small ||
                layoutId == R.layout.widget_medium ||
                layoutId == R.layout.widget_large) {
            PendingIntent piPrev = getServicePendingIntent(context, ACTION_PREVIOUS, 11);
            views.setOnClickPendingIntent(R.id.widget_prev, piPrev);

            PendingIntent piNext = getServicePendingIntent(context, ACTION_NEXT, 12);
            views.setOnClickPendingIntent(R.id.widget_next, piNext);
        }

        if (layoutId == R.layout.widget_large) {
            // Favorite Button Intent
            PendingIntent piFav = getServicePendingIntent(context, "com.dsann.music.ACTION_TOGGLE_FAV", 13);
            views.setOnClickPendingIntent(R.id.widget_favorite, piFav);

            // Repeat/Loop Button Intent
            PendingIntent piLoop = getServicePendingIntent(context, "com.dsann.music.ACTION_TOGGLE_LOOP", 14);
            views.setOnClickPendingIntent(R.id.widget_repeat, piLoop);
        }
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        migrateWidgetPreferences(context);
        super.onReceive(context, intent);
        if (ACTION_SMALL_LEFT_TAP.equals(intent.getAction())) {
            handleSmallArtworkTap(context, intent, -1);
            return;
        }
        if (ACTION_SMALL_RIGHT_TAP.equals(intent.getAction())) {
            handleSmallArtworkTap(context, intent, 1);
            return;
        }
        if (ACTION_TOGGLE_LYRICS.equals(intent.getAction())) {
            AppWidgetManager manager = AppWidgetManager.getInstance(context);
            int requestedWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1);
            int[] ids = requestedWidgetId >= 0
                    ? new int[] { requestedWidgetId }
                    : manager.getAppWidgetIds(new ComponentName(context, WidgetMediumProvider.class));
            android.content.SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

            for (int id : ids) {
                // Toggle the saved state
                boolean currentMode = prefs.getBoolean(PREF_LYRICS_MODE + id, false);
                prefs.edit().putBoolean(PREF_LYRICS_MODE + id, !currentMode).apply();
                
                // Toggle the saved page without replacing the current RemoteViews layout.
                RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_medium);
                views.setDisplayedChild(R.id.widget_flipper, !currentMode ? 1 : 0);
                manager.partiallyUpdateAppWidget(id, views);
            }

            // The old implementation only switched the ViewFlipper page. It never fetched
            // lyrics, so a newly opened lyrics page could remain "..." forever.
            try {
                Intent lyricsIntent = new Intent(context, com.dsann.music.service.AudioPlaybackService.class);
                lyricsIntent.setAction("com.dsann.music.ACTION_WIDGET_FETCH_LYRICS");
                context.startService(lyricsIntent);
            } catch (Exception e) {
                Log.w(TAG, "Could not request lyrics for widget", e);
            }
        }
    }

    /**
     * Small widget tap feedback. RemoteViews cannot run a ViewPropertyAnimator, so send
     * a short three-frame scale pulse through supported View.setScaleX/Y methods.
     */
    public static void animateSmallWidgetArtwork(Context context) {
        final Context appContext = context.getApplicationContext();
        final Handler handler = new Handler(Looper.getMainLooper());
        handler.post(() -> applySmallArtworkScale(appContext, 0.94f));
        handler.postDelayed(() -> applySmallArtworkScale(appContext, 1.035f), 65L);
        handler.postDelayed(() -> applySmallArtworkScale(appContext, 1.0f), 135L);
    }

    private static void applySmallArtworkScale(Context context, float scale) {
        try {
            AppWidgetManager manager = AppWidgetManager.getInstance(context);
            int[] ids = manager.getAppWidgetIds(
                    new ComponentName(context, WidgetSmallProvider.class));
            for (int id : ids) {
                RemoteViews feedback = new RemoteViews(context.getPackageName(), R.layout.widget_small);
                feedback.setFloat(R.id.widget_album_art, "setScaleX", scale);
                feedback.setFloat(R.id.widget_album_art, "setScaleY", scale);
                manager.partiallyUpdateAppWidget(id, feedback);
            }
        } catch (Exception error) {
            Log.w(TAG, "Could not animate 2x2 artwork tap", error);
        }
    }

    private static void handleSmallArtworkTap(Context context, Intent intent, int direction) {
        int widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1);
        if (widgetId < 0) return;

        PendingSmallArtworkTap pending = PENDING_SMALL_ART_TAPS.remove(widgetId);
        long now = SystemClock.uptimeMillis();
        if (pending != null &&
                pending.direction == direction &&
                now - pending.firstTapAtMs <= SMALL_WIDGET_DOUBLE_TAP_MS) {
            SMALL_WIDGET_TAP_HANDLER.removeCallbacks(pending.singleTapAction);
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .edit().putInt(PREF_SMALL_ART_DIRECTION, direction).apply();
            dispatchSmallWidgetPlaybackAction(
                    context, direction > 0 ? ACTION_NEXT : ACTION_PREVIOUS,
                    31_000 + widgetId);
            return;
        }

        if (pending != null) {
            // A tap on the opposite half is a separate single tap, not a double-tap pair.
            SMALL_WIDGET_TAP_HANDLER.removeCallbacks(pending.singleTapAction);
            dispatchSmallWidgetPlaybackAction(
                    context, ACTION_WIDGET_TOGGLE_PLAY_PAUSE, 32_000 + widgetId);
        }

        Runnable singleTap = () -> {
            PendingSmallArtworkTap current = PENDING_SMALL_ART_TAPS.get(widgetId);
            if (current != null && current.firstTapAtMs == now &&
                    current.direction == direction) {
                PENDING_SMALL_ART_TAPS.remove(widgetId);
                dispatchSmallWidgetPlaybackAction(
                        context, ACTION_WIDGET_TOGGLE_PLAY_PAUSE, 32_000 + widgetId);
            }
        };
        PENDING_SMALL_ART_TAPS.put(
                widgetId, new PendingSmallArtworkTap(direction, now, singleTap));
        SMALL_WIDGET_TAP_HANDLER.postDelayed(singleTap, SMALL_WIDGET_DOUBLE_TAP_MS);
    }

    private static void dispatchSmallWidgetPlaybackAction(
            Context context, String action, int requestCode) {
        try {
            getServicePendingIntent(context, action, requestCode).send();
        } catch (PendingIntent.CanceledException error) {
            Log.w(TAG, "Could not dispatch 2x2 widget action " + action, error);
        }
    }

    private static PendingIntent getServicePendingIntent(Context context, String action, int requestCode) {
        Intent intent = new Intent(context, com.dsann.music.service.AudioPlaybackService.class);
        intent.setAction(action);
        return PendingIntent.getService(context, requestCode, intent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }
}
