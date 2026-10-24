package com.xiaohan.xhsnotegen.ui.drafts

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.staggeredgrid.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.material.icons.automirrored.outlined.ViewList
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.xiaohan.xhsnotegen.domain.NoteDraft
import com.xiaohan.xhsnotegen.domain.NoteStatus
import com.xiaohan.xhsnotegen.domain.NoteSearch
import com.xiaohan.xhsnotegen.domain.NoteTag
import com.xiaohan.xhsnotegen.domain.SearchSuggestion
import com.xiaohan.xhsnotegen.domain.SearchTerm
import com.xiaohan.xhsnotegen.domain.TagFilter
import com.xiaohan.xhsnotegen.domain.Place
import com.xiaohan.xhsnotegen.domain.TagTree
import com.xiaohan.xhsnotegen.i18n.LanguageStore
import com.xiaohan.xhsnotegen.i18n.tr
import com.xiaohan.xhsnotegen.ui.components.AppFilterChip
import com.xiaohan.xhsnotegen.ui.components.ArchShape
import com.xiaohan.xhsnotegen.ui.components.CardStars
import com.xiaohan.xhsnotegen.ui.components.EmptyState
import com.xiaohan.xhsnotegen.ui.components.RatingDialog
import com.xiaohan.xhsnotegen.ui.components.RatingMeter
import com.xiaohan.xhsnotegen.ui.components.StatusPill
import com.xiaohan.xhsnotegen.ui.components.StatusSeal
import com.xiaohan.xhsnotegen.ui.components.dashedBorder
import com.xiaohan.xhsnotegen.ui.components.softFieldColors
import com.xiaohan.xhsnotegen.ui.publish.XhsAuthStore
import com.xiaohan.xhsnotegen.ui.theme.BandNumberStyle
import com.xiaohan.xhsnotegen.ui.theme.CardTitleStyle
import com.xiaohan.xhsnotegen.ui.theme.HeroTitleStyle
import com.xiaohan.xhsnotegen.ui.theme.NumberStyle
import com.xiaohan.xhsnotegen.ui.theme.ThemeBackdrop
import com.xiaohan.xhsnotegen.ui.theme.app

private val HomeLayout.label: String
    get() = when (this) {
        HomeLayout.GRID -> tr("Grid", "双列")
        HomeLayout.LIST -> tr("List", "列表")
        HomeLayout.GALLERY -> tr("Gallery", "相册")
    }

