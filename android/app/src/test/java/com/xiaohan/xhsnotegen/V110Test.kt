package com.xiaohan.xhsnotegen

import com.xiaohan.xhsnotegen.data.json.JsonCodec
import com.xiaohan.xhsnotegen.data.json.ModeDto
import com.xiaohan.xhsnotegen.data.local.toDomain
import com.xiaohan.xhsnotegen.data.local.toEntity
import com.xiaohan.xhsnotegen.domain.*
import com.xiaohan.xhsnotegen.ui.drafts.DraftListViewModel.Companion.buildFeed
import com.xiaohan.xhsnotegen.ui.drafts.FeedItem
import com.xiaohan.xhsnotegen.ui.drafts.GroupBy
import com.xiaohan.xhsnotegen.ui.drafts.HomePrefs
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File

/** Mode grouping, group order, place names in the app language, per-note language, 40 photos. */
class V110Test {

    @Before
    fun loadPlaces() {
        File("src/main/assets/places.txt").inputStream().use { PlaceCatalog.load(it) }
    }

    private fun titles(feed: List<FeedItem>) = feed.map {
        when (it) {
            is FeedItem.Header -> "${"  ".repeat(it.header.level)}[${it.header.title} ${it.header.count}]"
            is FeedItem.Note -> "${it.draft.id}"
        }
    }

    private fun note(id: Long, country: String, region: String, city: String, type: String = "food") =
        NoteDraft(id = id, type = type, foodInfo = FoodInfo(place = Place(country = country, region = region, city = city)))

    @Test
    fun `notes group by writing mode, named in the app language, in mode order`() {
        val notes = listOf(NoteDraft(id = 1, type = "travel"), NoteDraft(id = 2, type = "food"), NoteDraft(id = 3, type = "travel"), NoteDraft(id = 4, type = "custom_x"))
        val names = mapOf("food" to "Food", "travel" to "Travel", "custom_x" to "Coffee")
        val feed = buildFeed(notes, GroupBy.MODE, emptySet(), modeName = { names[it] ?: it }, modeOrder = listOf("food", "travel", "custom_x"))
        assertEquals(listOf("[Food 1]", "2", "[Travel 2]", "1", "3", "[Coffee 1]", "4"), titles(feed))
        assertEquals(listOf("[Food 1]", "[Travel 2]", "[Coffee 1]"), titles(buildFeed(notes, GroupBy.MODE, setOf("mode:food", "mode:travel", "mode:custom_x"), modeName = { names[it] ?: it }, modeOrder = listOf("food", "travel", "custom_x"))))
    }

    @Test
    fun `stored Chinese places show in English when the app is English, and group the same`() {
        val notes = listOf(note(1, "中国", "江苏省", "南京市"), note(2, "中国", "", "上海市"), note(3, "美国", "加利福尼亚州", "旧金山"))
        assertEquals(
            listOf("[China 2]", "  [Nanjing · Jiangsu 1]", "1", "  [Shanghai 1]", "2", "[United States 1]", "  [California 1]", "3"),
            titles(buildFeed(notes, GroupBy.PLACE, emptySet(), zh = false)),
        )
        // Same notes, Chinese app: the stored names.
        assertEquals("[中国 2]", titles(buildFeed(notes, GroupBy.PLACE, emptySet(), zh = true)).first())
        // Names the list doesn't know are left as they are.
        assertEquals("东城区", PlaceCatalog.cityName("中国", "东城区", false))
    }

    @Test
    fun `a city name shared by two countries is matched within its country`() {
        assertEquals("San Diego", PlaceCatalog.cityName("美国", "圣地亚哥", false))
        assertEquals("Santiago", PlaceCatalog.cityName("智利", "圣地亚哥", false))
        assertEquals("南京市 · 江苏省 · 中国", Place(country = "中国", region = "江苏省", city = "南京市").localized(true).let { "${it.city} · ${it.region} · ${it.country}" })
    }

    @Test
    fun `group button order keeps every group once and adds missing ones`() {
        assertEquals(HomePrefs.DEFAULT_ORDER, HomePrefs.normalize(emptyList()))
        assertEquals(listOf(GroupBy.RATING, GroupBy.PLACE, GroupBy.TAG, GroupBy.MODE), HomePrefs.normalize(listOf(GroupBy.RATING, GroupBy.NONE, GroupBy.RATING)))
        assertEquals(listOf(GroupBy.MODE, GroupBy.PLACE, GroupBy.TAG, GroupBy.RATING), HomePrefs.normalize(listOf(GroupBy.MODE)))
    }

    @Test
    fun `photo limit is 40, and the old 20 stored by earlier edits means not set`() {
        assertEquals(40, WritingMode.DEFAULT_MAX_PHOTOS)
        assertEquals(40, BuiltInModes.food.maxPhotos)
        val old = JsonCodec.gson.fromJson("""{"key":"food","maxPhotos":20}""", ModeDto::class.java)
        assertEquals(40, old.toDomain(BuiltInModes.food).maxPhotos)
        val chosen = JsonCodec.gson.fromJson("""{"key":"food","maxPhotos":9}""", ModeDto::class.java)
        assertEquals(9, chosen.toDomain(BuiltInModes.food).maxPhotos)
        assertEquals(40, JsonCodec.gson.fromJson("""{"key":"food","maxPhotos":99}""", ModeDto::class.java).toDomain(BuiltInModes.food).maxPhotos)
        // An edited mode doesn't pin the default limit any more.
        assertNull(ModeDto.from(BuiltInModes.food, BuiltInModes.food, BuiltInModes.food(zh = true)).maxPhotos)
        assertEquals(9, ModeDto.from(BuiltInModes.food.copy(maxPhotos = 9), BuiltInModes.food, BuiltInModes.food(zh = true)).maxPhotos)
    }

    @Test
    fun `form hints follow the app language while the AI name follows the note language`() {
        // App is English in tests; the mode writes Chinese notes.
        val zhMode = BuiltInModes.travel(zh = false).copy(language = PromptLanguage.ZH)
        val f = zhMode.field(FieldSlot.SUBJECT)
        assertEquals("做了什么", f.promptKey)                              // named for the AI in Chinese
        assertTrue(f.hint, f.hint.none { it.code in 0x4E00..0x9FFF })     // but the hint you read is English
    }

    @Test
    fun `a note remembers its language`() {
        val d = NoteDraft(id = 5, language = PromptLanguage.EN)
        assertEquals("en", d.toEntity().noteLanguage)
        assertEquals(PromptLanguage.EN, d.toEntity().toDomain().language)
        assertNull(NoteDraft(id = 6).toEntity().noteLanguage)
        assertNull(NoteDraft(id = 6).toEntity().toDomain().language)
        // Backups: the enum name Gson writes, or the key.
        assertEquals(PromptLanguage.EN, JsonCodec.parseImport("""{"drafts":[{"language":"EN"}]}""").drafts!!.single()!!.toDomain().language)
        assertEquals(PromptLanguage.ZH, JsonCodec.parseImport("""{"drafts":[{"language":"zh"}]}""").drafts!!.single()!!.toDomain().language)
        assertNull(JsonCodec.parseImport("""{"drafts":[{}]}""").drafts!!.single()!!.toDomain().language)
    }
}
