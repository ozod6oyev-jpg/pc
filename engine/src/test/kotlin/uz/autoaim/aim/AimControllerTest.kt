package uz.autoaim.aim

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import uz.autoaim.vision.Ball
import uz.autoaim.vision.Blob
import uz.autoaim.vision.Dot
import uz.autoaim.vision.Trajectory
import kotlin.math.abs

/**
 * Soxta o'yin: drag → tezlik bog'liqligi controller'ga noma'lum (biroz chiziqli emas), sharcha
 * tortganda siljiydi. Controller faqat "ekrandagi yoy" ni ko'radi.
 */
class AimControllerTest {

    private val g = 8.59f

    private class Game(val velocity: (Drag) -> Velocity?)

    /** Pastga tortsa yuqori-o'ngga otadi (rogatka). */
    private val slingshot = Game { d ->
        val vx = -0.40f * d.dx + 0.20f * d.dy
        val vy = 0.05f * d.dx - 0.75f * d.dy + 0.0008f * d.dy * abs(d.dy)
        if (d.length < 20f) null else Velocity(vx, vy)
    }

    /** Teskari: yuqoriga tortsa otadi. */
    private val inverted = Game { d ->
        val vx = 0.40f * d.dx - 0.20f * d.dy
        val vy = -0.05f * d.dx + 0.75f * d.dy
        if (d.length < 20f) null else Velocity(vx, vy)
    }

    private var restBall = Pair(281.5f, 1366.5f)
    private var box = Blob(638, 911, 892, 1070, 0, 765f, 990f)
    private var slot = Blob(711, 915, 820, 924, 0, 765.5f, 919.5f)

    private fun ballAt(d: Drag) = Ball(restBall.first + 0.06f * d.dx, restBall.second + 0.08f * d.dy, 26f, Blob(0, 0, 0, 0, 0, 0f, 0f))

    private fun arcFor(game: Game, d: Drag): Trajectory? {
        val v = game.velocity(d) ?: return null
        val b = ballAt(d)
        val dots = List(10) { Dot(0f, 0f, 50, 70) }
        return Trajectory(dots, List(10) { it }, b.centerX, v.vx, b.centerY, v.vy, g, 49f + 26f, 1030f - 26f, 0, 0.1f)
    }

    private fun observe(game: Game, d: Drag) =
        Observation(d, ballAt(d), box, slot, arcFor(game, d), 49f + 26f, 1030f - 26f, 1784f)

    private fun run(game: Game): AimAction.Ready {
        // Tinch holat: tegilmaganda o'yin odatiy yoyni ko'rsatadi (telefondagidek).
        val side = if (slot.centerX >= restBall.first) 1f else -1f
        val controller = AimController(rest = Velocity(26.87f, -99.6f), restBall = ballAt(Drag(0f, 0f)), targetSide = side)
        var action = controller.start()
        repeat(20) { step ->
            when (val a = action) {
                is AimAction.Move -> {
                    println("qadam $step: ${a.why} → ${a.drag}")
                    action = controller.next(observe(game, a.drag))
                }
                is AimAction.Ready -> {
                    // Haqiqiy (soxta o'yindagi) yoy bilan mustaqil tekshiruv.
                    val truth = GeometryGate.check(arcFor(game, a.drag)!!, ballAt(a.drag), box, slot, 1784f)
                    println("tayyor: ${a.drag} → $truth")
                    assertEquals(Verdict.HIT, truth.verdict)
                    assertTrue("siljish ${truth.slotOffset}", abs(truth.slotOffset!!) <= 4f)
                    return a
                }
                is AimAction.Fail -> fail("mo'ljal topilmadi: ${a.reason}")
            }
        }
        fail("20 qadamda tugamadi")
        error("")
    }

    @Test
    fun slingshotGame() {
        run(slingshot)
    }

