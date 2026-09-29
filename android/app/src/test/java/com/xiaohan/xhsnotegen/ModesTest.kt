package com.xiaohan.xhsnotegen

import com.xiaohan.xhsnotegen.data.json.JsonCodec
import com.xiaohan.xhsnotegen.data.json.ModeDto
import com.xiaohan.xhsnotegen.domain.*
import com.xiaohan.xhsnotegen.ui.drafts.DraftListViewModel.Companion.buildFeed
import com.xiaohan.xhsnotegen.ui.drafts.FeedItem
import com.xiaohan.xhsnotegen.ui.drafts.GroupBy
import com.xiaohan.xhsnotegen.ui.generate.FoodPrompts
import org.junit.Assert.*
import org.junit.Test

class ModesTest {

    @Test
    fun `every built-in mode is complete and distinct`() {
        val all = BuiltInModes.all
        assertEquals(all.size, all.map { it.key }.distinct().size)
        assertEquals(all.size, all.map { it.rootTag }.distinct().size)
        all.forEach { m ->
            FieldSlot.entries.forEach { assertTrue("${m.key} ${it}", m.field(it).promptKey.isNotBlank()) }
            NoteStyle.entries.forEach { assertTrue(m.style(it).isNotBlank()) }
            assertTrue(m.instructions.length > 300)
            // Output rules are appended by the app, never part of a mode's instructions.
            assertFalse(m.instructions.contains("\"variants\""))
        }
        assertTrue(BuiltInModes.builtIn("parenting")!!.instructions.contains("隐私"))
    }

    @Test
    fun `travel prompt uses travel labels, food keeps its labels`() {
        val info = FoodInfo(dishNames = "爬了伏见稻荷", restaurantName = "京都", tasteNotes = "人太多")
        val travel = FoodPrompts.buildUserPrompt(info, listOf(NoteStyle.CLEAN), mode = BuiltInModes.travel)
        assertTrue(travel.contains("## 这次去的"))
        assertTrue(travel.contains("做了什么：爬了伏见稻荷"))
        assertTrue(travel.contains("感受（我的原话）：人太多"))
        val food = FoodPrompts.buildUserPrompt(info, listOf(NoteStyle.CLEAN))
        assertTrue(food.contains("菜：爬了伏见稻荷"))
        assertTrue(food.contains("味道（我的原话）"))
    }

    @Test
    fun `stored mode edits merge over defaults`() {
        val dto = JsonCodec.gson.fromJson("""{"key":"travel","rootTag":"出游","fields":{"PLACE":{"label":"City"}}}""", ModeDto::class.java)
        val m = dto.toDomain(BuiltInModes.travel)
        assertEquals("出游", m.rootTag)
        assertEquals("City", m.field(FieldSlot.PLACE).label)
        assertEquals("地方", m.field(FieldSlot.PLACE).promptKey)      // untouched parts keep defaults
        assertEquals(BuiltInModes.travel.instructions, m.instructions)
    }

    @Test
    fun `tags group root then sub-tags, with General for root-only notes`() {
        val travel = NoteTag(1, "旅行")
        val kyoto = NoteTag(2, "京都", parentId = 1)
        val beach = NoteTag(3, "海边", parentId = 1)
        val coffee = NoteTag(4, "咖啡")
        val notes = listOf(
            NoteDraft(id = 10, tags = listOf(travel, kyoto)),
            NoteDraft(id = 11, tags = listOf(travel, beach)),
            NoteDraft(id = 12, tags = listOf(travel)),
            NoteDraft(id = 13, tags = listOf(coffee)),
            NoteDraft(id = 14),
        )
        val feed = buildFeed(notes, GroupBy.TAG, emptySet(), listOf(travel, kyoto, beach, coffee)).map {
            when (it) { is FeedItem.Header -> "${"  ".repeat(it.header.level)}[${it.header.title} ${it.header.count}]"; is FeedItem.Note -> "${it.draft.id}" }
        }
        assertEquals(
            listOf("[旅行 3]", "  [京都 1]", "10", "  [海边 1]", "11", "  [General 1]", "12", "[咖啡 1]", "13", "[No tag 1]", "14"),
            feed,
        )
    }

    @Test
    fun `old exports with FOOD type import as the food mode`() {
        val d = JsonCodec.parseImport("""{"drafts":[{"type":"FOOD"}],"tagParents":{"京都":"旅行"}}""")
        assertEquals(BuiltInModes.FOOD, d.drafts!!.single()!!.toDomain().type)
        assertEquals("旅行", d.tagParents!!["京都"])
    }
}
