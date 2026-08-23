package io.morgan.mio.feature.today.impl

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import coil3.ImageLoader
import io.morgan.mio.core.designsystem.accessibility.MioContrast
import io.morgan.mio.core.designsystem.accessibility.contrastRatioAgainst
import io.morgan.mio.core.designsystem.theme.MioTheme
import io.morgan.mio.core.ui.image.MioImageLoaderProvider
import io.morgan.mio.feature.today.impl.component.TODAY_NOW_LOWER_SCRIM_ALPHA
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
class TodayScreenTest {
    private val activityRule = TodayScreenActivityRule()
    private var imageLoader: ImageLoader? = null

    @get:Rule
    val composeRule = AndroidComposeTestRule(activityRule) { rule -> rule.activity }

    @After
    fun closeImageLoader() {
        imageLoader?.shutdown()
    }

    @Test
    fun populatedCardHasHeadingsCopyOneButtonAndOneNavigationCallback() {
        var callbackCount = 0
        var callbackId: String? = null
        show(
            state = TodayUiState.Content(populatedNow()),
            onMediaDetail = { id ->
                callbackCount += 1
                callbackId = id
            },
        )

        composeRule.onNodeWithText("Today").assertIsDisplayed()
        composeRule.onNodeWithText("Now").assertIsDisplayed()
        composeRule.onNodeWithText("Anime").assertIsDisplayed()
        composeRule.onNodeWithText("Readable title").assertIsDisplayed()
        composeRule.onNodeWithText("Next episode 9").assertIsDisplayed()
        composeRule.onNodeWithText("4 episodes ready").assertIsDisplayed()
        composeRule.onNodeWithText("Episode 8 of 12").assertIsDisplayed()

        val card = composeRule.onNode(hasClickAction())
        card.assertIsDisplayed().assertHasClickAction().performClick()
        composeRule.onAllNodes(hasClickAction()).assertCountEquals(1)

        assertEquals(1, callbackCount)
        assertEquals("media-1", callbackId)
        val cardSemantics = card.fetchSemanticsNode().config
        assertEquals(Role.Button, cardSemantics[SemanticsProperties.Role])
        assertEquals("Open Readable title details", cardSemantics[SemanticsActions.OnClick].label)
        assertEquals(
            listOf(
                "Anime",
                "Readable title",
                "Next episode 9",
                "4 episodes ready",
                "Episode 8 of 12",
            ),
            cardSemantics[SemanticsProperties.Text].map { text -> text.text },
        )
        composeRule
            .onNode(
                SemanticsMatcher.keyIsDefined(SemanticsProperties.ContentDescription),
                useUnmergedTree = true,
            ).assertDoesNotExist()
        assertTrue(
            composeRule
                .onNodeWithText("Today")
                .fetchSemanticsNode()
                .config
                .contains(SemanticsProperties.Heading),
        )
        assertTrue(
            composeRule
                .onNodeWithText("Now")
                .fetchSemanticsNode()
                .config
                .contains(SemanticsProperties.Heading),
        )
        listOf(
            "+1",
            "−1",
            "-1",
            "Add to library",
            "Change status",
            "New",
            "Caught up",
            "Explore Discover",
            "Retry",
            "Snackbar",
        ).forEach { forbidden ->
            composeRule.onAllNodesWithText(forbidden, substring = true, ignoreCase = true).assertCountEquals(0)
        }
    }

    @Test
    fun unknownTotalUsesUnitSpecificCopyWithoutAnOfSuffix() {
        show(
            TodayUiState.Content(
                populatedNow(
                    progressUnit = TodayProgressUnit.CHAPTER,
                    progress = 41,
                    nextUnit = 42,
                    total = null,
                    availableUnitCount = 3,
                ),
            ),
        )

        composeRule.onNodeWithText("Next chapter 42").assertIsDisplayed()
        composeRule.onNodeWithText("3 chapters ready").assertIsDisplayed()
        composeRule.onNodeWithText("Chapter 41").assertIsDisplayed()
        composeRule.onAllNodesWithText("of", substring = true).assertCountEquals(0)
    }

    @Test
    fun loadingHasNeutralSkeletonSemanticsAndEmptyHasNoNowClaim() {
        val state = show(TodayUiState.Loading)
        composeRule.onNodeWithText("Today").assertIsDisplayed()
        composeRule.onNodeWithText("Now").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Loading Today").assertIsDisplayed()
        composeRule.onAllNodesWithText("Open", substring = true).assertCountEquals(0)

        composeRule.runOnIdle { state.value = TodayUiState.Empty }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Today").assertIsDisplayed()
        composeRule.onAllNodesWithText("Now").assertCountEquals(0)
        listOf("Ready", "Continue", "unavailable", "offline", "error", "caught up").forEach { forbidden ->
            composeRule.onAllNodesWithText(forbidden, substring = true, ignoreCase = true).assertCountEquals(0)
        }
    }

