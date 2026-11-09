package com.dsann.music.util;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import android.os.Looper;

public class AudioFileManager {

    private static final String TAG = "AudioFileManager";

    public static String getRealPathFromURI(Context context, Uri uri) {
        if (uri == null) return null;

        try {
            String fileName = getFileName(context, uri);
            File tempFile = new File(context.getCacheDir(),
                    "temp_" + System.currentTimeMillis() + "_" + fileName);

            try (InputStream inputStream = context.getContentResolver().openInputStream(uri);
                 FileOutputStream outputStream = new FileOutputStream(tempFile)) {

                if (inputStream == null) {
                    Log.e(TAG, "Cannot open input stream from URI");
                    return null;
                }

                byte[] buffer = new byte[8192];
                int read;
                while ((read = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, read);
                }
            }

            return tempFile.getAbsolutePath();

        } catch (IOException e) {
            Log.e(TAG, "Error extracting file from URI: " + e.getMessage(), e);
            return null;
        }
    }

    public static File createOutputFile(Context context, String fileName) {
        String lowerName = fileName.toLowerCase(java.util.Locale.ROOT);
        if (!(lowerName.endsWith(".m4a") || lowerName.endsWith(".mp3") ||
                lowerName.endsWith(".webm") || lowerName.endsWith(".mka") || lowerName.endsWith(".ogg") ||
                lowerName.endsWith(".opus") || lowerName.endsWith(".aac") ||
                lowerName.endsWith(".flac") || lowerName.endsWith(".wav"))) {
            fileName = fileName + ".m4a";
        }

        String cleanName = fileName.replaceAll("[^a-zA-Z0-9._\\s()\\[\\]-]", "_");

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android 10+ - Create temp file in cache for FFmpeg processing
            File tempDir = new File(context.getCacheDir(), "Quantune_output");
            if (!tempDir.exists()) tempDir.mkdirs();

            File tempFile = new File(tempDir, cleanName);
            Log.d(TAG, "Temp output file for processing: " + tempFile.getAbsolutePath());
            return tempFile;
        } else {
            // Android 9 and below - Direct file access to Downloads/Quantune
            File downloadsDir = new File(Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_DOWNLOADS), "Quantune");
            if (!downloadsDir.exists()) downloadsDir.mkdirs();

