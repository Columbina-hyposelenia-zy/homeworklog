package com.zy.homeworklog.recurring

import com.zy.homeworklog.data.HomeworkRepository
import com.zy.homeworklog.util.TimeFmt
import com.zy.homeworklog.util.Weekdays
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 周期实例生成：每条启用规则每个周标签最多处理一次。
 *
 * 若规则已有未完成实例，只更新 lastGeneratedWeek 并跳过新建；否则创建纸质作业，内容为「周期作业」。
 * 截止时间为所选星期最近一次出现的当天 24:00（见 [Weekdays.nextDeadline]）；
 * 未选星期时使用本周日 24:00（[TimeFmt.defaultDeadline]）。
 * 周标签由 [TimeFmt.weekKey] 计算，lastGeneratedWeek 用于避免同周重复处理。
 */
object RecurringGenerator {
    private val generationLock = Mutex()

    suspend fun generate(repo: HomeworkRepository, now: Long = System.currentTimeMillis()) = generationLock.withLock {
        repo.transaction {
            val week = TimeFmt.weekKey(now)
            repo.allRulesNow().forEach { rule ->
                if (!rule.enabled) return@forEach
                // 忽略对应科目已删除的孤立规则，继续处理其它规则。
                if (repo.subject(rule.subjectId) == null) return@forEach
                if (rule.lastGeneratedWeek == week) return@forEach
                val exists = repo.incompleteForRule(rule.id)
                if (exists.isNotEmpty()) {
                    repo.updateRule(rule.copy(lastGeneratedWeek = week))
                    return@forEach
                }
                repo.addRecord(
                    subjectId = rule.subjectId,
                    typeName = "纸质",
                    content = "周期作业",
                    deadline = Weekdays.nextDeadline(rule.weekdays, now) ?: TimeFmt.defaultDeadline(now),
                    ruleId = rule.id,
                    imagePaths = emptyList(),
                )
                repo.updateRule(rule.copy(lastGeneratedWeek = week))
            }
        }
    }

}
