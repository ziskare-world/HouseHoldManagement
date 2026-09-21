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
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

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
                    val changelog = json.optString("description", json.optString("changelog", "• Added GitHub in-app update system\n• Fixed Giving Money & Debt recording\n• Performance improvements & bug fixes"))
                    val downloadUrl = json.optString("downloadUrl", GITHUB_RELEASES_PAGE)
                    val directApkUrl = json.optString("directApkUrl", "").takeIf { it.isNotBlank() }

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

    fun launchUpdateDownload(updateInfo: AppUpdateInfo) {
        try {
            val targetUrl = updateInfo.directApkUrl ?: updateInfo.downloadUrl
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(targetUrl)).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Error opening update URL: ${e.message}")
        }
    }
}