            File outputFile = new File(downloadsDir, cleanName);
            Log.d(TAG, "Output file (legacy): " + outputFile.getAbsolutePath());
            return outputFile;
        }
    }

    public static boolean scanFile(Context context, File file) {
        return scanFile(context, file, null, null, null);
    }

    public static boolean scanFile(Context context, File file, String title, String artist, String album) {
        if (file == null || !file.isFile()) {
            Log.e(TAG, "Download file does not exist");
            return false;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            boolean published = copyToMediaStore(context, file, title, artist, album);
            if (published && file.exists() && !file.delete()) {
                // MediaStore now owns the published copy; do not leave the staged audio
                // file in the app cache after a successful export.
                Log.w(TAG, "Could not remove staged audio after MediaStore publish: " + file.getAbsolutePath());
            }
            return published;
        }
        try {
            // The downloader immediately refreshes the local library after publishing a file.
            // Wait for the scanner callback on background callers so that refresh cannot race
            // ahead of the MediaStore row creation on Android 6-9.
            final CountDownLatch scanFinished = new CountDownLatch(1);
            final AtomicBoolean scanSucceeded = new AtomicBoolean(false);
            MediaScannerConnection.scanFile(
                    context,
                    new String[]{file.getAbsolutePath()},
                    new String[]{mimeTypeForFile(file.getName())},
                    (scannedPath, scannedUri) -> {
                        try {
                            if (scannedUri == null) {
                                Log.w(TAG, "Media scanner did not return a URI for " + scannedPath);
                                return;
                            }
                            ContentValues metadata = new ContentValues();
                            if (title != null && !title.trim().isEmpty()) {
                                metadata.put(MediaStore.Audio.Media.TITLE, title.trim());
                            }
                            if (artist != null && !artist.trim().isEmpty()) {
                                metadata.put(MediaStore.Audio.Media.ARTIST, artist.trim());
                                metadata.put(MediaStore.Audio.Media.ALBUM_ARTIST, artist.trim());
                            }
                            if (album != null && !album.trim().isEmpty()) {
                                metadata.put(MediaStore.Audio.Media.ALBUM, album.trim());
                            }
                            try {
                                int updated = context.getContentResolver().update(scannedUri, metadata, null, null);
                                Log.d(TAG, "Updated legacy MediaStore metadata rows=" + updated);
                            } catch (Exception metadataError) {
                                Log.w(TAG, "Could not update legacy MediaStore metadata", metadataError);
                            }
                            scanSucceeded.set(true);
                        } finally {
                            scanFinished.countDown();
                        }
                    });
            Log.d(TAG, "Media scan requested: " + file.getAbsolutePath());
            if (Looper.myLooper() != Looper.getMainLooper()) {
                try {
                    if (!scanFinished.await(12L, TimeUnit.SECONDS)) {
                        Log.e(TAG, "Timed out waiting for MediaStore to index " + file.getName());
                        return false;
                    }
                    if (!scanSucceeded.get()) {
                        Log.e(TAG, "MediaStore did not index " + file.getName());
                        return false;
                    }
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    Log.w(TAG, "Interrupted while waiting for MediaStore to index " + file.getName(), interrupted);
                    return false;
                }
            }
            return true;
        } catch (Exception e) {
            Log.e(TAG, "scanFile failed: " + e.getMessage(), e);
            return false;
        }
    }


    private static String mimeTypeForFile(String fileName) {
        String lowerFileName = fileName.toLowerCase(java.util.Locale.ROOT);
        if (lowerFileName.endsWith(".mp3")) return "audio/mpeg";
        if (lowerFileName.endsWith(".m4a") || lowerFileName.endsWith(".aac")) return "audio/mp4";
        if (lowerFileName.endsWith(".opus") || lowerFileName.endsWith(".ogg")) return "audio/ogg";
        if (lowerFileName.endsWith(".mka")) return "audio/x-matroska";
        if (lowerFileName.endsWith(".webm")) return "audio/webm";
        if (lowerFileName.endsWith(".flac")) return "audio/flac";
        if (lowerFileName.endsWith(".wav")) return "audio/wav";
        return "application/octet-stream";
    }

    private static boolean copyToMediaStore(Context context, File sourceFile, String suppliedTitle, String suppliedArtist, String suppliedAlbum) {
        if (!sourceFile.isFile() || sourceFile.length() <= 0L) {
            Log.e(TAG, "Source audio file is missing or empty: " + sourceFile.getAbsolutePath());
            return false;
        }

        String displayName = sourceFile.getName();
        int extensionIndex = displayName.lastIndexOf('.');
        String nameWithoutExtension = extensionIndex > 0
                ? displayName.substring(0, extensionIndex)
                : displayName;

        String title = suppliedTitle != null && !suppliedTitle.trim().isEmpty()
                ? suppliedTitle.trim() : nameWithoutExtension;
        String artist = suppliedArtist != null && !suppliedArtist.trim().isEmpty()
                ? suppliedArtist.trim() : "Unknown Artist";
        if ((suppliedTitle == null || suppliedTitle.trim().isEmpty() ||
                suppliedArtist == null || suppliedArtist.trim().isEmpty()) && nameWithoutExtension.contains(" - ")) {
            String[] parts = nameWithoutExtension.split(" - ", 2);
            if (suppliedTitle == null || suppliedTitle.trim().isEmpty()) title = parts[0].trim();
            if (suppliedArtist == null || suppliedArtist.trim().isEmpty()) artist = parts[1].trim();
        }

        String lowerFileName = sourceFile.getName().toLowerCase(java.util.Locale.ROOT);
        boolean isWebmAudio = lowerFileName.endsWith(".webm");
        boolean isMatroskaAudio = lowerFileName.endsWith(".mka");
        boolean isOggOpusAudio = lowerFileName.endsWith(".opus");
        // New Opus downloads are real Ogg Opus files and belong in the audio library.
        // Only older, unconverted WebM files need the generic Downloads collection.
        boolean isGenericContainerAudio = isWebmAudio;

        ContentValues values = new ContentValues();
        values.put(MediaStore.Audio.Media.DISPLAY_NAME, sourceFile.getName());

        // Audio-specific indexed columns are not part of MediaStore.Downloads on
        // older Android releases. Ogg Opus/MKA files carry their own embedded tags;
        // the audio-library columns are still populated for reliable system indexing.
        if (!isGenericContainerAudio) {
            values.put(MediaStore.Audio.Media.TITLE, title);
            values.put(MediaStore.Audio.Media.ARTIST, artist);
            values.put(MediaStore.Audio.Media.ALBUM_ARTIST, artist);
            if (suppliedAlbum != null && !suppliedAlbum.trim().isEmpty()) {
                values.put(MediaStore.Audio.Media.ALBUM, suppliedAlbum.trim());
            }
        }

        String mimeType;
        if (lowerFileName.endsWith(".mp3")) mimeType = "audio/mpeg";
        else if (lowerFileName.endsWith(".m4a") || lowerFileName.endsWith(".aac")) mimeType = "audio/mp4";
        else if (isOggOpusAudio || lowerFileName.endsWith(".ogg")) mimeType = "audio/ogg";
        else if (isWebmAudio) mimeType = "audio/webm";
        else if (isMatroskaAudio) mimeType = "audio/x-matroska";
        else if (lowerFileName.endsWith(".flac")) mimeType = "audio/flac";
        else if (lowerFileName.endsWith(".wav")) mimeType = "audio/wav";
        else mimeType = "application/octet-stream";
        values.put(MediaStore.Audio.Media.MIME_TYPE, mimeType);

        // Android's MediaStore.Audio collection does not consistently recognize audio/webm.
        // Legacy WebM files use Downloads; .opus is published as audio/ogg in Music/Quantune.
        Uri collectionUri;
        String relativePath;
        if (isGenericContainerAudio) {
            collectionUri = MediaStore.Downloads.EXTERNAL_CONTENT_URI;
            relativePath = Environment.DIRECTORY_DOWNLOADS + "/Quantune/";
        } else {
            collectionUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
            relativePath = Environment.DIRECTORY_MUSIC + "/Quantune/";
        }
        values.put(MediaStore.Audio.Media.RELATIVE_PATH, relativePath);
        values.put(MediaStore.Audio.Media.IS_PENDING, 1);

        ContentResolver resolver = context.getContentResolver();
        Uri itemUri;
        try {
            itemUri = resolver.insert(collectionUri, values);
        } catch (Exception insertError) {
            Log.e(TAG, "Failed to create MediaStore entry for " + sourceFile.getName(), insertError);
            return false;
        }
        if (itemUri == null) {
            Log.e(TAG, "Failed to create MediaStore entry for " + sourceFile.getName());
            return false;
        }

        try {
            long copiedBytes = 0L;
            try (InputStream in = new java.io.FileInputStream(sourceFile);
                 OutputStream out = resolver.openOutputStream(itemUri)) {
                if (out == null) throw new IOException("MediaStore returned no output stream");
                byte[] buffer = new byte[64 * 1024];
                int bytesRead;
                while ((bytesRead = in.read(buffer)) != -1) {
                    out.write(buffer, 0, bytesRead);
                    copiedBytes += bytesRead;
                }
                out.flush();
            }

            long sourceBytes = sourceFile.length();
            if (copiedBytes != sourceBytes) {
                throw new IOException("MediaStore copy was incomplete: " + copiedBytes + " of " + sourceBytes + " bytes");
            }

            ContentValues ready = new ContentValues();
            ready.put(MediaStore.Audio.Media.IS_PENDING, 0);
            if (resolver.update(itemUri, ready, null, null) <= 0) {
                throw new IOException("Could not publish the completed audio file");
            }
            Log.d(TAG, "File copied to MediaStore successfully: " + relativePath + sourceFile.getName());
            return true;
        } catch (Exception e) {
            try {
                resolver.delete(itemUri, null, null);
            } catch (Exception cleanupError) {
                Log.w(TAG, "Could not remove incomplete MediaStore entry", cleanupError);
            }
            Log.e(TAG, "Error copying to MediaStore: " + e.getMessage(), e);
            return false;
        }
    }

    private static String getFileName(Context context, Uri uri) {
        String name = null;

        if ("content".equals(uri.getScheme())) {
            try (Cursor cursor = context.getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int nameIndex = cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME);
                    name = cursor.getString(nameIndex);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error getting filename: " + e.getMessage());
            }
        }

        if (name == null) {
            name = uri.getLastPathSegment();
        }

        return name != null ? name : "audio.m4a";
    }
}
