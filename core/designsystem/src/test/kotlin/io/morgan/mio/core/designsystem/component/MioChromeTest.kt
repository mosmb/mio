package io.morgan.mio.core.designsystem.component

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
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
class MioChromeTest {
    private val activityRule = DesignSystemActivityRule()

    @get:Rule
    val composeRule: AndroidComposeTestRule<DesignSystemActivityRule, *> = activityRule.composeRule()

    @Test
    fun nestedTopBarKeepsBackFirstAndReturnsActionIds() {
        var back = 0
        var action = ""
        composeRule.setContent {
            MioTheme {
                MioTopBar(
                    title = "Nested",
                    backContentDescription = "Back",
                    onBack = { back++ },
                    actions = listOf(MioTopBarAction("search", MioIcons.Search, "Search")),
                    onAction = { action = it },
                )
            }
        }

        composeRule.onNodeWithContentDescription("Back").performClick()
        composeRule.onNodeWithContentDescription("Search").performClick()
        assertEquals(1, back)
        assertEquals("search", action)
        assertTrue(
            composeRule
                .onNodeWithContentDescription("Back")
                .fetchSemanticsNode()
                .boundsInRoot.height >=
                composeRule.density.run { 48.dp.toPx() },
        )
    }

    @Test
    fun topBarsRejectMoreThanTwoTrailingActions() {
        assertThrows(IllegalArgumentException::class.java) {
            composeRule.setContent {
                MioTheme {
                    MioRootTopBar(
                        title = "Too many",
                        onAction = {},
                        actions =
                            listOf(
                                MioTopBarAction("one", MioIcons.Add, "One"),
                                MioTopBarAction("two", MioIcons.Add, "Two"),
                                MioTopBarAction("three", MioIcons.Add, "Three"),
                            ),
                    )
                }
            }
        }
    }

    @Test
    fun bottomNavigationUsesOrderedItemsAndSelectedSemantics() {
        val selectedId = mutableStateOf("TODAY")
        val items =
            listOf(
                MioBottomNavigationItem("TODAY", "Today", MioIcons.TodayOutline, MioIcons.TodayFilled),
                MioBottomNavigationItem("LIBRARY", "Library", MioIcons.LibraryOutline, MioIcons.LibraryFilled),
                MioBottomNavigationItem("DISCOVER", "Discover", MioIcons.DiscoverOutline, MioIcons.DiscoverFilled),
                MioBottomNavigationItem("YOU", "You", MioIcons.YouOutline, MioIcons.YouFilled),
            )
        composeRule.setContent {
            MioTheme {
                MioBottomNavigation(items, selectedId.value, onItemSelected = { selectedId.value = it })
            }
        }

        composeRule.onNodeWithText("Today").assertIsSelected()
        composeRule
            .onNodeWithTag("MioBottomNavigation:TODAY:filled", useUnmergedTree = true)
            .assertExists()
        composeRule
            .onNodeWithTag("MioBottomNavigation:LIBRARY:outline", useUnmergedTree = true)
            .assertExists()

        val widths =
            listOf("Today", "Library", "Discover", "You").map { label ->
                composeRule
                    .onNodeWithText(label)
                    .fetchSemanticsNode()
                    .boundsInRoot.width
            }
        assertTrue(widths.all { width -> kotlin.math.abs(width - widths.first()) <= 0.5f })
        assertTrue(widths.all { width -> width >= composeRule.density.run { 48.dp.toPx() } })

        composeRule.onNodeWithText("Library").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Library").assertIsSelected()
        composeRule
            .onNodeWithTag("MioBottomNavigation:LIBRARY:filled", useUnmergedTree = true)
            .assertExists()
        assertEquals("LIBRARY", selectedId.value)
    }
}
