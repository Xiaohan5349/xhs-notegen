package com.xiaohan.xhsnotegen.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Translate
import com.xiaohan.xhsnotegen.domain.PromptLanguage
import androidx.compose.material3.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.xiaohan.xhsnotegen.domain.NoteStatus
import com.xiaohan.xhsnotegen.i18n.tr
import com.xiaohan.xhsnotegen.ui.theme.NumberStyle
import com.xiaohan.xhsnotegen.ui.theme.SectionTitleStyle
import com.xiaohan.xhsnotegen.ui.theme.app

// ---------------------------------------------------------------------------
// Status
// ---------------------------------------------------------------------------

data class StatusLook(val label: String, val container: Color, val content: Color)

/** Posted is gold in every theme; ready takes the theme color; the rest stay quiet. */
@Composable
fun statusLook(status: NoteStatus): StatusLook {
    val c = MaterialTheme.colorScheme
    val a = MaterialTheme.app
    return when (status) {
        NoteStatus.DRAFT -> StatusLook(tr("Draft", "草稿"), a.inset, c.onSurfaceVariant)
        NoteStatus.GENERATED -> StatusLook(tr("To review", "待查看"), c.secondaryContainer, c.onSecondaryContainer)
        NoteStatus.REVIEWED -> StatusLook(tr("Ready", "待发布"), c.primaryContainer, c.onPrimaryContainer)
        NoteStatus.SHARED -> StatusLook(tr("Posted", "已发布"), a.goldContainer, a.onGoldContainer)
    }
}

@Composable
fun StatusPill(status: NoteStatus, modifier: Modifier = Modifier) {
    val look = statusLook(status)
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(look.container)
            .padding(horizontal = 9.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(look.content))
        Text(look.label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = look.content)
    }
}

/**
 * The round, slightly tilted seal on a note's cover: gold when posted, the theme
 * color when ready, a dashed outline for drafts. The ring in [ringColor] (the page)
 * lifts it off the photo.
 */
@Composable
fun StatusSeal(status: NoteStatus, ringColor: Color, modifier: Modifier = Modifier, size: Dp = 56.dp) {
    val c = MaterialTheme.colorScheme
    val a = MaterialTheme.app
    val (bg, fg) = when (status) {
        NoteStatus.SHARED -> a.goldContainer to a.onGoldContainer
        NoteStatus.REVIEWED -> c.primary to c.onPrimary
        NoteStatus.GENERATED -> c.secondaryContainer to c.onSecondaryContainer
        NoteStatus.DRAFT -> ringColor to c.onSurfaceVariant
    }
    Box(
        modifier
            .size(size)
            .rotate(-8f)
            .clip(CircleShape)
            .background(bg)
            .then(
                if (status == NoteStatus.DRAFT) Modifier.dashedBorder(a.line2, size / 2)
                else Modifier.border(3.dp, ringColor, CircleShape)
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(statusLook(status).label, style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Serif),
            color = fg, textAlign = TextAlign.Center, maxLines = 2)
    }
}

/**
 * An arched frame: the top is a half-ellipse across the full width (a true
 * semicircle on portrait photos, a dome on wide ones), the bottom corners are [bottom].
 */
class ArchShape(private val bottom: Dp = 14.dp) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val w = size.width
        val h = size.height
        val arch = minOf(w / 2f, h * 0.6f)
        val r = with(density) { bottom.toPx() }.coerceAtMost(minOf(w, h) / 2f)
        val path = Path().apply {
            moveTo(0f, arch)
            arcTo(Rect(0f, 0f, w, arch * 2f), 180f, 180f, false)
            lineTo(w, h - r)
            arcTo(Rect(w - 2 * r, h - 2 * r, w, h), 0f, 90f, false)
            lineTo(r, h)
            arcTo(Rect(0f, h - 2 * r, 2 * r, h), 90f, 90f, false)
            close()
        }
        return Outline.Generic(path)
    }
}

