package com.xiaohan.xhsnotegen.ui.theme

import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import kotlin.random.Random

/**
 * Decorative art for the Glint and Summer Sky themes. Everything is original
 * and drawn in code — no third-party artwork.
 *
 * [inBand] draws only the motif (no sky fill), in the band's ink, on the left
 * behind the home progress ring, fading out before the status list so that
 * text never sits on it.
 */
@Composable
fun ThemeBackdrop(modifier: Modifier = Modifier, inBand: Boolean = false) {
    val theme = LocalAppTheme.current
    if (theme.backdrop == Backdrop.NONE) return
    val dark = LocalDarkTheme.current
    val colors = MaterialTheme.colorScheme
    val ink = MaterialTheme.app.onBand

    Canvas(
        modifier
            // Offscreen layer so the fade mask (DstIn) only affects this art.
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen },
    ) {
        if (inBand) {
            when (theme.backdrop) {
                Backdrop.SKY -> bandSky(ink, dark)
                Backdrop.RIPPLE -> bandRipples(ink)
                Backdrop.NONE -> Unit
            }
            drawRect(
                brush = Brush.horizontalGradient(0f to Color.Black, 0.3f to Color.Black, 0.46f to Color.Transparent),
                blendMode = BlendMode.DstIn,
            )
            return@Canvas
        }
        when (theme.backdrop) {
            Backdrop.SKY -> if (dark) nightSky(colors.primary) else summerSky()
            Backdrop.RIPPLE -> glintWater(dark)
            Backdrop.NONE -> Unit
        }
        drawRect(brush = Brush.verticalGradient(0f to Color.Black, 0.55f to Color.Black, 1f to Color.Transparent), blendMode = BlendMode.DstIn)
    }
}

// ---- Band versions: the motif alone, around the ring on the left ----

private fun DrawScope.bandRipples(ink: Color) {
    val c = Offset(size.width * 0.17f, size.height * 0.5f)
    for (i in 1..6) {
        val rx = size.width * (0.04f + 0.07f * i)
        val ry = rx * 0.3f
        drawOval(ink.copy(alpha = 0.34f / (0.8f + i * 0.3f)), Offset(c.x - rx, c.y - ry), Size(rx * 2, ry * 2), style = Stroke(width = 1.3f * density))
    }
    glint(Offset(size.width * 0.33f, size.height * 0.2f), 6f * density, GoldNightMark.copy(alpha = 0.7f))
    glint(Offset(size.width * 0.04f, size.height * 0.78f), 4f * density, GoldNightMark.copy(alpha = 0.55f))
}

private fun DrawScope.bandSky(ink: Color, dark: Boolean) {
    if (dark) {
        val rnd = Random(7) // fixed seed: stars don't jump between frames
        repeat(26) {
            val p = Offset(rnd.nextFloat() * size.width * 0.42f, rnd.nextFloat() * size.height)
            drawCircle(ink.copy(alpha = 0.25f + rnd.nextFloat() * 0.4f), (0.7f + rnd.nextFloat() * 1.3f) * density, p)
        }
    } else {
        listOf(Offset(0.05f, 0.22f) to 0.035f, Offset(0.36f, 0.8f) to 0.045f).forEach { (o, r) ->
            val c = Offset(size.width * o.x, size.height * o.y)
            val rr = size.width * r
            listOf(Offset(-1f, 0.2f) to 0.75f, Offset(-0.35f, -0.3f) to 1f, Offset(0.45f, -0.2f) to 1.05f, Offset(1.1f, 0.2f) to 0.8f).forEach { (d, k) ->
                drawCircle(ink.copy(alpha = 0.22f), rr * k, c + Offset(d.x * rr, d.y * rr))
            }
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

