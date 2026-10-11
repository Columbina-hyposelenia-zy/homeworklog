package com.zy.homeworklog.reminder

import com.zy.homeworklog.data.HomeworkRecord
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AlarmSchedulerTest {
    private val record = HomeworkRecord(subjectId = 1L, typeName = "纸质", content = "原内容",
        deadline = 1_800_000_000_000L, createdAt = 1_700_000_000_000L)
    private val lead = 3_600_000L

    @Test fun editingOnlyTextDoesNotResetAnAlreadySentReminder() {
        assertFalse(AlarmScheduler.reminderChanged(record, record.copy(content = "修改内容", typeName = "电子"), lead))
    }

    @Test fun changingAFollowGlobalDeadlineResetsTheReminder() {
        assertTrue(AlarmScheduler.reminderChanged(record, record.copy(deadline = record.deadline + 60_000L), lead))
    }

    @Test fun fixedReminderDoesNotMoveWhenDeadlineChanges() {
        val fixed = record.copy(remindAt = record.deadline - lead)
        assertFalse(AlarmScheduler.reminderChanged(fixed, fixed.copy(deadline = fixed.deadline + 60_000L), lead))
    }

    @Test fun changingAFixedReminderResetsIt() {
        val fixed = record.copy(remindAt = record.deadline - lead)
        assertTrue(AlarmScheduler.reminderChanged(fixed, fixed.copy(remindAt = fixed.remindAt!! + 60_000L), lead))
    }

    @Test fun switchingModeWithTheSameEffectiveTimeDoesNotSendAgain() {
        val fixed = record.copy(remindAt = record.deadline - lead)
        assertFalse(AlarmScheduler.reminderChanged(record, fixed, lead))
    }
}
