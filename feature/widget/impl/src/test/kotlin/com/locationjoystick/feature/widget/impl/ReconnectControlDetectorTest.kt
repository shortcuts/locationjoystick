package com.locationjoystick.feature.widget.impl

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.abs

/** Real night-map screenshots, 864x1920, control at about (791, 240) in both states. */
class ReconnectControlDetectorTest {
    private class Shot(
        val pixels: IntArray,
        val width: Int,
        val height: Int,
    )

    private fun load(path: String): Shot {
        val img = ImageIO.read(checkNotNull(javaClass.classLoader.getResourceAsStream(path)) { "Missing fixture $path" })
        val px = IntArray(img.width * img.height)
        img.getRGB(0, 0, img.width, img.height, px, 0, img.width)
        return Shot(px, img.width, img.height)
    }

    private fun detect(path: String): ReconnectControl? = load(path).let { ReconnectControlDetector.detect(it.pixels, it.width, it.height) }

    private fun assertNearControl(c: ReconnectControl) {
        assertTrue("x=${c.centerX}", abs(c.centerX - 791) <= 6)
        assertTrue("y=${c.centerY}", abs(c.centerY - 240) <= 6)
    }

    @Test
    fun connected_readsOn() {
        val c = checkNotNull(detect("reconnect/hud_on_night.png"))
        assertEquals(ReconnectState.ON, c.state)
        assertNearControl(c)
    }

    @Test
    fun disconnected_readsOff() {
        val c = checkNotNull(detect("reconnect/hud_off_night.png"))
        assertEquals(ReconnectState.OFF, c.state)
        assertNearControl(c)
    }

    @Test
    fun `detect on a cropped region with offset returns the same control as the full frame`() {
        for (path in listOf("reconnect/hud_on_night.png", "reconnect/hud_off_night.png")) {
            val s = load(path)
            val x0 = ReconnectControlDetector.searchX0(s.width)
            val y1 = ReconnectControlDetector.searchY1(s.height)
            val stride = s.width - x0
            val cropped = IntArray(stride * y1) { s.pixels[(it / stride) * s.width + x0 + it % stride] }
            val full = checkNotNull(ReconnectControlDetector.detect(s.pixels, s.width, s.height))
            val crop = checkNotNull(ReconnectControlDetector.detect(cropped, s.width, s.height, cropX = x0))
            assertEquals(path, full, crop)
        }
    }

    private fun mapped(
        path: String,
        f: (Int, Int, Int) -> Triple<Int, Int, Int>,
    ): Shot {
        val s = load(path)
        val px =
            IntArray(s.pixels.size) {
                val p = s.pixels[it]
                val (r, g, b) = f((p shr 16) and 0xFF, (p shr 8) and 0xFF, p and 0xFF)
                (0xFF shl 24) or (r.coerceIn(0, 255) shl 16) or (g.coerceIn(0, 255) shl 8) or b.coerceIn(0, 255)
            }
        return Shot(px, s.width, s.height)
    }

    private fun Shot.detect() = ReconnectControlDetector.detect(pixels, width, height)

    private fun dim(path: String) = mapped(path) { r, g, b -> Triple(r * 6 / 10, g * 6 / 10, b * 6 / 10) }

    private fun tint(path: String) = mapped(path) { r, g, b -> Triple(r * 9 / 10, g, b * 8 / 10) }

    private fun scaled(
        path: String,
        newWidth: Int,
        newHeight: Int,
    ): Shot {
        val s = load(path)
        val src = BufferedImage(s.width, s.height, BufferedImage.TYPE_INT_ARGB)
        src.setRGB(0, 0, s.width, s.height, s.pixels, 0, s.width)
        val dst = BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_ARGB)
        val g = dst.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
        g.drawImage(src, 0, 0, newWidth, newHeight, null)
        g.dispose()
        val px = IntArray(newWidth * newHeight)
        dst.getRGB(0, 0, newWidth, newHeight, px, 0, newWidth)
        return Shot(px, newWidth, newHeight)
    }

    private fun assertRead(
        c: ReconnectControl?,
        state: ReconnectState,
        scale: Float = 1f,
        tolPx: Float = 6f,
    ) {
        val got = checkNotNull(c)
        assertEquals(state, got.state)
        assertTrue("x=${got.centerX}", abs(got.centerX - 791 * scale) <= tolPx)
        assertTrue("y=${got.centerY}", abs(got.centerY - 240 * scale) <= tolPx)
    }

    @Test
    fun dimmedOn_stillReadsOn() = assertRead(dim("reconnect/hud_on_night.png").detect(), ReconnectState.ON)

    @Test
    fun dimmedOff_stillReadsOff() = assertRead(dim("reconnect/hud_off_night.png").detect(), ReconnectState.OFF)

    @Test
    fun tintedOn_stillReadsOn() = assertRead(tint("reconnect/hud_on_night.png").detect(), ReconnectState.ON)

    @Test
    fun tintedOff_stillReadsOff() = assertRead(tint("reconnect/hud_off_night.png").detect(), ReconnectState.OFF)

    @Test
    fun scaledTo1080x2400_readsBothStates() {
        val k = 1080f / 864f
        assertRead(scaled("reconnect/hud_on_night.png", 1080, 2400).detect(), ReconnectState.ON, k, 1f * k + 6)
        assertRead(scaled("reconnect/hud_off_night.png", 1080, 2400).detect(), ReconnectState.OFF, k, 1f * k + 6)
    }

    @Test
    fun scaledTo691x1536_readsBothStates() {
        val k = 691f / 864f
        assertRead(scaled("reconnect/hud_on_night.png", 691, 1536).detect(), ReconnectState.ON, k, 8f)
        assertRead(scaled("reconnect/hud_off_night.png", 691, 1536).detect(), ReconnectState.OFF, k, 8f)
    }

    @Test
    fun grayscaleBall_returnsNull() {
        val gray = mapped("reconnect/hud_on_night.png") { r, g, b -> ((r + g + b) / 3).let { Triple(it, it, it) } }
        assertNull(gray.detect())
    }

    @Test
    fun screenWithoutControl_returnsNull() {
        assertNull(detect("reconnect/screen_without_control.png"))
    }

    @Test
    fun blank_returnsNull() {
        assertNull(ReconnectControlDetector.detect(IntArray(200 * 400), 200, 400))
    }

    /**
     * Device corpus from scripts/reconnect_matrix.py corpus. Runs only when RECONNECT_FIXTURE_DIR is set.
     * Filenames are `<on|off>_<day|night>_<min|max>_<device>.png`; the prefix is the expected state.
     */
    @Test
    fun deviceCorpus_classifiesEveryFileAsItsLabel() {
        val dir = System.getenv("RECONNECT_FIXTURE_DIR")
        assumeTrue("RECONNECT_FIXTURE_DIR not set", !dir.isNullOrBlank())
        val files = File(dir).listFiles { f -> f.name.endsWith(".png") }.orEmpty().sortedBy { it.name }
        assumeTrue("no .png files in $dir", files.isNotEmpty())
        val misclassified =
            files.mapNotNull { f ->
                val expected = if (f.name.startsWith("on_")) ReconnectState.ON else ReconnectState.OFF
                val img = ImageIO.read(f)
                val px = IntArray(img.width * img.height)
                img.getRGB(0, 0, img.width, img.height, px, 0, img.width)
                val got = ReconnectControlDetector.detect(px, img.width, img.height)?.state
                if (got == expected) null else "${f.name}: expected $expected, got $got"
            }
        assertTrue("misclassified: $misclassified", misclassified.isEmpty())
    }
}
