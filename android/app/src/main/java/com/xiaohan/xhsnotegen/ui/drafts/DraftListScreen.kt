package com.xiaohan.xhsnotegen.ui.drafts

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.staggeredgrid.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.xiaohan.xhsnotegen.domain.NoteDraft
import com.xiaohan.xhsnotegen.domain.NoteStatus
import com.xiaohan.xhsnotegen.domain.NoteTag
import com.xiaohan.xhsnotegen.domain.Place
import com.xiaohan.xhsnotegen.domain.TagTree
import com.xiaohan.xhsnotegen.i18n.LanguageStore
import com.xiaohan.xhsnotegen.i18n.tr
import com.xiaohan.xhsnotegen.ui.components.AppFilterChip
import com.xiaohan.xhsnotegen.ui.components.CardStars
import com.xiaohan.xhsnotegen.ui.components.EmptyState
import com.xiaohan.xhsnotegen.ui.components.RatingDialog
import com.xiaohan.xhsnotegen.ui.components.RatingMeter
import com.xiaohan.xhsnotegen.ui.components.StatusPill
import com.xiaohan.xhsnotegen.ui.components.Tile
import com.xiaohan.xhsnotegen.ui.components.softFieldColors
import com.xiaohan.xhsnotegen.ui.publish.XhsAuthStore
import com.xiaohan.xhsnotegen.ui.theme.BigNumberStyle
import com.xiaohan.xhsnotegen.ui.theme.NumberStyle
import com.xiaohan.xhsnotegen.ui.theme.ThemeBackdrop
import com.xiaohan.xhsnotegen.ui.theme.app

