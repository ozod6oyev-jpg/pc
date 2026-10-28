package uz.autoaim.vision

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.awt.Color
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

class BallDetectorTest {

    private val detector = BallDetector()

    /** Platformaga botgan sharcha: pastki qismi qora tirqish bilan yopilgan — markaz baribir to'g'ri. */
    @Test
    fun occludedSyntheticBall() {
        for (hidden in listOf(0, 8, 16)) {
            // Haqiqiy nisbat: 1080 kenglikdagi ekranda 52 px sharcha.
            val img = BufferedImage(1080, 200, BufferedImage.TYPE_INT_RGB)
            val g = img.createGraphics()
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g.color = Color(15, 15, 17)
            g.fillRect(0, 0, 1080, 200)
            g.color = Color(125, 211, 181)
            g.fillOval(74, 70, 52, 52) // markaz (100, 96), radius 26
            g.color = Color(4, 4, 6)
            g.fillRect(40, 122 - hidden, 120, 40)
            g.dispose()
            val ball = detector.detect(img.toImage(), 0, 0, 1079, 199)
            assertNotNull(ball)
            assertEquals("x, yashirin $hidden px", 100f, ball!!.centerX, 1f)
            assertEquals("y, yashirin $hidden px", 96f, ball.centerY, 1f)
            assertEquals("r, yashirin $hidden px", 26f, ball.radius, 1f)
        }
    }

    /**
     * Telefondagi xato (04:58): ekran pastidagi overlay oynaning yashil ramkasi (#2E8F6E) sharcha nuri shartiga
     * mos keladi va sharchadan katta — ilgari "sharcha" deb olingan, zona topilmagan.
     */
    @Test
    fun greenOverlayFrameIsIgnored() {
        val img = ImageIO.read(sequenceOf("../docs/samples/real_01.png", "docs/samples/real_01.png").map(::File).first { it.exists() })
        val canvas = BufferedImage(img.width, img.height, BufferedImage.TYPE_INT_RGB)
        val g = canvas.createGraphics()
        g.drawImage(img, 0, 0, null)
        g.color = Color(46, 143, 110)
        g.stroke = java.awt.BasicStroke(3f)
        g.drawRoundRect(110, 1880, 860, 300, 54, 54)
        g.fillRoundRect(150, 2050, 780, 110, 40, 40) // yashil tugma ham
        g.dispose()
        val image = canvas.toImage()
        val ball = detector.detect(image, 0, 0, image.width - 1, image.height - 1)
        assertNotNull(ball)
        assertEquals(281.5f, ball!!.centerX, 0.5f)
        assertEquals(1366.5f, ball.centerY, 0.5f)
        val zone = GameZoneDetector().detect(image)
        assertNotNull("zona topilishi kerak", zone)
        assertNotNull(zone!!.slot)
    }

    @Test
    fun samples() {
        // ggg/jjg: nur 52×47 va 54×38 — pastki qismi platformada yashiringan.
        check("ggg.jpg", 281.5f, 963.5f, 26f)
        check("jjg.jpg", 266.5f, 1106.5f, 27f)
        check("real_01.png", 281.5f, 1366.5f, 26f)
    }

    private fun check(name: String, cx: Float, cy: Float, r: Float) {
        val image = load(name)
        val ball = detector.detect(image, 0, 0, image.width - 1, image.height - 1)
        assertNotNull(ball)
        println("$name: $ball")
        assertEquals("$name x", cx, ball!!.centerX, 0.5f)
        assertEquals("$name y", cy, ball.centerY, 0.5f)
        assertEquals("$name r", r, ball.radius, 0.5f)
    }

    private fun load(name: String): Image {
        val file = sequenceOf("../docs/samples/$name", "docs/samples/$name").map(::File).first { it.exists() }
        return ImageIO.read(file).toImage()
    }

    private fun BufferedImage.toImage(): Image {
        val px = IntArray(width * height)
        getRGB(0, 0, width, height, px, 0, width)
        return Image(width, height, px)
    }
}
