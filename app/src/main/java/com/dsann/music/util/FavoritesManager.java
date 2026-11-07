package com.dsann.music.util;

import android.content.Context;
import android.content.SharedPreferences;

import java.io.File;

import java.util.HashSet;
import java.util.Set;

/**
 * Helper class to manage persistent favorite songs.
 */
public class FavoritesManager {

    private static final String PREF_NAME = "dsmusic_favorites";
    private static final String KEY_FAVORITES = "favorite_song_ids";

    private final SharedPreferences prefs;

    public FavoritesManager(Context context) {
        prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        migrateExistingFavorites(context, prefs);
    }

    private static void migrateExistingFavorites(Context context, SharedPreferences target) {
        if (target.getBoolean("_dsmusic_storage_migrated", false)) return;
        Set<String> migrated = new HashSet<>(target.getStringSet(KEY_FAVORITES, new HashSet<>()));
        File directory = new File(context.getApplicationInfo().dataDir, "shared_prefs");
        File[] files = directory.listFiles();
        if (files != null) {
            for (File file : files) {
                String filename = file.getName();
                if (!filename.endsWith(".xml") || filename.equals(PREF_NAME + ".xml")) continue;
                String sourceName = filename.substring(0, filename.length() - 4);
                SharedPreferences source = context.getSharedPreferences(sourceName, Context.MODE_PRIVATE);
                try {
                    Set<String> values = source.getStringSet(KEY_FAVORITES, null);
                    if (values == null || values.isEmpty()) continue;
                    migrated.addAll(values);
                    source.edit().remove(KEY_FAVORITES).commit();
                    if (source.getAll().isEmpty()) file.delete();
                } catch (ClassCastException ignored) {
                    // This preference file does not store favourites.
                }
            }
        }
        target.edit().putStringSet(KEY_FAVORITES, migrated)
            .putBoolean("_dsmusic_storage_migrated", true).commit();
    }

    public boolean isFavorite(long songId) {
        Set<String> favorites = prefs.getStringSet(KEY_FAVORITES, new HashSet<>());
        return favorites.contains(String.valueOf(songId));
    }

    public void setFavorite(long songId, boolean isFavorite) {
        Set<String> favorites = new HashSet<>(prefs.getStringSet(KEY_FAVORITES, new HashSet<>()));
        String idStr = String.valueOf(songId);

        if (isFavorite) {
            favorites.add(idStr);
        } else {
            favorites.remove(idStr);
        }

        prefs.edit().putStringSet(KEY_FAVORITES, favorites).apply();
    }

    public Set<String> getFavoriteIds() {
        return new HashSet<>(prefs.getStringSet(KEY_FAVORITES, new HashSet<>()));
    }
}
