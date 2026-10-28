package uz.autoaim.aim

import uz.autoaim.vision.Ball
import uz.autoaim.vision.Blob
import uz.autoaim.vision.Trajectory
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot

/** Barmoqning bosilgan nuqtadan siljishi, px. */
data class Drag(val dx: Float, val dy: Float) {
    operator fun plus(o: Drag) = Drag(dx + o.dx, dy + o.dy)
    val length get() = hypot(dx, dy)
}

/** Sharchaning uchish tezligi, px / nuqta qadami (sharcha markazidan o'tish lahzasida). */
data class Velocity(val vx: Float, val vy: Float)

/** Bitta kadr: qaysi drag'da, nima ko'rindi. [arc] null — yoy ko'rinmadi. */
data class Observation(
    val drag: Drag,
    val ball: Ball,
    val box: Blob,
    val slot: Blob,
    val arc: Trajectory?,
    val wallLeft: Float,
    val wallRight: Float,
    val floorY: Float,
)

sealed class AimAction {
    /** Barmoqni bosilgan nuqtadan shu siljishga sur, so'ng kadr ol. */
    data class Move(val drag: Drag, val why: String) : AimAction()
    /** Mo'ljal tayyor: shu drag'da qo'yib yuborilsa — [check]. */
    data class Ready(val drag: Drag, val check: ShotCheck, val velocity: Velocity) : AimAction()
    /** Mo'ljal topilmadi. [best] — eng yaxshi urinish (bo'lsa). */
    data class Fail(val reason: String, val best: Pair<Drag, ShotCheck>?) : AimAction()
}

/**
 * Otmasdan mo'ljal olish (REJA 3.3): bot tortish yo'nalishini topadi, sinov siljishlari bilan
 * drag → tezlik bog'liqligini o'lchaydi (chiziqli model, Yakobian), GeometryGate bo'yicha
 * ishonchli "TUSHADI" beradigan tezlikni tanlaydi va drag'ni yopiq sikl bilan aniqlashtiradi.
 *
 * Har qadam: ilova [next] ga oxirgi kuzatuvni beradi → [AimAction] oladi → bajaradi → yangi kadr.
 * O'yin fizikasi noma'lum (qora quti) — hammasi ekrandagi yoydan o'lchanadi.
 */
