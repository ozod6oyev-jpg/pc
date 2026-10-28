package uz.autoaim.vision

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.awt.Color
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

/**
 * Namuna rasmlar (docs/samples) — foydalanuvchi o'yin zonasini kesib olgan. Haqiqiy ekranda zona
 * atrofida status bar, sarlavha, tugmalar bo'ladi — shuning uchun rasmlar soxta "to'liq ekran"
 * ichiga joylab ham tekshiriladi.
 */
class GameZoneDetectorTest {

    private val detector = GameZoneDetector()

    // Kesilgan rasmlardagi zona (qo'lda o'lchangan): chap/o'ng chiziq, tepa/past chegara.
    private val gggZone = intArrayOf(48, 20, 1032, 1381)
    private val jjgZone = intArrayOf(42, 20, 1036, 1572)

    @Test
    fun croppedGgg() = check(load("ggg.jpg"), gggZone)

    @Test
    fun croppedJjg() = check(load("jjg.jpg"), jjgZone)

    @Test
    fun fullScreenDarkHost() {
        val crop = load("ggg.jpg")
        val screen = fakeScreen(1080, 2400, Color(11, 12, 16), crop, offsetX = 0, offsetY = 420, scale = 1.0)
        check(screen, shift(gggZone, 0, 420, 1.0))
    }

    @Test
    fun fullScreenLightHost() {
        val crop = load("jjg.jpg")
        val screen = fakeScreen(1080, 2400, Color(242, 242, 245), crop, offsetX = 0, offsetY = 300, scale = 1.0)
        check(screen, shift(jjgZone, 0, 300, 1.0))
    }

    @Test
    fun fullScreenHalfScale() {
        // VirtualDisplay 1/2 masshtabda: 540×1200 kadr, zona ham kichraygan va gorizontal siljigan.
        val crop = load("ggg.jpg")
        val screen = fakeScreen(540, 1200, Color(20, 20, 24), crop, offsetX = 20, offsetY = 200, scale = 0.45)
        check(screen, shift(gggZone, 20, 200, 0.45), tolerance = 4)
    }

    @Test
    fun noGameOnScreen() {
        val screen = BufferedImage(1080, 2400, BufferedImage.TYPE_INT_RGB)
        val g = screen.createGraphics()
        g.color = Color(11, 12, 16)
        g.fillRect(0, 0, 1080, 2400)
        drawHostUi(g, 1080, 2400)
        g.dispose()
        val zone = detector.detect(screen.toImage())
        assertTrue("o'yinsiz ekranda ishonchli zona topilmasligi kerak", zone == null || zone.confidence < 1f)
    }

    @Test
    fun normalizeRoundTrip() {
        val zone = detector.detect(load("ggg.jpg").toImage())
        assertNotNull(zone)
        val (nx, ny) = zone!!.normalize(700f, 600f)
        val (x, y) = zone.denormalize(nx, ny)
        assertEquals(700f, x, 0.01f)
        assertEquals(600f, y, 0.01f)
    }

    @Test
    fun noGameTinyImage() {
        assertNull(detector.detect(Image(10, 10, IntArray(100))))
    }

    // ---------- yordamchilar ----------

    private fun check(source: BufferedImage, expected: IntArray, tolerance: Int = 6) {
        val zone = detector.detect(source.toImage())
        assertNotNull("zona topilmadi", zone)
        zone!!
        val actual = intArrayOf(zone.left, zone.top, zone.right, zone.bottom)
        println("zona ${actual.toList()} (kutilgan ${expected.toList()}), sharcha ${zone.ball}, quti ${zone.box}, slot ${zone.slot}")
        val names = listOf("left", "top", "right", "bottom")
        for (i in 0..3) {
            assertTrue(
                "${names[i]}: kutilgan ${expected[i]}, topilgan ${actual[i]}; zona ${actual.toList()}",
                kotlin.math.abs(actual[i] - expected[i]) <= tolerance,
            )
        }
        assertNotNull("sharcha topilmadi", zone.ball)
        assertNotNull("quti topilmadi", zone.box)
        assertNotNull("slot topilmadi", zone.slot)
        assertEquals(1f, zone.confidence, 0.001f)
    }

    private fun shift(zone: IntArray, dx: Int, dy: Int, scale: Double) =
        IntArray(4) { i -> ((zone[i] * scale) + if (i % 2 == 0) dx else dy).toInt() }

    private fun fakeScreen(
        w: Int, h: Int, background: Color, crop: BufferedImage,
        offsetX: Int, offsetY: Int, scale: Double,
    ): BufferedImage {
        val screen = BufferedImage(w, h, BufferedImage.TYPE_INT_RGB)
        val g = screen.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
        g.color = background
        g.fillRect(0, 0, w, h)
        drawHostUi(g, w, h)
        g.drawImage(crop, offsetX, offsetY, (crop.width * scale).toInt(), (crop.height * scale).toInt(), null)
        g.dispose()
        return screen
    }

    /** O'yinni o'rab turgan "ilova": status bar, sarlavha, tugmalar, pastki navigatsiya. */
    private fun drawHostUi(g: java.awt.Graphics2D, w: Int, h: Int) {
        val u = w / 1080.0
        fun r(x: Int, y: Int, rw: Int, rh: Int, c: Color) {
            g.color = c
            g.fillRect((x * u).toInt(), (y * u).toInt(), (rw * u).toInt(), (rh * u).toInt())
        }
        r(0, 0, 1080, 80, Color(0, 0, 0))
        r(40, 25, 90, 30, Color.WHITE)            // soat
        r(900, 25, 140, 30, Color.WHITE)          // batareya/signal
        r(40, 130, 60, 60, Color(230, 230, 230))  // orqaga tugmasi
        r(140, 140, 400, 45, Color(250, 250, 250)) // sarlavha matni
        r(40, 230, 1000, 120, Color(200, 205, 210)) // banner
        r(0, h - 160, 1080, 160, Color(28, 28, 32)) // pastki panel
        for (i in 0 until 4) r(90 + i * 260, h - 120, 80, 80, Color(220, 220, 225))
    }

    private fun load(name: String): BufferedImage {
        val file = sequenceOf("../docs/samples/$name", "docs/samples/$name").map(::File).first { it.exists() }
        return ImageIO.read(file)
    }

    private fun BufferedImage.toImage(): Image {
        val px = IntArray(width * height)
        getRGB(0, 0, width, height, px, 0, width)
        return Image(width, height, px)
    }
}
