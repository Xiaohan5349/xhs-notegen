package com.xiaohan.xhsnotegen

import com.xiaohan.xhsnotegen.domain.NoteTag
import com.xiaohan.xhsnotegen.domain.TagFilter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TagFilterTest {

    private val japan = NoteTag(1, "日本")
    private val kyoto = NoteTag(2, "京都", parentId = 1)
    private val osaka = NoteTag(3, "大阪", parentId = 1)
    private val ramen = NoteTag(4, "拉面")
    private val cafe = NoteTag(5, "咖啡")
    private val all = listOf(japan, kyoto, osaka, ramen, cafe)

    private fun m(note: List<NoteTag>, vararg picked: NoteTag) = TagFilter.matches(note, picked.map { it.id }.toSet(), all)

    @Test
    fun `nothing picked shows everything`() {
        assertTrue(m(emptyList()))
        assertTrue(m(listOf(ramen)))
    }

    @Test
    fun `same group is OR`() {
        assertTrue(m(listOf(kyoto), kyoto, osaka))
        assertTrue(m(listOf(osaka), kyoto, osaka))
        assertFalse(m(listOf(ramen), kyoto, osaka))
    }

    @Test
    fun `different groups are AND`() {
        assertTrue(m(listOf(kyoto, ramen), kyoto, osaka, ramen))
        assertFalse(m(listOf(kyoto), kyoto, osaka, ramen))
        assertFalse(m(listOf(ramen, cafe), kyoto, ramen))
    }

    @Test
    fun `a parent matches notes tagged below it`() {
        assertTrue(m(listOf(kyoto), japan))
        assertFalse(m(listOf(japan), kyoto)) // a note only tagged 日本 isn't known to be in 京都
    }

    @Test
    fun `picking a child drills down and brings its parent`() {
        assertEquals(setOf(1L, 2L), TagFilter.toggle(emptySet(), 2L, all)) // 京都 → {日本, 京都}
        assertEquals(setOf(2L), TagFilter.active(setOf(1L, 2L), all)) // 日本 steps aside for 京都
        assertTrue(m(listOf(kyoto), japan, kyoto))
        assertFalse(m(listOf(osaka), japan, kyoto))
    }

    @Test
    fun `un-picking the child goes back up to the parent`() {
        assertEquals(setOf(1L), TagFilter.toggle(setOf(1L, 2L), 2L, all))
        assertTrue(m(listOf(osaka), japan))
    }

    @Test
    fun `tapping a picked parent clears the picks below it`() {
        assertEquals(setOf(1L, 4L), TagFilter.toggle(setOf(1L, 2L, 3L, 4L), 1L, all))
        assertEquals(emptySet<Long>(), TagFilter.toggle(setOf(1L), 1L, all))
    }

    @Test
    fun `siblings stack as OR`() {
        assertEquals(setOf(1L, 2L, 3L), TagFilter.toggle(setOf(1L, 2L), 3L, all))
        assertTrue(m(listOf(osaka), japan, kyoto, osaka))
    }

    @Test
    fun `tags without a parent toggle on and off`() {
        assertEquals(setOf(4L), TagFilter.toggle(emptySet(), 4L, all))
        assertEquals(emptySet<Long>(), TagFilter.toggle(setOf(4L), 4L, all))
    }
}
