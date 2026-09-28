package com.xiaohan.xhsnotegen.ui.review

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.xiaohan.xhsnotegen.domain.NoteDraft
import com.xiaohan.xhsnotegen.domain.NoteStatus
import com.xiaohan.xhsnotegen.domain.NoteStyle
import com.xiaohan.xhsnotegen.domain.NoteVariant
import com.xiaohan.xhsnotegen.ui.components.EmptyState
import com.xiaohan.xhsnotegen.ui.components.Eyebrow
import com.xiaohan.xhsnotegen.ui.components.ModelChip
import com.xiaohan.xhsnotegen.ui.components.StatusPill
import com.xiaohan.xhsnotegen.ui.publish.XiaohongshuSharePublisher.TITLE_LIMIT

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewScreen(
    draftId: Long,
    onNavigateBack: () -> Unit,
    onNavigateToLogin: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    viewModel: ReviewViewModel = viewModel(),
) {
    val draft by viewModel.draft.collectAsState()
    val aiTask by viewModel.aiTask.collectAsState()
    val isPublishing by viewModel.isPublishing.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    var loginPrompt by remember { mutableStateOf<ReviewEvent.NeedsLogin?>(null) }
    var handoff by remember { mutableStateOf<ReviewEvent.HandedOff?>(null) }
    var confirmRegenerate by remember { mutableStateOf(false) }

    LaunchedEffect(draftId) { viewModel.load(draftId) }
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is ReviewEvent.Message -> snackbarHostState.showSnackbar(event.text)
                is ReviewEvent.NeedsLogin -> loginPrompt = event
                is ReviewEvent.HandedOff -> handoff = event
                is ReviewEvent.Published -> {
                    val result = snackbarHostState.showSnackbar(
                        "Posted to Xiaohongshu", actionLabel = "View", duration = SnackbarDuration.Long,
                    )
                    if (result == SnackbarResult.ActionPerformed) {
                        runCatching {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(event.link)))
                        }
                    }
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Review", style = MaterialTheme.typography.titleLarge)
                        draft?.foodInfo?.restaurantName?.takeIf { it.isNotBlank() }?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    draft?.let { StatusPill(it.status, Modifier.padding(end = 4.dp)) }
                    if (draft?.variants?.isNotEmpty() == true && draft?.status != NoteStatus.SHARED) {
                        TextButton(onClick = viewModel::saveChanges) { Text("Mark ready") }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        bottomBar = {
            val d = draft
            if (d != null && d.variants.isNotEmpty()) {
                PublishBar(
                    draft = d,
                    isPublishing = isPublishing,
                    enabled = aiTask == AiTask.NONE,
                    onPublish = viewModel::publish,
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        val d = draft ?: return@Scaffold
        if (d.variants.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                EmptyState(
                    icon = Icons.Outlined.EditNote,
                    title = "Not written yet",
                    body = "This note's photos and details are saved. Let's write it.",
                    action = {
                      Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        ModelChip(onOpenSettings = onOpenSettings)
                        Button(onClick = viewModel::regenerateAll, enabled = aiTask == AiTask.NONE) {
                            if (aiTask != AiTask.NONE) {
                                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary)
                                Spacer(Modifier.width(10.dp))
                                Text("Writing…")
                            } else {
                                Icon(Icons.Filled.AutoAwesome, null, Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Write it now")
                            }
                        }
                      }
                    },
                )
            }
            return@Scaffold
        }

        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .consumeWindowInsets(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            PhotoPicker(
                d,
                onToggle = viewModel::togglePublishPhoto,
                onSetCover = viewModel::setCover,
                onMove = viewModel::movePublishPhoto,
                onIncludeAll = viewModel::includeAllPhotos,
            )

            StyleTabs(d, onSelect = viewModel::selectVariant)

            val v = d.selectedVariant ?: return@Column
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                AnimatedVisibility(visible = v.warnings.isNotEmpty()) { WarningsCard(v.warnings) }

                NoteEditor(
                    variant = v,
                    busy = aiTask == AiTask.REWRITE_ONE,
                    onTitle = viewModel::updateTitle,
                    onBody = viewModel::updateBody,
                    onAddTags = viewModel::addHashtags,
                    onRemoveTag = viewModel::removeHashtag,
                )

                if (v.model.isNotBlank()) {
                    Text("Written by ${v.model}", style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 4.dp))
                }
                ModelChip(onOpenSettings = onOpenSettings)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(
                        onClick = viewModel::rewriteCurrent,
                        enabled = aiTask == AiTask.NONE && !isPublishing,
                        shape = CircleShape,
                    ) {
                        if (aiTask == AiTask.REWRITE_ONE) {
                            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Outlined.Refresh, null, Modifier.size(18.dp))
                        }
                        Spacer(Modifier.width(8.dp))
                        Text("Rewrite this one")
                    }
                    TextButton(
                        onClick = { confirmRegenerate = true },
                        enabled = aiTask == AiTask.NONE && !isPublishing,
                    ) {
                        if (aiTask == AiTask.REGENERATE_ALL) {
                            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                        }
                        Text("Rewrite all")
                    }
                }
            }
        }
    }

    // ---- Dialogs ----

    if (confirmRegenerate) {
        AlertDialog(
            onDismissRequest = { confirmRegenerate = false },
            icon = { Icon(Icons.Filled.AutoAwesome, null) },
            title = { Text("Rewrite all four?") },
            text = { Text("Every style gets a fresh version. Edits you made to any of them will be replaced.") },
            confirmButton = {
                TextButton(onClick = { confirmRegenerate = false; viewModel.regenerateAll() }) { Text("Rewrite all") }
            },
            dismissButton = { TextButton(onClick = { confirmRegenerate = false }) { Text("Cancel") } },
        )
    }

    loginPrompt?.let { prompt ->
        AlertDialog(
            onDismissRequest = { loginPrompt = null },
            icon = { Icon(Icons.Outlined.Link, null) },
            title = { Text(if (prompt.expired) "Your XHS login expired" else "Connect Xiaohongshu") },
            text = {
                Text("Log in to post directly from here. Or post it yourself: the text is copied and the photos are saved to your gallery, then Xiaohongshu opens.")
            },
            confirmButton = {
                Button(onClick = { loginPrompt = null; onNavigateToLogin() }) { Text("Log in") }
            },
            dismissButton = {
                TextButton(onClick = { loginPrompt = null; viewModel.publishManually() }) { Text("Post manually") }
            },
        )
    }

    handoff?.let { h ->
        AlertDialog(
            onDismissRequest = { handoff = null },
            icon = { Icon(Icons.Outlined.ContentPaste, null) },
            title = { Text("Ready to paste") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (h.reason != null) {
                        Text("Direct posting didn't work (${h.reason}), so here's the manual route.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text("1. The text is on your clipboard\n2. Photos are in Pictures/XHSNoteGen, in order\n3. In Xiaohongshu, tap + → pick the photos → paste")
                    if (!h.openedXhs) {
                        Text("Open Xiaohongshu yourself — it couldn't be launched from here.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { handoff = null; viewModel.markShared() }) { Text("I posted it") }
            },
            dismissButton = { TextButton(onClick = { handoff = null }) { Text("Not yet") } },
        )
    }
}

// ---------------------------------------------------------------------------
// Photos
// ---------------------------------------------------------------------------

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PhotoPicker(
    draft: NoteDraft,
    onToggle: (String) -> Unit,
    onSetCover: (String) -> Unit,
    onMove: (String, Int) -> Unit,
    onIncludeAll: () -> Unit,
) {
    val selected = draft.selectedPublishPhotoUris
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.padding(start = 20.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Eyebrow("Photos")
                Text(
                    "${selected.size} of ${draft.photoUris.size} in the post · tap to include, hold for cover & order",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (selected.size < draft.photoUris.size) {
                TextButton(onClick = onIncludeAll) { Text("Include all") }
            }
        }
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            itemsIndexed(draft.photoUris, key = { _, uri -> uri }) { _, uri ->
                val order = selected.indexOf(uri) // -1 when left out
                val isIn = order >= 0
                var menu by remember { mutableStateOf(false) }
                Box {
                    Box(
                        Modifier
                            .size(width = 104.dp, height = 138.dp)
                            .clip(MaterialTheme.shapes.medium)
                            .border(
                                width = if (isIn) 2.dp else 0.dp,
                                color = if (isIn) MaterialTheme.colorScheme.primary else Color.Transparent,
                                shape = MaterialTheme.shapes.medium,
                            )
                            .combinedClickable(onClick = { onToggle(uri) }, onLongClick = { menu = true }),
                    ) {
                        AsyncImage(
                            model = uri, contentDescription = null, contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainerHigh),
                        )
                        if (!isIn) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)))
                        if (order == 0) {
                            Text(
                                "Cover",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .padding(6.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary)
                                    .padding(horizontal = 8.dp, vertical = 2.dp),
                            )
                        }
                        Box(
                            Modifier
                                .align(Alignment.TopEnd)
                                .padding(6.dp)
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(if (isIn) MaterialTheme.colorScheme.primary else Color.Black.copy(alpha = 0.25f))
                                .border(1.5.dp, Color.White, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (isIn) {
                                Text("${order + 1}", style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onPrimary)
                            }
                        }
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        if (order != 0) DropdownMenuItem(
                            text = { Text("Set as cover") },
                            leadingIcon = { Icon(Icons.Outlined.Star, null) },
                            onClick = { menu = false; onSetCover(uri) },
                        )
                        if (isIn && order > 0) DropdownMenuItem(
                            text = { Text("Move earlier") },
                            leadingIcon = { Icon(Icons.AutoMirrored.Outlined.ArrowBack, null) },
                            onClick = { menu = false; onMove(uri, -1) },
                        )
                        if (isIn && order < selected.lastIndex) DropdownMenuItem(
                            text = { Text("Move later") },
                            leadingIcon = { Icon(Icons.AutoMirrored.Outlined.ArrowForward, null) },
                            onClick = { menu = false; onMove(uri, +1) },
                        )
                        DropdownMenuItem(
                            text = { Text(if (isIn) "Leave out of post" else "Include in post") },
                            leadingIcon = { Icon(if (isIn) Icons.Outlined.HideImage else Icons.Outlined.AddPhotoAlternate, null) },
                            onClick = { menu = false; onToggle(uri) },
                        )
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Styles
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StyleTabs(draft: NoteDraft, onSelect: (Int) -> Unit) {
    val preferred = draft.preferredStyle
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        itemsIndexed(draft.variants) { index, variant ->
            val style = NoteStyle.fromLabel(variant.styleLabel)
            val selected = index == draft.selectedVariantIndex
            FilterChip(
                selected = selected,
                onClick = { onSelect(index) },
                label = { Text(style.displayName) },
                leadingIcon = if (style == preferred) {
                    { Icon(Icons.Filled.Star, contentDescription = "Your favorite style", Modifier.size(16.dp)) }
                } else null,
                shape = CircleShape,
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.onSurface,
                    selectedLabelColor = MaterialTheme.colorScheme.surface,
                    selectedLeadingIconColor = MaterialTheme.colorScheme.tertiaryContainer,
                    iconColor = MaterialTheme.colorScheme.tertiary,
                ),
                border = if (selected) null else FilterChipDefaults.filterChipBorder(
                    enabled = true, selected = false, borderColor = MaterialTheme.colorScheme.outlineVariant,
                ),
            )
        }
    }
}

@Composable
private fun WarningsCard(warnings: List<String>) {
    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.tertiaryContainer) {
        Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Outlined.Info, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onTertiaryContainer)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Double-check", style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onTertiaryContainer)
                warnings.forEach {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onTertiaryContainer)
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Editor
// ---------------------------------------------------------------------------

/** The note as a sheet of paper: borderless title and body, tags at the foot. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NoteEditor(
    variant: NoteVariant,
    busy: Boolean,
    onTitle: (String) -> Unit,
    onBody: (String) -> Unit,
    onAddTags: (String) -> Unit,
    onRemoveTag: (String) -> Unit,
) {
    val c = MaterialTheme.colorScheme
    Surface(shape = MaterialTheme.shapes.large, color = c.surfaceContainerLowest) {
        Box {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                val over = variant.title.length > TITLE_LIMIT
                BasicTextField(
                    value = variant.title,
                    onValueChange = onTitle,
                    textStyle = MaterialTheme.typography.headlineSmall.copy(color = c.onSurface),
                    cursorBrush = SolidColor(c.primary),
                    modifier = Modifier.fillMaxWidth(),
                    decorationBox = { inner ->
                        if (variant.title.isEmpty()) {
                            Text("Title", style = MaterialTheme.typography.headlineSmall, color = c.onSurfaceVariant.copy(alpha = 0.5f))
                        }
                        inner()
                    },
                )
                Text(
                    if (over) "${variant.title.length}/$TITLE_LIMIT · XHS allows $TITLE_LIMIT characters"
                    else "${variant.title.length}/$TITLE_LIMIT",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (over) c.error else c.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.End),
                )
                HorizontalDivider(color = c.outlineVariant)
                BasicTextField(
                    value = variant.body,
                    onValueChange = onBody,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = c.onSurface),
                    cursorBrush = SolidColor(c.primary),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 180.dp),
                )
                HorizontalDivider(color = c.outlineVariant)

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    variant.hashtags.forEach { tag ->
                        Row(
                            Modifier
                                .clip(CircleShape)
                                .background(c.primaryContainer)
                                .padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("#$tag", style = MaterialTheme.typography.labelLarge, color = c.onPrimaryContainer)
                            Box(
                                Modifier.padding(start = 2.dp).size(22.dp).clip(CircleShape).clickable { onRemoveTag(tag) },
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(Icons.Filled.Close, contentDescription = "Remove #$tag",
                                    Modifier.size(14.dp), tint = c.onPrimaryContainer)
                            }
                        }
                    }
                    TagInput(onAddTags)
                }
            }
            if (busy) {
                Box(
                    Modifier.matchParentSize().clip(MaterialTheme.shapes.large).background(c.surfaceContainerLowest.copy(alpha = 0.75f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        CircularProgressIndicator()
                        Text("Rewriting…", style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center)
                    }
                }
            }
        }
    }
}

@Composable
private fun TagInput(onAdd: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    val c = MaterialTheme.colorScheme
    fun commit() {
        if (text.isNotBlank()) onAdd(text)
        text = ""
    }
    BasicTextField(
        value = text,
        onValueChange = { new ->
            // A space or comma finishes a tag, like most tag inputs.
            if (new.endsWith(" ") || new.endsWith(",") || new.endsWith("，")) {
                text = new.dropLast(1); commit()
            } else text = new
        },
        singleLine = true,
        textStyle = MaterialTheme.typography.labelLarge.copy(color = c.onSurface),
        cursorBrush = SolidColor(c.primary),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { commit() }),
        modifier = Modifier.widthIn(min = 96.dp, max = 180.dp),
        decorationBox = { inner ->
            Row(
                Modifier
                    .clip(CircleShape)
                    .border(1.dp, c.outlineVariant, CircleShape)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box {
                    if (text.isEmpty()) Text("+ tag", style = MaterialTheme.typography.labelLarge, color = c.onSurfaceVariant)
                    inner()
                }
            }
        },
    )
}

// ---------------------------------------------------------------------------
// Bottom bar
// ---------------------------------------------------------------------------

@Composable
private fun PublishBar(
    draft: NoteDraft,
    isPublishing: Boolean,
    enabled: Boolean,
    onPublish: () -> Unit,
) {
    val titleTooLong = (draft.selectedVariant?.title?.length ?: 0) > TITLE_LIMIT
    Surface(color = MaterialTheme.colorScheme.background, shadowElevation = 8.dp) {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (titleTooLong) {
                Text("Shorten the title to post", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error)
            }
            Button(
                onClick = onPublish,
                enabled = enabled && !isPublishing && !titleTooLong,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = CircleShape,
            ) {
                if (isPublishing) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary)
                    Spacer(Modifier.width(12.dp))
                    Text("Posting…", style = MaterialTheme.typography.titleMedium)
                } else {
                    Icon(Icons.AutoMirrored.Filled.Send, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(
                        if (draft.status == NoteStatus.SHARED) "Post again" else "Post to Xiaohongshu",
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
        }
    }
}
