package com.xiaohan.xhsnotegen.ui.drafts

import android.app.Application
import android.net.Uri
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.xiaohan.xhsnotegen.XhsNoteGenApp
import com.xiaohan.xhsnotegen.data.json.ExportData
import com.xiaohan.xhsnotegen.data.json.JsonCodec
import com.xiaohan.xhsnotegen.domain.NoteDraft
import com.xiaohan.xhsnotegen.domain.NoteStatus
import com.xiaohan.xhsnotegen.domain.NoteTag
import com.xiaohan.xhsnotegen.domain.Place
import com.xiaohan.xhsnotegen.domain.PlaceSource
import com.xiaohan.xhsnotegen.util.ImageCleanup
import com.xiaohan.xhsnotegen.util.PlaceResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class DraftFilter(val label: String) {
    ALL("All"), DRAFTS("Drafts"), READY("Ready"), SHARED("Posted");

    fun matches(status: NoteStatus): Boolean = when (this) {
        ALL -> true
        DRAFTS -> status == NoteStatus.DRAFT || status == NoteStatus.GENERATED
        READY -> status == NoteStatus.REVIEWED
        SHARED -> status == NoteStatus.SHARED
    }
}

/** How the home feed is arranged. */
enum class GroupBy(val label: String) { NONE("None"), PLACE("Place"), TAG("Tag"), RATING("Rating") }

/**
 * One header in a grouped feed. [level] 0 = top group (country / tag),
 * 1 = subgroup (city). [key] identifies it for collapsing.
 */
data class GroupHeader(val key: String, val title: String, val level: Int, val count: Int)

sealed interface FeedItem {
    data class Header(val header: GroupHeader) : FeedItem
    data class Note(val draft: NoteDraft, val groupKey: String) : FeedItem
}

data class DraftListState(
    val drafts: List<NoteDraft> = emptyList(),
    val feed: List<FeedItem> = emptyList(),
    val counts: Map<DraftFilter, Int> = emptyMap(),
    val filter: DraftFilter = DraftFilter.ALL,
    val tags: List<NoteTag> = emptyList(),
    val tagFilter: Long? = null,
    val groupBy: GroupBy = GroupBy.NONE,
    val collapsed: Set<String> = emptySet(),
    val unplacedCount: Int = 0,
    val loaded: Boolean = false,
)

data class OrganizeProgress(val done: Int, val total: Int)

class DraftListViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = (application as XhsNoteGenApp).draftRepository
    private val prefs = application.getSharedPreferences("home", 0)

    private val _filter = MutableStateFlow(DraftFilter.ALL)
    private val _tagFilter = MutableStateFlow<Long?>(null)
    private val _groupBy = MutableStateFlow(
        GroupBy.entries.firstOrNull { it.name == prefs.getString("group_by", null) } ?: GroupBy.NONE
    )
    private val _collapsed = MutableStateFlow<Set<String>>(emptySet())

    private val _selection = MutableStateFlow<Set<Long>>(emptySet())
    val selection: StateFlow<Set<Long>> = _selection.asStateFlow()

    private val _organizing = MutableStateFlow<OrganizeProgress?>(null)
    val organizing: StateFlow<OrganizeProgress?> = _organizing.asStateFlow()

    private val options = combine(_filter, _tagFilter, _groupBy, _collapsed) { f, t, g, c -> Options(f, t, g, c) }
    private data class Options(val filter: DraftFilter, val tag: Long?, val groupBy: GroupBy, val collapsed: Set<String>)

    val state: StateFlow<DraftListState> = combine(repo.getAllFlow(), repo.allTagsFlow(), options) { all, tags, o ->
        // A tag filter pointing at a deleted tag simply stops filtering.
        val tagFilter = o.tag?.takeIf { id -> tags.any { it.id == id } }
        val visible = all.filter { o.filter.matches(it.status) && (tagFilter == null || it.tags.any { t -> t.id == tagFilter }) }
        DraftListState(
            drafts = visible,
            feed = buildFeed(visible, o.groupBy, o.collapsed),
            counts = DraftFilter.entries.associateWith { f -> all.count { f.matches(it.status) } },
            filter = o.filter,
            tags = tags,
            tagFilter = tagFilter,
            groupBy = o.groupBy,
            collapsed = o.collapsed,
            unplacedCount = all.count { !it.foodInfo.place.isKnown },
            loaded = true,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DraftListState())

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages: Flow<String> = _messages.receiveAsFlow()

    // ---- Filters & grouping ----

    fun setFilter(filter: DraftFilter) { _filter.value = filter }

    fun setTagFilter(tagId: Long?) { _tagFilter.value = if (_tagFilter.value == tagId) null else tagId }

    /** Tapping the active grouping turns grouping off. */
    fun toggleGroupBy(groupBy: GroupBy) = setGroupBy(if (_groupBy.value == groupBy) GroupBy.NONE else groupBy)

    fun setGroupBy(groupBy: GroupBy) {
        _groupBy.value = groupBy
        prefs.edit { putString("group_by", groupBy.name) }
    }

    fun toggleCollapsed(key: String) {
        _collapsed.value = _collapsed.value.let { if (key in it) it - key else it + key }
    }

    // ---- Selection ----

    fun toggleSelected(id: Long) { _selection.value = _selection.value.let { if (id in it) it - id else it + id } }
    fun selectAll() { _selection.value = state.value.drafts.map { it.id }.toSet() }
    fun clearSelection() { _selection.value = emptySet() }

    /** Tag state across the selection: true = all have it, false = none, null = some. */
    fun tagStateForSelection(tagId: Long): Boolean? {
        val selected = state.value.drafts.filter { it.id in _selection.value }
        val with = selected.count { d -> d.tags.any { it.id == tagId } }
        return when (with) { 0 -> false; selected.size -> true; else -> null }
    }

    // ---- Bulk actions (also used for a single note from its menu) ----

    fun setStatus(ids: Set<Long>, status: NoteStatus) = viewModelScope.launch {
        repo.setStatus(ids, status)
        _messages.send("${ids.size} ${noun(ids.size)} marked ${statusLabel(status)}")
        clearSelection()
    }

    fun delete(ids: Set<Long>) = viewModelScope.launch {
        repo.deleteByIds(ids)
        _messages.send("Deleted ${ids.size} ${noun(ids.size)}")
        clearSelection()
    }

    /** Applies tag changes: [add] tag names (created if new), [remove] tag ids. */
    fun applyTags(ids: Set<Long>, add: List<String>, remove: List<Long>) = viewModelScope.launch {
        try {
            add.map { it.trim().removePrefix("#").trim() }.filter { it.isNotEmpty() }.distinct().forEach { name ->
                repo.addTag(ids, repo.getOrCreateTag(name).id)
            }
            remove.forEach { repo.removeTag(ids, it) }
        } catch (e: Exception) {
            _messages.send(e.message ?: "Couldn't update tags")
        }
    }

    fun renameTag(tagId: Long, name: String) = viewModelScope.launch {
        runCatching { repo.renameTag(tagId, name) }.onFailure { _messages.send("A tag with that name already exists") }
    }

    fun deleteTag(tagId: Long) = viewModelScope.launch { repo.deleteTag(tagId) }

    fun setRating(ids: Set<Long>, rating: Int) = viewModelScope.launch {
        repo.setRating(ids, rating)
        clearSelection()
    }

    /** Manual place for the given notes; auto-organize never overwrites it. */
    fun setPlace(ids: Set<Long>, country: String, region: String, city: String, address: String = "") = viewModelScope.launch {
        val place = Place(country = country.trim(), region = region.trim(), city = city.trim(),
            address = address.trim(), source = PlaceSource.MANUAL)
        ids.forEach { repo.setPlace(it, place) }
        clearSelection()
    }

    // ---- Auto-organize ----

    /**
     * Finds a Country → Region → City place for notes that don't have one yet
     * (or all non-manual ones when [redo]) from their photo GPS or their text.
     */
    fun organize(redo: Boolean = false) {
        if (_organizing.value != null) return
        viewModelScope.launch {
            val app = getApplication<Application>()
            if (!PlaceResolver.isAvailable()) {
                _messages.send("This phone has no geocoding service, so places can't be looked up. You can still set them by hand.")
                return@launch
            }
            val todo = repo.getAll().filter { d ->
                val p = d.foodInfo.place
                p.source != PlaceSource.MANUAL && (redo || !p.isKnown)
            }
            if (todo.isEmpty()) {
                _messages.send("Every note already has a place")
                setGroupBy(GroupBy.PLACE)
                return@launch
            }
            var placed = 0
            todo.forEachIndexed { i, d ->
                _organizing.value = OrganizeProgress(i, todo.size)
                PlaceResolver.forNote(app, d.foodInfo)?.let { repo.setPlace(d.id, it); placed++ }
            }
            _organizing.value = null
            setGroupBy(GroupBy.PLACE)
            val missed = todo.size - placed
            _messages.send(
                "Organized $placed ${noun(placed)} by place" +
                    if (missed > 0) " · $missed couldn't be placed — add an area or set it by hand" else ""
            )
        }
    }

    // ---- Export ----

    fun exportToUri(targetUri: Uri) {
        viewModelScope.launch {
            try {
                val all = repo.getAll()
                withContext(Dispatchers.IO) {
                    val json = JsonCodec.toJson(ExportData(drafts = all))
                    getApplication<Application>().contentResolver
                        .openOutputStream(targetUri)?.use { out ->
                            out.write(json.toByteArray(Charsets.UTF_8))
                        } ?: throw IllegalStateException("Can't write to that file")
                }
                _messages.send("Exported ${all.size} notes (text only — photos stay on this phone)")
            } catch (e: Exception) {
                _messages.send(e.message ?: "Export failed")
            }
        }
    }

    // ---- Import ----

    fun importFromUri(uri: Uri) {
        viewModelScope.launch {
            try {
                val drafts = withContext(Dispatchers.IO) {
                    val json = getApplication<Application>().contentResolver
                        .openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                        ?: throw IllegalStateException("Cannot read file")
                    JsonCodec.parseImport(json).drafts.orEmpty().filterNotNull()
                        .map { it.toDomain() }
                        .map { withOwnPhotoCopies(it) }
                }
                if (drafts.isEmpty()) {
                    _messages.send("No notes found in that file")
                    return@launch
                }
                repo.insertAll(drafts)
                _messages.send("Imported ${drafts.size} notes")
            } catch (e: Exception) {
                _messages.send("Import failed: ${e.message ?: "invalid file"}")
            }
        }
    }

    /**
     * Imported drafts get their own photo copies. Re-importing a backup on
     * the same phone used to make two drafts share files, so deleting either
     * one deleted the other's photos. References to photos that no longer
     * exist are dropped rather than shown as broken images.
     */
    private fun withOwnPhotoCopies(draft: NoteDraft): NoteDraft {
        val mapping = draft.photoUris
            .filter { ImageCleanup.localFileExists(it) }
            .associateWith { ImageCleanup.copyToLocal(getApplication(), Uri.parse(it))?.toString() }
            .filterValues { it != null }
            .mapValues { it.value!! }
        return draft.copy(
            photoUris = draft.photoUris.mapNotNull { mapping[it] },
            selectedPublishPhotoUris = draft.selectedPublishPhotoUris.mapNotNull { mapping[it] },
        )
    }

    private fun noun(n: Int) = if (n == 1) "note" else "notes"

    private fun statusLabel(s: NoteStatus) = when (s) {
        NoteStatus.DRAFT, NoteStatus.GENERATED -> "as draft"
        NoteStatus.REVIEWED -> "ready"
        NoteStatus.SHARED -> "posted"
    }

    companion object {
        const val UNKNOWN_PLACE = "Somewhere"
        const val UNTAGGED = "No tag"
        const val UNRATED = "Not rated"

        /**
         * Flattens notes into headers + notes. Place: Country → City (two levels;
         * the region is shown next to the city). Tag: one group per tag — a note
         * with several tags appears under each. Collapsed groups keep their header.
         */
        fun buildFeed(notes: List<NoteDraft>, groupBy: GroupBy, collapsed: Set<String>): List<FeedItem> = when (groupBy) {
            GroupBy.NONE -> notes.map { FeedItem.Note(it, "") }
            GroupBy.PLACE -> buildList {
                val byCountry = notes.groupBy { it.foodInfo.place.country.ifBlank { UNKNOWN_PLACE } }
                    .toSortedMap(compareBy<String> { it == UNKNOWN_PLACE }.thenByDescending { k -> notes.count { it.foodInfo.place.country.ifBlank { UNKNOWN_PLACE } == k } }.thenBy { it })
                byCountry.forEach { (country, inCountry) ->
                    val countryKey = "country:$country"
                    add(FeedItem.Header(GroupHeader(countryKey, country, 0, inCountry.size)))
                    if (countryKey in collapsed) return@forEach
                    inCountry.groupBy { cityTitle(it.foodInfo.place) }
                        .toList().sortedWith(compareBy<Pair<String, List<NoteDraft>>> { it.first == UNKNOWN_PLACE }.thenByDescending { it.second.size })
                        .forEach { (city, inCity) ->
                            val cityKey = "$countryKey/city:$city"
                            // A country with a single unknown city doesn't need a subheader.
                            if (!(country == UNKNOWN_PLACE && city == UNKNOWN_PLACE)) {
                                add(FeedItem.Header(GroupHeader(cityKey, city, 1, inCity.size)))
                            }
                            if (cityKey !in collapsed) inCity.forEach { add(FeedItem.Note(it, cityKey)) }
                        }
                }
            }
            GroupBy.RATING -> buildList {
                (5 downTo 0).forEach { stars ->
                    val inGroup = notes.filter { it.rating == stars }
                    if (inGroup.isEmpty()) return@forEach
                    val key = "rating:$stars"
                    val title = if (stars == 0) UNRATED else "★".repeat(stars) + "☆".repeat(5 - stars)
                    add(FeedItem.Header(GroupHeader(key, title, 0, inGroup.size)))
                    if (key !in collapsed) inGroup.forEach { add(FeedItem.Note(it, key)) }
                }
            }
            GroupBy.TAG -> buildList {
                val tagNames = notes.flatMap { d -> d.tags.map { it.name } }.distinct().sortedBy { it.lowercase() }
                (tagNames + UNTAGGED).forEach { tag ->
                    val inTag = if (tag == UNTAGGED) notes.filter { it.tags.isEmpty() } else notes.filter { d -> d.tags.any { it.name == tag } }
                    if (inTag.isEmpty()) return@forEach
                    val key = "tag:$tag"
                    add(FeedItem.Header(GroupHeader(key, if (tag == UNTAGGED) tag else "#$tag", 0, inTag.size)))
                    if (key !in collapsed) inTag.forEach { add(FeedItem.Note(it, key)) }
                }
            }
        }

        private fun cityTitle(p: Place): String = when {
            p.city.isNotBlank() && p.region.isNotBlank() -> "${p.city} · ${p.region}"
            p.city.isNotBlank() -> p.city
            p.region.isNotBlank() -> p.region
            else -> UNKNOWN_PLACE
        }
    }
}
