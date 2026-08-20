package com.huginprep.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID
import kotlin.math.atan

/**
 * 相机预设：一组「焦距 + 传感器尺寸」，用于计算水平视场角（HFOV），
 * 并作为写入 EXIF 与生成 .pto 项目的参数来源。
 *
 * @property id 主键，默认 UUID
 * @property name 用户自定义名称，如 "小米14 Pro主摄"
 * @property focalLength 焦距（毫米 mm）
 * @property sensorWidth 传感器宽度（毫米 mm）
 * @property sensorHeight 传感器高度（毫米 mm）
 * @property hfov 水平视场角（度），保存时由 [calculateHFOV] 计算好
 * @property isDefault 是否为默认预设
 * @property createdAt 创建时间戳
 * @property updatedAt 最近更新时间戳
 */
@Entity(tableName = "camera_presets")
data class CameraPreset(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,
    val focalLength: Float,
    val sensorWidth: Float,
    val sensorHeight: Float,
    val hfov: Float,
    val isDefault: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    companion object {
        /**
         * 计算水平视场角（HFOV），单位：度。
         *
         * 公式：HFOV = 2 * atan(sensorWidth / (2 * focalLength))
         *
         * @param sensorWidth 传感器宽度（mm）
         * @param focalLength 焦距（mm）
         * @return 角度值；当 [focalLength] 或 [sensorWidth] 非正数时返回 0f，
         *         避免除零 / 负焦距产生非法角度
         */
        fun calculateHFOV(sensorWidth: Float, focalLength: Float): Float {
            if (focalLength <= 0f || sensorWidth <= 0f) return 0f
            return Math.toDegrees(2.0 * atan(sensorWidth / (2.0 * focalLength))).toFloat()
        }
    }
}
