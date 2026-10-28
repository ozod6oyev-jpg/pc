package uz.autoaim.vision

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.sqrt

/**
 * Tiklangan yoy. Vaqt birligi — nuqtalar orasidagi qadam (o'yin nuqtalarni teng vaqtda qo'yadi).
 *
 * Model (gorizontal devorlar orasida "yoyilgan" x bilan):
 *   xu(t) = x0 + vx·t,     y(t) = y0 + vy·t + g·t²/2
 * Ekrandagi x — xu ni [wallLeft]..[wallRight] orasida oynadek "buklash" ([fold]); devor = zona cheti ∓ sharcha radiusi.
 */
data class Trajectory(
    /** Sharchadan boshlab tartiblangan nuqtalar va ularning vaqt indekslari (o'tkazib yuborilganlari hisobga olingan). */
    val dots: List<Dot>,
    val times: List<Int>,
    val x0: Float,
    val vx: Float,
    val y0: Float,
    val vy: Float,
    val g: Float,
    val wallLeft: Float,
    val wallRight: Float,
    /** Devordan qaytishlar soni (ko'rinib turgan qismida). */
    val bounces: Int,
    /** Moslashtirish xatosi, px (o'rtacha kvadratik). */
    val rms: Float,
) {
    fun xAt(t: Float): Float = fold(x0 + vx * t)

    fun yAt(t: Float): Float = y0 + vy * t + g * t * t / 2

    val apexTime: Float get() = -vy / g
    val apexY: Float get() = yAt(apexTime)

    /** Yoy cho'qqidan keyin [y] balandlikka tushadigan vaqt; yoy u yerga yetmasa — null. */
    fun descendingTimeAt(y: Float): Float? {
        // g/2·t² + vy·t + (y0 − y) = 0, kattaroq ildiz (pastga tushayotgan tarmoq)
        val a = g / 2
        val disc = vy * vy - 4 * a * (y0 - y)
        if (g <= 0 || disc < 0) return null
        return (-vy + sqrt(disc)) / (2 * a)
    }

    /** [y] balandlikda (pastga tushayotganda) sharcha markazining x koordinatasi; yetmasa — null. */
    fun landingX(y: Float): Float? = descendingTimeAt(y)?.let { xAt(it) }

    private fun fold(xu: Float): Float {
        val width = wallRight - wallLeft
        if (width <= 0) return xu
        val period = 2 * width
        var m = (xu - wallLeft) % period
        if (m < 0) m += period
        return wallLeft + if (m <= width) m else period - m
    }
}

/**
 * Nuqtalardan yoyni tiklaydi:
 *  1. Sharchaga eng yaqin nuqtadan boshlab zanjir: har qadamda keyingi nuqta bashorat qilinadi
 *     (oldingi qadam + gravitatsiya, devordan qaytish), unga eng yaqin nomzod olinadi. Yo'qolgan/xira
 *     nuqtalar ustidan 3 qadamgacha sakraydi — vaqt indeksi shunga ko'ra o'sadi.
 *  2. Devordan qaytgan qismlar "yoyiladi" (oynadek aks ettiriladi).
 *  3. Eng kichik kvadratlar: xu — chiziqli, y — kvadratik.
 */
object TrajectoryFitter {

    private const val MAX_SKIP = 3

