package com.dsann.music.update;

import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.util.Log;

import com.dsann.music.MainActivity;

public class UpdateReceiver extends BroadcastReceiver {

    private static final String TAG = "UpdateReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !DownloadManager.ACTION_DOWNLOAD_COMPLETE.equals(intent.getAction())) {
            return;
        }

        long downloadId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L);
        SharedPreferences prefs = context.getSharedPreferences("update_prefs", Context.MODE_PRIVATE);
        if (downloadId < 0L || downloadId != prefs.getLong("download_id", -1L)) {
            return;
        }

        DownloadManager downloadManager =
                (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
        if (downloadManager == null) {
            Log.e(TAG, "Download Manager is unavailable while handling the completed update.");
            return;
        }

        Cursor cursor = null;
        try {
            DownloadManager.Query query = new DownloadManager.Query().setFilterById(downloadId);
            cursor = downloadManager.query(query);
            if (cursor == null || !cursor.moveToFirst()) {
                Log.e(TAG, "Could not read the completed Quantune update download.");
                return;
            }

            int statusColumn = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS);
            if (statusColumn < 0) {
                Log.e(TAG, "Download Manager did not return a status column.");
                return;
            }

            int status = cursor.getInt(statusColumn);
            if (status != DownloadManager.STATUS_SUCCESSFUL) {
                int reasonColumn = cursor.getColumnIndex(DownloadManager.COLUMN_REASON);
                int reason = reasonColumn >= 0 ? cursor.getInt(reasonColumn) : -1;
                Log.e(TAG, "Quantune update download did not complete successfully. Status="
                        + status + ", reason=" + reason);
                prefs.edit().remove("download_id").apply();
                return;
            }

            int uriColumn = cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI);
            String downloadedUri = uriColumn >= 0 ? cursor.getString(uriColumn) : null;
            Uri apkUri = downloadedUri == null ? downloadManager.getUriForDownloadedFile(downloadId)
                    : Uri.parse(downloadedUri);

            if (apkUri == null || apkUri.toString().trim().isEmpty()) {
                Log.e(TAG, "Quantune update completed without a readable APK URI.");
                return;
            }

            // The activity handles Android 8+ unknown-app installation permission in the foreground.
            prefs.edit()
                    .putString("pending_update_uri", apkUri.toString())
                    .remove("download_id")
                    .apply();

            Intent openApp = new Intent(context, MainActivity.class);
            openApp.setAction("com.dsann.music.action.INSTALL_UPDATE");
            openApp.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                    | Intent.FLAG_ACTIVITY_CLEAR_TOP
                    | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            context.startActivity(openApp);
        } catch (Exception e) {
            Log.e(TAG, "Failed to hand the downloaded Quantune APK to the installer flow.", e);
        } finally {
            if (cursor != null) cursor.close();
        }
    }
}
