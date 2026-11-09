package com.dsann.music.update;

import android.os.Build;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class GitHubReleaseClient {

    private static final String TAG = "GitHubReleaseClient";
    private final String owner;
    private final String repo;
    private volatile Exception lastError;

    public Exception getLastError() {
        return lastError;
    }

    public GitHubReleaseClient(String owner, String repo) {
        this.owner = owner;
        this.repo = repo;
    }

    public ReleaseInfo getLatestRelease() {
        lastError = null;
        HttpURLConnection connection = null;
        try {
            String apiUrl = "https://api.github.com/repos/" + owner + "/" + repo + "/releases/latest";
            connection = openConnection(apiUrl);

            int responseCode = connection.getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_OK) {
                throw new IOException("GitHub Releases returned HTTP " + responseCode);
            }

            JSONObject json = new JSONObject(readResponse(connection));
            ReleaseInfo release = createReleaseInfo(json);
            if (release.apkUrl == null || release.apkUrl.trim().isEmpty()) {
                throw new IOException("The latest Quantune release does not contain a compatible APK asset.");
            }
            return release;
        } catch (Exception e) {
            lastError = e;
            Log.e(TAG, "Failed to fetch latest Quantune release", e);
            return null;
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    public java.util.List<ReleaseInfo> getAllReleases() {
        lastError = null;
        java.util.List<ReleaseInfo> releases = new java.util.ArrayList<>();
        HttpURLConnection connection = null;
        try {
            String apiUrl = "https://api.github.com/repos/" + owner + "/" + repo + "/releases?per_page=30";
            connection = openConnection(apiUrl);

            int responseCode = connection.getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_OK) {
                throw new IOException("GitHub Releases returned HTTP " + responseCode);
            }

            JSONArray jsonArray = new JSONArray(readResponse(connection));
            for (int i = 0; i < jsonArray.length(); i++) {
                releases.add(createReleaseInfo(jsonArray.getJSONObject(i)));
            }
        } catch (Exception e) {
            lastError = e;
            Log.e(TAG, "Failed to fetch Quantune release history", e);
        } finally {
            if (connection != null) connection.disconnect();
        }
        return releases;
    }

    private HttpURLConnection openConnection(String address) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(address).openConnection();
        connection.setRequestMethod("GET");
        connection.setRequestProperty("User-Agent", "Quantune-Android-Updater");
        connection.setRequestProperty("Accept", "application/vnd.github+json");
        connection.setConnectTimeout(10000);
        connection.setReadTimeout(15000);
        connection.setInstanceFollowRedirects(true);
        return connection;
    }

    private String readResponse(HttpURLConnection connection) throws IOException {
        StringBuilder response = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
        }
        return response.toString();
    }

    private ReleaseInfo createReleaseInfo(JSONObject json) throws Exception {
        String tagName = json.getString("tag_name");
        String body = json.optString("body", "");
        JSONArray assets = json.optJSONArray("assets");

        String apkName = selectApkAssetName(assets);
        String apkUrl = apkName == null ? null : findAssetUrl(assets, apkName);
        String checksumUrl = apkName == null ? null : findAssetUrl(assets, apkName + ".sha256");

        // Backward compatibility with older releases that used a single APK and checksum.txt.
        if (checksumUrl == null) {
            checksumUrl = findAssetUrl(assets, "checksum.txt");
        }

        return new ReleaseInfo(tagName, body, apkUrl, checksumUrl);
    }

    private String selectApkAssetName(JSONArray assets) {
        if (assets == null) return null;

        // The official release workflow publishes a combined ARM Universal APK.
        if (hasAsset(assets, "Quantune-Universal.apk")) {
            return "Quantune-Universal.apk";
        }

        // Fall back to the best matching split APK for releases without the Universal asset.
        boolean arm64 = false;
        for (String abi : Build.SUPPORTED_ABIS) {
            if ("arm64-v8a".equals(abi)) {
                arm64 = true;
                break;
            }
        }

        String preferred = arm64 ? "Quantune-arm64-v8a.apk" : "Quantune-armeabi-v7a.apk";
        String alternate = arm64 ? "Quantune-armeabi-v7a.apk" : "Quantune-arm64-v8a.apk";
        if (hasAsset(assets, preferred)) return preferred;
        if (hasAsset(assets, alternate)) return alternate;

        // Preserve compatibility with older builds that published app-release.apk.
        if (hasAsset(assets, "app-release.apk")) return "app-release.apk";
        return null;
    }

    private boolean hasAsset(JSONArray assets, String expectedName) {
        return findAssetUrl(assets, expectedName) != null;
    }

    private String findAssetUrl(JSONArray assets, String expectedName) {
        if (assets == null) return null;
        for (int i = 0; i < assets.length(); i++) {
            JSONObject asset = assets.optJSONObject(i);
            if (asset == null || !expectedName.equals(asset.optString("name"))) continue;
            String url = asset.optString("browser_download_url", null);
            if (url != null && !url.trim().isEmpty()) return url;
        }
        return null;
    }

    public static class ReleaseInfo {
        public final String tagName;
        public final String changelog;
        public final String apkUrl;
        public final String checksumUrl;

        public ReleaseInfo(String tagName, String changelog, String apkUrl, String checksumUrl) {
            this.tagName = tagName;
            this.changelog = changelog;
            this.apkUrl = apkUrl;
            this.checksumUrl = checksumUrl;
        }
    }
}
