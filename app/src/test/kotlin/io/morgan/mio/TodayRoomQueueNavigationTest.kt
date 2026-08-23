package io.morgan.mio

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import dev.zacsweers.metro.createDynamicGraphFactory
import io.morgan.mio.core.database.MioDatabase
import io.morgan.mio.core.database.entity.AccountProviderIdEntity
import io.morgan.mio.core.database.entity.CollectionEntryEntity
import io.morgan.mio.core.database.entity.MediaEntity
import io.morgan.mio.core.database.entity.ReleaseEntity
import io.morgan.mio.core.database.entity.SELECTED_ACCOUNT_KEY
import io.morgan.mio.core.database.entity.SelectedAccountEntity
import io.morgan.mio.core.database.model.StoredCollectionStatus
import io.morgan.mio.core.database.model.StoredConnectedProvider
import io.morgan.mio.core.database.model.StoredMediaFormat
import io.morgan.mio.core.database.model.StoredMediaPublicationStatus
import io.morgan.mio.core.database.model.StoredMediaType
import io.morgan.mio.core.database.model.StoredProgressUnit
import io.morgan.mio.core.database.model.StoredReleaseTimeKind
import io.morgan.mio.data.collection.CollectionRefreshRequester
import io.morgan.mio.data.collection.DefaultCollectionRepository
import io.morgan.mio.data.collection.local.RoomCollectionLocalDataSource
import io.morgan.mio.data.media.DefaultMediaRepository
import io.morgan.mio.data.media.MediaRefreshRequester
import io.morgan.mio.data.media.local.RoomMediaLocalDataSource
import io.morgan.mio.data.provider.api.ProviderKey
import io.morgan.mio.di.AccountGraphRequest
import io.morgan.mio.di.AppGraph
import io.morgan.mio.di.TestGraphBindings
import io.morgan.mio.di.TestGraphFixture
import io.morgan.mio.domain.model.AccountId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
@Suppress("DEPRECATION")
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TodayRoomQueueNavigationTest {
    private val activityRule = RobolectricActivityRule()

    @get:Rule
    val composeRule = AndroidComposeTestRule(activityRule) { rule -> rule.activity }

    @After
    fun resetMainDispatcher() {
        Dispatchers.resetMain()
    }

    @Test
    fun roomReplacementRecommendsNewestEntryAndOpensCachedFocusedDetail() {
        val scheduler = TestCoroutineScheduler()
        Dispatchers.setMain(StandardTestDispatcher(scheduler))
        val accountId = AccountId("account-1")
        val fixture = TestGraphFixture(activityRule.activity, scheduler)
        val graph =
            createDynamicGraphFactory<AppGraph.Factory>(productionBindings(fixture))
                .create(activityRule.activity)
        val handle =
            graph.accountGraphLifecycle.create(
                AccountGraphRequest(
                    accountId = accountId,
                    providerBinding =
                        io.morgan.mio.core.testing.provider
                            .providerBindingFixture(ProviderKey.ANILIST),
                    accountRefreshRequester =
                        io.morgan.mio.data.account
                            .AccountRefreshRequester {},
                    collectionRefreshRequester = CollectionRefreshRequester {},
                ),
            )

        try {
            seedAccount(fixture.database, accountId)
            seedMediaAndQueue(
                fixture.database,
                mediaId = "media-first",
                title = "First cached title",
                updatedAt = FIRST_UPDATED_AT,
            )
            showAuthenticated(graph, handle)

            waitForText(scheduler, "First cached title")
            composeRule.onNodeWithText("Next episode 2").assertIsDisplayed()

            seedMediaAndQueue(
                database = fixture.database,
                mediaId = "media-newer",
                title = "Newer cached title",
                updatedAt = NEWER_UPDATED_AT,
            )
            waitForText(scheduler, "Newer cached title")
            composeRule.onNodeWithText("Newer cached title").performClick()
            waitForText(scheduler, "In library")
            waitForText(scheduler, "Synopsis")
            composeRule.waitForIdle()

            composeRule.onNodeWithText("Newer cached title").assertIsDisplayed()
            composeRule.onNodeWithText("In library").assertIsDisplayed()
            composeRule.onNodeWithText("Synopsis").performScrollTo().assertIsDisplayed()
            composeRule.onAllNodesWithText("Today").assertCountEquals(0)
            composeRule.onAllNodesWithText("Library").assertCountEquals(0)
            composeRule.onAllNodesWithText("Discover").assertCountEquals(0)
            composeRule.onAllNodesWithText("You").assertCountEquals(0)
        } finally {
            graph.accountGraphLifecycle.destroy()
            fixture.close()
        }
    }

    private fun showAuthenticated(
        graph: AppGraph,
        handle: io.morgan.mio.di.AccountGraphHandle,
    ) {
        val accountViewModelFactory = handle.withGraph { accountGraph -> accountGraph.metroViewModelFactory }
        composeRule.setContent {
            MioApp(
                root = AppRoot.AUTHENTICATED,
                navigationRegistry = graph.navigationRegistry,
                imageLoader = graph.imageLoader,
                viewModelFactory = accountViewModelFactory,
            )
        }
    }

    private fun waitForText(
        scheduler: TestCoroutineScheduler,
        text: String,
    ) {
        repeat(30) {
            scheduler.runCurrent()
            composeRule.waitForIdle()
            if (composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()) return
            Thread.sleep(10)
        }
        fail("Timed out waiting for text: $text")
    }

    private fun productionBindings(fixture: TestGraphFixture): TestGraphBindings =
        TestGraphBindings(
            database = fixture.database,
            dataStore = fixture.dataStore,
            clock = fixture.clock,
            defaultDispatcher = fixture.dispatcher,
            ioDispatcher = fixture.dispatcher,
            mainDispatcher = fixture.dispatcher,
            processCoroutineScope = fixture.processCoroutineScope,
            logger = fixture.logger,
            providerCredentialStore = fixture.providerCredentialStore,
            imageLoader = fixture.imageLoader,
            workerFactory = fixture.workerFactory,
            workManagerConfiguration = fixture.workManagerConfiguration,
            accountRepository = fixture.accountRepository,
            mediaRepository =
                DefaultMediaRepository(
                    localDataSource = RoomMediaLocalDataSource(fixture.database),
                    refreshRequester = MediaRefreshRequester {},
                ),
            collectionRepository =
                DefaultCollectionRepository(
                    localDataSource = RoomCollectionLocalDataSource(fixture.database),
                    refreshRequester = CollectionRefreshRequester {},
                ),
            preferencesRepository = fixture.preferencesRepository,
            accountSessionRepository = fixture.accountSessionRepository,
        )

    private fun seedAccount(
        database: MioDatabase,
        accountId: AccountId,
    ) = runBlocking {
        database.selectedAccountDao().replaceSelectedAccount(
            account =
                SelectedAccountEntity(
                    selectionKey = SELECTED_ACCOUNT_KEY,
                    accountId = accountId.value,
                    connectedProvider = StoredConnectedProvider.ANILIST,
                    displayName = "Test account",
                    avatarReference = null,
                ),
            providerId =
                AccountProviderIdEntity(
                    accountId = accountId.value,
                    provider = StoredConnectedProvider.ANILIST,
                    remoteAccountId = "remote-account",
                ),
        )
    }

    private fun seedMediaAndQueue(
        database: MioDatabase,
        mediaId: String,
        title: String,
        updatedAt: Instant,
    ) = runBlocking {
        database.inTransaction {
            database.mediaDao().upsertMedia(
                listOf(
                    MediaEntity(
                        mediaId = mediaId,
                        mediaType = StoredMediaType.ANIME,
                        title = title,
                        format = StoredMediaFormat.TV,
                        publicationStatus = StoredMediaPublicationStatus.RELEASING,
                        synopsis = "Cached synopsis",
                        posterReference = "cached://poster/$mediaId",
                        bannerReference = "cached://banner/$mediaId",
                        episodeTotal = 12,
                        chapterTotal = null,
                        volumeTotal = null,
                    ),
                ),
            )
            database.collectionEntryDao().upsertEntries(
                listOf(
                    CollectionEntryEntity(
                        collectionEntryId = "entry-$mediaId",
                        accountId = "account-1",
                        mediaId = mediaId,
                        progressUnit = StoredProgressUnit.EPISODE,
                        remoteStatus = StoredCollectionStatus.CURRENT,
                        remoteProgress = 1,
                        remoteTotal = 12,
                        remoteUpdatedAtUtcMs = updatedAt,
                        effectiveStatus = StoredCollectionStatus.CURRENT,
                        effectiveProgress = 1,
                        effectiveTotal = 12,
                        effectiveUpdatedAtUtcMs = updatedAt,
                    ),
                ),
            )
            database.releaseDao().upsertReleases(
                listOf(
                    ReleaseEntity(
                        mediaId = mediaId,
                        progressUnit = StoredProgressUnit.EPISODE,
                        unitNumber = 2,
                        timeKind = StoredReleaseTimeKind.TRUSTED,
                        releaseInstantUtcMs = FIRST_UPDATED_AT,
                    ),
                ),
            )
        }
    }

    private companion object {
        val FIRST_UPDATED_AT: Instant = Instant.parse("2026-08-11T10:00:00Z")
        val NEWER_UPDATED_AT: Instant = Instant.parse("2026-08-11T11:00:00Z")
    }
}