    @Test
    fun missingArtworkPreservesTheCardBoundsAndLargeTextRemainsScrollable() {
        var contentDensity: Density? = null
        show(
            state = TodayUiState.Content(populatedNow(posterReference = null, bannerReference = null)),
            modifier = Modifier.width(320.dp).height(852.dp),
            fontScale = 2f,
            onDensity = { contentDensity = it },
        )
        val missingArtworkCard = composeRule.onNode(hasClickAction(), useUnmergedTree = true)
        val initialBounds = missingArtworkCard.fetchSemanticsNode().boundsInRoot
        assertTrue(
            "bounds=$initialBounds density=$contentDensity",
            initialBounds.height >= with(requireNotNull(contentDensity)) { 300.dp.toPx() },
        )

        composeRule.onNode(hasScrollAction()).assertIsDisplayed()
        composeRule.onNodeWithText("Episode 8 of 12").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun artworkChangesPreserveTheReferenceCardBounds() {
        val state = show(TodayUiState.Content(populatedNow()))
        val card = composeRule.onNode(hasClickAction())
        val populatedBounds = card.fetchSemanticsNode().boundsInRoot

        composeRule.runOnIdle {
            state.value =
                TodayUiState.Content(
                    populatedNow(posterReference = null, bannerReference = null),
                )
        }
        composeRule.waitForIdle()
        val missingBounds = card.fetchSemanticsNode().boundsInRoot

        assertEquals(populatedBounds, missingBounds)
        assertTrue(populatedBounds.height >= with(composeRule.density) { 332.dp.toPx() })
    }

    @Test
    fun lowerScrimMeetsTheRequiredContrastForWorstCaseArtwork() {
        val whiteArtwork = Color.White
        val blackArtwork = Color.Black
        val scrimOverWhite = Color.Black.copy(alpha = TODAY_NOW_LOWER_SCRIM_ALPHA).compositeOver(whiteArtwork)
        val scrimOverBlack = Color.Black.copy(alpha = TODAY_NOW_LOWER_SCRIM_ALPHA).compositeOver(blackArtwork)

        assertTrue(Color.White.contrastRatioAgainst(scrimOverWhite) >= MioContrast.NormalText)
        assertTrue(Color.White.contrastRatioAgainst(scrimOverBlack) >= MioContrast.NormalText)
        assertTrue(Color.White.contrastRatioAgainst(scrimOverWhite) >= MioContrast.LargeTextAndEssentialIcons)
        assertTrue(Color.White.contrastRatioAgainst(scrimOverBlack) >= MioContrast.LargeTextAndEssentialIcons)
    }

    private fun show(
        state: TodayUiState,
        onMediaDetail: (String) -> Unit = {},
        modifier: Modifier = Modifier,
        fontScale: Float = 1f,
        onDensity: ((Density) -> Unit)? = null,
    ): MutableState<TodayUiState> {
        val stateHolder = mutableStateOf(state)
        imageLoader = ImageLoader.Builder(activityRule.activity).build()
        composeRule.setContent {
            val density = LocalDensity.current
            SideEffect { onDensity?.invoke(density) }
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                MioTheme {
                    MioImageLoaderProvider(requireNotNull(imageLoader)) {
                        TodayScreen(
                            modifier = modifier,
                            state = stateHolder.value,
                            onMediaDetail = onMediaDetail,
                        )
                    }
                }
            }
        }
        return stateHolder
    }

    private fun populatedNow(
        progressUnit: TodayProgressUnit = TodayProgressUnit.EPISODE,
        progress: Int = 8,
        nextUnit: Int = 9,
        total: Int? = 12,
        availableUnitCount: Int = 4,
        posterReference: String? = "poster://one",
        bannerReference: String? = "banner://one",
    ): TodayNowUiModel =
        TodayNowUiModel(
            mediaId = "media-1",
            mediaType = TodayMediaType.ANIME,
            title = "Readable title",
            posterReference = posterReference,
            bannerReference = bannerReference,
            progress = progress,
            progressUnit = progressUnit,
            total = total,
            nextUnit = nextUnit,
            availableUnitCount = availableUnitCount,
        )
}

@Suppress("DEPRECATION")
class TodayScreenActivityRule : TestRule {
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
