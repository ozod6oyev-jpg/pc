package uz.autoaim.aim

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import uz.autoaim.vision.Ball
import uz.autoaim.vision.Blob
import uz.autoaim.vision.DotDetector
import uz.autoaim.vision.GameZoneDetector
import uz.autoaim.vision.Image
import uz.autoaim.vision.Trajectory
import uz.autoaim.vision.TrajectoryFitter
import java.io.File
import javax.imageio.ImageIO

class GeometryGateTest {

    // real_01 geometriyasi (1080×2340): zona 49..1030, sharcha (281.5, 1366.5) r 26, quti, slot.
    private val ball = Ball(281.5f, 1366.5f, 26f, Blob(256, 1341, 307, 1387, 0, 281.5f, 1364f))
    private val box = Blob(638, 911, 892, 1070, 0, 765f, 990f)
    private val slot = Blob(711, 915, 820, 924, 0, 765.5f, 919.5f)
    private val floor = 1784f
    private val g = 8.3f

    private fun arc(vx: Float, vy: Float, x0: Float = ball.centerX, y0: Float = ball.centerY) =
        Trajectory(emptyList(), listOf(0), x0, vx, y0, vy, g, 49f + 26f, 1030f - 26f, 0, 0f)

    private fun check(arc: Trajectory) = GeometryGate.check(arc, ball, box, slot, floor)

    @Test
    fun stockArcHits() {
        // real_01 dagi tiklangan yoy (vx 26.87, g 8.29) — slot markazidan +3 px.
        val result = sample("real_01.png")
        assertEquals(Verdict.HIT, result.verdict)
        assertEquals(3f, result.slotOffset!!, 3f)
    }

    @Test
    fun gggHits() = assertEquals(Verdict.HIT, sample("ggg.jpg").verdict)

    @Test
    fun jjgPassesUnder() = assertEquals(Verdict.UNDER, sample("jjg.jpg").verdict)

    @Test
    fun risingIntoLeftSide() {
        // Yoy qutining chap yoniga ko'tarilib boradi (03-39-58 holati).
        val r = check(arc(vx = 30f, vy = -75f))
        assertEquals(Verdict.SIDE_LEFT, r.verdict)
        assertTrue("urilish chap yon yonida: ${r.x}", r.x < box.left && r.x > box.left - 27)
    }

    @Test
    fun lowArcPassesUnder() = assertEquals(Verdict.UNDER, check(arc(vx = 40f, vy = -60f)).verdict)

    @Test
    fun grazingBottomHitsBottom() {
        // Quti tagiga juda yaqin: markaz tagdan pastda, lekin radius ichida.
        val r = check(arc(vx = 45f, vy = -72f))
        assertTrue("tag yoki chap pastki burchak: ${r.verdict}", r.verdict == Verdict.BOTTOM || r.verdict == Verdict.SIDE_LEFT)
    }

    @Test
    fun rimAndTopNearSlotEdges() {
        // Qutini chapga siljitib (= yoy o'ngga), slot chetlarini sinaymiz (tushish slotga nisbatan ≈ +3 + siljish).
        val stock = stockArc()
        fun shifted(dx: Float) = GeometryGate.check(stock, ball, box, slot, floor, boxShiftX = -dx)
            .also { println("siljish $dx: $it") }
        assertEquals(Verdict.HIT, shifted(20f).verdict)    // ≈ 788: slotga sig'adi (737..794)
        assertEquals(Verdict.RIM, shifted(45f).verdict)    // ≈ 813: o'ng qirraga tegadi
        assertEquals(Verdict.RIM, shifted(90f).verdict)    // ≈ 842 da ustga tegadi, chap yarmi teshik ustida — qirra
        assertEquals(Verdict.TOP, shifted(120f).verdict)   // slotdan ancha o'ngda, quti ustiga
        assertEquals(Verdict.RIM, shifted(-50f).verdict)   // ≈ 718: chap qirra
    }

    @Test
    fun overshootsBox() {
        // Baland va kuchli: qutining ustidan o'tib, o'ng tomonda tushadi (devorlar uzoqda — qaytishsiz).
        val wide = arc(vx = 50f, vy = -110f).copy(wallLeft = -10000f, wallRight = 10000f)
        assertEquals(Verdict.OVER, check(wide).verdict)
    }

    // ---------- yordamchilar ----------

    private fun stockArc(): Trajectory {
        val image = load("real_01.png")
        val zone = GameZoneDetector().detect(image)!!
        val b = zone.ball!!
        return TrajectoryFitter.fit(DotDetector().detect(image, zone), b, zone.left + b.radius, zone.right - b.radius)!!
    }

    private fun sample(name: String): ShotCheck {
        val image = load(name)
        val zone = GameZoneDetector().detect(image)!!
        val b = zone.ball!!
        val arc = TrajectoryFitter.fit(DotDetector().detect(image, zone), b, zone.left + b.radius, zone.right - b.radius)!!
        val result = GeometryGate.check(arc, b, zone.box!!, zone.slot!!, zone.bottom.toFloat())
        println("$name: $result")
        return result
    }

    private fun load(name: String): Image {
        val file = sequenceOf("../docs/samples/$name", "docs/samples/$name").map(::File).first { it.exists() }
        val img = ImageIO.read(file)
        val px = IntArray(img.width * img.height)
        img.getRGB(0, 0, img.width, img.height, px, 0, img.width)
        return Image(img.width, img.height, px)
    }
}
