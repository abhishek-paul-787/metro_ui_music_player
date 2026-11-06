package com.metro.music.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

enum class Ico { Play, Pause, Next, Prev, Shuffle, Repeat, RepeatOne, Settings, Note, Add, Back }

private fun DrawScope.tri(a: Offset, b: Offset, c: Offset, col: Color) {
    drawPath(Path().apply { moveTo(a.x, a.y); lineTo(b.x, b.y); lineTo(c.x, c.y); close() }, col)
}

@Composable
fun MetroIcon(kind: Ico, size: Dp, color: Color) {
    Canvas(Modifier.size(size)) {
        val w = this.size.width; val s = w / 24f; val st = 2f * s
        fun o(x: Float, y: Float) = Offset(x * s, y * s)
        when (kind) {
            Ico.Play -> tri(o(7f, 4f), o(7f, 20f), o(20f, 12f), color)
            Ico.Pause -> { drawRect(color, o(6f, 4f), Size(4f * s, 16f * s)); drawRect(color, o(14f, 4f), Size(4f * s, 16f * s)) }
            Ico.Next -> { tri(o(5f, 5f), o(5f, 19f), o(16f, 12f), color); drawRect(color, o(17f, 5f), Size(2.5f * s, 14f * s)) }
            Ico.Prev -> { tri(o(19f, 5f), o(19f, 19f), o(8f, 12f), color); drawRect(color, o(4.5f, 5f), Size(2.5f * s, 14f * s)) }
            Ico.Shuffle -> {
                drawLine(color, o(3f, 7f), o(8f, 7f), st); drawLine(color, o(8f, 7f), o(15f, 17f), st); drawLine(color, o(15f, 17f), o(18f, 17f), st)
                drawLine(color, o(3f, 17f), o(8f, 17f), st); drawLine(color, o(8f, 17f), o(15f, 7f), st); drawLine(color, o(15f, 7f), o(18f, 7f), st)
                tri(o(17f, 3.5f), o(17f, 10.5f), o(22f, 7f), color); tri(o(17f, 13.5f), o(17f, 20.5f), o(22f, 17f), color)
            }
            Ico.Repeat, Ico.RepeatOne -> {
                drawLine(color, o(5f, 11f), o(5f, 8f), st); drawLine(color, o(5f, 8f), o(16f, 8f), st)
                drawLine(color, o(19f, 13f), o(19f, 16f), st); drawLine(color, o(19f, 16f), o(8f, 16f), st)
                tri(o(15f, 4f), o(15f, 12f), o(21f, 8f), color); tri(o(9f, 12f), o(9f, 20f), o(3f, 16f), color)
                if (kind == Ico.RepeatOne) drawLine(color, o(12f, 10.5f), o(12f, 14f), st)
            }
            Ico.Settings -> {
                drawCircle(color, 4.2f * s, o(12f, 12f), style = Stroke(st))
                for (i in 0 until 8) {
                    val a = Math.PI / 4 * i
                    drawLine(color, o(12f + 6.5f * cos(a).toFloat(), 12f + 6.5f * sin(a).toFloat()), o(12f + 9.5f * cos(a).toFloat(), 12f + 9.5f * sin(a).toFloat()), st * 1.6f)
                }
            }
            Ico.Note -> {
                drawCircle(color, 3.5f * s, o(8f, 18f)); drawRect(color, o(10.5f, 4f), Size(2.2f * s, 14f * s))
                tri(o(12.5f, 4f), o(12.5f, 9f), o(19f, 11f), color)
            }
            Ico.Add -> { drawLine(color, o(12f, 4f), o(12f, 20f), st); drawLine(color, o(4f, 12f), o(20f, 12f), st) }
            Ico.Back -> { drawLine(color, o(20f, 12f), o(5f, 12f), st); drawLine(color, o(5f, 12f), o(11f, 6f), st); drawLine(color, o(5f, 12f), o(11f, 18f), st) }
        }
    }
}

/** Windows Phone application-bar style button: outlined circle with a glyph. */
@Composable
fun CircleButton(kind: Ico, onClick: () -> Unit, diameter: Dp = 48.dp, tint: Color = Metro.fg, active: Boolean = false) {
    Box(
        Modifier.size(diameter).clip(CircleShape)
            .background(if (active) Metro.accent else Color.Transparent)
            .border(2.dp, if (active) Metro.accent else tint, CircleShape)
            .metroPress(onClick),
        contentAlignment = Alignment.Center,
    ) { MetroIcon(kind, diameter * 0.5f, if (active) Color.White else tint) }
}
