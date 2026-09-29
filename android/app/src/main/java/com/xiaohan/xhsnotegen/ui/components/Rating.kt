package com.xiaohan.xhsnotegen.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.xiaohan.xhsnotegen.i18n.tr

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
                    contentDescription = starsLabel(i),
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
                        onClickLabel = tr("Rate $i", "评 $i 星"),
                    ) { onRate(if (i == rating) 0 else i) } else Modifier),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (filled) Icons.Filled.Star else Icons.Outlined.StarOutline,
                    contentDescription = if (onRate != null) starsLabel(i) else null,
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
        title = { Text(if (noteCount == 1) tr("Rate this meal", "给这篇打分") else tr("Rate $noteCount meals", "给 $noteCount 篇打分")) },
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
        confirmButton = { TextButton(onClick = { onSave(value); onDismiss() }) { Text(tr("Save", "保存")) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Cancel", "取消")) } },
    )
}

fun ratingWords(rating: Int): String = when (rating) {
    1 -> tr("Wouldn't go back", "不会再去")
    2 -> tr("Meh", "一般")
    3 -> tr("Fine", "还行")
    4 -> tr("Good — would go again", "不错，还会再去")
    5 -> tr("Loved it", "超喜欢")
    else -> tr("Not rated", "未评分")
}

private fun starsLabel(i: Int) = tr("$i star${if (i > 1) "s" else ""}", "$i 星")

/**
 * Rating as five small rounded bars in the theme's accent — used for group
 * headers, so stars stay the notes' own mark. Filled bars step up in strength
 * toward the top, like a level meter.
 */
@Composable
fun RatingMeter(rating: Int, modifier: Modifier = Modifier) {
    val on = MaterialTheme.colorScheme.primary
    val off = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
    Row(modifier, verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        for (i in 1..5) {
            Box(
                Modifier
                    .size(width = 6.dp, height = (8 + i * 3).dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(if (i <= rating) on.copy(alpha = 0.55f + 0.09f * i) else off),
            )
        }
    }
}
