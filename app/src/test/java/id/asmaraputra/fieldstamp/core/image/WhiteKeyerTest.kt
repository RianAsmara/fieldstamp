package id.asmaraputra.fieldstamp.core.image

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WhiteKeyerTest {
    private val navy = 0xFF0E2A47.toInt()
    private val white = 0xFFFFFFFF.toInt()

    /** 100×120 frame: navy border 10 px, white window inside, a small white "text" block in the top border. */
    private fun frame(windowColor: Int = white): IntArray {
        val w = 100; val h = 120
        return IntArray(w * h) { i ->
            val x = i % w; val y = i / w
            when {
                x in 20..29 && y in 2..6 -> white            // white lettering inside the border
                x < 10 || x >= 90 || y < 10 || y >= 110 -> navy
                else -> windowColor
            }
        }
    }

    private fun alpha(c: Int) = c ushr 24

    @Test fun clearsTheLargestWhiteRegionOnly() {
        val result = WhiteKeyer.key(frame(), 100, 120)!!
        assertEquals(0, alpha(result.pixels[60 * 100 + 50]))      // window center
        assertEquals(255, alpha(result.pixels[5 * 100 + 5]))      // border
        assertEquals(255, alpha(result.pixels[4 * 100 + 25]))     // small white text stays opaque
        assertTrue(result.clearedFraction > 0.5f)
    }

    @Test fun toleratesJpegNoiseInTheWindow() {
        val noisy = frame().mapIndexed { i, c -> if (c == white && i % 7 == 0) 0xFFF2F4F1.toInt() else c }.toIntArray()
        val result = WhiteKeyer.key(noisy, 100, 120)!!
        assertEquals(0, alpha(result.pixels[60 * 100 + 49]))
    }

    @Test fun softensLightEdgePixelsButKeepsDarkOnes() {
        val px = frame()
        // A light-gray anti-aliased pixel next to the window, and a dark one.
        px[60 * 100 + 89] = 0xFFC8C8C8.toInt()
        val result = WhiteKeyer.key(px, 100, 120)!!
        val a = alpha(result.pixels[60 * 100 + 89])
        assertTrue("partial alpha expected, was $a", a in 1..254)
        assertEquals(255, alpha(result.pixels[60 * 100 + 9]))
    }

    @Test fun rejectsFramesWithoutALargeWhiteArea() {
        assertNull(WhiteKeyer.key(frame(windowColor = 0xFF888888.toInt()), 100, 120))
    }
}
