package io.morgan.mio

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import io.morgan.mio.core.common.identity.StableOpaqueIdFactory
import io.morgan.mio.core.database.entity.SyncAccountIssueEntity
import io.morgan.mio.core.database.model.StoredConnectedProvider
import io.morgan.mio.core.database.model.StoredProgressUnit
import io.morgan.mio.data.provider.api.ProviderCredentials
import io.morgan.mio.domain.account.AccountAuthorizationRedirect
import io.morgan.mio.domain.account.AccountReconnectionState
import io.morgan.mio.domain.account.AccountSessionState
import io.morgan.mio.domain.account.ConnectedProvider
import io.morgan.mio.domain.model.AccountId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.FixMethodOrder
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestName
import org.junit.runners.MethodSorters
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.seconds

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class RefreshEmulatorTest {
    @get:Rule
    public val composeRule = createEmptyComposeRule()

    @get:Rule
    public val testName = TestName()

    @After
    public fun saveScenarioEvidence() =
        runBlocking {
            if (database.syncDao().getSelectedAccount() != null) {
                RefreshEmulatorFixtureTest().snapshot()
            } else {
                java.io.File(app.filesDir, "task21-snapshot.txt").writeText(
                    "selected_account=null\nentries=${countRows(
                        "collection_entry",
                    )}\nissues=${countRows("sync_account_issue")}\n",
                )
            }
            val prefix = "task21-${provider.name}-${testName.methodName}"
            java.io.File(app.filesDir, "$prefix-room.txt").writeText(
                java.io.File(app.filesDir, "task21-snapshot.txt").readText(),
            )
            java.io.File(app.filesDir, "$prefix-http.json").writeText(getControlSnapshot().toString(2))
        }

    private val app get() = ApplicationProvider.getApplicationContext<SyncEmulatorApplication>()
    private val database get() = app.appGraph.database
    private val provider: ConnectedProvider
        get() =
            if (InstrumentationRegistry.getArguments().getString("provider") == "myanimelist") {
                ConnectedProvider.MY_ANIME_LIST
            } else {
                ConnectedProvider.ANILIST
            }
    private val storedProvider: StoredConnectedProvider
        get() =
            when (provider) {
                ConnectedProvider.ANILIST -> StoredConnectedProvider.ANILIST
                ConnectedProvider.MY_ANIME_LIST -> StoredConnectedProvider.MY_ANIME_LIST
            }

    @Before
    public fun requireFixtureAndResetOnlyItsRemoteState() {
        runBlocking {
            val arguments = InstrumentationRegistry.getArguments()
            check(arguments.getString("task20Fixture") == "true")
            check(arguments.getString("task21Fixture") == "true")
            check(arguments.getString("provider") in setOf("anilist", "myanimelist"))
            check(java.io.File(app.filesDir, MARKER_FILE).isFile) {
                "Task 21 fixture marker is missing; run RefreshEmulatorFixtureTest#seed first"
            }
            check(database.syncDao().getSelectedAccount()?.accountId == ACCOUNT_ID)
            postControl("/reset", JSONObject())
        }
    }

    @Test
    public fun aForegroundRefreshDiscoversRemoteRowsWithAnEmptyOutbox() =
        runBlocking {
            val beforeEntries = database.syncDao().getEntries(ACCOUNT_ID)
            assertEquals("the first refresh scenario starts with only its three cached rows", 3, beforeEntries.size)
            assertEquals(0, countRows("pending_operation"))
            assertEquals(0, countRows("pending_operation_group"))
            val cachedNow = checkNotNull(database.syncDao().getEntry(localEntryId("501", "anime/101")))
            assertEquals(8, cachedNow.effectiveProgress)

            ActivityScenario.launch(MainActivity::class.java).use {
                awaitCollectionPageRequests(3)
                awaitCondition { database.syncDao().getEntries(ACCOUNT_ID).size == 5 }
            }

            val refreshed = database.syncDao().getEntries(ACCOUNT_ID)
            assertEquals(5, refreshed.size)
            assertEquals(0, countRows("pending_operation"))
            assertEquals(0, countRows("pending_operation_group"))
            val newEntry = checkNotNull(database.syncDao().getEntry(localEntryId("502", "anime/102")))
            assertEquals(4, newEntry.effectiveProgress)
            assertEquals(24, newEntry.effectiveTotal)
            val chapter = checkNotNull(database.syncDao().getEntry(localEntryId("602", "manga/202")))
            val volume = checkNotNull(database.syncDao().getEntry(localEntryId("603", "manga/303")))
            assertEquals(StoredProgressUnit.CHAPTER, chapter.progressUnit)
            assertEquals(20, chapter.effectiveProgress)
            assertEquals(StoredProgressUnit.VOLUME, volume.progressUnit)
            assertEquals(2, volume.effectiveProgress)
            assertNotNull(database.syncDao().findMediaProviderIdByRemote(storedProvider, remoteMediaId("103")))
            assertNotNull(
                database.syncDao().findEntryProviderIdByRemote(storedProvider, remoteEntryId("503", "anime/103")),
            )
        }

    @Test
    public fun bFailedSecondPageLeavesThePreviouslyCommittedSnapshotUntouched() =
        runBlocking {
            val nowId = localEntryId("501", "anime/101")
            val mediaId = localMediaId("101")
            val entryBefore = checkNotNull(database.syncDao().getEntry(nowId))
            val mediaBefore = checkNotNull(database.mediaDao().getMedia(mediaId))
            val state = getControlSnapshot().getJSONObject("state")
            val changedAnime = state.getJSONArray("anime")
            changedAnime
                .getJSONObject(0)
                .put("progress", 6)
                .put("title", "Task 21 Partial Page Must Not Commit")
            val failure =
                if (provider == ConnectedProvider.ANILIST) {
                    JSONObject().put("provider", "anilist").put("type", "ANIME").put("page", 2)
                } else {
                    JSONObject().put("provider", "myanimelist").put("list", "anime").put("offset", 2)
                }
            postControl(
                "/configure",
                JSONObject()
                    .put("anime", changedAnime)
                    .put("fail_next_collection_page", failure),
            )
            postControl("/clear-requests", JSONObject())

            ActivityScenario.launch(MainActivity::class.java).use {
                awaitCondition { hasSecondAnimePage(getControlSnapshot().getJSONArray("requests")) }
                awaitCondition { database.syncDao().getLease(ACCOUNT_ID) == null }
            }

            assertEquals(entryBefore, database.syncDao().getEntry(nowId))
            assertEquals(mediaBefore, database.mediaDao().getMedia(mediaId))
            assertEquals(0, countRows("pending_operation"))
        }

    @Test
    public fun cSameAccountReconnectReplacesCredentialsWithoutReplacingTheSessionOrCache() =
        runBlocking {
            ActivityScenario.launch(MainActivity::class.java).use {
                awaitCondition {
                    val state = app.appGraph.accountSessionRepository.state.value
                    state is AccountSessionState.Authenticated && state.accountId.value == ACCOUNT_ID
                }
                awaitCollectionPageRequests(3)
                awaitCondition { database.syncDao().getLease(ACCOUNT_ID) == null }
                val rowsBefore = database.syncDao().getEntries(ACCOUNT_ID)
                val groupsBefore = countRows("pending_operation_group")
                val operationsBefore = countRows("pending_operation")
                val activitiesBefore = countRows("local_progress_activity")
                val previousCredentials =
                    checkNotNull(app.appGraph.providerCredentialStore.read(provider.toKey()))

                val launch = checkNotNull(app.appGraph.accountReconnectionRepository.start())
                assertTrue(launch.authorizationUri.startsWith("mio://oauth/"))
                assertCredentialsUnchanged(
                    previousCredentials,
                    checkNotNull(app.appGraph.providerCredentialStore.read(provider.toKey())),
                )
                assertTrue(
                    app.appGraph.accountReconnectionRepository.state.value is AccountReconnectionState.Authorizing,
                )

                app.appGraph.accountReconnectionRepository.complete(
                    AccountAuthorizationRedirect("${provider.callbackUri}?state=task21-fixture-state&code=fixture"),
                )

                assertEquals(
                    AccountReconnectionState.Succeeded(AccountId(ACCOUNT_ID)),
                    app.appGraph.accountReconnectionRepository.state.value,
                )
                val stored = checkNotNull(app.appGraph.providerCredentialStore.read(provider.toKey()))
                assertEquals("task21-reconnected-access", stored.accessToken)
                assertEquals("task21-reconnected-refresh", stored.refreshToken)
                assertEquals(
                    AccountSessionState.Authenticated(AccountId(ACCOUNT_ID), provider),
                    app.appGraph.accountSessionRepository.state.value,
                )

                assertEquals(rowsBefore, database.syncDao().getEntries(ACCOUNT_ID))
                assertEquals(groupsBefore, countRows("pending_operation_group"))
                assertEquals(operationsBefore, countRows("pending_operation"))
                assertEquals(activitiesBefore, countRows("local_progress_activity"))
            }
        }

    @Test
    public fun dHeldRefreshKeepsCachedTodayUsableAndUndoRestoresProgress() =
        runBlocking {
            val nowId = localEntryId("501", "anime/101")
            val progressBefore = checkNotNull(database.syncDao().getEntry(nowId)).effectiveProgress
            val groupsBefore = countRows("pending_operation_group")
            val activitiesBefore = countRows("local_progress_activity")
            makeNowEpisodeAvailable()
            postControl("/configure", JSONObject().put("hold_next_collection_page", true))
            postControl("/clear-requests", JSONObject())

            try {
                ActivityScenario.launch(MainActivity::class.java).use {
                    awaitCondition {
                        getControlSnapshot()
                            .getJSONObject("state")
                            .getJSONObject("held")
                            .length() == 1
                    }
                    composeRule.waitUntil(10_000) {
                        composeRule
                            .onAllNodesWithContentDescription("Increase episode to ${progressBefore + 1}")
                            .fetchSemanticsNodes()
                            .isNotEmpty()
                    }

                    composeRule.onNodeWithText("Task 21 Now Fixture").assertIsDisplayed()
                    composeRule
                        .onNodeWithContentDescription("Increase episode to ${progressBefore + 1}")
                        .assertIsDisplayed()
                    assertEquals(progressBefore, checkNotNull(database.syncDao().getEntry(nowId)).effectiveProgress)

                    val beforePosition =
                        composeRule
                            .onNodeWithText("Task 21 Now Fixture")
                            .fetchSemanticsNode()
                            .boundsInRoot.topLeft
                    composeRule.mainClock.autoAdvance = false
                    composeRule
                        .onNodeWithContentDescription("Increase episode to ${progressBefore + 1}")
                        .performSemanticsAction(SemanticsActions.OnClick) { it() }
                    awaitCondition {
                        database.syncDao().getEntry(nowId)?.effectiveProgress == progressBefore + 1
                    }
                    composeRule.mainClock.advanceTimeBy(32)
                    releaseAllHeldRequests()
                    awaitCondition { database.syncDao().getLease(ACCOUNT_ID) == null }
                    composeRule.mainClock.advanceTimeBy(32)
                    assertEquals(
                        beforePosition,
                        composeRule
                            .onNodeWithText("Task 21 Now Fixture")
                            .fetchSemanticsNode()
                            .boundsInRoot.topLeft,
                    )
                    composeRule.mainClock.advanceTimeBy(300)
                    assertEquals(
                        beforePosition,
                        composeRule
                            .onNodeWithText("Task 21 Now Fixture")
                            .fetchSemanticsNode()
                            .boundsInRoot.topLeft,
                    )
                    composeRule.mainClock.autoAdvance = true
                    composeRule.waitUntil(5_000) {
                        composeRule.onAllNodesWithText("Undo").fetchSemanticsNodes().isNotEmpty()
                    }
                    composeRule.onNodeWithText("Undo").assertIsDisplayed().performClick()
                    awaitCondition {
                        database.syncDao().getEntry(nowId)?.effectiveProgress == progressBefore
                    }

                    awaitCondition { database.syncDao().getOrderedOperations(ACCOUNT_ID).isEmpty() }
                    assertEquals(groupsBefore, countRows("pending_operation_group"))
                    assertEquals(activitiesBefore + 1, countRows("local_progress_activity"))
                }
            } finally {
                composeRule.mainClock.autoAdvance = true
                releaseAllHeldRequests()
            }
        }

    @Test
    public fun d1BodyAndStepperKeepTheirCoordinatesDuringARealRefresh() =
        runBlocking {
            makeNowEpisodeAvailable()
            postControl("/configure", JSONObject().put("hold_next_collection_page", true))
            try {
                ActivityScenario.launch(MainActivity::class.java).use {
                    awaitCondition { getControlSnapshot().getJSONObject("state").getJSONObject("held").length() == 1 }
                    composeRule.waitUntil(10_000) {
                        composeRule.onAllNodesWithText("Task 21 Now Fixture").fetchSemanticsNodes().isNotEmpty()
                    }
                    val title = composeRule.onNodeWithText("Task 21 Now Fixture")
                    val bounds = title.fetchSemanticsNode().boundsInRoot
                    title.performTouchInput { down(center) }
                    releaseAllHeldRequests()
                    awaitCondition { database.syncDao().getLease(ACCOUNT_ID) == null }
                    composeRule.runOnIdle { }
                    assertEquals(bounds.topLeft, title.fetchSemanticsNode().boundsInRoot.topLeft)
                    title.performTouchInput { cancel() }
                    // The remote snapshot removes the cached release claim. Make it available again,
                    // then exercise a child control without triggering a mutation on cancellation.
                    makeNowEpisodeAvailable()
                    val value =
                        checkNotNull(
                            database.syncDao().getEntry(localEntryId("501", "anime/101")),
                        ).effectiveProgress
                    composeRule.waitUntil(10_000) {
                        composeRule
                            .onAllNodesWithContentDescription("Increase episode to ${value + 1}")
                            .fetchSemanticsNodes()
                            .isNotEmpty()
                    }
                    val stepper = composeRule.onNodeWithContentDescription("Increase episode to ${value + 1}")
                    val controlBounds = stepper.fetchSemanticsNode().boundsInRoot
                    stepper.performTouchInput { down(center) }
                    app.appGraph.accountRefreshObserver.requestRefresh()
                    awaitCollectionPageRequests(6)
                    awaitCondition { database.syncDao().getLease(ACCOUNT_ID) == null }
                    assertEquals(controlBounds, stepper.fetchSemanticsNode().boundsInRoot)
                    stepper.performTouchInput { cancel() }
                    assertEquals(
                        value,
                        database.syncDao().getEntry(localEntryId("501", "anime/101"))?.effectiveProgress,
                    )
                }
            } finally {
                releaseAllHeldRequests()
            }
        }

    @Test
    public fun eRejectedMutationShowsSafeIssueAndRetainsTheRetryableIntent() =
        runBlocking {
            val nowId = localEntryId("501", "anime/101")
            val progressBefore = checkNotNull(database.syncDao().getEntry(nowId)).effectiveProgress
            val groupsBefore = countRows("pending_operation_group")
            val activitiesBefore = countRows("local_progress_activity")
            makeNowEpisodeAvailable()
            postControl(
                "/configure",
                JSONObject().put("hold_next_collection_page", true).put("fail_next_mutation", 422),
            )
            postControl("/clear-requests", JSONObject())

            try {
                ActivityScenario.launch(MainActivity::class.java).use {
                    awaitCondition {
                        getControlSnapshot()
                            .getJSONObject("state")
                            .getJSONObject("held")
                            .length() == 1
                    }
                    val increaseDescription = "Increase episode to ${progressBefore + 1}"
                    composeRule.waitUntil(10_000) {
                        composeRule
                            .onAllNodesWithContentDescription(increaseDescription)
                            .fetchSemanticsNodes()
                            .isNotEmpty()
                    }
                    composeRule.onNodeWithContentDescription(increaseDescription).performClick()
                    awaitCondition {
                        database.syncDao().getEntry(nowId)?.effectiveProgress == progressBefore + 1
                    }
                    releaseAllHeldRequests()
                    awaitCondition {
                        database.syncDao().getIssues(ACCOUNT_ID).any { issue -> issue.kind == "rejected" }
                    }

                    val issue = database.syncDao().getIssues(ACCOUNT_ID).single()
                    val operation =
                        database.syncDao().getOrderedOperations(ACCOUNT_ID).single { row ->
                            row.operationId == issue.operationId
                        }
                    assertEquals("rejected", issue.kind)
                    assertEquals(progressBefore + 1, issue.targetProgress)
                    assertEquals(progressBefore + 1, issue.localProgress)
                    assertEquals(issue.operationGroupId, operation.operationGroupId)
                    assertEquals(groupsBefore + 1, countRows("pending_operation_group"))
                    assertEquals(activitiesBefore + 1, countRows("local_progress_activity"))

                    composeRule.waitUntil(10_000) {
                        composeRule.onAllNodesWithText("Retry sync").fetchSemanticsNodes().isNotEmpty()
                    }
                    composeRule
                        .onNodeWithText("This update was not accepted; your saved value is unchanged")
                        .performScrollTo()
                        .assertIsDisplayed()
                    composeRule
                        .onNodeWithText("Saved value: ${progressBefore + 1} episodes")
                        .performScrollTo()
                        .assertIsDisplayed()
                    composeRule.onNodeWithText("Retry sync").performScrollTo().assertIsDisplayed()
                    assertTrue(
                        composeRule
                            .onAllNodesWithText("fixture rejected mutation", substring = true)
                            .fetchSemanticsNodes()
                            .isEmpty(),
                    )

                    val requests = getControlSnapshot().getJSONArray("requests")
                    assertEquals(
                        1,
                        (0 until requests.length()).count { index ->
                            requests.getJSONObject(index).optString("operation") == "mutation_rejected"
                        },
                    )
                    assertEquals(
                        0,
                        (0 until requests.length()).count { index ->
                            requests.getJSONObject(index).optString("operation") == "mutation"
                        },
                    )
                    val retry = composeRule.onNodeWithText("Retry sync")
                    retry.performSemanticsAction(SemanticsActions.OnClick) { it() }
                    awaitCondition { database.syncDao().getIssues(ACCOUNT_ID).isEmpty() }
                    app.appGraph.accountGraphLifecycle
                        .activeHandle()
                        ?.withGraph { graph ->
                            graph.retrySyncIssueUseCase
                        }?.invoke(
                            io.morgan.mio.domain.model
                                .OperationId(operation.operationId),
                        )
                    awaitCondition { database.syncDao().getOrderedOperations(ACCOUNT_ID).isEmpty() }
                    assertTrue(database.syncDao().getIssues(ACCOUNT_ID).isEmpty())
                    assertEquals(activitiesBefore + 1, countRows("local_progress_activity"))
                    assertEquals(progressBefore + 1, database.syncDao().getEntry(nowId)?.effectiveProgress)
                }
            } finally {
                releaseAllHeldRequests()
            }
        }

    @Test
    public fun fForegroundCoalescesSignalsAndStopsTransportInTheBackground() =
        runBlocking {
            postControl("/configure", JSONObject().put("hold_next_collection_page", true))
            try {
                ActivityScenario.launch(MainActivity::class.java).use { activity ->
                    awaitCondition { getControlSnapshot().getJSONObject("state").getJSONObject("held").length() == 1 }
                    repeat(20) { app.appGraph.accountRefreshObserver.requestRefresh() }
                    releaseAllHeldRequests()
                    awaitCollectionPageRequests(6)
                    awaitCondition { database.syncDao().getLease(ACCOUNT_ID) == null }
                    assertEquals(6, collectionRequestCount())
                    activity.moveToState(Lifecycle.State.CREATED)
                    postControl("/clear-requests", JSONObject())
                    app.appGraph.accountRefreshObserver.requestRefresh()
                    delay(300)
                    assertEquals(0, collectionRequestCount())
                    activity.moveToState(Lifecycle.State.RESUMED)
                    awaitCollectionPageRequests(3)
                    awaitCondition { database.syncDao().getLease(ACCOUNT_ID) == null }
                    assertEquals(3, collectionRequestCount())
                }
            } finally {
                releaseAllHeldRequests()
            }
        }

    @Test
    public fun gReconnectButtonRecoversPersistedAuthenticationAfterActivityRecreation() =
        runBlocking {
            ActivityScenario.launch(MainActivity::class.java).use { activity ->
                awaitCollectionPageRequests(3)
                awaitCondition { database.syncDao().getLease(ACCOUNT_ID) == null }
                val before = database.syncDao().getEntries(ACCOUNT_ID)
                database.syncDao().upsertAccountIssue(
                    SyncAccountIssueEntity("task21-auth-ui", ACCOUNT_ID, "authentication", Clock.System.now()),
                )
                activity.recreate()
                composeRule.waitUntil(10_000) {
                    composeRule.onAllNodesWithText("Reconnect").fetchSemanticsNodes().isNotEmpty()
                }
                val providerName = if (provider == ConnectedProvider.ANILIST) "AniList" else "MyAnimeList"
                composeRule
                    .onNodeWithText("Reconnect to $providerName to continue syncing")
                    .performScrollTo()
                    .assertIsDisplayed()
                composeRule
                    .onNodeWithText("Reconnect")
                    .performScrollTo()
                    .performSemanticsAction(SemanticsActions.OnClick) { it() }
                delay(500)
                awaitCondition { database.syncDao().getAccountIssue(ACCOUNT_ID) == null }
                awaitCondition { activity.state == Lifecycle.State.RESUMED }
                composeRule.waitForIdle()
                assertTrue(app.appGraph.accountSessionRepository.state.value is AccountSessionState.Authenticated)
                assertEquals(
                    before.map {
                        it.collectionEntryId
                    },
                    database.syncDao().getEntries(ACCOUNT_ID).map { it.collectionEntryId },
                )
            }
        }

    @Test
    public fun hOfflineProgressWaitsForActualConnectivityAndExpiresNewWhilePinned() =
        runBlocking {
            ActivityScenario.launch(MainActivity::class.java).use {
                awaitCollectionPageRequests(3)
                awaitCondition { database.syncDao().getLease(ACCOUNT_ID) == null }
                makeVolumeAvailable()
                shell("svc wifi disable")
                shell("svc data disable")
                try {
                    awaitCondition {
                        app.appGraph.networkMonitor.availability
                            .first() ==
                            io.morgan.mio.core.network.NetworkAvailability.UNAVAILABLE
                    }
                    postControl("/clear-requests", JSONObject())
                    val entryId = localEntryId("502", "anime/102")
                    val entry = checkNotNull(database.syncDao().getEntry(entryId))
                    database.releaseDao().upsertReleases(
                        listOf(
                            io.morgan.mio.core.database.entity.ReleaseEntity(
                                mediaId = entry.mediaId,
                                progressUnit = StoredProgressUnit.EPISODE,
                                unitNumber = entry.effectiveProgress + 1,
                                timeKind = io.morgan.mio.core.database.model.StoredReleaseTimeKind.TRUSTED,
                                releaseInstantUtcMs = Clock.System.now() - 7.days + 4.seconds,
                            ),
                        ),
                    )
                    composeRule.waitUntil(10_000) {
                        composeRule.onAllNodesWithText("New · 6 days ago").fetchSemanticsNodes().isNotEmpty()
                    }
                    val title = composeRule.onNodeWithText("Task 21 New Fixture").performScrollTo()
                    val position = title.fetchSemanticsNode().boundsInRoot.topLeft
                    title.performTouchInput { down(center) }
                    delay(4_500)
                    composeRule.onAllNodesWithText("New · 6 days ago").assertCountEquals(0)
                    assertEquals(position, title.fetchSemanticsNode().boundsInRoot.topLeft)
                    title.performTouchInput { cancel() }
                    val volumeId = localEntryId("603", "manga/303")
                    val before = checkNotNull(database.syncDao().getEntry(volumeId)).effectiveProgress
                    composeRule
                        .onNodeWithContentDescription("Increase volume to ${before + 1}")
                        .performScrollTo()
                        .performClick()
                    awaitCondition { database.syncDao().getEntry(volumeId)?.effectiveProgress == before + 1 }
                    val operations = database.syncDao().getOrderedOperations(ACCOUNT_ID)
                    assertTrue(operations.isNotEmpty())
                    delay(500)
                    assertEquals(0, getControlSnapshot().getJSONArray("requests").length())
                    assertTrue(
                        androidx.work.WorkManager
                            .getInstance(app)
                            .getWorkInfosForUniqueWork("mio.pending.$ACCOUNT_ID")
                            .get()
                            .any { work -> work.state == androidx.work.WorkInfo.State.ENQUEUED },
                    )
                    shell("svc wifi enable")
                    shell("svc data enable")
                    awaitCondition { database.syncDao().getOrderedOperations(ACCOUNT_ID).isEmpty() }
                    assertEquals(before + 1, database.syncDao().getEntry(volumeId)?.effectiveProgress)
                    assertTrue(collectionRequestCount() >= 3)
                } finally {
                    shell("svc wifi enable")
                    shell("svc data enable")
                }
            }
        }

    @Test
    public fun iReconnectWrongIdentityDeniedAndSecureWriteFailurePreserveTheVisibleIssue() =
        runBlocking {
            ActivityScenario.launch(MainActivity::class.java).use { activity ->
                awaitCollectionPageRequests(3)
                awaitCondition { database.syncDao().getLease(ACCOUNT_ID) == null }
                val before = database.syncDao().getEntries(ACCOUNT_ID)
                val credentials = checkNotNull(app.appGraph.providerCredentialStore.read(provider.toKey()))
                val modeFile = java.io.File(app.filesDir, "task21-reconnect-mode")
                try {
                    for (mode in listOf("browser_cancel", "wrong_identity", "denied", "write_failure")) {
                        modeFile.writeText(mode)
                        database
                            .syncDao()
                            .getAccountIssue(
                                ACCOUNT_ID,
                            )?.let { database.syncDao().deleteAccountIssue(ACCOUNT_ID, it.issueId) }
                        val attempt = java.io.File(app.filesDir, "task21-reconnect-attempt")
                        val previousAttempt = if (attempt.exists()) attempt.readText() else ""
                        database.syncDao().upsertAccountIssue(
                            SyncAccountIssueEntity(
                                "task21-auth-$mode",
                                ACCOUNT_ID,
                                "authentication",
                                Clock.System.now(),
                            ),
                        )
                        composeRule.waitUntil(10_000) {
                            composeRule.onAllNodesWithText("Reconnect").fetchSemanticsNodes().isNotEmpty()
                        }
                        composeRule
                            .onNodeWithText("Reconnect")
                            .performSemanticsAction(SemanticsActions.OnClick) { it() }
                        awaitCondition { attempt.exists() && attempt.readText() != previousAttempt }
                        if (mode == "browser_cancel") {
                            awaitCondition { activity.state == Lifecycle.State.CREATED }
                            shell("input keyevent KEYCODE_BACK")
                            awaitCondition {
                                app.appGraph.accountReconnectionRepository.state.value == AccountReconnectionState.Idle
                            }
                        } else {
                            awaitCondition {
                                val reconnectState = app.appGraph.accountReconnectionRepository.state.value
                                reconnectState is AccountReconnectionState.Failed
                            }
                        }
                        awaitCondition { activity.state == Lifecycle.State.RESUMED }
                        assertEquals("task21-auth-$mode", database.syncDao().getAccountIssue(ACCOUNT_ID)?.issueId)
                        assertCredentialsUnchanged(
                            credentials,
                            checkNotNull(app.appGraph.providerCredentialStore.read(provider.toKey())),
                        )
                        assertEquals(before, database.syncDao().getEntries(ACCOUNT_ID))
                    }
                } finally {
                    modeFile.delete()
                }
                composeRule.onNodeWithText("Reconnect").performSemanticsAction(SemanticsActions.OnClick) { it() }
                awaitCondition { database.syncDao().getAccountIssue(ACCOUNT_ID) == null }
                awaitCondition { activity.state == Lifecycle.State.RESUMED }
            }
        }

    @Test
    public fun jConflictKeepsContextAndAllowsAnUnrelatedEntryToSync() =
        runBlocking {
            makeVolumeAvailable()
            postControl("/configure", JSONObject().put("hold_next_collection_page", true))
            try {
                ActivityScenario.launch(MainActivity::class.java).use {
                    awaitCondition { getControlSnapshot().getJSONObject("state").getJSONObject("held").length() == 1 }
                    val entryId = localEntryId("603", "manga/303")
                    val before = checkNotNull(database.syncDao().getEntry(entryId)).effectiveProgress
                    composeRule.waitUntil(10_000) {
                        composeRule
                            .onAllNodesWithContentDescription(
                                "Decrease volume to ${before - 1}",
                            ).fetchSemanticsNodes()
                            .isNotEmpty()
                    }
                    composeRule
                        .onNodeWithContentDescription("Decrease volume to ${before - 1}")
                        .performSemanticsAction(SemanticsActions.OnClick) { it() }
                    awaitCondition { database.syncDao().getEntry(entryId)?.effectiveProgress == before - 1 }
                    val manga = getControlSnapshot().getJSONObject("state").getJSONArray("manga")
                    manga
                        .getJSONObject(1)
                        .put("volumes_read", before + 1)
                        .put("updated_at", Clock.System.now().epochSeconds + 1)
                    postControl("/configure", JSONObject().put("manga", manga))
                    releaseAllHeldRequests()
                    awaitCondition {
                        database.syncDao().getIssues(ACCOUNT_ID).any { issue ->
                            issue.kind == "conflict"
                        }
                    }
                    composeRule
                        .onNodeWithText("This update conflicts with a provider change")
                        .performScrollTo()
                        .assertIsDisplayed()
                    composeRule
                        .onNodeWithText("Provider value: ${before + 1} volumes")
                        .performScrollTo()
                        .assertIsDisplayed()
                    composeRule.onAllNodesWithText("Resolve").assertCountEquals(0)
                    composeRule.onAllNodesWithText("Retry sync").assertCountEquals(0)
                    val issue = database.syncDao().getIssues(ACCOUNT_ID).single()
                    assertEquals(before - 1, database.syncDao().getEntry(entryId)?.effectiveProgress)
                    val otherId = localEntryId("602", "manga/202")
                    val other = checkNotNull(database.syncDao().getEntry(otherId))
                    val accountId = AccountId(ACCOUNT_ID)
                    val progressRepository =
                        io.morgan.mio.data.progress.DefaultProgressRepository(
                            accountId,
                            database,
                            io.morgan.mio.data.progress.local
                                .RoomProgressLocalDataSource(database),
                            io.morgan.mio.data.sync.DefaultPendingOperationWriter(
                                accountId,
                                io.morgan.mio.data.sync.local
                                    .RoomPendingOperationLocalDataSource(database),
                            ),
                            Clock.System,
                        )
                    val result =
                        io.morgan.mio.domain.progress
                            .StepProgressUseCase(progressRepository, Clock.System)(
                            io.morgan.mio.domain.model
                                .MediaId(other.mediaId),
                            io.morgan.mio.domain.progress.ProgressDirection.INCREASE,
                            io.morgan.mio.domain.model
                                .OperationGroupId("task21-unrelated-${java.util.UUID.randomUUID()}"),
                            6.seconds,
                        )
                    assertTrue(result is io.morgan.mio.domain.progress.ProgressWriteResult.Committed)
                    awaitCondition {
                        database.syncDao().getOrderedOperations(ACCOUNT_ID).none { op ->
                            op.collectionEntryId ==
                                otherId
                        }
                    }
                    assertEquals(other.effectiveProgress + 1, database.syncDao().getEntry(otherId)?.remoteProgress)
                    assertEquals(
                        issue.operationId,
                        database
                            .syncDao()
                            .getIssues(ACCOUNT_ID)
                            .single()
                            .operationId,
                    )
                }
            } finally {
                releaseAllHeldRequests()
            }
        }

    @Test
    public fun zEmptyCollectionAuthenticationHasARealProviderNamedRecoveryAction() =
        runBlocking {
            database.collectionEntryDao().deleteAllForAccount(ACCOUNT_ID)
            postControl(
                "/configure",
                JSONObject()
                    .put("anime", JSONArray())
                    .put("manga", JSONArray())
                    .put("fail_next_identity", "all"),
            )
            app.appGraph.providerCredentialStore.write(
                ProviderCredentials(provider.toKey(), "task21-expired-fixture", null, null, "Bearer"),
            )
            ActivityScenario.launch(MainActivity::class.java).use { activity ->
                awaitCondition { database.syncDao().getAccountIssue(ACCOUNT_ID)?.kind == "authentication" }
                assertTrue(database.syncDao().getEntries(ACCOUNT_ID).isEmpty())
                assertEquals(0, countRows("pending_operation"))
                val name = if (provider == ConnectedProvider.ANILIST) "AniList" else "MyAnimeList"
                composeRule.waitUntil(
                    10_000,
                ) { composeRule.onAllNodesWithText("Reconnect").fetchSemanticsNodes().isNotEmpty() }
                composeRule.onNodeWithText("Reconnect to $name to continue syncing").assertIsDisplayed()
                composeRule.onNodeWithText("Reconnect").performSemanticsAction(SemanticsActions.OnClick) { it() }
                awaitCondition { database.syncDao().getAccountIssue(ACCOUNT_ID) == null }
                awaitCondition { activity.state == Lifecycle.State.RESUMED }
                assertTrue(database.syncDao().getEntries(ACCOUNT_ID).isEmpty())
            }
        }

    @Test
    public fun zzRemovedAccountRejectsALateProviderSnapshot() =
        runBlocking {
            postControl("/configure", JSONObject().put("hold_next_collection_page", true))
            try {
                ActivityScenario.launch(MainActivity::class.java).use {
                    awaitCondition { getControlSnapshot().getJSONObject("state").getJSONObject("held").length() == 1 }
                    database.selectedAccountDao().deleteAccount(ACCOUNT_ID)
                    app.appGraph.accountGraphLifecycle.destroy()
                    releaseAllHeldRequests()
                    awaitCondition { database.syncDao().getLease(ACCOUNT_ID) == null }
                    delay(500)
                    assertEquals(0, countRows("selected_account"))
                    assertEquals(0, countRows("collection_entry"))
                    assertEquals(0, countRows("sync_account_issue"))
                    assertEquals(0, countRows("pending_operation"))
                    app.appGraph.accountRefreshObserver.requestRefresh()
                }
            } finally {
                releaseAllHeldRequests()
            }
        }

    private fun shell(command: String) {
        val descriptor = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)
        android.os.ParcelFileDescriptor
            .AutoCloseInputStream(descriptor)
            .use { it.readBytes() }
    }

    private suspend fun collectionRequestCount(): Int {
        val requests = getControlSnapshot().getJSONArray("requests")
        return (0 until requests.length()).count {
            requests.getJSONObject(it).optString("operation") ==
                "collection_page"
        }
    }

    private suspend fun makeVolumeAvailable() {
        val entry = checkNotNull(database.syncDao().getEntry(localEntryId("603", "manga/303")))
        database.releaseDao().upsertReleases(
            listOf(
                io.morgan.mio.core.database.entity.ReleaseEntity(
                    mediaId = entry.mediaId,
                    progressUnit = StoredProgressUnit.VOLUME,
                    unitNumber = entry.effectiveProgress + 1,
                    timeKind = io.morgan.mio.core.database.model.StoredReleaseTimeKind.TRUSTED,
                    releaseInstantUtcMs = Clock.System.now() - 60.seconds,
                ),
            ),
        )
    }

    private suspend fun makeNowEpisodeAvailable() {
        val mediaReleases = database.releaseDao().getReleases(localMediaId("101"))
        database.releaseDao().upsertReleases(
            listOf(
                mediaReleases
                    .single { release ->
                        release.progressUnit == StoredProgressUnit.EPISODE && release.unitNumber == 9
                    }.copy(releaseInstantUtcMs = Clock.System.now() - 60.seconds),
            ),
        )
    }

    private suspend fun awaitCollectionPageRequests(expected: Int) {
        awaitCondition {
            val requests = getControlSnapshot().getJSONArray("requests")
            (0 until requests.length()).count { index ->
                requests.getJSONObject(index).optString("operation") == "collection_page"
            } >= expected
        }
    }

    private fun assertCredentialsUnchanged(
        expected: ProviderCredentials,
        actual: ProviderCredentials,
    ) {
        assertEquals(expected.provider, actual.provider)
        assertTrue(
            "access token changed before successful authorization",
            expected.accessToken == actual.accessToken,
        )
        assertTrue(
            "refresh token changed before successful authorization",
            expected.refreshToken == actual.refreshToken,
        )
        assertEquals(expected.expiresAt, actual.expiresAt)
        assertEquals(expected.tokenType, actual.tokenType)
    }

    private fun hasSecondAnimePage(requests: JSONArray): Boolean =
        (0 until requests.length()).any { index ->
            val request = requests.getJSONObject(index)
            if (request.optString("operation") != "collection_page") return@any false
            val detail = request.optJSONObject("detail") ?: return@any false
            if (provider == ConnectedProvider.ANILIST) {
                detail.optString("provider") == "anilist" &&
                    detail.optString("type") == "ANIME" &&
                    detail.optInt("page") == 2
            } else {
                detail.optString("provider") == "myanimelist" &&
                    detail.optString("list") == "anime" &&
                    detail.optInt("offset") == 2
            }
        }

    private suspend fun awaitCondition(condition: suspend () -> Boolean) {
        withTimeout(120.seconds) {
            while (!condition()) delay(POLL_INTERVAL_MILLIS)
        }
    }

    private suspend fun getControlSnapshot(): JSONObject =
        withContext(Dispatchers.IO) {
            val connection = URL("$CONTROL_BASE_URL/snapshot").openConnection() as HttpURLConnection
            connection.connectTimeout = CONTROL_TIMEOUT_MILLIS
            connection.readTimeout = CONTROL_TIMEOUT_MILLIS
            try {
                assertEquals(200, connection.responseCode)
                JSONObject(connection.inputStream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() })
            } finally {
                connection.disconnect()
            }
        }

    private suspend fun releaseAllHeldRequests() =
        withContext(Dispatchers.IO) {
            val connection = URL("$CONTROL_BASE_URL/release-all").openConnection() as HttpURLConnection
            connection.connectTimeout = CONTROL_TIMEOUT_MILLIS
            connection.readTimeout = CONTROL_TIMEOUT_MILLIS
            try {
                assertEquals(200, connection.responseCode)
                connection.inputStream.close()
            } finally {
                connection.disconnect()
            }
        }

    private suspend fun postControl(
        path: String,
        body: JSONObject,
    ): JSONObject =
        withContext(Dispatchers.IO) {
            val connection = URL("$CONTROL_BASE_URL$path").openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.connectTimeout = CONTROL_TIMEOUT_MILLIS
            connection.readTimeout = CONTROL_TIMEOUT_MILLIS
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            try {
                connection.outputStream.use { output ->
                    output.write(body.toString().toByteArray(StandardCharsets.UTF_8))
                }
                assertEquals(200, connection.responseCode)
                JSONObject(connection.inputStream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() })
            } finally {
                connection.disconnect()
            }
        }

    private fun countRows(table: String): Int =
        database.openHelper.readableDatabase.query("SELECT COUNT(*) FROM $table").use { cursor ->
            check(cursor.moveToFirst())
            cursor.getInt(0)
        }

    private fun localMediaId(remoteId: String): String =
        StableOpaqueIdFactory.create(storedProvider.serializedValue, "media", remoteMediaId(remoteId))

    private fun localEntryId(
        anilistEntryId: String,
        malEntryId: String,
    ): String =
        StableOpaqueIdFactory.create(
            storedProvider.serializedValue,
            "collection-entry",
            if (provider == ConnectedProvider.ANILIST) anilistEntryId else malEntryId,
        )

    private fun remoteMediaId(remoteId: String): String =
        if (provider == ConnectedProvider.ANILIST) remoteId else "${remoteIdPath(remoteId)}/$remoteId"

    private fun remoteEntryId(
        anilistEntryId: String,
        malId: String,
    ): String = if (provider == ConnectedProvider.ANILIST) anilistEntryId else malId

    private fun remoteIdPath(remoteId: String): String = if (remoteId in MANGA_REMOTE_IDS) "manga" else "anime"

    private fun ConnectedProvider.toKey(): io.morgan.mio.data.provider.api.ProviderKey =
        when (this) {
            ConnectedProvider.ANILIST -> io.morgan.mio.data.provider.api.ProviderKey.ANILIST
            ConnectedProvider.MY_ANIME_LIST -> io.morgan.mio.data.provider.api.ProviderKey.MY_ANIME_LIST
        }

    private val ConnectedProvider.callbackUri: String
        get() =
            when (this) {
                ConnectedProvider.ANILIST -> "mio://oauth/anilist"
                ConnectedProvider.MY_ANIME_LIST -> "mio://oauth/myanimelist"
            }

    private companion object {
        const val ACCOUNT_ID = "task21-account"
        const val MARKER_FILE = "task21-fixture-marker"
        const val CONTROL_BASE_URL = "http://127.0.0.1:18821"
        const val CONTROL_TIMEOUT_MILLIS = 5_000
        const val POLL_INTERVAL_MILLIS = 100L
        val MANGA_REMOTE_IDS = setOf("202", "303")
    }
}
