package com.zy.homeworklog.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "subjects", indices = [Index(value = ["name"], unique = true)])
data class Subject(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val colorArgb: Long,
)

@Entity(
    tableName = "records",
    foreignKeys = [ForeignKey(entity = Subject::class, parentColumns = ["id"], childColumns = ["subjectId"])],
    indices = [Index(value = ["subjectId"]), Index(value = ["deadline"]), Index(value = ["recurringRuleId"])],
)
data class HomeworkRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val subjectId: Long,
    /** 作业类型，当前取值为「纸质」或「电子」（见 AddEditScreen 的 TYPE_OPTIONS）。 */
    val typeName: String,
    val content: String,
    val deadline: Long,
    val createdAt: Long,
    val completedAt: Long? = null,
    /** 关联的周期规则；null 表示一次性作业。 */
    val recurringRuleId: Long? = null,
    /**
     * 单条覆盖的提醒时刻（绝对 epoch 毫秒）；null = 跟随全局提前量（截止 − rem_lead_ms）。
     */
    val remindAt: Long? = null,
)

@Entity(
    tableName = "record_images",
    foreignKeys = [ForeignKey(
        entity = HomeworkRecord::class,
        parentColumns = ["id"],
        childColumns = ["recordId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index(value = ["recordId"])],
)
data class RecordImage(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val recordId: Long,
    val path: String,
    val ord: Int = 0,
)

@Entity(tableName = "recurring_rules", indices = [Index(value = ["subjectId"])])
data class RecurringRule(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val subjectId: Long,
    /** 星期组合，逗号分隔的 ISO 编号（1=周一 … 7=周日）。 */
    val weekdays: String,
    val enabled: Boolean = true,
    /** 周实例处理幂等键：最近处理的周标签（见 TimeFmt.weekKey）。 */
    val lastGeneratedWeek: String = "",
)
