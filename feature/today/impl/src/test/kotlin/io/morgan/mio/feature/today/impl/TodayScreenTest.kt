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
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
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
import org.junit.Assert.assertFalse
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
    fun bodyPointerProtectsTheCardUntilCancellationWithoutOpeningDetail() {
        val actions = mutableListOf<TodayAction>()
        val opened = mutableListOf<String>()
        show(state = populatedState(), onAction = actions::add, onMediaDetail = opened::add)
        val title = composeRule.onNodeWithText("Readable title")
        title.performTouchInput { down(center) }
        composeRule.runOnIdle {
            assertTrue(actions.any { it is TodayAction.InteractionChanged && it.pressed })
            assertTrue(opened.isEmpty())
        }
        title.performTouchInput { cancel() }
        composeRule.runOnIdle {
            assertFalse(actions.filterIsInstance<TodayAction.InteractionChanged>().last().pressed)
            assertTrue(opened.isEmpty())
        }
    }

    @Test
    fun expiredClaimsDisappearWithoutMovingTheNowTitle() {
        val initial = populatedState(newRecommendations = emptyList())
        val state = show(initial)
        val title = composeRule.onNodeWithText("Readable title")
        val before = title.fetchSemanticsNode().boundsInRoot.topLeft
        composeRule.onNodeWithText("Next episode 9").assertIsDisplayed()
        composeRule.onNodeWithText("4 episodes ready").assertIsDisplayed()
        composeRule.runOnIdle {
            state.value = initial.copy(now = initial.now.copy(nextUnit = null, availableUnitCount = null))
        }
        assertEquals(before, title.fetchSemanticsNode().boundsInRoot.topLeft)
        composeRule.onAllNodesWithText("Next episode 9").assertCountEquals(0)
        composeRule.onAllNodesWithText("4 episodes ready").assertCountEquals(0)
    }

    @Test
    fun expiredNewClaimsDoNotMoveProgressOrFollowingRows() {
        val first = previewNewRecommendations().first().copy(progress = 7)
        val initial = populatedState(newRecommendations = listOf(first, previewNewRecommendations()[1]))
        val state = show(initial)
        val progress = composeRule.onNodeWithContentDescription("Episode 7 of 12")
        val following = composeRule.onNodeWithText("A Longer Evening")
        val progressBefore = progress.fetchSemanticsNode().boundsInRoot
        val followingBefore = following.fetchSemanticsNode().boundsInRoot
        composeRule.onAllNodesWithText("2 episodes ready").assertCountEquals(2)
        composeRule.runOnIdle {
            state.value =
                initial.copy(
                    newRecommendations =
                        listOf(
                            first.copy(releaseAge = null, nextUnit = null, availableUnitCount = null),
                            initial.newRecommendations[1],
                        ),
                )
        }
        assertEquals(progressBefore, progress.fetchSemanticsNode().boundsInRoot)
        assertEquals(followingBefore, following.fetchSemanticsNode().boundsInRoot)
        composeRule.onAllNodesWithText("2 episodes ready").assertCountEquals(1)
    }

    @Test
    fun populatedShowsNowThreeNewRowsRemainingAndOneCallbackPerDetailTarget() {
        val clickedIds = mutableListOf<String>()
        var density: Density? = null
        show(
            state = populatedState(),
            onMediaDetail = clickedIds::add,
            onDensity = { density = it },
        )

        val targets = composeRule.onAllNodes(hasClickAction())
        targets.assertCountEquals(5)
        val currentDensity = requireNotNull(density)
        composeRule.onNodeWithText("Readable title").assertIsDisplayed()

        composeRule.onNodeWithText("Today").assertIsDisplayed()
        composeRule.onNodeWithText("Now").assertIsDisplayed()
        composeRule.onNodeWithText("New").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Readable title").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("A Gentle Beginning").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("A Longer Evening").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("A Distant Return").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("2 other titles are ready when you are").performScrollTo().assertIsDisplayed()
        composeRule.onAllNodesWithText("Next episode 9").assertCountEquals(4)
        composeRule.onAllNodesWithText("2 episodes ready").assertCountEquals(3)
        composeRule.onAllNodesWithContentDescription("Episode 8 of 12", substring = true).assertCountEquals(4)
        composeRule.onAllNodesWithText("New · under one hour ago").assertCountEquals(1)
        composeRule.onAllNodesWithText("New · 3 hours ago").assertCountEquals(1)
        composeRule.onAllNodesWithText("New · 7 days ago").assertCountEquals(1)
        listOf("A Gentle Beginning", "A Longer Evening", "A Distant Return").forEach { title ->
            val row = composeRule.onNodeWithText(title).performScrollTo()
            assertTrue(
                row.fetchSemanticsNode().boundsInRoot.height >= with(currentDensity) { 104.dp.toPx() },
            )
        }

        composeRule
            .onNodeWithText("A Gentle Beginning")
            .performScrollTo()
            .assertHasClickAction()
            .performClick()
        composeRule
            .onNodeWithText("A Distant Return")
            .performScrollTo()
            .assertHasClickAction()
            .performClick()
        assertEquals(listOf("new-one", "new-three"), clickedIds)

        val firstNewSemantics = targets[1].fetchSemanticsNode().config
        assertEquals(Role.Button, firstNewSemantics[SemanticsProperties.Role])
        assertEquals("Open A Gentle Beginning details", firstNewSemantics[SemanticsActions.OnClick].label)
        assertTrue(firstNewSemantics[SemanticsProperties.Text].any { text -> text.text == "Anime" })
        assertTrue(firstNewSemantics[SemanticsProperties.Text].any { text -> text.text == "A Gentle Beginning" })
        assertTrue(firstNewSemantics[SemanticsProperties.Text].any { text -> text.text.contains("New") })
        assertTrue(firstNewSemantics[SemanticsProperties.Text].any { text -> text.text == "Next episode 9" })
        assertTrue(firstNewSemantics[SemanticsProperties.Text].any { text -> text.text.contains("2 episodes ready") })
        assertTrue(firstNewSemantics[SemanticsProperties.ContentDescription].contains("Episode 8 of 12"))

        composeRule.onNode(hasScrollAction()).assertIsDisplayed()
        listOf(
            "+1",
            "−1",
            "-1",
            "Add to library",
            "Change status",
            "Explore Discover",
            "Retry",
            "Undo",
            "Achievement",
        ).forEach { forbidden ->
            composeRule.onAllNodesWithText(forbidden, substring = true, ignoreCase = true).assertCountEquals(0)
        }
    }

    @Test
    fun remainingSummaryOpensTheReadyLibraryAction() {
        var remainingReadyClicks = 0
        show(
            state =
                TodayUiState.Populated(
                    now = previewNow(),
                    newRecommendations = emptyList(),
                    remainingReadyCount = 1,
                ),
            onRemainingReady = { remainingReadyClicks += 1 },
        )

        val summary =
            composeRule
                .onNodeWithText("1 other title is ready when you are")
                .performScrollTo()
                .assertIsDisplayed()
                .assertHasClickAction()
        composeRule.onAllNodes(hasClickAction()).assertCountEquals(2)
        val summarySemantics = summary.fetchSemanticsNode().config
        assertEquals(Role.Button, summarySemantics[SemanticsProperties.Role])
        summary.performClick()
        assertEquals(1, remainingReadyClicks)
    }

    @Test
    fun caughtUpReceiptOffersDiscoverAndFutureReleaseActions() {
        val clickedIds = mutableListOf<String>()
        var discoverClicks = 0
        var density: Density? = null
        show(
            state = caughtUpState(),
            onMediaDetail = clickedIds::add,
            onExploreDiscover = { discoverClicks += 1 },
            onDensity = { density = it },
        )

        composeRule.onNodeWithText("You are caught up").assertIsDisplayed()
        composeRule.onNodeWithText("Nothing needs your attention right now.").assertIsDisplayed()
        composeRule.onNodeWithText("Next release").assertIsDisplayed()
        composeRule.onNodeWithText("The Next Chapter").assertIsDisplayed()
        composeRule.onNodeWithText("Next chapter 42").assertIsDisplayed()
        composeRule.onNodeWithText("In 2 days").assertIsDisplayed()
        val exploreTarget = composeRule.onNodeWithText("Explore Discover").assertIsDisplayed().assertHasClickAction()
        val futureTarget = composeRule.onNodeWithText("The Next Chapter").fetchSemanticsNode()
        composeRule.onAllNodes(hasClickAction()).assertCountEquals(2)
        assertTrue(futureTarget.boundsInRoot.height >= with(requireNotNull(density)) { 48.dp.toPx() })
        assertEquals(Role.Button, futureTarget.config[SemanticsProperties.Role])
        assertEquals(
            "Open The Next Chapter details",
            futureTarget.config[SemanticsActions.OnClick].label,
        )
        exploreTarget.performClick()
        assertEquals(1, discoverClicks)
        composeRule.onNodeWithText("The Next Chapter").performClick()
        assertEquals(listOf("future-preview"), clickedIds)
        composeRule
            .onNode(
                SemanticsMatcher.keyIsDefined(SemanticsProperties.ContentDescription),
                useUnmergedTree = true,
            ).assertDoesNotExist()
    }

    @Test
    fun accountRefreshIssueOffersOnlyTheReadOnlyRefreshRetry() {
        var refreshRequests = 0
        show(
            state =
                caughtUpState().copy(
                    accountIssue = TodayUiState.AccountIssueUiModel(AccountIssueKind.INVALID_REMOTE_DATA),
                ),
            onRetryRefresh = { refreshRequests += 1 },
        )

        composeRule.onNodeWithText("Saved content could not be refreshed").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Retry refresh").performScrollTo().performClick()
        assertEquals(1, refreshRequests)
        composeRule.onAllNodesWithText("Reconnect").assertCountEquals(0)
        composeRule.onAllNodesWithText("Retry sync").assertCountEquals(0)
    }

    @Test
    fun singularUnitAndRelativeTimeCopyIsLocalizedForNewAndFutureRows() {
        val newHour =
            previewNew(
                mediaId = "new-hour",
                title = "New hour",
                releaseAge = TodayRelativeTime(1, TodayRelativeTimeUnit.HOURS),
            ).copy(
                progressUnit = TodayProgressUnit.VOLUME,
                availableUnitCount = 1,
            )
        val newDay =
            previewNew(
                mediaId = "new-day",
                title = "New day",
                releaseAge = TodayRelativeTime(1, TodayRelativeTimeUnit.DAYS),
            ).copy(progressUnit = TodayProgressUnit.CHAPTER)
        val state =
            show(
                populatedState(
                    newRecommendations = listOf(newHour, newDay),
                    remainingReadyCount = 0,
                ),
            )

        composeRule.onNodeWithText("New · 1 hour ago").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("New · 1 day ago").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Next volume 9").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("1 volume ready").performScrollTo().assertIsDisplayed()
        composeRule
            .onNodeWithContentDescription(
                "Volume 8 of 12",
                substring = true,
            ).performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithText("Next chapter 9").performScrollTo().assertIsDisplayed()

        composeRule.runOnIdle {
            state.value =
                TodayUiState.CaughtUp(
                    TodayCaughtUpUiModel(
                        artwork = emptyList(),
                        nextRelease =
                            TodayNextReleaseUiModel(
                                mediaId = "future-hour",
                                mediaType = TodayMediaType.ANIME,
                                title = "Future hour",
                                posterReference = null,
                                progressUnit = TodayProgressUnit.EPISODE,
                                unitNumber = 2,
                                waitUntilRelease = TodayRelativeTime(1, TodayRelativeTimeUnit.HOURS),
                            ),
                    ),
                )
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("In 1 hour").assertIsDisplayed()

        composeRule.runOnIdle {
            state.value =
                TodayUiState.CaughtUp(
                    TodayCaughtUpUiModel(
                        artwork = emptyList(),
                        nextRelease =
                            TodayNextReleaseUiModel(
                                mediaId = "future-day",
                                mediaType = TodayMediaType.MANGA,
                                title = "Future day",
                                posterReference = null,
                                progressUnit = TodayProgressUnit.CHAPTER,
                                unitNumber = 2,
                                waitUntilRelease = TodayRelativeTime(1, TodayRelativeTimeUnit.DAYS),
                            ),
                    ),
                )
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("In 1 day").assertIsDisplayed()
    }

    @Test
    fun emptyCaughtUpReceiptOffersDiscoverWithoutArtworkOrFakeRelease() {
        show(
            TodayUiState.CaughtUp(
                TodayCaughtUpUiModel(artwork = emptyList(), nextRelease = null),
            ),
        )
        composeRule.onNodeWithText("You are caught up").assertIsDisplayed()
        composeRule.onNodeWithText("Explore Discover").assertIsDisplayed().assertHasClickAction()
        composeRule.onAllNodes(hasClickAction()).assertCountEquals(1)
        composeRule.onAllNodesWithText("Next release").assertCountEquals(0)
        composeRule
            .onNode(
                SemanticsMatcher.keyIsDefined(SemanticsProperties.ContentDescription),
                useUnmergedTree = true,
            ).assertDoesNotExist()
    }

    @Test
    fun loadingHasSkeletonAndLargeTextStatesRemainScrollable() {
        val state = show(TodayUiState.Loading())
        composeRule.onNodeWithText("Today").assertIsDisplayed()
        composeRule.onNodeWithText("Now").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Loading Today").assertIsDisplayed()

        composeRule.runOnIdle { state.value = populatedState() }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("A Distant Return").performScrollTo().assertIsDisplayed()
        composeRule.onNode(hasScrollAction()).assertIsDisplayed()
    }

    @Test
    fun missingArtworkAndCaughtUpLargeTextGrowWithoutClipping() {
        var density: Density? = null
        val state =
            show(
                state =
                    populatedState(
                        now = previewNow(posterReference = null, bannerReference = null),
                        newRecommendations =
                            previewNewRecommendations().mapIndexed { index, recommendation ->
                                if (index == 0) recommendation.copy(posterReference = null) else recommendation
                            },
                    ),
                modifier = Modifier.width(320.dp).height(852.dp),
                fontScale = 2f,
                onDensity = { density = it },
            )
        composeRule.onNodeWithText("Readable title").assertIsDisplayed()
        composeRule.onNodeWithText("A Distant Return").performScrollTo().assertIsDisplayed()

        composeRule.runOnIdle { state.value = caughtUpState() }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("The Next Chapter").performScrollTo().assertIsDisplayed()
        composeRule.onNode(hasScrollAction()).assertIsDisplayed()
    }

    @Test
    fun lowerScrimMeetsTheRequiredContrastForWorstCaseArtwork() {
        val scrimOverWhite = Color.Black.copy(alpha = TODAY_NOW_LOWER_SCRIM_ALPHA).compositeOver(Color.White)
        val scrimOverBlack = Color.Black.copy(alpha = TODAY_NOW_LOWER_SCRIM_ALPHA).compositeOver(Color.Black)
        assertTrue(Color.White.contrastRatioAgainst(scrimOverWhite) >= MioContrast.NormalText)
        assertTrue(Color.White.contrastRatioAgainst(scrimOverBlack) >= MioContrast.NormalText)
        assertTrue(Color.White.contrastRatioAgainst(scrimOverWhite) >= MioContrast.LargeTextAndEssentialIcons)
        assertTrue(Color.White.contrastRatioAgainst(scrimOverBlack) >= MioContrast.LargeTextAndEssentialIcons)
    }

    private fun show(
        state: TodayUiState,
        onMediaDetail: (String) -> Unit = {},
        onRemainingReady: () -> Unit = {},
        onExploreDiscover: () -> Unit = {},
        onAction: (TodayAction) -> Unit = {},
        providerLabel: String? = null,
        onReconnect: () -> Unit = {},
        onRetryRefresh: () -> Unit = {},
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
                            onRemainingReady = onRemainingReady,
                            onExploreDiscover = onExploreDiscover,
                            onAction = onAction,
                            providerLabel = providerLabel,
                            onReconnect = onReconnect,
                            onRetryRefresh = onRetryRefresh,
                        )
                    }
                }
            }
        }
        return stateHolder
    }

    private fun populatedState(
        now: TodayNowUiModel = previewNow(),
        newRecommendations: List<TodayNewUiModel> = previewNewRecommendations(),
        remainingReadyCount: Int = 2,
    ): TodayUiState.Populated = TodayUiState.Populated(now, newRecommendations, remainingReadyCount)

    private fun previewNow(
        posterReference: String? = "poster://one",
        bannerReference: String? = "banner://one",
    ): TodayNowUiModel =
        TodayNowUiModel(
            mediaId = "media-1",
            mediaType = TodayMediaType.ANIME,
            title = "Readable title",
            posterReference = posterReference,
            bannerReference = bannerReference,
            progress = 8,
            progressUnit = TodayProgressUnit.EPISODE,
            total = 12,
            nextUnit = 9,
            availableUnitCount = 4,
        )

    private fun previewNewRecommendations(): List<TodayNewUiModel> =
        listOf(
            previewNew("new-one", "A Gentle Beginning", TodayRelativeTime(0, TodayRelativeTimeUnit.UNDER_HOUR)),
            previewNew("new-two", "A Longer Evening", TodayRelativeTime(3, TodayRelativeTimeUnit.HOURS)),
            previewNew("new-three", "A Distant Return", TodayRelativeTime(7, TodayRelativeTimeUnit.DAYS)),
        )

    private fun previewNew(
        mediaId: String,
        title: String,
        releaseAge: TodayRelativeTime,
    ): TodayNewUiModel =
        TodayNewUiModel(
            mediaId = mediaId,
            mediaType = TodayMediaType.ANIME,
            title = title,
            posterReference = "poster://$mediaId",
            progress = 8,
            progressUnit = TodayProgressUnit.EPISODE,
            total = 12,
            nextUnit = 9,
            availableUnitCount = 2,
            releaseAge = releaseAge,
        )

    private fun caughtUpState(): TodayUiState.CaughtUp =
        TodayUiState.CaughtUp(
            TodayCaughtUpUiModel(
                artwork =
                    (1..6).map { index ->
                        TodayCaughtUpArtworkUiModel(
                            mediaId = "receipt-$index",
                            title = "Receipt title $index",
                            reference = if (index == 6) null else "poster://receipt-$index",
                        )
                    },
                nextRelease =
                    TodayNextReleaseUiModel(
                        mediaId = "future-preview",
                        mediaType = TodayMediaType.MANGA,
                        title = "The Next Chapter",
                        posterReference = "poster://future",
                        progressUnit = TodayProgressUnit.CHAPTER,
                        unitNumber = 42,
                        waitUntilRelease = TodayRelativeTime(2, TodayRelativeTimeUnit.DAYS),
                    ),
            ),
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
