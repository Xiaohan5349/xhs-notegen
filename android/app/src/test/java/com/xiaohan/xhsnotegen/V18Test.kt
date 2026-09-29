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
import java.util.Calendar

/** Multi-level tags, state-level places, English prompts, per-mode settings. */
class V18Test {

    private fun titles(feed: List<FeedItem>) = feed.map {
        when (it) {
            is FeedItem.Header -> "${"  ".repeat(it.header.level)}[${it.header.title} ${it.header.count}]"
            is FeedItem.Note -> "${it.draft.id}"
        }
    }

    @Test
    fun `tags group at any depth, like country and city`() {
        val travel = NoteTag(1, "旅行")
        val japan = NoteTag(2, "日本", parentId = 1)
        val kyoto = NoteTag(3, "京都", parentId = 2)
        val tokyo = NoteTag(4, "东京", parentId = 2)
        val all = listOf(travel, japan, kyoto, tokyo)
        val notes = listOf(
            NoteDraft(id = 10, tags = listOf(travel, kyoto)),
            NoteDraft(id = 11, tags = listOf(kyoto)),          // only the deepest tag: still under 旅行 › 日本
            NoteDraft(id = 12, tags = listOf(travel, tokyo)),
            NoteDraft(id = 13, tags = listOf(travel, japan)),  // 日本 but no city
            NoteDraft(id = 14, tags = listOf(travel)),
        )
        assertEquals(
            listOf(
                "[旅行 5]",
                "  [日本 4]",
                "    [京都 2]", "10", "11",
                "    [东京 1]", "12",
                "    [General 1]", "13",
                "  [General 1]", "14",
            ),
            titles(buildFeed(notes, GroupBy.TAG, emptySet(), all)),
        )
        // Collapsing 日本 hides everything below it.
        val collapsed = titles(buildFeed(notes, GroupBy.TAG, setOf("tag:1/2"), all))
        assertEquals(listOf("[旅行 5]", "  [日本 4]", "  [General 1]", "14"), collapsed)
    }

    @Test
    fun `tag tree helpers survive loops`() {
        val a = NoteTag(1, "a", parentId = 2)
        val b = NoteTag(2, "b", parentId = 1)
        val byId = listOf(a, b).associateBy { it.id }
        assertEquals(listOf("b", "a"), TagTree.path(a, byId).map { it.name })
        assertEquals(2, TagTree.ordered(listOf(a, b)).size)
        assertEquals(listOf("生活", "咖啡"), TagTree.parsePath("#生活 / 咖啡"))
        assertEquals(setOf(3L, 4L), TagTree.descendants(1, listOf(NoteTag(1, "x"), NoteTag(3, "y", 1), NoteTag(4, "z", 3))))
    }

    @Test
    fun `US notes group by state, Chinese ones by city`() {
        fun note(id: Long, country: String, region: String, city: String) =
            NoteDraft(id = id, foodInfo = FoodInfo(place = Place(country = country, region = region, city = city)))
        val notes = listOf(
            note(1, "美国", "加利福尼亚州", "旧金山"),
            note(2, "美国", "加利福尼亚州", "洛杉矶"),
            note(3, "美国", "纽约州", "纽约"),
            note(4, "中国", "江苏省", "南京市"),
        )
        assertEquals(
            listOf(
                "[美国 3]", "  [加利福尼亚州 2]", "1", "2", "  [纽约州 1]", "3",
                "[中国 1]", "  [南京市 · 江苏省 1]", "4",
            ),
            titles(buildFeed(notes, GroupBy.PLACE, emptySet())),
        )
    }

    @Test
    fun `rating groups carry their level instead of star characters`() {
        val feed = buildFeed(listOf(NoteDraft(id = 1, rating = 4), NoteDraft(id = 2)), GroupBy.RATING, emptySet())
        val headers = feed.filterIsInstance<FeedItem.Header>().map { it.header }
        assertEquals(listOf(4, 0), headers.map { it.rating })
        assertTrue(headers.none { it.title.contains('★') })
    }

