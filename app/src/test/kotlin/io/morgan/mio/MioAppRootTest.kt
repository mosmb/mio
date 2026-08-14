package io.morgan.mio

import androidx.activity.ComponentActivity
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import androidx.test.core.app.ApplicationProvider
import coil3.ImageLoader
import io.morgan.mio.core.navigation.MioNavigator
import io.morgan.mio.core.navigation.NavEntryBuilder
import io.morgan.mio.core.navigation.NavigationRegistry
import io.morgan.mio.core.navigation.OnboardingDestination
import io.morgan.mio.core.navigation.TopLevelDestination
import io.morgan.mio.core.navigation.TopLevelSlot
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runner.RunWith
import org.junit.runners.model.Statement
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.reflect.KClass

@Suppress("DEPRECATION")
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MioAppRootTest {
    private val activityRule = RobolectricActivityRule()
    private val imageLoader by lazy {
        ImageLoader.Builder(ApplicationProvider.getApplicationContext()).build()
    }

    @get:Rule
    val composeRule =
        AndroidComposeTestRule(activityRule) { rule ->
            rule.activity
        }

    @After
    fun closeImageLoader() {
        imageLoader.shutdown()
    }

    @Test
    fun switchingRootsReplacesNavigationSubtreesAndChrome() {
        val root = mutableStateOf(AppRoot.ONBOARDING)
        val registry = rootTestRegistry()

        assertEquals(RootTestOnboardingKey.ROOT, registry.onboardingDestination.key)
        assertEquals(
            listOf(
                RootTestTodayKey.ROOT,
                RootTestLibraryKey.ROOT,
                RootTestDiscoverKey.ROOT,
                RootTestYouKey.ROOT,
            ),
            registry.topLevelDestinations.map(TopLevelDestination::key),
        )

        composeRule.setContent {
            MioApp(
                root = root.value,
                navigationRegistry = registry,
                imageLoader = imageLoader,
                viewModelFactory = testMetroViewModelFactory,
            )
        }

        composeRule.onNodeWithText("Test onboarding content").assertIsDisplayed()
        composeRule.onAllNodesWithText("Today").assertCountEquals(0)
        composeRule.onAllNodesWithText("Library").assertCountEquals(0)

        root.value = AppRoot.AUTHENTICATED
        composeRule.waitForIdle()
        composeRule.onAllNodesWithText("Test onboarding content").assertCountEquals(0)
        composeRule.onNodeWithText("Test today content").assertIsDisplayed()
        composeRule.onAllNodesWithText("Today").assertCountEquals(1)
        composeRule.onAllNodesWithText("Library").assertCountEquals(1)
        composeRule.onAllNodesWithText("Discover").assertCountEquals(1)
        composeRule.onAllNodesWithText("You").assertCountEquals(1)
        composeRule.onNodeWithText("Today").assertIsSelected()
        composeRule.onNodeWithText("Library").assertIsNotSelected()
        val minimumTargetHeight = with(composeRule.density) { 48.dp.toPx() }
        assertTrue(
            composeRule
                .onNodeWithText("Library")
                .fetchSemanticsNode()
                .boundsInRoot.height >=
                minimumTargetHeight,
        )

        root.value = AppRoot.ONBOARDING
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Test onboarding content").assertIsDisplayed()
        composeRule.onAllNodesWithText("Test today content").assertCountEquals(0)
        composeRule.onAllNodesWithText("Today").assertCountEquals(0)
        composeRule.onAllNodesWithText("Library").assertCountEquals(0)
        composeRule.onAllNodesWithText("Discover").assertCountEquals(0)
        composeRule.onAllNodesWithText("You").assertCountEquals(0)
    }

    private fun rootTestRegistry(): NavigationRegistry =
        NavigationRegistry(
            onboardingDestination = OnboardingDestination(RootTestOnboardingKey.ROOT),
            topLevelDestinations =
                setOf(
                    TopLevelDestination(TopLevelSlot.TODAY, RootTestTodayKey.ROOT),
                    TopLevelDestination(TopLevelSlot.LIBRARY, RootTestLibraryKey.ROOT),
                    TopLevelDestination(TopLevelSlot.DISCOVER, RootTestDiscoverKey.ROOT),
                    TopLevelDestination(TopLevelSlot.YOU, RootTestYouKey.ROOT),
                ),
            entryBuilders = setOf(RootTestEntryBuilder),
        )

    private object RootTestEntryBuilder : NavEntryBuilder {
        override val destinationKeyTypes: Set<KClass<out NavKey>> =
            setOf(
                RootTestOnboardingKey::class,
                RootTestTodayKey::class,
                RootTestLibraryKey::class,
                RootTestDiscoverKey::class,
                RootTestYouKey::class,
            )

        override fun EntryProviderScope<NavKey>.build(navigator: MioNavigator) {
            entry<RootTestOnboardingKey> { BasicText("Test onboarding content") }
            entry<RootTestTodayKey> { BasicText("Test today content") }
            entry<RootTestLibraryKey> { BasicText("Test library content") }
            entry<RootTestDiscoverKey> { BasicText("Test discover content") }
            entry<RootTestYouKey> { BasicText("Test you content") }
        }
    }
}

public enum class RootTestOnboardingKey : NavKey {
    ROOT,
}

public enum class RootTestTodayKey : NavKey {
    ROOT,
}

public enum class RootTestLibraryKey : NavKey {
    ROOT,
}

public enum class RootTestDiscoverKey : NavKey {
    ROOT,
}

public enum class RootTestYouKey : NavKey {
    ROOT,
}

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
