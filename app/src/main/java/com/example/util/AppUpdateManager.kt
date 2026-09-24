package com.example.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.io.FileOutputStream

sealed class UpdateDownloadState {
    object Idle : UpdateDownloadState()
    data class Downloading(val progress: Float, val downloadedBytes: Long, val totalBytes: Long) : UpdateDownloadState()
    data class Downloaded(val apkFile: File) : UpdateDownloadState()
    data class Error(val message: String) : UpdateDownloadState()
}

data class AppUpdateInfo(
    val hasUpdate: Boolean,
    val isMandatory: Boolean,
    val latestVersionCode: Int,
    val latestVersionName: String,
    val currentVersionCode: Int = BuildConfig.VERSION_CODE,
    val currentVersionName: String = BuildConfig.VERSION_NAME,
    val title: String = "New Update Available",
    val changelog: String = "",
    val downloadUrl: String = "https://github.com/ziskare-world/HouseHoldManagement/releases/latest",
    val directApkUrl: String? = null
)

class AppUpdateManager(private val context: Context) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private val _downloadState = MutableStateFlow<UpdateDownloadState>(UpdateDownloadState.Idle)
    val downloadState: StateFlow<UpdateDownloadState> = _downloadState.asStateFlow()

    companion object {
        private const val TAG = "AppUpdateManager"
        const val GITHUB_OWNER = "ziskare-world"
        const val GITHUB_REPO = "HouseHoldManagement"
        const val RAW_VERSION_URL = "https://raw.githubusercontent.com/$GITHUB_OWNER/$GITHUB_REPO/main/version.json"
        const val GITHUB_RELEASES_API = "https://api.github.com/repos/$GITHUB_OWNER/$GITHUB_REPO/releases/latest"
        const val GITHUB_RELEASES_PAGE = "https://github.com/$GITHUB_OWNER/$GITHUB_REPO/releases/latest"
    }

    suspend fun checkForUpdate(): AppUpdateInfo? = withContext(Dispatchers.IO) {
        // Step 1: Try reading raw version.json from GitHub
        try {
            val request = Request.Builder()
                .url(RAW_VERSION_URL)
                .header("Cache-Control", "no-cache")
                .header("Pragma", "no-cache")
                .build()

            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val bodyStr = response.body?.string()
                if (!bodyStr.isNullOrBlank()) {
                    val json = JSONObject(bodyStr)
                    val remoteVersionCode = json.optInt("versionCode", BuildConfig.VERSION_CODE)
                    val remoteVersionName = json.optString("versionName", BuildConfig.VERSION_NAME)
                    val minSupportedVersionCode = json.optInt("minSupportedVersionCode", 1)
                    val isMandatory = json.optBoolean("isMandatory", true) || (BuildConfig.VERSION_CODE < minSupportedVersionCode)
                    val title = json.optString("title", "New App Update Available (v$remoteVersionName)")
                    val changelog = json.optString("description", json.optString("changelog", "• In-app APK download with live progress bar\n• Mutual roommate visibility and instant sync\n• Performance improvements & bug fixes"))
                    val downloadUrl = json.optString("downloadUrl", GITHUB_RELEASES_PAGE)
                    val directApkUrl = json.optString("directApkUrl", "").takeIf { it.isNotBlank() }
                        ?: "https://github.com/$GITHUB_OWNER/$GITHUB_REPO/releases/download/v$remoteVersionName/RoomieVault-v$remoteVersionName.apk"

                    val hasUpdate = remoteVersionCode > BuildConfig.VERSION_CODE || (remoteVersionName != BuildConfig.VERSION_NAME && remoteVersionCode >= BuildConfig.VERSION_CODE)

                    return@withContext AppUpdateInfo(
                        hasUpdate = hasUpdate,
                        isMandatory = isMandatory,
                        latestVersionCode = remoteVersionCode,
                        latestVersionName = remoteVersionName,
                        title = title,
                        changelog = changelog,
                        downloadUrl = downloadUrl,
                        directApkUrl = directApkUrl
                    )
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "version.json check error: ${e.message}")
        }

        // Step 2: Fallback to GitHub Releases API
        try {
            val request = Request.Builder()
                .url(GITHUB_RELEASES_API)
                .header("Accept", "application/vnd.github.v3+json")
                .header("User-Agent", "RoomieVault-Android")
                .build()

            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val bodyStr = response.body?.string()
                if (!bodyStr.isNullOrBlank()) {
                    val json = JSONObject(bodyStr)
                    val tagName = json.optString("tag_name", "v1.0")
                    val cleanVersionName = tagName.removePrefix("v").trim()
                    val body = json.optString("body", "• Latest improvements & bug fixes on GitHub.")
                    val htmlUrl = json.optString("html_url", GITHUB_RELEASES_PAGE)

                    var directApkUrl: String? = null
                    val assets = json.optJSONArray("assets")
                    if (assets != null) {
                        for (i in 0 until assets.length()) {
                            val asset = assets.getJSONObject(i)
                            val name = asset.optString("name", "")
                            if (name.endsWith(".apk", ignoreCase = true)) {
                                directApkUrl = asset.optString("browser_download_url", null)
                                break
                            }
                        }
                    }

                    val hasUpdate = cleanVersionName.isNotBlank() && cleanVersionName != BuildConfig.VERSION_NAME

                    return@withContext AppUpdateInfo(
                        hasUpdate = hasUpdate,
                        isMandatory = true,
                        latestVersionCode = BuildConfig.VERSION_CODE + (if (hasUpdate) 1 else 0),
                        latestVersionName = cleanVersionName,
                        title = "New Update Available: $tagName",
                        changelog = body,
                        downloadUrl = directApkUrl ?: htmlUrl,
                        directApkUrl = directApkUrl
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "GitHub Releases API check error: ${e.message}")
        }

        null
    }

    /**
     * Downloads the APK file in-app with live streaming byte progress.
     */
    suspend fun downloadApk(updateInfo: AppUpdateInfo) = withContext(Dispatchers.IO) {
        val targetUrl = updateInfo.directApkUrl?.takeIf { it.isNotBlank() }
            ?: "https://github.com/$GITHUB_OWNER/$GITHUB_REPO/releases/download/v${updateInfo.latestVersionName}/RoomieVault-v${updateInfo.latestVersionName}.apk"

        try {
            _downloadState.value = UpdateDownloadState.Downloading(0.01f, 0L, 0L)

            val updatesDir = File(context.cacheDir, "updates").apply { mkdirs() }
            val outputFile = File(updatesDir, "RoomieVault-v${updateInfo.latestVersionName}.apk")
            if (outputFile.exists()) {
                outputFile.delete()
            }

            val request = Request.Builder()
                .url(targetUrl)
                .header("User-Agent", "RoomieVault-InApp-Updater")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                _downloadState.value = UpdateDownloadState.Error("Failed to download update: HTTP ${response.code}")
                return@withContext
            }

            val body = response.body
            if (body == null) {
                _downloadState.value = UpdateDownloadState.Error("Empty download body received")
                return@withContext
            }

            val contentLength = body.contentLength()
            var downloadedBytes = 0L

            body.byteStream().use { input ->
                FileOutputStream(outputFile).use { output ->
                    val buffer = ByteArray(8 * 1024)
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        downloadedBytes += bytesRead

                        val progress = if (contentLength > 0) {
                            (downloadedBytes.toFloat() / contentLength.toFloat()).coerceIn(0f, 1f)
                        } else {
                            0.5f // Indeterminate fallback
                        }
                        _downloadState.value = UpdateDownloadState.Downloading(progress, downloadedBytes, contentLength)
                    }
                    output.flush()
                }
            }

            _downloadState.value = UpdateDownloadState.Downloaded(outputFile)
        } catch (e: Exception) {
            Log.e(TAG, "downloadApk error: ${e.message}", e)
            _downloadState.value = UpdateDownloadState.Error(e.localizedMessage ?: "Download failed")
        }
    }

    /**
     * Triggers the Android package installer Intent using FileProvider.
     */
    fun installApk(apkFile: File) {
        try {
            // Check unknown apps install permission on Android 8+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (!context.packageManager.canRequestPackageInstalls()) {
                    val settingsIntent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                        data = Uri.parse("package:${context.packageName}")
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(settingsIntent)
                    return
                }
            }

            val apkUri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )

            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
            }
            context.startActivity(installIntent)
        } catch (e: Exception) {
            Log.e(TAG, "installApk error: ${e.message}", e)
            _downloadState.value = UpdateDownloadState.Error("Installation error: ${e.localizedMessage}")
        }
    }

    fun resetDownloadState() {
        _downloadState.value = UpdateDownloadState.Idle
    }

    fun launchUpdateDownload(updateInfo: AppUpdateInfo) {
        val targetUrl = updateInfo.directApkUrl ?: updateInfo.downloadUrl
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(targetUrl)).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Error opening update URL: ${e.message}")
        }
    }
}
