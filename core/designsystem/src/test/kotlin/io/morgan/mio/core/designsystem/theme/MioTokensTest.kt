package io.morgan.mio.core.designsystem.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import kotlin.math.PI

class MioTokensTest {
    @Test
    fun spacingMatchesTheCanonicalScale() {
        val spacing = MioSpacing.Default
        assertEquals(4.dp, spacing.space1)
        assertEquals(8.dp, spacing.space2)
        assertEquals(12.dp, spacing.space3)
        assertEquals(16.dp, spacing.space4)
        assertEquals(20.dp, spacing.space5)
        assertEquals(24.dp, spacing.space6)
        assertEquals(32.dp, spacing.space8)
        assertEquals(48.dp, spacing.space12)
        assertEquals(spacing.space4, spacing.screenGutter)
        assertEquals(spacing.space6, spacing.rootTopContentPadding)
        assertEquals(spacing.space6, spacing.unrelatedSectionMinimum)
        assertEquals(28.dp, spacing.rootBottomClearance)
    }

    @Test
    fun shapesAndElevationExposeOnlyTheDocumentedRoles() {
        val shapes = MioShapes.Default
        assertEquals(RoundedCornerShape(10.dp), shapes.xs)
        assertEquals(RoundedCornerShape(12.dp), shapes.sm)
        assertEquals(RoundedCornerShape(16.dp), shapes.md)
        assertEquals(RoundedCornerShape(24.dp), shapes.lg)
        assertEquals(RoundedCornerShape(22.dp), shapes.nav)
        assertEquals(RoundedCornerShape(999.dp), shapes.full)
        assertEquals(shapes.xs, shapes.materialShapes.extraSmall)
        assertEquals(shapes.sm, shapes.materialShapes.small)
        assertEquals(shapes.md, shapes.materialShapes.medium)
        assertEquals(shapes.lg, shapes.materialShapes.large)
        assertEquals(shapes.lg, shapes.materialShapes.extraLarge)
        assertSame(shapes.materialShapes, shapes.materialShapes)

        val elevation = MioElevation.Default
        assertEquals(0.dp, elevation.flat)
        assertEquals(4.dp, elevation.artwork)
        assertEquals(6.dp, elevation.floatingLow)
        assertEquals(8.dp, elevation.floatingHigh)
    }

    @Test
    fun motionValuesAndSpringStiffnessMatchTheContract() {
        val motion = MioMotion.Default
        assertEquals(80, motion.pressDownMillis)
        assertEquals(0.96f, motion.pressScale, 0f)
        assertEquals(0.28f, motion.pressRelease.responseSeconds, 0f)
        assertEquals(1.0f, motion.pressRelease.dampingRatio, 0f)
        assertEquals(0.30f, motion.rootTabSelection.responseSeconds, 0f)
        assertEquals(0.9f, motion.rootTabSelection.dampingRatio, 0f)
        assertEquals(120, motion.rootTabIconCrossfadeMillis)
        assertEquals(220, motion.nestedNavigationMillis)
        assertEquals(16.dp, motion.nestedNavigationOffset)
        assertEquals(0.30f, motion.bottomSheet.responseSeconds, 0f)
        assertEquals(0.8f, motion.bottomSheet.dampingRatio, 0f)
        assertEquals(180, motion.scrimMillis)
        assertEquals(160, motion.smallStateCrossfadeMillis)
        assertEquals(180, motion.themeCrossfadeMillis)
        assertEquals(160, motion.reducedMotionCrossfadeMillis)

        val token = motion.pressRelease
        val expectedStiffness =
            ((2f * PI.toFloat()) / token.responseSeconds) *
                ((2f * PI.toFloat()) / token.responseSeconds)
        assertEquals(expectedStiffness, token.asSpringSpec().stiffness, 0.1f)
    }
}