private val HomeLayout.icon: ImageVector
    get() = when (this) {
        HomeLayout.GRID -> Icons.Outlined.GridView
        HomeLayout.LIST -> Icons.AutoMirrored.Outlined.ViewList
        HomeLayout.GALLERY -> Icons.Outlined.ViewModule
    }

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
    val layout by HomePrefs.layout.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val gridState = rememberLazyStaggeredGridState()
    var showMenu by remember { mutableStateOf(false) }
    var showLayoutMenu by remember { mutableStateOf(false) }
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
    val heroGone by remember { derivedStateOf { gridState.firstVisibleItemIndex > 0 } }
    // In the grid, the first note of each group (or of the whole feed) gets a full-width tile.
    val featured = remember(state.feed, layout) {
        if (layout != HomeLayout.GRID) return@remember emptySet()
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
                    // The big title in the list says it already; this one shows once that scrolls away.
                    title = { if (heroGone || total == 0) Text(tr("Glint", "浮生拾遗"), style = MaterialTheme.typography.headlineSmall, maxLines = 1) },
                    actions = {
                        Box {
                            IconButton(onClick = { showLayoutMenu = true }) {
                                Icon(layout.icon, contentDescription = tr("Layout", "展示方式"))
                            }
                            DropdownMenu(expanded = showLayoutMenu, onDismissRequest = { showLayoutMenu = false }) {
                                HomeLayout.entries.forEach { l ->
                                    DropdownMenuItem(
                                        text = { Text(l.label) },
                                        leadingIcon = { Icon(l.icon, null) },
                                        trailingIcon = { if (l == layout) Icon(Icons.Filled.Check, null) },
                                        onClick = { showLayoutMenu = false; HomePrefs.setLayout(context, l) },
                                    )
                                }
                            }
                        }
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
                shape = CircleShape,
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        LazyVerticalStaggeredGrid(
            columns = StaggeredGridCells.Fixed(layout.columns),
            state = gridState,
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = PagePadding, end = PagePadding, top = 0.dp, bottom = 96.dp),
            verticalItemSpacing = when (layout) {
                HomeLayout.LIST -> 2.dp
                HomeLayout.GALLERY -> 8.dp
                else -> 16.dp
            },
            horizontalArrangement = Arrangement.spacedBy(if (layout == HomeLayout.GALLERY) 8.dp else 14.dp),
        ) {
            if (total > 0) {
                item(span = StaggeredGridItemSpan.FullLine, key = "hero") {
                    HomeHero(total, state.counts[DraftFilter.READY] ?: 0)
                }
                item(span = StaggeredGridItemSpan.FullLine, key = "stats") {
                    ProgressBand(state.counts, state.filter, viewModel::setFilter, Modifier.bleed(PagePadding))
                }
                item(span = StaggeredGridItemSpan.FullLine, key = "controls") {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        SearchBox(
                            state = state,
                            onAdd = viewModel::addSearchTerm,
                            onPickTag = viewModel::toggleTagFilter,
                            onRemove = viewModel::removeSearchTerm,
                            onClearAll = viewModel::clearSearch,
                        ) {
                            // Not typing: the most-used tags, one tap each.
                            if (state.tags.isNotEmpty()) {
                                TagFilterBox(
                                    state = state,
                                    onToggle = viewModel::toggleTagFilter,
                                    onClear = viewModel::clearTagFilters,
                                    onOpenAll = { dialog = HomeDialog.AllTags },
                                )
                            }
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
                                Button(onClick = onCreateClick) {
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
                        val ids = setOf(draft.id)
                        val onClick = { if (selecting) viewModel.toggleSelected(draft.id) else onDraftClick(draft.id) }
                        val onLongClick = { viewModel.toggleSelected(draft.id) }
                        when (layout) {
                            HomeLayout.GRID -> NoteCard(
                                draft = draft,
                                featured = "${item.groupKey}:${draft.id}" in featured,
                                selecting = selecting,
                                selected = draft.id in selection,
                                onClick = onClick,
                                onLongClick = onLongClick,
                                onTags = { dialog = HomeDialog.Tags(ids) },
                                onPlace = { dialog = HomeDialog.SetPlace(ids) },
                                onRate = { dialog = HomeDialog.Rate(ids) },
                                onQuickRate = { viewModel.setRating(ids, it) },
                                onStatus = { viewModel.setStatus(ids, it) },
                                onDelete = { dialog = HomeDialog.Delete(ids) },
                            )
                            HomeLayout.LIST -> NoteRow(
                                draft = draft,
                                selecting = selecting,
                                selected = draft.id in selection,
                                onClick = onClick,
                                onLongClick = onLongClick,
                                onTags = { dialog = HomeDialog.Tags(ids) },
                                onPlace = { dialog = HomeDialog.SetPlace(ids) },
                                onRate = { dialog = HomeDialog.Rate(ids) },
                                onQuickRate = { viewModel.setRating(ids, it) },
                                onStatus = { viewModel.setStatus(ids, it) },
                                onDelete = { dialog = HomeDialog.Delete(ids) },
                            )
                            HomeLayout.GALLERY -> NoteTile(
                                draft = draft,
                                selecting = selecting,
                                selected = draft.id in selection,
                                onClick = onClick,
                                onLongClick = onLongClick,
                            )
                        }
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
// Hero and band: the counts are the filter
// ---------------------------------------------------------------------------

private val PagePadding = 20.dp

/** Lets a full-line item reach the screen edges past the grid's side padding. */
private fun Modifier.bleed(padding: Dp) = layout { measurable, constraints ->
    val extra = padding.roundToPx() * 2
    val placeable = measurable.measure(constraints.copy(minWidth = constraints.maxWidth + extra, maxWidth = constraints.maxWidth + extra))
    layout(constraints.maxWidth, placeable.height) { placeable.place(-extra / 2, 0) }
}

@Composable
private fun HomeHero(total: Int, ready: Int) {
    val c = MaterialTheme.colorScheme
    val accent = MaterialTheme.app.accent2
    Column(Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 2.dp)) {
        Text(
            buildAnnotatedString {
                if (LanguageStore.isZh) {
                    append("浮生")
                    withStyle(SpanStyle(color = accent)) { append("拾遗") }
                } else append("Glint")
            },
            style = HeroTitleStyle, color = c.primary,
        )
        Text(tr("$total notes · $ready ready to post", "$total 篇 · $ready 篇待发布"),
            style = MaterialTheme.typography.bodyMedium, color = c.onSurfaceVariant)
    }
}

/**
 * The deep band under the title: a ring for all notes (gold arc = posted, light arc =
 * ready, bare track = drafts) and a list of the three statuses. Tapping any of them filters.
 */
@Composable
private fun ProgressBand(counts: Map<DraftFilter, Int>, selected: DraftFilter, onSelect: (DraftFilter) -> Unit, modifier: Modifier = Modifier) {
    val total = counts[DraftFilter.ALL] ?: 0
    val posted = counts[DraftFilter.SHARED] ?: 0
    val ready = counts[DraftFilter.READY] ?: 0
    val drafts = counts[DraftFilter.DRAFTS] ?: 0
    val a = MaterialTheme.app
    val readyInk = a.onBand.copy(alpha = 0.7f)
    Box(modifier.fillMaxWidth().background(a.band)) {
        // The theme's art (Glint ripples, summer sky) sits around the ring, never under the list.
        ThemeBackdrop(Modifier.matchParentSize(), inBand = true)
        Row(
            Modifier.padding(horizontal = PagePadding, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            ProgressRing(total, posted, ready, selected == DraftFilter.ALL) { onSelect(DraftFilter.ALL) }
            Column(Modifier.weight(1f)) {
                BandRow(tr("Ready", "待发布"), ready, readyInk, selected == DraftFilter.READY, first = true) { onSelect(DraftFilter.READY) }
                BandRow(tr("Drafts", "草稿"), drafts, a.onBand.copy(alpha = 0.3f), selected == DraftFilter.DRAFTS) { onSelect(DraftFilter.DRAFTS) }
                BandRow(tr("Posted", "已发布"), posted, a.bandGold, selected == DraftFilter.SHARED) { onSelect(DraftFilter.SHARED) }
            }
        }
    }
}

@Composable
private fun ProgressRing(total: Int, posted: Int, ready: Int, selected: Boolean, onClick: () -> Unit) {
    val a = MaterialTheme.app
    Box(
        Modifier
            .size(108.dp)
            .clip(CircleShape)
            .selectable(selected = selected, role = Role.Tab, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.matchParentSize()) {
            val stroke = 7.dp.toPx()
            val inset = 4.dp.toPx() + stroke / 2   // room for the "selected" outline
            val arcSize = androidx.compose.ui.geometry.Size(size.width - inset * 2, size.height - inset * 2)
            val topLeft = androidx.compose.ui.geometry.Offset(inset, inset)
            // A solid disc so the theme art never shows behind the count.
            drawCircle(a.band, radius = arcSize.width / 2 + stroke / 2)
            drawArc(a.onBand.copy(alpha = 0.18f), 0f, 360f, false, topLeft, arcSize, style = Stroke(stroke))
            if (total > 0) {
                val gap = if (posted > 0 && ready > 0) 3f else 0f
                val postedSweep = 360f * posted / total
                val readySweep = 360f * ready / total
                if (posted > 0) drawArc(a.bandGold, -90f, postedSweep - gap, false, topLeft, arcSize, style = Stroke(stroke))
                if (ready > 0) drawArc(a.onBand.copy(alpha = 0.7f), -90f + postedSweep + gap / 2, readySweep - gap, false, topLeft, arcSize, style = Stroke(stroke))
            }
            if (selected) drawCircle(a.onBand, radius = size.width / 2 - 0.75.dp.toPx(), style = Stroke(1.5.dp.toPx()))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("$total", style = BandNumberStyle.copy(fontSize = 30.sp, lineHeight = 32.sp), color = a.onBand)
            Text(tr("All notes", "全部笔记"), style = MaterialTheme.typography.labelSmall, color = a.onBand)
        }
    }
}

@Composable
private fun BandRow(label: String, n: Int, dot: Color, selected: Boolean, first: Boolean = false, onClick: () -> Unit) {
    val a = MaterialTheme.app
    Column {
        if (!first) HorizontalDivider(color = a.onBand.copy(alpha = 0.25f))
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 40.dp)
                .selectable(selected = selected, role = Role.Tab, onClick = onClick)
                .then(if (selected) Modifier.background(a.onBand.copy(alpha = 0.10f)) else Modifier)
                .padding(start = if (selected) 10.dp else 2.dp, end = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(dot))
            Spacer(Modifier.width(8.dp))
            Text(label, style = MaterialTheme.typography.labelLarge,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                color = a.onBand, modifier = Modifier.weight(1f), maxLines = 1)
            Text("$n", style = BandNumberStyle, color = a.onBand)
        }
    }
}

// ---------------------------------------------------------------------------
// Search: one box for places, tags, modes, ratings and words; picks stack up
// ---------------------------------------------------------------------------

private val SearchSuggestion.Kind.icon: ImageVector
    get() = when (this) {
        SearchSuggestion.Kind.PLACE -> Icons.Outlined.Place
        SearchSuggestion.Kind.TAG -> Icons.AutoMirrored.Outlined.Label
        SearchSuggestion.Kind.MODE -> Icons.Outlined.Category
        SearchSuggestion.Kind.RATING -> Icons.Outlined.StarOutline
        SearchSuggestion.Kind.KEYWORD -> Icons.Outlined.TextFields
    }

private val SearchSuggestion.Kind.label: String
    get() = when (this) {
        SearchSuggestion.Kind.PLACE -> tr("Place", "地点")
        SearchSuggestion.Kind.TAG -> tr("Tag", "标签")
        SearchSuggestion.Kind.MODE -> tr("Mode", "模式")
        SearchSuggestion.Kind.RATING -> tr("Rating", "评分")
        SearchSuggestion.Kind.KEYWORD -> tr("Text", "文字")
    }

/**
 * Type to get suggestions by kind; picking one adds it as a chip. Same kind = or,
 * different kinds = and (see [NoteSearch]). While not typing, [idle] shows (the tag row).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SearchBox(
    state: DraftListState,
    onAdd: (SearchTerm) -> Unit,
    onPickTag: (Long) -> Unit,
    onRemove: (SearchTerm) -> Unit,
    onClearAll: () -> Unit,
    idle: @Composable () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val zh = LanguageStore.isZh
    val suggestions = remember(query, state.all, state.tags, state.tagCounts, state.modeNames, zh) {
        NoteSearch.suggest(query, state.all, state.tags, state.tagCounts,
            modeName = { state.modeNames[it] ?: it }, localize = { it.localized(zh) })
    }
    fun pick(s: SearchSuggestion) {
        s.term?.let(onAdd)
        s.tagId?.let { id -> if (id !in state.tagFilters) onPickTag(id) }
        query = ""
    }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        TextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(tr("Search places, tags, modes, 4★ or words", "搜索地点、标签、模式、4星或文字"), maxLines = 1, overflow = TextOverflow.Ellipsis) },
            leadingIcon = { Icon(Icons.Outlined.Search, null) },
            trailingIcon = {
                if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Filled.Close, tr("Clear", "清除")) }
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            // Enter searches the words as typed.
            keyboardActions = KeyboardActions(onSearch = { suggestions.lastOrNull()?.let(::pick) }),
            colors = softFieldColors(),
        )

        if (query.isNotBlank()) {
            Column {
                suggestions.forEach { s ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.small)
                            .clickable { pick(s) }
                            .padding(horizontal = 4.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(s.kind.icon, null, Modifier.size(18.dp), tint = MaterialTheme.app.accent2)
                        Spacer(Modifier.width(8.dp))
                        Text(s.kind.label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.width(40.dp))
                        Text(
                            if (s.kind == SearchSuggestion.Kind.KEYWORD) tr("“${s.label}” in the text", "正文里有「${s.label}」") else s.label,
                            style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Text("${s.count}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        // What is filtering right now: search conditions and picked tags, each removable.
        val pickedTags = TagFilter.active(state.tagFilters, state.tags).mapNotNull { id -> state.tags.firstOrNull { it.id == id } }
        if (state.search.isNotEmpty() || pickedTags.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                state.search.forEach { t ->
                    val (icon, label) = when (t) {
                        is SearchTerm.InPlace -> Icons.Outlined.Place to t.label
                        is SearchTerm.Mode -> Icons.Outlined.Category to (state.modeNames[t.key] ?: t.key)
                        is SearchTerm.MinRating -> Icons.Outlined.StarOutline to "★".repeat(t.stars) + if (t.stars < 5) "+" else ""
                        is SearchTerm.Keyword -> Icons.Outlined.TextFields to "“${t.text}”"
                    }
                    ConditionChip(icon, label) { onRemove(t) }
                }
                pickedTags.forEach { t -> ConditionChip(Icons.AutoMirrored.Outlined.Label, t.name) { onPickTag(t.id) } }
                TextButton(onClick = onClearAll) { Text(tr("Clear all", "全部清除")) }
            }
        }

        if (query.isBlank()) idle()
    }
}

@Composable
private fun ConditionChip(icon: ImageVector, label: String, onRemove: () -> Unit) {
    InputChip(
        selected = true,
        onClick = onRemove,
        label = { Text(label, maxLines = 1) },
        leadingIcon = { Icon(icon, null, Modifier.size(16.dp)) },
        trailingIcon = { Icon(Icons.Filled.Close, tr("Remove", "移除"), Modifier.size(16.dp)) },
        shape = CircleShape,
    )
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
                .clip(MaterialTheme.shapes.medium)
                .background(MaterialTheme.app.tile2)
                .padding(start = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LazyRow(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                items(path, key = { "p${it.id}" }) { p ->
                    Text("${p.name} ›", style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                Icon(Icons.Filled.Close, tr("Close", "收起"), tint = MaterialTheme.colorScheme.onSurfaceVariant)
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
            .dashedBorder(MaterialTheme.app.line2, 16.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(tr("All $count ›", "全部 $count 个 ›"), style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
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
            Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth().height(48.dp)) {
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
        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(tr("Group", "分组"), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterVertically).padding(end = 6.dp))
            state.groupOrder.forEach { g -> GroupTab(g.label, g == state.groupBy) { onGroupBy(g) } }
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

/** A word you tap; the chosen one turns serif with an accent rule underneath. */
@Composable
private fun GroupTab(label: String, selected: Boolean, onClick: () -> Unit) {
    val accent = MaterialTheme.app.accent2
    Box(
        Modifier
            .heightIn(min = 40.dp)
            .clip(RoundedCornerShape(8.dp))
            .selectable(selected = selected, role = Role.Tab, onClick = onClick)
            .padding(horizontal = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = if (selected) MaterialTheme.typography.titleMedium.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Serif, fontWeight = FontWeight.Normal)
            else MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Normal),
            color = if (selected) MaterialTheme.app.head else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = if (selected) Modifier.drawUnderline(accent) else Modifier,
        )
    }
}

private fun Modifier.drawUnderline(color: Color) = this.then(
    Modifier.drawBehind { drawLine(color, androidx.compose.ui.geometry.Offset(0f, size.height), androidx.compose.ui.geometry.Offset(size.width, size.height), strokeWidth = 1.5.dp.toPx()) }
)

@Composable
private fun GroupHeaderRow(header: GroupHeader, collapsed: Boolean, onToggle: () -> Unit) {
    val arrow by animateFloatAsState(if (collapsed) -90f else 0f, label = "arrow")
    val c = MaterialTheme.colorScheme
    val top = header.level == 0
    Column(Modifier.fillMaxWidth().padding(top = if (top) 12.dp else 0.dp)) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.small)
                .clickable(onClick = onToggle)
                .padding(
                    // Deeper levels step in, up to a point, so long branches still fit.
                    start = if (top) 0.dp else (12 * header.level.coerceAtMost(4)).dp,
                    top = 4.dp, bottom = 6.dp,
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (header.rating != null) {
                // Rating groups: a level meter in the theme color, not stars (stars belong to the cards).
                if (header.rating > 0) {
                    RatingMeter(header.rating)
                    Spacer(Modifier.width(10.dp))
                }
                Text(header.title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f, fill = false),
                    color = if (header.rating > 0) MaterialTheme.app.head else c.onSurfaceVariant)
            } else if (top) {
                Text(header.title, style = MaterialTheme.typography.headlineSmall.copy(fontSize = 24.sp, lineHeight = 30.sp),
                    color = MaterialTheme.app.head, modifier = Modifier.weight(1f, fill = false))
            } else {
                Icon(if (header.key.startsWith("tag:")) Icons.Outlined.Label else Icons.Outlined.Place, null,
                    Modifier.size(16.dp), tint = MaterialTheme.app.accent2)
                Spacer(Modifier.width(4.dp))
                Text(header.title, style = (if (header.level == 1) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleSmall)
                    .copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Serif, fontWeight = FontWeight.Normal),
                    color = MaterialTheme.app.head, modifier = Modifier.weight(1f, fill = false))
            }
            Spacer(Modifier.width(10.dp))
            Text(tr("${header.count}", "${header.count} 篇"), style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant)
            Spacer(Modifier.weight(1f))
            Icon(Icons.Outlined.ExpandMore, contentDescription = if (collapsed) tr("Expand", "展开") else tr("Collapse", "收起"),
                modifier = Modifier.size(22.dp).rotate(arrow), tint = c.onSurfaceVariant)
        }
        if (top) HorizontalDivider(color = MaterialTheme.app.line2)
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
    val variant = draft.selectedVariant
    val title = variant?.title?.takeIf { it.isNotBlank() }
        ?: draft.foodInfo.dishNames.ifBlank { tr("Untitled", "无标题") }
    val cover = draft.publishPhotoUris.firstOrNull()
    val place = draft.foodInfo.place

    val arch = remember { ArchShape() }
    val page = MaterialTheme.colorScheme.background
    // Not clipped: the seal hangs off the cover's corner.
    Column(
        Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        Box(Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(if (featured) 16f / 10f else 3f / 4f)
                    .clip(arch)
                    .then(if (selected) Modifier.border(3.dp, MaterialTheme.colorScheme.primary, arch) else Modifier),
            ) {
                CoverPlaceholder(draft.foodInfo.restaurantName)
                if (cover != null) {
                    AsyncImage(model = cover, contentDescription = null, contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize())
                }
                if (selecting) {
                    SelectMark(selected, Modifier.align(Alignment.BottomStart).padding(10.dp))
                } else if (draft.photoUris.size > 1) {
                    Row(
                        Modifier
                            .align(Alignment.BottomStart)
                            .padding(10.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.48f))
                            .padding(horizontal = 7.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Outlined.PhotoLibrary, null, Modifier.size(13.dp), tint = Color.White)
                        Spacer(Modifier.width(3.dp))
                        Text("${draft.photoUris.size}", style = MaterialTheme.typography.labelSmall, color = Color.White)
                    }
                }
            }
            StatusSeal(draft.status, ringColor = page, modifier = Modifier.align(Alignment.BottomEnd).offset(x = 6.dp, y = 6.dp))
        }

        Column {
            Column(Modifier.padding(start = 2.dp, top = 6.dp)) {
                Text(title, style = if (featured) CardTitleStyle.copy(fontSize = 19.sp, lineHeight = 26.sp) else CardTitleStyle,
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
                        color = MaterialTheme.app.head,
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
                    if (!selecting) NoteMenuButton(onSelect = onLongClick, onTags, onPlace, onRate, onStatus, onDelete)
                }
            }
        }
    }
}

