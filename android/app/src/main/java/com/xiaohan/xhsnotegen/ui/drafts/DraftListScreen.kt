package com.xiaohan.xhsnotegen.ui.drafts

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.staggeredgrid.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.xiaohan.xhsnotegen.ui.components.EmptyState
import com.xiaohan.xhsnotegen.ui.components.StatusPill
import com.xiaohan.xhsnotegen.ui.publish.XhsAuthStore
import com.xiaohan.xhsnotegen.ui.theme.Backdrop
import com.xiaohan.xhsnotegen.ui.theme.LocalAppTheme
import com.xiaohan.xhsnotegen.ui.theme.ThemeBackdrop

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DraftListScreen(
    onCreateClick: () -> Unit,
    onDraftClick: (Long) -> Unit,
    onOpenSettings: () -> Unit = {},
    viewModel: DraftListViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsState()
    val loggedIn by XhsAuthStore.loggedIn.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val gridState = rememberLazyStaggeredGridState()
    var showMenu by remember { mutableStateOf(false) }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { viewModel.importFromUri(it) }
    }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let { viewModel.exportToUri(it) }
    }

    LaunchedEffect(Unit) {
        viewModel.messages.collect { snackbarHostState.showSnackbar(it) }
    }

    val total = state.counts[DraftFilter.ALL] ?: 0
    val ready = state.counts[DraftFilter.READY] ?: 0

    val hasBackdrop = LocalAppTheme.current.backdrop != Backdrop.NONE

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        // Anime-inspired themes paint art behind the header; it fades as the header collapses.
        ThemeBackdrop(
            Modifier
                .fillMaxWidth()
                .height(320.dp)
                .graphicsLayer { alpha = 1f - scrollBehavior.state.collapsedFraction },
        )
        Scaffold(
            modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
            topBar = {
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
                                    text = { Text("Settings") },
                                    leadingIcon = { Icon(Icons.Outlined.Settings, null) },
                                    onClick = { showMenu = false; onOpenSettings() },
                                )
                                HorizontalDivider()
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
            },
            floatingActionButton = {
                // The empty state has its own call to action; one is enough.
                if (total > 0) ExtendedFloatingActionButton(
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
                    item(span = StaggeredGridItemSpan.FullLine, key = "filters") {
                        FilterRow(state.filter, state.counts, viewModel::setFilter)
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
                                body = "No notes match “${state.filter.label}”.",
                            )
                        }
                    }
                }

                items(state.drafts, key = { it.id }) { draft ->
                    NoteCard(
                        draft = draft,
                        onClick = { onDraftClick(draft.id) },
                        onDelete = { viewModel.deleteDraft(draft) },
                    )
                }
            }
        }
    }
}

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
        modifier = Modifier.padding(bottom = 4.dp),
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NoteCard(
    draft: NoteDraft,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    var showMenu by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    val variant = draft.selectedVariant
    val title = variant?.title?.takeIf { it.isNotBlank() }
        ?: draft.foodInfo.dishNames.ifBlank { "Untitled meal" }
    val cover = draft.publishPhotoUris.firstOrNull()

    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .combinedClickable(onClick = onClick, onLongClick = { showMenu = true }),
    ) {
        Column {
            Box(Modifier.fillMaxWidth().aspectRatio(3f / 4f)) {
                CoverPlaceholder(draft.foodInfo.restaurantName)
                if (cover != null) {
                    AsyncImage(
                        model = cover,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                // Legibility scrim for the overlaid chips.
                Box(
                    Modifier.fillMaxWidth().height(56.dp).background(
                        Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.28f), Color.Transparent))
                    )
                )
                StatusPill(draft.status, Modifier.align(Alignment.TopStart).padding(8.dp))
                if (draft.photoUris.size > 1) {
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
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(end = 8.dp),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        listOf(draft.foodInfo.restaurantName, draft.foodInfo.location)
                            .filter { it.isNotBlank() }.joinToString(" · ")
                            .ifBlank { draft.foodInfo.mealDate.take(10) },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Box {
                        IconButton(onClick = { showMenu = true }, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Outlined.MoreHoriz, contentDescription = "Note options",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                        }
                        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                            DropdownMenuItem(
                                text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                                leadingIcon = { Icon(Icons.Outlined.Delete, null, tint = MaterialTheme.colorScheme.error) },
                                onClick = { showMenu = false; confirmDelete = true },
                            )
                        }
                    }
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            icon = { Icon(Icons.Outlined.Delete, null) },
            title = { Text("Delete this note?") },
            text = {
                Text(
                    if (draft.status == NoteStatus.SHARED) "It stays on Xiaohongshu — only the copy in this app and its photos are removed."
                    else "The note and its photos will be removed from this phone."
                )
            },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; onDelete() }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
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
