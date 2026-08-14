package io.morgan.mio.feature.onboarding.impl

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasProgressBarRangeInfo
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import io.morgan.mio.core.designsystem.theme.MioTheme
import io.morgan.mio.feature.onboarding.impl.component.onboardingMosaicTileCount
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

@Suppress("DEPRECATION")
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class OnboardingScreenTest {
    private val activityRule = ScreenActivityRule()

    @get:Rule
    val composeRule = AndroidComposeTestRule(activityRule) { rule -> rule.activity }

    @Test
    fun welcomeHasExactCopyTwoActionsAndNoForbiddenPath() {
        show(welcomeState())

        composeRule.onNodeWithText("mio").assertIsDisplayed()
        composeRule.onNodeWithText("Your anime and manga, ready when you are.").assertIsDisplayed()
        composeRule
            .onNodeWithText(
                "Connect the provider you already use. It stays the source of truth for your library and progress.",
            ).assertIsDisplayed()
        composeRule.onNodeWithText("Continue with AniList").assertIsEnabled()
        composeRule.onNodeWithText("Continue with MyAnimeList").assertIsEnabled()
        composeRule.onAllNodesWithText("Continue with", substring = true).assertCountEquals(2)

        listOf(
            "guest",
            "skip",
            "browse",
            "tutorial",
            "carousel",
            "questionnaire",
            "merge",
            "switch",
        ).forEach { forbidden ->
            composeRule
                .onAllNodesWithText(forbidden, substring = true, ignoreCase = true)
                .assertCountEquals(0)
        }
    }

    @Test
    fun eachWelcomeCallbackFiresOnlyItsMatchingAction() {
        var aniList = 0
        var myAnimeList = 0
        var retry = 0
        show(
            state =
                welcomeState(
                    error =
                        OnboardingErrorUi(
                            titleRes = R.string.onboarding_error_authentication_title,
                            messageRes = R.string.onboarding_error_authentication_message,
                            retryLabelRes = R.string.onboarding_retry,
                        ),
                    provider = OnboardingProviderUi.ANILIST,
                ),
            onAniList = { aniList += 1 },
            onMyAnimeList = { myAnimeList += 1 },
            onRetry = { retry += 1 },
        )

        composeRule.onNodeWithText("Continue with AniList").performClick()
        assertEquals(listOf(1, 0, 0), listOf(aniList, myAnimeList, retry))
        composeRule.onNodeWithText("Continue with MyAnimeList").performClick()
        assertEquals(listOf(1, 1, 0), listOf(aniList, myAnimeList, retry))
        composeRule.onNodeWithText("Retry").performClick()
        assertEquals(listOf(1, 1, 1), listOf(aniList, myAnimeList, retry))
    }

    @Test
    fun progressStatesAreIndeterminateAndDisableProviderActions() {
        val states =
            listOf(
                OnboardingUiState(OnboardingPhase.RESTORING, null, null) to "Checking your connection",
                OnboardingUiState(OnboardingPhase.AUTHORIZING, OnboardingProviderUi.ANILIST, null) to
                    "Continue in your browser to connect AniList.",
            )
        val currentState = showChanging(states.first().first)
        states.forEachIndexed { index, (state, status) ->
            if (index > 0) {
                composeRule.runOnIdle { currentState.value = state }
            }
            composeRule.onNodeWithContentDescription(status).assertIsDisplayed()
            composeRule.onNode(hasProgressBarRangeInfo(ProgressBarRangeInfo.Indeterminate)).assertIsDisplayed()
            composeRule.onNodeWithText("Continue with AniList").assertIsNotEnabled()
            composeRule.onNodeWithText("Continue with MyAnimeList").assertIsNotEnabled()
            composeRule.onAllNodesWithText("%", substring = true).assertCountEquals(0)
        }
    }

    @Test
    fun retryAppearsOnlyForRetryableFailures() {
        val currentState =
            showChanging(
                welcomeState(
                    error =
                        OnboardingErrorUi(
                            R.string.onboarding_error_authentication_title,
                            R.string.onboarding_error_authentication_message,
                            R.string.onboarding_retry,
                        ),
                    provider = OnboardingProviderUi.ANILIST,
                ),
            )
        composeRule.onNodeWithText("Retry").assertIsDisplayed()

        composeRule.runOnIdle {
            currentState.value =
                welcomeState(
                    error =
                        OnboardingErrorUi(
                            R.string.onboarding_error_unavailable_title,
                            R.string.onboarding_error_unavailable_message,
                            null,
                        ),
                )
        }
        composeRule.onAllNodesWithText("Retry").assertCountEquals(0)
    }

    @Test
    fun importShowsTenPlaceholderMosaicAndNoFakeCompletion() {
        show(OnboardingUiState(OnboardingPhase.IMPORTING, OnboardingProviderUi.ANILIST, null))

        composeRule.onNodeWithText("Bringing in your library").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Library import in progress").assertIsDisplayed()
        composeRule
            .onNodeWithText(
                "Your AniList library remains the source of truth for your library and progress.",
            ).assertIsDisplayed()
        assertEquals(10, onboardingMosaicTileCount())
        listOf("%", "Library imported", "Open Today").forEach { forbidden ->
            composeRule.onAllNodesWithText(forbidden, substring = true).assertCountEquals(0)
        }
    }

    @Test
    fun compactLargeTextRemainsScrollableWithFullProviderLabels() {
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 2f)) {
                MioTheme {
                    OnboardingScreen(
                        modifier = Modifier.width(320.dp).height(480.dp),
                        state = welcomeState(),
                        onContinueWithAniList = {},
                        onContinueWithMyAnimeList = {},
                        onRetry = {},
                    )
                }
            }
        }

        composeRule.onNodeWithText("Continue with AniList").performScrollTo().assertIsDisplayed()
        val second =
            composeRule
                .onNodeWithText("Continue with MyAnimeList")
                .performScrollTo()
                .assertIsDisplayed()
                .fetchSemanticsNode()
        assertTrue(second.boundsInRoot.left >= 0f)
        assertTrue(second.boundsInRoot.right <= with(composeRule.density) { 320.dp.toPx() })
    }

    private fun show(
        state: OnboardingUiState,
        onAniList: () -> Unit = {},
        onMyAnimeList: () -> Unit = {},
        onRetry: () -> Unit = {},
    ) {
        composeRule.setContent {
            MioTheme {
                OnboardingScreen(
                    state = state,
                    onContinueWithAniList = onAniList,
                    onContinueWithMyAnimeList = onMyAnimeList,
                    onRetry = onRetry,
                )
            }
        }
    }

    private fun showChanging(initialState: OnboardingUiState): MutableState<OnboardingUiState> {
        val state = mutableStateOf(initialState)
        composeRule.setContent {
            MioTheme {
                OnboardingScreen(
                    state = state.value,
                    onContinueWithAniList = {},
                    onContinueWithMyAnimeList = {},
                    onRetry = {},
                )
            }
        }
        return state
    }

    private fun welcomeState(
        error: OnboardingErrorUi? = null,
        provider: OnboardingProviderUi? = null,
    ): OnboardingUiState = OnboardingUiState(OnboardingPhase.WELCOME, provider, error)
}

@Suppress("DEPRECATION")
class ScreenActivityRule : TestRule {
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
