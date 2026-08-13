package io.morgan.mio.core.designsystem.component

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
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
class MioBottomSheetTest {
    private val activityRule = DesignSystemActivityRule()

    @get:Rule
    val composeRule: AndroidComposeTestRule<DesignSystemActivityRule, *> = activityRule.composeRule()

    @Test
    fun invisibleSheetDoesNotRenderAndReducedMotionSheetIsStaticContent() {
        val visible = mutableStateOf(false)
        var dismissals = 0
        composeRule.setContent {
            MioTheme(reducedMotionOverride = true) {
                MioBottomSheet(
                    visible = visible.value,
                    onDismissRequest = { dismissals++ },
                    title = "Status",
                    subtitle = "Choose a status",
                ) {
                    MioStatusRow("watching", "Watching", selected = true, onSelected = {})
                }
            }
        }

        composeRule.onNodeWithText("Status").assertDoesNotExist()
        composeRule.runOnIdle { visible.value = true }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Status").assertExists()
        composeRule.onNodeWithText("Watching").performClick()
        composeRule.runOnIdle { assertEquals(0, dismissals) }
    }

    @Test
    fun sheetRowSelectionRemainsAnExplicitDismissalAlternative() {
        var selection = ""
        composeRule.setContent {
            MioTheme(reducedMotionOverride = true) {
                MioBottomSheet(visible = true, onDismissRequest = {}, title = "Status") {
                    MioStatusRow("completed", "Completed", selected = false, onSelected = { selection = it })
                }
            }
        }

        composeRule.onNodeWithText("Completed").performClick()
        assertEquals("completed", selection)
    }

    @Test
    fun reducedMotionSheetContentDoesNotDismissWhenItsSurfaceIsTapped() {
        var dismissals = 0
        composeRule.setContent {
            MioTheme(reducedMotionOverride = true) {
                MioBottomSheet(
                    visible = true,
                    onDismissRequest = { dismissals++ },
                    title = "Static status",
                ) {
                    MioStatusRow("watching", "Watching", selected = true, onSelected = {})
                }
            }
        }

        composeRule.onNodeWithText("Static status").performTouchInput {
            down(center)
            up()
        }
        composeRule.runOnIdle { assertEquals(0, dismissals) }
        composeRule
            .onAllNodesWithTag("MioBottomSheet:dragHandle", useUnmergedTree = true)
            .assertCountEquals(1)
    }

    @Test
    fun ordinaryMotionSheetRendersExactlyOneDragHandle() {
        composeRule.setContent {
            MioTheme {
                MioBottomSheet(visible = true, onDismissRequest = {}, title = "Animated status") {
                    MioStatusRow("watching", "Watching", selected = true, onSelected = {})
                }
            }
        }

        composeRule.waitForIdle()
        composeRule
            .onAllNodesWithTag("MioBottomSheet:dragHandle", useUnmergedTree = true)
            .assertCountEquals(1)
    }
}