// ---------------------------------------------------------------------------
// Layout
// ---------------------------------------------------------------------------

/**
 * A sheet of paper one step up from the page: no edge, radius 18.
 * [selected] fills it with the theme's container color (e.g. the active filter).
 */
@Composable
fun Tile(
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(14.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.app.tile
    val content2 = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
    val body: @Composable () -> Unit = { Column(Modifier.padding(contentPadding), content = content) }
    if (onClick != null) {
        Surface(onClick = onClick, modifier = modifier, shape = MaterialTheme.shapes.medium, color = color, contentColor = content2, content = body)
    } else {
        Surface(modifier = modifier, shape = MaterialTheme.shapes.medium, color = color, contentColor = content2, content = body)
    }
}

/** A titled group of fields on a tile. */
@Composable
fun SectionCard(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    trailing: @Composable (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Tile(modifier = modifier.fillMaxWidth(), contentPadding = PaddingValues(16.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(title, style = SectionTitleStyle, color = MaterialTheme.app.head)
                    if (subtitle != null) {
                        Text(subtitle, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                trailing?.invoke()
            }
            content()
        }
    }
}

/** Small label above a group (sentence case — no shouting caps). */
@Composable
fun Eyebrow(text: String, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Text(text, modifier = modifier, style = MaterialTheme.typography.labelLarge, color = color)
}

@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier.size(88.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer)
        }
        Spacer(Modifier.height(4.dp))
        Text(title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        Text(body, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (action != null) {
            Spacer(Modifier.height(8.dp))
            action()
        }
    }
}

// ---------------------------------------------------------------------------
// Chips and segmented choices
// ---------------------------------------------------------------------------

/** The one filter-chip look: an outlined pill, filled with the theme color when on. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppFilterChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailing: String? = null,
    /** Kept for call sites on a tinted strip; the "on" state is solid everywhere now. */
    @Suppress("UNUSED_PARAMETER") onTinted: Boolean = false,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        modifier = modifier,
        label = {
            Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
            if (trailing != null) {
                Spacer(Modifier.width(6.dp))
                Text(trailing, style = NumberStyle, color = LocalContentColor.current.copy(alpha = 0.85f))
            }
        },
        leadingIcon = leadingIcon,
        shape = CircleShape,
        colors = FilterChipDefaults.filterChipColors(
            containerColor = Color.Transparent,
            labelColor = MaterialTheme.colorScheme.onSurface,
            iconColor = MaterialTheme.colorScheme.onSurfaceVariant,
            selectedContainerColor = MaterialTheme.colorScheme.primary,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
            selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimary,
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true, selected = selected, borderWidth = 1.5.dp,
            borderColor = MaterialTheme.app.line2, selectedBorderColor = Color.Transparent,
        ),
    )
}

/**
 * Bento segmented control: options sit in an inset track, the chosen one is a raised tile.
 * Works as a radio group for accessibility.
 */
