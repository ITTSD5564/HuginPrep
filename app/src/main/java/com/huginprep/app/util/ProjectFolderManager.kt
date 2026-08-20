package com.huginprep.app.util

import android.content.Context
import android.util.Log
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * 项目文件夹管理（单例）。
 *
 * 目录结构：getExternalFilesDir(null)/projects/<projectName>/images/
 * （应用专属外部目录，卸载即清除，无需任何存储权限）
 */
object ProjectFolderManager {

    private const val TAG = "HuginPrep"
    private const val PROJECTS_DIR = "projects"
    private const val IMAGES_DIR = "images"

    /**
     * 创建项目文件夹：
     * 1. 确保 projects/ 存在；
     * 2. 创建 <projectName>/；
     * 3. 创建 <projectName>/images/。
     *
     * @return 项目根目录 File
     */
    fun createProjectFolder(context: Context, projectName: String): File {
        val projectDir = getProjectPath(context, projectName)
        File(projectDir, IMAGES_DIR).mkdirs()
        Log.d(TAG, "已创建项目目录: ${projectDir.absolutePath}")
        return projectDir
    }

    /** 项目根目录（不创建） */
    fun getProjectPath(context: Context, projectName: String): File =
        File(File(context.getExternalFilesDir(null), PROJECTS_DIR), projectName)

    /** 项目 images/ 子目录 */
    fun getImagesDir(context: Context, projectName: String): File =
        File(getProjectPath(context, projectName), IMAGES_DIR)

    /** 第 N 张照片的路径（images/NNNN.jpg，N 从 1 开始） */
    fun getImagePath(context: Context, projectName: String, sequenceNumber: Int): File =
        File(getImagesDir(context, projectName), String.format("%04d.jpg", sequenceNumber))

    /**
     * 将 images/ 目录下的 jpg 按「修改时间升序」重命名为 0001.jpg、0002.jpg ...
     *
     * 采用两段式重命名（先全部改到临时名，再改回正式编号名），
     * 避免「0002.jpg -> 0001.jpg」这类目标名冲突导致覆盖。
     *
     * @return 重命名后的文件列表（按顺序）
     */
    fun renameImagesSequentially(projectDir: File): List<File> {
        val imagesDir = File(projectDir, IMAGES_DIR)
        if (!imagesDir.exists() || !imagesDir.isDirectory) return emptyList()

        val files = imagesDir.listFiles { f -> f.isFile && f.extension.equals("jpg", ignoreCase = true) }
            ?.sortedBy { it.lastModified() }
            ?: return emptyList()

        // 第一段：全部移动到临时名
        val tempFiles = mutableListOf<File>()
        files.forEachIndexed { index, file ->
            val tmp = File(imagesDir, ".tmp_${System.nanoTime()}_$index")
            runCatching {
                Files.move(file.toPath(), tmp.toPath(), StandardCopyOption.REPLACE_EXISTING)
                tempFiles.add(tmp)
            }.onFailure { e -> Log.e(TAG, "重命名失败: $file", e) }
        }

        // 第二段：临时名 -> 正式编号
        val renamed = mutableListOf<File>()
        tempFiles.forEachIndexed { index, tmp ->
            val target = File(imagesDir, String.format("%04d.jpg", index + 1))
            runCatching {
                Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
                renamed.add(target)
            }.onFailure { e -> Log.e(TAG, "重命名失败: $tmp", e) }
        }
        Log.d(TAG, "顺序重命名完成: ${renamed.size} 张")
        return renamed
    }

    /**
     * 将整个项目文件夹打包为 ZIP。
     *
     * ZIP 内的路径为相对路径（如 images/0001.jpg、<项目名>.pto），
     * 与 .pto 中的图片相对引用保持一致，解压后可直接用 Hugin 打开。
     *
     * @param projectDir 项目根目录
     * @param outputZipFile 输出 ZIP 文件（建议放在项目目录之外，如 cache 目录，
     *                      避免把 ZIP 自己打进 ZIP）
     */
    fun packProjectAsZip(projectDir: File, outputZipFile: File): Result<File> = runCatching {
        ZipOutputStream(BufferedOutputStream(FileOutputStream(outputZipFile))).use { zip ->
            projectDir.walkTopDown()
                .filter { it != projectDir } // 排除根目录自身
                .forEach { file ->
                    val relative = file.relativeTo(projectDir).path.replace('\\', '/')
                    if (file.isDirectory) {
                        zip.putNextEntry(ZipEntry("$relative/"))
                        zip.closeEntry()
                    } else {
                        zip.putNextEntry(ZipEntry(relative))
                        file.inputStream().use { it.copyTo(zip) }
                        zip.closeEntry()
                    }
                }
        }
        Log.d(TAG, "ZIP 打包完成: ${outputZipFile.absolutePath} (${outputZipFile.length()} bytes)")
        outputZipFile
    }
}
