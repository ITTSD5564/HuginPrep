package com.huginprep.app.util

import android.graphics.BitmapFactory
import android.util.Log
import com.huginprep.app.data.CameraPreset
import java.io.File

/**
 * Hugin .pto 项目文件生成器（单例）。
 *
 * 关键约定：**所有图片的偏航角 y 统一为 0**，绝不按 360 度均分——
 * 用户拍摄时未必转满 360°（可能只转了 180°/270°），强行均分会导致
 * 图片全部错位；正确的做法是把角度计算交给 Hugin 的 cpfind：
 * 它自动分析相邻图片的重叠区域，反推出每张图真实的 y/p/r。
 *
 * 生成的格式（Hugin 标准）：
 * ```
 * # Hugin project file
 * p f2 w8000 h4000 v360 n"TIFF" m0
 * i w5184 h3456 f24 v72.5 r0 p0 y0 t0 j0 S0 n"images/0001.jpg"
 * ```
 */
object PtoGenerator {

    private const val TAG = "HuginPrep"

    // 输出全景图参数（目前固定，后续可做成用户可配置）
    private const val PANO_WIDTH = 8000
    private const val PANO_HEIGHT = 4000
    private const val PANO_HFOV = 360f
    private const val OUTPUT_FORMAT = "TIFF"

    // 图片尺寸读取失败时的兜底值
    private const val DEFAULT_WIDTH = 4000
    private const val DEFAULT_HEIGHT = 3000

    /**
     * 生成 Hugin 项目文件 projectDir/<项目名>.pto。
     *
     * @param projectDir 项目根目录（.pto 与 images/ 都在其下）
     * @param preset 相机预设：焦距 f 与水平视场角 v 的来源
     * @param imageFiles images/ 下的图片文件（内部按文件名排序，保证 0001 → 000N 顺序）
     * @return 生成的 .pto 文件
     */
    fun generatePtoFile(projectDir: File, preset: CameraPreset, imageFiles: List<File>): File {
        val ptoFile = File(projectDir, "${projectDir.name}.pto")
        val sorted = imageFiles.sortedBy { it.name }

        ptoFile.bufferedWriter(Charsets.UTF_8).use { writer ->
            writer.write("# Hugin project file\n")
            writer.write(
                "p f2 w$PANO_WIDTH h$PANO_HEIGHT v$PANO_HFOV n\"$OUTPUT_FORMAT\" m0\n"
            )
            sorted.forEach { file ->
                val (w, h) = getImageDimensions(file)
                // 关键：y（偏航角）固定为 0，由 Hugin cpfind 自动计算真实角度；
                // a/b（图内畸变参数）省略时 Hugin 按 0 处理，因此格式保持精简
                writer.write(
                    "i w$w h$h f${preset.focalLength} v${preset.hfov} " +
                        "r0 p0 y0 t0 j0 S0 n\"images/${file.name}\"\n"
                )
            }
        }
        Log.d(TAG, ".pto 已生成: ${ptoFile.absolutePath} (${sorted.size} 张图片)")
        return ptoFile
    }

    /**
     * 只解码图片边界（inJustDecodeBounds）读取宽高，避免解码整图导致 OOM。
     *
     * @return (宽, 高)；读取失败时返回 (4000, 3000) 并记录警告
     */
    fun getImageDimensions(file: File): Pair<Int, Int> {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        val result = runCatching {
            BitmapFactory.decodeFile(file.absolutePath, options)
            val w = options.outWidth
            val h = options.outHeight
            if (w > 0 && h > 0) w to h else null
        }.getOrNull()

        return result ?: run {
            Log.w(TAG, "无法读取图片尺寸: ${file.name}，使用默认 $DEFAULT_WIDTH x $DEFAULT_HEIGHT")
            DEFAULT_WIDTH to DEFAULT_HEIGHT
        }
    }
}