/** Which dialog is open, and for which notes. */
private sealed interface HomeDialog {
    data class Tags(val ids: Set<Long>) : HomeDialog
    data class SetPlace(val ids: Set<Long>) : HomeDialog
    data class Delete(val ids: Set<Long>) : HomeDialog
    data class Rate(val ids: Set<Long>) : HomeDialog
    data object ManageTags : HomeDialog
    data object AllTags : HomeDialog
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DraftListScreen(
    onCreateClick: () -> Unit,
    onDraftClick: (Long) -> Unit,
    onOpenSettings: () -> Unit = {},
    viewModel: DraftListViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsState()
    val selection by viewModel.selection.collectAsState()
    val organizing by viewModel.organizing.collectAsState()
    val loggedIn by XhsAuthStore.loggedIn.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val gridState = rememberLazyStaggeredGridState()
    var showMenu by remember { mutableStateOf(false) }
    var dialog by remember { mutableStateOf<HomeDialog?>(null) }
    val selecting = selection.isNotEmpty()

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { viewModel.importFromUri(it) }
    }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let { viewModel.exportToUri(it) }
    }

    LaunchedEffect(Unit) {
        viewModel.messages.collect { snackbarHostState.showSnackbar(it) }
    }
    // Back leaves selection mode before leaving the screen.
    BackHandler(enabled = selecting) { viewModel.clearSelection() }

    val total = state.counts[DraftFilter.ALL] ?: 0
    // The first note of each group (or of the whole feed) gets a full-width tile.
    val featured = remember(state.feed) {
        buildSet {
            var afterHeader = true
            state.feed.forEach { item ->
                when (item) {
                    is FeedItem.Header -> afterHeader = true
                    is FeedItem.Note -> { if (afterHeader) add("${item.groupKey}:${item.draft.id}"); afterHeader = false }
                }
            }
        }
    }

    Scaffold(
        topBar = {
            if (selecting) {
                SelectionBar(
                    count = selection.size,
                    allSelected = selection.size == state.drafts.size,
                    onClose = viewModel::clearSelection,
                    onSelectAll = viewModel::selectAll,
                    onTags = { dialog = HomeDialog.Tags(selection) },
                    onStatus = { viewModel.setStatus(selection, it) },
                    onPlace = { dialog = HomeDialog.SetPlace(selection) },
                    onRate = { dialog = HomeDialog.Rate(selection) },
                    onDelete = { dialog = HomeDialog.Delete(selection) },
                )
            } else {
                TopAppBar(
                    title = { Text(tr("Glint", "浮生拾遗"), style = MaterialTheme.typography.headlineSmall, maxLines = 1) },
                    actions = {
                        IconButton(onClick = onOpenSettings) {
                            BadgedBox(badge = {
                                if (loggedIn) Badge(containerColor = MaterialTheme.app.goldMark)
                            }) {
                                Icon(Icons.Outlined.AccountCircle,
                                    contentDescription = if (loggedIn) tr("Settings · Xiaohongshu connected", "设置 · 已连接小红书") else tr("Settings", "设置"))
                            }
                        }
                        Box {
                            IconButton(onClick = { showMenu = true }) {
                                Icon(Icons.Outlined.MoreVert, contentDescription = tr("More", "更多"))
                            }
                            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                                DropdownMenuItem(
                                    text = { Text(tr("Organize by place", "按地点整理")) },
                                    leadingIcon = { Icon(Icons.Outlined.TravelExplore, null) },
                                    onClick = { showMenu = false; viewModel.organize() },
                                )
                                DropdownMenuItem(
                                    text = { Text(tr("Re-check all places", "重新识别所有地点")) },
                                    leadingIcon = { Icon(Icons.Outlined.Refresh, null) },
                                    onClick = { showMenu = false; viewModel.organize(redo = true) },
                                )
                                DropdownMenuItem(
                                    text = { Text(tr("Manage tags", "管理标签")) },
                                    leadingIcon = { Icon(Icons.Outlined.Label, null) },
                                    onClick = { showMenu = false; dialog = HomeDialog.ManageTags },
                                )
                                HorizontalDivider()
                                DropdownMenuItem(
                                    text = { Text(tr("Import backup", "导入备份")) },
                                    leadingIcon = { Icon(Icons.Outlined.FileOpen, null) },
                                    onClick = { showMenu = false; importLauncher.launch(arrayOf("application/json")) },
                                )
                                DropdownMenuItem(
                                    text = { Text(tr("Export backup", "导出备份")) },
                                    leadingIcon = { Icon(Icons.Outlined.SaveAlt, null) },
                                    onClick = { showMenu = false; exportLauncher.launch("xhs_notes_backup.json") },
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                )
            }
        },
        floatingActionButton = {
            // The empty state has its own call to action; one is enough.
            if (total > 0 && !selecting) ExtendedFloatingActionButton(
                onClick = onCreateClick,
                expanded = !gridState.canScrollBackward,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text(tr("New note", "写笔记")) },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        LazyVerticalStaggeredGrid(
            columns = StaggeredGridCells.Fixed(2),
            state = gridState,
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 4.dp, bottom = 96.dp),
            verticalItemSpacing = 10.dp,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (total > 0) {
                item(span = StaggeredGridItemSpan.FullLine, key = "stats") {
                    StatsTiles(state.counts, state.filter, viewModel::setFilter)
                }
                item(span = StaggeredGridItemSpan.FullLine, key = "controls") {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (state.tags.isNotEmpty()) {
                            TagFilterBox(
                                state = state,
                                onToggle = viewModel::toggleTagFilter,
                                onClear = viewModel::clearTagFilters,
                                onOpenAll = { dialog = HomeDialog.AllTags },
                            )
                        }
                        GroupRow(state, organizing, onGroupBy = viewModel::toggleGroupBy, onOrganize = { viewModel.organize() })
                    }
                }
            }

            if (state.loaded && state.drafts.isEmpty()) {
                item(span = StaggeredGridItemSpan.FullLine, key = "empty") {
                    if (total == 0) {
                        EmptyState(
                            icon = Icons.Outlined.AutoAwesome,
                            title = tr("Your diary is empty", "还没有笔记"),
                            body = tr("Snap a photo, jot down a few words, and get a note that sounds like you.", "拍张照，随手写几句，就能得到一篇像你自己写的笔记。"),
                            action = {
                                Button(onClick = onCreateClick, shape = MaterialTheme.shapes.medium) {
                                    Icon(Icons.Filled.Add, null, Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text(tr("Write your first note", "写第一篇笔记"))
                                }
                            },
                        )
                    } else {
                        EmptyState(
                            icon = Icons.Outlined.FilterList,
                            title = tr("Nothing here yet", "这里还没有"),
                            body = tr("No notes match these filters.", "没有符合筛选条件的笔记。"),
                        )
                    }
                }
            }

            items(
                state.feed,
                // A note can appear under several tags, so keys include the group.
                key = { item ->
                    when (item) {
                        is FeedItem.Header -> "h:${item.header.key}"
                        is FeedItem.Note -> "n:${item.groupKey}:${item.draft.id}"
                    }
                },
                span = { item ->
                    when {
                        item is FeedItem.Header -> StaggeredGridItemSpan.FullLine
                        item is FeedItem.Note && "${item.groupKey}:${item.draft.id}" in featured -> StaggeredGridItemSpan.FullLine
                        else -> StaggeredGridItemSpan.SingleLane
                    }
                },
            ) { item ->
                when (item) {
                    is FeedItem.Header -> GroupHeaderRow(
                        header = item.header,
                        collapsed = item.header.key in state.collapsed,
                        onToggle = { viewModel.toggleCollapsed(item.header.key) },
                    )
                    is FeedItem.Note -> {
                        val draft = item.draft
                        NoteCard(
                            draft = draft,
                            featured = "${item.groupKey}:${draft.id}" in featured,
                            selecting = selecting,
                            selected = draft.id in selection,
                            onClick = { if (selecting) viewModel.toggleSelected(draft.id) else onDraftClick(draft.id) },
                            onLongClick = { viewModel.toggleSelected(draft.id) },
                            onTags = { dialog = HomeDialog.Tags(setOf(draft.id)) },
                            onPlace = { dialog = HomeDialog.SetPlace(setOf(draft.id)) },
                            onRate = { dialog = HomeDialog.Rate(setOf(draft.id)) },
                            onQuickRate = { viewModel.setRating(setOf(draft.id), it) },
                            onStatus = { viewModel.setStatus(setOf(draft.id), it) },
                            onDelete = { dialog = HomeDialog.Delete(setOf(draft.id)) },
                        )
                    }
                }
            }
        }
    }

    // ---- Dialogs ----
    when (val d = dialog) {
        is HomeDialog.Tags -> TagsDialog(
            noteCount = d.ids.size,
            tags = state.tags,
            stateOf = { tagId ->
                val notes = state.drafts.filter { it.id in d.ids }
                when (notes.count { n -> n.tags.any { it.id == tagId } }) {
                    0 -> false
                    notes.size -> true
                    else -> null
                }
            },
            onApply = { add, remove, parent -> viewModel.applyTags(d.ids, add, remove, parent); viewModel.clearSelection() },
            onDismiss = { dialog = null },
        )
        is HomeDialog.SetPlace -> PlaceDialog(
            noteCount = d.ids.size,
            initial = state.drafts.firstOrNull { it.id in d.ids }?.foodInfo?.place?.takeIf { d.ids.size == 1 } ?: Place(),
            onSave = { country, region, city, address -> viewModel.setPlace(d.ids, country, region, city, address) },
            onDismiss = { dialog = null },
        )
        is HomeDialog.Rate -> RatingDialog(
            noteCount = d.ids.size,
            initial = state.drafts.filter { it.id in d.ids }.map { it.rating }.distinct().singleOrNull() ?: 0,
            onSave = { viewModel.setRating(d.ids, it) },
            onDismiss = { dialog = null },
        )
        is HomeDialog.Delete -> ConfirmDeleteDialog(
            noteCount = d.ids.size,
            anyPosted = state.drafts.any { it.id in d.ids && it.status == NoteStatus.SHARED },
            onConfirm = { viewModel.delete(d.ids) },
            onDismiss = { dialog = null },
        )
        HomeDialog.ManageTags -> ManageTagsDialog(
            tags = state.tags,
            onRename = viewModel::renameTag,
            onDelete = viewModel::deleteTag,
            onSetParent = viewModel::setTagParent,
            onDismiss = { dialog = null },
        )
        HomeDialog.AllTags -> AllTagsSheet(
            tags = state.tags,
            picked = state.tagFilters,
            counts = state.tagCounts,
            onToggle = viewModel::toggleTagFilter,
            onClear = viewModel::clearTagFilters,
            onDismiss = { dialog = null },
        )
        null -> Unit
    }
}

// ---------------------------------------------------------------------------
// Top bar in selection mode
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelectionBar(
    count: Int,
    allSelected: Boolean,
    onClose: () -> Unit,
    onSelectAll: () -> Unit,
    onTags: () -> Unit,
    onStatus: (NoteStatus) -> Unit,
    onPlace: () -> Unit,
    onRate: () -> Unit,
    onDelete: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    TopAppBar(
        title = { Text(tr("$count selected", "已选 $count 篇")) },
        navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.Filled.Close, tr("Cancel selection", "取消选择")) } },
        actions = {
            if (!allSelected) IconButton(onClick = onSelectAll) { Icon(Icons.Outlined.SelectAll, tr("Select all", "全选")) }
            IconButton(onClick = onTags) { Icon(Icons.Outlined.Label, tr("Tags", "标签")) }
            IconButton(onClick = onRate) { Icon(Icons.Outlined.StarOutline, tr("Rate", "评分")) }
            IconButton(onClick = onDelete) { Icon(Icons.Outlined.Delete, tr("Delete", "删除")) }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, tr("More", "更多")) }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    StatusMenuItems { menu = false; onStatus(it) }
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text(tr("Set place…", "设置地点…")) },
                        leadingIcon = { Icon(Icons.Outlined.Place, null) },
                        onClick = { menu = false; onPlace() },
                    )
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            navigationIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            actionIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
    )
}

