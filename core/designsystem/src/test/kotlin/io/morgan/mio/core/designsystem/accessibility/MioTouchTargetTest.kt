package io.morgan.mio.core.designsystem.accessibility

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import io.morgan.mio.core.designsystem.testing.DesignSystemActivityRule
import io.morgan.mio.core.designsystem.testing.composeRule
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MioTouchTargetTest {
    private val activityRule = DesignSystemActivityRule()

    @get:Rule
    val composeRule: AndroidComposeTestRule<DesignSystemActivityRule, *> = activityRule.composeRule()

    @Test
    fun smallChildrenAreMeasuredAtTheMinimumTouchTarget() {
        composeRule.setContent {
            Box(
                Modifier
                    .mioMinimumTouchTarget()
                    .testTag("target"),
            ) {
                Box(Modifier.size(8.dp))
            }
        }
        val minimum = with(composeRule.density) { MioTouchTarget.MinimumSize.toPx() }
        val bounds = composeRule.onNodeWithTag("target").fetchSemanticsNode().boundsInRoot
        assertTrue(bounds.width >= minimum)
        assertTrue(bounds.height >= minimum)
    }
}
