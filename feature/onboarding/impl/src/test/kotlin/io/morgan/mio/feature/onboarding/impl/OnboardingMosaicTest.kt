package io.morgan.mio.feature.onboarding.impl

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.unit.dp
import io.morgan.mio.core.designsystem.theme.MioTheme
import io.morgan.mio.feature.onboarding.impl.component.OnboardingMosaic
import io.morgan.mio.feature.onboarding.impl.component.onboardingMosaicTileDelays
import io.morgan.mio.feature.onboarding.impl.component.onboardingMosaicTileDimensions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runner.RunWith
import org.junit.runners.model.Statement
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@Suppress("DEPRECATION")
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class OnboardingMosaicTest {
    private val activityRule = MosaicActivityRule()

    @get:Rule
    val composeRule = AndroidComposeTestRule(activityRule) { rule -> rule.activity }

    @Test
    fun normalMotionUsesTheSpecifiedTwoRowStagger() {
        assertEquals(
            listOf(0, 24, 48, 72, 96, 220, 244, 268, 292, 316),
            onboardingMosaicTileDelays(),
        )
    }

    @Test
    fun compactTilesScaleWidthAndHeightTogether() {
        val reference = onboardingMosaicTileDimensions(345.dp)
        val compact = onboardingMosaicTileDimensions(272.dp)

        assertTrue(compact.width < reference.width)
        assertTrue(compact.height < reference.height)
        assertEquals(reference.height.value / reference.width.value, compact.height.value / compact.width.value, 0.001f)
    }

    @Test
    fun normalMotionSettlesOnceAndDoesNotRestartOnRecomposition() {
        composeRule.mainClock.autoAdvance = false
        var settled = 0
        val recompositions = mutableIntStateOf(0)
        composeRule.setContent {
            recompositions.intValue
            MioTheme {
                OnboardingMosaic(
                    reducedMotion = false,
                    modifier = Modifier.fillMaxWidth(),
                    onMotionSettled = { settled += 1 },
                )
            }
        }

        composeRule.runOnIdle { assertEquals(0, settled) }
        composeRule.mainClock.advanceTimeBy(780)
        composeRule.runOnIdle { assertEquals(0, settled) }
        composeRule.mainClock.advanceTimeBy(100)
        composeRule.runOnIdle { assertEquals(1, settled) }

        composeRule.runOnIdle { recompositions.intValue += 1 }
        composeRule.mainClock.advanceTimeBy(1_000)
        composeRule.runOnIdle { assertEquals(1, settled) }
    }

    @Test
    fun reducedMotionUsesOneShortCrossfade() {
        composeRule.mainClock.autoAdvance = false
        var settled = 0
        composeRule.setContent {
            MioTheme {
                OnboardingMosaic(
                    reducedMotion = true,
                    onMotionSettled = { settled += 1 },
                )
            }
        }

        composeRule.onNodeWithContentDescription("Library import in progress").assertIsDisplayed()
        composeRule.mainClock.advanceTimeBy(160)
        composeRule.runOnIdle { assertEquals(0, settled) }
        composeRule.mainClock.advanceTimeBy(80)
        composeRule.runOnIdle { assertEquals(1, settled) }
    }

    @Test
    fun disposalCancelsMotionWithoutADeferredCompletion() {
        composeRule.mainClock.autoAdvance = false
        var settled = 0
        val visible = mutableStateOf(true)
        composeRule.setContent {
            MioTheme {
                if (visible.value) {
                    OnboardingMosaic(
                        reducedMotion = false,
                        onMotionSettled = { settled += 1 },
                    )
                }
            }
        }

        composeRule.mainClock.advanceTimeBy(100)
        composeRule.runOnIdle { visible.value = false }
        composeRule.mainClock.advanceTimeBy(1_500)
        composeRule.runOnIdle { assertEquals(0, settled) }
    }

    @Test
    fun reducedMotionInterruptionCancelsTheNormalFlight() {
        composeRule.mainClock.autoAdvance = false
        var settled = 0
        val reduced = mutableStateOf(false)
        composeRule.setContent {
            MioTheme {
                OnboardingMosaic(
                    reducedMotion = reduced.value,
                    onMotionSettled = { settled += 1 },
                )
            }
        }

        composeRule.mainClock.advanceTimeBy(100)
        composeRule.runOnIdle { reduced.value = true }
        composeRule.mainClock.advanceTimeBy(260)
        composeRule.runOnIdle { assertEquals(1, settled) }
        composeRule.mainClock.advanceTimeBy(1_500)
        composeRule.runOnIdle { assertEquals(1, settled) }
    }
}

@Suppress("DEPRECATION")
class MosaicActivityRule : TestRule {
    lateinit var activity: ComponentActivity

    override fun apply(
        base: Statement,
        description: Description,
    ): Statement =
        object : Statement() {
            override fun evaluate() {
                activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
                try {
                    base.evaluate()
                } finally {
                    activity.finish()
                }
            }
        }
}
