package com.zy.homeworklog.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

data class SubjectCount(val subjectId: Long, val cnt: Int)

@Dao
interface SubjectDao {
    @Query("SELECT * FROM subjects ORDER BY id")
    fun all(): Flow<List<Subject>>

    @Query("SELECT * FROM subjects WHERE id = :id")
    suspend fun byId(id: Long): Subject?

    @Query("SELECT * FROM subjects WHERE name = :name LIMIT 1")
    suspend fun byName(name: String): Subject?

    @Insert
    suspend fun insert(s: Subject): Long

    @Update
    suspend fun update(s: Subject)

    @Delete
    suspend fun delete(s: Subject)

    @Query("SELECT COUNT(*) FROM records WHERE subjectId = :subjectId")
    suspend fun recordCount(subjectId: Long): Int
}

@Dao
interface RecordDao {
    @Query("SELECT * FROM records WHERE completedAt IS NULL ORDER BY deadline ASC")
    fun incomplete(): Flow<List<HomeworkRecord>>

    @Query("SELECT subjectId, COUNT(*) AS cnt FROM records WHERE completedAt IS NULL GROUP BY subjectId")
    fun counts(): Flow<List<SubjectCount>>

    @Query("SELECT * FROM records WHERE completedAt IS NOT NULL ORDER BY completedAt DESC")
    fun completed(): Flow<List<HomeworkRecord>>

    @Query("SELECT * FROM records WHERE id = :id")
    suspend fun byId(id: Long): HomeworkRecord?

    @Query("SELECT * FROM records WHERE completedAt IS NULL")
    suspend fun incompleteNow(): List<HomeworkRecord>

    /**
     * 自动清理候选：**未完成**且截止时间早于 `cutoff`（= now − 10 天）的记录。
     * 只取未完成的 —— 已完成的是历史台账（历史页），不参与超期清理。
     */
    @Query("SELECT * FROM records WHERE completedAt IS NULL AND deadline <= :cutoff")
    suspend fun expiredIncomplete(cutoff: Long): List<HomeworkRecord>

    @Query("SELECT * FROM records WHERE recurringRuleId = :ruleId AND completedAt IS NULL")
    suspend fun incompleteForRule(ruleId: Long): List<HomeworkRecord>

    @Insert
    suspend fun insert(r: HomeworkRecord): Long

    @Update
    suspend fun update(r: HomeworkRecord)

    @Query("DELETE FROM records WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("UPDATE records SET completedAt = :ts WHERE id = :id")
    suspend fun complete(id: Long, ts: Long)

    @Query("UPDATE records SET recurringRuleId = NULL WHERE recurringRuleId = :ruleId AND completedAt IS NULL")
    suspend fun detachIncompleteRule(ruleId: Long)
}

@Dao
interface ImageDao {
    @Query("SELECT * FROM record_images ORDER BY recordId, ord, id")
    fun all(): Flow<List<RecordImage>>

    @Query("SELECT COUNT(*) FROM record_images WHERE path = :path")
    suspend fun referenceCount(path: String): Int

    @Query("SELECT * FROM record_images WHERE recordId = :recordId ORDER BY ord, id")
    suspend fun forRecord(recordId: Long): List<RecordImage>

    @Insert
    suspend fun insert(i: RecordImage): Long

    @Query("DELETE FROM record_images WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM record_images WHERE recordId = :recordId")
    suspend fun deleteForRecord(recordId: Long)
}

@Dao
interface RuleDao {
    @Query("SELECT * FROM recurring_rules ORDER BY id")
    fun all(): Flow<List<RecurringRule>>

    @Query("SELECT * FROM recurring_rules WHERE id = :id")
    suspend fun byId(id: Long): RecurringRule?

    @Query("SELECT * FROM recurring_rules WHERE enabled = 1")
    suspend fun enabledNow(): List<RecurringRule>

    @Query("SELECT * FROM recurring_rules")
    suspend fun allNow(): List<RecurringRule>

    @Insert
    suspend fun insert(r: RecurringRule): Long

    @Update
    suspend fun update(r: RecurringRule)

    @Delete
    suspend fun delete(r: RecurringRule)

    @Query("DELETE FROM recurring_rules WHERE subjectId = :subjectId")
    suspend fun deleteForSubject(subjectId: Long)
}
