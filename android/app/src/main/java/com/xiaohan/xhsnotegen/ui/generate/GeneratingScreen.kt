package com.xiaohan.xhsnotegen.ui.generate

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.RamenDining
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.xiaohan.xhsnotegen.i18n.tr
import com.xiaohan.xhsnotegen.ui.components.EmptyState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GeneratingScreen(
    draftId: Long,
    onGenerationComplete: (Long) -> Unit,
    onError: () -> Unit,
    viewModel: GenerationViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsState()

    // start() is idempotent, so re-running this after rotation is harmless.
    LaunchedEffect(draftId) { viewModel.start(draftId) }
    LaunchedEffect(state.isComplete) {
        if (state.isComplete) onGenerationComplete(draftId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onError) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = tr("Back", "返回"))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        AnimatedContent(
            targetState = state.error != null,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            modifier = Modifier.fillMaxSize().padding(padding),
            label = "generating",
        ) { failed ->
            if (failed) {
                Column(
                    Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    EmptyState(
                        icon = Icons.Outlined.CloudOff,
                        title = tr("Couldn't write the note", "笔记没写成"),
                        body = state.error.orEmpty(),
                        action = {
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                OutlinedButton(onClick = onError) { Text(tr("Back", "返回")) }
                                Button(onClick = { viewModel.retry(draftId) }) { Text(tr("Try again", "重试")) }
                            }
                        },
                    )
                    Text(
                        tr("Your note is saved as a draft — you can come back to it anytime.", "笔记已存为草稿，随时可以回来继续。"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 32.dp),
                    )
                }
            } else {
                Working(state)
            }
        }
    }
}

@Composable
private fun Working(state: GenerationState) {
    Column(
        Modifier.fillMaxSize().padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        PhotoStack(state.photoUris)
        Spacer(Modifier.height(40.dp))
        Text(tr("Writing your note", "正在写笔记"), style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            tr("Four takes, in four styles. Usually under a minute.", "四种风格，各写一篇，通常不到一分钟。"),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(28.dp))

        val writing = state.phase == NoteGenerator.Phase.WRITING
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Step(tr("Looking at your photos", "正在看照片"), done = writing, active = !writing)
            Step(tr("Writing in your voice", "用你的语气写作"), done = false, active = writing)
        }
    }
}

@Composable
private fun Step(label: String, done: Boolean, active: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
            when {
                done -> Box(
                    Modifier.fillMaxSize().clip(CircleShape).background(MaterialTheme.colorScheme.secondary),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.Check, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSecondary)
                }
                active -> CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                else -> Box(Modifier.size(10.dp).clip(CircleShape).background(MaterialTheme.colorScheme.outline))
            }
        }
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            color = if (done || active) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Up to three photos as a loosely stacked, gently floating pile of prints. */
@Composable
private fun PhotoStack(photos: List<String>) {
    val transition = rememberInfiniteTransition(label = "float")
    val float by transition.animateFloat(
        initialValue = -1f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "float",
    )
    val shown = photos.take(3)
    val tilts = listOf(-7f, 5f, -2f)

    Box(Modifier.size(width = 220.dp, height = 220.dp), contentAlignment = Alignment.Center) {
        if (shown.isEmpty()) {
            Box(
                Modifier.size(140.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Outlined.RamenDining, null, Modifier.size(56.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }
        shown.asReversed().forEachIndexed { i, uri ->
            val layer = shown.size - 1 - i // 0 = top of the pile
            Surface(
                modifier = Modifier
                    .size(width = 150.dp, height = 180.dp)
                    .graphicsLayer {
                        rotationZ = tilts[layer] + float * (1.5f - layer * 0.5f)
                        translationY = float * (6f - layer * 2f)
                        translationX = (layer - 1) * 14f
                    }
                    .shadow(8.dp, MaterialTheme.shapes.small),
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.surfaceContainerLowest,
            ) {
                AsyncImage(
                    model = uri,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 24.dp)
                        .clip(MaterialTheme.shapes.extraSmall)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                )
            }
        }
    }
}
