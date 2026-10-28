package uz.autoaim.track

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt

/** Kuzatuv: vaqt (ms) va slot markazi x (px). */
data class Sample(val tMs: Double, val x: Double)

/** Qutining gorizontal harakat qonuni. */
sealed class BoxMotion {
    abstract fun x(tMs: Double): Double
    abstract val minX: Double
    abstract val maxX: Double
    abstract fun describe(): String

    data class Still(val at: Double) : BoxMotion() {
        override fun x(tMs: Double) = at
        override val minX get() = at
        override val maxX get() = at
        override fun describe() = "harakatsiz (x = %.1f)".format(at)
    }

    /** x = c + A·sin(2π(t − t0)/T). */
    data class Sine(val center: Double, val amplitude: Double, val periodMs: Double, val t0: Double) : BoxMotion() {
        override fun x(tMs: Double) = center + amplitude * sin(2 * PI * (tMs - t0) / periodMs)
        override val minX get() = center - abs(amplitude)
        override val maxX get() = center + abs(amplitude)
        override fun describe() = "sinusoida: markaz %.0f, amplituda %.0f px, davr %.0f ms".format(center, abs(amplitude), periodMs)
    }

    /** Doimiy tezlikda [minX]..[maxX] orasida borib-kelish (chetlardan qaytish). t0 da minX da, o'ngga ketmoqda. */
    data class Bounce(override val minX: Double, override val maxX: Double, val speedPxPerMs: Double, val t0: Double) : BoxMotion() {
        override fun x(tMs: Double): Double {
            val span = maxX - minX
            if (span <= 0) return minX
            val period = 2 * span
            var d = (speedPxPerMs * (tMs - t0)) % period
            if (d < 0) d += period
            return minX + if (d <= span) d else period - d
        }
        override fun describe() = "doimiy tezlikda borib-kelish: x %.0f…%.0f, tezlik %.0f px/s, davr %.0f ms"
            .format(minX, maxX, speedPxPerMs * 1000, 2 * (maxX - minX) / speedPxPerMs)
    }
}

data class MotionFit(val motion: BoxMotion, val rmsPx: Double, val samples: Int)

/**
 * Kuzatuvlardan harakat qonunini topadi: harakatsiz / sinusoida / doimiy tezlikda borib-kelish —
 * eng kichik o'rtacha kvadratik xatolisi tanlanadi.
 */
object MotionFitter {

    fun fit(samples: List<Sample>): MotionFit? {
        if (samples.size < 8) return null
        val min = samples.minOf { it.x }
        val max = samples.maxOf { it.x }
        if (max - min < STILL_PX) {
            val mean = samples.map { it.x }.average()
            return MotionFit(BoxMotion.Still(mean), rms(samples, BoxMotion.Still(mean)), samples.size)
        }
        val candidates = listOfNotNull(fitSine(samples), fitBounce(samples))
        val best = candidates.minByOrNull { rms(samples, it) } ?: return null
        return MotionFit(best, rms(samples, best), samples.size)
    }

    /** Model ishonchlimi: xato 3 px dan yoki amplitudaning 5% idan (qaysi biri katta) oshmasligi kerak. */
    fun isReliable(fit: MotionFit): Boolean {
        val amplitude = (fit.motion.maxX - fit.motion.minX) / 2
        return !fit.rmsPx.isNaN() && fit.rmsPx <= maxOf(3.0, amplitude * 0.05)
    }

    fun rms(samples: List<Sample>, m: BoxMotion): Double =
        sqrt(samples.sumOf { (it.x - m.x(it.tMs)).let { e -> e * e } } / samples.size)

    /**
     * Davr — to'g'ridan-to'g'ri qidiruv: logarifmik to'r (0.4 s … kuzatuvning 4 barobari), so'ng eng yaxshisi
     * atrofida ±10% mayda to'r; har davr uchun (c, a, b) chiziqli eng kichik kvadratlar.
     * (O'rta chiziqni kesishlardan baholash telefonda buzilgan: x 0.5 px qadamli — ketma-ket ikki qiymat o'rtaga
     * aynan teng bo'lib 0/0 = NaN, shovqin esa bitta kesishni ikki marta sanagan.)
     */
    internal fun fitSine(s: List<Sample>): BoxMotion.Sine? {
        val span = s.last().tMs - s.first().tMs
        if (span <= 0) return null
        var best: BoxMotion.Sine? = null
        var bestErr = Double.MAX_VALUE
        fun tryPeriod(period: Double) {
            val m = linearSine(s, period) ?: return
            val err = rms(s, m)
            if (!err.isNaN() && err < bestErr) { bestErr = err; best = m }
        }
        val minPeriod = 400.0
        val maxPeriod = maxOf(4 * span, 20000.0)
        val steps = 240
        for (i in 0..steps) tryPeriod(minPeriod * Math.pow(maxPeriod / minPeriod, i.toDouble() / steps))
        val coarse = best?.periodMs ?: return null
        for (i in -100..100) tryPeriod(coarse * (1 + i * 0.001))
        return best
    }

