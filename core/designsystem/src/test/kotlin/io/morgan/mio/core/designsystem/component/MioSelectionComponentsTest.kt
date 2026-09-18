package io.morgan.mio.core.designsystem.component

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import io.morgan.mio.core.designsystem.icon.MioIcons
import io.morgan.mio.core.designsystem.testing.DesignSystemActivityRule
import io.morgan.mio.core.designsystem.testing.composeRule
import io.morgan.mio.core.designsystem.theme.MioTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MioSelectionComponentsTest {
    private val activityRule = DesignSystemActivityRule()

    @get:Rule
    val composeRule: AndroidComposeTestRule<DesignSystemActivityRule, *> = activityRule.composeRule()

    @Test
    fun filterAndStatusRowsExposeSelectionAndUiIds() {
        var selectedId = ""
        composeRule.setContent {
            MioTheme {
                Column {
                    MioFilterPill("Selected", selected = true, onClick = {})
                    MioStatusRow(
                        id = "completed",
                        label = "Completed",
                        selected = false,
                        onSelected = { selectedId = it },
                        leadingIconRes = MioIcons.Check,
                        supportingText = "Finished",
                    )
                }
            }
        }

        composeRule.onNodeWithText("Selected").assertIsSelected()
        composeRule.onNodeWithText("Completed").performClick()
        composeRule.waitForIdle()
        assertEquals("completed", selectedId)
        assertTrue(
            composeRule
                .onNodeWithText("Completed")
                .fetchSemanticsNode()
                .boundsInRoot.height >=
                composeRule.density.run { 48.dp.toPx() },
        )
    }

    @Test
    fun progressBarExposesDescriptionAndRejectsInvalidUiModels() {
        composeRule.setContent {
            MioTheme { MioProgressBar(0.42f, progressDescription = "42 percent") }
        }
        composeRule
            .onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo))
            .assertExists()
        composeRule.onNodeWithText("42 percent").assertDoesNotExist()

        assertThrows(Throwable::class.java) {
            composeRule.setContent {
                MioTheme { MioProgressBar(Float.NaN) }
            }
        }
    }

    @Test
    fun compactFilterStillKeepsA48DpTargetAndDisabledRowsDoNotCommit() {
        var selectedId = ""
        composeRule.setContent {
            MioTheme {
                Column {
                    MioFilterPill("I", selected = false, onClick = {})
                    MioStatusRow(
                        id = "disabled",
                        label = "Disabled",
                        selected = false,
                        onSelected = { selectedId = it },
                        enabled = false,
                    )
                }
            }
        }

        val filterBounds = composeRule.onNodeWithText("I").fetchSemanticsNode().boundsInRoot
        assertTrue(filterBounds.width >= composeRule.density.run { 48.dp.toPx() })
        assertEquals(composeRule.density.run { 48.dp.toPx() }, filterBounds.height, 1f)
        val labelBounds = composeRule.onNodeWithText("I", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertEquals(filterBounds.center.x, labelBounds.center.x, 1f)
        assertEquals(filterBounds.center.y, labelBounds.center.y, 1f)
        composeRule.onNodeWithText("Disabled").performClick()
        composeRule.runOnIdle { assertEquals("", selectedId) }
    }
}
