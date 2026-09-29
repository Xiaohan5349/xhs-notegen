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
import com.xiaohan.xhsnotegen.domain.TagTree
import com.xiaohan.xhsnotegen.ai.ModeStore
import com.xiaohan.xhsnotegen.i18n.notesCount
import com.xiaohan.xhsnotegen.i18n.tr
import com.xiaohan.xhsnotegen.ui.components.ratingWords
import com.xiaohan.xhsnotegen.util.ImageCleanup
import com.xiaohan.xhsnotegen.util.PlaceResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class DraftFilter {
    ALL, DRAFTS, READY, SHARED;

    val label: String
        get() = when (this) {
            ALL -> tr("All", "全部")
            DRAFTS -> tr("Drafts", "草稿")
            READY -> tr("Ready", "待发布")
            SHARED -> tr("Posted", "已发布")
        }

    fun matches(status: NoteStatus): Boolean = when (this) {
        ALL -> true
        DRAFTS -> status == NoteStatus.DRAFT || status == NoteStatus.GENERATED
        READY -> status == NoteStatus.REVIEWED
        SHARED -> status == NoteStatus.SHARED
    }
}

/** How the home feed is arranged. */
enum class GroupBy {
    NONE, PLACE, TAG, RATING;

    val label: String
        get() = when (this) {
            NONE -> tr("None", "不分组")
            PLACE -> tr("Place", "地点")
            TAG -> tr("Tag", "标签")
            RATING -> tr("Rating", "评分")
        }
}

/**
 * One header in a grouped feed. [level] 0 = top group (country / tag),
 * 1+ = subgroups (city / state / sub-tags at any depth). [key] identifies it
 * for collapsing. [rating] is set on rating groups (0 = not rated).
 */