    @Test
    fun `English note language uses the English prompt, rules and date`() {
        val mode = BuiltInModes.food.copy(language = PromptLanguage.EN)
        assertEquals(EnglishPrompts.food.instructions, mode.instructions)
        val sys = FoodPrompts.systemPrompt(mode.instructions, mode.language)
        assertTrue(sys.contains("## Output") && sys.contains("\"variants\""))
        assertFalse(sys.contains("## 输出"))

        val info = FoodInfo(dishNames = "ramen", restaurantName = "Ippudo", tasteNotes = "broth a bit salty", mealDate = "2026-03-08 12:30")
        val user = FoodPrompts.buildUserPrompt(info, listOf(NoteStyle.CLEAN), mode = mode)
        assertTrue(user, user.contains("${mode.field(FieldSlot.SUBJECT).promptKey}: ramen"))
        assertTrue(user.contains("(my words): broth a bit salty"))
        assertTrue(user.contains("\"Sun, Mar 8 · lunch\""))
        assertFalse(user.contains("我的原话"))

        val in2026 = Calendar.getInstance().apply { set(2026, 5, 1) }
        assertEquals("Sun, Nov 2, 2025", FoodPrompts.describeMealDate("2025-11-02", in2026, PromptLanguage.EN)!!.headerLine)
    }

    @Test
    fun `every built-in mode has a complete English prompt`() {
        BuiltInModes.all.forEach { m ->
            val en = m.copy(language = PromptLanguage.EN)
            assertTrue(m.key, en.instructions.length > 300)
            assertFalse(en.instructions.contains("\"variants\""))
            FieldSlot.entries.forEach { assertTrue("${m.key} $it", en.field(it).promptKey.isNotBlank()) }
            NoteStyle.entries.forEach { assertTrue(en.style(it).isNotBlank()) }
            assertNotEquals(m.instructions, en.instructions)
        }
    }

    @Test
    fun `mode edits store only differences, and default names follow the app language`() {
        val enBase = BuiltInModes.food(zh = false)
        val zhBase = BuiltInModes.food(zh = true)
        val edited = enBase.copy(language = PromptLanguage.EN, maxPhotos = 6)
            .let { it.withPrompt(it.prompt.copy(instructions = "Write one line.")) }
        val dto = JsonCodec.gson.fromJson(JsonCodec.toJson(ModeDto.from(edited, enBase, zhBase)), ModeDto::class.java)
        assertNull(dto.name)             // untouched name isn't stored…
        assertNull(dto.instructions)     // …nor the untouched Chinese prompt
        // Loaded while the app is in Chinese: Chinese name, the English edit kept.
        val loaded = dto.toDomain(zhBase, enBase)
        assertEquals("美食", loaded.name)
        assertEquals(zhBase.field(FieldSlot.SUBJECT).label, loaded.field(FieldSlot.SUBJECT).label)
        assertEquals(PromptLanguage.EN, loaded.language)
        assertEquals(6, loaded.maxPhotos)
        assertEquals("Write one line.", loaded.instructions)
        assertEquals(zhBase.prompts[PromptLanguage.ZH], loaded.prompts[PromptLanguage.ZH])
    }

    @Test
    fun `v1_7 stored modes still load, with their English defaults localized`() {
        // v1.7 stored the whole mode, English name and labels included.
        val old = """{"key":"food","name":"Food","rootTag":"美食","promptHeading":"这次吃的",
            "fields":{"SUBJECT":{"label":"What did you eat","hint":"红烧肉","promptKey":"菜"}},"instructions":"只写一句话。"}"""
        val dto = JsonCodec.gson.fromJson(old, ModeDto::class.java)
        val m = dto.toDomain(BuiltInModes.food(zh = true), BuiltInModes.food(zh = false))
        assertEquals("美食", m.name)
        assertEquals("吃了什么", m.field(FieldSlot.SUBJECT).label)
        assertEquals("只写一句话。", m.instructions)
        assertEquals(PromptLanguage.ZH, m.language)
        assertEquals(WritingMode.DEFAULT_MAX_PHOTOS, m.maxPhotos)
        // A limit outside the allowed range is clamped.
        val big = JsonCodec.gson.fromJson("""{"key":"food","maxPhotos":99}""", ModeDto::class.java)
        assertEquals(WritingMode.MAX_PHOTOS_LIMIT, big.toDomain(BuiltInModes.food).maxPhotos)
    }
}
