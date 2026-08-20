package com.huginprep.app.util

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.core.content.ContextCompat

/**
 * 运行时权限助手（单例）：集中管理权限组合、状态检查与设置页跳转。
 */
object PermissionHelper {

    const val CAMERA = Manifest.permission.CAMERA

    /**
     * 当前系统的媒体读取权限：
     * - Android 13 (API 33)+：READ_MEDIA_IMAGES
     * - Android 12 (API 32) 及以下：READ_EXTERNAL_STORAGE
     *
     * 注意：导入流程走 Photo Picker（PickMultipleVisualMedia），运行期**不需要**
     * 存储权限；此权限仅当「直接读取 MediaStore 的兜底路径」时才需要。
     */
    fun storagePermission(): String? = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> Manifest.permission.READ_MEDIA_IMAGES
        else -> Manifest.permission.READ_EXTERNAL_STORAGE
    }

    /** 实时拍摄所需的权限（仅相机） */
    fun capturePermissions(): Array<String> = arrayOf(CAMERA)

    /** 完整权限集合：相机 + 当前系统的媒体读取权限（如需要） */
    fun requiredPermissions(): Array<String> {
        val list = mutableListOf(CAMERA)
        storagePermission()?.let { list.add(it) }
        return list.toTypedArray()
    }

    /** 是否已授予全部给定权限 */
    fun hasPermissions(context: Context, permissions: Array<String>): Boolean =
        permissions.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }

    /** 是否已授予拍摄所需的全部权限 */
    fun hasCapturePermissions(context: Context): Boolean =
        hasPermissions(context, capturePermissions())

    /** 打开应用详情设置页（用户勾选「不再询问」时引导其手动开启） */
    fun openAppSettings(context: Context) {
        context.startActivity(
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:${context.packageName}")
            )
        )
    }
}

/**
 * Compose 封装：创建「多权限请求启动器」。
 *
 * 用法（在 MainActivity / 任意 Composable 中）：
 * ```
 * val requestPermissions = rememberPermissionRequester(
 *     onAllGranted = { /* 全部授予，进入相机 */ },
 *     onDenied = { denied -> /* 有权限被拒，提示或跳设置 */ }
 * )
 * Button(onClick = { requestPermissions(PermissionHelper.capturePermissions()) }) { ... }
 * ```
 *
 * @param onAllGranted 所有权限都被授予
 * @param onDenied 有权限被拒绝（参数为被拒权限列表）
 * @return launch 函数：传入权限数组即触发系统弹窗
 */
@Composable
fun rememberPermissionRequester(
    onAllGranted: () -> Unit,
    onDenied: (denied: List<String>) -> Unit
): (Array<String>) -> Unit {
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val denied = result.filterValues { !it }.keys.toList()
        if (denied.isEmpty()) onAllGranted() else onDenied(denied)
    }
    return { permissions -> launcher.launch(permissions) }
}
