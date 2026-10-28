package uz.autoaim.aim

import org.junit.Assert.assertEquals
import org.junit.Test
import uz.autoaim.vision.DotDetector
import uz.autoaim.vision.GameZoneDetector
import uz.autoaim.vision.Image
import uz.autoaim.vision.TrajectoryFitter
import java.io.File
import javax.imageio.ImageIO

/**
 * Telefondan olingan asl skrinshotlar: to'liq zanjir (zona → sharcha → quti/slot → nuqtalar → yoy → GeometryGate).
 * Kutilgan natija — foydalanuvchi qanday holatni suratga olgani va rasmni ko'rib tekshirilgan.
 */
class RealSamplesTest {

    private val expected = mapOf(
        "real_01.png" to Verdict.HIT,                  // odatiy holat
        "real_02_tik.png" to Verdict.SHORT,            // deyarli tik — o'z joyiga qaytib tushadi
        "real_03_stock.png" to Verdict.HIT,            // odatiy holat
        "real_04_past_qaytish.png" to Verdict.BOTTOM,  // past: nuqtalar quti tagidan radiusdan yaqin o'tadi
        "real_05.png" to Verdict.OVER,                 // kuchli: qutidan oshib, devordan qaytadi
        "real_06_yon.png" to Verdict.SIDE_LEFT,        // qutining chap yoniga
        "real_07_chap_nishon.png" to Verdict.SIDE_RIGHT, // keyingi bosqich: sharcha o'ngdagi kartada, nishon chapda; past yoy
        "real_08_mojal_tushadi.png" to Verdict.HIT,      // bot o'zi olgan mo'ljal (barmoq bosilgan holda), +2.6 px
    )

    @Test
    fun allSamples() {
        val dir = sequenceOf("../docs/samples", "docs/samples").map(::File).first { it.exists() }
        for (f in dir.listFiles()!!.filter { it.name.startsWith("real_") }.sortedBy { it.name }) {
            val img = ImageIO.read(f)
            val px = IntArray(img.width * img.height)
            img.getRGB(0, 0, img.width, img.height, px, 0, img.width)
            val image = Image(img.width, img.height, px)
            val zone = GameZoneDetector().detect(image)!!
            val b = zone.ball!!
            val dots = DotDetector().detect(image, zone)
            val arc = TrajectoryFitter.fit(dots, b, zone.left + b.radius, zone.right - b.radius)
            val check = arc?.let { GeometryGate.check(it, b, zone.box!!, zone.slot!!, zone.bottom.toFloat()) }
            println(
                "${f.name}: zona ${zone.left},${zone.top},${zone.right},${zone.bottom} slot ${zone.slot?.left}-${zone.slot?.right}/${zone.slot?.top}" +
                    " sharcha (%.1f,%.1f) r%.1f | nuqtalar ${arc?.dots?.size}/${dots.size} vaqt ${arc?.times} qaytish ${arc?.bounces}".format(b.centerX, b.centerY, b.radius) +
                    (arc?.let { " vx %.2f vy %.2f g %.3f rms %.2f".format(it.vx, it.vy, it.g, it.rms) } ?: "") +
                    "\n   => $check"
            )
            assertEquals(f.name, expected[f.name], check?.verdict)
        }
    }
}
