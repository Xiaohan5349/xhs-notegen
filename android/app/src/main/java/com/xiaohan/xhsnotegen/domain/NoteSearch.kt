package com.xiaohan.xhsnotegen.domain

/** One condition picked in the home search. Tags are kept apart, in [TagFilter]. */
sealed interface SearchTerm {
    /** Words found in the note's text: titles, bodies, dishes, restaurant, your notes, hashtags. */
    data class Keyword(val text: String) : SearchTerm
    /** A country, province/state or city, by its stored (Chinese) name; [label] is what the chip shows. */
    data class InPlace(val name: String, val label: String = name) : SearchTerm
    data class Mode(val key: String) : SearchTerm
    /** At least this many stars. */
    data class MinRating(val stars: Int) : SearchTerm
}

/** A row offered while typing. Exactly one of [term] and [tagId] is set. */
data class SearchSuggestion(
    val kind: Kind,
    val label: String,
    val count: Int,
    val term: SearchTerm? = null,
    val tagId: Long? = null,
) {
    enum class Kind { PLACE, TAG, MODE, RATING, KEYWORD }
}

/**
 * The home search. Rules, like the tag filter: conditions of the same kind are OR
 * (京都 or 大阪), different kinds are AND (京都 and Food and 4★+). Keywords are the
 * exception — each one must be found, so typing more words narrows the list.
 */
object NoteSearch {

    fun matches(note: NoteDraft, terms: Collection<SearchTerm>): Boolean {
        if (terms.isEmpty()) return true
        val keywords = terms.filterIsInstance<SearchTerm.Keyword>()
        val places = terms.filterIsInstance<SearchTerm.InPlace>()
        val modes = terms.filterIsInstance<SearchTerm.Mode>()
        val ratings = terms.filterIsInstance<SearchTerm.MinRating>()
        val text by lazy { searchableText(note) }
        return keywords.all { text.contains(it.text.trim().lowercase()) } &&
            (places.isEmpty() || places.any { inPlace(note.foodInfo.place, it.name) }) &&
            (modes.isEmpty() || modes.any { it.key == note.type }) &&
            (ratings.isEmpty() || note.rating >= ratings.minOf { it.stars })
    }

    private fun inPlace(p: Place, name: String) = name.isNotBlank() && (p.country == name || p.region == name || p.city == name)

    internal fun searchableText(n: NoteDraft): String = buildList {
        n.variants.forEach { add(it.title); add(it.body); addAll(it.hashtags) }
        add(n.title); add(n.body); addAll(n.hashtags)
        with(n.foodInfo) { addAll(listOf(dishNames, restaurantName, location, tasteNotes, vibeNotes, personalNotes, place.address)) }
        n.tags.forEach { add(it.name) }
    }.filter { it.isNotBlank() }.joinToString("\n").lowercase()

    /**
     * What [query] could mean, most useful first: places, tags, modes, a rating
     * ("4", "4星", "4★"), and always the plain words as a keyword.
     * [localize] gives a place's names in the app language; [modeName] a mode's name.
     */
    fun suggest(
        query: String,
        notes: List<NoteDraft>,
        tags: List<NoteTag>,
        tagCounts: Map<Long, Int>,
        modeName: (String) -> String,
        localize: (Place) -> Place = { it },
        limit: Int = 6,
    ): List<SearchSuggestion> {
        val q = query.trim()
        if (q.isEmpty()) return emptyList()
        fun hit(vararg names: String) = names.any { it.contains(q, ignoreCase = true) }
        val out = mutableListOf<SearchSuggestion>()

        // Places: each level separately, named in the app language, matched in either language.
        val places = linkedMapOf<String, Pair<String, Int>>()
        notes.forEach { n ->
            val raw = n.foodInfo.place
            val loc = localize(raw)
            listOf(raw.city to loc.city, raw.region to loc.region, raw.country to loc.country)
                .filter { (r, l) -> r.isNotBlank() && hit(r, l) }
                .distinctBy { it.first }
                .forEach { (r, l) ->
                    val label = if (l == loc.country || loc.country.isBlank()) l else "$l · ${loc.country}"
                    places[r] = (places[r]?.first ?: label) to ((places[r]?.second ?: 0) + 1)
                }
        }
        places.entries.sortedByDescending { it.value.second }.take(limit).forEach { (raw, v) ->
            out += SearchSuggestion(SearchSuggestion.Kind.PLACE, v.first, v.second, term = SearchTerm.InPlace(raw, v.first))
        }

        tags.filter { hit(it.name) }.sortedByDescending { tagCounts[it.id] ?: 0 }.take(limit).forEach {
            out += SearchSuggestion(SearchSuggestion.Kind.TAG, it.name, tagCounts[it.id] ?: 0, tagId = it.id)
        }

        notes.groupBy { it.type }.filterKeys { hit(it, modeName(it)) }.forEach { (key, inMode) ->
            out += SearchSuggestion(SearchSuggestion.Kind.MODE, modeName(key), inMode.size, term = SearchTerm.Mode(key))
        }

        RATING.matchEntire(q)?.groupValues?.get(1)?.toInt()?.let { stars ->
            out += SearchSuggestion(SearchSuggestion.Kind.RATING, "★".repeat(stars) + if (stars < 5) "+" else "",
                notes.count { it.rating >= stars }, term = SearchTerm.MinRating(stars))
        }

        val kw = SearchTerm.Keyword(q)
        out += SearchSuggestion(SearchSuggestion.Kind.KEYWORD, q, notes.count { matches(it, listOf(kw)) }, term = kw)
        return out
    }

    private val RATING = Regex("""([1-5])\s*(星|★|stars?|\+)?""", RegexOption.IGNORE_CASE)
}
