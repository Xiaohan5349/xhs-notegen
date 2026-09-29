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
import androidx.compose.foundation.lazy.items
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
import com.xiaohan.xhsnotegen.domain.BuiltInModes
import com.xiaohan.xhsnotegen.domain.FieldSlot
import com.xiaohan.xhsnotegen.domain.NoteStyle
import com.xiaohan.xhsnotegen.domain.WritingMode
import com.xiaohan.xhsnotegen.ui.components.ModelChip
import com.xiaohan.xhsnotegen.ui.components.RatingBar
import com.xiaohan.xhsnotegen.ui.components.ratingWords
import com.xiaohan.xhsnotegen.domain.Place
import com.xiaohan.xhsnotegen.domain.PlaceSource
import com.xiaohan.xhsnotegen.ui.components.DateWheelDialog
import com.xiaohan.xhsnotegen.ui.components.PickerField
import com.xiaohan.xhsnotegen.ui.components.PlacePickerDialog
import com.xiaohan.xhsnotegen.ui.components.formatDateForDisplay
import com.xiaohan.xhsnotegen.ui.components.SectionCard
import com.xiaohan.xhsnotegen.ui.components.SoftTextField
import com.xiaohan.xhsnotegen.ui.components.dashedBorder
import com.xiaohan.xhsnotegen.util.PhotoLocation
import com.xiaohan.xhsnotegen.i18n.tr
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateFormScreen(
    onNavigateBack: () -> Unit,
    onDraftSaved: (Long) -> Unit,
    onOpenSettings: () -> Unit = {},
    onManageModes: () -> Unit = {},
    viewModel: CreateFormViewModel = viewModel(),
) {
    val photos by viewModel.photoUris.collectAsState()
    val foodInfo by viewModel.foodInfo.collectAsState()
    val selectedStyle by viewModel.selectedStyle.collectAsState()
    val photoMessage by viewModel.photoMessage.collectAsState()
    val isImporting by viewModel.isImporting.collectAsState()
    val isSaving by viewModel.isSaving.collectAsState()
    val photoPlace by viewModel.photoPlace.collectAsState()
    val rating by viewModel.rating.collectAsState()
    val place by viewModel.place.collectAsState()
    var showPlacePicker by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    val mode by viewModel.mode.collectAsState()
    val modes by viewModel.modes.collectAsState()
    fun f(slot: FieldSlot) = mode.field(slot)
    val canUnlockPlaces by viewModel.canUnlockPhotoPlaces.collectAsState()
    val placePermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        // Whatever was granted, try again; without access it just offers nothing.
        viewModel.dismissPhotoPlaceOffer()
        viewModel.resolvePhotoPlace()
    }
    val scope = rememberCoroutineScope()
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Each mode has its own photo limit; the multi-picker needs at least 2.
    val maxPhotos = mode.maxPhotos
    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(maxItems = maxPhotos.coerceAtLeast(2)),
    ) { uris -> viewModel.addPhotos(uris) }
    val singlePicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let { viewModel.addPhotos(listOf(it)) }
    }
    val pickPhotos = {
        val request = PickVisualMediaRequest(PickVisualMedia.ImageOnly)
        if (maxPhotos - photos.size <= 1) singlePicker.launch(request) else picker.launch(request)
    }

    val missing = buildList {
        if (photos.isEmpty()) add(tr("a photo", "照片"))
        if (foodInfo.dishNames.isBlank()) add(f(FieldSlot.SUBJECT).label.let { tr(it.lowercase(), it) })
        if (foodInfo.restaurantName.isBlank()) add(f(FieldSlot.PLACE).label.let { tr(it.lowercase(), it) })
    }
    val tooMany = (photos.size - maxPhotos).coerceAtLeast(0)
    val canGenerate = missing.isEmpty() && tooMany == 0 && !isSaving && !isImporting

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(tr("New ${mode.name.lowercase()} note", "新${mode.name}笔记")) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = tr("Back", "返回"))
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
                    val hint = errorMessage
                        ?: if (missing.isNotEmpty()) tr("Add ${missing.joinToString(", ")} to continue", "请先填写：${missing.joinToString("、")}")
                        else if (tooMany > 0) tr("This mode takes up to $maxPhotos photos — remove $tooMany", "这个模式最多 $maxPhotos 张照片，请删掉 $tooMany 张")
                        else null
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
                                    errorMessage = e.message ?: tr("Couldn't save the note", "笔记没能保存")
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
                            Text(tr("Write my note", "帮我写笔记"), style = MaterialTheme.typography.titleMedium)
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
            // ---- Kind of note ----
            ModePicker(modes = modes, selected = mode.key, onSelect = viewModel::setMode, onManage = onManageModes)

            // ---- Photos ----
            var selecting by remember { mutableStateOf(false) }
            var selected by remember { mutableStateOf(emptySet<Uri>()) }
            LaunchedEffect(photos) {
                selected = selected.filter { it in photos }.toSet()
                if (photos.isEmpty()) selecting = false
            }
            SectionCard(
                title = tr("Photos", "照片"),
                subtitle = when {
                    photos.isEmpty() -> tr("Date and place are filled in from the photos", "日期和地点会从照片里自动读取")
                    selecting -> tr("${selected.size} selected", "已选 ${selected.size} 张")
                    else -> tr("Tap a photo for cover, order and remove", "点照片可设封面、调顺序、删除")
                },
                trailing = {
                    if (photos.size > 1 || selecting) {
                        TextButton(onClick = { selecting = !selecting; selected = emptySet() }) {
                            Text(if (selecting) tr("Done", "完成") else tr("Select", "选择"))
                        }
                    } else if (photos.isNotEmpty()) {
                        Text("${photos.size}/$maxPhotos",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
            ) {
                if (photos.isEmpty() && !isImporting) {
                    BigAddPhotos(maxPhotos, onClick = pickPhotos)
                } else {
                    PhotoStrip(
                        photos = photos,
                        importing = isImporting,
                        canAddMore = photos.size < maxPhotos && !selecting,
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
                            }) { Text(if (selected.size == photos.size) tr("Select none", "全不选") else tr("Select all", "全选")) }
                            Spacer(Modifier.weight(1f))
                            FilledTonalButton(
                                onClick = { selected.single().let(viewModel::makeCover); selected = emptySet() },
                                enabled = selected.size == 1,
                            ) { Text(tr("Set as cover", "设为封面")) }
                            Button(
                                onClick = { viewModel.removePhotos(selected); selected = emptySet(); selecting = false },
                                enabled = selected.isNotEmpty(),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.error,
                                    contentColor = MaterialTheme.colorScheme.onError,
                                ),
                            ) { Text(if (selected.isEmpty()) tr("Remove", "删除") else tr("Remove ${selected.size}", "删除 ${selected.size} 张")) }
                        }
                    } else if (photos.isNotEmpty()) {
                        Text(tr("${photos.size}/$maxPhotos photos", "${photos.size}/$maxPhotos 张"),
                            style = MaterialTheme.typography.labelMedium,
                            color = if (tooMany > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                photoMessage?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
                photoPlace?.takeIf { it.isKnown }?.let { place ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Outlined.Place, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.secondary)
                        Text(tr("${place.fullAddress} · from photo", "${place.fullAddress} · 来自照片"), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (canUnlockPlaces) {
                    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.secondaryContainer) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(tr("Read where these photos were taken?", "读取照片的拍摄地点？"), style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer)
                            Text(
                                tr(
                                    "Android hides photo locations from apps unless you allow photo access. " +
                                        "The place is used to fill in the area and organize notes by city.",
                                    "除非允许访问照片，Android 不会把照片的位置交给 App。地点用来填写区域、按城市整理笔记。",
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = { placePermission.launch(PhotoLocation.permissions()) }) { Text(tr("Allow", "允许")) }
                                TextButton(onClick = viewModel::dismissPhotoPlaceOffer) { Text(tr("Not now", "暂不")) }
                            }
                        }
                    }
                }
            }

            // ---- Facts ----
            SectionCard(
                title = if (mode.key == BuiltInModes.FOOD) tr("The meal", "这一餐") else tr("Details", "内容"),
                subtitle = mode.rootTag.takeIf { it.isNotBlank() }?.let { tr("Tagged $it", "标签：$it") },
            ) {
                SoftTextField(
                    value = foodInfo.dishNames,
                    onValueChange = { viewModel.updateFoodInfo(foodInfo.copy(dishNames = it)) },
                    label = f(FieldSlot.SUBJECT).label, required = true,
                    placeholder = f(FieldSlot.SUBJECT).hint,
                    leadingIcon = if (mode.key == BuiltInModes.FOOD) Icons.Outlined.RestaurantMenu else Icons.Outlined.EditNote,
                )
                SoftTextField(
                    value = foodInfo.restaurantName,
                    onValueChange = { viewModel.updateFoodInfo(foodInfo.copy(restaurantName = it)) },
                    label = f(FieldSlot.PLACE).label, required = true, singleLine = true,
                    placeholder = f(FieldSlot.PLACE).hint,
                    leadingIcon = if (mode.key == BuiltInModes.FOOD) Icons.Outlined.Storefront else Icons.Outlined.PinDrop,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
                )
                // Chosen from lists, not typed — works without internet or Google services.
                PickerField(
                    value = place?.takeIf { it.isKnown }?.display.orEmpty(),
                    label = tr("Place", "地点"),
                    placeholder = tr("Choose country, province, city", "选择国家、省份、城市"),
                    icon = Icons.Outlined.Place,
                    onClick = { showPlacePicker = true },
                    supporting = when {
                        place?.isKnown != true -> null
                        place?.source == PlaceSource.GPS -> tr("Read from the photo — tap to change", "来自照片，点按可修改")
                        else -> null
                    },
                )
                PickerField(
                    value = formatDateForDisplay(foodInfo.mealDate),
                    label = tr("When", "时间"),
                    placeholder = tr("Choose a date", "选择日期"),
                    icon = Icons.Outlined.CalendarToday,
                    onClick = { showDatePicker = true },
                    supporting = if (foodInfo.mealDate.isNotBlank() && photos.isNotEmpty()) tr("From the photo unless you change it", "默认取自照片，可以修改") else null,
                )
            }

            // ---- The human part ----
            SectionCard(
                title = tr("In your own words", "用你自己的话"),
                subtitle = tr("Optional, but this is what makes it sound like you — your phrasing is kept almost as-is.", "选填，但这部分让笔记更像你写的——你的说法会几乎原样保留。"),
            ) {
                Column {
                    Text(tr("Your rating", "你的评分"), style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RatingBar(rating, onRate = viewModel::setRating)
                        Spacer(Modifier.width(8.dp))
                        Text(ratingWords(rating), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                SoftTextField(
                    value = foodInfo.tasteNotes,
                    onValueChange = { viewModel.updateFoodInfo(foodInfo.copy(tasteNotes = it)) },
                    label = f(FieldSlot.FEELING).label, minLines = 2, placeholder = f(FieldSlot.FEELING).hint,
                )
                SoftTextField(
                    value = foodInfo.priceOrRating,
                    onValueChange = { viewModel.updateFoodInfo(foodInfo.copy(priceOrRating = it)) },
                    label = f(FieldSlot.COST).label, singleLine = true, placeholder = f(FieldSlot.COST).hint,
                )
                SoftTextField(
                    value = foodInfo.vibeNotes,
                    onValueChange = { viewModel.updateFoodInfo(foodInfo.copy(vibeNotes = it)) },
                    label = f(FieldSlot.SCENE).label, minLines = 2, placeholder = f(FieldSlot.SCENE).hint,
                )
                SoftTextField(
                    value = foodInfo.personalNotes,
                    onValueChange = { viewModel.updateFoodInfo(foodInfo.copy(personalNotes = it)) },
                    label = f(FieldSlot.OTHER).label, minLines = 2, placeholder = f(FieldSlot.OTHER).hint,
                )
            }

            // ---- Style ----
            SectionCard(title = tr("Favorite style", "偏好风格"), subtitle = tr("All four get written — this one shows first", "四种都会写，这一种排在最前")) {
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

    if (showPlacePicker) {
        PlacePickerDialog(
            initial = place ?: Place(),
            showAddress = false,
            onConfirm = { viewModel.setPlace(it); showPlacePicker = false },
            onDismiss = { showPlacePicker = false },
        )
    }
    if (showDatePicker) {
        DateWheelDialog(
            initial = foodInfo.mealDate,
            onConfirm = { viewModel.updateFoodInfo(foodInfo.copy(mealDate = it)); showDatePicker = false },
            onClear = if (foodInfo.mealDate.isNotBlank()) ({ viewModel.updateFoodInfo(foodInfo.copy(mealDate = "")); showDatePicker = false }) else null,
            onDismiss = { showDatePicker = false },
        )
    }
}

@Composable
private fun BigAddPhotos(maxPhotos: Int, onClick: () -> Unit) {
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
        Text(tr("Add photos", "添加照片"), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        Text(tr("Up to $maxPhotos", "最多 $maxPhotos 张"), style = MaterialTheme.typography.bodySmall,
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
                            tr("Cover", "封面"),
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
                            Icon(Icons.Filled.Close, contentDescription = tr("Remove photo", "删除照片"), tint = Color.White,
                                modifier = Modifier.size(14.dp))
                        }
                    }
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    if (index != 0) DropdownMenuItem(
                        text = { Text(tr("Set as cover", "设为封面")) },
                        leadingIcon = { Icon(Icons.Outlined.Star, null) },
                        onClick = { menu = false; onMakeCover(uri) },
                    )
                    if (index > 0) DropdownMenuItem(
                        text = { Text(tr("Move left", "左移")) },
                        leadingIcon = { Icon(Icons.AutoMirrored.Outlined.ArrowBack, null) },
                        onClick = { menu = false; onMove(uri, -1) },
                    )
                    if (index < photos.lastIndex) DropdownMenuItem(
                        text = { Text(tr("Move right", "右移")) },
                        leadingIcon = { Icon(Icons.AutoMirrored.Outlined.ArrowForward, null) },
                        onClick = { menu = false; onMove(uri, +1) },
                    )
                    DropdownMenuItem(
                        text = { Text(tr("Remove", "删除"), color = MaterialTheme.colorScheme.error) },
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
                        Icon(Icons.Outlined.Add, contentDescription = tr("Add photos", "添加照片"),
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

/** Food · Travel · Outfit … as chips; the last chip manages modes. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModePicker(modes: List<WritingMode>, selected: String, onSelect: (String) -> Unit, onManage: () -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(modes, key = { it.key }) { m ->
            FilterChip(
                selected = m.key == selected,
                onClick = { onSelect(m.key) },
                label = { Text(m.name) },
                shape = CircleShape,
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                ),
            )
        }
        item(key = "manage") {
            AssistChip(
                onClick = onManage,
                label = { Text(tr("Modes", "模式")) },
                leadingIcon = { Icon(Icons.Outlined.Tune, null, Modifier.size(16.dp)) },
                shape = CircleShape,
            )
        }
    }
}
