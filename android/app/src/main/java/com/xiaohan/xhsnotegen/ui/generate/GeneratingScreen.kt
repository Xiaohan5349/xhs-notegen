package com.xiaohan.xhsnotegen.ui.generate

import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun GeneratingScreen(
    draftId: Long,
    onGenerationComplete: (Long) -> Unit,
    onError: () -> Unit,
    viewModel: GenerationViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(draftId) { viewModel.generate(draftId) }

    LaunchedEffect(state.isComplete) {
        if (state.isComplete) onGenerationComplete(draftId)
    }

    val infiniteTransition = rememberInfiniteTransition(label = "dots")
    val dotCount by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 3f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ), label = "dots",
    )

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            if (state.error != null) {
                Icon(Icons.Default.Warning, contentDescription = null,
                    modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.error)
                Text("Generation failed", style = MaterialTheme.typography.titleMedium)
                Text(state.error!!, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = onError) { Text("Back") }
                    Button(onClick = { viewModel.generate(draftId) }) { Text("Retry") }
                }
            } else {
                CircularProgressIndicator(
                    modifier = Modifier.size(64.dp),
                    color = MaterialTheme.colorScheme.primary, strokeWidth = 4.dp,
                )
                Text(
                    when {
                        state.isCompressing -> "📸 ${state.progress}"
                        state.isGenerating -> "🧠 ${state.progress}"
                        else -> state.progress
                    },
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    ".".repeat(dotCount.toInt()) + " ".repeat(3 - dotCount.toInt()),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "Generating 2-3 style variants...",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
