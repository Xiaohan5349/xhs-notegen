package com.xiaohan.xhsnotegen.ui.drafts

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DriveFileMove
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Label
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.xiaohan.xhsnotegen.domain.NoteTag
import com.xiaohan.xhsnotegen.domain.Place
import com.xiaohan.xhsnotegen.domain.TagTree
import com.xiaohan.xhsnotegen.i18n.tr
import com.xiaohan.xhsnotegen.ui.components.softFieldColors

/**
 * Add/remove tags on one or more notes. Each existing tag shows whether all,
 * some or none of the notes have it; tapping cycles to "all" / "none".
 */
@Composable
fun TagsDialog(
    noteCount: Int,
    tags: List<NoteTag>,
    stateOf: (Long) -> Boolean?,
    /** Suggested root for new tags (the notes' mode root tag), if any. */
    defaultParent: NoteTag?,
    onApply: (add: List<String>, remove: List<Long>, newParentId: Long?) -> Unit,
    onDismiss: () -> Unit,
) {
    val byId = tags.associateBy { it.id }
    val tree = remember(tags) { TagTree.ordered(tags) }
    var newParent by remember { mutableStateOf(defaultParent) }
    // null = leave as is (mixed), true = add to all, false = remove from all
    val choices = remember { mutableStateMapOf<Long, Boolean?>().apply { tags.forEach { put(it.id, stateOf(it.id)) } } }
    val initial = remember { tags.associate { it.id to stateOf(it.id) } }
    val newTags = remember { mutableStateListOf<String>() }
    var input by remember { mutableStateOf("") }

    fun commitInput() {
        // "/" is kept: "日本/京都" makes a two-level tag.
        input.split(Regex("[,，#\\s]+")).map { it.trim() }.filter { it.isNotEmpty() }
            .forEach { name -> if (name !in newTags && tags.none { it.name == name }) newTags.add(name) else tags.firstOrNull { it.name == name }?.let { choices[it.id] = true } }
        input = ""
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Outlined.Label, null) },
        title = { Text(if (noteCount == 1) tr("Tags", "标签") else tr("Tags for $noteCount notes", "$noteCount 篇笔记的标签")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(tr("New tag, e.g. 火锅 or 日本/京都", "新标签，比如 火锅 或 日本/京都")) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { commitInput() }),
                    trailingIcon = { TextButton(onClick = ::commitInput, enabled = input.isNotBlank()) { Text(tr("Add", "添加")) } },
                    shape = MaterialTheme.shapes.medium,
                    colors = softFieldColors(),
                )
                LazyColumn(Modifier.heightIn(max = 320.dp)) {
                    items(newTags.toList()) { name ->
                        TagRow(name, ToggleableState.On, note = tr("new", "新")) { newTags.remove(name) }
                    }
                    items(tree, key = { it.first.id }) { (tag, depth) ->
                        val state = when (choices[tag.id]) {
                            true -> ToggleableState.On
                            false -> ToggleableState.Off
                            null -> ToggleableState.Indeterminate
                        }
                        TagRow(tag.name, state, depth = depth,
                            note = if (initial[tag.id] == null && choices[tag.id] == null) tr("some", "部分") else null) {
                            choices[tag.id] = choices[tag.id] != true
                        }
                    }
                }
                if (newTags.isNotEmpty() && tags.isNotEmpty()) {
                    Text(tr("New tags go under", "新标签放在"), style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    // The notes' root tag first, then the rest of the tree.
                    val options = listOfNotNull(defaultParent) + tree.map { it.first }.filter { it.id != defaultParent?.id }
                    androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        item {
                            FilterChip(selected = newParent == null, onClick = { newParent = null }, label = { Text(tr("Top level", "顶层")) })
                        }
                        items(options, key = { it.id }) { r ->
                            FilterChip(selected = newParent?.id == r.id, onClick = { newParent = r },
                                label = { Text(TagTree.pathLabel(r, byId)) })
                        }
                    }
                }
                if (tags.isEmpty() && newTags.isEmpty()) {
                    Text(tr("No tags yet — type one above.", "还没有标签，在上面输入一个吧。"), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                commitInput()
                val add = newTags.toList() + tags.filter { choices[it.id] == true && initial[it.id] != true }.map { it.name }
                val remove = tags.filter { choices[it.id] == false && initial[it.id] != false }.map { it.id }
                onApply(add, remove, newParent?.id)
                onDismiss()
            }) { Text(tr("Apply", "应用")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Cancel", "取消")) } },
    )
}

