package com.xiaohan.xhsnotegen.ui.theme

import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import kotlin.random.Random

/**
 * Decorative art for the Glint and anime-inspired themes. Everything is
 * original and drawn in code — no third-party artwork. It fades out toward
 * the bottom; [inTile] (the home "all notes" tile) also fades it in from the
 * left, so the big number on the left stays on a plain surface.
 */
@Composable
fun ThemeBackdrop(modifier: Modifier = Modifier, inTile: Boolean = false) {
    val theme = LocalAppTheme.current
    if (theme.backdrop == Backdrop.NONE) return
    val dark = LocalDarkTheme.current
    val colors = MaterialTheme.colorScheme

    Canvas(
        modifier
            // Offscreen layer so the fade mask (DstIn) only affects this art.
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen },
    ) {
        when (theme.backdrop) {
            Backdrop.SKY -> if (dark) nightSky(colors.primary) else summerSky()
            Backdrop.SUNSET -> retroSunset(dark)
            Backdrop.SEIGAIHA -> seigaiha(colors.primary, colors.background)
            Backdrop.RIPPLE -> glintWater(dark)
            Backdrop.NONE -> Unit
        }
        // Fade out towards the bottom. The busy wave pattern fades sooner and
        // softer so the header text on top of it stays readable.
        val fade = if (theme.backdrop == Backdrop.SEIGAIHA)
            Brush.verticalGradient(0f to Color.Black, 0.25f to Color.Black.copy(alpha = 0.8f), 0.6f to Color.Transparent)
        else Brush.verticalGradient(0f to Color.Black, 0.55f to Color.Black, 1f to Color.Transparent)
        drawRect(brush = fade, blendMode = BlendMode.DstIn)
        if (inTile) {
            drawRect(
                brush = Brush.horizontalGradient(0f to Color.Transparent, 0.38f to Color.Transparent, 0.8f to Color.Black),
                blendMode = BlendMode.DstIn,
            )
        }
    }
}

// ---- Summer Sky (夏空): anime-film blue with drifting cumulus ----

private fun DrawScope.summerSky() {
    drawRect(Brush.verticalGradient(listOf(Color(0xFF5AA9F5), Color(0xFF9FD0FF), Color(0xFFDCEEFF))))
    // Kept clear of the title (left, middle) and the action icons (top right).
    val w = size.width
    val h = size.height
    cloud(Offset(w * 0.36f, h * 0.17f), w * 0.04f)
    cloud(Offset(w * 0.62f, h * 0.20f), w * 0.065f)
    cloud(Offset(w * 0.88f, h * 0.52f), w * 0.055f)
}

/** A puffy cloud built from overlapping circles, with a soft shaded base. */
private fun DrawScope.cloud(center: Offset, r: Float) {
    val puffs = listOf(
        Offset(-1.1f, 0.25f) to 0.75f, Offset(-0.45f, -0.25f) to 1.0f, Offset(0.35f, -0.45f) to 1.15f,
        Offset(1.1f, 0.05f) to 0.85f, Offset(0.2f, 0.35f) to 0.9f, Offset(-0.5f, 0.4f) to 0.8f,
    )
    puffs.forEach { (o, s) ->
        drawCircle(Color(0xFFC9DDF2), r * s, center + Offset(o.x * r, o.y * r + r * 0.12f))
    }
    puffs.forEach { (o, s) ->
        drawCircle(Color.White, r * s * 0.94f, center + Offset(o.x * r, o.y * r))
    }
}

private fun DrawScope.nightSky(accent: Color) {
    drawRect(Brush.verticalGradient(listOf(Color(0xFF071226), Color(0xFF0E2446), Color(0xFF14325C))))
    val rnd = Random(7) // fixed seed: stars don't jump between frames
    repeat(70) {
        val p = Offset(rnd.nextFloat() * size.width, rnd.nextFloat() * size.height * 0.85f)
        drawCircle(Color.White.copy(alpha = 0.35f + rnd.nextFloat() * 0.6f), 0.8f + rnd.nextFloat() * 1.8f, p)
    }
    // Moon with a soft glow.
    val moon = Offset(size.width * 0.62f, size.height * 0.2f)
    drawCircle(Brush.radialGradient(listOf(accent.copy(alpha = 0.35f), Color.Transparent), moon, 90f), 90f, moon)
    drawCircle(Color(0xFFFFF6D8), 28f, moon)
}

// ---- City Pop: 80s-anime sunset with a striped retro sun ----