class AimController(
    /** Tegilmagan holatdagi yoy tezligi va sharcha — tegish o'yinga yetganini bilish uchun (null — tekshirilmaydi). */
    private val rest: Velocity? = null,
    private val restBall: Ball? = null,
    /** Nishon qaysi tomonda: +1 o'ngda, −1 chapda (sharchaga nisbatan). Yo'nalishni sinash tartibi shunga bog'liq. */
    private val targetSide: Float = 1f,
    private val probeDistance: Float = 150f,
    private val probeStep: Float = 40f,
    private val maxDrag: Float = 450f,
    private val maxIterations: Int = 6,
    private val tolerancePx: Float = 4f,
) {
    private enum class Phase { DISCOVER, PROBE_X, PROBE_Y, REFINE }

    private var phase = Phase.DISCOVER
    // Birinchi — rogatka taxmini (telefonda tasdiqlangan): nishonga teskari va pastga tortish. Qolganlari —
    // o'yin boshqacha bo'lsa (qora quti).
    private val discover = listOf(
        Drag(-targetSide * probeDistance, probeDistance), Drag(0f, probeDistance),
        Drag(targetSide * probeDistance, probeDistance), Drag(0f, -probeDistance),
        Drag(-targetSide * probeDistance, 0f), Drag(targetSide * probeDistance, 0f),
    )
    private var discoverIndex = 0
    private lateinit var base: Drag
    private val probes = mutableListOf<Pair<Drag, Velocity>>()
    private var iterations = 0
    private var best: Pair<Drag, ShotCheck>? = null

    /** Birinchi harakat (hali kuzatuv yo'q). */
    fun start(): AimAction = AimAction.Move(discover[0], "yo'nalishni aniqlash: ${describe(discover[0])}")

    fun next(obs: Observation): AimAction {
        val v = obs.arc?.takeIf { isReliable(it) }?.let { launchVelocity(it, obs.ball) }
        val responded = restBall == null || responds(rest, restBall, obs.ball, v)
        if (responded) anyResponse = true else ignoredFrames++
        // Tinch holatdagi yoy (tegish yetmagan) — modelga kiritilmaydi, aks holda Yakobian buziladi.
        if (v != null && responded) probes += obs.drag to v

        return when (phase) {
            Phase.DISCOVER -> {
                // Yoy yuqoriga va nishon tomonga (chap yoki o'ng) ketishi kerak.
                val side = if (obs.slot.centerX >= obs.ball.centerX) 1f else -1f
                if (responded && v != null && v.vx * side > 0.5f && v.vy < 0f) {
                    base = obs.drag
                    phase = Phase.PROBE_X
                    AimAction.Move(base + Drag(probeStep, 0f), "sinov: x bo'yicha +${probeStep.toInt()} px")
                } else {
                    discoverIndex++
                    if (discoverIndex >= discover.size) return AimAction.Fail("nishon tomonga yuqoriga yoy beradigan yo'nalish topilmadi", null)
                    AimAction.Move(discover[discoverIndex], "yo'nalishni aniqlash: ${describe(discover[discoverIndex])}")
                }
            }
            Phase.PROBE_X -> {
                phase = Phase.PROBE_Y
                AimAction.Move(base + Drag(0f, probeStep), "sinov: y bo'yicha +${probeStep.toInt()} px")
            }
            Phase.PROBE_Y -> {
                phase = Phase.REFINE
                solveAndMove(obs, "hisoblangan mo'ljal")
            }
            Phase.REFINE -> {
                val arc = obs.arc
                if (arc != null && v != null) {
                    val check = GeometryGate.check(arc, obs.ball, obs.box, obs.slot, obs.floorY)
                    if (best == null || score(check) < score(best!!.second)) best = obs.drag to check
                    if (check.verdict == Verdict.HIT && abs(check.slotOffset ?: Float.MAX_VALUE) <= tolerancePx) {
                        return AimAction.Ready(obs.drag, check, v)
                    }
                }
                iterations++
                if (iterations >= maxIterations) {
                    val b = best
                    return if (b != null && b.second.verdict == Verdict.HIT) AimAction.Ready(b.first, b.second, v ?: Velocity(0f, 0f))
                    else AimAction.Fail("$maxIterations ta aniqlashtirishda TUSHADI topilmadi", b)
                }
                solveAndMove(obs, "aniqlashtirish #$iterations")
            }
        }
    }

    private fun solveAndMove(obs: Observation, why: String): AimAction {
        val g = obs.arc?.g ?: probesG ?: return AimAction.Fail("gravitatsiya noma'lum (yoy ko'rinmadi)", best)
        probesG = g
        val target = targetVelocity(obs.ball, obs.box, obs.slot, g, obs.wallLeft, obs.wallRight, obs.floorY)
            ?: return AimAction.Fail("bu geometriyada ishonchli TUSHADI yo'q", best)
        // Oxirgi sinovlar — model mahalliy bo'lsin (bog'liqlik to'liq chiziqli bo'lmasligi mumkin).
        val model = LinearModel.fit(probes.takeLast(MODEL_PROBES)) ?: return AimAction.Fail("sinovlar yetarli emas (yoy ko'rinmadi)", best)
        val drag = clamp(model.dragFor(target.first) ?: return AimAction.Fail("yoy drag'ga javob bermayapti (model teskarilanmadi)", best))
        return AimAction.Move(drag, "$why: maqsad vx %.1f vy %.1f".format(target.first.vx, target.first.vy))
    }

    private var probesG: Float? = null

    /** Hech bo'lmasa bitta kadrda yoy yoki sharcha tegishga javob berdimi. */
    var anyResponse = false
        private set

    /** Tegishga javob bermagan kadrlar soni (yoy va sharcha tinch holatdagidek). */
    var ignoredFrames = 0
        private set

    private fun clamp(d: Drag): Drag {
        val len = d.length
        return if (len <= maxDrag) d else Drag(d.dx * maxDrag / len, d.dy * maxDrag / len)
    }

    private fun score(c: ShotCheck): Float = when (c.verdict) {
        Verdict.HIT -> abs(c.slotOffset ?: 0f)
        Verdict.RIM -> 1000f + abs(c.slotOffset ?: 0f)
        else -> 10000f
    }

    private fun describe(d: Drag) = "(%+.0f, %+.0f)".format(d.dx, d.dy)

    companion object {
        private const val MIN_DOTS = 5
        private const val MAX_RMS = 1.5f
        private val G_RANGE = 6f..11f

        /**
         * Yoyga ishonsa bo'ladimi: yetarli nuqta, kichik xato, gravitatsiya jismoniy oraliqda (telefonda 8.58–8.59;
         * 4 nuqtali tik yoyda g = −11.4 chiqqan — bunday kadr modelni buzadi).
         */
        fun isReliable(arc: Trajectory) = arc.dots.size >= MIN_DOTS && arc.rms <= MAX_RMS && arc.g in G_RANGE
        private const val MODEL_PROBES = 5
        private const val ROBUST_PX = 8f
        private const val MIN_ENTRY_DEG = 50f

        /**
         * Kadr tegishga javob berdimi: sharcha tinch joyidan siljigan, yoy paydo bo'lgan/yo'qolgan yoki
         * uning tezligi tinch holatdagidan farq qiladi.
         */
        fun responds(rest: Velocity?, restBall: Ball, ball: Ball, v: Velocity?): Boolean {
            if (hypot(ball.centerX - restBall.centerX, ball.centerY - restBall.centerY) > BALL_MOVED_PX) return true
            if ((rest == null) != (v == null)) return true
            if (rest == null || v == null) return false
            return abs(v.vx - rest.vx) > VELOCITY_CHANGED || abs(v.vy - rest.vy) > VELOCITY_CHANGED
        }

        private const val BALL_MOVED_PX = 3f
        private const val VELOCITY_CHANGED = 1.5f

        /** Yoydan sharcha markazidan o'tish lahzasidagi tezlik. */
        fun launchVelocity(arc: Trajectory, ball: Ball): Velocity {
            val t = if (abs(arc.vx) > 0.5f) (ball.centerX - arc.x0) / arc.vx else 0f
            return Velocity(arc.vx, arc.vy + arc.g * t)
        }

        /**
         * Slot markaziga tepadan tushadigan tezliklarni uchish vaqti T bo'yicha qidiradi; GeometryGate
         * ±[ROBUST_PX] siljishda ham TUSHADI deganlari ichidan, kirish burchagi ≥ [MIN_ENTRY_DEG] bo'lgan
         * eng tez (kichik T) otishni tanlaydi.
         */
        fun targetVelocity(
            ball: Ball, box: Blob, slot: Blob, g: Float,
            wallLeft: Float, wallRight: Float, floorY: Float,
        ): Pair<Velocity, ShotCheck>? {
            val tx = slot.centerX
            val ty = slot.top.toFloat()
            var t = 6f
            while (t <= 60f) {
                val vx = (tx - ball.centerX) / t
                val vy = (ty - ball.centerY - g * t * t / 2) / t
                val entryDeg = Math.toDegrees(atan2((vy + g * t).toDouble(), abs(vx).toDouble())).toFloat()
                if (entryDeg >= MIN_ENTRY_DEG) {
                    val arc = Trajectory(emptyList(), listOf(0), ball.centerX, vx, ball.centerY, vy, g, wallLeft, wallRight, 0, 0f)
                    val check = GeometryGate.check(arc, ball, box, slot, floorY)
                    val robust = check.verdict == Verdict.HIT &&
                        GeometryGate.check(arc, ball, box, slot, floorY, boxShiftX = ROBUST_PX).verdict == Verdict.HIT &&
                        GeometryGate.check(arc, ball, box, slot, floorY, boxShiftX = -ROBUST_PX).verdict == Verdict.HIT
                    if (robust) return Velocity(vx, vy) to check
                }
                t += 0.25f
            }
            return null
        }
    }
}

