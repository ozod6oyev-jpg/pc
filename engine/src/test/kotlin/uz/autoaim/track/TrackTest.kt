package uz.autoaim.track

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import uz.autoaim.vision.GameZoneDetector
import uz.autoaim.vision.Image
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.abs
import kotlin.random.Random

class TrackTest {

    private val rnd = Random(7)

    /** 60 fps, [seconds] s, ±[noise] px shovqin. */
    private fun samples(seconds: Double, noise: Double = 1.0, f: (Double) -> Double) =
        generateSequence(0.0) { it + 1000.0 / 60 }.takeWhile { it <= seconds * 1000 }
            .map { Sample(it, f(it) + (rnd.nextDouble() * 2 - 1) * noise) }.toList()

    @Test
    fun sineMotion() {
        val truth = BoxMotion.Sine(600.0, 200.0, 2400.0, 300.0)
        val fit = MotionFitter.fit(samples(4.0) { truth.x(it) })!!
        println("sinus: ${fit.motion.describe()}, xato %.2f px".format(fit.rmsPx))
        val m = fit.motion as BoxMotion.Sine
        assertEquals(2400.0, m.periodMs, 2400 * 0.01)
        assertEquals(200.0, abs(m.amplitude), 3.0)
        assertTrue(fit.rmsPx < 1.5)
        // Kelajakni bashorat: 1 s keyin.
        assertEquals(truth.x(5000.0), m.x(5000.0), 4.0)
    }

    @Test
    fun bounceMotion() {
        val truth = BoxMotion.Bounce(300.0, 800.0, 0.4, -500.0)
        val fit = MotionFitter.fit(samples(4.0) { truth.x(it) })!!
        println("qaytish: ${fit.motion.describe()}, xato %.2f px".format(fit.rmsPx))
        val m = fit.motion as BoxMotion.Bounce
        assertEquals(0.4, m.speedPxPerMs, 0.4 * 0.03)
        assertTrue(fit.rmsPx < 3.0)
        assertEquals(truth.x(5000.0), m.x(5000.0), 8.0)
    }

    @Test
    fun stillBox() {
        val fit = MotionFitter.fit(samples(2.0, noise = 0.8) { 765.0 })!!
        assertTrue(fit.motion is BoxMotion.Still)
    }

    @Test
    fun flightCalibration() {
        // 1 qadam = 33.3 ms, kechikish 45 ms, kadr vaqti ±4 ms shovqin.
        val pts = (2..14).map { t -> Pair(45 + 33.3 * t + (rnd.nextDouble() * 8 - 4), t.toDouble()) }
        val r = FlightCalibration.fit(pts)!!
        println("uchish: 1 qadam %.1f ms, kechikish %.1f ms".format(r.msPerStep, r.latencyMs))
        assertEquals(33.3, r.msPerStep, 1.0)
        assertEquals(45.0, r.latencyMs, 10.0)
    }

    @Test
    fun rowTrackerOnRealScreen() {
        val image = load("real_01.png")
        val zone = GameZoneDetector().detect(image)!!
        val box = zone.box!!
        val slot = zone.slot!!
        val bodyCenter = (box.left + box.right) / 2f
        val tracker = BoxRowTracker((box.top + box.bottom) / 2, zone.left + 2, zone.right - 2, slot.centerX - bodyCenter, box.width / 2)
        val x = tracker.slotX(image)
        assertNotNull(x)
        assertEquals(slot.centerX, x!!, 1f)

        // Bir qator — tez: 1000 marta.
        val start = System.nanoTime()
        repeat(1000) { tracker.slotX(image) }
        println("qator kuzatish: %.3f ms/kadr".format((System.nanoTime() - start) / 1e6 / 1000))
    }

    @Test
    fun ballFinderOnRealScreen() {
        val image = load("real_01.png")
        val zone = GameZoneDetector().detect(image)!!
        val (x, y) = BallFinder(zone.left, zone.top, zone.right, zone.bottom).find(image)!!
        assertEquals(zone.ball!!.centerX, x, 2f)
        assertEquals(zone.ball!!.centerY, y, 2f)
    }

    @Test
    fun sinePartialObservation() {
        // Faqat ~1.6 davr kuzatilgan (telefonda 4–6 s kuzatuv, davr noma'lum).
        val truth = BoxMotion.Sine(500.0, 150.0, 2600.0, 0.0)
        val fit = MotionFitter.fit(samples(4.2, noise = 1.5) { truth.x(it) })!!
        assertTrue(fit.motion is BoxMotion.Sine)
        assertEquals(2600.0, (fit.motion as BoxMotion.Sine).periodMs, 2600 * 0.02)
    }

    private fun load(name: String): Image {
        val file = sequenceOf("../docs/samples/$name", "docs/samples/$name").map(::File).first { it.exists() }
        val img = ImageIO.read(file)
        val px = IntArray(img.width * img.height)
        img.getRGB(0, 0, img.width, img.height, px, 0, img.width)
        return Image(img.width, img.height, px)
    }
}
