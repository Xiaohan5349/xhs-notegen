package com.xiaohan.xhsnotegen.ui.create

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.xiaohan.xhsnotegen.domain.NoteStyle
import com.xiaohan.xhsnotegen.ui.components.ModelChip
import com.xiaohan.xhsnotegen.ui.components.SectionCard
import com.xiaohan.xhsnotegen.ui.components.SoftTextField
import com.xiaohan.xhsnotegen.ui.components.dashedBorder
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateFormScreen(
    onNavigateBack: () -> Unit,
    onDraftSaved: (Long) -> Unit,
    onOpenSettings: () -> Unit = {},
    viewModel: CreateFormViewModel = viewModel(),
) {
    val photos by viewModel.photoUris.collectAsState()
    val foodInfo by viewModel.foodInfo.collectAsState()
    val selectedStyle by viewModel.selectedStyle.collectAsState()
    val photoMessage by viewModel.photoMessage.collectAsState()
    val isImporting by viewModel.isImporting.collectAsState()
    val isSaving by viewModel.isSaving.collectAsState()
    val scope = rememberCoroutineScope()
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(maxItems = CreateFormViewModel.MAX_PHOTOS),
    ) { uris -> viewModel.addPhotos(uris) }
    val pickPhotos = { picker.launch(PickVisualMediaRequest(PickVisualMedia.ImageOnly)) }

    val missing = buildList {
        if (photos.isEmpty()) add("a photo")
        if (foodInfo.dishNames.isBlank()) add("what you ate")
        if (foodInfo.restaurantName.isBlank()) add("where")
    }
    val canGenerate = missing.isEmpty() && !isSaving && !isImporting

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("New note") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.background) {
                Column(
                    // No imePadding here: while typing, the keyboard covers this bar
                    // instead of the bar eating half of the remaining screen.
                    Modifier.fillMaxWidth().navigationBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ModelChip(onOpenSettings = onOpenSettings)
                    val hint = errorMessage ?: if (missing.isNotEmpty()) "Add ${missing.joinToString(", ")} to continue" else null
                    if (hint != null) {
                        Text(hint, style = MaterialTheme.typography.bodySmall,
                            color = if (errorMessage != null) MaterialTheme.colorScheme.error
                                    else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Button(
                        onClick = {
                            scope.launch {
                                try {
                                    errorMessage = null
                                    viewModel.saveDraftSuspend()?.let(onDraftSaved)
                                } catch (e: Exception) {
                                    errorMessage = e.message ?: "Couldn't save the note"
                                }
                            }
                        },
                        enabled = canGenerate,
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        shape = CircleShape,
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary)
                        } else {
                            Icon(Icons.Filled.AutoAwesome, null, Modifier.size(20.dp))
                            Spacer(Modifier.width(10.dp))
                            Text("Write my note", style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .consumeWindowInsets(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // ---- Photos ----
            var selecting by remember { mutableStateOf(false) }
            var selected by remember { mutableStateOf(emptySet<Uri>()) }
            LaunchedEffect(photos) {
                selected = selected.filter { it in photos }.toSet()
                if (photos.isEmpty()) selecting = false
            }
            SectionCard(
                title = "Photos",
                subtitle = when {
                    photos.isEmpty() -> "Date and place are filled in from the photos"
                    selecting -> "${selected.size} selected"
                    else -> "Tap a photo for cover, order and remove"
                },
                trailing = {
                    if (photos.size > 1 || selecting) {
                        TextButton(onClick = { selecting = !selecting; selected = emptySet() }) {
                            Text(if (selecting) "Done" else "Select")
                        }
                    } else if (photos.isNotEmpty()) {
                        Text("${photos.size}/${CreateFormViewModel.MAX_PHOTOS}",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
            ) {
                if (photos.isEmpty() && !isImporting) {
                    BigAddPhotos(onClick = pickPhotos)
                } else {
                    PhotoStrip(
                        photos = photos,
                        importing = isImporting,
                        canAddMore = photos.size < CreateFormViewModel.MAX_PHOTOS && !selecting,
                        selecting = selecting,
                        selected = selected,
                        onToggleSelect = { uri -> selected = if (uri in selected) selected - uri else selected + uri },
                        onAdd = pickPhotos,
                        onRemove = viewModel::removePhoto,
                        onMakeCover = viewModel::makeCover,
                        onMove = viewModel::movePhoto,
                    )
                    if (selecting) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            TextButton(onClick = {
                                selected = if (selected.size == photos.size) emptySet() else photos.toSet()
                            }) { Text(if (selected.size == photos.size) "Select none" else "Select all") }
                            Spacer(Modifier.weight(1f))
                            FilledTonalButton(
                                onClick = { selected.single().let(viewModel::makeCover); selected = emptySet() },
                                enabled = selected.size == 1,
                            ) { Text("Set as cover") }
                            Button(
                                onClick = { viewModel.removePhotos(selected); selected = emptySet(); selecting = false },
                                enabled = selected.isNotEmpty(),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.error,
                                    contentColor = MaterialTheme.colorScheme.onError,
                                ),
                            ) { Text(if (selected.isEmpty()) "Remove" else "Remove ${selected.size}") }
                        }
                    } else if (photos.isNotEmpty()) {
                        Text("${photos.size}/${CreateFormViewModel.MAX_PHOTOS} photos",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                photoMessage?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }

            // ---- Facts ----
            SectionCard(title = "The meal") {
                SoftTextField(
                    value = foodInfo.dishNames,
                    onValueChange = { viewModel.updateFoodInfo(foodInfo.copy(dishNames = it)) },
                    label = "What did you eat", required = true,
                    placeholder = "红烧肉, 糖醋里脊",
                    leadingIcon = Icons.Outlined.RestaurantMenu,
                )
                SoftTextField(
                    value = foodInfo.restaurantName,
                    onValueChange = { viewModel.updateFoodInfo(foodInfo.copy(restaurantName = it)) },
                    label = "Where", required = true, singleLine = true,
                    placeholder = "Restaurant name",
                    leadingIcon = Icons.Outlined.Storefront,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    SoftTextField(
                        value = foodInfo.location,
                        onValueChange = { viewModel.updateFoodInfo(foodInfo.copy(location = it)) },
                        label = "Area", singleLine = true, placeholder = "City / area",
                        leadingIcon = Icons.Outlined.Place,
                        modifier = Modifier.weight(1f),
                    )
                    SoftTextField(
                        value = foodInfo.mealDate,
                        onValueChange = { viewModel.updateFoodInfo(foodInfo.copy(mealDate = it)) },
                        label = "When", singleLine = true, placeholder = "yyyy-MM-dd",
                        leadingIcon = Icons.Outlined.CalendarToday,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            // ---- The human part ----
            SectionCard(
                title = "In your own words",
                subtitle = "Optional, but this is what makes it sound like you — your phrasing is kept almost as-is.",
            ) {
                SoftTextField(
                    value = foodInfo.tasteNotes,
                    onValueChange = { viewModel.updateFoodInfo(foodInfo.copy(tasteNotes = it)) },
                    label = "How was it", minLines = 2, placeholder = "汤有点咸，但面很筋道",
                )
                SoftTextField(
                    value = foodInfo.priceOrRating,
                    onValueChange = { viewModel.updateFoodInfo(foodInfo.copy(priceOrRating = it)) },
                    label = "Price or rating", singleLine = true, placeholder = "两个人150",
                )
                SoftTextField(
                    value = foodInfo.vibeNotes,
                    onValueChange = { viewModel.updateFoodInfo(foodInfo.copy(vibeNotes = it)) },
                    label = "The place", minLines = 2, placeholder = "排了40分钟，店里很吵",
                )
                SoftTextField(
                    value = foodInfo.personalNotes,
                    onValueChange = { viewModel.updateFoodInfo(foodInfo.copy(personalNotes = it)) },
                    label = "Anything else", minLines = 2, placeholder = "和谁、为什么来、下次想点什么",
                )
            }

            // ---- Style ----
            SectionCard(title = "Favorite style", subtitle = "All four get written — this one shows first") {
                NoteStyle.entries.chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        row.forEach { style ->
                            StyleOption(
                                style = style,
                                selected = style == selectedStyle,
                                onClick = { viewModel.setStyle(style) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun BigAddPhotos(onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .height(160.dp)
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f))
            .dashedBorder(MaterialTheme.colorScheme.primary.copy(alpha = 0.6f), 16.dp)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Outlined.AddPhotoAlternate, null, Modifier.size(36.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(8.dp))
        Text("Add photos", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        Text("Up to ${CreateFormViewModel.MAX_PHOTOS}", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun PhotoStrip(
    photos: List<Uri>,
    importing: Boolean,
    canAddMore: Boolean,
    selecting: Boolean,
    selected: Set<Uri>,
    onToggleSelect: (Uri) -> Unit,
    onAdd: () -> Unit,
    onRemove: (Uri) -> Unit,
    onMakeCover: (Uri) -> Unit,
    onMove: (Uri, Int) -> Unit,
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        itemsIndexed(photos, key = { _, uri -> uri.toString() }) { index, uri ->
            var menu by remember { mutableStateOf(false) }
            val isSelected = uri in selected
            Box {
                Box(
                    Modifier
                        .size(width = 96.dp, height = 128.dp)
                        .clip(MaterialTheme.shapes.medium)
                        .border(
                            width = if (isSelected) 3.dp else 0.dp,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                            shape = MaterialTheme.shapes.medium,
                        )
                        .clickable { if (selecting) onToggleSelect(uri) else menu = true }
                ) {
                    AsyncImage(model = uri, contentDescription = null, contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainerHigh))
                    if (index == 0) {
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
                    if (selecting) {
                        Box(
                            Modifier
                                .align(Alignment.TopEnd)
                                .padding(6.dp)
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(if (isSelected) MaterialTheme.colorScheme.primary else Color.Black.copy(alpha = 0.3f))
                                .border(1.5.dp, Color.White, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (isSelected) Icon(Icons.Filled.Check, null, Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.onPrimary)
                        }
                    } else {
                        Box(
                            Modifier
                                .align(Alignment.TopEnd)
                                .padding(4.dp)
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.5f))
                                .clickable { onRemove(uri) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Filled.Close, contentDescription = "Remove photo", tint = Color.White,
                                modifier = Modifier.size(14.dp))
                        }
                    }
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    if (index != 0) DropdownMenuItem(
                        text = { Text("Set as cover") },
                        leadingIcon = { Icon(Icons.Outlined.Star, null) },
                        onClick = { menu = false; onMakeCover(uri) },
                    )
                    if (index > 0) DropdownMenuItem(
                        text = { Text("Move left") },
                        leadingIcon = { Icon(Icons.AutoMirrored.Outlined.ArrowBack, null) },
                        onClick = { menu = false; onMove(uri, -1) },
                    )
                    if (index < photos.lastIndex) DropdownMenuItem(
                        text = { Text("Move right") },
                        leadingIcon = { Icon(Icons.AutoMirrored.Outlined.ArrowForward, null) },
                        onClick = { menu = false; onMove(uri, +1) },
                    )
                    DropdownMenuItem(
                        text = { Text("Remove", color = MaterialTheme.colorScheme.error) },
                        leadingIcon = { Icon(Icons.Outlined.Delete, null, tint = MaterialTheme.colorScheme.error) },
                        onClick = { menu = false; onRemove(uri) },
                    )
                }
            }
        }
        if (importing || canAddMore) {
            item(key = "add") {
                Box(
                    Modifier
                        .size(width = 96.dp, height = 128.dp)
                        .clip(MaterialTheme.shapes.medium)
                        .dashedBorder(MaterialTheme.colorScheme.outline, 16.dp)
                        .clickable(enabled = !importing, onClick = onAdd),
                    contentAlignment = Alignment.Center,
                ) {
                    if (importing) {
                        CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Outlined.Add, contentDescription = "Add photos",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun StyleOption(
    style: NoteStyle,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = MaterialTheme.colorScheme
    Box(
        modifier
            .heightIn(min = 84.dp)
            .clip(MaterialTheme.shapes.medium)
            .background(if (selected) c.primaryContainer else c.surfaceContainer)
            .border(
                width = if (selected) 1.5.dp else 0.dp,
                color = if (selected) c.primary else Color.Transparent,
                shape = MaterialTheme.shapes.medium,
            )
            .clickable(onClick = onClick)
            .padding(14.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(end = 18.dp)) {
            Text(style.displayName, style = MaterialTheme.typography.titleSmall,
                color = if (selected) c.onPrimaryContainer else c.onSurface)
            Text(style.blurb, style = MaterialTheme.typography.bodySmall,
                color = if (selected) c.onPrimaryContainer.copy(alpha = 0.8f) else c.onSurfaceVariant)
        }
        if (selected) {
            Icon(Icons.Filled.CheckCircle, null, tint = c.primary,
                modifier = Modifier.align(Alignment.TopEnd).size(18.dp))
        }
    }
}
