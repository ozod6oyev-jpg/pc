package uz.autoaim.vision

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.imageio.ImageIO

/**
 * Nuqtali yoyni o'qish va davom ettirish. Kutilgan qiymatlar nuqtalarni qo'lda o'lchashdan:
 * gorizontal qadam doimiy (real/ggg 26.9 px, jjg 76.4 px), ikkinchi farq (gravitatsiya) ~8.3 px,
 * jjg da o'ng devordan qaytish (x = 1010 = zona cheti − sharcha radiusi).
 */
class TrajectoryTest {

    private val zones = GameZoneDetector()
    private val dots = DotDetector()

    @Test
    fun real01LandsInSlot() {
        val (zone, arc) = arc("real_01.png")
        assertEquals(0, arc.bounces)
        assertTrue("nuqtalar ${arc.dots.size}", arc.dots.size >= 14)
        assertEquals(26.9f, arc.vx, 0.3f)
        assertEquals(8.35f, arc.g, 0.3f)
        assertTrue("rms ${arc.rms}", arc.rms < 1.5f)
        val slot = zone.slot!!
        val x = arc.landingX(slot.top.toFloat())
        assertNotNull(x)
        assertEquals(768f, x!!, 6f)
        assertTrue("slot ichida", x > slot.left && x < slot.right)
    }

    @Test
    fun gggLandsInSlot() {
        val (zone, arc) = arc("ggg.jpg")
        assertEquals(0, arc.bounces)
        assertEquals(26.9f, arc.vx, 0.3f)
        assertEquals(8.3f, arc.g, 0.4f)
        val slot = zone.slot!!
        val x = arc.landingX(slot.top.toFloat())
        assertNotNull(x)
        assertTrue("slot ichida: $x", x!! > slot.left && x < slot.right)
    }

    @Test
    fun jjgBouncesAndPassesUnderBox() {
        val (zone, arc) = arc("jjg.jpg")
        assertEquals(1, arc.bounces)
        assertTrue("nuqtalar ${arc.dots.size}", arc.dots.size >= 11)
        assertEquals(76.4f, arc.vx, 1f)
        // Cho'qqi qutining tagidan pastda — yoy slot balandligiga umuman yetmaydi.
        assertTrue("cho'qqi ${arc.apexY}", arc.apexY > zone.box!!.bottom)
        assertNull(arc.landingX(zone.slot!!.top.toFloat()))
        // Qaytgan nuqtalar modelga mos: (901.3, 916.9) va (825.0, 952.8).
        val t = arc.times[arc.dots.indexOfFirst { kotlin.math.abs(it.x - 901f) < 3 }].toFloat()
        assertEquals(901.3f, arc.xAt(t), 3f)
        assertEquals(916.9f, arc.yAt(t), 3f)
    }

    private fun arc(name: String): Pair<GameZone, Trajectory> {
        val image = load(name)
        val zone = zones.detect(image)!!
        val ball = zone.ball!!
        val found = dots.detect(image, zone)
        val arc = TrajectoryFitter.fit(found, ball, zone.left + ball.radius, zone.right - ball.radius)
        assertNotNull("yoy tiklanmadi ($name, ${found.size} nuqta)", arc)
        arc!!
        val slotTop = zone.slot!!.top.toFloat()
        println(
            "$name: nuqtalar ${arc.dots.size}/${found.size}, vaqtlar ${arc.times}, qaytish ${arc.bounces}\n" +
                "  vx %.2f, vy %.2f, g %.3f, rms %.2f, cho'qqi y %.1f, slot tepasida x = %s (slot ${zone.slot!!.left}–${zone.slot!!.right})"
                    .format(arc.vx, arc.vy, arc.g, arc.rms, arc.apexY, arc.landingX(slotTop)?.let { "%.1f".format(it) })
        )
        return zone to arc
    }

    private fun load(name: String): Image {
        val file = sequenceOf("../docs/samples/$name", "docs/samples/$name").map(::File).first { it.exists() }
        val img = ImageIO.read(file)
        val px = IntArray(img.width * img.height)
        img.getRGB(0, 0, img.width, img.height, px, 0, img.width)
        return Image(img.width, img.height, px)
    }
}