@Composable
private fun StatusMenuItems(onPick: (NoteStatus) -> Unit) {
    DropdownMenuItem(text = { Text(tr("Mark as draft", "标为草稿")) }, leadingIcon = { Icon(Icons.Outlined.EditNote, null) },
        onClick = { onPick(NoteStatus.GENERATED) })
    DropdownMenuItem(text = { Text(tr("Mark as ready", "标为待发布")) }, leadingIcon = { Icon(Icons.Outlined.TaskAlt, null) },
        onClick = { onPick(NoteStatus.REVIEWED) })
    DropdownMenuItem(text = { Text(tr("Mark as posted", "标为已发布")) }, leadingIcon = { Icon(Icons.Outlined.Send, null) },
        onClick = { onPick(NoteStatus.SHARED) })
}

// ---------------------------------------------------------------------------
// Status tiles: the counts are the filter
// ---------------------------------------------------------------------------

@Composable
private fun StatsTiles(counts: Map<DraftFilter, Int>, selected: DraftFilter, onSelect: (DraftFilter) -> Unit) {
    val total = counts[DraftFilter.ALL] ?: 0
    val posted = counts[DraftFilter.SHARED] ?: 0
    val ready = counts[DraftFilter.READY] ?: 0
    val drafts = counts[DraftFilter.DRAFTS] ?: 0
    val a = MaterialTheme.app
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Tile(
            modifier = Modifier.weight(1.55f).fillMaxHeight(),
            selected = selected == DraftFilter.ALL,
            onClick = { onSelect(DraftFilter.ALL) },
            contentPadding = PaddingValues(0.dp),
        ) {
            Box(Modifier.fillMaxSize()) {
                // The theme's art (Glint ripples, summer sky, …) lives here now.
                ThemeBackdrop(Modifier.matchParentSize(), inTile = true)
                Column(Modifier.padding(14.dp)) {
                    Text(tr("All notes", "全部笔记"), style = MaterialTheme.typography.labelMedium, color = LocalContentColor.current)
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text("$total", style = BigNumberStyle)
                        Spacer(Modifier.width(4.dp))
                        Text(tr("notes", "篇"), style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(bottom = 10.dp))
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        if (posted > 0) Box(Modifier.weight(posted.toFloat()).fillMaxHeight().background(a.goldMark))
                        if (ready > 0) Box(Modifier.weight(ready.toFloat()).fillMaxHeight().background(MaterialTheme.colorScheme.primary))
                        if (drafts > 0) Box(Modifier.weight(drafts.toFloat()).fillMaxHeight().background(a.line2))
                    }
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Legend(a.goldMark, tr("Posted", "已发布"), posted)
                        Legend(MaterialTheme.colorScheme.primary, tr("Ready", "待发"), ready)
                        Legend(a.line2, tr("Drafts", "草稿"), drafts)
                    }
                }
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            CountTile(tr("Ready", "待发布"), ready, MaterialTheme.colorScheme.primary, selected == DraftFilter.READY) { onSelect(DraftFilter.READY) }
            CountTile(tr("Drafts", "草稿"), drafts, a.line2, selected == DraftFilter.DRAFTS) { onSelect(DraftFilter.DRAFTS) }
            CountTile(tr("Posted", "已发布"), posted, a.goldMark, selected == DraftFilter.SHARED) { onSelect(DraftFilter.SHARED) }
        }
    }
}

