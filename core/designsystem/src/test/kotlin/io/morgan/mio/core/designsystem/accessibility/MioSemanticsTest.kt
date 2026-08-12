package io.morgan.mio.core.designsystem.accessibility

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import io.morgan.mio.core.designsystem.testing.DesignSystemActivityRule
import io.morgan.mio.core.designsystem.testing.composeRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MioSemanticsTest {
    private val activityRule = DesignSystemActivityRule()

    @get:Rule
    val composeRule: AndroidComposeTestRule<DesignSystemActivityRule, *> = activityRule.composeRule()

    @Test
    fun genericSemanticModifiersExposeTheExpectedTree() {
        composeRule.setContent {
            Column(
                Modifier
                    .mioHeading()
                    .mioSelected(true)
                    .mioSemanticGroup(),
            ) {
                Text("Heading")
                Text("Selected child")
            }
            Text("Decorative", modifier = Modifier.mioDecorative())
        }
        composeRule
            .onNodeWithText("Heading")
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Selected, true))
        composeRule.onNodeWithText("Selected child", useUnmergedTree = true).assertExists()
        composeRule.onAllNodesWithText("Decorative").assertCountEquals(0)
        composeRule.onAllNodesWithText("Decorative", useUnmergedTree = true).assertCountEquals(0)
    }
}
