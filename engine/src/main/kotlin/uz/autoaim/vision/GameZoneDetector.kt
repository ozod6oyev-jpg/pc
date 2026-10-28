package uz.autoaim.vision

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** Ekrandagi o'yin zonasi (yumaloq burchakli ramka) — rasm koordinatalarida, chegaralar qo'shilgan holda. */
data class GameZone(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
    val ball: Ball?,
    /** Qutining to'liq tanasi (barcha qatlamlar). */
    val box: Blob?,
    /** Qutining tepasidagi slot tirqishi — nishon. */
    val slot: Blob?,
    val confidence: Float,
) {
    val width get() = right - left + 1
    val height get() = bottom - top + 1

    fun contains(x: Float, y: Float) = x >= left && x <= right && y >= top && y <= bottom

    /** Ekran koordinatasi → zona ichidagi nisbiy (0..1) koordinata. */
    fun normalize(x: Float, y: Float) = Pair((x - left) / (width - 1), (y - top) / (height - 1))

    /** Zona ichidagi nisbiy (0..1) koordinata → ekran koordinatasi. */
    fun denormalize(nx: Float, ny: Float) = Pair(left + nx * (width - 1), top + ny * (height - 1))
}

/**
 * O'yin zonasini butun ekran kadridan topadi (REJA: o'yin to'liq ekranga yoyilmaydi, atrofida
 * status bar, ilova sarlavhasi va tugmalar bo'lishi mumkin).
 *
 * Belgilar (namuna kadrlardan o'lchangan):
 *  - zonaning chap/o'ng chetida ingichka yorqin vertikal chiziq (yorqinlik ~32–40, fon ~11–13),
 *    deyarli butun balandlik bo'ylab uzluksiz; burchaklar yumaloq;
 *  - zona ichida yashil sharcha (tayanch — ekranda boshqa bunday rang yo'q) va slotli quti ([BoxDetector]);
 *  - zonaning tepasi/pasti tashqi fondan rangi bilan farq qiladi (tepa ~19, past ~7, tashqari ~12).
 */
class GameZoneDetector {

    private val ballDetector = BallDetector()
    private val boxDetector = BoxDetector()

    fun detect(image: Image): GameZone? {
        val w = image.width
        val h = image.height
        if (w < 50 || h < 50) return null
        val luma = image.lumaArray()
        val off = max(2, w / 360)

        val ball = ballDetector.detect(image, 0, 0, w - 1, h - 1)
        val glow = ball?.glow

        // 1. Chap va o'ng chegara chiziqlari: sharcha topilgan bo'lsa — uning ikki tomonidan,
        //    bo'lmasa — ekranning chap va o'ng yarmidan.
        val leftRange = if (glow != null) off until glow.left else off until w / 2
        val rightRange = if (glow != null) (glow.right + 1) until (w - off) else (w / 2) until (w - off)
        val rowHint = ball?.centerY?.toInt()
        val leftLine = bestEdge(luma, w, h, off, leftRange, rowHint, insideToRight = true) ?: return null
        val rightLine = bestEdge(luma, w, h, off, rightRange, rowHint, insideToRight = false) ?: return null
        if (rightLine.x - leftLine.x < w / 3) return null

        // 2. Tepa va past: chiziq tugagan joydan (burchak boshlanishi) tashqariga qarab,
        //    qator rangi zona tashqarisidagi rangga tenglashguncha.
        val lineTop = min(leftLine.top, rightLine.top)
        val lineBottom = max(leftLine.bottom, rightLine.bottom)
        val inset = (rightLine.x - leftLine.x) / 8
        val x0 = leftLine.x + inset
        val x1 = rightLine.x - inset
        val searchLimit = max(8, (rightLine.x - leftLine.x) / 8)

        val outsideTop = outsideColor(luma, w, h, off, leftLine.x, rightLine.x, lineTop, lineTop + searchLimit)
        val outsideBottom = outsideColor(luma, w, h, off, leftLine.x, rightLine.x, lineBottom - searchLimit, lineBottom)
        val top = findBoundary(luma, w, h, x0, x1, lineTop, -1, searchLimit, outsideTop)
        val bottom = findBoundary(luma, w, h, x0, x1, lineBottom, +1, searchLimit, outsideBottom)

        // 3. Tasdiqlash: zona ichida quti va slot (sarlavha matnidan pastda, sharchadan farqli).
        val headerBottom = top + (bottom - top) * 12 / 100
        val target = boxDetector.detect(image, leftLine.x + 1, headerBottom, rightLine.x - 1, bottom, exclude = glow)

        val ballInside = ball?.takeIf { it.centerX > leftLine.x && it.centerX < rightLine.x && it.centerY > top && it.centerY < bottom }
        var confidence = 0.5f
        if (ballInside != null) confidence += 0.25f
        if (target?.slot != null) confidence += 0.25f
        return GameZone(leftLine.x, top, rightLine.x, bottom, ballInside, target?.body, target?.slot, confidence)
    }