    /** Keyingi bosqich (real_07): sharcha o'ngdagi kartada, nishon chapda — yoy chapga-yuqoriga kerak. */
    @Test
    fun targetOnTheLeft() {
        restBall = Pair(771f, 1366.5f)
        box = Blob(232, 891, 485, 1051, 0, 358f, 970f)
        slot = Blob(305, 895, 412, 904, 0, 358.5f, 899.5f)
        val ready = run(slingshot)
        assertTrue("chapga otish: ${ready.velocity}", ready.velocity.vx < 0)
    }

    /**
     * Telefondagi 04:30 sinovi CSV'sidan: vx ≈ −0.383·dx + 7.9, vy ≈ −0.337·dy − 0.3 (rogatka), g 8.59;
     * tik tortishda (dx = 0) 4 nuqtali buzuq yoy (g = −11.4) chiqadi, yuqoriga tortishda — yoy yo'q.
     */
    @Test
    fun phoneCalibratedGame() {
        restBall = Pair(765.5f, 1366.5f)
        box = Blob(232, 891, 485, 1051, 0, 358f, 970f)
        slot = Blob(305, 895, 412, 904, 0, 358.5f, 899.5f)
        val phone = Game { d -> if (d.dy <= 0f || d.length < 20f) null else Velocity(-0.383f * d.dx + 7.9f, -0.337f * d.dy - 0.3f) }
        val controller = AimController(rest = null, restBall = ballAt(Drag(0f, 0f)), targetSide = -1f, maxDrag = 430f)
        var action = controller.start()
        var steps = 0
        while (action is AimAction.Move && steps < 12) {
            steps++
            val d = (action as AimAction.Move).drag
            val arc = if (d.dx == 0f) {
                // buzuq: 4 nuqta, manfiy g
                Trajectory(List(4) { Dot(0f, 0f, 50, 70) }, List(4) { it }, ballAt(d).centerX, 0.08f, ballAt(d).centerY, -8.79f, -11.37f, 75f, 1004f, 0, 0.19f)
            } else arcFor(phone, d)
            println("telefon modeli, qadam $steps: ${action.why} → $d")
            action = controller.next(Observation(d, ballAt(d), box, slot, arc, 75f, 1004f, 1784f))
        }
        println("telefon modeli: $steps qadam → $action")
        assertTrue(action is AimAction.Ready)
        val truth = GeometryGate.check(arcFor(phone, (action as AimAction.Ready).drag)!!, ballAt(action.drag), box, slot, 1784f)
        assertEquals(Verdict.HIT, truth.verdict)
        assertTrue("qadamlar: $steps", steps <= 5)
    }

    @Test
    fun invertedGame() {
        run(inverted)
    }

    /** O'yin tegishni qabul qilmaydi: har doim tinch holatdagi yoy ko'rinadi (telefondagi 1-sinov). */
    @Test
    fun ignoredTouchesAreDetected() {
        val restV = Velocity(26.87f, -99.6f)
        val restBall = ballAt(Drag(0f, 0f))
        val restArc = Trajectory(List(16) { Dot(0f, 0f, 50, 70) }, List(16) { it }, restBall.centerX, restV.vx, restBall.centerY, restV.vy, g, 75f, 1004f, 0, 0.1f)
        val controller = AimController(rest = restV, restBall = restBall)
        var action = controller.start()
        var frames = 0
        while (action is AimAction.Move && frames < 10) {
            frames++
            action = controller.next(Observation((action as AimAction.Move).drag, restBall, box, slot, restArc, 75f, 1004f, 1784f))
        }
        println("e'tiborsiz: $frames kadr, $action")
        assertTrue(action is AimAction.Fail)
        assertTrue(!controller.anyResponse)
        assertEquals(frames, controller.ignoredFrames)
    }

    @Test
    fun targetIsRobustAndSteep() {
        val ball = Ball(281.5f, 1366.5f, 26f, Blob(0, 0, 0, 0, 0, 0f, 0f))
        val (v, check) = AimController.targetVelocity(ball, box, slot, g, 75f, 1004f, 1784f)!!
        println("maqsad: $v → $check")
        assertEquals(Verdict.HIT, check.verdict)
        assertTrue(abs(check.slotOffset!!) <= 6f)
    }
}
