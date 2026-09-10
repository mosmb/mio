package io.morgan.mio

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
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
class ProgressRoomNavigationTest {
    private val activityRule = RobolectricActivityRule()

    @get:Rule
    val composeRule = AndroidComposeTestRule(activityRule) { rule -> rule.activity }

    @After
    fun resetMainDispatcher() {
        Dispatchers.resetMain()
    }

    @Test
    fun realTodayProgressCommitsOneGroupAndUndoRestoresItAcrossTheLatestReceipt() {
        val scheduler = TestCoroutineScheduler()
        Dispatchers.setMain(StandardTestDispatcher(scheduler))
        val fixture = TestGraphFixture(activityRule.activity, scheduler)
        val graph = createGraph(fixture)
        val handle = createAccountGraph(graph)

        try {
            seedFixture(fixture.database)
            showAuthenticated(graph, handle)

            waitForContentDescription(scheduler, "Increase episode to 2")
            val control = composeRule.onNodeWithContentDescription("Increase episode to 2").performScrollTo()
            val scroll = composeRule.onAllNodes(hasScrollAction())[0]
            val viewport = scroll.fetchSemanticsNode().boundsInRoot
            val center = control.fetchSemanticsNode().boundsInRoot.center
            scroll.performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, center.y - viewport.center.y) }
            composeRule.waitForIdle()
            control.performClick()
            waitForProgress(scheduler, fixture.database, 2)

            assertEquals(1, countRows(fixture.database, "pending_operation_group"))
            assertEquals(1, countRows(fixture.database, "pending_operation"))
            assertEquals(1, countRows(fixture.database, "local_progress_activity"))
            val activity =
                requireNotNull(
                    runBlocking { fixture.database.progressDao().getLatestActivity(ACCOUNT_ID.value) },
                )
            assertTrue(activity.operationGroupId.isNotBlank())
            assertEquals(1, activity.beforeProgress)
            assertEquals(2, activity.afterProgress)
            assertEquals(1, activity.afterRevision)
            assertEquals(BASELINE.toEpochMilliseconds(), activity.beforeUpdatedAtUtcMs.toEpochMilliseconds())
            assertEquals(
                1,
                runBlocking {
                    fixture.database
                        .progressDao()
                        .getEntryForMedia(ACCOUNT_ID.value, MEDIA_ID)
                        ?.remoteProgress
                },
            )

            waitForText(scheduler, "Undo")
            composeRule.onNodeWithText("Undo").performClick()
            waitForProgress(scheduler, fixture.database, 1)

