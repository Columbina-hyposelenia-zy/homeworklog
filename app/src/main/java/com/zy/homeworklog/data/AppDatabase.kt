package com.zy.homeworklog.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [Subject::class, HomeworkRecord::class, RecordImage::class, RecurringRule::class],
    // v2：HomeworkRecord 增加 remindAt（单条覆盖提醒时刻）
    version = 2,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun subjectDao(): SubjectDao
    abstract fun recordDao(): RecordDao
    abstract fun imageDao(): ImageDao
    abstract fun ruleDao(): RuleDao

    companion object {
        /**
         * v1 → v2：records 增加可空的 remindAt 列。
         * 只写显式迁移，不用 fallbackToDestructiveMigration —— 否则漏写迁移时会静默清空用户数据。
         */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE records ADD COLUMN remindAt INTEGER")
            }
        }

        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "homeworklog.db")
                .addMigrations(MIGRATION_1_2)
                .build()
    }
}
