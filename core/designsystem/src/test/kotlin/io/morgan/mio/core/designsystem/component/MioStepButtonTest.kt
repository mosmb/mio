package io.morgan.mio.core.designsystem.component

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import io.morgan.mio.core.designsystem.icon.MioIcons
import io.morgan.mio.core.designsystem.testing.DesignSystemActivityRule
import io.morgan.mio.core.designsystem.testing.composeRule
import io.morgan.mio.core.designsystem.theme.MioTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MioStepButtonTest {
    private val activityRule = DesignSystemActivityRule()

    @get:Rule
    val composeRule: AndroidComposeTestRule<DesignSystemActivityRule, *> = activityRule.composeRule()

    @Test
    fun buttonsHaveTheFixedTouchTargetAndExposeTheirActionDescriptions() {
        composeRule.setContent {
            MioTheme {
                Column {
                    MioStepButton(
                        MioIcons.Remove,
                        "Decrease episode to 7",
                        enabled = true,
                        accent = false,
                        onClick = {},
                    )
                    MioStepButton(MioIcons.Add, "Increase episode to 9", enabled = true, accent = true, onClick = {})
                }
            }
        }

        listOf("Decrease episode to 7", "Increase episode to 9").forEach { description ->
            val node = composeRule.onNodeWithContentDescription(description)
            node.assertIsDisplayed().assertHasClickAction()
            assertEquals(
                composeRule.density.run { 52.dp.toPx() },
                node.fetchSemanticsNode().boundsInRoot.width,
                0.5f,
            )
            assertEquals(
                composeRule.density.run { 52.dp.toPx() },
                node.fetchSemanticsNode().boundsInRoot.height,
                0.5f,
            )
        }
    }

    @Test
    fun disabledMinusRemainsVisibleAndCannotCommit() {
        var clicks = 0
        composeRule.setContent {
            MioTheme {
                MioStepButton(
                    iconRes = MioIcons.Remove,
                    contentDescription = "Decrease episode to 0",
                    enabled = false,
                    accent = false,
                    onClick = { clicks++ },
                )
            }
        }

        composeRule.onNodeWithContentDescription("Decrease episode to 0").assertIsNotEnabled().performClick()
        composeRule.runOnIdle { assertEquals(0, clicks) }
    }

    @Test
    fun leavingCancelsAndReenteringBeforeReleaseCommitsExactlyOnce() {
        var clicks = 0
        composeRule.setContent {
            MioTheme {
                MioStepButton(
                    iconRes = MioIcons.Add,
                    contentDescription = "Increase episode to 9",
                    enabled = true,
                    accent = true,
                    onClick = { clicks++ },
                )
            }
        }
        val node = composeRule.onNodeWithContentDescription("Increase episode to 9")

        node.performTouchInput {
            down(center)
            moveTo(center + Offset(500f, 0f))
            up()
        }
        composeRule.waitForIdle()
        assertEquals(0, clicks)

        node.performTouchInput {
            down(center)
            moveTo(center + Offset(500f, 0f))
            moveTo(center)
            up()
        }
        composeRule.waitForIdle()
        assertEquals(1, clicks)
    }

    @Test
    fun recompositionUsesLatestCallbackAndDisableOrDisposeCancelsPendingPress() {
        val enabled = mutableStateOf(true)
        val visible = mutableStateOf(true)
        val replacement = mutableStateOf(false)
        var originalClicks = 0
        var replacementClicks = 0
        composeRule.setContent {
            MioTheme {
                if (visible.value) {
                    MioStepButton(
                        iconRes = MioIcons.Add,
                        contentDescription = "Step",
                        enabled = enabled.value,
                        accent = true,
                        onClick = if (replacement.value) ({ replacementClicks++ }) else ({ originalClicks++ }),
                    )
                }
            }
        }
        val step = composeRule.onNodeWithContentDescription("Step")
        step.performTouchInput { down(center) }
        composeRule.runOnIdle { replacement.value = true }
        step.performTouchInput { up() }
        composeRule.runOnIdle {
            assertEquals(0, originalClicks)
            assertEquals(1, replacementClicks)
        }
        step.performTouchInput { down(center) }
        composeRule.runOnIdle { enabled.value = false }
        step.performTouchInput { up() }
        composeRule.runOnIdle {
            assertEquals(1, replacementClicks)
            enabled.value = true
        }
        step.performTouchInput { down(center) }
        composeRule.runOnIdle { visible.value = false }
        composeRule.onRoot().performTouchInput { up() }
        composeRule.runOnIdle { assertEquals(1, replacementClicks) }
    }
}