/** The "…" button on a note and its menu. */
@Composable
private fun NoteMenuButton(
    onSelect: () -> Unit,
    onTags: () -> Unit,
    onPlace: () -> Unit,
    onRate: () -> Unit,
    onStatus: (NoteStatus) -> Unit,
    onDelete: () -> Unit,
) {
    var showMenu by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { showMenu = true }, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Outlined.MoreHoriz, contentDescription = tr("Note options", "笔记选项"),
                tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
        }
        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
            DropdownMenuItem(text = { Text(tr("Select", "选择")) }, leadingIcon = { Icon(Icons.Outlined.CheckCircle, null) },
                onClick = { showMenu = false; onSelect() })
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

/** The tick shown on a cover while selecting notes. */
@Composable
private fun SelectMark(selected: Boolean, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(26.dp)
            .clip(CircleShape)
            .background(if (selected) MaterialTheme.colorScheme.primary else Color.Black.copy(alpha = 0.3f))
            .border(2.dp, Color.White, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) Icon(Icons.Filled.Check, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onPrimary)
    }
}

/** List layout: small cover on the left, the facts beside it. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NoteRow(
    draft: NoteDraft,
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
    val title = draft.selectedVariant?.title?.takeIf { it.isNotBlank() }
        ?: draft.foodInfo.dishNames.ifBlank { tr("Untitled", "无标题") }
    val shape = RoundedCornerShape(10.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(72.dp)
                .clip(shape)
                .then(if (selected) Modifier.border(3.dp, MaterialTheme.colorScheme.primary, shape) else Modifier),
        ) {
            CoverPlaceholder(draft.foodInfo.restaurantName)
            draft.publishPhotoUris.firstOrNull()?.let {
                AsyncImage(model = it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
            if (selecting) SelectMark(selected, Modifier.align(Alignment.Center))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = CardTitleStyle, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                listOf(
                    draft.foodInfo.restaurantName,
                    draft.foodInfo.place.localized(LanguageStore.isZh).city.ifBlank { draft.foodInfo.location },
                    draft.foodInfo.mealDate.take(10),
                ).filter { it.isNotBlank() }.joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                CardStars(rating = draft.rating, onRate = if (selecting) null else onQuickRate, modifier = Modifier.offset(x = (-5).dp))
                Spacer(Modifier.weight(1f))
                StatusPill(draft.status)
            }
        }
        if (!selecting) NoteMenuButton(onSelect = onLongClick, onTags, onPlace, onRate, onStatus, onDelete)
    }
}

/** Gallery layout: the cover alone, with its status. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NoteTile(
    draft: NoteDraft,
    selecting: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val arch = remember { ArchShape(10.dp) }
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(3f / 4f)
            .clip(arch)
            .then(if (selected) Modifier.border(3.dp, MaterialTheme.colorScheme.primary, arch) else Modifier)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        CoverPlaceholder(draft.foodInfo.restaurantName)
        draft.publishPhotoUris.firstOrNull()?.let {
            AsyncImage(model = it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
        // The arch cuts the top away; marks stay at the bottom.
        if (selecting) SelectMark(selected, Modifier.align(Alignment.BottomStart).padding(6.dp))
        else StatusPill(draft.status, Modifier.align(Alignment.BottomStart).padding(6.dp))
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