data class GroupHeader(val key: String, val title: String, val level: Int, val count: Int, val rating: Int? = null)

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
        // Filtering by a tag includes everything below it.
        val filterIds = tagFilter?.let { id -> setOf(id) + TagTree.descendants(id, tags) }
        val visible = all.filter { o.filter.matches(it.status) && (filterIds == null || it.tags.any { t -> t.id in filterIds }) }
        DraftListState(
            drafts = visible,
            feed = buildFeed(visible, o.groupBy, o.collapsed, tags),
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
        _messages.send(tr("${notesCount(ids.size)} marked ${statusLabel(status)}", "${notesCount(ids.size)}已标为${statusLabelZh(status)}"))
        clearSelection()
    }

    fun delete(ids: Set<Long>) = viewModelScope.launch {
        repo.deleteByIds(ids)
        _messages.send(tr("Deleted ${notesCount(ids.size)}", "已删除 ${notesCount(ids.size)}"))
        clearSelection()
    }

    /**
     * Applies tag changes: [add] tag names (new ones are created under
     * [newParentId], usually the notes' root tag; a name may be a path like
     * "日本/京都"), [remove] tag ids.
     */
    fun applyTags(ids: Set<Long>, add: List<String>, remove: List<Long>, newParentId: Long? = null) = viewModelScope.launch {
        try {
            add.map { TagTree.parsePath(it) }.filter { it.isNotEmpty() }.distinct().forEach { path ->
                var tag: NoteTag? = null
                path.forEachIndexed { i, name -> tag = repo.getOrCreateTag(name, if (i == 0) newParentId else tag?.id) }
                tag?.let { repo.addTag(ids, it.id) }
            }
            remove.forEach { repo.removeTag(ids, it) }
        } catch (e: Exception) {
            _messages.send(e.message ?: tr("Couldn't update tags", "标签没能更新"))
        }
    }

    fun renameTag(tagId: Long, name: String) = viewModelScope.launch {
        runCatching { repo.renameTag(tagId, name) }.onFailure { _messages.send(tr("A tag with that name already exists", "已有同名标签")) }
    }

    fun deleteTag(tagId: Long) = viewModelScope.launch { repo.deleteTag(tagId) }

    fun setTagParent(tagId: Long, parentId: Long?) = viewModelScope.launch { repo.setTagParent(tagId, parentId) }

    /** The root tag shared by all these notes' modes, if they're all in one mode. */
    fun commonRootTag(ids: Set<Long>): NoteTag? {
        val modes = state.value.drafts.filter { it.id in ids }.map { it.type }.distinct()
        val root = modes.singleOrNull()?.let { TagTree.parsePath(ModeStore.get(it).rootTag).lastOrNull() } ?: return null
        return state.value.tags.firstOrNull { it.name == root }
    }

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
                _messages.send(tr("This phone has no geocoding service, so places can't be looked up. You can still set them by hand.",
                    "这台手机没有地理编码服务，查不到地点。你仍然可以手动设置。"))
                return@launch
            }
            val todo = repo.getAll().filter { d ->
                val p = d.foodInfo.place
                p.source != PlaceSource.MANUAL && (redo || !p.isKnown)
            }
            if (todo.isEmpty()) {
                _messages.send(tr("Every note already has a place", "每篇笔记都已经有地点了"))
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
                tr("Organized ${notesCount(placed)} by place", "已按地点整理 ${notesCount(placed)}") +
                    if (missed > 0) tr(" · $missed couldn't be placed — add an area or set it by hand", " · $missed 篇没找到地点，可以补充区域或手动设置") else ""
            )
        }
    }

    // ---- Export ----

    fun exportToUri(targetUri: Uri) {
        viewModelScope.launch {
            try {
                val all = repo.getAll()
                withContext(Dispatchers.IO) {
                    val json = JsonCodec.toJson(ExportData(drafts = all, tagParents = repo.tagParentsByName()))
                    getApplication<Application>().contentResolver
                        .openOutputStream(targetUri)?.use { out ->
                            out.write(json.toByteArray(Charsets.UTF_8))
                        } ?: throw IllegalStateException(tr("Can't write to that file", "无法写入这个文件"))
                }
                _messages.send(tr("Exported ${all.size} notes (text only — photos stay on this phone)", "已导出 ${all.size} 篇笔记（仅文字，照片留在手机上）"))
            } catch (e: Exception) {
                _messages.send(e.message ?: tr("Export failed", "导出失败"))
            }
        }
    }

    // ---- Import ----

    fun importFromUri(uri: Uri) {
        viewModelScope.launch {
            try {
                var parents: Map<String, String> = emptyMap()
                val drafts = withContext(Dispatchers.IO) {
                    val json = getApplication<Application>().contentResolver
                        .openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                        ?: throw IllegalStateException(tr("Cannot read file", "无法读取文件"))
                    val data = JsonCodec.parseImport(json)
                    parents = data.tagParents.orEmpty().mapNotNull { (k, v) -> if (k.isNullOrBlank() || v.isNullOrBlank()) null else k to v }.toMap()
                    data.drafts.orEmpty().filterNotNull()
                        .map { it.toDomain() }
                        .map { withOwnPhotoCopies(it) }
                }
                if (drafts.isEmpty()) {
                    _messages.send(tr("No notes found in that file", "文件里没有笔记"))
                    return@launch
                }
                repo.insertAll(drafts, parents)
                _messages.send(tr("Imported ${drafts.size} notes", "已导入 ${drafts.size} 篇笔记"))
            } catch (e: Exception) {
                _messages.send(tr("Import failed: ${e.message ?: "invalid file"}", "导入失败：${e.message ?: "文件无效"}"))
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

    private fun statusLabel(s: NoteStatus) = when (s) {
        NoteStatus.DRAFT, NoteStatus.GENERATED -> "as draft"
        NoteStatus.REVIEWED -> "ready"
        NoteStatus.SHARED -> "posted"
    }

    private fun statusLabelZh(s: NoteStatus) = when (s) {
        NoteStatus.DRAFT, NoteStatus.GENERATED -> "草稿"
        NoteStatus.REVIEWED -> "待发布"
        NoteStatus.SHARED -> "已发布"
    }

    companion object {
        val UNKNOWN_PLACE: String get() = tr("Somewhere", "未知地点")
        val UNTAGGED: String get() = tr("No tag", "无标签")
        val UNRATED: String get() = tr("Not rated", "未评分")
        val GENERAL: String get() = tr("General", "其他")

        /**
         * Countries grouped Country → State rather than Country → City: in these,
         * the state is the level people think in. Names as the geocoder gives them
         * (Chinese) plus common English spellings for places typed by hand.
         */
        private val STATE_COUNTRIES = setOf(
            "美国", "United States", "United States of America", "USA", "US", "U.S.",
            "加拿大", "Canada", "澳大利亚", "Australia",
        )

        fun groupsByState(country: String) = country.trim() in STATE_COUNTRIES

        /**
         * Flattens notes into headers + notes. Place: Country → City (the region
         * is shown next to the city), or Country → State for the US, Canada and
         * Australia. Tag: the tag tree at any depth — a note sits under the deepest
         * tags it has, notes with only an upper tag under "General", and a note with
         * several tags appears under each. Collapsed groups keep their header.
         */
        fun buildFeed(
            notes: List<NoteDraft>, groupBy: GroupBy, collapsed: Set<String>,
            allTags: List<NoteTag> = emptyList(),
        ): List<FeedItem> = when (groupBy) {
            GroupBy.NONE -> notes.map { FeedItem.Note(it, "") }
            GroupBy.PLACE -> buildList {
                val unknown = UNKNOWN_PLACE
                val byCountry = notes.groupBy { it.foodInfo.place.country.ifBlank { unknown } }
                    .toSortedMap(compareBy<String> { it == unknown }.thenByDescending { k -> notes.count { it.foodInfo.place.country.ifBlank { unknown } == k } }.thenBy { it })
                byCountry.forEach { (country, inCountry) ->
                    val countryKey = "country:$country"
                    add(FeedItem.Header(GroupHeader(countryKey, country, 0, inCountry.size)))
                    if (countryKey in collapsed) return@forEach
                    val byState = groupsByState(country)
                    inCountry.groupBy { if (byState) stateTitle(it.foodInfo.place) else cityTitle(it.foodInfo.place) }
                        .toList().sortedWith(compareBy<Pair<String, List<NoteDraft>>> { it.first == unknown }.thenByDescending { it.second.size })
                        .forEach { (sub, inSub) ->
                            val subKey = "$countryKey/${if (byState) "state" else "city"}:$sub"
                            // A country with a single unknown city doesn't need a subheader.
                            if (!(country == unknown && sub == unknown)) {
                                add(FeedItem.Header(GroupHeader(subKey, sub, 1, inSub.size)))
                            }
                            if (subKey !in collapsed) inSub.forEach { add(FeedItem.Note(it, subKey)) }
                        }
                }
            }
            GroupBy.RATING -> buildList {
                (5 downTo 0).forEach { stars ->
                    val inGroup = notes.filter { it.rating == stars }
                    if (inGroup.isEmpty()) return@forEach
                    val key = "rating:$stars"
                    val title = if (stars == 0) UNRATED else ratingWords(stars)
                    add(FeedItem.Header(GroupHeader(key, title, 0, inGroup.size, rating = stars)))
                    if (key !in collapsed) inGroup.forEach { add(FeedItem.Note(it, key)) }
                }
            }
            GroupBy.TAG -> buildList {
                val byId = (allTags + notes.flatMap { it.tags }).associateBy { it.id }
                val tree = byId.values.toList()
                // Ids of each note's tags and everything above them.
                val branchIds = notes.associate { n -> n.id to n.tags.flatMap { t -> TagTree.path(t, byId).map { it.id } }.toSet() }
                fun inBranch(n: NoteDraft, id: Long) = id in branchIds[n.id].orEmpty()
                fun order(groups: List<Pair<NoteTag, List<NoteDraft>>>) =
                    groups.sortedWith(compareByDescending<Pair<NoteTag, List<NoteDraft>>> { it.second.size }.thenBy { it.first.name.lowercase() })

                fun emit(tag: NoteTag, members: List<NoteDraft>, level: Int, key: String, seen: Set<Long>) {
                    add(FeedItem.Header(GroupHeader(key, tag.name, level, members.size)))
                    if (key in collapsed) return
                    val children = order(TagTree.children(tag.id, tree).filter { it.id !in seen }
                        .map { c -> c to members.filter { inBranch(it, c.id) } }
                        .filter { it.second.isNotEmpty() })
                    if (children.isEmpty()) { members.forEach { add(FeedItem.Note(it, key)) }; return }
                    children.forEach { (c, m) -> emit(c, m, level + 1, "$key/${c.id}", seen + c.id) }
                    val general = members.filter { n -> children.none { (c, _) -> inBranch(n, c.id) } }
                    if (general.isNotEmpty()) {
                        val gKey = "$key/general"
                        add(FeedItem.Header(GroupHeader(gKey, GENERAL, level + 1, general.size)))
                        if (gKey !in collapsed) general.forEach { add(FeedItem.Note(it, gKey)) }
                    }
                }

                val roots = notes.flatMap { n -> n.tags.map { TagTree.rootOf(it, byId) } }.distinctBy { it.id }
                order(roots.map { r -> r to notes.filter { inBranch(it, r.id) } })
                    .forEach { (r, m) -> emit(r, m, 0, "tag:${r.id}", setOf(r.id)) }
                val untagged = notes.filter { it.tags.isEmpty() }
                if (untagged.isNotEmpty()) {
                    val key = "tag:none"
                    add(FeedItem.Header(GroupHeader(key, UNTAGGED, 0, untagged.size)))
                    if (key !in collapsed) untagged.forEach { add(FeedItem.Note(it, key)) }
                }
            }
        }

        private fun cityTitle(p: Place): String = when {
            p.city.isNotBlank() && p.region.isNotBlank() -> "${p.city} · ${p.region}"
            p.city.isNotBlank() -> p.city
            p.region.isNotBlank() -> p.region
            else -> UNKNOWN_PLACE
        }

        /** State (or province); the city only when the state is missing. */
        private fun stateTitle(p: Place): String = p.region.ifBlank { p.city }.ifBlank { UNKNOWN_PLACE }
    }
}
