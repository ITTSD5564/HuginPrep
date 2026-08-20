package com.huginprep.app.util

import android.content.Context
import android.content.SharedPreferences
import java.io.File

/**
 * 应用设置存储（SharedPreferences 封装）。
 *
 * 说明：androidx.preference 库的渲染层基于 Fragment（PreferenceFragmentCompat），
 * 与「Compose 实现、不用 Fragment」的约束冲突，因此这里使用与 Preference 库
 * 相同的 SharedPreferences 后端 + Compose 手写 UI，交互与视觉保持一致。
 */
object SettingsManager {

    private const val PREFS_NAME = "huginprep_settings"

    private const val KEY_DEFAULT_PROJECT_PATH = "default_project_path"
    private const val KEY_PHOTO_NAME_PREFIX = "photo_name_prefix_enabled"
    private const val KEY_AUTO_CHECK_UPDATE = "auto_check_update"

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // ==================== 默认项目保存路径 ====================

    /** 自定义保存路径（null = 使用默认应用专属目录 projects/） */
    fun getDefaultProjectPath(context: Context): String? =
        prefs(context).getString(KEY_DEFAULT_PROJECT_PATH, null)

    fun setDefaultProjectPath(context: Context, path: String) {
        prefs(context).edit().putString(KEY_DEFAULT_PROJECT_PATH, path).apply()
    }

    /**
     * 将自定义路径解析为 File（仅当可解析时返回）。
     * 支持两种形式：
     * - SAF 树 URI 路径段："primary:Download/HuginProjects" -> /storage/emulated/0/Download/HuginProjects
     * - 绝对路径：/storage/emulated/0/xxx
     */
    fun resolveDefaultProjectRoot(context: Context): File? {
        val raw = getDefaultProjectPath(context) ?: return null
        return runCatching {
            val file = if (raw.startsWith("primary:")) {
                File("/storage/emulated/0", raw.removePrefix("primary:"))
            } else {
                File(raw)
            }
            file.takeIf { it.exists() || it.mkdirs() }
        }.getOrNull()
    }

    // ==================== 照片命名前缀 ====================

    fun isPhotoNamePrefixEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_PHOTO_NAME_PREFIX, false)

    fun setPhotoNamePrefixEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_PHOTO_NAME_PREFIX, enabled).apply()
    }

    // ==================== 启动自动检查更新 ====================

    fun isAutoCheckUpdateEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_AUTO_CHECK_UPDATE, true)

    fun setAutoCheckUpdateEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_AUTO_CHECK_UPDATE, enabled).apply()
    }
}
