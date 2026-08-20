package com.huginprep.app.update

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * GitHub Releases 更新检查器（手写 HttpURLConnection + org.json，零额外依赖）。
 *
 * 检查 https://api.github.com/repos/ITTSD5564/HuginPrep/releases/latest。
 */
object UpdateChecker {

    private const val TAG = "HuginPrep"

    /** 你的 GitHub 仓库（owner/repo） */
    private const val REPO = "ITTSD5564/HuginPrep"

    private const val API_URL = "https://api.github.com/repos/$REPO/releases/latest"

    /** 远程更新信息 */
    data class UpdateInfo(
        val latestVersion: String,   // 远程 tag_name，如 v1.0.1
        val downloadUrl: String?,    // .apk 资源的 browser_download_url
        val releaseNotes: String?,   // release body
        val isNewer: Boolean         // 远程是否比本地新
    )

    /**
     * 检查是否有新版本。
     *
     * @return [UpdateInfo]；网络失败 / 仓库不存在 / 解析失败时返回 null
     */
    suspend fun checkForUpdate(context: Context): UpdateInfo? {
        val localVersion = localVersionName(context) ?: return null
        val json = getJson(API_URL) ?: return null
        val tag = json.optString("tag_name").ifBlank { return null }
        val notes = json.optString("body").ifBlank { null }
        return UpdateInfo(
            latestVersion = tag,
            downloadUrl = findApkDownloadUrl(json),
            releaseNotes = notes,
            isNewer = isNewerVersion(tag, localVersion)
        )
    }

    /** 从 releases/latest 响应中提取第一个 .apk 资源下载地址 */
    private fun findApkDownloadUrl(json: JSONObject): String? {
        val assets = json.optJSONArray("assets") ?: return null
        for (i in 0 until assets.length()) {
            val asset = assets.optJSONObject(i) ?: continue
            if (asset.optString("name").endsWith(".apk", ignoreCase = true)) {
                return asset.optString("browser_download_url").ifBlank { null }
            }
        }
        return null
    }

    /** 版本比较：v1.0.1 > 0.1.0 > 0.1.0-beta 之外按段比较 */
    fun isNewerVersion(remote: String, local: String): Boolean {
        val r = parseVersion(remote) ?: return false
        val l = parseVersion(local) ?: return true
        for (i in 0 until maxOf(r.size, l.size)) {
            val a = r.getOrElse(i) { 0 }
            val b = l.getOrElse(i) { 0 }
            if (a != b) return a > b
        }
        return false
    }

    private fun parseVersion(version: String): List<Int>? {
        val cleaned = version.trim().removePrefix("v").removePrefix("V")
        val parts = cleaned.split(".").mapNotNull { it.toIntOrNull() }
        return parts.takeIf { it.isNotEmpty() }
    }

    /** 本地版本号（build.gradle versionName） */
    fun localVersionName(context: Context): String? = runCatching {
        val pm = context.packageManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0)).versionName
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageInfo(context.packageName, 0).versionName
        }
    }.getOrNull()

    /** GET 请求并解析 JSON（10s 超时；GitHub API 强制要求 User-Agent） */
    private suspend fun getJson(url: String): JSONObject? = withContext(Dispatchers.IO) {
        val text = runCatching {
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.connectTimeout = 10_000
            conn.readTimeout = 10_000
            conn.setRequestProperty("Accept", "application/vnd.github+json")
            conn.setRequestProperty("User-Agent", "HuginPrep-Android")
            try {
                if (conn.responseCode !in 200..299) {
                    Log.e(TAG, "GitHub API 返回 ${conn.responseCode}")
                    null
                } else {
                    conn.inputStream.bufferedReader().use { it.readText() }
                }
            } finally {
                conn.disconnect()
            }
        }.getOrNull()
        text?.let { runCatching { JSONObject(it) }.getOrNull() }
    }
}
