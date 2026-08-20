package com.huginprep.app.update

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.huginprep.app.R
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * APK 下载前台服务：Notification 展示下载进度，完成后调用系统安装器。
 *
 * 前置条件：
 * - Android 13+ 的 POST_NOTIFICATIONS（仅影响通知栏展示，不影响下载/安装）；
 * - Android 8+ 的 REQUEST_INSTALL_PACKAGES（安装未知应用，系统会引导用户授权）。
 */
class DownloadService : Service() {

    companion object {
        private const val TAG = "HuginPrep"
        private const val CHANNEL_ID = "apk_download"
        private const val NOTIFICATION_ID = 1001

        const val ACTION_DOWNLOAD = "com.huginprep.app.action.DOWNLOAD_APK"

        private const val EXTRA_URL = "extra_url"
        private const val EXTRA_FILE_NAME = "extra_file_name"

        /** 启动下载前台服务（Android 8+ 需 startForegroundService） */
        fun start(context: Context, url: String, fileName: String) {
            val intent = Intent(context, DownloadService::class.java).apply {
                action = ACTION_DOWNLOAD
                putExtra(EXTRA_URL, url)
                putExtra(EXTRA_FILE_NAME, fileName)
            }
            ContextCompat.startForegroundService(context, intent)
        }
    }

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_DOWNLOAD -> {
                val url = intent.getStringExtra(EXTRA_URL)
                val fileName = intent.getStringExtra(EXTRA_FILE_NAME) ?: "HuginPrep.apk"
                if (url != null) startDownload(url, fileName)
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun startDownload(url: String, fileName: String) {
        createChannel()
        startForeground(NOTIFICATION_ID, buildProgressNotification(getString(R.string.update_downloading), 0))

        serviceScope.launch {
            try {
                val apkFile = File(cacheDir, "apk").apply { mkdirs() }
                    .resolve(fileName)
                download(url, apkFile) { progress ->
                    updateProgressNotification(progress)
                }
                Log.d(TAG, "下载完成: ${apkFile.absolutePath}")
                onDownloadFinished(apkFile)
            } catch (e: Exception) {
                Log.e(TAG, "下载失败", e)
                onDownloadFailed(e.message ?: "未知错误")
            }
        }
    }

    /** 流式下载到文件，回调进度百分比 */
    private fun download(url: String, dest: File, onProgress: (Int) -> Unit) {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 15_000
        conn.readTimeout = 15_000
        conn.setRequestProperty("User-Agent", "HuginPrep-Android")
        try {
            conn.connect()
            if (conn.responseCode !in 200..299) {
                throw IllegalStateException("下载地址返回 ${conn.responseCode}")
            }
            val total = conn.contentLength
            var downloaded = 0L
            conn.inputStream.use { input ->
                dest.outputStream().use { output ->
                    val buffer = ByteArray(8192)
                    while (true) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                        downloaded += read
                        if (total > 0) {
                            onProgress(((downloaded * 100) / total).toInt())
                        }
                    }
                }
            }
        } finally {
            conn.disconnect()
        }
    }

    /** 下载完成：通知可点击安装 + 直接调起系统安装器 */
    private fun onDownloadFinished(apkFile: File) {
        val uri: Uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", apkFile)
        val installIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, installIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(
            NOTIFICATION_ID,
            NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentTitle(getString(R.string.update_download_complete))
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .build()
        )

        // 调起系统安装器（未知来源授权由系统引导）
        runCatching { startActivity(installIntent) }
            .onFailure { e ->
                Log.e(TAG, "调起安装器失败", e)
                manager.notify(
                    NOTIFICATION_ID,
                    NotificationCompat.Builder(this, CHANNEL_ID)
                        .setSmallIcon(android.R.drawable.stat_sys_warning)
                        .setContentTitle(getString(R.string.update_download_failed, e.message ?: ""))
                        .setAutoCancel(true)
                        .build()
                )
            }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    /** 下载失败：失败通知 + 结束服务 */
    private fun onDownloadFailed(message: String) {
        getSystemService(NotificationManager::class.java).notify(
            NOTIFICATION_ID,
            NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_warning)
                .setContentTitle(getString(R.string.update_download_failed, message))
                .setAutoCancel(true)
                .build()
        )
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.update_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.update_channel_desc)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildProgressNotification(title: String, progress: Int): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(title)
            .setContentText("$progress%")
            .setProgress(100, progress, false)
            .setOngoing(true)
            .build()

    private fun updateProgressNotification(progress: Int) {
        getSystemService(NotificationManager::class.java).notify(
            NOTIFICATION_ID,
            buildProgressNotification(getString(R.string.update_downloading), progress)
        )
    }
}