@Composable
fun <T> InsetSegmented(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    icon: ((T) -> ImageVector?)? = null,
) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.app.inset)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEach { o ->
            val on = o == selected
            Row(
                Modifier
                    .weight(1f)
                    .heightIn(min = 40.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .then(if (on) Modifier.background(MaterialTheme.app.tile).border(1.dp, MaterialTheme.app.line2, RoundedCornerShape(9.dp)) else Modifier)
                    .selectable(selected = on, role = Role.RadioButton, onClick = { onSelect(o) })
                    .padding(horizontal = 6.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                icon?.invoke(o)?.let {
                    Icon(it, null, Modifier.size(16.dp),
                        tint = if (on) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(6.dp))
                }
                Text(
                    label(o),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (on) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Photo sheet: all of a note's photos in a 4-column grid, like a contact sheet
// ---------------------------------------------------------------------------

/**
 * Photos in a plain grid. Collapsed, it shows [collapsedRows] rows and a
 * "Show all N" pill; [trailingCell] (e.g. "+ add") always stays visible.
 * [cell] draws photo [index]; [footer] is the small caption under the grid.
 */
@Composable
fun PhotoSheet(
    count: Int,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    modifier: Modifier = Modifier,
    columns: Int = 4,
    collapsedRows: Int = 2,
    footer: String? = null,
    footerEnd: String? = null,
    trailingCell: (@Composable () -> Unit)? = null,
    cell: @Composable (index: Int) -> Unit,
) {
    val a = MaterialTheme.app
    val extra = if (trailingCell != null) 1 else 0
    val limit = columns * collapsedRows
    val overflows = count + extra > limit
    val shownPhotos = if (overflows && !expanded) limit - extra else count
    val cells: List<Int?> = (0 until shownPhotos).toList() + if (trailingCell != null) listOf(-1) else emptyList()

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            cells.chunked(columns).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    row.forEach { i ->
                        Box(Modifier.weight(1f)) {
                            if (i == -1) trailingCell?.invoke() else if (i != null) cell(i)
                        }
                    }
                    // Keep the last row's cells the same width as the others.
                    repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
        if (overflows) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 40.dp)
                    .clip(CircleShape)
                    .border(1.5.dp, a.line2, CircleShape)
                    .clickable(onClick = onToggleExpanded),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    if (expanded) tr("Show fewer", "收起") else tr("Show all $count", "展开全部 $count 张"),
                    style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(4.dp))
                Icon(if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, null,
                    Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
            }
        }
        if (footer != null || footerEnd != null) {
            Row(Modifier.padding(horizontal = 2.dp)) {
                Text(footer.orEmpty(), style = NumberStyle, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                if (footerEnd != null) Text(footerEnd, style = NumberStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** "01" in the top-left corner of a photo cell. */
@Composable
fun PhotoNumber(n: Int, modifier: Modifier = Modifier) {
    Text(
        "%02d".format(n),
        style = NumberStyle, color = Color.White,
        modifier = modifier.clip(RoundedCornerShape(4.dp)).background(Color.Black.copy(alpha = 0.6f)).padding(horizontal = 4.dp),
    )
}

/** Posting order of an included photo, in the theme color. */
@Composable
fun OrderBadge(n: Int, modifier: Modifier = Modifier) {
    Box(
        modifier.size(20.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary),
        contentAlignment = Alignment.Center,
    ) {
        Text("$n", style = NumberStyle, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary)
    }
}

@Composable
fun CoverTag(modifier: Modifier = Modifier) {
    Text(
        tr("Cover", "封面"),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.app.onGoldContainer,
        modifier = modifier.clip(CircleShape).background(MaterialTheme.app.goldContainer).padding(horizontal = 7.dp, vertical = 1.dp),
    )
}

// ---------------------------------------------------------------------------
// Inputs
// ---------------------------------------------------------------------------

/**
 * A field written on the page: the label above, a rule underneath that turns
 * the theme color on focus — calmer than stacked boxes when a form has many fields.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SoftTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    supporting: String? = null,
    isError: Boolean = false,
    required: Boolean = false,
    singleLine: Boolean = false,
    minLines: Int = 1,
    leadingIcon: ImageVector? = null,
    textStyle: TextStyle = MaterialTheme.typography.bodyLarge,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row {
            Text(label, style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (required) Text(" *", style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary)
        }
        // BasicTextField + DecorationBox rather than TextField: the text starts flush with the
        // label above it instead of TextField's built-in 16dp inset.
        val colors = softFieldColors()
        val interaction = remember { MutableInteractionSource() }
        val focused by interaction.collectIsFocusedAsState()
        val c = MaterialTheme.colorScheme
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            textStyle = textStyle.copy(color = c.onSurface),
            cursorBrush = SolidColor(if (isError) c.error else c.primary),
            keyboardOptions = keyboardOptions,
            singleLine = singleLine,
            minLines = minLines,
            interactionSource = interaction,
        ) { inner ->
            TextFieldDefaults.DecorationBox(
                value = value,
                innerTextField = inner,
                enabled = true,
                singleLine = singleLine,
                visualTransformation = VisualTransformation.None,
                interactionSource = interaction,
                isError = isError,
                placeholder = placeholder?.let { { Text(it, style = textStyle) } },
                leadingIcon = leadingIcon?.let { { Icon(it, contentDescription = null, modifier = Modifier.size(20.dp)) } },
                colors = colors,
                contentPadding = TextFieldDefaults.contentPaddingWithoutLabel(start = 0.dp, end = 0.dp, top = 12.dp, bottom = 12.dp),
                container = {
                    TextFieldDefaults.Container(enabled = true, isError = isError, interactionSource = interaction,
                        colors = colors, shape = RectangleShape,
                        focusedIndicatorLineThickness = 2.dp, unfocusedIndicatorLineThickness = if (focused) 2.dp else 1.dp)
                },
            )
        }
        if (supporting != null) {
            Text(supporting, style = MaterialTheme.typography.bodySmall,
                color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * "Write the note in: 中文 | English" — the language of the AI's prompt and of the note it writes.
 * Separate from the app language; each note (and each rewrite) can choose.
 */
@Composable
fun NoteLanguageRow(
    language: PromptLanguage,
    onSelect: (PromptLanguage) -> Unit,
    modifier: Modifier = Modifier,
    caption: String? = null,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Outlined.Translate, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(tr("Write the note in", "笔记语言"), style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            PromptLanguage.entries.forEach { l ->
                AppFilterChip(selected = language == l, onClick = { onSelect(l) }, label = l.label)
            }
        }
        if (caption != null) {
            Text(caption, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Looks like a text field but opens a picker when tapped — for values chosen from a list, not typed. */
@Composable
fun PickerField(
    value: String,
    label: String,
    placeholder: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    supporting: String? = null,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        val rule = MaterialTheme.app.line2
        Surface(onClick = onClick, shape = RoundedCornerShape(0.dp), color = Color.Transparent,
            modifier = Modifier.drawBehind { drawLine(rule, Offset(0f, size.height - 0.5f), Offset(size.width, size.height - 0.5f), strokeWidth = 1.dp.toPx()) }) {
            Row(
                Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 4.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    value.ifBlank { placeholder },
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (value.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                    maxLines = 2,
                )
                Icon(Icons.Filled.ArrowDropDown, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (supporting != null) {
            Text(supporting, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** No fill; a rule under the field, in the theme color on focus. */
@Composable
fun softFieldColors(): TextFieldColors {
    val c = MaterialTheme.colorScheme
    val a = MaterialTheme.app
    return TextFieldDefaults.colors(
        focusedContainerColor = Color.Transparent,
        unfocusedContainerColor = Color.Transparent,
        disabledContainerColor = Color.Transparent,
        errorContainerColor = c.errorContainer.copy(alpha = 0.5f),
        focusedIndicatorColor = c.primary,
        unfocusedIndicatorColor = a.line2,
        disabledIndicatorColor = a.line,
        errorIndicatorColor = c.error,
        focusedPlaceholderColor = c.onSurfaceVariant,
        unfocusedPlaceholderColor = c.onSurfaceVariant,
    )
}

/** Dashed rounded outline, e.g. for "add photo" tiles. */
fun Modifier.dashedBorder(color: Color, cornerRadius: Dp, strokeWidth: Dp = 1.5.dp): Modifier =
    drawBehind {
        val stroke = strokeWidth.toPx()
        drawRoundRect(
            color = color,
            topLeft = androidx.compose.ui.geometry.Offset(stroke / 2, stroke / 2),
            size = androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke),
            cornerRadius = CornerRadius(cornerRadius.toPx()),
            style = Stroke(width = stroke, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))),
        )
    }
