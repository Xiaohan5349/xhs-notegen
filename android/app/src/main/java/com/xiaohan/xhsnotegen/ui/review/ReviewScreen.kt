package com.xiaohan.xhsnotegen.ui.review

import android.net.Uri
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import com.xiaohan.xhsnotegen.ui.publish.XiaohongshuSharePublisher

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewScreen(
    draftId: Long,
    onNavigateBack: () -> Unit,
    onNavigateToLogin: () -> Unit = {},
    viewModel: ReviewViewModel = viewModel(),
) {
    val draft by viewModel.draft.collectAsState()
    val selectedIndex by viewModel.selectedVariantIndex.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var isPublishing by remember { mutableStateOf(false) }

    LaunchedEffect(draftId) { viewModel.load(draftId) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Review & Edit") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(onClick = {
                        viewModel.saveChanges()
                        scope.launch { snackbarHostState.showSnackbar("Saved") }
                    }) { Text("Save") }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        draft?.let { d ->
            val variants = d.variants
            if (variants.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Text("No variants generated", style = MaterialTheme.typography.bodyLarge)
                }
                return@Scaffold
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Variants", style = MaterialTheme.typography.titleMedium)
                    val isRegenerating by viewModel.isRegenerating.collectAsState()
                    TextButton(
                        onClick = { viewModel.regenerateAllStyles() },
                        enabled = !isRegenerating,
                    ) {
                        if (isRegenerating) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp), strokeWidth = 2.dp,
                            )
                            Spacer(Modifier.width(4.dp))
                            Text("Regenerating...")
                        } else {
                            Text("🔄 Regenerate All")
                        }
                    }
                }

                val regenerateError by viewModel.regenerateError.collectAsState()
                if (regenerateError != null) {
                    SelectionContainer {
                        Text(regenerateError!!, color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall)
                    }
                }

                ScrollableTabRow(selectedTabIndex = selectedIndex) {
                    variants.forEachIndexed { index, variant ->
                        Tab(
                            selected = index == selectedIndex,
                            onClick = { viewModel.selectVariant(index) },
                            text = {
                                Column {
                                    Text(variant.styleLabel, style = MaterialTheme.typography.labelLarge,
                                        fontWeight = if (index == 0) FontWeight.Bold else FontWeight.Normal)
                                    if (index == 0) Text("Preferred", style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary)
                                }
                            },
                        )
                    }
                }

                val v = variants[selectedIndex]

                if (v.warnings.isNotEmpty()) {
                    Card(colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.1f)
                    )) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("⚠️", style = MaterialTheme.typography.bodyLarge)
                            Spacer(Modifier.width(8.dp))
                            Text(v.warnings.joinToString("; "), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }

                OutlinedTextField(value = v.title, onValueChange = { viewModel.updateVariantTitle(it) },
                    label = { Text("Title") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = v.body, onValueChange = { viewModel.updateVariantBody(it) },
                    label = { Text("Body") }, minLines = 8, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    value = v.hashtags.joinToString(" "),
                    onValueChange = { text ->
                        viewModel.updateVariantHashtags(
                            text.trim().split("\\s+".toRegex()).filter { it.isNotBlank() }
                        )
                    },
                    label = { Text("Hashtags (space-separated)") },
                    singleLine = false,
                    minLines = 1,
                    modifier = Modifier.fillMaxWidth(),
                )

                Text("Photos (tap to toggle publish)", style = MaterialTheme.typography.titleMedium)
                Text("${d.selectedPublishPhotoUris.size}/${d.photoUris.size} selected",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)

                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    itemsIndexed(d.photoUris) { index, uriStr ->
                        val uri = Uri.parse(uriStr)
                        val isSelected = uriStr in d.selectedPublishPhotoUris
                        Box(modifier = Modifier
                            .size(80.dp)
                            .clip(MaterialTheme.shapes.medium)
                            .border(
                                width = if (isSelected) 3.dp else 1.dp,
                                color = if (isSelected) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.outline,
                                shape = MaterialTheme.shapes.medium,
                            )
                            .clickable { viewModel.togglePublishPhoto(index) }
                        ) {
                            AsyncImage(model = uri, contentDescription = null,
                                contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                            if (isSelected) {
                                Surface(
                                    modifier = Modifier.align(Alignment.TopEnd).padding(4.dp),
                                    shape = MaterialTheme.shapes.small,
                                    color = MaterialTheme.colorScheme.primary,
                                ) {
                                    Icon(Icons.Default.Check, contentDescription = "Selected",
                                        modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.onPrimary)
                                }
                            }
                        }
                    }
                }

                Button(
                    onClick = {
                        viewModel.saveChanges()
                        isPublishing = true
                        scope.launch {
                            val result = XiaohongshuSharePublisher.publish(context, d)
                            isPublishing = false
                            when (result) {
                                is XiaohongshuSharePublisher.PublishResult.Success -> {
                                    viewModel.markShared()
                                    snackbarHostState.showSnackbar("Published! ${result.shareLink}")
                                }
                                is XiaohongshuSharePublisher.PublishResult.NeedsLogin -> {
                                    onNavigateToLogin()
                                }
                                is XiaohongshuSharePublisher.PublishResult.Handoff -> {
                                    val snackbarResult = snackbarHostState.showSnackbar(
                                        message = "已准备好：文字已复制，照片已保存。发布完成后可标记",
                                        actionLabel = "标记已发布",
                                        duration = SnackbarDuration.Long,
                                    )
                                    if (snackbarResult == SnackbarResult.ActionPerformed) {
                                        viewModel.markShared()
                                        snackbarHostState.showSnackbar("已标记为已发布")
                                    }
                                }
                                is XiaohongshuSharePublisher.PublishResult.Error -> {
                                    snackbarHostState.showSnackbar(
                                        message = result.message,
                                        duration = SnackbarDuration.Long,
                                    )
                                }
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    enabled = !isPublishing,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                ) {
                    if (isPublishing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Publishing...")
                    } else {
                        Text("Share to Xiaohongshu")
                    }
                }
            }
        }
    }
}
