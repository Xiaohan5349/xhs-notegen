package com.xiaohan.xhsnotegen.ui.drafts

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.staggeredgrid.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.xiaohan.xhsnotegen.domain.NoteDraft
import com.xiaohan.xhsnotegen.domain.NoteStatus
import com.xiaohan.xhsnotegen.domain.Place
import com.xiaohan.xhsnotegen.ui.components.EmptyState
import com.xiaohan.xhsnotegen.ui.components.CardStars
import com.xiaohan.xhsnotegen.ui.components.RatingDialog
import com.xiaohan.xhsnotegen.ui.components.StatusPill
import com.xiaohan.xhsnotegen.ui.publish.XhsAuthStore
import com.xiaohan.xhsnotegen.ui.theme.Backdrop
import com.xiaohan.xhsnotegen.ui.theme.LocalAppTheme
import com.xiaohan.xhsnotegen.ui.theme.ThemeBackdrop

/** Which dialog is open, and for which notes. */
private sealed interface HomeDialog {
    data class Tags(val ids: Set<Long>) : HomeDialog
    data class SetPlace(val ids: Set<Long>) : HomeDialog
    data class Delete(val ids: Set<Long>) : HomeDialog
    data class Rate(val ids: Set<Long>) : HomeDialog
    data object ManageTags : HomeDialog
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
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
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
    val ready = state.counts[DraftFilter.READY] ?: 0
    val hasBackdrop = LocalAppTheme.current.backdrop != Backdrop.NONE && !selecting

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        // Anime-inspired themes paint art behind the header; it fades as the header collapses.
        ThemeBackdrop(
            Modifier
                .fillMaxWidth()
                .height(320.dp)
                .graphicsLayer { alpha = if (selecting) 0f else 1f - scrollBehavior.state.collapsedFraction },
        )
        Scaffold(
            modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
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
                    LargeTopAppBar(
                        title = {
                            Column {
                                Text("Food diary", maxLines = 1)
                                if (total > 0 && scrollBehavior.state.collapsedFraction < 0.5f) {
                                    Text(
                                        buildString {
                                            append("$total ${if (total == 1) "note" else "notes"}")
                                            if (ready > 0) append(" · $ready ready to post")
                                        },
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        },
                        actions = {
                            IconButton(onClick = onOpenSettings) {
                                BadgedBox(badge = {
                                    if (loggedIn) Badge(containerColor = MaterialTheme.colorScheme.secondary)
                                }) {
                                    Icon(Icons.Outlined.AccountCircle,
                                        contentDescription = if (loggedIn) "Xiaohongshu connected" else "Account")
                                }
                            }
                            Box {
                                IconButton(onClick = { showMenu = true }) {
                                    Icon(Icons.Outlined.MoreVert, contentDescription = "More")
                                }
                                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                                    DropdownMenuItem(
                                        text = { Text("Organize by place") },
                                        leadingIcon = { Icon(Icons.Outlined.TravelExplore, null) },
                                        onClick = { showMenu = false; viewModel.organize() },
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Re-check all places") },
                                        leadingIcon = { Icon(Icons.Outlined.Refresh, null) },
                                        onClick = { showMenu = false; viewModel.organize(redo = true) },
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Manage tags") },
                                        leadingIcon = { Icon(Icons.Outlined.Label, null) },
                                        onClick = { showMenu = false; dialog = HomeDialog.ManageTags },
                                    )
                                    HorizontalDivider()
                                    DropdownMenuItem(
                                        text = { Text("Settings") },
                                        leadingIcon = { Icon(Icons.Outlined.Settings, null) },
                                        onClick = { showMenu = false; onOpenSettings() },
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Import backup") },
                                        leadingIcon = { Icon(Icons.Outlined.FileOpen, null) },
                                        onClick = { showMenu = false; importLauncher.launch(arrayOf("application/json")) },
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Export backup") },
                                        leadingIcon = { Icon(Icons.Outlined.SaveAlt, null) },
                                        onClick = { showMenu = false; exportLauncher.launch("xhs_notes_backup.json") },
                                    )
                                }
                            }
                        },
                        scrollBehavior = scrollBehavior,
                        colors = TopAppBarDefaults.largeTopAppBarColors(
                            containerColor = if (hasBackdrop) Color.Transparent else MaterialTheme.colorScheme.background,
                            scrolledContainerColor = MaterialTheme.colorScheme.background,
                        ),
                    )
                }
            },
            floatingActionButton = {
                // The empty state has its own call to action; one is enough.
                if (total > 0 && !selecting) ExtendedFloatingActionButton(
                    onClick = onCreateClick,
                    expanded = !gridState.canScrollBackward,
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    text = { Text("New note") },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                )
            },
            snackbarHost = { SnackbarHost(snackbarHostState) },
            containerColor = Color.Transparent,
            // A transparent container gives no content color, so text would fall back
            // to black — invisible in dark themes. Use the theme's text color.
            contentColor = MaterialTheme.colorScheme.onBackground,
        ) { padding ->
            LazyVerticalStaggeredGrid(
                columns = StaggeredGridCells.Fixed(2),
                state = gridState,
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 96.dp),
                verticalItemSpacing = 12.dp,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (total > 0) {
                    item(span = StaggeredGridItemSpan.FullLine, key = "controls") {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            FilterRow(state.filter, state.counts, viewModel::setFilter)
                            if (state.tags.isNotEmpty()) TagFilterRow(state, viewModel::setTagFilter)
                            GroupRow(state, organizing, onGroupBy = viewModel::toggleGroupBy, onOrganize = { viewModel.organize() })
                        }
                    }
                }

