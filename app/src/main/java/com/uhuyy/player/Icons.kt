package com.uhuyy.player

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.sin

// Icon digambar sendiri pakai Canvas (tanpa library tambahan).
internal enum class Ic { PLAY, PAUSE, NEXT, PREV, LOCK, PIN, PIP, ROTATE, SUBS, REPEAT, FIT, BACK, IMG,
    INFO, ROT_LAND, ROT_PORT, FIT_PAS, FIT_CROP, FIT_STRETCH, GEAR, GLOBE, CHECK, CHEV }

@Composable
internal fun Icon24(k: Ic, tint: Color, boxSize: Dp = 24.dp) {
    Canvas(Modifier.size(boxSize)) {
        drawIc(k, tint, this.size.minDimension / 24f)
    }
}

// Ikon seek: panah melingkar + angka 5 di tengah
@Composable
internal fun SeekIcon(forward: Boolean, tint: Color, boxSize: Dp = 30.dp) {
    Box(Modifier.size(boxSize), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val u = this.size.minDimension / 24f
            if (forward) arcArrow(tint, u, 12f, 12.5f, 8.5f, -60f, 300f)
            else arcArrow(tint, u, 12f, 12.5f, 8.5f, -120f, -300f)
        }
        Text("5", color = tint, fontSize = (boxSize.value * 0.36f).sp, fontWeight = FontWeight.Bold)
    }
}

// Busur dengan kepala panah di ujungnya. Sudut dalam derajat, sweep negatif = berlawanan arah jarum jam.
private fun DrawScope.arcArrow(
    c: Color, u: Float, cx: Float, cy: Float, r: Float, startDeg: Float, sweepDeg: Float
) {
    val st = Stroke(width = 2f * u, cap = StrokeCap.Round, join = StrokeJoin.Round)
    drawArc(c, startDeg, sweepDeg, false, Offset((cx - r) * u, (cy - r) * u), Size(2f * r * u, 2f * r * u), style = st)
    val s = if (sweepDeg >= 0f) 1f else -1f
    val th = (startDeg + sweepDeg) * 0.017453292f
    val px = cx * u + r * u * cos(th)
    val py = cy * u + r * u * sin(th)
    val tx = -sin(th) * s
    val ty = cos(th) * s
    val nx = cos(th)
    val ny = sin(th)
    val w = 3.2f * u
    val path = Path()
    path.moveTo(px + tx * w * 1.3f, py + ty * w * 1.3f)
    path.lineTo(px + nx * w, py + ny * w)
    path.lineTo(px - nx * w, py - ny * w)
    path.close()
    drawPath(path, c)
}

