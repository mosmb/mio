package io.morgan.mio.core.ui.progress

import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import io.morgan.mio.core.designsystem.theme.MioTheme
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
class MioProgressValueTest {
    private val activityRule = ActivityRule()

    @get:Rule
    val composeRule: AndroidComposeTestRule<ActivityRule, *> =
        AndroidComposeTestRule(activityRule) { rule -> rule.activity }

    @Test
    fun committedValueIsImmediatelyAccessibleAndKnownTotalsExposeOneBar() {
        composeRule.setContent {
            MioTheme {
                MioProgressValue(
                    value = 8,
                    total = 12,
                    description = "Episode 8 of 12",
                    modifier = Modifier.testTag("known"),
                )
            }
        }

        composeRule.onNodeWithContentDescription("Episode 8 of 12").assertIsDisplayed()
        composeRule.onAllNodesWithTag("known", useUnmergedTree = true).assertCountEquals(1)
        composeRule
            .onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo))
            .assertCountEquals(1)
    }

    @Test
    fun unknownTotalsOmitTheProgressBarWithoutOmittingTheDescription() {
        composeRule.setContent {
            MioTheme {
                MioProgressValue(
                    value = 41,
                    total = null,
                    description = "Chapter 41 with unknown total",
                )
            }
        }

        composeRule.onNodeWithContentDescription("Chapter 41 with unknown total").assertIsDisplayed()
        composeRule
            .onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo))
            .assertCountEquals(0)
    }

    @Test
    fun incrementTravelsUpAndSemanticsAlreadyExposeTheCommittedTarget() {
        val value = mutableIntStateOf(8)
        composeRule.setContent {
            MioTheme { MioProgressValue(value.intValue, 12, "Episode ${value.intValue} of 12") }
        }
        composeRule.mainClock.autoAdvance = false
        val initialTop =
            composeRule
                .onNodeWithText(
                    "Episode 8 of 12",
                    useUnmergedTree = true,
                ).fetchSemanticsNode()
                .positionInRoot.y
        composeRule.runOnIdle {
            value.intValue = 9
            androidx.compose.runtime.snapshots.Snapshot
                .sendApplyNotifications()
        }
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.mainClock.advanceTimeBy(64)
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription("Episode 9 of 12").assertExists()
        val oldTop =
            composeRule
                .onNodeWithText(
                    "Episode 8 of 12",
                    useUnmergedTree = true,
                ).fetchSemanticsNode()
                .positionInRoot.y
        val newTop =
            composeRule
                .onNodeWithText(
                    "Episode 9 of 12",
                    useUnmergedTree = true,
                ).fetchSemanticsNode()
                .positionInRoot.y
        assertTrue("Outgoing number must travel upward", oldTop < initialTop)
        assertTrue("Incoming number must arrive from below", newTop > initialTop)
        val semanticProgress =
            composeRule
                .onNodeWithContentDescription(
                    "Episode 9 of 12",
                ).fetchSemanticsNode()
                .config[SemanticsProperties.ProgressBarRangeInfo]
        assertEquals(0.75f, semanticProgress.current, 0.001f)
        composeRule.mainClock.advanceTimeBy(240)
        composeRule.waitForIdle()
        assertEquals(
            initialTop,
            composeRule
                .onNodeWithText("Episode 9 of 12", useUnmergedTree = true)
                .fetchSemanticsNode()
                .positionInRoot.y,
            0.5f,
        )
    }

    @Test
    fun decrementTravelsDownAndSemanticsAlreadyExposeTheCommittedTarget() {
        val value = mutableIntStateOf(8)
        composeRule.setContent {
            MioTheme { MioProgressValue(value.intValue, 12, "Episode ${value.intValue} of 12") }
        }
        composeRule.mainClock.autoAdvance = false
        val initialTop =
            composeRule
                .onNodeWithText(
                    "Episode 8 of 12",
                    useUnmergedTree = true,
                ).fetchSemanticsNode()
                .positionInRoot.y
        composeRule.runOnIdle {
            value.intValue = 7
            androidx.compose.runtime.snapshots.Snapshot
                .sendApplyNotifications()
        }
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.mainClock.advanceTimeBy(64)
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription("Episode 7 of 12").assertExists()
        val oldTop =
            composeRule
                .onNodeWithText(
                    "Episode 8 of 12",
                    useUnmergedTree = true,
                ).fetchSemanticsNode()
                .positionInRoot.y
        val newTop =
            composeRule
                .onNodeWithText(
                    "Episode 7 of 12",
                    useUnmergedTree = true,
                ).fetchSemanticsNode()
                .positionInRoot.y
        assertTrue("Outgoing number must travel downward", oldTop > initialTop)
        assertTrue("Incoming number must arrive from above", newTop < initialTop)
        val semanticProgress =
            composeRule
                .onNodeWithContentDescription(
                    "Episode 7 of 12",
                ).fetchSemanticsNode()
                .config[SemanticsProperties.ProgressBarRangeInfo]
        assertEquals(7f / 12f, semanticProgress.current, 0.001f)
        composeRule.mainClock.advanceTimeBy(240)
        composeRule.waitForIdle()
        assertEquals(
            initialTop,
            composeRule
                .onNodeWithText("Episode 7 of 12", useUnmergedTree = true)
                .fetchSemanticsNode()
                .positionInRoot.y,
            0.5f,
        )
    }

    @Test
    fun reducedMotionDoesNotRollAndInterruptedChangesSettleOnLatestValue() {
        val value = mutableIntStateOf(8)
        composeRule.setContent {
            MioTheme(reducedMotionOverride = true) {
                MioProgressValue(value.intValue, 12, "Episode ${value.intValue} of 12")
            }
        }
        composeRule.mainClock.autoAdvance = false
        val initialTop =
            composeRule
                .onNodeWithText(
                    "Episode 8 of 12",
                    useUnmergedTree = true,
                ).fetchSemanticsNode()
                .positionInRoot.y

        fun change(next: Int) {
            composeRule.runOnIdle {
                value.intValue = next
                androidx.compose.runtime.snapshots.Snapshot
                    .sendApplyNotifications()
            }
            composeRule.mainClock.advanceTimeByFrame()
            composeRule.mainClock.advanceTimeBy(32)
        }
        change(9)
        assertEquals(
            initialTop,
            composeRule
                .onNodeWithText("Episode 9 of 12", useUnmergedTree = true)
                .fetchSemanticsNode()
                .positionInRoot.y,
            0.5f,
        )
        change(10)
        change(9)
        composeRule.onNodeWithContentDescription("Episode 9 of 12").assertExists()
        composeRule.mainClock.advanceTimeBy(176)
        composeRule.onNodeWithText("Episode 9 of 12", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithText("Episode 10 of 12", useUnmergedTree = true).assertDoesNotExist()
        assertEquals(
            0.75f,
            composeRule
                .onNodeWithContentDescription(
                    "Episode 9 of 12",
                ).fetchSemanticsNode()
                .config[SemanticsProperties.ProgressBarRangeInfo]
                .current,
            0.001f,
        )
    }

    @Test
    fun rapidAnimatedChangesSettleOnTheLatestCommittedValue() {
        val value = mutableIntStateOf(8)
        composeRule.setContent {
            MioTheme {
                MioProgressValue(value.intValue, 12, "Episode ${value.intValue} of 12")
            }
        }
        composeRule.mainClock.autoAdvance = false

        fun change(next: Int) {
            composeRule.runOnIdle {
                value.intValue = next
                androidx.compose.runtime.snapshots.Snapshot
                    .sendApplyNotifications()
            }
            composeRule.mainClock.advanceTimeByFrame()
            composeRule.mainClock.advanceTimeBy(32)
        }
        change(9)
        change(10)
        change(9)
        composeRule.onNodeWithContentDescription("Episode 9 of 12").assertExists()
        composeRule.mainClock.advanceTimeBy(176)
        composeRule.onNodeWithText("Episode 9 of 12", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithText("Episode 10 of 12", useUnmergedTree = true).assertDoesNotExist()
        assertEquals(
            0.75f,
            composeRule
                .onNodeWithContentDescription(
                    "Episode 9 of 12",
                ).fetchSemanticsNode()
                .config[SemanticsProperties.ProgressBarRangeInfo]
                .current,
            0.001f,
        )
    }

    class ActivityRule : TestRule {
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
}
