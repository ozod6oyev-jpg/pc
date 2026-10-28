package uz.autoaim.vision

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.abs

/**
 * Haqiqiy telefondan (1080×2340) ilova orqali saqlangan to'liq ekran skrinshotlari.
 * Kutilgan qiymatlar piksellar bo'yicha qo'lda o'lchangan.
 */
class RealScreenTest {

    private val detector = GameZoneDetector()

    @Test
    fun real01() {
        val image = load("real_01.png")
        val start = System.nanoTime()
        val zone = detector.detect(image)
        val ms = (System.nanoTime() - start) / 1_000_000
        assertNotNull("zona topilmadi", zone)
        zone!!
        println("real_01 ($ms ms): zona ${zone.left},${zone.top},${zone.right},${zone.bottom}\n  sharcha ${zone.ball}\n  quti ${zone.box}\n  slot ${zone.slot}")

        // Zona: chap chiziq x=48–49, o'ng x=1030–1031, tepa y=422, past y=1784.
        near("zona", intArrayOf(zone.left, zone.top, zone.right, zone.bottom), intArrayOf(48, 422, 1031, 1784), 2)
        // Quti (barcha qatlamlar) va slot — nishon, 1 px aniqlikda.
        val box = zone.box!!
        near("quti", intArrayOf(box.left, box.top, box.right, box.bottom), intArrayOf(638, 911, 892, 1070), 2)
        val slot = zone.slot!!
        near("slot", intArrayOf(slot.left, slot.top, slot.right, slot.bottom), intArrayOf(711, 915, 820, 924), 1)
        // Sharcha nuri: x 256–307, y 1341–1387 (pasti platforma tirqishiga botgan) →
        // aylana: markaz (281.5, 1366.5), radius 26.
        val ball = zone.ball!!
        val glow = ball.glow
        near("sharcha nuri", intArrayOf(glow.left, glow.top, glow.right, glow.bottom), intArrayOf(256, 1341, 307, 1387), 1)
        assertEquals(281.5f, ball.centerX, 0.5f)
        assertEquals(1366.5f, ball.centerY, 0.5f)
        assertEquals(26f, ball.radius, 0.5f)
        assertEquals(1f, zone.confidence, 0.001f)
    }

    private fun near(what: String, actual: IntArray, expected: IntArray, tolerance: Int) {
        val names = listOf("left", "top", "right", "bottom")
        for (i in 0..3) {
            assertTrue(
                "$what ${names[i]}: kutilgan ${expected[i]}, topilgan ${actual[i]}; ${actual.toList()}",
                abs(actual[i] - expected[i]) <= tolerance,
            )
        }
    }

    private fun load(name: String): Image {
        val file = sequenceOf("../docs/samples/$name", "docs/samples/$name").map(::File).first { it.exists() }
        val img = ImageIO.read(file)
        val px = IntArray(img.width * img.height)
        img.getRGB(0, 0, img.width, img.height, px, 0, img.width)
        return Image(img.width, img.height, px)
    }
}