private fun DrawScope.drawIc(k: Ic, c: Color, u: Float) {
    val sw = 2f * u
    val st = Stroke(width = sw, cap = StrokeCap.Round, join = StrokeJoin.Round)
    fun o(x: Float, y: Float) = Offset(x * u, y * u)
    fun line(x1: Float, y1: Float, x2: Float, y2: Float, w: Float = sw) {
        drawLine(c, o(x1, y1), o(x2, y2), strokeWidth = w, cap = StrokeCap.Round)
    }
    fun tri(x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float) {
        val p = Path()
        p.moveTo(x1 * u, y1 * u)
        p.lineTo(x2 * u, y2 * u)
        p.lineTo(x3 * u, y3 * u)
        p.close()
        drawPath(p, c)
    }
    when (k) {
        Ic.PLAY -> tri(7f, 4f, 20f, 12f, 7f, 20f)
        Ic.PAUSE -> {
            drawRoundRect(c, topLeft = o(6f, 4f), size = Size(4f * u, 16f * u), cornerRadius = CornerRadius(u, u))
            drawRoundRect(c, topLeft = o(14f, 4f), size = Size(4f * u, 16f * u), cornerRadius = CornerRadius(u, u))
        }
        Ic.NEXT -> {
            tri(5f, 5f, 15f, 12f, 5f, 19f)
            drawRoundRect(c, topLeft = o(16.6f, 5f), size = Size(2.6f * u, 14f * u), cornerRadius = CornerRadius(u, u))
        }
        Ic.PREV -> {
            tri(19f, 5f, 9f, 12f, 19f, 19f)
            drawRoundRect(c, topLeft = o(4.8f, 5f), size = Size(2.6f * u, 14f * u), cornerRadius = CornerRadius(u, u))
        }
        Ic.LOCK -> {
            drawRoundRect(c, topLeft = o(5f, 11f), size = Size(14f * u, 10f * u), cornerRadius = CornerRadius(2f * u, 2f * u))
            drawArc(c, 180f, 180f, false, o(8f, 4f), Size(8f * u, 8f * u), style = st)
            line(8f, 8f, 8f, 11f)
            line(16f, 8f, 16f, 11f)
        }
        Ic.PIN -> {
            drawCircle(c, radius = 4.6f * u, center = o(12f, 8f))
            line(12f, 12f, 12f, 21f)
            line(7.5f, 12.5f, 16.5f, 12.5f)
        }
        Ic.PIP -> {
            drawRoundRect(c, topLeft = o(3f, 5f), size = Size(18f * u, 14f * u), cornerRadius = CornerRadius(2f * u, 2f * u), style = st)
            drawRoundRect(c, topLeft = o(12f, 11f), size = Size(6.5f * u, 5f * u), cornerRadius = CornerRadius(u, u))
        }
        Ic.ROTATE -> {
            drawRoundRect(c, topLeft = o(8f, 5f), size = Size(8f * u, 14f * u), cornerRadius = CornerRadius(1.5f * u, 1.5f * u), style = Stroke(width = 1.6f * u))
            arcArrow(c, u, 12f, 12f, 10.5f, 15f, 60f)
            arcArrow(c, u, 12f, 12f, 10.5f, 195f, 60f)
        }
        Ic.SUBS -> {
            drawRoundRect(c, topLeft = o(3f, 5f), size = Size(18f * u, 14f * u), cornerRadius = CornerRadius(2f * u, 2f * u), style = Stroke(width = 1.8f * u))
            line(7f, 10.5f, 11f, 10.5f, 1.8f * u)
            line(13f, 10.5f, 17f, 10.5f, 1.8f * u)
            line(7f, 14f, 9.5f, 14f, 1.8f * u)
            line(11.5f, 14f, 17f, 14f, 1.8f * u)
        }
        Ic.REPEAT -> arcArrow(c, u, 12f, 12f, 8f, -60f, 300f)
        Ic.FIT -> {
            line(4f, 9f, 4f, 4f); line(4f, 4f, 9f, 4f)
            line(15f, 4f, 20f, 4f); line(20f, 4f, 20f, 9f)
            line(20f, 15f, 20f, 20f); line(20f, 20f, 15f, 20f)
            line(9f, 20f, 4f, 20f); line(4f, 20f, 4f, 15f)
        }
        Ic.BACK -> {
            line(20f, 12f, 5f, 12f)
            line(11f, 6f, 5f, 12f)
            line(5f, 12f, 11f, 18f)
        }
        Ic.INFO -> {
            drawCircle(c, radius = 9f * u, center = o(12f, 12f), style = st)
            drawCircle(c, radius = 1.3f * u, center = o(12f, 7.8f))
            line(12f, 11f, 12f, 17f)
        }
        Ic.ROT_LAND -> {
            drawRoundRect(c, topLeft = o(3f, 7f), size = Size(18f * u, 10f * u), cornerRadius = CornerRadius(2f * u, 2f * u), style = st)
            line(6.2f, 10.5f, 6.2f, 13.5f, 1.6f * u)
        }
        Ic.ROT_PORT -> {
            drawRoundRect(c, topLeft = o(7f, 3f), size = Size(10f * u, 18f * u), cornerRadius = CornerRadius(2f * u, 2f * u), style = st)
            line(10.5f, 6.2f, 13.5f, 6.2f, 1.6f * u)
        }
        Ic.FIT_PAS -> {
            drawRoundRect(c, topLeft = o(3f, 5f), size = Size(18f * u, 14f * u), cornerRadius = CornerRadius(2f * u, 2f * u), style = Stroke(width = 1.8f * u))
            drawRoundRect(c, topLeft = o(8f, 8.5f), size = Size(8f * u, 7f * u), cornerRadius = CornerRadius(u, u))
        }
        Ic.FIT_CROP -> {
            drawRoundRect(c, topLeft = o(3f, 5f), size = Size(18f * u, 14f * u), cornerRadius = CornerRadius(2f * u, 2f * u), style = Stroke(width = 1.8f * u))
            drawRoundRect(c, topLeft = o(5.5f, 7f), size = Size(13f * u, 10f * u), cornerRadius = CornerRadius(u, u))
        }
        Ic.FIT_STRETCH -> {
            drawRoundRect(c, topLeft = o(3f, 5f), size = Size(18f * u, 14f * u), cornerRadius = CornerRadius(2f * u, 2f * u), style = Stroke(width = 1.8f * u))
            line(7f, 12f, 17f, 12f, 1.8f * u)
            line(7f, 12f, 9.6f, 9.4f, 1.8f * u); line(7f, 12f, 9.6f, 14.6f, 1.8f * u)
            line(17f, 12f, 14.4f, 9.4f, 1.8f * u); line(17f, 12f, 14.4f, 14.6f, 1.8f * u)
        }
        Ic.GEAR -> {
            drawCircle(c, radius = 3.2f * u, center = o(12f, 12f), style = st)
            drawCircle(c, radius = 6.8f * u, center = o(12f, 12f), style = st)
            for (i in 0 until 8) {
                val a = i * 0.7853982f
                line(12f + cos(a) * 6.8f, 12f + sin(a) * 6.8f, 12f + cos(a) * 10f, 12f + sin(a) * 10f, 2.8f * u)
            }
        }
        Ic.GLOBE -> {
            drawCircle(c, radius = 9f * u, center = o(12f, 12f), style = st)
            drawOval(c, topLeft = o(8f, 3f), size = Size(8f * u, 18f * u), style = Stroke(width = 1.6f * u))
            line(3f, 12f, 21f, 12f, 1.6f * u)
        }
        Ic.CHECK -> {
            line(5f, 12.5f, 10f, 17.5f)
            line(10f, 17.5f, 19f, 7f)
        }
        Ic.CHEV -> {
            line(9f, 5f, 16f, 12f)
            line(16f, 12f, 9f, 19f)
        }
        Ic.IMG -> {
            drawRoundRect(c, topLeft = o(3f, 4f), size = Size(18f * u, 16f * u), cornerRadius = CornerRadius(2f * u, 2f * u), style = Stroke(width = 1.8f * u))
            drawCircle(c, radius = 1.7f * u, center = o(9f, 9.5f))
            line(3.5f, 18f, 9f, 13f, 1.8f * u)
            line(9f, 13f, 13f, 17f, 1.8f * u)
            line(13f, 17f, 15.5f, 14.5f, 1.8f * u)
            line(15.5f, 14.5f, 20.5f, 19f, 1.8f * u)
        }
    }
}
