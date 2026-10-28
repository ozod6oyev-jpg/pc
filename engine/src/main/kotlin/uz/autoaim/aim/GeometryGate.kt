package uz.autoaim.aim

import uz.autoaim.vision.Ball
import uz.autoaim.vision.Blob
import uz.autoaim.vision.Trajectory
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/** Otish natijasi (bashorat). */
enum class Verdict(val text: String, val success: Boolean = false) {
    HIT("TUSHADI", success = true),
    RIM("QIRRAGA TEGADI"),
    TOP("QUTI USTIGA TUSHADI"),
    SIDE_LEFT("CHAP YONGA URILADI"),
    SIDE_RIGHT("O'NG YONGA URILADI"),
    BOTTOM("TAGIGA URILADI"),
    SHORT("YETMAYDI"),
    OVER("OSHIB KETADI"),
    UNDER("OSTIDAN O'TADI"),
}

/**
 * [verdict] lahzasi: vaqt (nuqta qadamlarida), sharcha markazi va (TUSHADI/QIRRA uchun) slot markazidan
 * siljish (TUSHADI da — slot tepasini kesib o'tgan joyda).
 */
data class ShotCheck(val verdict: Verdict, val t: Float, val x: Float, val y: Float, val slotOffset: Float?)

/**
 * Qattiq shartlar (REJA 3.4): sharchaning yo'li **radiusi bilan** quti qismlariga tegmasligi va slotga
 * tepadan kirishi kerak. O'yin yoyni qutini e'tiborsiz chizadi (yoy quti ichidan "o'tib" ketishi mumkin),
 * shuning uchun to'qnashuvni o'zimiz hisoblaymiz.
 *
 * Quti — kesmalar to'plami: chap yon, o'ng yon, tag, slotning ikki yonidagi ust qismlar va slot devorlari
 * (teshik qirralari). Sharcha markazi slot ichida teshik tubigacha (slot.bottom) hech narsaga tegmasdan
 * tushsa — TUSHADI.
 */
object GeometryGate {

    private const val DT = 0.02f
    private const val MAX_T = 400f

    /**
     * @param boxShiftX quti uchish paytida gorizontal siljigan bo'lsa (harakatlanuvchi quti) — shuncha px.
     * @param floorY shundan pastga tushsa — sharcha yo'qoldi (odatda zona tagi).
     */
    fun check(arc: Trajectory, ball: Ball, box: Blob, slot: Blob, floorY: Float, boxShiftX: Float = 0f): ShotCheck {
        val r = ball.radius
        val left = box.left + boxShiftX
        val right = box.right + boxShiftX
        val top = box.top.toFloat()
        val bottom = box.bottom.toFloat()
        val holeL = slot.left + boxShiftX
        val holeR = slot.right + boxShiftX
        val holeBottom = slot.bottom.toFloat()

        // Kesmalar: (x1, y1, x2, y2, natija)
        val parts = listOf(
            Segment(left, top, left, bottom, Verdict.SIDE_LEFT),
            Segment(right, top, right, bottom, Verdict.SIDE_RIGHT),
            Segment(left, bottom, right, bottom, Verdict.BOTTOM),
            Segment(left, top, holeL, top, Verdict.TOP),
            Segment(holeR, top, right, top, Verdict.TOP),
            Segment(holeL, top, holeL, holeBottom, Verdict.RIM),
            Segment(holeR, top, holeR, holeBottom, Verdict.RIM),
        )

        var t = startTime(arc, ball)
        var prevY = arc.yAt(t)
        var crossedTopX: Float? = null
        var entryX: Float? = null
        // Sharcha qutining ustidan o'tdimi (keyin devordan qaytib chetga tushsa ham — OSHIB KETADI).
        var passedOver = false
        while (t < MAX_T) {
            t += DT
            val x = arc.xAt(t)
            val y = arc.yAt(t)
            val descending = y > prevY

            // Slot tepasini kesib o'tgan joy — siljish shu yerda o'lchanadi.
            if (descending && prevY < slot.top && y >= slot.top) entryX = x
            // Teshik tubiga yetdi — hech narsaga tegmagan.
            if (descending && prevY < holeBottom && y >= holeBottom && x > holeL && x < holeR) {
                return ShotCheck(Verdict.HIT, t, x, y, (entryX ?: x) - (holeL + holeR) / 2)
            }
            // Tegish: eng yaqin kesma; teshik qirrasining uchiga tegish ham QIRRA.
            var hit: Segment? = null
            var best = Float.MAX_VALUE
            for (s in parts) {
                val d = s.distance(x, y)
                if (d < r && d < best) { best = d; hit = s }
            }
            if (hit != null) {
                val verdict = if (hit.verdict == Verdict.TOP && (near(x, holeL, r) || near(x, holeR, r))) Verdict.RIM else hit.verdict
                val offset = if (verdict == Verdict.RIM) x - (holeL + holeR) / 2 else null
                return ShotCheck(verdict, t, x, y, offset)
            }
            if (descending && prevY < top && y >= top && crossedTopX == null) crossedTopX = x
            if (y < top - r && x > left - r && x < right + r) passedOver = true
            if (y > floorY + r) {
                val cx = crossedTopX
                val verdict = when {
                    cx == null -> Verdict.UNDER
                    passedOver || cx > right -> Verdict.OVER
                    else -> Verdict.SHORT
                }
                return ShotCheck(verdict, t, x, y, null)
            }
            prevY = y
        }
        return ShotCheck(Verdict.UNDER, t, arc.xAt(t), arc.yAt(t), null)
    }

    /** Sharcha markazidan o'tadigan lahza (yoy sharchadan boshlanadi). */
    private fun startTime(arc: Trajectory, ball: Ball): Float {
        if (abs(arc.vx) > 0.5f) return (ball.centerX - arc.x0) / arc.vx
        // Deyarli tik otish: y bo'yicha, ko'tarilayotgan tarmoq.
        val a = arc.g / 2
        val disc = arc.vy * arc.vy - 4 * a * (arc.y0 - ball.centerY)
        return if (disc < 0 || a == 0f) arc.times.first().toFloat() else (-arc.vy - kotlin.math.sqrt(disc)) / (2 * a)
    }

    private fun near(x: Float, edge: Float, r: Float) = abs(x - edge) < r

    private class Segment(val x1: Float, val y1: Float, val x2: Float, val y2: Float, val verdict: Verdict) {
        fun distance(px: Float, py: Float): Float {
            val dx = x2 - x1
            val dy = y2 - y1
            val len2 = dx * dx + dy * dy
            val k = if (len2 == 0f) 0f else max(0f, min(1f, ((px - x1) * dx + (py - y1) * dy) / len2))
            return hypot(px - (x1 + k * dx), py - (y1 + k * dy))
        }
    }
}
