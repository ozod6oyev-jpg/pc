package uz.autoaim.vision

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/** Yoy (traektoriya) nuqtasi: markaz, maydon (px) va eng yorqin piksel yorqinligi. */
data class Dot(val x: Float, val y: Float, val area: Int, val peak: Int)

/**
 * O'yin chizadigan nuqtali yoyning nuqtalarini topadi (tartiblanmagan; tartib va vaqt
 * [TrajectoryFitter] da beriladi).
 *
 * Namunalardan: nuqtalar neytral kulrang, yorqinligi fonga nisbatan past (~48–70, `jjg` da yaqinlari
 * ~200 gacha), diametri ~8–13 px.
 */
class DotDetector {

    fun detect(image: Image, zone: GameZone): List<Dot> {
        val ball = zone.ball ?: return emptyList()
        // Sarlavha (ball, "THIS WEEK") va ramka chizig'idan pastda/ichkarida qidiramiz.
        val x0 = zone.left + EDGE_INSET
        val x1 = zone.right - EDGE_INSET
        val y0 = zone.top + zone.height * HEADER_PERCENT / 100
        val y1 = zone.bottom - EDGE_INSET
        val maxSize = max(6, image.width / 54)

        return Blobs.all(image, x0, y0, x1, y1, minArea = MIN_AREA) { isDotPixel(it) }
            .filter { it.width <= maxSize && it.height <= maxSize && it.area <= maxSize * maxSize }
            .filter { it.width <= it.height * 2 + 1 && it.height <= it.width * 2 + 1 }
            .filterNot { inside(it, zone.box, pad = 4) || nearBall(it, ball) || belowBall(it, ball) }
            .map { Dot(it.centerX, it.centerY, it.area, peak(image, it)) }
    }

    private fun peak(image: Image, b: Blob): Int {
        var best = 0
        for (y in b.top..b.bottom) for (x in b.left..b.right) best = max(best, image.luma(x, y))
        return best
    }

    private fun inside(b: Blob, box: Blob?, pad: Int) =
        box != null && b.right >= box.left - pad && b.left <= box.right + pad && b.bottom >= box.top - pad && b.top <= box.bottom + pad

    private fun nearBall(b: Blob, ball: Ball) =
        hypot(b.centerX - ball.centerX, b.centerY - ball.centerY) < ball.radius * 1.4f

    /** Sharcha ostidagi platforma va undan pastdagi narsalar — yoy u yerdan boshlanmaydi. */
    private fun belowBall(b: Blob, ball: Ball) =
        b.centerY > ball.centerY && abs(b.centerX - ball.centerX) < ball.radius * 3

    companion object {
        private const val EDGE_INSET = 6
        private const val HEADER_PERCENT = 12
        private const val MIN_AREA = 6
        private const val MIN_LUMA = 40

        fun isDotPixel(c: Int): Boolean {
            val r = Image.red(c)
            val g = Image.green(c)
            val b = Image.blue(c)
            return max(r, max(g, b)) - min(r, min(g, b)) <= 20 && Image.luma(c) > MIN_LUMA
        }
    }
}
