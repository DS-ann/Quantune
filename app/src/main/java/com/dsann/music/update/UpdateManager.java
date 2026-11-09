package com.dsann.music.update;

import android.app.Activity;
import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Environment;
import android.util.Log;
import android.view.View;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;

public class UpdateManager {

    private static final String TAG = "UpdateManager";
    private static final String GITHUB_OWNER = "DS-ann";
    private static final String GITHUB_REPO = "Quantune";

    private final Context context;
    private final GitHubReleaseClient client;

    public UpdateManager(Context context) {
        this.context = context.getApplicationContext();
        this.client = new GitHubReleaseClient(GITHUB_OWNER, GITHUB_REPO);
    }

    public void checkForUpdate(View rootView, String currentVersion) {
        showSnackbarAnchored(rootView, "Checking for updates...", Snackbar.LENGTH_SHORT);

        new Thread(() -> {
            final GitHubReleaseClient.ReleaseInfo release = client.getLatestRelease();

            if (release == null) {
                String detail = client.getLastError() == null
                        ? "No compatible APK was found in the latest release."
                        : client.getLastError().getMessage();
                runOnUi(() -> showSnackbarAnchored(
                        rootView,
                        "Couldn't check for updates: " + detail,
                        Snackbar.LENGTH_LONG
                ));
                return;
            }

            boolean isNewer = VersionUtils.isNewer(release.tagName, currentVersion);
            runOnUi(() -> {
                if (isNewer) {
                    promptUpdate(rootView, release);
                } else {
                    showSnackbarAnchored(rootView, "You're using the latest Quantune version.", Snackbar.LENGTH_LONG);
                }
            });
        }).start();
    }

    private void promptUpdate(View rootView, GitHubReleaseClient.ReleaseInfo release) {
        String message = "New version " + release.tagName + " is available!\n\n";
        if (release.changelog != null && !release.changelog.isEmpty()) {
            String changelog = release.changelog.length() > 600
                    ? release.changelog.substring(0, 600) + "..."
                    : release.changelog;
            message += changelog;
        }

        Activity host = getActivityIfPossible();
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(
                host != null ? host : rootView.getContext()
        )
                .setTitle("Quantune update available")
                .setMessage(message)
                .setPositiveButton("Download", (dialog, which) -> downloadRelease(rootView, release))
                .setNegativeButton("Later", null);

        runOnUi(builder::show);
    }

    /**
     * Download the APK from the release already shown to the user.
     * This avoids making a second Releases API request after the user taps Update.
     */
    public void downloadRelease(View rootView, GitHubReleaseClient.ReleaseInfo release) {
        if (release == null || release.apkUrl == null || release.apkUrl.trim().isEmpty()) {
            showSnackbarAnchored(rootView, "This release has no downloadable Quantune APK.", Snackbar.LENGTH_LONG);
            return;
        }
        startDownload(rootView, release.apkUrl, release.tagName);
    }

    private void startDownload(View rootView, String apkUrl, String releaseTag) {
        try {
            Uri uri = Uri.parse(apkUrl);
            if (!"https".equalsIgnoreCase(uri.getScheme())) {
                throw new IllegalArgumentException("The release APK URL is not secure.");
            }

            DownloadManager dm = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
            if (dm == null) {
                showSnackbarAnchored(rootView, "Download Manager is not available on this device.", Snackbar.LENGTH_LONG);
                return;
            }

            String safeTag = releaseTag == null ? "latest" : releaseTag.replaceAll("[^A-Za-z0-9._-]", "_");
            String filename = "Quantune-update-" + safeTag + "-" + System.currentTimeMillis() + ".apk";

            DownloadManager.Request request = new DownloadManager.Request(uri);
            request.setTitle("Quantune " + safeTag + " update");
            request.setDescription("Downloading the official GitHub release APK...");
            request.setMimeType("application/vnd.android.package-archive");
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            request.setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, filename);

            long downloadId = dm.enqueue(request);

            SharedPreferences prefs = context.getSharedPreferences("update_prefs", Context.MODE_PRIVATE);
            prefs.edit()
                    .putLong("download_id", downloadId)
                    .putString("download_filename", filename)
                    .putString("download_release_tag", safeTag)
                    .apply();

            runOnUi(() -> {
                Snackbar sb = makeAnchoredSnackbar(rootView, "Downloading Quantune update " + safeTag + "...");
                sb.setAction("View", v -> {
                    Intent intent = new Intent(DownloadManager.ACTION_VIEW_DOWNLOADS);
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    context.startActivity(intent);
                });
                sb.show();
            });
        } catch (Exception e) {
            Log.e(TAG, "Quantune update download failed", e);
            showSnackbarAnchored(rootView, "Update download failed. Please try again.", Snackbar.LENGTH_LONG);
        }
    }

    private void runOnUi(Runnable runnable) {
        new android.os.Handler(android.os.Looper.getMainLooper()).post(runnable);
    }

    private Snackbar makeAnchoredSnackbar(View rootView, String message) {
        return Snackbar.make(rootView, message, Snackbar.LENGTH_LONG);
    }

    private void showSnackbarAnchored(View rootView, String message, int duration) {
        runOnUi(() -> com.dsann.music.ui.SnackbarController.INSTANCE.showMessage(message));
    }

    private Activity getActivityIfPossible() {
        return null;
    }
}
