package uz.autoaim.track

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Telefondagi xato (06:16): kuzatuv uzaygani sari sinusoida moslash NaN berib, 37–47 px xatoli "borib-kelish"
 * modeli tanlangan — quti o'ng tomonda bo'lsa ham "chap chekkada bo'ladi" deb otilgan. Haqiqiy jonli kuzatuv
 * (826 kadr) bilan: har qanday oyna uzunligida sinusoida tanlanishi va bashorat haqiqatga mos bo'lishi kerak.
 */
class PhoneMotionRegressionTest {
    @Test
    fun replayPhoneTrack() {
        val file = sequenceOf("../docs/samples/live_box_06-16.csv", "docs/samples/live_box_06-16.csv").map(::File).first { it.exists() }
        val s = file.readLines().drop(1).map { val p = it.split(","); Sample(p[0].toDouble(), p[1].toDouble()) }
        val release = 12298.0
        val actual = s.filter { kotlin.math.abs(it.tMs - (release + 553)) < 20 }.map { it.x }.average()
        for (until in listOf(6500.0, 9000.0, 11000.0, release)) {
            val r = RollingMotion()
            s.filter { it.tMs <= until }.forEach { r.add(it) }
            val fit = r.fit()!!
            println("t≤%.0f: ${fit.motion.describe()}, xato %.2f px".format(until, fit.rmsPx))
            assertTrue("t≤$until: sinusoida bo'lishi kerak, ${fit.motion}", fit.motion is BoxMotion.Sine)
            assertTrue(MotionFitter.isReliable(fit))
        }
        val r = RollingMotion()
        s.filter { it.tMs <= release }.forEach { r.add(it) }
        val predicted = r.fit()!!.motion.x(release + 553)
        println("otishdan 553 ms keyin: bashorat %.1f, haqiqiy %.1f".format(predicted, actual))
        assertEquals(actual, predicted, 4.0)
    }

    @Test
    fun badModelIsRejected() {
        val bad = MotionFit(BoxMotion.Bounce(245.6, 383.6, 0.045, 0.0), 47.2, 700)
        assertTrue(!MotionFitter.isReliable(bad))
    }
}
