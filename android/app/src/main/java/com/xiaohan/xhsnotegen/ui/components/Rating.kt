package com.xiaohan.xhsnotegen.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Star gold that reads on both light and dark surfaces. */
val StarColor = Color(0xFFF5B100)

/**
 * Tappable 1–5 stars. Tapping the current rating again clears it (0 = not rated).
 */
@Composable
fun RatingBar(
    rating: Int,
    onRate: (Int) -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 32.dp,
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        for (i in 1..5) {
            IconButton(onClick = { onRate(if (i == rating) 0 else i) }, modifier = Modifier.size(size + 8.dp)) {
                Icon(
                    if (i <= rating) Icons.Filled.Star else Icons.Outlined.StarOutline,
                    contentDescription = "$i star${if (i > 1) "s" else ""}",
                    tint = if (i <= rating) StarColor else MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(size),
                )
            }
        }
    }
}

/**
 * Small 5-star row for cards. Always visible (outlined when unrated); tapping
 * a star rates the note right there, tapping the current rating clears it.
 * Each star has a 32dp touch target so it doesn't open the note by accident.
 */
@Composable
fun CardStars(rating: Int, onRate: ((Int) -> Unit)?, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        for (i in 1..5) {
            val filled = i <= rating
            Box(
                Modifier
                    .size(if (onRate != null) 28.dp else 18.dp)
                    .then(if (onRate != null) Modifier.clickable(
                        onClickLabel = "Rate $i",
                    ) { onRate(if (i == rating) 0 else i) } else Modifier),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (filled) Icons.Filled.Star else Icons.Outlined.StarOutline,
                    contentDescription = if (onRate != null) "$i star${if (i > 1) "s" else ""}" else null,
                    tint = if (filled) StarColor else MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

/** Compact read-only "★ 4" for cards. */
@Composable
fun RatingBadge(rating: Int, modifier: Modifier = Modifier, onDark: Boolean = false) {
    if (rating <= 0) return
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        Icon(Icons.Filled.Star, null, Modifier.size(14.dp), tint = StarColor)
        Text("$rating", style = MaterialTheme.typography.labelMedium,
            color = if (onDark) Color.White else MaterialTheme.colorScheme.onSurface)
    }
}

/** Pick a rating for one or more notes. */
@Composable
fun RatingDialog(noteCount: Int, initial: Int, onSave: (Int) -> Unit, onDismiss: () -> Unit) {
    var value by remember { mutableIntStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Filled.Star, null, tint = StarColor) },
        title = { Text(if (noteCount == 1) "Rate this meal" else "Rate $noteCount meals") },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                RatingBar(value, onRate = { value = it }, size = 36.dp)
                Text(
                    ratingWords(value),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSave(value); onDismiss() }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

fun ratingWords(rating: Int): String = when (rating) {
    1 -> "Wouldn't go back"
    2 -> "Meh"
    3 -> "Fine"
    4 -> "Good — would go again"
    5 -> "Loved it"
    else -> "Not rated"
}
