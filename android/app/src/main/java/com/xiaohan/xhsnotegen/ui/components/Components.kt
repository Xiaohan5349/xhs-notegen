package com.xiaohan.xhsnotegen.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.xiaohan.xhsnotegen.domain.NoteStatus
import com.xiaohan.xhsnotegen.i18n.tr

// ---------------------------------------------------------------------------
// Status
// ---------------------------------------------------------------------------

data class StatusLook(val label: String, val container: Color, val content: Color)

@Composable
fun statusLook(status: NoteStatus): StatusLook {
    val c = MaterialTheme.colorScheme
    return when (status) {
        NoteStatus.DRAFT -> StatusLook(tr("Draft", "草稿"), c.surfaceContainerHighest, c.onSurfaceVariant)
        NoteStatus.GENERATED -> StatusLook(tr("To review", "待查看"), c.tertiaryContainer, c.onTertiaryContainer)
        NoteStatus.REVIEWED -> StatusLook(tr("Ready", "待发布"), c.secondaryContainer, c.onSecondaryContainer)
        NoteStatus.SHARED -> StatusLook(tr("Posted", "已发布"), c.primaryContainer, c.onPrimaryContainer)
    }
}

@Composable
fun StatusPill(status: NoteStatus, modifier: Modifier = Modifier) {
    val look = statusLook(status)
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(look.container)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(look.content))
        Text(look.label, style = MaterialTheme.typography.labelMedium, color = look.content)
    }
}

// ---------------------------------------------------------------------------
// Layout
// ---------------------------------------------------------------------------

/** A titled group of fields on a softly raised card. */
@Composable
fun SectionCard(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    trailing: @Composable (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        tonalElevation = 0.dp,
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(title, style = MaterialTheme.typography.titleMedium)
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

/** Small uppercase-feeling label used above groups. */
@Composable
fun Eyebrow(text: String, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.primary) {
    Text(text.uppercase(), modifier = modifier, style = MaterialTheme.typography.labelMedium, color = color)
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
// Inputs
// ---------------------------------------------------------------------------

/**
 * Filled, borderless field with the label sitting above it — calmer than
 * stacked outlined boxes when a form has many fields.
 */
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
        TextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = placeholder?.let { { Text(it, style = textStyle) } },
            leadingIcon = leadingIcon?.let { { Icon(it, contentDescription = null, modifier = Modifier.size(20.dp)) } },
            isError = isError,
            singleLine = singleLine,
            minLines = minLines,
            textStyle = textStyle,
            keyboardOptions = keyboardOptions,
            shape = MaterialTheme.shapes.medium,
            colors = softFieldColors(),
        )
        if (supporting != null) {
            Text(supporting, style = MaterialTheme.typography.bodySmall,
                color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun softFieldColors(): TextFieldColors {
    val c = MaterialTheme.colorScheme
    return TextFieldDefaults.colors(
        focusedContainerColor = c.surfaceContainerHigh,
        unfocusedContainerColor = c.surfaceContainer,
        disabledContainerColor = c.surfaceContainer,
        errorContainerColor = c.errorContainer.copy(alpha = 0.5f),
        focusedIndicatorColor = Color.Transparent,
        unfocusedIndicatorColor = Color.Transparent,
        disabledIndicatorColor = Color.Transparent,
        errorIndicatorColor = Color.Transparent,
        focusedPlaceholderColor = c.onSurfaceVariant.copy(alpha = 0.6f),
        unfocusedPlaceholderColor = c.onSurfaceVariant.copy(alpha = 0.6f),
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