            assertEquals(0, countRows(fixture.database, "pending_operation_group"))
            assertEquals(0, countRows(fixture.database, "pending_operation"))
            assertEquals(1, countRows(fixture.database, "local_progress_activity"))
            assertNotNull(
                runBlocking {
                    fixture.database
                        .progressDao()
                        .getLatestActivity(ACCOUNT_ID.value)
                        ?.undoneAtUtcMs
                },
            )
        } finally {
            graph.accountGraphLifecycle.destroy()
            fixture.close()
        }
    }

    @Test
    fun detailUsesTheSameRoomProgressPathAndReturnsToTodayWithoutNestedNavigation() {
        val scheduler = TestCoroutineScheduler()
        Dispatchers.setMain(StandardTestDispatcher(scheduler))
        val fixture = TestGraphFixture(activityRule.activity, scheduler)
        val graph = createGraph(fixture)
        val handle = createAccountGraph(graph)

        try {
            seedFixture(fixture.database)
            showAuthenticated(graph, handle)

            waitForText(scheduler, "Fixture title")
            composeRule.onNodeWithText("Fixture title").performClick()
            waitForText(scheduler, "In library")
            composeRule
                .onNodeWithContentDescription("Increase episode to 2")
                .performScrollTo()
                .performClick()
            waitForProgress(scheduler, fixture.database, 2)
            assertEquals(1, countRows(fixture.database, "local_progress_activity"))

            composeRule.onNodeWithContentDescription("Back").performClick()
            waitForText(scheduler, "Today")
            waitForText(scheduler, "Undo")
            assertEquals(
                2,
                runBlocking {
                    fixture.database
                        .progressDao()
                        .getEntryForMedia(ACCOUNT_ID.value, MEDIA_ID)
                        ?.effectiveProgress
                },
            )
            assertEquals(1, countRows(fixture.database, "local_progress_activity"))
        } finally {
            graph.accountGraphLifecycle.destroy()
            fixture.close()
        }
    }

    private fun createGraph(fixture: TestGraphFixture): AppGraph =
        createDynamicGraphFactory<AppGraph.Factory>(
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
            ),
        ).create(activityRule.activity)

    private fun createAccountGraph(graph: AppGraph): io.morgan.mio.di.AccountGraphHandle =
        graph.accountGraphLifecycle.create(
            AccountGraphRequest(
                accountId = ACCOUNT_ID,
                providerBinding =
                    io.morgan.mio.core.testing.provider
                        .providerBindingFixture(ProviderKey.ANILIST),
                accountRefreshRequester =
                    io.morgan.mio.data.account
                        .AccountRefreshRequester {},
                collectionRefreshRequester = CollectionRefreshRequester {},
            ),
        )

    private fun showAuthenticated(
        graph: AppGraph,
        handle: io.morgan.mio.di.AccountGraphHandle,
    ) {
        composeRule.setContent {
            MioApp(
                root = AppRoot.AUTHENTICATED,
                navigationRegistry = graph.navigationRegistry,
                imageLoader = graph.imageLoader,
                viewModelFactory = handle.withGraph { accountGraph -> accountGraph.metroViewModelFactory },
            )
        }
    }

    private fun waitForText(
        scheduler: TestCoroutineScheduler,
        text: String,
    ) {
        repeat(60) {
            scheduler.runCurrent()
            composeRule.waitForIdle()
            if (composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()) return
            Thread.sleep(10)
        }
        fail("Timed out waiting for text: $text")
    }

    private fun waitForContentDescription(
        scheduler: TestCoroutineScheduler,
        description: String,
    ) {
        repeat(60) {
            scheduler.runCurrent()
            composeRule.waitForIdle()
            if (composeRule.onAllNodesWithContentDescription(description).fetchSemanticsNodes().isNotEmpty()) return
            Thread.sleep(10)
        }
        fail("Timed out waiting for content description: $description")
    }

    private fun waitForProgress(
        scheduler: TestCoroutineScheduler,
        database: MioDatabase,
        expected: Int,
    ) {
        repeat(60) {
            scheduler.runCurrent()
            composeRule.waitForIdle()
            if (
                runBlocking {
                    database.progressDao().getEntryForMedia(ACCOUNT_ID.value, MEDIA_ID)?.effectiveProgress == expected
                }
            ) {
                return
            }
            Thread.sleep(10)
        }
        fail("Timed out waiting for persisted progress: $expected")
    }

    private fun seedFixture(database: MioDatabase) =
        runBlocking {
            database.selectedAccountDao().replaceSelectedAccount(
                SelectedAccountEntity(
                    selectionKey = SELECTED_ACCOUNT_KEY,
                    accountId = ACCOUNT_ID.value,
                    connectedProvider = StoredConnectedProvider.ANILIST,
                    displayName = "Task 19 account",
                    avatarReference = null,
                ),
                AccountProviderIdEntity(
                    accountId = ACCOUNT_ID.value,
                    provider = StoredConnectedProvider.ANILIST,
                    remoteAccountId = "fixture-account",
                ),
            )
            database.inTransaction {
                database.mediaDao().upsertMedia(
                    listOf(
                        MediaEntity(
                            mediaId = MEDIA_ID,
                            mediaType = StoredMediaType.ANIME,
                            title = "Fixture title",
                            format = StoredMediaFormat.TV,
                            publicationStatus = StoredMediaPublicationStatus.RELEASING,
                            synopsis = "Fixture synopsis",
                            posterReference = null,
                            bannerReference = null,
                            episodeTotal = 12,
                            chapterTotal = null,
                            volumeTotal = null,
                        ),
                    ),
                )
                database.collectionEntryDao().upsertEntries(
                    listOf(
                        CollectionEntryEntity(
                            collectionEntryId = ENTRY_ID,
                            accountId = ACCOUNT_ID.value,
                            mediaId = MEDIA_ID,
                            progressUnit = StoredProgressUnit.EPISODE,
                            remoteStatus = StoredCollectionStatus.CURRENT,
                            remoteProgress = 1,
                            remoteTotal = 12,
                            remoteUpdatedAtUtcMs = BASELINE,
                            effectiveStatus = StoredCollectionStatus.CURRENT,
                            effectiveProgress = 1,
                            effectiveTotal = 12,
                            effectiveUpdatedAtUtcMs = BASELINE,
                        ),
                    ),
                )
                database.releaseDao().upsertReleases(
                    listOf(
                        ReleaseEntity(
                            mediaId = MEDIA_ID,
                            progressUnit = StoredProgressUnit.EPISODE,
                            unitNumber = 2,
                            timeKind = StoredReleaseTimeKind.TRUSTED,
                            releaseInstantUtcMs = BASELINE,
                        ),
                    ),
                )
            }
        }

    private fun countRows(
        database: MioDatabase,
        table: String,
    ): Int =
        database.openHelper.readableDatabase.query("SELECT COUNT(*) FROM $table").use { cursor ->
            cursor.moveToFirst()
            cursor.getInt(0)
        }

    private companion object {
        val ACCOUNT_ID = AccountId("task19-account")
        const val ENTRY_ID = "task19-entry"
        const val MEDIA_ID = "task19-media"
        val BASELINE = Instant.parse("2026-08-11T12:00:00Z")
    }
}