    /** Berilgan davr uchun x = c + a·sin ωt + b·cos ωt (chiziqli eng kichik kvadratlar). */
    private fun linearSine(s: List<Sample>, period: Double): BoxMotion.Sine? {
        if (period <= 0) return null
        val w = 2 * PI / period
        val m = Array(3) { DoubleArray(4) }
        for (p in s) {
            val row = doubleArrayOf(1.0, sin(w * p.tMs), cos(w * p.tMs))
            for (i in 0..2) {
                for (j in 0..2) m[i][j] += row[i] * row[j]
                m[i][3] += row[i] * p.x
            }
        }
        val sol = solve3(m) ?: return null
        val (c, a, b) = Triple(sol[0], sol[1], sol[2])
        // a·sin ωt + b·cos ωt = A·sin(ω(t − t0)),  A = √(a²+b²),  −ω·t0 = atan2(b, a)
        val amp = hypot(a, b)
        val t0 = -atan2(b, a) / w
        return BoxMotion.Sine(c, amp, period, t0)
    }

    /** Tezlik — burilishlardan uzoq qismlardagi |dx/dt| medianasi; faza — t0 bo'yicha qidiruv. */
    internal fun fitBounce(s: List<Sample>): BoxMotion.Bounce? {
        val min = s.minOf { it.x }
        val max = s.maxOf { it.x }
        val span = max - min
        val speeds = mutableListOf<Double>()
        for (i in 1 until s.size) {
            val dt = s[i].tMs - s[i - 1].tMs
            val mid = (s[i].x + s[i - 1].x) / 2
            if (dt > 0 && mid > min + span * 0.15 && mid < max - span * 0.15) speeds += abs(s[i].x - s[i - 1].x) / dt
        }
        if (speeds.isEmpty()) return null
        val speed = speeds.sorted()[speeds.size / 2]
        if (speed <= 0) return null
        val period = 2 * span / speed
        var best: BoxMotion.Bounce? = null
        var bestErr = Double.MAX_VALUE
        val start = s.first().tMs
        var t0 = start - period
        while (t0 <= start) {
            val m = BoxMotion.Bounce(min, max, speed, t0)
            val err = rms(s, m)
            if (err < bestErr) { bestErr = err; best = m }
            t0 += period / 400
        }
        return best
    }

    private fun solve3(m: Array<DoubleArray>): DoubleArray? {
        for (col in 0..2) {
            val pivot = (col..2).maxBy { abs(m[it][col]) }
            if (abs(m[pivot][col]) < 1e-12) return null
            val tmp = m[col]; m[col] = m[pivot]; m[pivot] = tmp
            for (row in 0..2) {
                if (row == col) continue
                val f = m[row][col] / m[col][col]
                for (k in col..3) m[row][k] -= f * m[col][k]
            }
        }
        return DoubleArray(3) { m[it][3] / m[it][it] }
    }

    private const val STILL_PX = 4.0
}

/**
 * Uchish vaqtini kalibrlash: otishdan keyingi kadrlarda sharcha x (va y) dan yoy vaqti t (nuqta qadamlarida)
 * olinadi; kadr vaqti = kechikish + t·(1 qadam ms) — chiziqli eng kichik kvadratlar.
 */
object FlightCalibration {
    data class Result(val msPerStep: Double, val latencyMs: Double, val points: Int, val rmsMs: Double)

    /** [points]: (otishdan keyingi ms, yoy vaqti qadamlarda). */
    fun fit(points: List<Pair<Double, Double>>): Result? {
        if (points.size < 3) return null
        val mt = points.map { it.second }.average()
        val my = points.map { it.first }.average()
        var sxy = 0.0
        var sxx = 0.0
        for ((ms, t) in points) {
            sxy += (t - mt) * (ms - my)
            sxx += (t - mt) * (t - mt)
        }
        if (sxx == 0.0) return null
        val slope = sxy / sxx
        val intercept = my - slope * mt
        val rms = sqrt(points.sumOf { (ms, t) -> (ms - (intercept + slope * t)).let { it * it } } / points.size)
        return Result(slope, intercept, points.size, rms)
    }
}
