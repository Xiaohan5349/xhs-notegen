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
import kotlin.math.roundToInt
import com.xiaohan.xhsnotegen.ai.ModeStore
import com.xiaohan.xhsnotegen.domain.BuiltInModes
import com.xiaohan.xhsnotegen.domain.FieldSlot
import com.xiaohan.xhsnotegen.domain.NoteStyle
import com.xiaohan.xhsnotegen.domain.PromptField
import com.xiaohan.xhsnotegen.domain.TagTree
import com.xiaohan.xhsnotegen.domain.PromptLanguage
import com.xiaohan.xhsnotegen.domain.WritingMode
import com.xiaohan.xhsnotegen.i18n.LanguageStore
import com.xiaohan.xhsnotegen.i18n.tr
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
                title = { Text(tr("Writing modes", "写作模式")) },
                navigationIcon = { IconButton(onClick = onNavigateBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, tr("Back", "返回")) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { creating = true },
                icon = { Icon(Icons.Filled.Add, null) },
                text = { Text(tr("New mode", "新建模式")) },
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
                    tr(
                        "Each mode has its own form, AI prompt, note language, photo limit and root tag. Every note you write in a mode gets its root tag, " +
                            "so notes are grouped by kind automatically.",
                        "每个模式有自己的表单、AI 提示词、笔记语言、照片上限和根标签。在某个模式下写的笔记会自动带上它的根标签，按类别归好。",
                    ),
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
                                m.rootTag.takeIf { it.isNotBlank() }?.let { TagTree.parsePath(it).joinToString(" › ") },
                                if (m.builtIn) (if (ModeStore.isCustomized(m.key)) tr("Built-in · edited", "内置 · 已修改") else tr("Built-in", "内置")) else tr("Your mode", "自定义"),
                                m.language.label,
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
            title = { Text(tr("New mode", "新建模式")) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextField(
                        value = name, onValueChange = { name = it }, singleLine = true,
                        placeholder = { Text(tr("e.g. Coffee, Hiking, Concerts", "比如：咖啡、徒步、演唱会")) },
                        shape = MaterialTheme.shapes.medium, colors = softFieldColors(), modifier = Modifier.fillMaxWidth(),
                    )
                    Text(tr("It starts with a general prompt you can then tailor.", "先用通用提示词，之后可以再改。"), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            confirmButton = {
                TextButton(onClick = { creating = false; onEdit(ModeStore.create(name).key) }, enabled = name.isNotBlank()) { Text(tr("Create", "创建")) }
            },
            dismissButton = { TextButton(onClick = { creating = false }) { Text(tr("Cancel", "取消")) } },
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
    var draft by remember(modeKey, ModeStore.isCustomized(modeKey), LanguageStore.isZh) { mutableStateOf(mode) }
    var confirm by remember { mutableStateOf<String?>(null) } // "reset" | "delete"

    fun update(m: WritingMode) { draft = m; ModeStore.save(m) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(draft.name) },
                navigationIcon = { IconButton(onClick = onNavigateBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, tr("Back", "返回")) } },
                actions = {
                    if (mode.builtIn) {
                        TextButton(onClick = { confirm = "reset" }, enabled = ModeStore.isCustomized(modeKey)) { Text(tr("Reset", "恢复默认")) }
                    } else {
                        IconButton(onClick = { confirm = "delete" }) {
                            Icon(Icons.Outlined.Delete, tr("Delete mode", "删除模式"), tint = MaterialTheme.colorScheme.error)
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
            SectionCard(title = tr("Basics", "基本")) {
                Field(tr("Name", "名称"), draft.name) { update(draft.copy(name = it)) }
                Field(tr("Root tag", "根标签"), draft.rootTag,
                    support = tr(
                        "Added to every note in this mode. Use / for more levels, e.g. 生活/咖啡",
                        "会加到这个模式的每篇笔记上。用 / 分多级，比如 生活/咖啡",
                    )) { update(draft.copy(rootTag = it)) }
            }

            SectionCard(title = tr("Notes", "笔记"), subtitle = tr("Separate from the app language", "和界面语言分开设置")) {
                Text(tr("Write notes in", "笔记语言"), style = MaterialTheme.typography.labelLarge)
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    PromptLanguage.entries.forEachIndexed { i, lang ->
                        SegmentedButton(
                            selected = draft.language == lang,
                            onClick = { update(draft.copy(language = lang)) },
                            shape = SegmentedButtonDefaults.itemShape(i, PromptLanguage.entries.size),
                        ) { Text(lang.label) }
                    }
                }
                Text(
                    tr(
                        "Uses this mode's ${draft.language.label} prompt below. Each language keeps its own prompt.",
                        "使用下面的${draft.language.label}提示词。两种语言的提示词分开保存。",
                    ),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(tr("Photo limit", "照片上限"), style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                    Text(tr("Up to ${draft.maxPhotos}", "最多 ${draft.maxPhotos} 张"), style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary)
                }
                Slider(
                    value = draft.maxPhotos.toFloat(),
                    onValueChange = { v ->
                        val n = v.roundToInt()
                        if (n != draft.maxPhotos) update(draft.copy(maxPhotos = n))
                    },
                    valueRange = WritingMode.MIN_PHOTOS.toFloat()..WritingMode.MAX_PHOTOS_LIMIT.toFloat(),
                    steps = WritingMode.MAX_PHOTOS_LIMIT - WritingMode.MIN_PHOTOS - 1,
                )
            }

            SectionCard(
                title = tr("Form", "表单"),
                subtitle = tr("Labels on the new-note screen, and how each is named for the AI (${draft.language.label})",
                    "新建笔记页上的标签，以及告诉 AI 的名称（${draft.language.label}）"),
            ) {
                FieldSlot.entries.forEach { slot ->
                    val spec = draft.field(slot)
                    Text(slotName(slot), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Field(tr("Label", "标签"), spec.label, Modifier.weight(1.4f)) { update(draft.copy(labels = draft.labels + (slot to it))) }
                        Field(tr("For AI", "给 AI"), spec.promptKey, Modifier.weight(1f)) { setPromptField(draft, slot, PromptField(it, spec.hint), ::update) }
                    }
                    Field(tr("Hint", "提示"), spec.hint) { setPromptField(draft, slot, PromptField(spec.promptKey, it), ::update) }
                }
            }

            SectionCard(
                title = tr("AI instructions (${draft.language.label})", "AI 提示词（${draft.language.label}）"),
                subtitle = tr("${draft.instructions.length} characters · tip: replace the examples with notes you wrote",
                    "${draft.instructions.length} 字 · 小技巧：把示例换成你自己写的笔记"),
            ) {
                TextField(
                    value = draft.instructions, onValueChange = { update(draft.withPrompt(draft.prompt.copy(instructions = it))) },
                    modifier = Modifier.fillMaxWidth(), minLines = 12,
                    textStyle = MaterialTheme.typography.bodyMedium,
                    shape = MaterialTheme.shapes.medium, colors = softFieldColors(),
                )
                Field(tr("Heading above the facts", "事实上方的小标题"), draft.promptHeading,
                    support = if (draft.language == PromptLanguage.EN) "e.g. This meal / This trip" else "例如 这次吃的 / 这次去的") {
                    update(draft.withPrompt(draft.prompt.copy(heading = it)))
                }
            }

            SectionCard(title = tr("Styles", "风格"), subtitle = tr("How each of the four versions should be written", "四个版本分别怎么写")) {
                NoteStyle.entries.forEach { style ->
                    Text(style.displayName, style = MaterialTheme.typography.titleSmall)
                    TextField(
                        value = draft.styles[style.key].orEmpty(),
                        onValueChange = { update(draft.withPrompt(draft.prompt.copy(styles = draft.styles + (style.key to it)))) },
                        modifier = Modifier.fillMaxWidth(), minLines = 3,
                        textStyle = MaterialTheme.typography.bodyMedium,
                        shape = MaterialTheme.shapes.medium, colors = softFieldColors(),
                    )
                }
            }

            LockedOutput(draft.language)
            Spacer(Modifier.height(16.dp))
        }
    }

    when (confirm) {
        "reset" -> AlertDialog(
            onDismissRequest = { confirm = null },
            icon = { Icon(Icons.Outlined.RestartAlt, null) },
            title = { Text(tr("Reset ${mode.name}?", "把「${mode.name}」恢复默认？")) },
            text = { Text(tr("Name, root tag, form labels, note language, photo limit, both prompts and styles go back to the app's defaults.", "名称、根标签、表单标签、笔记语言、照片上限、两种语言的提示词和风格都会恢复成默认。")) },
            confirmButton = {
                TextButton(onClick = {
                    ModeStore.resetBuiltIn(modeKey)
                    draft = BuiltInModes.builtIn(modeKey) ?: draft
                    confirm = null
                }) { Text(tr("Reset", "恢复默认")) }
            },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text(tr("Cancel", "取消")) } },
        )
        "delete" -> AlertDialog(
            onDismissRequest = { confirm = null },
            icon = { Icon(Icons.Outlined.Delete, null) },
            title = { Text(tr("Delete ${mode.name}?", "删除「${mode.name}」？")) },
            text = { Text(tr("Notes written in this mode are kept (they'll use the Food prompt if rewritten), and so is the ${mode.rootTag} tag.", "这个模式下写的笔记会保留（重写时改用美食提示词），${mode.rootTag} 标签也会保留。")) },
            confirmButton = {
                TextButton(onClick = { confirm = null; ModeStore.delete(modeKey); onNavigateBack() }) {
                    Text(tr("Delete", "删除"), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text(tr("Cancel", "取消")) } },
        )
    }
}

private fun setPromptField(m: WritingMode, slot: FieldSlot, field: PromptField, update: (WritingMode) -> Unit) =
    update(m.withPrompt(m.prompt.copy(fields = m.prompt.fields + (slot to field))))

private fun slotName(slot: FieldSlot) = when (slot) {
    FieldSlot.SUBJECT -> tr("Main field (required)", "主要内容（必填）")
    FieldSlot.PLACE -> tr("Where (required)", "地点（必填）")
    FieldSlot.FEELING -> tr("Your words 1", "你的话 1")
    FieldSlot.COST -> tr("Your words 2", "你的话 2")
    FieldSlot.SCENE -> tr("Your words 3", "你的话 3")
    FieldSlot.OTHER -> tr("Your words 4", "你的话 4")
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
private fun LockedOutput(language: PromptLanguage) {
    var expanded by remember { mutableStateOf(false) }
    SectionCard(
        title = tr("Output format", "输出格式"),
        subtitle = tr("Added automatically — the app needs it to read the answer", "自动附加，App 需要它来读取回答"),
        trailing = { Icon(Icons.Outlined.Lock, tr("Not editable", "不可编辑"), tint = MaterialTheme.colorScheme.onSurfaceVariant) },
    ) {
        TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) tr("Hide", "收起") else tr("Show", "展开")) }
        AnimatedVisibility(expanded) {
            Text(FoodPrompts.outputRules(language), style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
