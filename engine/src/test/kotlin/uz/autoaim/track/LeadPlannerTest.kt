package uz.autoaim.track

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class LeadPlannerTest {

    /** Telefonda o'lchangan harakat (05:54): sinusoida, markaz 318, amplituda 68, davr 8821 ms. */
    private val phone = BoxMotion.Sine(318.0, 68.0, 8821.0, 0.0)

    @Test
    fun sineAimsAtExtremeAndToleratesFlightError() {
        val now = 1000.0
        val aim = LeadPlanner.chooseAim(phone, now)
        assertTrue(aim.x == phone.maxX || aim.x == phone.minX)
        val delay = 550.0 // taxminiy kechikish + uchish
        val release = LeadPlanner.nextRelease(phone, now, delay, aim.x, 4.0, 12000.0)
        assertNotNull(release)
        // Uchish vaqti 2 baravar xato bo'lsa ham (275 yoki 1100 ms) — tushish nuqtasida xato kichik.
        for (realDelay in listOf(275.0, 550.0, 1100.0)) {
            val err = abs(phone.x(release!! + realDelay) - aim.x)
            println("haqiqiy kechikish $realDelay ms → slot xatosi %.1f px".format(err))
            // Slotga sig'ish ruxsati ±28 px — uchish vaqti 2 baravar xato bo'lsa ham ancha zaxira bilan.
            assertTrue("xato $err", err < 6)
        }
    }

    @Test
    fun bounceReleaseWindow() {
        val m = BoxMotion.Bounce(250.0, 390.0, 0.06, 0.0)
        val aim = LeadPlanner.chooseAim(m, 0.0)
        val release = LeadPlanner.nextRelease(m, 0.0, 500.0, aim.x, 4.0, LeadPlanner.waitLimitMs(m))!!
        assertEquals(aim.x, m.x(release + 500.0), 4.0)
    }

    @Test
    fun flightSteps() {
        // real_08 (bot mo'ljali): sharcha y 1380.5, vy −96.5, g 8.59, slot tepasi 895 → ~14 qadam.
        val t = LeadPlanner.flightSteps(1380.5, -96.52, 8.59, 895.0)!!
        assertEquals(14.0, t, 1.0)
    }
}
