package com.zy.homeworklog.util

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Test

class TimeFmtTest {
    private val pickerDate = LocalDate.of(2026, 10, 1)
    private val pickerDateUtcMillis = pickerDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

    @Test
    fun pickerDateTimeKeepsSelectedDayInShanghai() {
        assertPickerDateAndTime("Asia/Shanghai")
    }

    @Test
    fun pickerDateTimeKeepsSelectedDayInLosAngeles() {
        assertPickerDateAndTime("America/Los_Angeles")
    }

    @Test
    fun pickerDateTimeKeepsSelectedDayAtExtremeOffsets() {
        assertPickerDateAndTime("Pacific/Honolulu")
        assertPickerDateAndTime("Pacific/Kiritimati")
    }

    @Test
    fun pickerDateTimeUsesJavaTimeRuleForNewYorkDstGap() {
        val date = LocalDate.of(2026, 3, 8)
        val zone = ZoneId.of("America/New_York")
        val utcDateMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

        val saved = TimeFmt.pickerDateTime(utcDateMillis, 2, 30, zone)
        val actual = Instant.ofEpochMilli(saved).atZone(zone).toLocalDateTime()

        assertEquals(LocalDateTime.of(date, LocalTime.of(3, 30)), actual)
    }

    @Test
    fun fmtPickerDateIgnoresSystemDefaultTimeZone() {
        withDefaultTimeZone("America/Los_Angeles") {
            val negativeOffset = TimeFmt.fmtPickerDate(pickerDateUtcMillis)
            withDefaultTimeZone("Pacific/Kiritimati") {
                val positiveOffset = TimeFmt.fmtPickerDate(pickerDateUtcMillis)
                assertEquals("10 月 1 日", negativeOffset)
                assertEquals(negativeOffset, positiveOffset)
            }
        }
    }

    @Test
    fun defaultDeadlineIsNextMondayAtMidnight() {
        withDefaultTimeZone("UTC") {
            val now = Instant.parse("2026-10-01T12:00:00Z").toEpochMilli()
            val expected = Instant.parse("2026-10-05T00:00:00Z").toEpochMilli()

            assertEquals(expected, TimeFmt.defaultDeadline(now))
        }
    }

    @Test
    fun overdueTextIsCappedAtNineDaysAndTwentyThreeHours() {
        val now = Instant.parse("2026-10-01T00:00:00Z").toEpochMilli()
        val deadline = now - 11L * 86_400_000L

        assertEquals("已逾期 9 天 23 小时", TimeFmt.remaining(now, deadline).text)
    }

    private fun assertPickerDateAndTime(zoneId: String) {
        val zone = ZoneId.of(zoneId)
        val saved = TimeFmt.pickerDateTime(pickerDateUtcMillis, 21, 35, zone)
        val actual = Instant.ofEpochMilli(saved).atZone(zone)

        assertEquals(pickerDate, actual.toLocalDate())
        assertEquals(LocalTime.of(21, 35), actual.toLocalTime())
    }

    private inline fun withDefaultTimeZone(zoneId: String, block: () -> Unit) {
        val previous = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone(zoneId))
            block()
        } finally {
            TimeZone.setDefault(previous)
        }
    }
}
