package com.huginprep.app.ui.preview

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
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
import com.huginprep.app.util.PtoGenerator
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 项目预览页 UI 状态 */
data class ProjectUiState(
    val projectName: String = "",
    val imageFiles: List<File> = emptyList(),
    val preset: CameraPreset? = null,
    val totalPhotos: Int = 0,
    val isGenerating: Boolean = false,
    val ptoFile: File? = null,
    val isZipping: Boolean = false,
    val zipFile: File? = null,
    val shareIntent: Intent? = null,
    val errorMessage: String? = null
)

/**
 * 项目预览 / 导出 ViewModel：
 * 扫描项目 → 解析预设 → 生成 .pto + 补齐 EXIF → ZIP 打包 → 系统分享。
 */
class ProjectPreviewViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "HuginPrep"

        /** 无 Hilt 场景的工厂 */
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]!!
                ProjectPreviewViewModel(app)
            }
        }
    }

    private val repository = PresetRepository(AppDatabase.getInstance(application))

    private val _uiState = MutableStateFlow(ProjectUiState())
    val uiState: StateFlow<ProjectUiState> = _uiState.asStateFlow()

    private var projectDir: File? = null

    /**
     * 加载项目：扫描 images/ 按文件名排序（0001.jpg → 000N.jpg）。
     *
     * 预设解析优先级：
     * 1. 第一张照片 EXIF 中的焦距（拍摄/导入时已写入）；
     * 2. 数据库默认预设（提供传感器尺寸，用于重算 HFOV）；
     * 3. 都没有时，用 35mm 全幅假设（36×24mm）并记录日志。
     */
    fun loadProject(projectDir: File) {
        this.projectDir = projectDir
        viewModelScope.launch {
            _uiState.update { it.copy(errorMessage = null) }
            val images = withContext(Dispatchers.IO) {
                File(projectDir, "images")
                    .listFiles { f -> f.isFile && f.extension.equals("jpg", ignoreCase = true) }
                    ?.sortedBy { it.name }
                    ?: emptyList()
            }
            val preset = resolvePreset(images.firstOrNull())
            _uiState.update {
                it.copy(
                    projectName = projectDir.name,
                    imageFiles = images,
                    totalPhotos = images.size,
                    preset = preset
                )
            }
            Log.d(TAG, "项目已加载: ${projectDir.name} (${images.size} 张)")
        }
    }

    /**
     * 生成 .pto 并补齐所有图片的 EXIF（幂等，可重复点击）：
     * 1. 每张图片写入焦距等参数（拍摄模式拍出的照片此前没有 EXIF）；
     * 2. 调用 [PtoGenerator] 生成 <项目名>.pto（yaw 全部为 0）。
     */
    fun generatePtoAndExif(projectDir: File, preset: CameraPreset) {
        val s = _uiState.value
        if (s.isGenerating) return
        viewModelScope.launch {
            _uiState.update { it.copy(isGenerating = true, errorMessage = null) }
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    s.imageFiles.forEach { ExifWriter.writeAllParamsToExif(it, preset) }
                    PtoGenerator.generatePtoFile(projectDir, preset, s.imageFiles)
                }
            }
            result
                .onSuccess { pto ->
                    _uiState.update { it.copy(isGenerating = false, ptoFile = pto) }
                    Log.d(TAG, "生成完成: ${pto.absolutePath}")
                }
                .onFailure { e ->
                    Log.e(TAG, "生成 .pto 失败", e)
                    _uiState.update { it.copy(isGenerating = false, errorMessage = "生成失败: ${e.message}") }
                }
        }
    }

    /**
     * 打包 ZIP 并在成功后自动弹出系统分享面板。
     * ZIP 输出到 cache 目录（避免把自己打进 ZIP）。
     */
    fun exportAndShare(projectDir: File, context: Context) {
        val s = _uiState.value
        if (s.isZipping) return
        viewModelScope.launch {
            _uiState.update { it.copy(isZipping = true, errorMessage = null) }
            val outputZip = File(context.cacheDir, "${projectDir.name}.zip")
            val result = withContext(Dispatchers.IO) {
                ProjectFolderManager.packProjectAsZip(projectDir, outputZip)
            }
            result
                .onSuccess { zip ->
                    _uiState.update { it.copy(isZipping = false, zipFile = zip) }
                    openShareSheet(context, zip)
                }
                .onFailure { e ->
                    Log.e(TAG, "ZIP 导出失败", e)
                    _uiState.update { it.copy(isZipping = false, errorMessage = "导出失败: ${e.message}") }
                }
        }
    }

    /** 重新分享已导出的 ZIP；未导出时先导出再分享 */
    fun shareZip(projectDir: File, context: Context) {
        val zip = _uiState.value.zipFile
        if (zip == null || !zip.exists()) {
            exportAndShare(projectDir, context)
        } else {
            openShareSheet(context, zip)
        }
    }

    fun clearShareIntent() {
        _uiState.update { it.copy(shareIntent = null) }
    }

    // ==================== 内部 ====================

    /** 解析拍摄预设：EXIF 焦距优先，数据库默认预设提供传感器尺寸 */
    private suspend fun resolvePreset(firstImage: File?): CameraPreset? {
        val exifFocal = withContext(Dispatchers.IO) {
            firstImage?.let { file ->
                runCatching {
                    ExifInterface(file.absolutePath)
                        .getAttributeDouble(ExifInterface.TAG_FOCAL_LENGTH, 0.0)
                        .takeIf { it > 0.0 }
                }.getOrNull()
            }
        }

        val base = repository.getDefaultPreset() ?: repository.getAllPresets().firstOrNull()

        val focal = exifFocal?.toFloat() ?: base?.focalLength ?: run {
            Log.w(TAG, "未找到 EXIF 焦距且无数据库预设")
            return null
        }
        val sensorWidth = base?.sensorWidth ?: 36f // 35mm 全幅假设
        val sensorHeight = base?.sensorHeight ?: 24f

        return CameraPreset(
            name = base?.name ?: "EXIF 焦距（35mm 假设）",
            focalLength = focal,
            sensorWidth = sensorWidth,
            sensorHeight = sensorHeight,
            hfov = CameraPreset.calculateHFOV(sensorWidth, focal)
        )
    }

    /** 通过 FileProvider 构建分享 Intent（content:// URI，跨应用可读） */
    private fun openShareSheet(context: Context, zip: File) {
        val uri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            zip
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/zip"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        _uiState.update {
            it.copy(
                shareIntent = Intent.createChooser(
                    intent,
                    getApplication<Application>().getString(R.string.project_share_chooser)
                )
            )
        }
    }
}
