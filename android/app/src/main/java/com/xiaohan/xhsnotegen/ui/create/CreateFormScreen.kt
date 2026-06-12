package com.xiaohan.xhsnotegen.ui.create

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMedia
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.xiaohan.xhsnotegen.domain.FoodInfo
import com.xiaohan.xhsnotegen.domain.NoteStyle
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CreateFormScreen(
    onNavigateBack: () -> Unit,
    onDraftSaved: (Long) -> Unit,
    viewModel: CreateFormViewModel = viewModel(),
) {
    val photos by viewModel.photoUris.collectAsState()
    val foodInfo by viewModel.foodInfo.collectAsState()
    val selectedStyle by viewModel.selectedStyle.collectAsState()
    val photoCountError by viewModel.photoCountError.collectAsState()
    val isSaving by viewModel.isSaving.collectAsState()
    val scope = rememberCoroutineScope()

    var styleExpanded by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 20),
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) viewModel.setPhotos(uris)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("New Food Note") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            // ---- Photo Section ----
            Text("Photos", style = MaterialTheme.typography.titleMedium)
            Text(
                "Select 5-20 food photos",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(photos) { uri ->
                    AsyncImage(
                        model = uri,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(72.dp)
                            .clip(MaterialTheme.shapes.medium)
                            .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.medium),
                    )
                }
                item {
                    Surface(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(MaterialTheme.shapes.medium)
                            .border(2.dp, MaterialTheme.colorScheme.primary, MaterialTheme.shapes.medium)
                            .clickable { photoPickerLauncher.launch(PickVisualMedia.ImageOnly) },
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Add, contentDescription = "Add photos",
                                tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }

            if (photoCountError != null) {
                Text(photoCountError!!, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error)
            }

            // ---- Food Info Section ----
            Text("Food Info", style = MaterialTheme.typography.titleMedium)

            OutlinedTextField(
                value = foodInfo.dishName,
                onValueChange = { viewModel.updateFoodInfo(foodInfo.copy(dishName = it)) },
                label = { Text("Dish name *") },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = foodInfo.restaurantName,
                onValueChange = { viewModel.updateFoodInfo(foodInfo.copy(restaurantName = it)) },
                label = { Text("Restaurant name *") },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
            )

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = foodInfo.location,
                    onValueChange = { viewModel.updateFoodInfo(foodInfo.copy(location = it)) },
                    label = { Text("Location") }, singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = foodInfo.mealDate,
                    onValueChange = { viewModel.updateFoodInfo(foodInfo.copy(mealDate = it)) },
                    label = { Text("Date") }, singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }

            OutlinedTextField(
                value = foodInfo.tasteNotes,
                onValueChange = { viewModel.updateFoodInfo(foodInfo.copy(tasteNotes = it)) },
                label = { Text("Taste notes") }, minLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = foodInfo.priceOrRating,
                onValueChange = { viewModel.updateFoodInfo(foodInfo.copy(priceOrRating = it)) },
                label = { Text("Price or rating") }, singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = foodInfo.vibeNotes,
                onValueChange = { viewModel.updateFoodInfo(foodInfo.copy(vibeNotes = it)) },
                label = { Text("Atmosphere / vibe") }, minLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = foodInfo.personalNotes,
                onValueChange = { viewModel.updateFoodInfo(foodInfo.copy(personalNotes = it)) },
                label = { Text("Personal notes (optional)") }, minLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )

            // ---- Style Section ----
            Text("Style", style = MaterialTheme.typography.titleMedium)

            ExposedDropdownMenuBox(expanded = styleExpanded, onExpandedChange = { styleExpanded = it }) {
                OutlinedTextField(
                    value = selectedStyle.displayName, onValueChange = {}, readOnly = true,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = styleExpanded) },
                    modifier = Modifier.fillMaxWidth().menuAnchor(),
                )
                ExposedDropdownMenu(expanded = styleExpanded, onDismissRequest = { styleExpanded = false }) {
                    NoteStyle.entries.forEach { style ->
                        DropdownMenuItem(
                            text = { Text(style.displayName) },
                            onClick = { viewModel.setStyle(style); styleExpanded = false },
                        )
                    }
                }
            }

            errorMessage?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }

            // ---- Generate Button ----
            Button(
                onClick = {
                    scope.launch {
                        try {
                            val draftId = viewModel.saveDraftSuspend()
                            onDraftSaved(draftId)
                        } catch (e: Exception) {
                            errorMessage = e.message ?: "Failed to save draft"
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                enabled = foodInfo.isValid() && photos.size in 5..20 && !isSaving,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp,
                    )
                } else {
                    Text("✨ Generate Note")
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}
