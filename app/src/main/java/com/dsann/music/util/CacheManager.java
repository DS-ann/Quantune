package com.dsann.music.util;

import android.annotation.SuppressLint;
import android.content.Context;
import android.util.Log;


import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Utility class to manage app cache and clean up temp files.
 * Clears temporary local-audio copies left by file handling.
 */
public class CacheManager {

    private static final String TAG = "CacheManager";

    // File patterns to clean
    private static final String[] TEMP_FILE_PREFIXES = {
            "temp_", // Audio temp copies from AudioFileManager
    };

    /**
     * Clear old cache files on app startup.
     * Runs on background thread to avoid blocking main thread.
     */
    public static void clearOldCache(Context context) {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> {
            try {
                int deletedCount = 0;
                long freedBytes = 0;

                File cacheDir = context.getCacheDir();
                if (cacheDir != null && cacheDir.exists()) {
                    // Clean temp files with known prefixes
                    File[] files = cacheDir.listFiles();
                    if (files != null) {
                        for (File file : files) {
                            if (shouldDeleteFile(file)) {
                                long size = file.length();
                                if (file.delete()) {
                                    deletedCount++;
                                    freedBytes += size;
                                }
                            }
                        }
                    }

                }
if (deletedCount > 0) {
                    Log.d(TAG, "Cache cleanup: deleted " + deletedCount + " files, freed " +
                            formatSize(freedBytes));
                } else {
                    Log.d(TAG, "Cache cleanup: no old files to delete");
                }

            } catch (Exception e) {
                Log.e(TAG, "Cache cleanup failed: " + e.getMessage(), e);
            }
        });
        executor.shutdown();
    }

    /**
     * Check if a file should be deleted based on its name prefix.
     */
    private static boolean shouldDeleteFile(File file) {
        if (file == null || !file.isFile())
            return false;

        String name = file.getName();
        for (String prefix : TEMP_FILE_PREFIXES) {
            if (name.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Recursively delete a directory and all its contents.
     * 
     * @return Total bytes deleted
     */
    /**
     * Format bytes to human-readable string.
     */
    @SuppressLint("DefaultLocale")
    private static String formatSize(long bytes) {
        if (bytes < 1024)
            return bytes + " B";
        if (bytes < 1024 * 1024)
            return String.format("%.1f KB", bytes / 1024.0);
        return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
    }
}
