package io.morgan.mio.core.navigation

import androidx.activity.ComponentActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import kotlinx.serialization.Serializable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runner.RunWith
import org.junit.runners.model.Statement
import org.robolectric.Robolectric
import org.robolectric.annotation.Config

@Suppress("DEPRECATION")
@RunWith(org.robolectric.RobolectricTestRunner::class)
@Config(sdk = [35])
class NavigationRestorationTest {
    private val activityRule = RobolectricActivityRule()

    @get:Rule
    val composeRule =
        AndroidComposeTestRule(activityRule) { rule ->
            rule.activity
        }

    @Test
    fun retainedStacksScrollAndSaveableEntryStateSurviveSwitchesAndRestoration() {
        val destinations =
            listOf(
                TopLevelDestination(TopLevelSlot.TODAY, RestorationTodayKey),
                TopLevelDestination(TopLevelSlot.LIBRARY, RestorationLibraryKey),
                TopLevelDestination(TopLevelSlot.DISCOVER, RestorationDiscoverKey),
                TopLevelDestination(TopLevelSlot.YOU, RestorationYouKey),
            )
        val harness = RestorationHarness()
        val entryProvider =
            entryProvider {
                entry<RestorationTodayKey> { harness.TodayContent() }
                entry<RestorationTodayNestedKey> { BasicText("Today nested") }
                entry<RestorationLibraryKey> { BasicText("Library root") }
                entry<RestorationLibraryNestedKey> { harness.LibraryCounterContent() }
                entry<RestorationDiscoverKey> { BasicText("Discover root") }
                entry<RestorationYouKey> { BasicText("You root") }
            }
        lateinit var state: AuthenticatedNavigationState
        lateinit var navigator: MioNavigator
        val restorationTester = StateRestorationTester(composeRule)

        restorationTester.setContent {
            state = rememberAuthenticatedNavigationState(destinations)
            navigator = state.navigator
            NavDisplay(
                entries = state.decoratedEntries(entryProvider),
                onBack = { navigator.goBack() },
            )
        }

        composeRule.onNodeWithTag("today-list").performScrollToNode(hasText("Today row 50"))
        composeRule.onNodeWithText("Today row 50").assertIsDisplayed()
        navigator.navigate(RestorationTodayNestedKey)
        navigator.navigate(RestorationLibraryKey)
        navigator.navigate(RestorationLibraryNestedKey)
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Library counter: 0").performClick()
        composeRule.onNodeWithText("Library counter: 1").assertIsDisplayed()

        navigator.navigate(RestorationTodayKey)
        composeRule.waitForIdle()
        assertEquals(
            listOf(RestorationTodayKey, RestorationTodayNestedKey),
            state.stackSnapshot(TopLevelSlot.TODAY),
        )
        composeRule.runOnIdle {
            assertTrue(harness.todayListState?.firstVisibleItemIndex ?: 0 > 0)
        }

        navigator.navigate(RestorationLibraryKey)
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Library counter: 1").assertIsDisplayed()
        val expectedTodayStack = state.stackSnapshot(TopLevelSlot.TODAY)
        val expectedLibraryStack = state.stackSnapshot(TopLevelSlot.LIBRARY)

        restorationTester.emulateSavedInstanceStateRestore()
        composeRule.waitForIdle()

        assertEquals(TopLevelSlot.LIBRARY, state.selectedDestination.slot)
        assertEquals(expectedTodayStack, state.stackSnapshot(TopLevelSlot.TODAY))
        assertEquals(expectedLibraryStack, state.stackSnapshot(TopLevelSlot.LIBRARY))
        assertEquals(listOf(RestorationDiscoverKey), state.stackSnapshot(TopLevelSlot.DISCOVER))
        assertEquals(listOf(RestorationYouKey), state.stackSnapshot(TopLevelSlot.YOU))
        composeRule.onNodeWithText("Library counter: 1").assertIsDisplayed()

        assertTrue(navigator.goBack())
        composeRule.waitForIdle()
        assertEquals(listOf(RestorationLibraryKey), state.stackSnapshot(TopLevelSlot.LIBRARY))
        assertEquals(expectedTodayStack, state.stackSnapshot(TopLevelSlot.TODAY))
        composeRule.runOnIdle {
            assertTrue(harness.todayListState?.firstVisibleItemIndex ?: 0 > 0)
        }

        navigator.navigate(RestorationTodayKey)
        composeRule.waitForIdle()
        assertEquals(expectedTodayStack, state.stackSnapshot(TopLevelSlot.TODAY))
        assertTrue(navigator.goBack())
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Today row 50").assertIsDisplayed()
    }

    private class RestorationHarness {
        var todayListState: androidx.compose.foundation.lazy.LazyListState? = null

        @Composable
        fun TodayContent() {
            val listState = rememberLazyListState()
            todayListState = listState
            LazyColumn(
                modifier = Modifier.testTag("today-list"),
                state = listState,
            ) {
                items((0 until 100).toList()) { index ->
                    BasicText(
                        text = "Today row $index",
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }

        @Composable
        fun LibraryCounterContent() {
            var count by rememberSaveable { mutableIntStateOf(0) }
            BasicText(
                text = "Library counter: $count",
                modifier = Modifier.clickable { count++ },
            )
        }
    }
}

@Serializable
public data object RestorationTodayKey : NavKey

@Serializable
public data object RestorationTodayNestedKey : NavKey

@Serializable
public data object RestorationLibraryKey : NavKey

@Serializable
public data object RestorationLibraryNestedKey : NavKey

@Serializable
public data object RestorationDiscoverKey : NavKey

@Serializable
public data object RestorationYouKey : NavKey

public class RobolectricActivityRule : TestRule {
    lateinit var activity: ComponentActivity

    override fun apply(
        base: Statement,
        description: Description,
    ): Statement =
        object : Statement() {
            override fun evaluate() {
                activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
                try {
                    base.evaluate()
                } finally {
                    activity.finish()
                }
            }
        }
}