                if (state.loaded && state.drafts.isEmpty()) {
                    item(span = StaggeredGridItemSpan.FullLine, key = "empty") {
                        if (total == 0) {
                            EmptyState(
                                icon = Icons.Outlined.RamenDining,
                                title = "Your food diary is empty",
                                body = "Snap a meal, jot down a few words, and get a note that sounds like you.",
                                action = {
                                    Button(onClick = onCreateClick) {
                                        Icon(Icons.Filled.Add, null, Modifier.size(18.dp))
                                        Spacer(Modifier.width(8.dp))
                                        Text("Write your first note")
                                    }
                                },
                            )
                        } else {
                            EmptyState(
                                icon = Icons.Outlined.FilterList,
                                title = "Nothing here yet",
                                body = "No notes match these filters.",
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
                    span = { item -> if (item is FeedItem.Header) StaggeredGridItemSpan.FullLine else StaggeredGridItemSpan.SingleLane },
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
            defaultParent = viewModel.commonRootTag(d.ids),
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
        title = { Text("$count selected") },
        navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.Filled.Close, "Cancel selection") } },
        actions = {
            if (!allSelected) IconButton(onClick = onSelectAll) { Icon(Icons.Outlined.SelectAll, "Select all") }
            IconButton(onClick = onTags) { Icon(Icons.Outlined.Label, "Tags") }
            IconButton(onClick = onRate) { Icon(Icons.Outlined.StarOutline, "Rate") }
            IconButton(onClick = onDelete) { Icon(Icons.Outlined.Delete, "Delete") }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, "More") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    StatusMenuItems { menu = false; onStatus(it) }
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text("Set place…") },
                        leadingIcon = { Icon(Icons.Outlined.Place, null) },
                        onClick = { menu = false; onPlace() },
                    )
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            titleContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            navigationIconContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            actionIconContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ),
    )
}

@Composable
private fun StatusMenuItems(onPick: (NoteStatus) -> Unit) {
    DropdownMenuItem(text = { Text("Mark as draft") }, leadingIcon = { Icon(Icons.Outlined.EditNote, null) },
        onClick = { onPick(NoteStatus.GENERATED) })
    DropdownMenuItem(text = { Text("Mark as ready") }, leadingIcon = { Icon(Icons.Outlined.TaskAlt, null) },
        onClick = { onPick(NoteStatus.REVIEWED) })
    DropdownMenuItem(text = { Text("Mark as posted") }, leadingIcon = { Icon(Icons.Outlined.Send, null) },
        onClick = { onPick(NoteStatus.SHARED) })
}

