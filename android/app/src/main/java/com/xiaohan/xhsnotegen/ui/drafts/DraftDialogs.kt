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
    val roots = tags.filter { it.parentId == null }
    var newParent by remember { mutableStateOf(defaultParent) }
    // null = leave as is (mixed), true = add to all, false = remove from all
    val choices = remember { mutableStateMapOf<Long, Boolean?>().apply { tags.forEach { put(it.id, stateOf(it.id)) } } }
    val initial = remember { tags.associate { it.id to stateOf(it.id) } }
    val newTags = remember { mutableStateListOf<String>() }
    var input by remember { mutableStateOf("") }

    fun commitInput() {
        input.split(Regex("[,，#\\s]+")).map { it.trim() }.filter { it.isNotEmpty() }
            .forEach { name -> if (name !in newTags && tags.none { it.name == name }) newTags.add(name) else tags.firstOrNull { it.name == name }?.let { choices[it.id] = true } }
        input = ""
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Outlined.Label, null) },
        title = { Text(if (noteCount == 1) "Tags" else "Tags for $noteCount notes") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("New tag, e.g. 火锅") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { commitInput() }),
                    trailingIcon = { TextButton(onClick = ::commitInput, enabled = input.isNotBlank()) { Text("Add") } },
                    shape = MaterialTheme.shapes.medium,
                    colors = softFieldColors(),
                )
                LazyColumn(Modifier.heightIn(max = 320.dp)) {
                    items(newTags.toList()) { name ->
                        TagRow(name, ToggleableState.On, note = "new") { newTags.remove(name) }
                    }
                    items(tags.sortedWith(compareBy({ (it.parentId?.let { p -> tags.firstOrNull { t -> t.id == p }?.name } ?: it.name).lowercase() }, { it.parentId != null }, { it.name.lowercase() })), key = { it.id }) { tag ->
                        val state = when (choices[tag.id]) {
                            true -> ToggleableState.On
                            false -> ToggleableState.Off
                            null -> ToggleableState.Indeterminate
                        }
                        val parentName = tag.parentId?.let { p -> tags.firstOrNull { it.id == p }?.name }
                        TagRow(if (parentName != null) "$parentName › ${tag.name}" else tag.name, state,
                            note = if (initial[tag.id] == null && choices[tag.id] == null) "some" else null) {
                            choices[tag.id] = choices[tag.id] != true
                        }
                    }
                }
                if (newTags.isNotEmpty() && roots.isNotEmpty()) {
                    Text("New tags go under", style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        item {
                            FilterChip(selected = newParent == null, onClick = { newParent = null }, label = { Text("Top level") })
                        }
                        items(roots, key = { it.id }) { r ->
                            FilterChip(selected = newParent?.id == r.id, onClick = { newParent = r }, label = { Text("#${r.name}") })
                        }
                    }
                }
                if (tags.isEmpty() && newTags.isEmpty()) {
                    Text("No tags yet — type one above.", style = MaterialTheme.typography.bodySmall,
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
            }) { Text("Apply") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun TagRow(name: String, state: ToggleableState, note: String?, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TriStateCheckbox(state = state, onClick = onClick)
        Text("#$name", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
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
        title = { Text(if (noteCount == 1) "Place" else "Place for $noteCount notes") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf(
                    Triple("Country", country) { v: String -> country = v },
                    Triple("Province / state", region) { v: String -> region = v },
                    Triple("City", city) { v: String -> city = v },
                ).plus(if (noteCount == 1) listOf(Triple("Street address (optional)", address) { v: String -> address = v }) else emptyList())
                .forEach { (label, value, set) ->
                    TextField(
                        value = value, onValueChange = set, label = { Text(label) },
                        singleLine = true, modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium, colors = softFieldColors(),
                    )
                }
                Text("Organize won't change places you set by hand.", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(country, region, city, address); onDismiss() },
                enabled = country.isNotBlank() || city.isNotBlank()) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun ConfirmDeleteDialog(noteCount: Int, anyPosted: Boolean, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Outlined.Delete, null) },
        title = { Text(if (noteCount == 1) "Delete this note?" else "Delete $noteCount notes?") },
        text = {
            Text(
                (if (anyPosted) "Posts stay on Xiaohongshu — only the copies in this app are removed. " else "") +
                    "The notes and their photos will be removed from this phone."
            )
        },
        confirmButton = {
            TextButton(onClick = { onDismiss(); onConfirm() }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
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
    // Roots first, each followed by its sub-tags.
    val ordered = tags.filter { it.parentId == null }.sortedBy { it.name.lowercase() }
        .flatMap { r -> listOf(r) + tags.filter { it.parentId == r.id }.sortedBy { it.name.lowercase() } } +
        tags.filter { t -> t.parentId != null && tags.none { it.id == t.parentId } }
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Outlined.Label, null) },
        title = { Text("Manage tags") },
        text = {
            if (tags.isEmpty()) {
                Text("No tags yet. Select notes and tap the tag icon to add some.")
            } else LazyColumn(Modifier.heightIn(max = 360.dp)) {
                items(ordered, key = { it.id }) { tag ->
                    if (editing?.id == tag.id) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TextField(value = text, onValueChange = { text = it }, singleLine = true,
                                modifier = Modifier.weight(1f), shape = MaterialTheme.shapes.medium, colors = softFieldColors())
                            TextButton(onClick = { onRename(tag.id, text); editing = null }, enabled = text.isNotBlank()) { Text("Save") }
                        }
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "#${tag.name}",
                                style = if (tag.parentId == null) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.weight(1f).padding(start = if (tag.parentId == null) 0.dp else 20.dp),
                            )
                            Box {
                                IconButton(onClick = { moving = tag }) { Icon(Icons.Outlined.DriveFileMove, "Move under…") }
                                DropdownMenu(expanded = moving?.id == tag.id, onDismissRequest = { moving = null }) {
                                    DropdownMenuItem(text = { Text("Top level") }, onClick = { moving = null; onSetParent(tag.id, null) })
                                    tags.filter { it.parentId == null && it.id != tag.id }.sortedBy { it.name.lowercase() }.forEach { r ->
                                        DropdownMenuItem(text = { Text("Under #${r.name}") }, onClick = { moving = null; onSetParent(tag.id, r.id) })
                                    }
                                }
                            }
                            IconButton(onClick = { editing = tag; text = tag.name }) { Icon(Icons.Outlined.Edit, "Rename") }
                            IconButton(onClick = { onDelete(tag.id) }) {
                                Icon(Icons.Outlined.Delete, "Delete tag", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )
}
