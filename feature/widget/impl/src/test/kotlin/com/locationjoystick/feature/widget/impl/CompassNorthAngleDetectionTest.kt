package com.locationjoystick.feature.widget.impl

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.math.abs

/**
 * Regression coverage for [CompassAccessibilityService.detectNorthAngle] against real AR/GPS game
 * screenshots (captured on-device, camera rotated via swipe between shots — see
 * docs/features/tap-to-walk.md, "Compass Orientation"). A synthetic red square can't stand in for
 * this: the blob-shape/PCA/skew logic only proves itself against the actual needle icon rendered
 * by the game, at three distinct headings.
 *
 * Expected angles are the algorithm's own output at capture time (golden values) — this guards
 * against future regressions changing detection behavior, not against a hand-computed ground
 * truth heading.
 *
 * Robolectric's default SDK shadow predates Bitmap.Config.HARDWARE (added API 26) — pin to a
 * modern API so the shadow bitmap actually has the field detectNorthAngle checks for.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class CompassNorthAngleDetectionTest {
    private fun loadFixture(name: String): Bitmap {
        val stream =
            checkNotNull(javaClass.classLoader?.getResourceAsStream("compass/$name")) {
                "Missing test fixture compass/$name"
            }
        return checkNotNull(BitmapFactory.decodeStream(stream)) { "Failed to decode compass/$name" }
    }

    @Test
    fun orientationA_matchesCapturedAngle() {
        val angle = CompassAccessibilityService.detectNorthAngle(loadFixture("pogo_orientation_a.png"))
        assertEquals(-12.5, Math.toDegrees(checkNotNull(angle).toDouble()), 2.0)
    }

    @Test
    fun orientationB_matchesCapturedAngle() {
        val angle = CompassAccessibilityService.detectNorthAngle(loadFixture("pogo_orientation_b.png"))
        assertEquals(-115.2, Math.toDegrees(checkNotNull(angle).toDouble()), 2.0)
    }

    @Test
    fun orientationC_matchesCapturedAngle() {
        val angle = CompassAccessibilityService.detectNorthAngle(loadFixture("pogo_orientation_c.png"))
        assertEquals(123.5, Math.toDegrees(checkNotNull(angle).toDouble()), 2.0)
    }

    @Test
    fun distinctOrientations_produceDistinctAngles() {
        val a = CompassAccessibilityService.detectNorthAngle(loadFixture("pogo_orientation_a.png"))
        val b = CompassAccessibilityService.detectNorthAngle(loadFixture("pogo_orientation_b.png"))
        val c = CompassAccessibilityService.detectNorthAngle(loadFixture("pogo_orientation_c.png"))

        // Same needle, three different camera headings — detection must tell them apart, not
        // collapse to one direction regardless of input (the failure mode a constant-return bug
        // would produce).
        assert(abs(checkNotNull(a) - checkNotNull(b)) > 0.1f)
        assert(abs(a - checkNotNull(c)) > 0.1f)
        assert(abs(b - c) > 0.1f)
    }

    @Test
    fun noRedPixels_returnsNull() {
        val blank = Bitmap.createBitmap(200, 400, Bitmap.Config.ARGB_8888)
        blank.eraseColor(Color.BLACK)
        assertNull(CompassAccessibilityService.detectNorthAngle(blank))
    }
}
