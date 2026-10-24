package com.xiaohan.xhsnotegen

import com.xiaohan.xhsnotegen.domain.FoodInfo
import com.xiaohan.xhsnotegen.domain.NoteDraft
import com.xiaohan.xhsnotegen.domain.NoteSearch
import com.xiaohan.xhsnotegen.domain.NoteTag
import com.xiaohan.xhsnotegen.domain.NoteVariant
import com.xiaohan.xhsnotegen.domain.Place
import com.xiaohan.xhsnotegen.domain.SearchSuggestion
import com.xiaohan.xhsnotegen.domain.SearchTerm
import org.junit.Assert.*
import org.junit.Test

class NoteSearchTest {

    private fun note(id: Long, city: String, type: String = "food", rating: Int = 0, body: String = "", country: String = "日本") = NoteDraft(
        id = id, type = type, rating = rating,
        variants = listOf(NoteVariant("s", "title $id", body, emptyList())),
        foodInfo = FoodInfo(dishNames = "dish", restaurantName = "店$id", place = Place(country = country, city = city)),
    )

    private val kyotoRamen = note(1, "京都", rating = 5, body = "一碗拉面")
    private val osakaRamen = note(2, "大阪", rating = 3, body = "拉面很咸")
    private val kyotoTea = note(3, "京都", type = "travel", rating = 4, body = "抹茶")
    private val all = listOf(kyotoRamen, osakaRamen, kyotoTea)

    private fun find(vararg t: SearchTerm) = all.filter { NoteSearch.matches(it, t.toList()) }.map { it.id }

    @Test
    fun noTermsMatchesEverything() = assertEquals(listOf(1L, 2L, 3L), find())

    @Test
    fun samePlaceKindIsOr() = assertEquals(listOf(1L, 2L, 3L), find(SearchTerm.InPlace("京都"), SearchTerm.InPlace("大阪")))

    @Test
    fun countryMatchesItsCities() = assertEquals(listOf(1L, 2L, 3L), find(SearchTerm.InPlace("日本")))

    @Test
    fun differentKindsAreAnd() {
        assertEquals(listOf(1L), find(SearchTerm.InPlace("京都"), SearchTerm.Mode("food")))
        assertEquals(listOf(1L, 3L), find(SearchTerm.InPlace("京都"), SearchTerm.MinRating(4)))
    }

    @Test
    fun keywordsEachMustBeFound() {
        assertEquals(listOf(1L, 2L), find(SearchTerm.Keyword("拉面")))
        assertEquals(listOf(2L), find(SearchTerm.Keyword("拉面"), SearchTerm.Keyword("咸")))
        // Case-insensitive, and the restaurant counts as text.
        assertEquals(listOf(3L), find(SearchTerm.Keyword("TITLE 3")))
        assertEquals(listOf(2L), find(SearchTerm.Keyword("店2")))
    }

    @Test
    fun suggestionsOfferPlacesTagsModesRatingAndWords() {
        val tags = listOf(NoteTag(9, "京都抹茶"))
        val s = NoteSearch.suggest("京都", all, tags, mapOf(9L to 1), modeName = { it })
        val place = s.first { it.kind == SearchSuggestion.Kind.PLACE }
        assertEquals(SearchTerm.InPlace("京都", place.label), place.term)
        assertEquals(2, place.count)
        assertEquals(9L, s.first { it.kind == SearchSuggestion.Kind.TAG }.tagId)
        assertEquals(SearchSuggestion.Kind.KEYWORD, s.last().kind)

        val r = NoteSearch.suggest("4星", all, emptyList(), emptyMap(), modeName = { it })
        assertEquals(SearchTerm.MinRating(4), r.first { it.kind == SearchSuggestion.Kind.RATING }.term)
        assertEquals(2, r.first { it.kind == SearchSuggestion.Kind.RATING }.count)

        val m = NoteSearch.suggest("旅", all, emptyList(), emptyMap(), modeName = { if (it == "travel") "旅行" else "美食" })
        assertEquals(SearchTerm.Mode("travel"), m.first { it.kind == SearchSuggestion.Kind.MODE }.term)

        assertTrue(NoteSearch.suggest("  ", all, emptyList(), emptyMap(), modeName = { it }).isEmpty())
    }
}
