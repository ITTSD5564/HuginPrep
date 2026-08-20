package com.huginprep.app.ui.settings

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.huginprep.app.util.SettingsManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 设置页 UI 状态 */
data class SettingsUiState(
    val defaultProjectPath: String? = null,
    val photoPrefixEnabled: Boolean = false,
    val autoCheckUpdate: Boolean = true
)

/**
 * 设置页 ViewModel：读写 SharedPreferences（与 Preference 库相同的后端）。
 */
class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "HuginPrep"

        /** 无 Hilt 场景的工厂 */
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]!!
                SettingsViewModel(app)
            }
        }
    }

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        reload()
    }

    /** 从 SharedPreferences 重新加载 */
    fun reload() {
        val app = getApplication<Application>()
        _uiState.value = SettingsUiState(
            defaultProjectPath = SettingsManager.getDefaultProjectPath(app),
            photoPrefixEnabled = SettingsManager.isPhotoNamePrefixEnabled(app),
            autoCheckUpdate = SettingsManager.isAutoCheckUpdateEnabled(app)
        )
    }

    /**
     * 保存 SAF 目录选择器选中的默认项目路径：
     * 1. 持久化目录的读写权限（takePersistableUriPermission，重启后仍有效）；
     * 2. 优先保存可解析为物理路径的形式（primary:Download/xxx），否则存目录名；
     * 3. 刷新 UI。
     */
    fun saveDefaultProjectPath(context: Context, uri: Uri) {
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        }.onFailure { e -> Log.w(TAG, "持久化目录权限失败（设备可能不支持）", e) }

        val stored = pathFromTreeUri(uri)
            ?: DocumentFile.fromTreeUri(context, uri)?.name
            ?: uri.toString()
        SettingsManager.setDefaultProjectPath(context, stored)
        reload()
    }

    fun setPhotoPrefixEnabled(context: Context, enabled: Boolean) {
        SettingsManager.setPhotoNamePrefixEnabled(context, enabled)
        _uiState.value = _uiState.value.copy(photoPrefixEnabled = enabled)
    }

    fun setAutoCheckUpdate(context: Context, enabled: Boolean) {
        SettingsManager.setAutoCheckUpdateEnabled(context, enabled)
        _uiState.value = _uiState.value.copy(autoCheckUpdate = enabled)
    }

    /**
     * 从树 URI 提取物理路径描述：
     * content://com.android.externalstorage.documents/tree/primary%3ADownload%2FHuginProjects
     * -> primary:Download/HuginProjects（lastPathSegment 已自动解码）
     */
    private fun pathFromTreeUri(uri: Uri): String? {
        val docId = uri.lastPathSegment ?: return null
        return docId.takeIf { it.contains(":") }
    }
}