@Composable
private fun Legend(color: Color, label: String, n: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(4.dp))
        Text("$label $n", style = NumberStyle, color = LocalContentColor.current, maxLines = 1)
    }
}

@Composable
private fun CountTile(label: String, n: Int, dot: Color, selected: Boolean, onClick: () -> Unit) {
    Tile(Modifier.fillMaxWidth(), selected = selected, onClick = onClick, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(7.dp).clip(CircleShape).background(dot))
            Spacer(Modifier.width(6.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f), maxLines = 1)
            Text("$n", style = MaterialTheme.typography.titleLarge)
        }
    }
}

// ---------------------------------------------------------------------------
// Tags: up to two lines, the picked branch on its own row, everything in a sheet
// ---------------------------------------------------------------------------

/**
 * Top-level tags wrap onto at most two lines; when they don't fit, the last
 * spot becomes "All N". Picking a tag that has tags below it opens a row with
 * those (日本 › 京都 大阪 …). Several tags can be picked at once.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TagFilterBox(
    state: DraftListState,
    onToggle: (Long) -> Unit,
    onClear: () -> Unit,
    onOpenAll: () -> Unit,
) {
    val tags = state.tags
    val picked = state.tagFilters
    val byId = remember(tags) { tags.associateBy { it.id } }
    var focusId by rememberSaveable { mutableStateOf<Long?>(null) }
    val focus = focusId?.let { byId[it] }
    fun pickedBelow(id: Long) = TagTree.descendants(id, tags).count { it in picked }
    fun hasChildren(id: Long) = tags.any { it.parentId == id }

    /** Tapping a tag with children opens its row first; tapping it again (un)picks it. */
    fun tap(t: NoteTag) {
        if (!hasChildren(t.id)) { onToggle(t.id); return }
        if (focusId != t.id && (t.id in picked || pickedBelow(t.id) > 0)) { focusId = t.id; return }
        onToggle(t.id)
        focusId = t.id
    }

    FlowRow(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp),
        maxLines = 2,
        overflow = FlowRowOverflow.expandIndicator {
            AllTagsChip(tags.size, onOpenAll)
        },
    ) {
        Text(tr("Tags", "标签"), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.CenterVertically).padding(end = 2.dp))
        if (picked.isNotEmpty()) {
            AssistChip(onClick = { onClear(); focusId = null }, label = { Text(tr("Clear", "清除")) },
                leadingIcon = { Icon(Icons.Filled.Close, null, Modifier.size(16.dp)) }, shape = CircleShape,
                border = BorderStroke(1.dp, MaterialTheme.app.line2))
        }
        // Most-used first, so the two visible lines hold the tags you actually filter by.
        TagTree.roots(tags).sortedByDescending { state.tagCounts[it.id] ?: 0 }.forEach { r ->
            val below = pickedBelow(r.id)
            AppFilterChip(selected = r.id in picked || below > 0, onClick = { tap(r) }, label = r.name,
                trailing = if (below > 0) "+$below" else null)
        }
    }

    if (focus != null) {
        val path = TagTree.path(focus, byId)
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 6.dp)
                .clip(MaterialTheme.shapes.small)
                .background(MaterialTheme.colorScheme.primaryContainer)
                .padding(start = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LazyRow(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                items(path, key = { "p${it.id}" }) { p ->
                    Text("${p.name} ›", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable { focusId = p.id }.padding(4.dp))
                }
                item(key = "all") {
                    // "All" is on only when this tag itself filters, not a tag below it.
                    AppFilterChip(selected = focus.id in picked && pickedBelow(focus.id) == 0, onClick = { onToggle(focus.id) }, label = tr("All", "全部"), onTinted = true)
                }
                items(TagTree.children(focus.id, tags).sortedByDescending { state.tagCounts[it.id] ?: 0 }, key = { "c${it.id}" }) { c ->
                    val below = pickedBelow(c.id)
                    AppFilterChip(selected = c.id in picked || below > 0, onClick = { tap(c) },
                        label = c.name + if (hasChildren(c.id)) " ›" else "",
                        trailing = if (below > 0) "+$below" else null, onTinted = true)
                }
            }
            IconButton(onClick = { focusId = null }) {
                Icon(Icons.Filled.Close, tr("Close", "收起"), tint = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }
    }
}

