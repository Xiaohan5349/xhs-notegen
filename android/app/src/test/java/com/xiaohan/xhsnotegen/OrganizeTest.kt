package com.xiaohan.xhsnotegen

import com.xiaohan.xhsnotegen.data.json.JsonCodec
import com.xiaohan.xhsnotegen.domain.*
import com.xiaohan.xhsnotegen.ui.drafts.DraftListViewModel.Companion.UNKNOWN_PLACE
import com.xiaohan.xhsnotegen.ui.drafts.DraftListViewModel.Companion.buildFeed
import com.xiaohan.xhsnotegen.ui.drafts.FeedItem
import com.xiaohan.xhsnotegen.ui.drafts.GroupBy
import org.junit.Assert.*
import org.junit.Test

class OrganizeTest {

    private fun note(id: Long, country: String = "", region: String = "", city: String = "", vararg tags: String) =
        NoteDraft(
            id = id,
            foodInfo = FoodInfo(place = Place(country = country, region = region, city = city)),
            tags = tags.mapIndexed { i, t -> NoteTag(i + 1L, t) },
        )

    private fun titles(feed: List<FeedItem>) = feed.map {
        when (it) {
            is FeedItem.Header -> "${"  ".repeat(it.header.level)}[${it.header.title} ${it.header.count}]"
            is FeedItem.Note -> "${it.draft.id}"
        }
    }

    @Test
    fun `place grouping is country then city, biggest first, unknown last`() {
        val notes = listOf(
            note(1, "中国", "江苏省", "南京市"),
            note(2, "中国", "", "上海市"),
            note(3, "中国", "", "上海市"),
            note(4, "日本", "东京都", "东京"),
            note(5),
        )
        assertEquals(
            listOf(
                "[中国 3]", "  [上海市 2]", "2", "3", "  [南京市 · 江苏省 1]", "1",
                "[日本 1]", "  [东京 · 东京都 1]", "4",
                "[$UNKNOWN_PLACE 1]", "5",
            ),
            titles(buildFeed(notes, GroupBy.PLACE, emptySet())),
        )
    }

    @Test
    fun `collapsing a country hides its cities and notes`() {
        val notes = listOf(note(1, "中国", "", "上海市"), note(2, "日本", "", "东京"))
        val feed = buildFeed(notes, GroupBy.PLACE, setOf("country:中国"))
        assertEquals(listOf("[中国 1]", "[日本 1]", "  [东京 1]", "2"), titles(feed))
    }

    @Test
    fun `a note with two tags appears under both, untagged last`() {
        val notes = listOf(note(1, tags = arrayOf("咖啡", "甜品")), note(2, tags = arrayOf("咖啡")), note(3))
        assertEquals(
            listOf("[#咖啡 2]", "1", "2", "[#甜品 1]", "1", "[No tag 1]", "3"),
            titles(buildFeed(notes, GroupBy.TAG, emptySet())),
        )
    }

    @Test
    fun `backups carry places and tags`() {
        val json = """{"drafts":[{"foodInfo":{"dishNames":"面","place":{"country":"中国","city":"南京市","source":"MANUAL"}},
            "tags":[{"id":7,"name":"面食"},{"name":" "},null]}]}"""
        val d = JsonCodec.parseImport(json).drafts!!.single()!!.toDomain()
        assertEquals("南京市", d.foodInfo.place.city)
        assertEquals(PlaceSource.MANUAL, d.foodInfo.place.source)
        assertEquals(listOf("面食"), d.tags.map { it.name })
    }
}
