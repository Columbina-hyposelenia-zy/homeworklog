package com.zy.homeworklog.ui

/** 图片记录独占展开；纯文字保留三条FIFO。 */
object ExpansionPolicy {
    fun toggle(current: List<Long>, id: Long, imageIds: Set<Long>): List<Long> = when {
        id in current -> current - id
        id in imageIds -> listOf(id)
        else -> current.filterNot { it in imageIds }.takeLast(2) + id
    }
}
