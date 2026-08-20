package com.huginprep.app.data

import android.util.Log
import androidx.room.withTransaction

/**
 * 预设数据仓库：封装数据库访问，向 ViewModel 提供业务方法。
 *
 * 线程模型：Room 的挂起 DAO 与 [withTransaction] 会自动将数据库操作
 * 调度到 Room 的后台执行器，因此无需（也不应）再包一层 Dispatchers.IO。
 *
 * @param db 应用数据库实例
 */
class PresetRepository(private val db: AppDatabase) {

    private val dao: PresetDao
        get() = db.presetDao()

    companion object {
        private const val TAG = "HuginPrep"
    }

    /** 全部预设（默认优先、名称升序） */
    suspend fun getAllPresets(): List<CameraPreset> = dao.getAll()

    /** 按 id 查询单个预设 */
    suspend fun getPresetById(id: String): CameraPreset? = dao.getById(id)

    /** 当前默认预设；未设置时返回 null */
    suspend fun getDefaultPreset(): CameraPreset? = dao.getDefault()

    /** 新增预设 */
    suspend fun insertPreset(preset: CameraPreset): Result<Unit> = runCatching {
        dao.insert(preset)
        Log.d(TAG, "已插入预设: ${preset.name}")
    }

    /** 更新预设（自动刷新 updatedAt） */
    suspend fun updatePreset(preset: CameraPreset): Result<Unit> = runCatching {
        dao.update(preset.copy(updatedAt = System.currentTimeMillis()))
        Log.d(TAG, "已更新预设: ${preset.name}")
    }

    /** 删除预设 */
    suspend fun deletePreset(preset: CameraPreset): Result<Unit> = runCatching {
        dao.delete(preset)
        Log.d(TAG, "已删除预设: ${preset.name}")
    }

    /**
     * 将指定 id 的预设设为默认，分两步且放在同一个事务里保证原子性：
     * 1. 清除所有预设的默认标记；
     * 2. 将目标预设标记为默认。
     *
     * @return 成功返回 [Result.success]；目标不存在（[NoSuchElementException]）
     *         或数据库异常返回 [Result.failure]
     */
    suspend fun setDefaultPreset(id: String): Result<Unit> = runCatching {
        db.withTransaction {
            dao.clearAllDefaults()
            val target = dao.getById(id)
                ?: throw NoSuchElementException("预设不存在: id=$id")
            dao.update(target.copy(isDefault = true, updatedAt = System.currentTimeMillis()))
        }
        Log.d(TAG, "已设为默认预设: $id")
    }
}
