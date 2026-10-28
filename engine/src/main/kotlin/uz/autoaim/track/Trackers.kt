package uz.autoaim.track

import uz.autoaim.vision.BallDetector
import uz.autoaim.vision.BoxDetector
import uz.autoaim.vision.PixelSource
import kotlin.math.max
import kotlin.math.min

/**
 * Harakatlanuvchi qutini har kadrda **tez** kuzatish: to'liq tahlil o'rniga quti tanasidan o'tuvchi
 * bitta gorizontal qator o'qiladi (1080 px — bir necha ms). Qatordagi eng uzun "quti rangidagi" bo'lak —
 * qutining chap va o'ng cheti; slot markazi = bo'lak markazi + [slotOffset] (to'liq tahlildan).
 *
 * @param rowY quti tanasining o'rtasidan o'tuvchi qator (sharcha platformasidan boshqa balandlikda).
 */
class BoxRowTracker(
    private val rowY: Int,
    private val x0: Int,
    private val x1: Int,
    private val slotOffset: Float,
    private val minWidth: Int,
) {
    /** Slot markazining x koordinatasi yoki null (qatorda quti topilmadi). */
    fun slotX(src: PixelSource): Float? {
        var bestStart = -1
        var bestLen = 0
        var start = -1
        val gap = 3 // ajratgich chiziqlari / shovqin uchun kichik bo'shliq kechiriladi
        var lastHit = -100
        for (x in max(0, x0)..min(src.width - 1, x1)) {
            if (BoxDetector.isBoxBody(src.argb(x, rowY))) {
                if (x - lastHit > gap) start = x
                lastHit = x
                if (x - start + 1 > bestLen) {
                    bestLen = x - start + 1
                    bestStart = start
                }
            }
        }
        if (bestLen < minWidth) return null
        return bestStart + (bestLen - 1) / 2f + slotOffset
    }
}

/**
 * Uchayotgan sharchani tez topish: zona ichida yashil nur piksellarini qadam bilan qidiradi va ularning
 * chegaralaridan markazni oladi (to'liq [BallDetector] dan tezroq; sharcha uchayotganda platformaga botmagan).
 */
class BallFinder(
    private val x0: Int,
    private val y0: Int,
    private val x1: Int,
    private val y1: Int,
    private val step: Int = 2,
) {
    /** (x, y) yoki null. */
    fun find(src: PixelSource): Pair<Float, Float>? {
        var minX = Int.MAX_VALUE
        var maxX = Int.MIN_VALUE
        var minY = Int.MAX_VALUE
        var maxY = Int.MIN_VALUE
        var count = 0
        var y = max(0, y0)
        while (y <= min(src.height - 1, y1)) {
            var x = max(0, x0)
            while (x <= min(src.width - 1, x1)) {
                if (BallDetector.isGlow(src.argb(x, y))) {
                    count++
                    if (x < minX) minX = x
                    if (x > maxX) maxX = x
                    if (y < minY) minY = y
                    if (y > maxY) maxY = y
                }
                x += step
            }
            y += step
        }
        if (count < MIN_HITS) return null
        // Bir nechta yashil narsa bo'lsa (chegaralar juda keng) — ishonchsiz.
        if (maxX - minX > (x1 - x0) / 6 || maxY - minY > (x1 - x0) / 6) return null
        val r = (maxX - minX) / 2f
        return Pair((minX + maxX) / 2f, minY + r)
    }

    companion object {
        private const val MIN_HITS = 12
    }
}
