package com.zy.homeworklog.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class ExpansionPolicyTest {
    private val images = setOf(8L, 9L)

    @Test fun fourthTextClosesOldestText() {
        assertEquals(listOf(2L, 3L, 4L), ExpansionPolicy.toggle(listOf(1, 2, 3), 4, images))
    }

    @Test fun imageClosesEveryExpandedText() {
        assertEquals(listOf(8L), ExpansionPolicy.toggle(listOf(1, 2, 3), 8, images))
    }

    @Test fun anotherImageReplacesExpandedImage() {
        assertEquals(listOf(9L), ExpansionPolicy.toggle(listOf(8), 9, images))
    }

    @Test fun textClosesImageBeforeExpanding() {
        assertEquals(listOf(1L), ExpansionPolicy.toggle(listOf(8), 1, images))
    }

    @Test fun expandedImageCanBeCollapsed() {
        assertEquals(emptyList<Long>(), ExpansionPolicy.toggle(listOf(8), 8, images))
    }

    @Test fun collapsingTextKeepsOtherExpandedTextInOrder() {
        assertEquals(listOf(1L, 3L), ExpansionPolicy.toggle(listOf(1, 2, 3), 2, images))
    }
}
