package com.huginprep.app.camera

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.util.Log
import android.util.SizeF
import com.huginprep.app.data.CameraPreset

/**
 * 从 Camera2 读取到的相机硬件参数。
 *
 * @property focalLengths 可用焦距列表（mm），来自 LENS_INFO_AVAILABLE_FOCAL_LENGTHS
 * @property sensorSize 传感器物理尺寸（mm），来自 SENSOR_INFO_PHYSICAL_SIZE
 * @property sensorOrientation 传感器方向角（度），来自 SENSOR_ORIENTATION
 * @property isLogicalMultiCamera 是否为逻辑多摄（如主摄+长焦组合），通过能力数组判断
 */
data class CameraHardwareParams(
    val focalLengths: List<Float>,
    val sensorSize: SizeF,
    val sensorOrientation: Int,
    val isLogicalMultiCamera: Boolean
) {
    /**
     * 以传感器宽度 + 第一个（最短）焦距计算的水平视场角（度）。
     * 焦距列表为空时返回 0f。
     */
    val hfov: Float
        get() = if (focalLengths.isEmpty()) 0f
        else CameraPreset.calculateHFOV(sensorSize.width, focalLengths.first())
}

/**
 * 相机硬件参数读取器（单例）。
 *
 * 通过 Camera2 的 [CameraManager] 读取指定 cameraId 的硬件特性：
 * 焦距、传感器物理尺寸、传感器方向、逻辑多摄标记。
 */
object CameraParamsReader {

    private const val TAG = "HuginPrep"

    /**
     * 逻辑多摄能力值（CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_LOGICAL_MULTI_CAMERA = 12）。
     * 该常量与 LOGICAL_MULTI_CAMERA 均为 API 28 引入，minSdk 26 下不能直接引用，
     * 因此使用能力值字面量 + 能力数组（API 21+）判断。
     */
    private const val CAPABILITY_LOGICAL_MULTI_CAMERA = 12

    /**
     * 读取指定相机的硬件参数。
     *
     * @param context 任意 Context
     * @param cameraId Camera2 相机 id（可通过 CameraManager.cameraIdList 或
     *                 CameraX 的 Camera2CameraInfo.from(cameraInfo).cameraId 获取）
     * @throws IllegalArgumentException cameraId 不存在
     * @throws IllegalStateException 传感器物理尺寸不可用（sensorSize 为 null）——
     *                               此时无法自动计算 HFOV，需提示用户手动输入参数
     */
    fun readCameraParams(context: Context, cameraId: String): CameraHardwareParams {
        val manager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val characteristics = manager.getCameraCharacteristics(cameraId)

        // 可用焦距列表（多摄/变焦镜头的所有档位）
        val focalLengths = characteristics
            .get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)
            ?.toList()
            ?: emptyList()

        // 传感器物理尺寸：HFOV 计算的必要输入，缺失时无法自动计算，直接抛异常
        val sensorSize = characteristics.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE)
            ?: throw IllegalStateException(
                "相机 $cameraId 的传感器物理尺寸不可用，请手动输入焦距与传感器尺寸"
            )

        val sensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 0

        val isLogicalMultiCamera = characteristics
            .get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)
            ?.contains(CAPABILITY_LOGICAL_MULTI_CAMERA) == true

        Log.d(
            TAG,
            "相机参数: id=$cameraId 焦距=$focalLengths 传感器=$sensorSize " +
                "orientation=$sensorOrientation logicalMulti=$isLogicalMultiCamera"
        )
        return CameraHardwareParams(
            focalLengths = focalLengths,
            sensorSize = sensorSize,
            sensorOrientation = sensorOrientation,
            isLogicalMultiCamera = isLogicalMultiCamera
        )
    }
}