    fun fit(candidates: List<Dot>, ball: Ball, wallLeft: Float, wallRight: Float): Trajectory? {
        if (candidates.size < 3) return null
        val left = candidates.toMutableList()

        // Boshlanish: sharchaga eng yaqin, keyin unga eng yaqin.
        val first = left.minBy { hypot(it.x - ball.centerX, it.y - ball.centerY) }
        if (hypot(first.x - ball.centerX, first.y - ball.centerY) > ball.radius * 12) return null
        left.remove(first)
        val second = left.minBy { hypot(it.x - first.x, it.y - first.y) }
        left.remove(second)

        val dots = mutableListOf(first, second)
        val times = mutableListOf(0, 1)
        val unfolded = mutableListOf(first.x, second.x)
        // xu = a·x + b — devordan qaytishlarni yoyish uchun
        var a = 1f
        var b = 0f
        var bounces = 0
        var vx = second.x - first.x
        var vy = second.y - first.y
        var g = Float.NaN

        while (left.isNotEmpty()) {
            val last = dots.last()
            var accepted = false
            for (k in 1..MAX_SKIP) {
                val gk = if (g.isNaN()) 0f else g
                var px = last.x + vx * k
                val py = last.y + vy * k + gk * k * (k + 1) / 2
                var wall = Float.NaN
                if (px > wallRight) { wall = wallRight; px = 2 * wallRight - px }
                else if (px < wallLeft) { wall = wallLeft; px = 2 * wallLeft - px }

                val tolerance = hypot(vx, vy) * k * (if (g.isNaN()) 0.45f else 0.25f) + 6f
                val next = left.minBy { hypot(it.x - px, it.y - py) }
                if (hypot(next.x - px, next.y - py) > tolerance) continue

                val dy = next.y - last.y
                if (g.isNaN()) {
                    g = (dy - vy * k) * 2 / (k * (k + 1))
                } else {
                    val gNew = (dy - vy * k) * 2 / (k * (k + 1))
                    g = (g * (dots.size - 2) + gNew) / (dots.size - 1)
                }
                vy += k * g
                if (!wall.isNaN()) {
                    val uWall = a * wall + b
                    a = -a
                    b = 2 * uWall - b
                    vx = -vx
                    bounces++
                } else {
                    vx = (next.x - last.x) / k
                }
                dots += next
                times += times.last() + k
                unfolded += a * next.x + b
                left.remove(next)
                accepted = true
                break
            }
            if (!accepted) break
        }
        if (dots.size < 3) return null

        val t = times.map { it.toFloat() }
        val (x0, vxFit) = linearFit(t, unfolded)
        val (y0, vyFit, g2) = quadraticFit(t, dots.map { it.y })
        val gFit = 2 * g2

        var sum = 0.0
        for (i in t.indices) {
            val ex = unfolded[i] - (x0 + vxFit * t[i])
            val ey = dots[i].y - (y0 + vyFit * t[i] + g2 * t[i] * t[i])
            sum += ex * ex + ey * ey
        }
        val rms = sqrt(sum / t.size).toFloat()
        return Trajectory(dots, times, x0, vxFit, y0, vyFit, gFit, wallLeft, wallRight, bounces, rms)
    }

    /** y = c0 + c1·x */
    private fun linearFit(x: List<Float>, y: List<Float>): Pair<Float, Float> {
        val n = x.size
        val mx = x.average()
        val my = y.average()
        var sxy = 0.0
        var sxx = 0.0
        for (i in 0 until n) {
            sxy += (x[i] - mx) * (y[i] - my)
            sxx += (x[i] - mx) * (x[i] - mx)
        }
        val c1 = if (sxx == 0.0) 0.0 else sxy / sxx
        return Pair((my - c1 * mx).toFloat(), c1.toFloat())
    }

    /** y = c0 + c1·x + c2·x² (normal tenglamalar, 3×3). */
    private fun quadraticFit(x: List<Float>, y: List<Float>): Triple<Float, Float, Float> {
        val s = DoubleArray(5)
        val r = DoubleArray(3)
        for (i in x.indices) {
            var p = 1.0
            for (k in 0..4) {
                s[k] += p
                if (k <= 2) r[k] += p * y[i]
                p *= x[i]
            }
        }
        val m = arrayOf(
            doubleArrayOf(s[0], s[1], s[2], r[0]),
            doubleArrayOf(s[1], s[2], s[3], r[1]),
            doubleArrayOf(s[2], s[3], s[4], r[2]),
        )
        // Gauss usuli
        for (col in 0..2) {
            val pivot = (col..2).maxBy { abs(m[it][col]) }
            val tmp = m[col]; m[col] = m[pivot]; m[pivot] = tmp
            for (row in 0..2) {
                if (row == col || m[col][col] == 0.0) continue
                val f = m[row][col] / m[col][col]
                for (k in col..3) m[row][k] -= f * m[col][k]
            }
        }
        return Triple((m[0][3] / m[0][0]).toFloat(), (m[1][3] / m[1][1]).toFloat(), (m[2][3] / m[2][2]).toFloat())
    }
}
