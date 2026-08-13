package io.morgan.mio.core.designsystem.component

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import io.morgan.mio.core.designsystem.icon.MioIcons
import io.morgan.mio.core.designsystem.testing.DesignSystemActivityRule
import io.morgan.mio.core.designsystem.testing.composeRule
import io.morgan.mio.core.designsystem.theme.MioTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MioButtonTest {
    private val activityRule = DesignSystemActivityRule()

    @get:Rule
    val composeRule: AndroidComposeTestRule<DesignSystemActivityRule, *> = activityRule.composeRule()

    @Test
    fun primaryAndSecondaryButtonsKeepLabelsAndMinimumHeight() {
        composeRule.setContent {
            MioTheme {
                Column {
                    MioButton("Primary", onClick = {})
                    MioButton("Secondary", onClick = {}, style = MioButtonStyle.SECONDARY)
                }
            }
        }

        composeRule.onNodeWithText("Primary").assertIsDisplayed().assertHasClickAction()
        composeRule.onNodeWithText("Secondary").assertIsDisplayed()
        assertTrue(
            composeRule
                .onNodeWithText("Primary")
                .fetchSemanticsNode()
                .boundsInRoot.height >=
                composeRule.density.run { 52.dp.toPx() },
        )
    }

    @Test
    fun disabledButtonsDoNotCommitAndLeadingIconIsDecorative() {
        var clicks = 0
        composeRule.setContent {
            MioTheme {
                MioButton(
                    text = "Add",
                    onClick = { clicks++ },
                    enabled = false,
                    leadingIconRes = MioIcons.Add,
                )
            }
        }

        composeRule.onNodeWithText("Add").assertIsNotEnabled().performClick()
        composeRule.onNodeWithContentDescription("Add").assertDoesNotExist()
        composeRule.runOnIdle { assertEquals(0, clicks) }
    }

    @Test
    fun releaseCommitsOnceButDraggingOutsideCancels() {
        var clicks = 0
        composeRule.setContent {
            MioTheme { MioButton("Commit", onClick = { clicks++ }) }
        }
        val node = composeRule.onNodeWithText("Commit")

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
        node.performClick()
        composeRule.waitForIdle()
        assertEquals(2, clicks)
    }

    @Test
    fun reducedMotionDoesNotScaleTheSemanticBounds() {
        val bounds = mutableStateOf<androidx.compose.ui.geometry.Rect?>(null)
        composeRule.setContent {
            MioTheme(reducedMotionOverride = true) {
                MioButton("Stable", onClick = {})
            }
        }
        val node = composeRule.onNodeWithText("Stable")
        bounds.value = node.fetchSemanticsNode().boundsInRoot
        node.performTouchInput {
            down(center)
            moveTo(center)
        }
        assertEquals(bounds.value, node.fetchSemanticsNode().boundsInRoot)
    }

    @Test
    fun iconAndIconButtonKeepTheirExactVisualAndSemanticSizes() {
        composeRule.setContent {
            MioTheme {
                Column {
                    MioIcon(MioIcons.Search, "Standalone search")
                    MioIconButton(
                        iconRes = MioIcons.Check,
                        contentDescription = "Selected action",
                        onClick = {},
                        style = MioIconButtonStyle.SELECTED,
                    )
                }
            }
        }

        val standaloneIcon = composeRule.onNodeWithContentDescription("Standalone search").fetchSemanticsNode()
        val selectedButton = composeRule.onNodeWithContentDescription("Selected action")
        assertEquals(composeRule.density.run { 24.dp.toPx() }, standaloneIcon.boundsInRoot.width, 0.5f)
        assertEquals(composeRule.density.run { 24.dp.toPx() }, standaloneIcon.boundsInRoot.height, 0.5f)
        selectedButton.assertIsSelected()
        assertEquals(
            composeRule.density.run { 48.dp.toPx() },
            selectedButton.fetchSemanticsNode().boundsInRoot.width,
            0.5f,
        )
        assertEquals(
            composeRule.density.run { 48.dp.toPx() },
            selectedButton.fetchSemanticsNode().boundsInRoot.height,
            0.5f,
        )
    }

    @Test
    fun disabledIconButtonsDoNotCommit() {
        var clicks = 0
        composeRule.setContent {
            MioTheme {
                MioIconButton(
                    iconRes = MioIcons.Search,
                    contentDescription = "Disabled search",
                    onClick = { clicks++ },
                    enabled = false,
                )
            }
        }

        composeRule.onNodeWithContentDescription("Disabled search").assertIsNotEnabled().performClick()
        composeRule.runOnIdle { assertEquals(0, clicks) }
    }

    @Test
    fun iconButtonRejectsBlankDescriptions() {
        org.junit.Assert.assertThrows(Throwable::class.java) {
            composeRule.setContent {
                MioTheme { MioIconButton(MioIcons.Search, " ", onClick = {}) }
            }
        }
    }
}