@Composable
private fun AllTagsChip(count: Int, onClick: () -> Unit) {
    Box(
        Modifier
            .padding(vertical = 8.dp)
            .heightIn(min = 32.dp)
            .clip(CircleShape)
            .border(1.dp, MaterialTheme.colorScheme.primary, CircleShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(tr("All $count ›", "全部 $count 个 ›"), style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.SemiBold)
    }
}

/** Every tag, searchable: branches (日本 › 京都 …) as groups with note counts, the rest together. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun AllTagsSheet(
    tags: List<NoteTag>,
    picked: Set<Long>,
    counts: Map<Long, Int>,
    onToggle: (Long) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val byId = remember(tags) { tags.associateBy { it.id } }
    val ordered = remember(tags) { TagTree.ordered(tags) }
    fun hit(t: NoteTag) = query.isBlank() || t.name.contains(query.trim(), ignoreCase = true)

    val roots = TagTree.roots(tags).sortedByDescending { counts[it.id] ?: 0 }
    val branches = roots.filter { r -> tags.any { it.parentId == r.id } }
    val loose = roots.filter { r -> tags.none { it.parentId == r.id } && hit(r) }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.app.tile) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(tr("Tags", "标签"), style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.width(8.dp))
                Text(tr("${tags.size} · pick any", "${tags.size} 个 · 可多选"), style = NumberStyle, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f))
                TextButton(onClick = onClear, enabled = picked.isNotEmpty()) { Text(tr("Clear", "清除筛选")) }
            }
            TextField(
                value = query, onValueChange = { query = it }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                leadingIcon = { Icon(Icons.Outlined.Search, null) },
                placeholder = { Text(tr("Search tags, e.g. Kyoto or ramen", "搜索标签，比如「京都」或「拉面」")) },
                shape = MaterialTheme.shapes.small, colors = softFieldColors(),
            )
            branches.forEach { r ->
                val below = ordered.filter { (t, d) -> d > 0 && TagTree.rootOf(t, byId).id == r.id }
                val shown = below.filter { (t, _) -> hit(t) }
                if (!hit(r) && shown.isEmpty()) return@forEach
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AppFilterChip(selected = r.id in picked, onClick = { onToggle(r.id) }, label = r.name)
                        Spacer(Modifier.width(8.dp))
                        Text(tr("${below.size} below · ${counts[r.id] ?: 0} notes", "${below.size} 个 · ${counts[r.id] ?: 0} 篇"),
                            style = NumberStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        (if (hit(r)) below else shown).forEach { (t, d) ->
                            AppFilterChip(selected = t.id in picked, onClick = { onToggle(t.id) },
                                label = (if (d > 1) "› " else "") + t.name, trailing = "${counts[t.id] ?: 0}")
                        }
                    }
                }
            }
            if (loose.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(tr("Other tags", "其他标签"), style = MaterialTheme.typography.titleSmall)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        loose.forEach { t ->
                            AppFilterChip(selected = t.id in picked, onClick = { onToggle(t.id) }, label = t.name, trailing = "${counts[t.id] ?: 0}")
                        }
                    }
                }
            }
            Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth().height(48.dp), shape = MaterialTheme.shapes.small) {
                Text(tr("Done", "完成"))
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Grouping
// ---------------------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GroupRow(
    state: DraftListState,
    organizing: OrganizeProgress?,
    onGroupBy: (GroupBy) -> Unit,
    onOrganize: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(tr("Group", "分组"), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterVertically).padding(end = 2.dp))
            state.groupOrder.forEach { g ->
                AppFilterChip(selected = g == state.groupBy, onClick = { onGroupBy(g) }, label = g.label)
            }
        }
        // On its own line: with the Group buttons in English there is no room for it beside them.
        if (state.groupBy == GroupBy.PLACE && state.unplacedCount > 0 && organizing == null) {
            TextButton(onClick = onOrganize) {
                Icon(Icons.Outlined.TravelExplore, null, Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(tr("Organize ${state.unplacedCount} without a place", "整理 ${state.unplacedCount} 篇没有地点的笔记"), maxLines = 1)
            }
        }
        if (organizing != null) {
            Column(Modifier.padding(horizontal = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(tr("Finding places… ${organizing.done}/${organizing.total}", "正在识别地点… ${organizing.done}/${organizing.total}"), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                LinearProgressIndicator(
                    progress = { if (organizing.total == 0) 0f else organizing.done / organizing.total.toFloat() },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun GroupHeaderRow(header: GroupHeader, collapsed: Boolean, onToggle: () -> Unit) {
    val arrow by animateFloatAsState(if (collapsed) -90f else 0f, label = "arrow")
    Row(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .clickable(onClick = onToggle)
            .padding(
                // Deeper levels step in, up to a point, so long branches still fit.
                start = if (header.level == 0) 2.dp else (2 + 12 * header.level.coerceAtMost(4)).dp,
                top = if (header.level == 0) 10.dp else 2.dp,
                bottom = 2.dp,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.ExpandMore, contentDescription = if (collapsed) tr("Expand", "展开") else tr("Collapse", "收起"),
            modifier = Modifier.size(20.dp).rotate(arrow), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(6.dp))
        if (header.rating != null) {
            // Rating groups: a level meter in the theme color, not stars (stars belong to the cards).
            if (header.rating > 0) {
                RatingMeter(header.rating)
                Spacer(Modifier.width(10.dp))
            }
            Text(header.title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f, fill = false),
                color = if (header.rating > 0) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurfaceVariant)
        } else if (header.level == 0) {
            Text(header.title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f, fill = false))
        } else {
            Icon(if (header.key.startsWith("tag:")) Icons.Outlined.Label else Icons.Outlined.Place, null,
                Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(4.dp))
            Text(header.title, style = if (header.level == 1) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f, fill = false))
        }
        Spacer(Modifier.width(8.dp))
        Text(
            "${header.count}",
            style = NumberStyle,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .clip(CircleShape)
                .background(MaterialTheme.app.inset)
                .padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}

// ---------------------------------------------------------------------------
// Note card
// ---------------------------------------------------------------------------

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NoteCard(
    draft: NoteDraft,
    featured: Boolean,
    selecting: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onTags: () -> Unit,
    onPlace: () -> Unit,
    onRate: () -> Unit,
    onQuickRate: (Int) -> Unit,
    onStatus: (NoteStatus) -> Unit,
    onDelete: () -> Unit,
) {
    var showMenu by remember { mutableStateOf(false) }

    val variant = draft.selectedVariant
    val title = variant?.title?.takeIf { it.isNotBlank() }
        ?: draft.foodInfo.dishNames.ifBlank { tr("Untitled", "无标题") }
    val cover = draft.publishPhotoUris.firstOrNull()
    val place = draft.foodInfo.place

    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.app.tile,
        border = if (selected) BorderStroke(2.5.dp, MaterialTheme.colorScheme.primary) else BorderStroke(1.dp, MaterialTheme.app.line),
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        Column {
            Box(Modifier.fillMaxWidth().aspectRatio(if (featured) 4f / 3f else 3f / 4f)) {
                CoverPlaceholder(draft.foodInfo.restaurantName)
                if (cover != null) {
                    AsyncImage(model = cover, contentDescription = null, contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize())
                }
                // Legibility scrim for the overlaid chips.
                Box(
                    Modifier.fillMaxWidth().height(56.dp).background(
                        Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.28f), Color.Transparent))
                    )
                )
                StatusPill(draft.status, Modifier.align(Alignment.TopStart).padding(8.dp))
                if (selecting) {
                    Box(
                        Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                            .size(26.dp)
                            .clip(CircleShape)
                            .background(if (selected) MaterialTheme.colorScheme.primary else Color.Black.copy(alpha = 0.3f))
                            .border(2.dp, Color.White, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (selected) Icon(Icons.Filled.Check, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onPrimary)
                    }
                } else if (draft.photoUris.size > 1) {
                    Text(
                        "×${draft.photoUris.size}",
                        style = NumberStyle, color = Color.White,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color.Black.copy(alpha = 0.55f))
                            .padding(horizontal = 6.dp, vertical = 1.dp),
                    )
                }
            }

            Column(Modifier.padding(start = 12.dp, end = 4.dp, top = 10.dp, bottom = 4.dp)) {
                Text(title, style = if (featured) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleSmall,
                    maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(end = 8.dp))
                // Always visible; tap a star to rate without opening the note.
                CardStars(
                    rating = draft.rating,
                    onRate = if (selecting) null else onQuickRate,
                    modifier = Modifier.padding(top = 2.dp).offset(x = (-5).dp),
                )
                if (draft.tags.isNotEmpty()) {
                    Text(
                        draft.tags.joinToString(" · ") { it.name },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp, end = 8.dp),
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        listOf(draft.foodInfo.restaurantName, place.localized(LanguageStore.isZh).city.ifBlank { draft.foodInfo.location })
                            .filter { it.isNotBlank() }.joinToString(" · ")
                            .ifBlank { draft.foodInfo.mealDate.take(10) },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (!selecting) Box {
                        IconButton(onClick = { showMenu = true }, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Outlined.MoreHoriz, contentDescription = tr("Note options", "笔记选项"),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                        }
                        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                            DropdownMenuItem(text = { Text(tr("Select", "选择")) }, leadingIcon = { Icon(Icons.Outlined.CheckCircle, null) },
                                onClick = { showMenu = false; onLongClick() })
                            DropdownMenuItem(text = { Text(tr("Tags…", "标签…")) }, leadingIcon = { Icon(Icons.Outlined.Label, null) },
                                onClick = { showMenu = false; onTags() })
                            DropdownMenuItem(text = { Text(tr("Place…", "地点…")) }, leadingIcon = { Icon(Icons.Outlined.Place, null) },
                                onClick = { showMenu = false; onPlace() })
                            DropdownMenuItem(text = { Text(tr("Rate…", "评分…")) }, leadingIcon = { Icon(Icons.Outlined.StarOutline, null) },
                                onClick = { showMenu = false; onRate() })
                            HorizontalDivider()
                            StatusMenuItems { showMenu = false; onStatus(it) }
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text(tr("Delete", "删除"), color = MaterialTheme.colorScheme.error) },
                                leadingIcon = { Icon(Icons.Outlined.Delete, null, tint = MaterialTheme.colorScheme.error) },
                                onClick = { showMenu = false; onDelete() },
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Shown under the photo (and instead of it when the photo is missing). */
@Composable
private fun CoverPlaceholder(restaurant: String) {
    Box(
        Modifier.fillMaxSize().background(
            Brush.linearGradient(listOf(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.tertiaryContainer))
        ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            restaurant.trim().take(1).ifBlank { "食" },
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f),
        )
    }
}
