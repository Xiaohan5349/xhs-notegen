package com.xiaohan.xhsnotegen.ui.review

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.text.style.TextOverflow
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
import com.xiaohan.xhsnotegen.i18n.tr
import com.xiaohan.xhsnotegen.ui.components.CoverTag
import com.xiaohan.xhsnotegen.ui.components.EmptyState
import com.xiaohan.xhsnotegen.ui.components.InsetSegmented
import com.xiaohan.xhsnotegen.ui.components.OrderBadge
import com.xiaohan.xhsnotegen.ui.components.PhotoNumber
import com.xiaohan.xhsnotegen.ui.components.PhotoSheet
import com.xiaohan.xhsnotegen.ui.components.Tile
import com.xiaohan.xhsnotegen.ui.theme.NumberStyle
import com.xiaohan.xhsnotegen.ui.theme.app
import com.xiaohan.xhsnotegen.ui.components.Eyebrow
import com.xiaohan.xhsnotegen.ui.components.ModelChip
import com.xiaohan.xhsnotegen.ui.components.RatingBar
import com.xiaohan.xhsnotegen.ui.components.ratingWords
import com.xiaohan.xhsnotegen.ui.drafts.PlaceDialog
import com.xiaohan.xhsnotegen.ai.ModeStore
import com.xiaohan.xhsnotegen.ui.components.NoteLanguageRow
import com.xiaohan.xhsnotegen.domain.PlaceSource
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
    var editPlace by remember { mutableStateOf(false) }

    LaunchedEffect(draftId) { viewModel.load(draftId) }
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is ReviewEvent.Message -> snackbarHostState.showSnackbar(event.text)
                is ReviewEvent.NeedsLogin -> loginPrompt = event
                is ReviewEvent.HandedOff -> handoff = event
                is ReviewEvent.Published -> {
                    val result = snackbarHostState.showSnackbar(
                        tr("Posted to Xiaohongshu", "已发布到小红书"), actionLabel = tr("View", "查看"), duration = SnackbarDuration.Long,
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
                        Text(tr("Review", "预览"), style = MaterialTheme.typography.titleLarge)
                        draft?.foodInfo?.restaurantName?.takeIf { it.isNotBlank() }?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = tr("Back", "返回"))
                    }
                },
                actions = {
                    draft?.let { StatusPill(it.status, Modifier.padding(end = 4.dp)) }
                    if (draft?.variants?.isNotEmpty() == true && draft?.status != NoteStatus.SHARED) {
                        TextButton(onClick = viewModel::saveChanges) { Text(tr("Mark ready", "标为待发布")) }
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
                    title = tr("Not written yet", "还没写"),
                    body = tr("This note's photos and details are saved. Let's write it.", "照片和信息都已保存，现在就来写吧。"),
                    action = {
                      Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        NoteLanguageRow(d.language ?: ModeStore.get(d.type).language, viewModel::setLanguage)
                        ModelChip(onOpenSettings = onOpenSettings)
                        Button(onClick = viewModel::regenerateAll, enabled = aiTask == AiTask.NONE) {
                            if (aiTask != AiTask.NONE) {
                                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary)
                                Spacer(Modifier.width(10.dp))
                                Text(tr("Writing…", "写作中…"))
                            } else {
                                Icon(Icons.Filled.AutoAwesome, null, Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(tr("Write it now", "现在就写"))
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
                .padding(start = 14.dp, end = 14.dp, top = 4.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            PhotoPicker(
                d,
                onToggle = viewModel::togglePublishPhoto,
                onSetCover = viewModel::setCover,
                onMove = viewModel::movePublishPhoto,
                onIncludeAll = viewModel::includeAllPhotos,
            )

            MealInfoRow(d, onRate = viewModel::setRating, onEditPlace = { editPlace = true })

            StyleTabs(d, onSelect = viewModel::selectVariant)

            val v = d.selectedVariant ?: return@Column
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
                    Text(tr("Written by ${v.model}", "写作模型：${v.model}"), style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 4.dp))
                }
                NoteLanguageRow(
                    d.language ?: ModeStore.get(d.type).language, viewModel::setLanguage,
                    caption = tr("Applies the next time you rewrite", "下次重写时生效"),
                )
                ModelChip(onOpenSettings = onOpenSettings)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(
                        onClick = viewModel::rewriteCurrent,
                        enabled = aiTask == AiTask.NONE && !isPublishing,
                        shape = MaterialTheme.shapes.small,
                    ) {
                        if (aiTask == AiTask.REWRITE_ONE) {
                            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Outlined.Refresh, null, Modifier.size(18.dp))
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(tr("Rewrite this one", "重写这篇"))
                    }
                    TextButton(
                        onClick = { confirmRegenerate = true },
                        enabled = aiTask == AiTask.NONE && !isPublishing,
                    ) {
                        if (aiTask == AiTask.REGENERATE_ALL) {
                            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(tr("Rewrite all", "全部重写"))
                    }
                }
            }
        }
    }

    // ---- Dialogs ----

    if (editPlace) {
        draft?.let { d ->
            PlaceDialog(
                noteCount = 1,
                initial = d.foodInfo.place,
                onSave = { country, region, city, address ->
                    val old = d.foodInfo.place
                    val moved = old.country != country.trim() || old.city != city.trim()
                    viewModel.setPlace(old.copy(
                        country = country.trim(), region = region.trim(), city = city.trim(), address = address.trim(),
                        // Coordinates from the photo would point at the old place.
                        latitude = if (moved) null else old.latitude, longitude = if (moved) null else old.longitude,
                        source = PlaceSource.MANUAL,
                    ))
                },
                onDismiss = { editPlace = false },
            )
        }
    }

    if (confirmRegenerate) {
        AlertDialog(
            onDismissRequest = { confirmRegenerate = false },
            icon = { Icon(Icons.Filled.AutoAwesome, null) },
            title = { Text(tr("Rewrite all four?", "四篇全部重写？")) },
            text = { Text(tr("Every style gets a fresh version. Edits you made to any of them will be replaced.", "每种风格都会重新生成，你做过的修改将被覆盖。")) },
            confirmButton = {
                TextButton(onClick = { confirmRegenerate = false; viewModel.regenerateAll() }) { Text(tr("Rewrite all", "全部重写")) }
            },
            dismissButton = { TextButton(onClick = { confirmRegenerate = false }) { Text(tr("Cancel", "取消")) } },
        )
    }

    loginPrompt?.let { prompt ->
        AlertDialog(
            onDismissRequest = { loginPrompt = null },
            icon = { Icon(Icons.Outlined.Link, null) },
            title = { Text(if (prompt.expired) tr("Your XHS login expired", "小红书登录已过期") else tr("Connect Xiaohongshu", "连接小红书")) },
            text = {
                Text(tr("Log in to post directly from here. Or post it yourself: the text is copied and the photos are saved to your gallery, then Xiaohongshu opens.", "登录后可直接在这里发布。也可以手动发布：文字会被复制，照片会存到相册，然后打开小红书。"))
            },
            confirmButton = {
                Button(onClick = { loginPrompt = null; onNavigateToLogin() }) { Text(tr("Log in", "登录")) }
            },
            dismissButton = {
                TextButton(onClick = { loginPrompt = null; viewModel.publishManually() }) { Text(tr("Post manually", "手动发布")) }
            },
        )
    }

    handoff?.let { h ->
        AlertDialog(
            onDismissRequest = { handoff = null },
            icon = { Icon(Icons.Outlined.ContentPaste, null) },
            title = { Text(tr("Ready to paste", "可以粘贴了")) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (h.reason != null) {
                        Text(tr("Direct posting didn't work (${h.reason}), so here's the manual route.", "直接发布没成功（${h.reason}），请手动发布。"),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(tr("1. The text is on your clipboard\n2. Photos are in Pictures/XHSNoteGen, in order\n3. In Xiaohongshu, tap + → pick the photos → paste", "1. 文字已复制到剪贴板\n2. 照片已按顺序存到 Pictures/XHSNoteGen\n3. 在小红书点 + → 选照片 → 粘贴"))
                    if (!h.openedXhs) {
                        Text(tr("Open Xiaohongshu yourself — it couldn't be launched from here.", "无法从这里打开小红书，请手动打开。"),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { handoff = null; viewModel.markShared() }) { Text(tr("I posted it", "我已发布")) }
            },
            dismissButton = { TextButton(onClick = { handoff = null }) { Text(tr("Not yet", "还没有")) } },
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
    var expanded by rememberSaveable { mutableStateOf(false) }
    Tile(Modifier.fillMaxWidth(), contentPadding = PaddingValues(start = 14.dp, end = 4.dp, top = 6.dp, bottom = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Eyebrow(tr("Photos", "照片"))
                Text(
                    tr(
                        "${selected.size} of ${draft.photoUris.size} in the post · tap to include, hold for cover & order",
                        "已选 ${selected.size}/${draft.photoUris.size} 张 · 点按选择，长按设封面和顺序",
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (selected.size < draft.photoUris.size) {
                TextButton(onClick = onIncludeAll) { Text(tr("Include all", "全选")) }
            }
        }
        Spacer(Modifier.height(8.dp))
        PhotoSheet(
            count = draft.photoUris.size,
            expanded = expanded,
            onToggleExpanded = { expanded = !expanded },
            modifier = Modifier.padding(end = 10.dp),
            footer = tr("${draft.photoUris.size} photos · ${selected.size} in", "${draft.photoUris.size} 张 · 已选 ${selected.size}"),
            footerEnd = selected.firstOrNull()?.let { tr("cover %02d".format(draft.photoUris.indexOf(it) + 1), "封面 %02d".format(draft.photoUris.indexOf(it) + 1)) },
        ) { i ->
            val uri = draft.photoUris[i]
            val order = selected.indexOf(uri) // -1 when left out
            val isIn = order >= 0
            var menu by remember { mutableStateOf(false) }
            Box {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(3f / 4f)
                        .clip(RoundedCornerShape(4.dp))
                        .then(if (isIn) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(4.dp)) else Modifier)
                        .combinedClickable(onClick = { onToggle(uri) }, onLongClick = { menu = true }),
                ) {
                    AsyncImage(
                        model = uri, contentDescription = null, contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize().background(MaterialTheme.app.tile2),
                    )
                    if (!isIn) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)))
                    PhotoNumber(i + 1, Modifier.align(Alignment.TopStart).padding(3.dp))
                    if (isIn) OrderBadge(order + 1, Modifier.align(Alignment.TopEnd).padding(3.dp))
                    if (order == 0) CoverTag(Modifier.align(Alignment.BottomStart).padding(3.dp))
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    if (order != 0) DropdownMenuItem(
                        text = { Text(tr("Set as cover", "设为封面")) },
                        leadingIcon = { Icon(Icons.Outlined.Star, null) },
                        onClick = { menu = false; onSetCover(uri) },
                    )
                    if (isIn && order > 0) DropdownMenuItem(
                        text = { Text(tr("Move earlier", "前移")) },
                        leadingIcon = { Icon(Icons.AutoMirrored.Outlined.ArrowBack, null) },
                        onClick = { menu = false; onMove(uri, -1) },
                    )
                    if (isIn && order < selected.lastIndex) DropdownMenuItem(
                        text = { Text(tr("Move later", "后移")) },
                        leadingIcon = { Icon(Icons.AutoMirrored.Outlined.ArrowForward, null) },
                        onClick = { menu = false; onMove(uri, +1) },
                    )
                    DropdownMenuItem(
                        text = { Text(if (isIn) tr("Leave out of post", "不放进笔记") else tr("Include in post", "放进笔记")) },
                        leadingIcon = { Icon(if (isIn) Icons.Outlined.HideImage else Icons.Outlined.AddPhotoAlternate, null) },
                        onClick = { menu = false; onToggle(uri) },
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Rating + place
// ---------------------------------------------------------------------------

@Composable
private fun MealInfoRow(draft: NoteDraft, onRate: (Int) -> Unit, onEditPlace: () -> Unit) {
    val place = draft.foodInfo.place
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Tile(Modifier.weight(1f).fillMaxHeight(), contentPadding = PaddingValues(start = 8.dp, end = 8.dp, top = 10.dp, bottom = 8.dp)) {
            Text(tr("Rating", "评分"), style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 4.dp))
            RatingBar(draft.rating, onRate = onRate, size = 18.dp)
            Text(ratingWords(draft.rating), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 4.dp))
        }
        Tile(Modifier.weight(1.25f).fillMaxHeight(), onClick = onEditPlace, contentPadding = PaddingValues(12.dp)) {
            val source = when (place.source) {
                PlaceSource.GPS -> tr("From photo location", "来自照片定位")
                PlaceSource.TEXT -> tr("Found from the restaurant / area — tap to correct", "根据店名 / 区域查到 — 点按修改")
                PlaceSource.MANUAL -> tr("Set by you", "手动设置")
                null -> tr("Tap to set, or use Organize by place on the home screen", "点按设置，或在首页用“按地点整理”")
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Place, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(4.dp))
                Text(tr("Place", "地点"), style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                Icon(Icons.Outlined.Edit, tr("Edit place", "编辑地点"), Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(4.dp))
            Text(
                place.fullAddressDisplay.ifBlank { draft.foodInfo.location.ifBlank { tr("Add a place", "添加地点") } },
                style = MaterialTheme.typography.titleSmall,
                maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
            Text(source, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ---------------------------------------------------------------------------
// Styles
// ---------------------------------------------------------------------------

@Composable
private fun StyleTabs(draft: NoteDraft, onSelect: (Int) -> Unit) {
    val preferred = draft.preferredStyle
    InsetSegmented(
        options = draft.variants.indices.toList(),
        selected = draft.selectedVariantIndex,
        label = { i ->
            val style = NoteStyle.fromLabel(draft.variants[i].styleLabel)
            (if (style == preferred) "★ " else "") + style.displayName
        },
        onSelect = onSelect,
    )
}

@Composable
private fun WarningsCard(warnings: List<String>) {
    Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.tertiaryContainer) {
        Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Outlined.Info, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onTertiaryContainer)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(tr("Double-check", "请核对"), style = MaterialTheme.typography.labelLarge,
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
    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.app.tile, border = BorderStroke(1.dp, MaterialTheme.app.line)) {
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
                            Text(tr("Title", "标题"), style = MaterialTheme.typography.headlineSmall, color = c.onSurfaceVariant.copy(alpha = 0.5f))
                        }
                        inner()
                    },
                )
                Text(
                    if (over) tr("${variant.title.length}/$TITLE_LIMIT · XHS allows $TITLE_LIMIT characters", "${variant.title.length}/$TITLE_LIMIT · 小红书标题最多 $TITLE_LIMIT 字")
                    else "${variant.title.length}/$TITLE_LIMIT",
                    style = NumberStyle,
                    color = if (over) c.error else c.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.End),
                )
                HorizontalDivider(color = MaterialTheme.app.line)
                BasicTextField(
                    value = variant.body,
                    onValueChange = onBody,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = c.onSurface),
                    cursorBrush = SolidColor(c.primary),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 180.dp),
                )
                HorizontalDivider(color = MaterialTheme.app.line)

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
                                Icon(Icons.Filled.Close, contentDescription = tr("Remove #$tag", "删除 #$tag"),
                                    Modifier.size(14.dp), tint = c.onPrimaryContainer)
                            }
                        }
                    }
                    TagInput(onAddTags)
                }
            }
            if (busy) {
                Box(
                    Modifier.matchParentSize().clip(MaterialTheme.shapes.medium).background(MaterialTheme.app.tile.copy(alpha = 0.75f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        CircularProgressIndicator()
                        Text(tr("Rewriting…", "重写中…"), style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center)
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
                    .border(1.dp, MaterialTheme.app.line2, CircleShape)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box {
                    if (text.isEmpty()) Text(tr("+ tag", "+ 话题"), style = MaterialTheme.typography.labelLarge, color = c.onSurfaceVariant)
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
    Surface(color = MaterialTheme.colorScheme.background) {
      Column {
        HorizontalDivider(color = MaterialTheme.app.line)
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (titleTooLong) {
                Text(tr("Shorten the title to post", "标题太长，缩短后才能发布"), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error)
            }
            Button(
                onClick = onPublish,
                enabled = enabled && !isPublishing && !titleTooLong,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = MaterialTheme.shapes.medium,
            ) {
                if (isPublishing) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary)
                    Spacer(Modifier.width(12.dp))
                    Text(tr("Posting…", "发布中…"), style = MaterialTheme.typography.titleMedium)
                } else {
                    Icon(Icons.AutoMirrored.Filled.Send, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(
                        if (draft.status == NoteStatus.SHARED) tr("Post again", "再次发布") else tr("Post to Xiaohongshu", "发布到小红书"),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
        }
      }
    }
}
