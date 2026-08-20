package com.huginprep.app.update

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.huginprep.app.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 更新检查 UI 状态 */
data class UpdateUiState(
    val isChecking: Boolean = false,
    val updateInfo: UpdateChecker.UpdateInfo? = null,  // 非空 → 显示新版本弹窗
    val noUpdateMessage: String? = null,               // 非空 → 显示"已是最新版本"
    val errorMessage: String? = null                   // 非空 → 显示检查失败
)

/**
 * 更新检查 ViewModel（Activity 作用域，MainActivity 持有并全局显示结果弹窗）。
 * 同时服务「启动自动检查」与「设置页手动检查」。
 */
class UpdateViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "HuginPrep"

        /** 无 Hilt 场景的工厂 */
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]!!
                UpdateViewModel(app)
            }
        }
    }

    private val _uiState = MutableStateFlow(UpdateUiState())
    val uiState: StateFlow<UpdateUiState> = _uiState.asStateFlow()

    /** 触发检查（手动或启动自动） */
    fun checkForUpdate(context: Context) {
        if (_uiState.value.isChecking) return
        viewModelScope.launch {
            _uiState.update {
                it.copy(isChecking = true, updateInfo = null, noUpdateMessage = null, errorMessage = null)
            }
            val result = runCatching { UpdateChecker.checkForUpdate(context) }
            result
                .onSuccess { info ->
                    when {
                        info == null -> _uiState.update {
                            it.copy(isChecking = false, errorMessage = getString(R.string.update_check_failed, "无法获取版本信息"))
                        }
                        info.isNewer -> _uiState.update { it.copy(isChecking = false, updateInfo = info) }
                        else -> _uiState.update {
                            it.copy(isChecking = false, noUpdateMessage = getString(R.string.update_no_update))
                        }
                    }
                }
                .onFailure { e ->
                    Log.e(TAG, "检查更新失败", e)
                    _uiState.update {
                        it.copy(isChecking = false, errorMessage = getString(R.string.update_check_failed, e.message ?: ""))
                    }
                }
        }
    }

    /** 开始下载新版本 APK（启动前台下载服务） */
    fun startDownload(context: Context) {
        val info = _uiState.value.updateInfo ?: return
        val url = info.downloadUrl
        if (url == null) {
            _uiState.update { it.copy(errorMessage = getString(R.string.update_no_apk)) }
            return
        }
        DownloadService.start(context, url, "HuginPrep-${info.latestVersion}.apk")
        dismissUpdate()
        Log.d(TAG, "开始下载: $url")
    }

    fun dismissUpdate() = _uiState.update { it.copy(updateInfo = null) }
    fun dismissNoUpdate() = _uiState.update { it.copy(noUpdateMessage = null) }
    fun dismissError() = _uiState.update { it.copy(errorMessage = null) }

    private fun getString(resId: Int): String = getApplication<Application>().getString(resId)

    private fun getString(resId: Int, vararg args: Any): String =
        getApplication<Application>().getString(resId, *args)
}
