package com.huginprep.app.ui.import

import android.app.Application
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.huginprep.app.R
import com.huginprep.app.data.AppDatabase
import com.huginprep.app.data.CameraPreset
import com.huginprep.app.data.PresetRepository
import com.huginprep.app.util.ExifWriter
import com.huginprep.app.util.ProjectFolderManager
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 导入页 UI 状态 */
data class ImportUiState(
    val selectedUris: List<Uri> = emptyList(),
    val selectedPreset: CameraPreset? = null,
    val showPresetDialog: Boolean = false,
    val importedCount: Int = 0,
    val isProcessing: Boolean = false,
    val progress: Float = 0f,
    val presets: List<CameraPreset> = emptyList(),
    val importedProjectDir: File? = null,
    val errorMessage: String? = null
)

/**
 * 本地照片导入 ViewModel。
 *
 * 流程：pickPhotos（触发系统 Photo Picker）→ onPhotosPicked（自动弹预设选择）
 * → applyPresetToPhotos（复制 + 顺序编号 + 写 EXIF + 进度）。
 */
class LocalImportViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "HuginPrep"

        /** 无 Hilt 场景的工厂 */
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]!!
                LocalImportViewModel(app)
            }
        }
    }

    private val repository = PresetRepository(AppDatabase.getInstance(application))

    private val _uiState = MutableStateFlow(ImportUiState())
    val uiState: StateFlow<ImportUiState> = _uiState.asStateFlow()

    /** 一次性「打开系统选择器」请求：由界面收集后 launch Photo Picker */
    private val _pickRequests = Channel<Unit>(Channel.BUFFERED)
    val pickRequests = _pickRequests.receiveAsFlow()

    init {
        loadPresets()
    }

    /** 触发系统照片选择器（PickMultipleVisualMedia，可多选） */
    fun pickPhotos() {
        _pickRequests.trySend(Unit)
    }

    /** 接收选中的 URI：更新列表并自动弹出预设选择弹窗 */
    fun onPhotosPicked(uris: List<Uri>) {
        if (uris.isEmpty()) return
        _uiState.update {
            it.copy(
                selectedUris = uris,
                importedCount = 0,
                importedProjectDir = null,
                errorMessage = null,
                showPresetDialog = true
            )
        }
        Log.d(TAG, "已选择 ${uris.size} 张照片")
    }

    fun loadPresets() {
        viewModelScope.launch {
            runCatching { repository.getAllPresets() }
                .onSuccess { list -> _uiState.update { it.copy(presets = list) } }
                .onFailure { e -> Log.e(TAG, "加载预设失败", e) }
        }
    }

    /** 选择预设并关闭弹窗 */
    fun selectPreset(preset: CameraPreset) {
        _uiState.update { it.copy(selectedPreset = preset, showPresetDialog = false) }
    }

    fun showPresetDialog() = _uiState.update { it.copy(showPresetDialog = true) }

    fun dismissPresetDialog() = _uiState.update { it.copy(showPresetDialog = false) }

    /**
     * 应用预设并导入：
     * 1. 创建项目目录 projects/<import_时间戳>/images/；
     * 2. 按 Uri 顺序（即用户选择的顺序）把每张照片复制为 0001.jpg、0002.jpg ...；
     * 3. 每张写入 EXIF 焦距（使用所选预设）；
     * 4. 实时更新进度与成功计数。
     *
     * 全部在 Dispatchers.IO 执行，避免阻塞主线程。
     */
    fun applyPresetToPhotos(context: Context) {
        val s = _uiState.value
        val preset = s.selectedPreset
        if (preset == null) {
            _uiState.update { it.copy(errorMessage = getString(R.string.import_error_no_preset)) }
            return
        }
        if (s.selectedUris.isEmpty() || s.isProcessing) return

        val projectName = "import_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val projectDir = ProjectFolderManager.createProjectFolder(context, projectName)
        val imagesDir = File(projectDir, "images")

        viewModelScope.launch {
            _uiState.update {
                it.copy(isProcessing = true, progress = 0f, importedCount = 0, errorMessage = null)
            }
            val total = s.selectedUris.size
            val successCount = withContext(Dispatchers.IO) {
                var success = 0
                s.selectedUris.forEachIndexed { index, uri ->
                    val target = File(imagesDir, String.format("%04d.jpg", index + 1))
                    val copied = runCatching {
                        context.contentResolver.openInputStream(uri)?.use { input ->
                            target.outputStream().use { out -> input.copyTo(out) }
                        } ?: throw IOException("无法读取照片: $uri")
                    }.isSuccess

                    if (copied) {
                        if (ExifWriter.writeAllParamsToExif(target, preset)) success++
                    } else {
                        Log.e(TAG, "复制失败: $uri")
                    }
                    // StateFlow 线程安全，可在 IO 线程直接更新进度
                    _uiState.update { it.copy(importedCount = success, progress = (index + 1f) / total) }
                }
                success
            }
            _uiState.update {
                it.copy(
                    isProcessing = false,
                    importedProjectDir = projectDir.takeIf { successCount > 0 },
                    errorMessage = if (successCount < total) {
                        getString(R.string.import_error_failed, total - successCount)
                    } else {
                        null
                    }
                )
            }
            Log.d(TAG, "导入完成: 成功 $successCount/$total -> ${projectDir.absolutePath}")
        }
    }

    /** 清空已选照片与导入结果 */
    fun clearSelection() {
        _uiState.update {
            it.copy(
                selectedUris = emptyList(),
                selectedPreset = null,
                importedCount = 0,
                progress = 0f,
                importedProjectDir = null,
                errorMessage = null,
                showPresetDialog = false
            )
        }
    }

    private fun getString(resId: Int): String = getApplication<Application>().getString(resId)
}
