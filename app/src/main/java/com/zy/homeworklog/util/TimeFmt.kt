package com.zy.homeworklog.util

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale

object TimeFmt {

    /** 未完成记录的超期清理阈值；remaining 使用同一上限封顶显示文案。 */
    const val OVERDUE_LIMIT_DAYS = 10
    const val OVERDUE_LIMIT_MS = OVERDUE_LIMIT_DAYS * 86_400_000L

    private val mdhm: DateTimeFormatter = DateTimeFormatter.ofPattern("MM-dd HH:mm")
    private val ymd: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    private val cnDate: DateTimeFormatter = DateTimeFormatter.ofPattern("M 月 d 日", Locale.CHINA)

    fun fmtDeadline(ts: Long): String =
        Instant.ofEpochMilli(ts).atZone(ZoneId.systemDefault()).format(mdhm)

    fun fmtDate(ts: Long): String =
        Instant.ofEpochMilli(ts).atZone(ZoneId.systemDefault()).format(ymd)

    fun fmtCnDate(ts: Long): String =
        Instant.ofEpochMilli(ts).atZone(ZoneId.systemDefault()).format(cnDate)

    /** Material 日期值是 UTC 零点的日期编码，不能作为本地时刻解释。 */
    fun pickerDate(utcMillis: Long): LocalDate =
        Instant.ofEpochMilli(utcMillis).atZone(ZoneOffset.UTC).toLocalDate()

    fun fmtPickerDate(utcMillis: Long): String = pickerDate(utcMillis).format(cnDate)

    fun pickerDateTime(
        utcMillis: Long, hour: Int, minute: Int, zone: ZoneId = ZoneId.systemDefault(),
    ): Long = pickerDate(utcMillis).atTime(LocalTime.of(hour, minute))
        .atZone(zone).toInstant().toEpochMilli()

    /**
     * 未设置截止时间时的默认值：本周日 24:00（即下周一 00:00）。
     */
    fun defaultDeadline(now: Long = System.currentTimeMillis()): Long {
        val d = Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()).toLocalDate()
        val nextMonday = d.with(TemporalAdjusters.next(DayOfWeek.MONDAY))
        return nextMonday.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }

    /** 按周一为起点计算的当前周标签，用于周期实例去重。 */
    fun weekKey(now: Long = System.currentTimeMillis()): String {
        val d = Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()).toLocalDate()
        val week = d.get(java.time.temporal.WeekFields.of(DayOfWeek.MONDAY, 1).weekOfWeekBasedYear())
        val year = d.get(java.time.temporal.WeekFields.of(DayOfWeek.MONDAY, 1).weekBasedYear())
        return String.format(Locale.ROOT, "%04d-W%02d", year, week)
    }

    /**
     * 把「提前量」毫秒格式化成中文短语。
     * 例：1800000 → "30 分钟"；10800000 → "3 小时"；90000000 → "1 天 1 小时"。
     * 超过 1 天时略去分钟，避免文案过长。
     */
    fun fmtLead(ms: Long): String {
        val total = (ms / 60_000L).coerceAtLeast(0)
        val days = total / (60 * 24)
        val hours = total % (60 * 24) / 60
        val mins = total % 60
        val parts = buildList {
            if (days > 0) add("$days 天")
            if (hours > 0) add("$hours 小时")
            if (mins > 0 && days == 0L) add("$mins 分钟")
        }
        return if (parts.isEmpty()) "0 分钟" else parts.joinToString(" ")
    }

    data class Remaining(val text: String, val kind: Int) // kind: 0 normal, 1 urgent(<24h), 2 overdue, 3 later(>=7d)
    fun remaining(now: Long, deadline: Long): Remaining {
        val diff = deadline - now
        val overdue = diff < 0
        val raw = kotlin.math.abs(diff)
        // 清理任务运行前，超期记录可能短暂留在列表；封顶显示可控制倒计时文案长度。
        val a = if (overdue) raw.coerceAtMost(OVERDUE_LIMIT_MS - 3_600_000L) else raw
        val days = a / 86_400_000
        val hours = a % 86_400_000 / 3_600_000
        val mins = a % 3_600_000 / 60_000
        val text = when {
            days >= 1 -> if (hours > 0) "$days 天 $hours 小时" else "$days 天"
            hours >= 1 -> "$hours 小时"
            else -> "$mins 分钟"
        }
        val body = if (overdue) "已逾期 $text" else text
        val kind = when {
            overdue -> 2
            days >= 7 -> 3
            a < 86_400_000 -> 1
            else -> 0
        }
        return Remaining(body, kind)
    }
}