    private class Edge(val x: Int, val top: Int, val bottom: Int, val score: Int)

    /**
     * [range] dagi ustunlar ichidan eng uzun vertikal chegarani topadi.
     * Chegara = ingichka yorqin chiziq (ikki qo'shnisidan yorqinroq) yoki keskin pog'ona,
     * bunda zona tomoni ([insideToRight]) to'q bo'lishi kerak.
     */
    private fun bestEdge(
        luma: IntArray, w: Int, h: Int, off: Int,
        range: IntRange, rowHint: Int?, insideToRight: Boolean,
    ): Edge? {
        if (range.isEmpty()) return null
        val minRun = h * 30 / 100
        var best: Edge? = null
        for (x in range) {
            if (x - off < 0 || x + off >= w) continue
            // Eng uzun uzluksiz bo'lak (8 qatorgacha bo'shliq kechiriladi).
            var runStart = -1
            var lastHit = -100
            var bestStart = -1
            var bestEnd = -1
            for (y in 0 until h) {
                if (isEdgePixel(luma, w, x, y, off, insideToRight)) {
                    if (y - lastHit > GAP) runStart = y
                    lastHit = y
                    if (y - runStart > bestEnd - bestStart) {
                        bestStart = runStart
                        bestEnd = y
                    }
                }
            }
            val length = bestEnd - bestStart + 1
            if (bestStart < 0 || length < minRun) continue
            if (rowHint != null && (rowHint < bestStart || rowHint > bestEnd)) continue
            // Teng uzunlikda — zonaga (sharchaga) yaqinrog'i: chapda kattaroq x, o'ngda kichikroq x.
            // Ustunlar chapdan o'ngga ko'riladi: chap chiziqda deyarli teng uzunlikdagi keyingisi (sharchaga
            // yaqinroq) afzal, o'ng chiziqda — birinchisi.
            val better = best == null || length > best.score + off || (insideToRight && length >= best.score - off)
            if (better) best = Edge(x, bestStart, bestEnd, length)
        }
        return best
    }

    private fun isEdgePixel(luma: IntArray, w: Int, x: Int, y: Int, off: Int, insideToRight: Boolean): Boolean {
        val row = y * w
        val c = luma[row + x]
        val a = luma[row + x - off]
        val b = luma[row + x + off]
        val inside = if (insideToRight) b else a
        val outside = if (insideToRight) a else b
        if (inside > DARK_MAX) return false
        val peak = c - max(a, b) >= LINE_CONTRAST
        val step = abs(outside - inside) >= STEP_CONTRAST
        return peak || step
    }

    /** Zona chizig'idan tashqaridagi (chap va o'ng chekka) o'rtacha yorqinlik. */
    private fun outsideColor(luma: IntArray, w: Int, h: Int, off: Int, left: Int, right: Int, y0: Int, y1: Int): Float? {
        var sum = 0L
        var n = 0
        for (y in max(0, y0)..min(h - 1, y1)) {
            for (x in (left - 4 * off)..(left - 2 * off)) if (x >= 0) { sum += luma[y * w + x]; n++ }
            for (x in (right + 2 * off)..(right + 4 * off)) if (x < w) { sum += luma[y * w + x]; n++ }
        }
        return if (n == 0) null else sum.toFloat() / n
    }

    /**
     * [from] qatordan [dir] yo'nalishida ko'pi bilan [limit] qator yurib, qator o'rtachasi tashqi rangga
     * 3 qator ketma-ket teng bo'lgan joyni topadi va zonaning oxirgi qatorini qaytaradi.
     * Rasm cheti uchrasa — zona chetgacha boradi; hech narsa topilmasa — [from].
     */
    private fun findBoundary(luma: IntArray, w: Int, h: Int, x0: Int, x1: Int, from: Int, dir: Int, limit: Int, outside: Float?): Int {
        if (outside == null) return from
        var matches = 0
        var y = from
        while (y in 0 until h && abs(y - from) <= limit) {
            var sum = 0L
            var n = 0
            var x = x0
            while (x <= x1) { sum += luma[y * w + x]; n++; x += 2 }
            if (abs(sum.toFloat() / n - outside) <= OUTSIDE_TOLERANCE) {
                if (++matches == 3) return y - dir * 3
            } else {
                matches = 0
            }
            y += dir
        }
        return if (y !in 0 until h) y - dir else from
    }

    companion object {
        private const val GAP = 8
        private const val DARK_MAX = 45
        private const val LINE_CONTRAST = 10
        private const val STEP_CONTRAST = 30
        private const val OUTSIDE_TOLERANCE = 2.5f
    }
}
