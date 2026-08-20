package com.huginprep.app.ui.capture

import android.app.Application
import android.content.Context
import android.hardware.camera2.CaptureRequest
import android.util.Log
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.google.common.util.concurrent.ListenableFuture
import com.huginprep.app.R
import com.huginprep.app.camera.CameraParamsReader
import com.huginprep.app.data.AppDatabase
import com.huginprep.app.data.CameraPreset
import com.huginprep.app.data.PresetRepository
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine

/** 拍摄页 UI 状态 */
data class CaptureUiState(
    val isCameraReady: Boolean = false,
    val currentFocalLength: Float? = null,
    val currentHfov: Float? = null,
    val selectedPresetName: String? = null,
    val selectedPresetId: String? = null,
    val capturedCount: Int = 0,
    val isCapturing: Boolean = false,
    val isAeAfLocked: Boolean = false,
    val showPresetDialog: Boolean = false,
    val presets: List<CameraPreset> = emptyList(),
    val errorMessage: String? = null
)

/**
 * 实时拍摄 ViewModel：基于 CameraX（底层 Camera2）管理预览与拍照。
 *
 * AE/AF 锁定说明（Camera2 实际 API）：
 * - 曝光锁定：CaptureRequest.CONTROL_AE_LOCK = true（Camera2 中不存在
 *   CONTROL_AE_MODE_LOCK 常量，AE 锁定用的是 CONTROL_AE_LOCK 开关）；
 * - 对焦锁定：CameraX 的 CameraControl.startFocusAndMetering() 对画面中心
 *   触发一次 AF+AE 区域锁定，使后续拍摄保持同一对焦与测光基准。
 */
class CameraCaptureViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "HuginPrep"

        /** 无 Hilt 场景的工厂 */
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]!!
                CameraCaptureViewModel(app)
            }
        }
    }

    private val repository = PresetRepository(AppDatabase.getInstance(application))

    // CameraX 组件
    private var cameraProvider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private var preview: Preview? = null
    private var imageCapture: ImageCapture? = null
    private var previewView: PreviewView? = null
    private var lifecycleOwner: LifecycleOwner? = null
    private var lensFacing = CameraSelector.LENS_FACING_BACK

    // 会话状态
    private var currentPreset: CameraPreset? = null
    private var aeAfLocked = false

    private val _uiState = MutableStateFlow(CaptureUiState())
    val uiState: StateFlow<CaptureUiState> = _uiState.asStateFlow()

    init {
        loadPresets()
    }

    // ==================== 生命周期 ====================

    /** 由预览界面在创建 PreviewView 时调用，绑定视图与生命周期 */
    fun attachPreviewView(view: PreviewView, owner: LifecycleOwner) {
        previewView = view
        lifecycleOwner = owner
    }

    /** 加载预设列表（供选择弹窗使用） */
    fun loadPresets() {
        viewModelScope.launch {
            runCatching { repository.getAllPresets() }
                .onSuccess { list -> _uiState.update { it.copy(presets = list) } }
                .onFailure { e -> Log.e(TAG, "加载预设失败", e) }
        }
    }

    /**
     * 启动相机并绑定预览（需已授予 CAMERA 权限）。
     *
     * @param lensFacing 前置/后置，默认后置 CameraSelector.LENS_FACING_BACK
     */
    fun startCamera(context: Context, lensFacing: Int = CameraSelector.LENS_FACING_BACK) {
        this.lensFacing = lensFacing
        val pv = previewView ?: run {
            Log.w(TAG, "PreviewView 尚未附加，忽略 startCamera")
            return
        }
        val owner = lifecycleOwner ?: run {
            Log.w(TAG, "LifecycleOwner 尚未附加，忽略 startCamera")
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(errorMessage = null) }
            try {
                val provider = ProcessCameraProvider.getInstance(context).awaitResult()
                cameraProvider = provider
                val selector = CameraSelector.Builder().requireLensFacing(lensFacing).build()
                provider.unbindAll()

                preview = Preview.Builder().build().also { it.setSurfaceProvider(pv.surfaceProvider) }
                imageCapture = buildImageCapture(locked = false)
                camera = provider.bindToLifecycle(owner, selector, preview, imageCapture)

                // 读取硬件参数并联动预设（预设优先，否则用硬件自动值）
                val cameraId = Camera2CameraInfo.from(checkNotNull(camera).cameraInfo).cameraId
                val hardware = CameraParamsReader.readCameraParams(context, cameraId)
                applyParameters(hardware)

                _uiState.update { it.copy(isCameraReady = true) }
                Log.d(TAG, "相机已启动: cameraId=$cameraId")
            } catch (e: Exception) {
                Log.e(TAG, "相机启动失败", e)
                _uiState.update {
                    it.copy(
                        isCameraReady = false,
                        errorMessage = "相机不可用（模拟器无相机、权限被拒或设备不支持），请检查后重试"
                    )
                }
            }
        }
    }

    /** 关闭并释放相机（离开拍摄页时调用） */
    fun closeCamera() {
        cameraProvider?.unbindAll()
        cameraProvider = null
        camera = null
        preview = null
        imageCapture = null
        _uiState.update { it.copy(isCameraReady = false) }
    }

    // ==================== 预设 ====================

    /**
     * 选择拍摄预设：决定写入 EXIF / .pto 的焦距与 HFOV 元数据
     * （实际镜头焦距由硬件决定，此处不改变取景）。
     */
    fun setPreset(preset: CameraPreset) {
        currentPreset = preset
        _uiState.update {
            it.copy(
                selectedPresetId = preset.id,
                selectedPresetName = preset.name,
                currentFocalLength = preset.focalLength,
                currentHfov = preset.hfov,
                showPresetDialog = false
            )
        }
        Log.d(TAG, "已选择预设: ${preset.name}")
    }

    fun showPresetDialog() = _uiState.update { it.copy(showPresetDialog = true) }

    fun dismissPresetDialog() = _uiState.update { it.copy(showPresetDialog = false) }

    // ==================== 拍摄 ====================

    /**
     * 拍照并保存到 [projectFolder]/images/ 目录，文件名自动编号 %04d.jpg
     * （0001.jpg、0002.jpg ...）。
     */
    fun captureImage(context: Context, projectFolder: File) {
        val capture = imageCapture ?: run {
            _uiState.update { it.copy(errorMessage = "相机未就绪，无法拍摄") }
            return
        }
        val s = _uiState.value
        if (!s.isCameraReady || s.isCapturing) return

        val imagesDir = File(projectFolder, "images").apply { mkdirs() }
        val nextIndex = s.capturedCount + 1
        val file = File(imagesDir, String.format("%04d.jpg", nextIndex))
        val options = ImageCapture.OutputFileOptions.Builder(file).build()

        _uiState.update { it.copy(isCapturing = true) }
        capture.takePicture(
            options,
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    Log.d(TAG, "拍照成功: ${file.absolutePath}")
                    _uiState.update { it.copy(isCapturing = false, capturedCount = nextIndex) }
                }

                override fun onError(exc: ImageCaptureException) {
                    Log.e(TAG, "拍照失败", exc)
                    _uiState.update {
                        it.copy(isCapturing = false, errorMessage = "拍照失败: ${exc.message}")
                    }
                }
            }
        )
    }

    // ==================== AE/AF 锁定 ====================

    /** 锁定曝光（CONTROL_AE_LOCK）与对焦（画面中心区域） */
    fun lockExposureAndFocus() {
        val provider = cameraProvider ?: return
        val owner = lifecycleOwner ?: return
        val pv = previewView ?: return
        viewModelScope.launch {
            try {
                aeAfLocked = true
                rebindWithLock(provider, owner, pv, locked = true)
                _uiState.update { it.copy(isAeAfLocked = true, errorMessage = null) }
                Log.d(TAG, "已锁定 AE/AF")
            } catch (e: Exception) {
                aeAfLocked = false
                Log.e(TAG, "锁定 AE/AF 失败", e)
                _uiState.update { it.copy(errorMessage = "锁定曝光/对焦失败: ${e.message}") }
            }
        }
    }

    /** 解锁曝光与对焦 */
    fun unlockExposureAndFocus() {
        val provider = cameraProvider ?: return
        val owner = lifecycleOwner ?: return
        val pv = previewView ?: return
        viewModelScope.launch {
            try {
                aeAfLocked = false
                camera?.cameraControl?.cancelFocusAndMetering()
                rebindWithLock(provider, owner, pv, locked = false)
                _uiState.update { it.copy(isAeAfLocked = false, errorMessage = null) }
                Log.d(TAG, "已解锁 AE/AF")
            } catch (e: Exception) {
                Log.e(TAG, "解锁 AE/AF 失败", e)
                _uiState.update { it.copy(errorMessage = "解锁曝光/对焦失败: ${e.message}") }
            }
        }
    }

    // ==================== 内部 ====================

    /** 创建本次拍摄会话的项目目录：getExternalFilesDir(null)/projects/<pano_时间戳>/images */
    fun createProjectFolder(context: Context): File {
        val root = File(context.getExternalFilesDir(null), "projects")
        val name = "pano_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val folder = File(root, name)
        File(folder, "images").mkdirs()
        return folder
    }

    /** 按锁定状态重建并绑定 Preview + ImageCapture（AE 锁定通过 Camera2Interop 注入） */
    private fun rebindWithLock(
        provider: ProcessCameraProvider,
        owner: LifecycleOwner,
        pv: PreviewView,
        locked: Boolean
    ) {
        provider.unbindAll()
        val selector = CameraSelector.Builder().requireLensFacing(lensFacing).build()

        preview = Preview.Builder().apply {
            if (locked) {
                Camera2Interop.Extender(this)
                    .setCaptureRequestOption(CaptureRequest.CONTROL_AE_LOCK, true)
            }
        }.build().also { it.setSurfaceProvider(pv.surfaceProvider) }

        imageCapture = buildImageCapture(locked)
        camera = provider.bindToLifecycle(owner, selector, preview, imageCapture)

        if (locked) {
            // 对画面中心触发一次 AF+AE 区域锁定，保持后续拍摄对焦与测光一致
            val point = pv.meteringPointFactory.createPoint(0.5f, 0.5f)
            camera?.cameraControl?.startFocusAndMetering(
                FocusMeteringAction.Builder(
                    point,
                    FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE
                )
                    .setAutoCancelDuration(30, TimeUnit.SECONDS)
                    .build()
            )
        }
    }

    /** 构建 ImageCapture（锁定状态下给拍照请求注入 AE 锁定） */
    private fun buildImageCapture(locked: Boolean): ImageCapture {
        val builder = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .setJpegQuality(100)
        if (locked) {
            Camera2Interop.Extender(builder)
                .setCaptureRequestOption(CaptureRequest.CONTROL_AE_LOCK, true)
        }
        return builder.build()
    }

    /** 预设优先，否则用硬件自动读取值填充当前参数 */
    private fun applyParameters(hardware: CameraParamsReader.CameraHardwareParams) {
        val preset = currentPreset
        _uiState.update {
            it.copy(
                currentFocalLength = preset?.focalLength ?: hardware.focalLengths.firstOrNull(),
                currentHfov = preset?.hfov ?: hardware.hfov.takeIf { h -> h > 0f }
            )
        }
    }

    /** ListenableFuture -> suspend（避免额外引入 kotlinx-coroutines-guava 依赖） */
    private suspend fun <T> ListenableFuture<T>.awaitResult(): T =
        suspendCancellableCoroutine { cont ->
            addListener(
                {
                    try {
                        cont.resume(get())
                    } catch (e: Exception) {
                        cont.resumeWithException(e)
                    }
                },
                ContextCompat.getMainExecutor(getApplication())
            )
            cont.invokeOnCancellation { cancel(false) }
        }
}
