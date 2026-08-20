package com.huginprep.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * 应用数据库（当前仅包含相机预设表）。
 *
 * 版本 1：初版，无迁移（后续新增表/字段时需提供 Migration）。
 */
@Database(
    entities = [CameraPreset::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun presetDao(): PresetDao

    companion object {
        private const val DATABASE_NAME = "huginprep.db"

        @Volatile
        private var INSTANCE: AppDatabase? = null

        /**
         * 线程安全的单例（双重检查锁 + synchronized）。
         *
         * @param context 任意 Context，内部会使用 applicationContext 防止泄漏
         */
        fun getInstance(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    DATABASE_NAME
                ).build().also { INSTANCE = it }
            }
    }
}
