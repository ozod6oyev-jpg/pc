package uz.autoaim.track

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Harakatlanuvchi qutiga otish rejasi.
 *
 * Telefonda o'lchangan: sinusoida, amplituda 68 px, davr 8.8 s, eng katta tezlik ~49 px/s. Chekkada quti deyarli
 * to'xtaydi (±0.5 s ichida ~4 px) — shuning uchun nishon nuqtasi **chekka**: uchish vaqti xato baholansa ham
 * otish buzilmaydi. Doimiy tezlikda borib-kelishda chekkada keskin burilish bor — nishon oraliqning 25% nuqtasi.
 */
object LeadPlanner {

    /** Nishon: slot markazi shu x da bo'lganda tushishi kerak. */
    data class AimPoint(val x: Double, val description: String)

    fun chooseAim(motion: BoxMotion, nowMs: Double): AimPoint = when (motion) {
        is BoxMotion.Still -> AimPoint(motion.at, "harakatsiz")
        is BoxMotion.Sine -> {
            // Hozir qaysi chekkaga ketyapti — o'sha chekka (vaqt bo'yicha eng yaqin).
            val dx = motion.x(nowMs + 50) - motion.x(nowMs)
            val goingRight = dx >= 0
            AimPoint(if (goingRight) motion.maxX else motion.minX, if (goingRight) "o'ng chekka" else "chap chekka")
        }
        is BoxMotion.Bounce -> AimPoint(motion.minX + (motion.maxX - motion.minX) * 0.25, "oraliqning 25% nuqtasi")
    }

    /** Sharcha [arrivalDelayMs] dan keyin yetib keladi — o'sha lahzada slot nishonga [tolerancePx] dan yaqin bo'ladimi. */
    fun releaseNow(motion: BoxMotion, nowMs: Double, arrivalDelayMs: Double, aimX: Double, tolerancePx: Double): Boolean =
        abs(motion.x(nowMs + arrivalDelayMs) - aimX) <= tolerancePx

    /**
     * Keyingi qo'yib yuborish lahzasi ([horizonMs] ichida) yoki null: ruxsat oynasining **eng yaxshi** nuqtasi
     * (xato eng kichik — chekkada aynan burilish lahzasi), birinchi kirish nuqtasi emas — uchish vaqti har
     * ikki tomonga xato bo'lsa ham bir xil chidamli.
     */
    fun nextRelease(motion: BoxMotion, nowMs: Double, arrivalDelayMs: Double, aimX: Double, tolerancePx: Double, horizonMs: Double): Double? {
        var t = nowMs
        var best: Double? = null
        var bestErr = Double.MAX_VALUE
        while (t <= nowMs + horizonMs) {
            val err = abs(motion.x(t + arrivalDelayMs) - aimX)
            if (err <= tolerancePx) {
                if (err < bestErr) { bestErr = err; best = t }
            } else if (best != null) {
                return best // oyna tugadi
            }
            t += 5.0
        }
        return best
    }

    /** Sharcha slot balandligiga yetguncha nuqta-qadamlar: y(t) = y0 + vy·t + g·t²/2 = slotTop (tushayotganda). */
    fun flightSteps(ballY: Double, vy: Double, g: Double, slotTop: Double): Double? {
        val disc = vy * vy - 2 * g * (ballY - slotTop)
        if (g <= 0 || disc < 0) return null
        return (-vy + sqrt(disc)) / g
    }

    /** Kutish chegarasi: sinusoida/qaytishda ~1.2 davr, ko'pi bilan [capMs]. */
    fun waitLimitMs(motion: BoxMotion, capMs: Double = 15000.0): Double = when (motion) {
        is BoxMotion.Still -> 0.0
        is BoxMotion.Sine -> minOf(capMs, motion.periodMs * 1.2)
        is BoxMotion.Bounce -> minOf(capMs, max(1.0, 2 * (motion.maxX - motion.minX) / motion.speedPxPerMs) * 1.2)
    }
}

/**
 * Jonli kuzatuvning sirpanuvchi oynasi: oxirgi [windowMs] dagi kuzatuvlar va talab bo'yicha qayta moslash
 * (mo'ljal olish davomida faza aniqlashib boradi).
 */
class RollingMotion(private val windowMs: Double = 12000.0) {
    private val samples = ArrayDeque<Sample>()

    @Synchronized
    fun add(s: Sample) {
        samples.addLast(s)
        while (samples.isNotEmpty() && s.tMs - samples.first().tMs > windowMs) samples.removeFirst()
    }

    @Synchronized
    fun snapshot(): List<Sample> = samples.toList()

    fun fit(): MotionFit? = MotionFitter.fit(snapshot())
}