@Composable
private fun TagRow(name: String, state: ToggleableState, note: String?, depth: Int = 0, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(start = (16 * depth.coerceAtMost(4)).dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TriStateCheckbox(state = state, onClick = onClick)
        Text(if (depth == 0) name else "› $name", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        if (note != null) {
            Text(note, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Set a place by hand (country / region / city). Auto-organize won't overwrite it. */
@Composable
fun PlaceDialog(
    noteCount: Int,
    initial: Place,
    onSave: (country: String, region: String, city: String, address: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var country by remember { mutableStateOf(initial.country) }
    var region by remember { mutableStateOf(initial.region) }
    var city by remember { mutableStateOf(initial.city) }
    var address by remember { mutableStateOf(initial.address) }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Outlined.Place, null) },
        title = { Text(if (noteCount == 1) tr("Place", "地点") else tr("Place for $noteCount notes", "$noteCount 篇笔记的地点")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf(
                    Triple(tr("Country", "国家"), country) { v: String -> country = v },
                    Triple(tr("Province / state", "省 / 州"), region) { v: String -> region = v },
                    Triple(tr("City", "城市"), city) { v: String -> city = v },
                ).plus(if (noteCount == 1) listOf(Triple(tr("Street address (optional)", "详细地址（选填）"), address) { v: String -> address = v }) else emptyList())
                .forEach { (label, value, set) ->
                    TextField(
                        value = value, onValueChange = set, label = { Text(label) },
                        singleLine = true, modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium, colors = softFieldColors(),
                    )
                }
                Text(tr("Organize won't change places you set by hand.", "整理地点不会改动你手动设置的地点。"), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(country, region, city, address); onDismiss() },
                enabled = country.isNotBlank() || city.isNotBlank()) { Text(tr("Save", "保存")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Cancel", "取消")) } },
    )
}

@Composable
fun ConfirmDeleteDialog(noteCount: Int, anyPosted: Boolean, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Outlined.Delete, null) },
        title = { Text(if (noteCount == 1) tr("Delete this note?", "删除这篇笔记？") else tr("Delete $noteCount notes?", "删除 $noteCount 篇笔记？")) },
        text = {
            Text(
                (if (anyPosted) tr("Posts stay on Xiaohongshu — only the copies in this app are removed. ", "小红书上已发布的内容不受影响，只删除本 App 里的副本。") else "") +
                    tr("The notes and their photos will be removed from this phone.", "笔记和照片会从这台手机上删除。")
            )
        },
        confirmButton = {
            TextButton(onClick = { onDismiss(); onConfirm() }) { Text(tr("Delete", "删除"), color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Cancel", "取消")) } },
    )
}

/** Rename or delete tags (deleting a tag leaves the notes themselves alone). */
@Composable
fun ManageTagsDialog(
    tags: List<NoteTag>,
    onRename: (Long, String) -> Unit,
    onDelete: (Long) -> Unit,
    onSetParent: (Long, Long?) -> Unit,
    onDismiss: () -> Unit,
) {
    var editing by remember { mutableStateOf<NoteTag?>(null) }
    var moving by remember { mutableStateOf<NoteTag?>(null) }
    // The whole tree: each tag followed by the tags under it, at any depth.
    val ordered = remember(tags) { TagTree.ordered(tags) }
    val byId = tags.associateBy { it.id }
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Outlined.Label, null) },
        title = { Text(tr("Manage tags", "管理标签")) },
        text = {
            if (tags.isEmpty()) {
                Text(tr("No tags yet. Select notes and tap the tag icon to add some.", "还没有标签。选中笔记后点标签图标就能添加。"))
            } else LazyColumn(Modifier.heightIn(max = 360.dp)) {
                items(ordered, key = { it.first.id }) { (tag, depth) ->
                    if (editing?.id == tag.id) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TextField(value = text, onValueChange = { text = it }, singleLine = true,
                                modifier = Modifier.weight(1f), shape = MaterialTheme.shapes.medium, colors = softFieldColors())
                            TextButton(onClick = { onRename(tag.id, text); editing = null }, enabled = text.isNotBlank()) { Text(tr("Save", "保存")) }
                        }
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                if (depth == 0) tag.name else "› ${tag.name}",
                                style = if (depth == 0) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.weight(1f).padding(start = (16 * depth.coerceAtMost(4)).dp),
                            )
                            Box {
                                IconButton(onClick = { moving = tag }) { Icon(Icons.Outlined.DriveFileMove, tr("Move under…", "移到…下")) }
                                DropdownMenu(expanded = moving?.id == tag.id, onDismissRequest = { moving = null }) {
                                    DropdownMenuItem(text = { Text(tr("Top level", "顶层")) }, onClick = { moving = null; onSetParent(tag.id, null) })
                                    // Any tag except itself and what's already below it.
                                    val below = TagTree.descendants(tag.id, tags)
                                    ordered.map { it.first }.filter { it.id != tag.id && it.id !in below && it.id != tag.parentId }.forEach { r ->
                                        DropdownMenuItem(text = { Text(tr("Under ${TagTree.pathLabel(r, byId)}", "放到 ${TagTree.pathLabel(r, byId)} 下")) },
                                            onClick = { moving = null; onSetParent(tag.id, r.id) })
                                    }
                                }
                            }
                            IconButton(onClick = { editing = tag; text = tag.name }) { Icon(Icons.Outlined.Edit, tr("Rename", "重命名")) }
                            IconButton(onClick = { onDelete(tag.id) }) {
                                Icon(Icons.Outlined.Delete, tr("Delete tag", "删除标签"), tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(tr("Done", "完成")) } },
    )
}
