package io.morgan.mio

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.onNodeWithText
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import androidx.test.core.app.ApplicationProvider
import coil3.ImageLoader
import io.morgan.mio.core.designsystem.theme.MioColors
import io.morgan.mio.core.designsystem.theme.MioTheme
import io.morgan.mio.core.navigation.MioNavigator
import io.morgan.mio.core.navigation.NavEntryBuilder
import io.morgan.mio.core.navigation.NavigationRegistry
import io.morgan.mio.core.navigation.OnboardingDestination
import io.morgan.mio.core.navigation.TopLevelDestination
import io.morgan.mio.core.navigation.TopLevelSlot
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.reflect.KClass

@Suppress("DEPRECATION")
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MioAppThemeTest {
    private val activityRule = RobolectricActivityRule()
    private val imageLoader by lazy {
        ImageLoader.Builder(ApplicationProvider.getApplicationContext()).build()
    }

    @get:Rule
    val composeRule: AndroidComposeTestRule<RobolectricActivityRule, *> =
        AndroidComposeTestRule(activityRule) { rule -> rule.activity }

    @After
    fun closeImageLoader() {
        imageLoader.shutdown()
    }

    @Test
    fun appThemeSuppliesLightAndDarkBackgroundAndContentColors() {
        val rootState = mutableStateOf(AppRoot.ONBOARDING)
        val darkThemeState = mutableStateOf(false)
        var observedBackground = androidx.compose.ui.graphics.Color.Unspecified
        var observedContentColor = androidx.compose.ui.graphics.Color.Unspecified
        val navigationRegistry =
            registry {
                observedBackground = MioTheme.colors.background
                observedContentColor = LocalContentColor.current
            }
        composeRule.setContent {
            MioApp(
                root = rootState.value,
                navigationRegistry = navigationRegistry,
                imageLoader = imageLoader,
                darkTheme = darkThemeState.value,
                highContrastOverride = false,
            )
        }
        AppRoot.entries.forEach { root ->
            listOf(
                false to 0xFFFFFFFF,
                true to 0xFF0B0D0F,
            ).forEach { (darkTheme, expectedBackground) ->
                composeRule.runOnIdle {
                    rootState.value = root
                    darkThemeState.value = darkTheme
                }
                composeRule.waitForIdle()

                val expectedBackgroundColor =
                    androidx.compose.ui.graphics.Color(
                        expectedBackground,
                    )
                assertEquals(expectedBackgroundColor, observedBackground)
                val expectedContent = if (darkTheme) MioColors.Dark.text else MioColors.Light.text
                assertEquals(expectedContent, observedContentColor)
                composeRule.onNodeWithText("Task 08 theme probe").assertExists()
            }
        }
    }

    private fun registry(probe: @Composable () -> Unit): NavigationRegistry =
        NavigationRegistry(
            onboardingDestination = OnboardingDestination(RootTestOnboardingKey.ROOT),
            topLevelDestinations =
                setOf(
                    TopLevelDestination(TopLevelSlot.TODAY, RootTestTodayKey.ROOT),
                    TopLevelDestination(TopLevelSlot.LIBRARY, RootTestLibraryKey.ROOT),
                    TopLevelDestination(TopLevelSlot.DISCOVER, RootTestDiscoverKey.ROOT),
                    TopLevelDestination(TopLevelSlot.YOU, RootTestYouKey.ROOT),
                ),
            entryBuilders = setOf(AppThemeEntryBuilder(probe)),
        )

    private class AppThemeEntryBuilder(
        private val probe: @Composable () -> Unit,
    ) : NavEntryBuilder {
        override val destinationKeyTypes: Set<KClass<out NavKey>> =
            setOf(
                RootTestOnboardingKey::class,
                RootTestTodayKey::class,
                RootTestLibraryKey::class,
                RootTestDiscoverKey::class,
                RootTestYouKey::class,
            )

        override fun EntryProviderScope<NavKey>.build(navigator: MioNavigator) {
            entry<RootTestOnboardingKey> {
                probe()
                Text("Task 08 theme probe")
            }
            entry<RootTestTodayKey> {
                probe()
                Text("Task 08 theme probe")
            }
            entry<RootTestLibraryKey> { Text("Library") }
            entry<RootTestDiscoverKey> { Text("Discover") }
            entry<RootTestYouKey> { Text("You") }
        }
    }
}