// ---------------------------------------------------------------------------
// Filters, tags, grouping
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterRow(
    selected: DraftFilter,
    counts: Map<DraftFilter, Int>,
    onSelect: (DraftFilter) -> Unit,
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 4.dp),
    ) {
        items(DraftFilter.entries) { filter ->
            val count = counts[filter] ?: 0
            FilterChip(
                selected = filter == selected,
                onClick = { onSelect(filter) },
                label = { Text(if (count > 0) "${filter.label}  $count" else filter.label) },
                shape = CircleShape,
                colors = FilterChipDefaults.filterChipColors(
                    // Solid so chips stay legible over a theme backdrop.
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                    selectedContainerColor = MaterialTheme.colorScheme.onSurface,
                    selectedLabelColor = MaterialTheme.colorScheme.surface,
                ),
                border = if (filter == selected) null else FilterChipDefaults.filterChipBorder(
                    enabled = true, selected = false,
                    borderColor = MaterialTheme.colorScheme.outlineVariant,
                ),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TagFilterRow(state: DraftListState, onSelect: (Long) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(horizontal = 4.dp)) {
        // Root tags always; a root's sub-tags appear once it (or one of them) is selected.
        val selected = state.tags.firstOrNull { it.id == state.tagFilter }
        val openRoot = selected?.parentId ?: selected?.id
        val shown = state.tags.filter { it.parentId == null || it.parentId == openRoot }
            .sortedWith(compareBy({ (it.parentId ?: it.id) != openRoot }, { it.parentId != null }, { it.name.lowercase() }))
        items(shown, key = { it.id }) { tag ->
            FilterChip(
                selected = state.tagFilter == tag.id,
                onClick = { onSelect(tag.id) },
                label = { Text("#${tag.name}") },
                shape = CircleShape,
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
            )
        }
    }
}

@Composable
private fun GroupRow(
    state: DraftListState,
    organizing: OrganizeProgress?,
    onGroupBy: (GroupBy) -> Unit,
    onOrganize: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Group", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(8.dp))
            GroupBy.entries.filter { it != GroupBy.NONE }.forEach { g ->
                val on = g == state.groupBy
                Text(
                    g.label,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (on) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(if (on) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                        .clickable { onGroupBy(g) }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
            Spacer(Modifier.weight(1f))
            if (state.groupBy == GroupBy.PLACE && state.unplacedCount > 0 && organizing == null) {
                TextButton(onClick = onOrganize) {
                    Icon(Icons.Outlined.TravelExplore, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Organize ${state.unplacedCount}")
                }
            }
        }
        if (organizing != null) {
            Column(Modifier.padding(horizontal = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Finding places… ${organizing.done}/${organizing.total}", style = MaterialTheme.typography.bodySmall,
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
                start = if (header.level == 0) 4.dp else 16.dp,
                top = if (header.level == 0) 12.dp else 2.dp,
                bottom = 2.dp,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.ExpandMore, contentDescription = if (collapsed) "Expand" else "Collapse",
            modifier = Modifier.size(20.dp).rotate(arrow), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(6.dp))
        if (header.level == 0) {
            Text(header.title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f, fill = false))
        } else {
            Icon(if (header.key.startsWith("tag:")) Icons.Outlined.Label else Icons.Outlined.Place, null,
                Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(4.dp))
            Text(header.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f, fill = false))
        }
        Spacer(Modifier.width(8.dp))
        Text(
            "${header.count}",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
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
        ?: draft.foodInfo.dishNames.ifBlank { "Untitled meal" }
    val cover = draft.publishPhotoUris.firstOrNull()
    val place = draft.foodInfo.place

    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .then(if (selected) Modifier.border(3.dp, MaterialTheme.colorScheme.primary, MaterialTheme.shapes.medium) else Modifier)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        Column {
            Box(Modifier.fillMaxWidth().aspectRatio(3f / 4f)) {
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
                    Row(
                        Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.45f))
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(Icons.Outlined.PhotoLibrary, null, Modifier.size(12.dp), tint = Color.White)
                        Text("${draft.photoUris.size}", style = MaterialTheme.typography.labelSmall, color = Color.White)
                    }
                }
            }

            Column(Modifier.padding(start = 12.dp, end = 4.dp, top = 10.dp, bottom = 4.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 2,
                    overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(end = 8.dp))
                // Always visible; tap a star to rate without opening the note.
                CardStars(
                    rating = draft.rating,
                    onRate = if (selecting) null else onQuickRate,
                    modifier = Modifier.padding(top = 2.dp).offset(x = (-5).dp),
                )
                if (draft.tags.isNotEmpty()) {
                    Text(
                        draft.tags.joinToString(" ") { "#${it.name}" },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp, end = 8.dp),
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        listOf(draft.foodInfo.restaurantName, place.city.ifBlank { draft.foodInfo.location })
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
                            Icon(Icons.Outlined.MoreHoriz, contentDescription = "Note options",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                        }
                        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                            DropdownMenuItem(text = { Text("Select") }, leadingIcon = { Icon(Icons.Outlined.CheckCircle, null) },
                                onClick = { showMenu = false; onLongClick() })
                            DropdownMenuItem(text = { Text("Tags…") }, leadingIcon = { Icon(Icons.Outlined.Label, null) },
                                onClick = { showMenu = false; onTags() })
                            DropdownMenuItem(text = { Text("Place…") }, leadingIcon = { Icon(Icons.Outlined.Place, null) },
                                onClick = { showMenu = false; onPlace() })
                            DropdownMenuItem(text = { Text("Rate…") }, leadingIcon = { Icon(Icons.Outlined.StarOutline, null) },
                                onClick = { showMenu = false; onRate() })
                            HorizontalDivider()
                            StatusMenuItems { showMenu = false; onStatus(it) }
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
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
