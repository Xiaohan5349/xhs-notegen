package com.xiaohan.xhsnotegen.ui.components

import com.xiaohan.xhsnotegen.ui.theme.app
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.xiaohan.xhsnotegen.ai.AiConfig
import com.xiaohan.xhsnotegen.ai.AiModel
import com.xiaohan.xhsnotegen.ai.AiProvider
import com.xiaohan.xhsnotegen.ai.AiSettings
import com.xiaohan.xhsnotegen.i18n.tr

/**
 * "Writing with: Gemini 3.8 Flash ▾" — switch provider/model right where you
 * write. Changes the same setting as Settings → AI writing, so it sticks.
 */
@Composable
fun ModelChip(onOpenSettings: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var config by remember { mutableStateOf(AiSettings.current(context)) }
    var showSheet by remember { mutableStateOf(false) }
    // Keys/models may have changed in Settings meanwhile.
    LifecycleResumeEffect(Unit) {
        config = AiSettings.current(context)
        onPauseOrDispose {}
    }

    val problem = config.problem()
    AssistChip(
        onClick = { showSheet = true },
        modifier = modifier,
        shape = CircleShape,
        leadingIcon = {
            Icon(Icons.Filled.AutoAwesome, null, Modifier.size(16.dp),
                tint = if (problem != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
        },
        label = {
            Text(
                if (problem != null) tr("${config.label} · needs setup", "${config.label} · 需要设置")
                else tr("Writing with ${config.label}", "写作模型：${config.label}"),
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        },
        trailingIcon = { Icon(Icons.Filled.ArrowDropDown, null, Modifier.size(18.dp)) },
    )

    if (showSheet) {
        ModelPickerSheet(
            current = config,
            onPick = { p, model ->
                AiSettings.setProvider(context, p)
                AiSettings.setModel(context, p, model)
                config = AiSettings.current(context)
                showSheet = false
            },
            onOpenSettings = { showSheet = false; onOpenSettings() },
            onDismiss = { showSheet = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModelPickerSheet(
    current: AiConfig,
    onPick: (AiProvider, String) -> Unit,
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.app.tile) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(tr("Choose a model", "选择模型"), style = MaterialTheme.typography.headlineSmall)
            Text(tr("Used for new notes and rewrites. Each version shows which model wrote it.", "用于新笔记和重写。每个版本都会显示写作模型。"),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

            AiProvider.entries.forEach { p ->
                val hasKey = AiSettings.apiKey(context, p).isNotBlank()
                val customReady = p == AiProvider.CUSTOM && AiSettings.customBaseUrl(context).isNotBlank()
                val usable = if (p == AiProvider.CUSTOM) customReady else hasKey
                // Built-in models, plus a saved "other" model id for this provider.
                val saved = AiSettings.model(context, p)
                val models = p.models + listOfNotNull(
                    saved.takeIf { it.isNotBlank() && p.findModel(it) == null }?.let { AiModel(it, it, tr("Your model", "自定义模型")) }
                )
                if (models.isEmpty() && !usable) return@forEach

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Eyebrow(p.displayName, Modifier.weight(1f))
                        if (!usable) {
                            TextButton(onClick = onOpenSettings) {
                                Icon(Icons.Outlined.Key, null, Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(if (p == AiProvider.CUSTOM) tr("Set up", "设置") else tr("Add key", "添加密钥"))
                            }
                        }
                    }
                    Column(Modifier.clip(MaterialTheme.shapes.small).background(MaterialTheme.app.inset)) {
                        models.forEach { m ->
                            val selected = current.provider == p && current.model == m.id
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .selectable(selected = selected, enabled = usable, role = Role.RadioButton) { onPick(p, m.id) }
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                RadioButton(selected = selected, onClick = null, enabled = usable)
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(m.label, style = MaterialTheme.typography.titleSmall,
                                        color = if (usable) MaterialTheme.colorScheme.onSurface
                                                else MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(m.note, style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }

            OutlinedButton(onClick = onOpenSettings, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Outlined.Settings, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(tr("API keys, custom models & test", "密钥、自定义模型和测试"))
            }
        }
    }
}
