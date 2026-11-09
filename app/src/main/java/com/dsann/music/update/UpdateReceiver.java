package com.dsann.music.update;

import android.app.DownloadManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.dsann.music.MainActivity;
import com.dsann.music.R;

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

            postInstallNotification(context);
        } catch (Exception e) {
            Log.e(TAG, "Failed to hand the downloaded Quantune APK to the installer flow.", e);
        } finally {
            if (cursor != null) cursor.close();
        }
    }

    private void postInstallNotification(Context context) {
        final String channelId = "quantune_updates";
        final int notificationId = 7124;

        NotificationManager manager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) return;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    channelId,
                    "Quantune updates",
                    NotificationManager.IMPORTANCE_DEFAULT
            );
            channel.setDescription("Notifications when a Quantune release update is ready to install.");
            manager.createNotificationChannel(channel);
        }

        Intent openApp = new Intent(context, MainActivity.class);
        openApp.setAction("com.dsann.music.action.INSTALL_UPDATE");
        openApp.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_CLEAR_TOP
                | Intent.FLAG_ACTIVITY_SINGLE_TOP);

        PendingIntent pendingIntent = PendingIntent.getActivity(
                context,
                notificationId,
                openApp,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        NotificationCompat.Builder notification = new NotificationCompat.Builder(context, channelId)
                .setSmallIcon(R.drawable.ic_update)
                .setContentTitle("Quantune update downloaded")
                .setContentText("Tap to open Quantune and install the latest release.")
                .setStyle(new NotificationCompat.BigTextStyle().bigText(
                        "The GitHub Release APK is ready. Tap to continue with Android's installer."
                ))
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT);

        try {
            NotificationManagerCompat.from(context).notify(notificationId, notification.build());
        } catch (SecurityException e) {
            // Android 13+ may deny notifications when POST_NOTIFICATIONS has not been granted.
            // The APK URI remains stored and will be picked up the next time Quantune resumes.
            Log.w(TAG, "Update notification permission is unavailable; pending install was retained.", e);
        }
    }

}
