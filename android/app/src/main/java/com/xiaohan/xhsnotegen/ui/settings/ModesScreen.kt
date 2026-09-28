package com.xiaohan.xhsnotegen.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.xiaohan.xhsnotegen.ai.ModeStore
import com.xiaohan.xhsnotegen.domain.BuiltInModes
import com.xiaohan.xhsnotegen.domain.FieldSlot
import com.xiaohan.xhsnotegen.domain.FieldSpec
import com.xiaohan.xhsnotegen.domain.NoteStyle
import com.xiaohan.xhsnotegen.domain.WritingMode
import com.xiaohan.xhsnotegen.ui.components.SectionCard
import com.xiaohan.xhsnotegen.ui.components.softFieldColors
import com.xiaohan.xhsnotegen.ui.generate.FoodPrompts

/** All writing modes: built-in and your own. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModesScreen(onNavigateBack: () -> Unit, onEdit: (String) -> Unit) {
    val modes by ModeStore.modes.collectAsState()
    var creating by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Writing modes") },
                navigationIcon = { IconButton(onClick = onNavigateBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { creating = true },
                icon = { Icon(Icons.Filled.Add, null) },
                text = { Text("New mode") },
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Text(
                    "Each mode has its own form, AI prompt and root tag. Every note you write in a mode gets its root tag, " +
                        "so notes are grouped by kind automatically.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }
            items(modes, key = { it.key }) { m ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.medium)
                        .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                        .clickable { onEdit(m.key) }
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(m.name, style = MaterialTheme.typography.titleMedium)
                        Text(
                            listOfNotNull(
                                m.rootTag.takeIf { it.isNotBlank() }?.let { "#$it" },
                                if (m.builtIn) (if (ModeStore.isCustomized(m.key)) "Built-in · edited" else "Built-in") else "Your mode",
                            ).joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Icon(Icons.Outlined.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }

    if (creating) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { creating = false },
            title = { Text("New mode") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextField(
                        value = name, onValueChange = { name = it }, singleLine = true,
                        placeholder = { Text("e.g. Coffee, Hiking, Concerts") },
                        shape = MaterialTheme.shapes.medium, colors = softFieldColors(), modifier = Modifier.fillMaxWidth(),
                    )
                    Text("It starts with a general prompt you can then tailor.", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            confirmButton = {
                TextButton(onClick = { creating = false; onEdit(ModeStore.create(name).key) }, enabled = name.isNotBlank()) { Text("Create") }
            },
            dismissButton = { TextButton(onClick = { creating = false }) { Text("Cancel") } },
        )
    }
}

/**
 * Edit one mode. Changes save as you type. Built-in modes can be reset to the
 * app's defaults; your own modes can be deleted (their notes are kept).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModeEditorScreen(modeKey: String, onNavigateBack: () -> Unit) {
    val modes by ModeStore.modes.collectAsState()
    val mode = modes.firstOrNull { it.key == modeKey } ?: run { LaunchedEffect(Unit) { onNavigateBack() }; return }
    // Local copy, keyed so a reset reloads it.
    var draft by remember(modeKey, ModeStore.isCustomized(modeKey)) { mutableStateOf(mode) }
    var confirm by remember { mutableStateOf<String?>(null) } // "reset" | "delete"

    fun update(m: WritingMode) { draft = m; ModeStore.save(m) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(draft.name) },
                navigationIcon = { IconButton(onClick = onNavigateBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = {
                    if (mode.builtIn) {
                        TextButton(onClick = { confirm = "reset" }, enabled = ModeStore.isCustomized(modeKey)) { Text("Reset") }
                    } else {
                        IconButton(onClick = { confirm = "delete" }) {
                            Icon(Icons.Outlined.Delete, "Delete mode", tint = MaterialTheme.colorScheme.error)
                        }
                    }
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
            SectionCard(title = "Basics") {
                Field("Name", draft.name) { update(draft.copy(name = it)) }
                Field("Root tag", draft.rootTag, prefix = "#",
                    support = "Added to every note in this mode; other tags can sit under it") { update(draft.copy(rootTag = it)) }
            }

            SectionCard(title = "Form", subtitle = "Labels on the new-note screen, and how each is named for the AI") {
                FieldSlot.entries.forEach { slot ->
                    val spec = draft.field(slot)
                    Text(slotName(slot), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Field("Label", spec.label, Modifier.weight(1.4f)) { setField(draft, slot, spec.copy(label = it), ::update) }
                        Field("For AI", spec.promptKey, Modifier.weight(1f)) { setField(draft, slot, spec.copy(promptKey = it), ::update) }
                    }
                    Field("Hint", spec.hint) { setField(draft, slot, spec.copy(hint = it), ::update) }
                }
            }

            SectionCard(title = "AI instructions", subtitle = "${draft.instructions.length} characters · tip: replace the examples with notes you wrote") {
                TextField(
                    value = draft.instructions, onValueChange = { update(draft.copy(instructions = it)) },
                    modifier = Modifier.fillMaxWidth(), minLines = 12,
                    textStyle = MaterialTheme.typography.bodyMedium,
                    shape = MaterialTheme.shapes.medium, colors = softFieldColors(),
                )
                Field("Heading above the facts", draft.promptHeading, support = "e.g. 这次吃的 / 这次去的") {
                    update(draft.copy(promptHeading = it))
                }
            }

            SectionCard(title = "Styles", subtitle = "How each of the four versions should be written") {
                NoteStyle.entries.forEach { style ->
                    Text(style.displayName, style = MaterialTheme.typography.titleSmall)
                    TextField(
                        value = draft.styles[style.key].orEmpty(),
                        onValueChange = { update(draft.copy(styles = draft.styles + (style.key to it))) },
                        modifier = Modifier.fillMaxWidth(), minLines = 3,
                        textStyle = MaterialTheme.typography.bodyMedium,
                        shape = MaterialTheme.shapes.medium, colors = softFieldColors(),
                    )
                }
            }

            LockedOutput()
            Spacer(Modifier.height(16.dp))
        }
    }

    when (confirm) {
        "reset" -> AlertDialog(
            onDismissRequest = { confirm = null },
            icon = { Icon(Icons.Outlined.RestartAlt, null) },
            title = { Text("Reset ${mode.name}?") },
            text = { Text("Name, root tag, form labels, prompt and styles go back to the app's defaults.") },
            confirmButton = {
                TextButton(onClick = {
                    ModeStore.resetBuiltIn(modeKey)
                    draft = BuiltInModes.builtIn(modeKey) ?: draft
                    confirm = null
                }) { Text("Reset") }
            },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("Cancel") } },
        )
        "delete" -> AlertDialog(
            onDismissRequest = { confirm = null },
            icon = { Icon(Icons.Outlined.Delete, null) },
            title = { Text("Delete ${mode.name}?") },
            text = { Text("Notes written in this mode are kept (they'll use the Food prompt if rewritten), and so is the #${mode.rootTag} tag.") },
            confirmButton = {
                TextButton(onClick = { confirm = null; ModeStore.delete(modeKey); onNavigateBack() }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("Cancel") } },
        )
    }
}

private fun setField(m: WritingMode, slot: FieldSlot, spec: FieldSpec, update: (WritingMode) -> Unit) =
    update(m.copy(fields = m.fields + (slot to spec)))

private fun slotName(slot: FieldSlot) = when (slot) {
    FieldSlot.SUBJECT -> "Main field (required)"
    FieldSlot.PLACE -> "Where (required)"
    FieldSlot.FEELING -> "Your words 1"
    FieldSlot.COST -> "Your words 2"
    FieldSlot.SCENE -> "Your words 3"
    FieldSlot.OTHER -> "Your words 4"
}

@Composable
private fun Field(
    label: String, value: String, modifier: Modifier = Modifier,
    prefix: String? = null, support: String? = null, onChange: (String) -> Unit,
) {
    TextField(
        value = value, onValueChange = onChange, modifier = modifier.fillMaxWidth(),
        label = { Text(label) }, singleLine = true,
        prefix = prefix?.let { { Text(it) } },
        supportingText = support?.let { { Text(it) } },
        shape = MaterialTheme.shapes.medium, colors = softFieldColors(),
    )
}

/** The fixed JSON section, shown for transparency but not editable. */
@Composable
private fun LockedOutput() {
    var expanded by remember { mutableStateOf(false) }
    SectionCard(
        title = "Output format",
        subtitle = "Added automatically — the app needs it to read the answer",
        trailing = { Icon(Icons.Outlined.Lock, "Not editable", tint = MaterialTheme.colorScheme.onSurfaceVariant) },
    ) {
        TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "Hide" else "Show") }
        AnimatedVisibility(expanded) {
            Text(FoodPrompts.OUTPUT_RULES, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