/** v = v0 + J·d — sinovlardan eng kichik kvadratlar bilan (≥3 ta, bir chiziqda yotmagan). */
class LinearModel(
    private val ax: Float, private val bx: Float, private val cx: Float,
    private val ay: Float, private val by: Float, private val cy: Float,
) {
    fun velocity(d: Drag) = Velocity(ax * d.dx + bx * d.dy + cx, ay * d.dx + by * d.dy + cy)

    /** Kerakli tezlik uchun drag (2×2 tenglama). */
    fun dragFor(v: Velocity): Drag? {
        val det = ax * by - bx * ay
        if (abs(det) < 1e-6f) return null
        val rx = v.vx - cx
        val ry = v.vy - cy
        return Drag((rx * by - bx * ry) / det, (ax * ry - rx * ay) / det)
    }

    companion object {
        fun fit(samples: List<Pair<Drag, Velocity>>): LinearModel? {
            if (samples.size < 3) return null
            val x = fit(samples) { it.vx } ?: return null
            val y = fit(samples) { it.vy } ?: return null
            return LinearModel(x[0], x[1], x[2], y[0], y[1], y[2])
        }

        /** value = a·dx + b·dy + c, normal tenglamalar 3×3. */
        private fun fit(samples: List<Pair<Drag, Velocity>>, value: (Velocity) -> Float): FloatArray? {
            val m = Array(3) { DoubleArray(4) }
            for ((d, v) in samples) {
                val row = doubleArrayOf(d.dx.toDouble(), d.dy.toDouble(), 1.0)
                val target = value(v).toDouble()
                for (i in 0..2) {
                    for (j in 0..2) m[i][j] += row[i] * row[j]
                    m[i][3] += row[i] * target
                }
            }
            for (col in 0..2) {
                val pivot = (col..2).maxBy { abs(m[it][col]) }
                if (abs(m[pivot][col]) < 1e-9) return null
                val tmp = m[col]; m[col] = m[pivot]; m[pivot] = tmp
                for (row in 0..2) {
                    if (row == col) continue
                    val f = m[row][col] / m[col][col]
                    for (k in col..3) m[row][k] -= f * m[col][k]
                }
            }
            return FloatArray(3) { (m[it][3] / m[it][it]).toFloat() }
        }
    }
}
