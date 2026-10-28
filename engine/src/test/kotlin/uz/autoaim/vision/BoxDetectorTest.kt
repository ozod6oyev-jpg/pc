package uz.autoaim.vision

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.abs

/**
 * Quti va slot — namuna rasmlarda qo'lda o'lchangan qiymatlar bilan solishtiriladi.
 * Slot — nishon, shuning uchun uning chegaralari 2 px aniqlikda talab qilinadi.
 */
class BoxDetectorTest {

    private val zoneDetector = GameZoneDetector()

    @Test
    fun ggg() = check("ggg.jpg", box = intArrayOf(638, 508, 892, 667), slot = intArrayOf(711, 512, 820, 521))

    @Test
    fun jjg() = check("jjg.jpg", box = intArrayOf(640, 635, 897, 797), slot = intArrayOf(713, 639, 824, 648))

    private fun check(name: String, box: IntArray, slot: IntArray) {
        val zone = zoneDetector.detect(load(name))
        assertNotNull("zona topilmadi", zone)
        val body = zone!!.box
        val hole = zone.slot
        println("$name: quti $body\n  slot $hole")
        assertNotNull("quti topilmadi", body)
        assertNotNull("slot topilmadi", hole)
        near("quti", intArrayOf(body!!.left, body.top, body.right, body.bottom), box, 3)
        near("slot", intArrayOf(hole!!.left, hole.top, hole.right, hole.bottom), slot, 2)
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
