package io.morgan.mio.core.designsystem.accessibility

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MioAccessibilityTest {
    @Test
    fun onlyZeroAnimatorScaleEnablesReducedMotion() {
        assertTrue(isReducedMotionScale(0f))
        assertFalse(isReducedMotionScale(1f))
        assertFalse(isReducedMotionScale(0.5f))
        assertFalse(isReducedMotionScale(-1f))
        assertFalse(isReducedMotionScale(Float.NaN))
    }

    @Test
    fun spatialMotionIsTheInverseOfReducedMotion() {
        val ordinary =
            MioAccessibility(
                highContrastEnabled = false,
                reducedMotionEnabled = false,
                hapticsEnabled = true,
            )
        assertTrue(ordinary.spatialMotionEnabled)
        assertFalse(ordinary.copy(reducedMotionEnabled = true).spatialMotionEnabled)
    }
}
