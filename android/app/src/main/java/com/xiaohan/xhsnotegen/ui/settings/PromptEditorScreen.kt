package com.xiaohan.xhsnotegen.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.xiaohan.xhsnotegen.ai.PromptStore
import com.xiaohan.xhsnotegen.domain.NoteStyle
import com.xiaohan.xhsnotegen.ui.components.SectionCard
import com.xiaohan.xhsnotegen.ui.components.softFieldColors
import com.xiaohan.xhsnotegen.ui.generate.FoodPrompts

/**
 * Edit what the AI reads before writing. Changes save as you type and apply
 * to the next note or rewrite. The JSON output format is fixed and appended
 * automatically, so an edit here can never break parsing.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PromptEditorScreen(onNavigateBack: () -> Unit) {
    val context = LocalContext.current
    var instructions by remember { mutableStateOf(PromptStore.instructions(context)) }
    val styleTexts = remember {
        mutableStateMapOf<NoteStyle, String>().apply {
            NoteStyle.entries.forEach { put(it, PromptStore.styleInstruction(context, it)) }
        }
    }
    var confirmResetAll by remember { mutableStateOf(false) }

    fun resetAll() {
        PromptStore.resetAll(context)
        instructions = FoodPrompts.DEFAULT_SYSTEM_PROMPT
        NoteStyle.entries.forEach { styleTexts[it] = FoodPrompts.defaultStyleInstruction(it) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Writing prompt") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(onClick = { confirmResetAll = true }) { Text("Reset all") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
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
            Text(
                "This is what the AI reads before writing each note. Edits save as you type and apply to the next note or rewrite. " +
                    "Tip: replacing the two 语气示例 with notes you wrote yourself changes the voice the most.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            SectionCard(
                title = "Main instructions",
                subtitle = "${instructions.length} characters",
                trailing = {
                    ResetButton(visible = instructions.trim() != FoodPrompts.DEFAULT_SYSTEM_PROMPT.trim()) {
                        instructions = FoodPrompts.DEFAULT_SYSTEM_PROMPT
                        PromptStore.setInstructions(context, instructions)
                    }
                },
            ) {
                PromptField(instructions, minLines = 14) {
                    instructions = it
                    PromptStore.setInstructions(context, it)
                }
            }

            SectionCard(title = "Styles", subtitle = "How each of the four versions should be written") {
                NoteStyle.entries.forEach { style ->
                    val text = styleTexts[style].orEmpty()
                    val default = FoodPrompts.defaultStyleInstruction(style)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(style.displayName, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                        ResetButton(visible = text.trim() != default.trim()) {
                            styleTexts[style] = default
                            PromptStore.setStyleInstruction(context, style, default)
                        }
                    }
                    PromptField(text, minLines = 3) {
                        styleTexts[style] = it
                        PromptStore.setStyleInstruction(context, style, it)
                    }
                }
            }

            LockedOutputCard()
            Spacer(Modifier.height(16.dp))
        }
    }

    if (confirmResetAll) {
        AlertDialog(
            onDismissRequest = { confirmResetAll = false },
            icon = { Icon(Icons.Outlined.RestartAlt, null) },
            title = { Text("Reset the whole prompt?") },
            text = { Text("The main instructions and all four styles go back to the app's defaults.") },
            confirmButton = {
                TextButton(onClick = { resetAll(); confirmResetAll = false }) { Text("Reset") }
            },
            dismissButton = { TextButton(onClick = { confirmResetAll = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun PromptField(value: String, minLines: Int, onChange: (String) -> Unit) {
    TextField(
        value = value,
        onValueChange = onChange,
        modifier = Modifier.fillMaxWidth(),
        minLines = minLines,
        textStyle = MaterialTheme.typography.bodyMedium,
        shape = MaterialTheme.shapes.medium,
        colors = softFieldColors(),
        placeholder = { Text("Empty uses the default") },
    )
}

@Composable
private fun ResetButton(visible: Boolean, onClick: () -> Unit) {
    AnimatedVisibility(visible) {
        TextButton(onClick = onClick) {
            Icon(Icons.Outlined.RestartAlt, null, Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Reset")
        }
    }
}

/** The fixed JSON section, shown for transparency but not editable. */
@Composable
private fun LockedOutputCard() {
    var expanded by remember { mutableStateOf(false) }
    SectionCard(
        title = "Output format",
        subtitle = "Added automatically — the app needs it to read the answer",
        trailing = {
            Icon(Icons.Outlined.Lock, contentDescription = "Not editable",
                tint = MaterialTheme.colorScheme.onSurfaceVariant)
        },
    ) {
        Row(
            Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(if (expanded) "Hide" else "Show", style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
            Icon(if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, null,
                tint = MaterialTheme.colorScheme.primary)
        }
        AnimatedVisibility(expanded) {
            Text(
                FoodPrompts.OUTPUT_RULES,
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
