package com.zy.homeworklog.util

import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WeekdaysTest {
    private val zone = ZoneId.of("Asia/Shanghai")

    /** 某天某个时刻（本地时区）。 */
    private fun at(year: Int, month: Int, day: Int, hour: Int = 12): Long =
        LocalDate.of(year, month, day).atTime(hour, 0).atZone(zone).toInstant().toEpochMilli()

    /** 某天的 24:00，即次日 00:00。 */
    private fun endOfDay(year: Int, month: Int, day: Int): Long =
        LocalDate.of(year, month, day).plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()

    // 2026-10-07 是周三；本周内依次为 周三 7 日、周四 8 日、周五 9 日、周六 10 日、周日 11 日、下周一 12 日。

    @Test
    fun todayThatMatchesWeekdayEndsTodayAtMidnight() {
        assertEquals(endOfDay(2026, 10, 7), Weekdays.nextDeadline("3", at(2026, 10, 7), zone))
    }

    @Test
    fun todayStillCountsLateInTheEvening() {
        assertEquals(endOfDay(2026, 10, 7), Weekdays.nextDeadline("3", at(2026, 10, 7, 23), zone))
    }

    @Test
    fun laterWeekdayInTheSameWeekIsUsed() {
        assertEquals(endOfDay(2026, 10, 9), Weekdays.nextDeadline("5", at(2026, 10, 7), zone))
    }

    @Test
    fun earlierWeekdayRollsToNextWeek() {
        assertEquals(endOfDay(2026, 10, 12), Weekdays.nextDeadline("1", at(2026, 10, 7), zone))
    }

    @Test
    fun earliestSelectedWeekdayWins() {
        assertEquals(endOfDay(2026, 10, 9), Weekdays.nextDeadline("5,7", at(2026, 10, 7), zone))
    }

    @Test
    fun sundayIsSevenNotZero() {
        assertEquals(endOfDay(2026, 10, 11), Weekdays.nextDeadline("7", at(2026, 10, 7), zone))
    }

    @Test
    fun everyWeekdayIsReachable() {
        val now = at(2026, 10, 7)
        // 周四 8 日、周五 9 日、周六 10 日、周日 11 日、下周一 12 日、下周二 13 日；
        // 周三 7 日由 todayThatMatchesWeekdayEndsTodayAtMidnight 覆盖（今天即所选日）。
        val expectedDays = mapOf("4" to 8, "5" to 9, "6" to 10, "7" to 11, "1" to 12, "2" to 13)
        expectedDays.forEach { (iso, day) ->
            assertEquals(endOfDay(2026, 10, day), Weekdays.nextDeadline(iso, now, zone))
        }
    }

    @Test
    fun emptyOrInvalidInputFallsBackToNull() {
        val now = at(2026, 10, 7)
        assertNull(Weekdays.nextDeadline("", now, zone))
        assertNull(Weekdays.nextDeadline("0,8,abc", now, zone))
    }

    @Test
    fun textIgnoresUnknownTokens() {
        assertEquals("周一、周五", Weekdays.text("1, 5"))
        assertEquals("不重复", Weekdays.text(""))
    }
}
