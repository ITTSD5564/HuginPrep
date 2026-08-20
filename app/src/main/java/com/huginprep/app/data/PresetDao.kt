package com.huginprep.app.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update

/**
 * 相机预设表的 DAO。
 *
 * 所有方法均为挂起函数，Room 会自动把数据库操作调度到后台执行器，
 * 调用方无需（也不应）再手动包一层 Dispatchers.IO。
 */
@Dao
interface PresetDao {

    /** 插入预设，返回新行的 rowId */
    @Insert
    suspend fun insert(preset: CameraPreset): Long

    /** 按主键更新预设 */
    @Update
    suspend fun update(preset: CameraPreset)

    /** 删除预设 */
    @Delete
    suspend fun delete(preset: CameraPreset)

    /** 所有预设：默认优先，其次按名称升序 */
    @Query("SELECT * FROM camera_presets ORDER BY isDefault DESC, name ASC")
    suspend fun getAll(): List<CameraPreset>

    /** 当前默认预设；未设置时返回 null */
    @Query("SELECT * FROM camera_presets WHERE isDefault = 1 LIMIT 1")
    suspend fun getDefault(): CameraPreset?

    /** 清除所有预设的默认标记（isDefault 置 0） */
    @Query("UPDATE camera_presets SET isDefault = 0")
    suspend fun clearAllDefaults()

    /** 按 id 查询单个预设 */
    @Query("SELECT * FROM camera_presets WHERE id = :id")
    suspend fun getById(id: String): CameraPreset?
}
