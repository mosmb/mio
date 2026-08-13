package io.morgan.mio.core.designsystem.component

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import io.morgan.mio.core.designsystem.testing.DesignSystemActivityRule
import io.morgan.mio.core.designsystem.testing.composeRule
import io.morgan.mio.core.designsystem.theme.MioTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MioSnackbarTest {
    private val activityRule = DesignSystemActivityRule()

    @get:Rule
    val composeRule: AndroidComposeTestRule<DesignSystemActivityRule, *> = activityRule.composeRule()

    @Test
    fun actionlessSnackbarHasNoActionSemanticsAndIsPolite() {
        composeRule.setContent {
            MioTheme { MioSnackbar("Library imported") }
        }

        composeRule.onNodeWithText("Library imported").assertExists()
        composeRule.onNodeWithText("Undo").assertDoesNotExist()
        composeRule
            .onNode(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
            .assertExists()
    }

    @Test
    fun actionableSnackbarCallsItsImmediateAction() {
        var actions = 0
        composeRule.setContent {
            MioTheme { MioSnackbar("Progress updated", actionLabel = "Undo", onAction = { actions++ }) }
        }

        composeRule.onNodeWithText("Undo").performClick()
        assertEquals(1, actions)
        val bounds = composeRule.onNodeWithText("Undo").fetchSemanticsNode().boundsInRoot
        org.junit.Assert.assertTrue(bounds.width >= composeRule.density.run { 48.dp.toPx() })
        org.junit.Assert.assertTrue(bounds.height >= composeRule.density.run { 48.dp.toPx() })
    }

    @Test
    fun hostReplacesOneMessageAndReturnsItsExactId() {
        val message = mutableStateOf<MioSnackbarMessage?>(null)
        var actionId = ""
        composeRule.setContent {
            MioTheme {
                MioSnackbarHost(
                    snackbar = message.value,
                    bottomNavigationVisible = true,
                    onAction = { actionId = it },
                )
            }
        }

        composeRule.runOnIdle {
            message.value = MioSnackbarMessage("first", "First", "Undo")
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("First").assertExists()
        composeRule.runOnIdle {
            message.value = MioSnackbarMessage("second", "Second", "Undo")
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Second").assertExists()
        composeRule.onNodeWithText("Undo").performClick()
        assertEquals("second", actionId)
    }

    @Test
    fun mismatchedActionPairIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            composeRule.setContent {
                MioTheme { MioSnackbar("Invalid", actionLabel = "Undo") }
            }
        }
    }
}
