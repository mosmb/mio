package io.morgan.mio.core.designsystem.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
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
class MioStateComponentsTest {
    private val activityRule = DesignSystemActivityRule()

    @get:Rule
    val composeRule: AndroidComposeTestRule<DesignSystemActivityRule, *> = activityRule.composeRule()

    @Test
    fun emptyStateRequiresAnActionPairAndCallsIt() {
        var actions = 0
        composeRule.setContent {
            MioTheme {
                MioEmptyState(
                    title = "Empty",
                    message = "Nothing to show",
                    actionLabel = "Retry",
                    onAction = { actions++ },
                )
            }
        }
        composeRule.onNodeWithText("Retry").performClick()
        assertEquals(1, actions)
        assertThrows(Throwable::class.java) {
            composeRule.setContent {
                MioTheme {
                    MioEmptyState(title = "Invalid", message = "Missing callback", actionLabel = "Action")
                }
            }
        }
    }

    @Test
    fun errorStateIncludesTextAndRetryAndSkeletonIsDecorative() {
        var retries = 0
        composeRule.setContent {
            MioTheme(reducedMotionOverride = true) {
                Column {
                    MioSkeleton(Modifier.size(120.dp, 32.dp))
                    MioErrorState("Error", "Try again", "Retry", onRetry = { retries++ })
                }
            }
        }
        composeRule.onNodeWithText("Error").assertExists()
        composeRule.onNodeWithText("Retry").performClick()
        assertEquals(1, retries)
        composeRule.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.ContentDescription)).assertDoesNotExist()
    }
}
