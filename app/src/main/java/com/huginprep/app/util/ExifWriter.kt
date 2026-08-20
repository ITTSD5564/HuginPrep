package com.huginprep.app.util

import android.util.Log
import androidx.exifinterface.media.ExifInterface
import com.huginprep.app.data.CameraPreset
import java.io.File

/**
 * EXIF 写入工具（单例）。
 *
 * 注意：ExifInterface 会改写原文件（重建后写回），
 * 因此请先复制到项目目录、完成重命名后再写 EXIF。
 */
object ExifWriter {

    private const val TAG = "HuginPrep"

    /**
     * 将焦距写入图片 EXIF（标准标签 TAG_FOCAL_LENGTH）。
     *
     * @param imageFile 目标图片（已复制到项目目录）
     * @param focalLength 焦距（mm）
     * @return 是否写入成功
     */
    fun writeFocalLengthToExif(imageFile: File, focalLength: Float): Boolean =
        runCatching {
            val exif = ExifInterface(imageFile.absolutePath)
            exif.setAttribute(ExifInterface.TAG_FOCAL_LENGTH, toRationalString(focalLength))
            exif.saveAttributes()
            Log.d(TAG, "EXIF 已写入焦距 ${focalLength}mm -> ${imageFile.name}")
        }.isSuccess

    /**
     * 将预设中的参数写入 EXIF。
     *
     * 说明：传感器尺寸没有标准 EXIF 标签（EXIF 规范中不存在该字段），
     * 因此目前只写焦距；传感器尺寸会随 .pto 文件一并交给 Hugin。
     *
     * @return 是否全部写入成功
     */
    fun writeAllParamsToExif(imageFile: File, preset: CameraPreset): Boolean {
        val focalOk = writeFocalLengthToExif(imageFile, preset.focalLength)
        Log.d(TAG, "writeAllParamsToExif: 焦距写入=$focalOk")
        return focalOk
    }

    /** Float -> EXIF RATIONAL 格式 "分子/分母"（50.0 -> "50/1"，4.3 -> "43/10"） */
    private fun toRationalString(value: Float): String {
        val scaled = Math.round(value * 100f)
        val divisor = 100
        val g = gcd(scaled, divisor)
        return "${scaled / g}/${divisor / g}"
    }

    private fun gcd(a: Int, b: Int): Int = if (b == 0) a else gcd(b, a % b)
}
