package com.huginprep.app.ui.preset

import android.app.Application
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
import java.util.UUID
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 预设编辑表单的字段标识 */
enum class EditField { NAME, FOCAL_LENGTH, SENSOR_WIDTH, SENSOR_HEIGHT }

/** 预设列表页 UI 状态 */
data class PresetUiState(
    val presets: List<CameraPreset> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val showDeleteDialog: Boolean = false,
    val presetToDelete: CameraPreset? = null
)

/** 预设新建/编辑表单 UI 状态 */
data class PresetEditUiState(
    val presetId: String? = null,
    val name: String = "",
    val focalLengthText: String = "",
    val sensorWidthText: String = "",
    val sensorHeightText: String = "",
    val nameError: String? = null,
    val numericError: String? = null,
    val hfovPreview: Float? = null,
    val isSaving: Boolean = false
) {
    /** 是否编辑模式（presetId 非空） */
    val isEditMode: Boolean get() = presetId != null
}

/** 一次性 UI 事件，用于触发页面导航 */
sealed interface PresetEvent {
    /** 保存成功，页面应返回上一级 */
    data object Saved : PresetEvent
}

/**
 * 预设管理 ViewModel：同时服务预设列表页与新建/编辑页。
 *
 * 继承 [AndroidViewModel] 以便通过 Application 访问字符串资源与构建数据库单例
 * （无需引入 Hilt 等 DI 框架）。
 */
class PresetViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "HuginPrep"

        /** 无 Hilt 场景的工厂：Composable 中通过 viewModel(factory = PresetViewModel.Factory) 获取 */
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]!!
                PresetViewModel(app)
            }
        }
    }

    private val repository = PresetRepository(AppDatabase.getInstance(application))

    private val _uiState = MutableStateFlow(PresetUiState())
    val uiState: StateFlow<PresetUiState> = _uiState.asStateFlow()

    private val _editState = MutableStateFlow(PresetEditUiState())
    val editState: StateFlow<PresetEditUiState> = _editState.asStateFlow()

    private val _events = Channel<PresetEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    init {
        loadPresets()
    }

    // ==================== 列表页 ====================

    /** 加载全部预设 */
    fun loadPresets() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            runCatching { repository.getAllPresets() }
                .onSuccess { presets ->
                    _uiState.update { it.copy(presets = presets, isLoading = false) }
                }
                .onFailure { e ->
                    Log.e(TAG, "加载预设失败", e)
                    _uiState.update {
                        it.copy(isLoading = false, errorMessage = getString(R.string.preset_error_load))
                    }
                }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    /** 新增预设 */
    fun addPreset(preset: CameraPreset) {
        viewModelScope.launch {
            repository.insertPreset(preset)
                .onSuccess {
                    Log.d(TAG, "新增预设: ${preset.name}")
                    loadPresets()
                }
                .onFailure { e ->
                    Log.e(TAG, "新增预设失败", e)
                    _uiState.update { it.copy(errorMessage = getString(R.string.preset_error_save)) }
                }
        }
    }

    /** 更新预设 */
    fun updatePreset(preset: CameraPreset) {
        viewModelScope.launch {
            repository.updatePreset(preset)
                .onSuccess {
                    Log.d(TAG, "更新预设: ${preset.name}")
                    loadPresets()
                }
                .onFailure { e ->
                    Log.e(TAG, "更新预设失败", e)
                    _uiState.update { it.copy(errorMessage = getString(R.string.preset_error_save)) }
                }
        }
    }

    /** 删除预设：先弹出确认对话框 */
    fun deletePreset(preset: CameraPreset) {
        _uiState.update { it.copy(showDeleteDialog = true, presetToDelete = preset) }
    }

    fun dismissDeleteDialog() {
        _uiState.update { it.copy(showDeleteDialog = false, presetToDelete = null) }
    }

    /** 确认删除对话框中的删除动作 */
    fun confirmDelete() {
        val preset = _uiState.value.presetToDelete ?: return
        viewModelScope.launch {
            repository.deletePreset(preset)
                .onSuccess {
                    Log.d(TAG, "删除预设: ${preset.name}")
                    loadPresets()
                }
                .onFailure { e ->
                    Log.e(TAG, "删除预设失败", e)
                    _uiState.update { it.copy(errorMessage = getString(R.string.preset_error_delete)) }
                }
            dismissDeleteDialog()
        }
    }

    /** 设为默认（会自动取消其他预设的默认标记） */
    fun setDefault(presetId: String) {
        viewModelScope.launch {
            repository.setDefaultPreset(presetId)
                .onSuccess {
                    Log.d(TAG, "设为默认: $presetId")
                    loadPresets()
                }
                .onFailure { e ->
                    Log.e(TAG, "设为默认失败", e)
                    _uiState.update { it.copy(errorMessage = getString(R.string.preset_error_default)) }
                }
        }
    }

    // ==================== 编辑页 ====================

    /** 编辑模式：按 id 预填表单；新建模式：清空表单 */
    fun loadPresetForEdit(presetId: String?) {
        viewModelScope.launch {
            if (presetId == null) {
                _editState.value = PresetEditUiState()
                return@launch
            }
            val preset = repository.getPresetById(presetId)
            if (preset == null) {
                _editState.update { it.copy(numericError = getString(R.string.preset_error_not_found)) }
            } else {
                _editState.value = PresetEditUiState(
                    presetId = preset.id,
                    name = preset.name,
                    focalLengthText = formatNumber(preset.focalLength),
                    sensorWidthText = formatNumber(preset.sensorWidth),
                    sensorHeightText = formatNumber(preset.sensorHeight),
                    hfovPreview = preset.hfov
                )
            }
        }
    }

    /** 表单字段变更：更新字段、清除错误、实时重算 HFOV 预览 */
    fun onEditFieldChange(field: EditField, value: String) {
        _editState.update { current ->
            val next = when (field) {
                EditField.NAME -> current.copy(name = value)
                EditField.FOCAL_LENGTH -> current.copy(focalLengthText = value)
                EditField.SENSOR_WIDTH -> current.copy(sensorWidthText = value)
                EditField.SENSOR_HEIGHT -> current.copy(sensorHeightText = value)
            }
            next.copy(
                nameError = null,
                numericError = null,
                hfovPreview = next.previewHfov()
            )
        }
    }

    /**
     * 校验并保存：
     * 1. 名称非空、焦距/传感器宽/高均为正数；
     * 2. 通过后按「新增/更新」写入数据库（自动计算 hfov，编辑模式保留默认标记与创建时间）；
     * 3. 成功后发送 [PresetEvent.Saved]，由页面负责返回导航。
     */
    fun validateAndSave() {
        val s = _editState.value
        val name = s.name.trim()
        val focal = s.focalLengthText.toFloatOrNull()
        val width = s.sensorWidthText.toFloatOrNull()
        val height = s.sensorHeightText.toFloatOrNull()

        val nameError = if (name.isEmpty()) getString(R.string.preset_error_name_required) else null
        val numericError = when {
            focal == null || focal <= 0f -> getString(R.string.preset_error_focal)
            width == null || width <= 0f -> getString(R.string.preset_error_width)
            height == null || height <= 0f -> getString(R.string.preset_error_height)
            else -> null
        }

        if (nameError != null || numericError != null) {
            _editState.update { it.copy(nameError = nameError, numericError = numericError) }
            return
        }

        val f = requireNotNull(focal)
        val w = requireNotNull(width)
        val h = requireNotNull(height)

        _editState.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            // 编辑模式保留原默认标记与创建时间
            val base = s.presetId?.let { repository.getPresetById(it) }
            val now = System.currentTimeMillis()
            val preset = CameraPreset(
                id = s.presetId ?: UUID.randomUUID().toString(),
                name = name,
                focalLength = f,
                sensorWidth = w,
                sensorHeight = h,
                hfov = CameraPreset.calculateHFOV(w, f),
                isDefault = base?.isDefault ?: false,
                createdAt = base?.createdAt ?: now,
                updatedAt = now
            )
            val result = if (s.isEditMode) repository.updatePreset(preset) else repository.insertPreset(preset)
            result
                .onSuccess {
                    Log.d(TAG, "预设保存成功: ${preset.name}")
                    _events.send(PresetEvent.Saved)
                }
                .onFailure { e ->
                    Log.e(TAG, "保存预设失败", e)
                    _editState.update {
                        it.copy(isSaving = false, numericError = getString(R.string.preset_error_save))
                    }
                }
        }
    }

    // ==================== 工具 ====================

    private fun getString(resId: Int): String = getApplication<Application>().getString(resId)

    /** 50.0 -> "50"，4.3 -> "4.3"，去掉无意义的小数尾巴 */
    private fun formatNumber(value: Float): String =
        if (value % 1f == 0f) value.toInt().toString() else value.toString()
}

/** 根据当前表单数值实时计算 HFOV 预览（数值缺失或非法时返回 null） */
private fun PresetEditUiState.previewHfov(): Float? {
    val f = focalLengthText.toFloatOrNull() ?: return null
    val w = sensorWidthText.toFloatOrNull() ?: return null
    if (f <= 0f || w <= 0f) return null
    return CameraPreset.calculateHFOV(w, f)
}
