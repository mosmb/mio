package io.morgan.mio.feature.media.impl

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import coil3.ImageLoader
import io.morgan.mio.core.designsystem.theme.MioTheme
import io.morgan.mio.core.ui.image.MioImageLoaderProvider
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

@Suppress("DEPRECATION")
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MediaDetailScreenTest {
    private val activityRule = ScreenActivityRule()
    private var imageLoader: ImageLoader? = null

    @get:Rule
    val composeRule = AndroidComposeTestRule(activityRule) { rule -> rule.activity }

    @After
    fun tearDown() {
        imageLoader?.shutdown()
    }

    @Test
    fun populatedTitleHasHeadingsCallbacksAndNoMutationControls() {
        var backCalls = 0
        var relatedId: String? = null
        show(
            state = populatedState(),
            onBack = { backCalls += 1 },
            onRelatedTitleClick = { id -> relatedId = id },
        )

        composeRule.onNodeWithText("Readable title").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Poster for Readable title").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Back").performClick()
        composeRule.onNodeWithText("Not in library").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Synopsis").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Related titles").performScrollTo()
        composeRule.onNodeWithText("Related titles").assertIsDisplayed()
        composeRule.onNode(verticalScrollMatcher()).performTouchInput { swipeUp() }
        composeRule.onNodeWithContentDescription("Open related title Related title").assertIsDisplayed()
        composeRule.onNodeWithText("Related title").assertIsDisplayed().performClick()

        assertEquals(1, backCalls)
        assertEquals("related-1", relatedId)
        listOf("Add to library", "Change status", "Set status", "+1", "-1", "Progress editor", "Correction")
            .forEach { forbidden ->
                composeRule.onAllNodesWithText(forbidden, substring = true, ignoreCase = true).assertCountEquals(0)
            }
    }

    @Test
    fun loadingAndErrorStatesExposeOnlyLoadingOrRetryActions() {
        var retryCalls = 0
        var backCalls = 0
        val state = mutableStateOf<MediaDetailUiState>(MediaDetailUiState.Loading())
        imageLoader = ImageLoader.Builder(activityRule.activity).build()
        composeRule.setContent {
            MioTheme {
                MioImageLoaderProvider(requireNotNull(imageLoader)) {
                    MediaDetailScreen(
                        state = state.value,
                        onBack = { backCalls += 1 },
                        onRetry = { retryCalls += 1 },
                        onRelatedTitleClick = {},
                    )
                }
            }
        }
        composeRule.onNodeWithContentDescription("Loading title").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Back").performClick()

        composeRule.runOnIdle { state.value = MediaDetailUiState.Error() }
        composeRule.onNodeWithText("Retry").performClick()
        composeRule.onNodeWithContentDescription("Back").performClick()
        assertEquals(1, retryCalls)
        assertEquals(2, backCalls)
        composeRule.onAllNodesWithText("Add", substring = true, ignoreCase = true).assertCountEquals(0)
    }

    @Test
    fun normalMotionAnchorsTheHeroWhileTheOpaqueSheetMovesAndReverses() {
        show(state = populatedState())
        val scroll = composeRule.onNode(verticalScrollMatcher())
        val initialHeroTop =
            composeRule
                .onNodeWithTag(
                    "media-detail-anchored-hero",
                ).fetchSemanticsNode()
                .boundsInRoot.top
        val initialSheetTop =
            composeRule
                .onNodeWithTag("media-detail-sheet")
                .fetchSemanticsNode()
                .boundsInRoot.top

        scroll.performTouchInput { swipeUp() }
        composeRule.waitForIdle()

        val raisedHeroTop =
            composeRule
                .onNodeWithTag(
                    "media-detail-anchored-hero",
                ).fetchSemanticsNode()
                .boundsInRoot.top
        val raisedSheetTop =
            composeRule
                .onNodeWithTag("media-detail-sheet")
                .fetchSemanticsNode()
                .boundsInRoot.top
        assertEquals(initialHeroTop, raisedHeroTop, 1f)
        assertTrue(raisedSheetTop < initialSheetTop)

        scroll.performTouchInput { repeat(4) { swipeDown() } }
        composeRule.waitForIdle()
        val reversedSheetTop =
            composeRule
                .onNodeWithTag("media-detail-sheet")
                .fetchSemanticsNode()
                .boundsInRoot.top
        assertEquals(initialSheetTop, reversedSheetTop, 1f)
    }

    @Test
    fun reducedMotionUsesAnOrdinaryScrollingHeroInsteadOfAnAnchoredLayer() {
        show(state = populatedState(), reducedMotion = true)
        val initialTop =
            composeRule
                .onNodeWithTag("media-detail-scrolling-hero")
                .fetchSemanticsNode()
                .boundsInRoot.top

        composeRule.onNode(verticalScrollMatcher()).performTouchInput { swipeUp() }
        composeRule.waitForIdle()

        assertEquals(0f, initialTop, 1f)
        composeRule.onNodeWithTag("media-detail-scrolling-hero").assertDoesNotExist()
        composeRule.onAllNodesWithText("Add", substring = true, ignoreCase = true).assertCountEquals(0)
    }

    @Test
    fun compactLargeTextScrollsAndReversedGesturesKeepBackAndContentReachable() {
        imageLoader = ImageLoader.Builder(activityRule.activity).build()
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 2f)) {
                MioTheme(reducedMotionOverride = true) {
                    MioImageLoaderProvider(requireNotNull(imageLoader)) {
                        MediaDetailScreen(
                            modifier = Modifier.width(320.dp).height(852.dp),
                            state = populatedState(),
                            onBack = {},
                            onRetry = {},
                            onRelatedTitleClick = {},
                        )
                    }
                }
            }
        }

        composeRule.onNode(verticalScrollMatcher()).performTouchInput {
            swipeUp()
            swipeDown()
            swipeUp()
            swipeDown()
        }
        composeRule.onNode(verticalScrollMatcher()).performTouchInput {
            swipeUp()
            swipeUp()
            swipeUp()
        }
        composeRule.onNodeWithText("Related titles").assertIsDisplayed()
        composeRule.onNodeWithText("Related title").assertIsDisplayed()
        composeRule.onNode(verticalScrollMatcher()).performTouchInput {
            repeat(5) { swipeDown() }
        }
        val back = composeRule.onNodeWithContentDescription("Back").fetchSemanticsNode()
        assertTrue(back.boundsInRoot.width >= with(composeRule.density) { 48.dp.toPx() })
    }

    private fun show(
        state: MediaDetailUiState,
        onBack: () -> Unit = {},
        onRetry: () -> Unit = {},
        onRelatedTitleClick: (String) -> Unit = {},
        reducedMotion: Boolean = false,
    ) {
        imageLoader = ImageLoader.Builder(activityRule.activity).build()
        composeRule.setContent {
            MioTheme(reducedMotionOverride = reducedMotion) {
                MioImageLoaderProvider(requireNotNull(imageLoader)) {
                    MediaDetailScreen(
                        state = state,
                        onBack = onBack,
                        onRetry = onRetry,
                        onRelatedTitleClick = onRelatedTitleClick,
                    )
                }
            }
        }
    }

    private fun populatedState(): MediaDetailUiState.Content =
        MediaDetailUiState.Content(
            media =
                MediaDetailUiModel(
                    id = "media-1",
                    title = "Readable title",
                    type = MediaDetailMediaType.ANIME,
                    format = MediaDetailFormat.TV,
                    publicationStatus = MediaDetailPublicationStatus.RELEASING,
                    synopsis =
                        "A synopsis that is long enough to exercise the measured four-line expansion behavior. "
                            .repeat(
                                4,
                            ),
                    posterReference = null,
                    bannerReference = null,
                    totals = MediaDetailTotalsUiModel(episodes = 12, chapters = null, volumes = null),
                    genres = listOf("Adventure", "Drama"),
                    releaseYear = 2026,
                    scorePercent = 87,
                ),
            relatedTitles =
                listOf(
                    MediaDetailRelatedTitleUiModel(
                        id = "related-1",
                        title = "Related title",
                        type = MediaDetailMediaType.MANGA,
                        posterReference = null,
                    ),
                ),
            collection = null,
        )
}

private fun verticalScrollMatcher(): SemanticsMatcher =
    SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange)

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
