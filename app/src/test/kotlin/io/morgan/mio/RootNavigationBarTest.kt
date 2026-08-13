package io.morgan.mio

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.navigation3.runtime.NavKey
import io.morgan.mio.core.designsystem.theme.MioTheme
import io.morgan.mio.core.navigation.TopLevelDestination
import io.morgan.mio.core.navigation.TopLevelSlot
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@Suppress("DEPRECATION")
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RootNavigationBarTest {
    private val activityRule = RobolectricActivityRule()

    @get:Rule
    val composeRule: AndroidComposeTestRule<RobolectricActivityRule, *> =
        AndroidComposeTestRule(activityRule) { rule -> rule.activity }

    @Test
    fun adapterKeepsNormativeOrderLabelsSelectedStateAndExactDestination() {
        val today = TopLevelDestination(TopLevelSlot.TODAY, RootBarTodayKey.ROOT)
        val library = TopLevelDestination(TopLevelSlot.LIBRARY, RootBarLibraryKey.ROOT)
        val discover = TopLevelDestination(TopLevelSlot.DISCOVER, RootBarDiscoverKey.ROOT)
        val you = TopLevelDestination(TopLevelSlot.YOU, RootBarYouKey.ROOT)
        val selected = mutableStateOf(TopLevelSlot.TODAY)
        val selectedDestinations = mutableListOf<TopLevelDestination>()
        val destinations = listOf(today, library, discover, you)

        composeRule.setContent {
            MioTheme {
                RootNavigationBar(
                    modifier = Modifier.fillMaxWidth(),
                    destinations = destinations,
                    selectedSlot = selected.value,
                    onSelected = {
                        selectedDestinations += it
                        selected.value = it.slot
                    },
                )
            }
        }

        listOf("Today", "Library", "Discover", "You").forEach { label ->
            composeRule.onNodeWithText(label).assertExists()
        }
        composeRule.onNodeWithText("Today").assertIsSelected()
        composeRule.onNodeWithText("Library").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Library").assertIsSelected()
        assertEquals(listOf(library), selectedDestinations)
    }
}

private enum class RootBarTodayKey : NavKey {
    ROOT,
}

private enum class RootBarLibraryKey : NavKey {
    ROOT,
}

private enum class RootBarDiscoverKey : NavKey {
    ROOT,
}

private enum class RootBarYouKey : NavKey {
    ROOT,
}
