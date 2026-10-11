package com.zy.homeworklog.data

import android.content.SharedPreferences
import org.junit.Assert.*
import org.junit.Test

class DraftStoreTest {
    private val now = 1_800_000_000_000L
    private val prefs = MemoryPreferences()
    private val store = DraftStore(prefs)
    private val empty = DraftStore.Draft(null, null, "", now + 1000L, true, null)

    @Test fun openingAnEmptyFormDoesNotCreateDraft() {
        store.save(empty, now)
        assertNull(store.load(now))
    }

    @Test fun deadlineOnlyInputSurvivesReopening() {
        val draft = empty.copy(isDefaultDeadline = false)
        store.save(draft, now)
        assertEquals(draft, DraftStore(prefs).load(now + 1L))
    }

    @Test fun reminderOnlyInputSurvivesReopening() {
        val draft = empty.copy(remindAt = now + 3000L)
        store.save(draft, now)
        assertEquals(draft, DraftStore(prefs).load(now + 1L))
    }

    @Test fun allFormFieldsRoundTrip() {
        val draft = DraftStore.Draft(8L, "纸质", "证明题一\n证明题二", now + 5000L, false, 4L, now + 1000L)
        store.save(draft, now)
        assertEquals(draft, DraftStore(prefs).load(now + 1L))
    }

    @Test fun draftExpiresOnlyAfterTwentyFourHours() {
        val draft = empty.copy(content = "待保存")
        store.save(draft, now)
        assertEquals(draft, store.load(now + DraftStore.TTL_MS))
        assertNull(store.load(now + DraftStore.TTL_MS + 1L))
        assertTrue(prefs.all.isEmpty())
    }

    @Test fun successfulSaveClearsItsOwnDraft() {
        val draft = empty.copy(subjectId = 1L, content = "作业")
        store.save(draft, now)
        store.clearIfMatches(draft, now + 1L)
        assertNull(store.load(now + 1L))
    }

    @Test fun lateSaveCannotClearANewerDraft() {
        val first = empty.copy(content = "第一份")
        val second = first.copy(content = "后来填写的内容")
        store.save(first, now)
        DraftStore(prefs).save(second, now + 1L)
        store.clearIfMatches(first, now + 2L)
        assertEquals(second, store.load(now + 2L))
    }

    @Test fun clearRemovesEveryDraftField() {
        store.save(empty.copy(subjectId = 2L, ruleId = 3L, remindAt = now), now)
        store.clear()
        assertTrue(prefs.all.isEmpty())
        assertNull(DraftStore(prefs).load(now))
    }

    @Test fun imageOnlyDraftAndImageOrderSurviveReopening() {
        val paths = listOf("/data/user/0/app/files/images/第二张.png", "/data/user/0/app/files/images/first.jpg")
        val draft = empty.copy(imagePaths = paths)
        store.save(draft, now)
        assertEquals(draft, DraftStore(prefs).load(now + 1))
    }

    @Test fun legacyDraftWithoutImagesLoadsAsEmptyImageList() {
        store.save(empty.copy(content = "旧草稿"), now)
        prefs.edit().remove("imagePaths").apply()
        assertEquals(emptyList<String>(), store.load(now + 1)?.imagePaths)
    }

    @Test fun imageReorderingPreventsLateSaveFromClearingNewDraft() {
        val first = empty.copy(imagePaths = listOf("a.jpg", "b.png"))
        val second = first.copy(imagePaths = first.imagePaths.reversed())
        store.save(second, now)
        store.clearIfMatches(first, now + 1)
        assertEquals(second, store.load(now + 1))
    }

    @Test fun expiringAnImageDraftReportsFilesForReferenceAwareCleanup() {
        var discarded = emptyList<String>()
        val withCleanup = DraftStore(prefs) { discarded = it }
        withCleanup.save(empty.copy(imagePaths = listOf("kept.jpg")), now)
        assertNull(withCleanup.load(now + DraftStore.TTL_MS + 1))
        assertEquals(listOf("kept.jpg"), discarded)
    }

    @Test fun removingFromActiveDraftDoesNotDeleteBeforeEditingEnds() {
        var discarded = emptyList<String>()
        val withCleanup = DraftStore(prefs) { discarded = it }
        withCleanup.save(empty.copy(imagePaths = listOf("a.jpg", "b.jpg")), now)
        withCleanup.save(empty.copy(imagePaths = listOf("b.jpg")), now + 1)
        assertTrue(discarded.isEmpty())
    }
}

/** 仅模拟 SharedPreferences 存储；草稿的判断、序列化、过期和清除使用生产实现。 */
private class MemoryPreferences : SharedPreferences {
    private val values = mutableMapOf<String, Any?>()
    override fun getAll(): MutableMap<String, *> = values.toMutableMap()
    override fun getString(key: String?, defValue: String?): String? = values[key] as? String ?: defValue
    @Suppress("UNCHECKED_CAST")
    override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? =
        (values[key] as? Set<String>)?.toMutableSet() ?: defValues
    override fun getInt(key: String?, defValue: Int): Int = values[key] as? Int ?: defValue
    override fun getLong(key: String?, defValue: Long): Long = values[key] as? Long ?: defValue
    override fun getFloat(key: String?, defValue: Float): Float = values[key] as? Float ?: defValue
    override fun getBoolean(key: String?, defValue: Boolean): Boolean = values[key] as? Boolean ?: defValue
    override fun contains(key: String?): Boolean = values.containsKey(key)
    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit
    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit
    override fun edit(): SharedPreferences.Editor = MemoryEditor()

    private inner class MemoryEditor : SharedPreferences.Editor {
        private val pending = mutableMapOf<String, Any?>()
        private var clearAll = false
        override fun putString(key: String?, value: String?): SharedPreferences.Editor = put(key, value)
        override fun putStringSet(key: String?, values: MutableSet<String>?): SharedPreferences.Editor = put(key, values?.toSet())
        override fun putInt(key: String?, value: Int): SharedPreferences.Editor = put(key, value)
        override fun putLong(key: String?, value: Long): SharedPreferences.Editor = put(key, value)
        override fun putFloat(key: String?, value: Float): SharedPreferences.Editor = put(key, value)
        override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor = put(key, value)
        override fun remove(key: String?): SharedPreferences.Editor = put(key, null)
        override fun clear(): SharedPreferences.Editor { clearAll = true; return this }
        override fun apply() { commit() }
        override fun commit(): Boolean {
            if (clearAll) values.clear()
            pending.forEach { (key, value) -> if (value == null) values.remove(key) else values[key] = value }
            return true
        }
        private fun put(key: String?, value: Any?): SharedPreferences.Editor {
            pending[requireNotNull(key)] = value
            return this
        }
    }
}