private fun DrawScope.retroSunset(dark: Boolean) {
    val sky = if (dark) listOf(Color(0xFF1B0F3A), Color(0xFF4A1A6B), Color(0xFF9C2F7E))
    else listOf(Color(0xFFFFB199), Color(0xFFFF7EB3), Color(0xFFC77DFF))
    val skyBrush = Brush.verticalGradient(sky, startY = 0f, endY = size.height)
    drawRect(skyBrush)

    // Sized and placed between the action icons and the filter chips.
    val r = size.width * 0.13f
    val c = Offset(size.width * 0.72f, size.height * 0.5f)
    val sun = Path().apply { addOval(Rect(c, r)) }
    clipPath(sun) {
        drawRect(Brush.verticalGradient(listOf(Color(0xFFFFE27A), Color(0xFFFF6B8B)), startY = c.y - r, endY = c.y + r))
        // Horizontal gaps that widen towards the bottom, the classic retro-sun look.
        var y = c.y
        var gap = r * 0.05f
        while (y < c.y + r) {
            // Painted with the same sky brush, so the gaps show the sky behind the sun.
            drawRect(skyBrush, Offset(c.x - r, y), Size(r * 2, gap))
            y += gap + r * 0.14f
            gap *= 1.45f
        }
    }
    // Perspective grid lines on the horizon.
    val horizon = size.height * 0.78f
    val line = Color.White.copy(alpha = if (dark) 0.25f else 0.45f)
    for (i in -8..8) {
        drawLine(line, Offset(size.width / 2 + i * size.width * 0.06f, horizon),
            Offset(size.width / 2 + i * size.width * 0.3f, size.height), strokeWidth = 1.5f)
    }
    var y = horizon
    var step = 6f
    while (y < size.height) {
        drawLine(line, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.5f)
        y += step; step *= 1.5f
    }
}

// ---- Glint (浮生拾遗): still water, spreading ripples and drifting glints of light ----

private fun DrawScope.glintWater(dark: Boolean) {
    val sky = if (dark) listOf(Color(0xFF0B3540), Color(0xFF0F2A33), Color(0xFF0C1A1F))
    else listOf(Color(0xFFBFE3E5), Color(0xFFDDEFF0), Color(0xFFF2F6F6))
    drawRect(Brush.verticalGradient(sky))

    // Ripples spreading from one spot, kept clear of the title (left) and the action icons (top right).
    val c = Offset(size.width * 0.74f, size.height * 0.56f)
    val ink = if (dark) Color(0xFF9FDCE0) else Color(0xFF1B6B73)
    for (i in 1..6) {
        val rx = size.width * (0.05f + 0.085f * i)
        val ry = rx * 0.26f
        drawOval(
            color = ink.copy(alpha = (if (dark) 0.38f else 0.30f) / (0.8f + i * 0.35f)),
            topLeft = Offset(c.x - rx, c.y - ry), size = Size(rx * 2, ry * 2),
            style = Stroke(width = 1.4f * density),
        )
    }

    // A few small glints drifting over the water, and one bigger where the ripples begin.
    val gold = if (dark) Color(0xFFFFD98A) else Color(0xFFD9A030)
    val rnd = Random(11) // fixed seed: the glints don't jump between frames
    repeat(9) {
        val p = Offset(size.width * (0.30f + rnd.nextFloat() * 0.68f), size.height * (0.12f + rnd.nextFloat() * 0.62f))
        glint(p, (3f + rnd.nextFloat() * 4.5f) * density, gold.copy(alpha = 0.35f + rnd.nextFloat() * 0.5f))
    }
    glint(Offset(c.x, c.y - size.height * 0.16f), 13f * density, gold)
}

/** A four-point sparkle with a soft glow. */
private fun DrawScope.glint(center: Offset, r: Float, color: Color) {
    drawCircle(Brush.radialGradient(listOf(color.copy(alpha = color.alpha * 0.35f), Color.Transparent), center, r * 1.8f), r * 1.8f, center)
    val k = 0.16f * r // how far the arms pinch in toward the middle
    val star = Path().apply {
        moveTo(center.x, center.y - r)
        quadraticBezierTo(center.x + k, center.y - k, center.x + r * 0.72f, center.y)
        quadraticBezierTo(center.x + k, center.y + k, center.x, center.y + r)
        quadraticBezierTo(center.x - k, center.y + k, center.x - r * 0.72f, center.y)
        quadraticBezierTo(center.x - k, center.y - k, center.x, center.y - r)
        close()
    }
    drawPath(star, color)
}

// ---- Matsuri: 青海波 (seigaiha) wave pattern — a traditional motif ----

private fun DrawScope.seigaiha(accent: Color, background: Color) {
    drawRect(Brush.verticalGradient(listOf(accent.copy(alpha = 0.16f), accent.copy(alpha = 0.04f))))
    val r = 26f * density
    val stroke = Stroke(width = r * 0.07f)
    val rows = (size.height / (r * 0.5f)).toInt() + 2
    val cols = (size.width / (r * 2)).toInt() + 2
    // Rows drawn top to bottom; each scale covers the ones behind it.
    for (row in 0 until rows) {
        val y = row * r * 0.5f
        val shift = if (row % 2 == 0) 0f else r
        for (col in -1 until cols) {
            val c = Offset(col * r * 2 + shift, y)
            drawCircle(background, r, c)
            drawCircle(accent.copy(alpha = 0.06f), r, c)
            listOf(1f, 0.72f, 0.44f).forEach { k ->
                drawCircle(accent.copy(alpha = 0.28f), r * k - stroke.width, c, style = stroke)
            }
        }
    }
}
