package com.xiaohan.xhsnotegen.ui.drafts

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.xiaohan.xhsnotegen.domain.NoteDraft
import com.xiaohan.xhsnotegen.domain.NoteStatus
import com.xiaohan.xhsnotegen.ui.generate.GeminiClient
import com.xiaohan.xhsnotegen.ui.publish.XhsAuthStore
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DraftListScreen(
    onCreateClick: () -> Unit,
    onDraftClick: (Long) -> Unit,
    onNavigateToLogin: () -> Unit = {},
    viewModel: DraftListViewModel = viewModel(),
) {
    val drafts by viewModel.drafts.collectAsState()
    val count by viewModel.draftCount.collectAsState()
    val filterTab by viewModel.filterTab.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()) }
    var showApiKeyDialog by remember { mutableStateOf(false) }
    var apiKeyInput by remember { mutableStateOf(GeminiClient.getApiKey(context) ?: "") }

    if (showApiKeyDialog) {
        AlertDialog(
            onDismissRequest = { showApiKeyDialog = false },
            title = { Text("Gemini API Key") },
            text = {
                Column {
                    Text("Get a free key at aistudio.google.com/apikey",
                        style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = apiKeyInput,
                        onValueChange = { apiKeyInput = it },
                        label = { Text("API Key") },
                        singleLine = true,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    GeminiClient.saveApiKey(context, apiKeyInput)
                    showApiKeyDialog = false
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showApiKeyDialog = false }) { Text("Cancel") }
            },
        )
    }

    // Import file picker
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { viewModel.importFromUri(it) }
    }

    // Export: system Save dialog, user picks where to save
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri?.let { viewModel.exportToUri(it) }
    }

    // Handle import/export results
    LaunchedEffect(Unit) {
        viewModel.importResult.collect { result ->
            when (result) {
                is ImportResult.Success ->
                    snackbarHostState.showSnackbar("Imported ${result.count} drafts")
                is ImportResult.Error ->
                    snackbarHostState.showSnackbar(result.message)
            }
        }
    }
    LaunchedEffect(Unit) {
        viewModel.exportResult.collect { result ->
            when (result) {
                is ExportResult.Success ->
                    snackbarHostState.showSnackbar("Exported ${result.count} notes")
                is ExportResult.Error ->
                    snackbarHostState.showSnackbar(result.message)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("My Notes") },
                actions = {
                    // Settings (API key)
                    IconButton(onClick = { showApiKeyDialog = true }) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    // XHS login indicator
                    val isLoggedIn = XhsAuthStore.isLoggedIn(context)
                    IconButton(onClick = onNavigateToLogin) {
                        Icon(
                            if (isLoggedIn) Icons.Default.Star else Icons.Default.Person,
                            contentDescription = if (isLoggedIn) "XHS Logged in" else "Login to XHS",
                            tint = if (isLoggedIn) MaterialTheme.colorScheme.primary
                                   else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = {
                        importLauncher.launch(arrayOf("application/json"))
                    }) {
                        Icon(Icons.Default.FileOpen, contentDescription = "Import")
                    }
                    IconButton(onClick = {
                        exportLauncher.launch("xhs_notes_backup.json")
                    }) {
                        Icon(Icons.Default.Save, contentDescription = "Export")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onCreateClick,
                containerColor = MaterialTheme.colorScheme.primary,
            ) {
                Icon(Icons.Default.Add, contentDescription = "New note")
            }
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            // Filter tabs
            if (drafts.isNotEmpty() || filterTab != 0) {
                ScrollableTabRow(
                    selectedTabIndex = filterTab,
                    modifier = Modifier.fillMaxWidth(),
                    edgePadding = 16.dp,
                ) {
                    Tab(selected = filterTab == 0, onClick = { viewModel.setFilterTab(0) }) {
                        Text("All", modifier = Modifier.padding(12.dp))
                    }
                    Tab(selected = filterTab == 1,
                        onClick = { viewModel.setFilterTab(1) }) {
                        Text("Drafts", modifier = Modifier.padding(12.dp))
                    }
                    Tab(selected = filterTab == 2,
                        onClick = { viewModel.setFilterTab(2) }) {
                        Text("Ready", modifier = Modifier.padding(12.dp))
                    }
                    Tab(selected = filterTab == 3,
                        onClick = { viewModel.setFilterTab(3) }) {
                        Text("Shared", modifier = Modifier.padding(12.dp))
                    }
                }
            }

            if (drafts.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            if (filterTab != 0) "No notes in this category" else "No notes yet",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Tap + to create your first food note",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item {
                        Text(
                            "$count note${if (count != 1) "s" else ""}",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    items(drafts, key = { it.id }) { draft ->
                        DraftCard(
                            draft = draft,
                            dateFormat = dateFormat,
                            onClick = { onDraftClick(draft.id) },
                            onDelete = { viewModel.deleteDraft(draft) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DraftCard(
    draft: NoteDraft,
    dateFormat: SimpleDateFormat,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete note?") },
            text = { Text("This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    onDelete()
                    showDeleteConfirm = false
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
            },
        )
    }

    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = draft.foodInfo.dishNames.ifBlank { "Untitled" },
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                StatusChip(draft.status)
            }

            if (draft.foodInfo.restaurantName.isNotBlank()) {
                Text(
                    draft.foodInfo.restaurantName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            // Location + date row
            if (draft.foodInfo.location.isNotBlank() || draft.foodInfo.mealDate.isNotBlank()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (draft.foodInfo.location.isNotBlank()) {
                        Text(
                            "📍 ${draft.foodInfo.location}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (draft.foodInfo.mealDate.isNotBlank()) {
                        Text(
                            "📅 ${draft.foodInfo.mealDate}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // Body preview
            val bodyPreview = draft.body.ifBlank {
                draft.variants.firstOrNull()?.body?.take(100) ?: ""
            }
            if (bodyPreview.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    bodyPreview,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "${draft.photoUris.size} photos · ${dateFormat.format(Date(draft.updatedAt))}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                IconButton(onClick = { showDeleteConfirm = true }, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusChip(status: NoteStatus) {
    val (label, color) = when (status) {
        NoteStatus.DRAFT -> "Draft" to MaterialTheme.colorScheme.outline
        NoteStatus.GENERATED -> "Generated" to MaterialTheme.colorScheme.tertiary
        NoteStatus.REVIEWED -> "Ready" to MaterialTheme.colorScheme.secondary
        NoteStatus.SHARED -> "Shared" to MaterialTheme.colorScheme.primary
    }
    Surface(shape = MaterialTheme.shapes.small, color = color.copy(alpha = 0.15f)) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            color = color,
        )
    }
}
