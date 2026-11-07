package com.dsann.music.util

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Handles the Cancel action attached to an active download progress notification.
 * This receiver is private to the app and delegates all network/job cancellation
 * to SongDownloader, which performs blocking teardown on its IO scope.
 */
class SongDownloadCancelReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != ACTION_CANCEL) return

        val videoId = intent.getStringExtra(EXTRA_VIDEO_ID)
            ?.takeIf { it.isNotBlank() } ?: return
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, -1)
        if (notificationId <= 0) return

        SongDownloader.cancelDownloadFromNotification(
            context.applicationContext,
            videoId,
            notificationId
        )
    }

    companion object {
        const val ACTION_CANCEL = "com.dsann.music.action.CANCEL_SONG_DOWNLOAD"
        const val EXTRA_VIDEO_ID = "com.dsann.music.extra.DOWNLOAD_VIDEO_ID"
        const val EXTRA_NOTIFICATION_ID = "com.dsann.music.extra.DOWNLOAD_NOTIFICATION_ID"
    }
}
