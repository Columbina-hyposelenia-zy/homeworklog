package com.zy.homeworklog.util

import java.time.Instant
import java.time.ZoneId

/** 星期工具：规则 weekdays 字段为逗号分隔 ISO 编号（1=周一 … 7=周日）。 */
object Weekdays {
    val NAMES = linkedMapOf("1" to "一", "2" to "二", "3" to "三", "4" to "四", "5" to "五", "6" to "六", "7" to "日")
    val ORDER: List<String> = NAMES.keys.toList()

    fun text(weekdays: String): String =
        weekdays.split(",").mapNotNull { NAMES[it.trim()] }
            .joinToString("、") { "周$it" }
            .ifEmpty { "不重复" }

    /** 解析成 ISO 编号集合；空白与越界项忽略。 */
    fun parse(weekdays: String): Set<Int> =
        weekdays.split(",").mapNotNull { it.trim().toIntOrNull() }.filter { it in 1..7 }.toSet()

    /**
     * 下一次「所选星期」的 24:00（即次日 00:00）；今天就是所选星期时返回今天 24:00。
     * weekdays 为空或全部非法时返回 null，调用方自行回退默认截止时间。
     */
    fun nextDeadline(
        weekdays: String,
        now: Long = System.currentTimeMillis(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): Long? {
        val days = parse(weekdays)
        if (days.isEmpty()) return null
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        for (offset in 0..7) {
            val date = today.plusDays(offset.toLong())
            if (date.dayOfWeek.value !in days) continue
            val end = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
            if (end > now) return end
        }
        return null
    }
}
