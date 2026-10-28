package uz.autoaim.shizuku

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InputZoneTest {

    // ggg.jpg: sharcha glow y≈938–984 → markaz ≈961, radius ≈23
    private val zone = InputZone.fromBall(ballCenterY = 961f, ballRadius = 23f, paddingPx = 40f)

    @Test
    fun lineIsBelowBall() {
        assertEquals(1024f, zone.lineY, 0.001f)
    }

    @Test
    fun aboveLineIsAim() {
        assertTrue(zone.contains(500f, 800f))
        assertTrue(zone.contains(500f, 1023.9f))
    }

    @Test
    fun onOrBelowLineIsIgnored() {
        assertFalse(zone.contains(500f, 1024f))
        assertFalse(zone.contains(500f, 1300f))
    }

    @Test
    fun clampKeepsMoveAboveLine() {
        assertEquals(700f, zone.clampY(700f), 0.001f)
        assertEquals(1023f, zone.clampY(1200f), 0.001f)
        assertTrue(zone.contains(0f, zone.clampY(5000f)))
    }
}
