package io.morgan.mio

import androidx.activity.compose.setContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.morgan.mio.core.database.MioDatabase
import io.morgan.mio.core.database.model.StoredCollectionStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProgressEmulatorTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun nowPlusAndUndoUseTheProductionRoomPath() {
        val database = targetApplication().appGraph.database
        waitUntil {
            composeRule
                .onAllNodes(
                    hasContentDescription("Increase episode to 9"),
                ).fetchSemanticsNodes()
                .isNotEmpty()
        }
        composeRule.onAllNodesWithText("Today")[0].assertIsDisplayed()
        val initialActivities = countRows(database, "local_progress_activity")
        val initialGroups = countRows(database, "pending_operation_group")

        composeRule.onNodeWithContentDescription("Increase episode to 9").performClick()
        waitForProgress(database, 9)
        assertEquals(initialGroups + 1, countRows(database, "pending_operation_group"))
        assertEquals(initialActivities + 1, countRows(database, "local_progress_activity"))

        waitUntil { composeRule.onAllNodesWithText("Undo").fetchSemanticsNodes().isNotEmpty() }
        composeRule.onNodeWithText("Undo").performClick()
        waitForProgress(database, 8)
        assertEquals(initialGroups, countRows(database, "pending_operation_group"))
        assertEquals(initialActivities + 1, countRows(database, "local_progress_activity"))
    }

    @Test
    fun realPointerCancellationReentryAndRapidStepsPersistExactlyOnce() {
        val database = targetApplication().appGraph.database
        waitUntil {
            composeRule
                .onAllNodes(
                    hasContentDescription("Increase episode to 9"),
                ).fetchSemanticsNodes()
                .isNotEmpty()
        }
        val initialActivities = countRows(database, "local_progress_activity")
        val initialGroups = countRows(database, "pending_operation_group")
        val plus = composeRule.onNodeWithContentDescription("Increase episode to 9")
        plus.performTouchInput {
            down(center)
            moveTo(Offset(-20f, center.y))
            up()
        }
        composeRule.waitForIdle()
        assertEquals(initialActivities, countRows(database, "local_progress_activity"))
        plus.performTouchInput {
            down(center)
            moveTo(Offset(-20f, center.y))
            moveTo(center)
            up()
        }
        waitForProgress(database, 9)
        assertEquals(initialActivities + 1, countRows(database, "local_progress_activity"))
        composeRule.onNodeWithContentDescription("Increase episode to 10").performTouchInput {
            down(center)
            up()
        }
        waitForProgress(database, 10)
        composeRule.onNodeWithContentDescription("Decrease episode to 9").performTouchInput {
            down(center)
            up()
        }
        waitForProgress(database, 9)
        assertEquals(initialActivities + 3, countRows(database, "local_progress_activity"))
        assertEquals(initialGroups + 3, countRows(database, "pending_operation_group"))
        waitUntil { composeRule.onAllNodesWithText("Undo").fetchSemanticsNodes().size == 1 }
        composeRule.onNodeWithText("Undo").performClick()
        waitForProgress(database, 10)
        assertEquals(initialGroups + 2, countRows(database, "pending_operation_group"))
        val entry = runBlocking { requireNotNull(database.progressDao().getEntryForMedia(ACCOUNT_ID, NOW_MEDIA_ID)) }
        assertEquals(8, entry.remoteProgress)
        val latest = runBlocking { requireNotNull(database.progressDao().getLatestActivity(ACCOUNT_ID)) }
        org.junit.Assert.assertNotNull(latest.undoneAtUtcMs)
    }

    @Test
    fun finalDetailCompletionUndoesBothOperationsAndCanCompleteAgain() {
        val database = targetApplication().appGraph.database
        waitUntil { composeRule.onAllNodesWithText("Task 19 Final").fetchSemanticsNodes().isNotEmpty() }
        composeRule.onNodeWithText("Task 19 Final").performScrollTo().performClick()
        waitUntil { composeRule.onAllNodesWithText("In library").fetchSemanticsNodes().isNotEmpty() }
        val groups = countRows(database, "pending_operation_group")
        val operations = countRows(database, "pending_operation")
        val activities = countRows(database, "local_progress_activity")
        composeRule.onNodeWithContentDescription("Increase episode to 12").performScrollTo().performClick()
        waitUntil { entry(database, "task19-emulator-final").effectiveStatus == StoredCollectionStatus.COMPLETED }
        assertEquals(groups + 1, countRows(database, "pending_operation_group"))
        assertEquals(operations + 2, countRows(database, "pending_operation"))
        waitUntil { composeRule.onAllNodesWithText("Undo").fetchSemanticsNodes().size == 1 }
        composeRule.onNodeWithText("Undo").performClick()
        waitUntil { entry(database, "task19-emulator-final").effectiveProgress == 11 }
        assertEquals(StoredCollectionStatus.CURRENT, entry(database, "task19-emulator-final").effectiveStatus)
        assertEquals(groups, countRows(database, "pending_operation_group"))
        assertEquals(operations, countRows(database, "pending_operation"))
        composeRule.onNodeWithContentDescription("Increase episode to 12").performClick()
        waitUntil { entry(database, "task19-emulator-final").effectiveProgress == 12 }
        assertEquals(activities + 2, countRows(database, "local_progress_activity"))
    }

    @Test
    fun zeroUnknownAndStatusOnlyDetailUseRealCollectionState() {
        val database = targetApplication().appGraph.database
        waitUntil { composeRule.onAllNodesWithText("Task 19 Now").fetchSemanticsNodes().isNotEmpty() }
        composeRule.onNodeWithText("Task 19 Now").performClick()
        waitUntil { composeRule.onAllNodesWithText("In library").fetchSemanticsNodes().isNotEmpty() }
        runBlocking {
            val initial = entry(database, NOW_MEDIA_ID)
            database.collectionEntryDao().upsertEntries(
                listOf(initial.copy(effectiveProgress = 0, effectiveTotal = null)),
            )
        }
        composeRule.onNodeWithContentDescription("Decrease episode to 0").performScrollTo().assertIsNotEnabled()
        composeRule.onNodeWithContentDescription("Increase episode to 1").performClick()
        waitForProgress(database, 1)
        assertEquals(null, entry(database, NOW_MEDIA_ID).effectiveTotal)
        listOf(
            StoredCollectionStatus.PLANNED,
            StoredCollectionStatus.PAUSED,
            StoredCollectionStatus.DROPPED,
            StoredCollectionStatus.COMPLETED,
            StoredCollectionStatus.REPEATING,
        ).forEach { status ->
            runBlocking {
                database.collectionEntryDao().upsertEntries(
                    listOf(entry(database, NOW_MEDIA_ID).copy(effectiveStatus = status)),
                )
            }
            waitUntil {
                composeRule
                    .onAllNodes(
                        hasContentDescription("Increase episode to 2"),
                    ).fetchSemanticsNodes()
                    .isEmpty()
            }
            composeRule.onNodeWithContentDescription("Decrease episode to 0").assertDoesNotExist()
        }
    }

    @Test
    fun chapterCorrectionAndVolumeCompletionKeepTheirUnitsAndLatestReceipt() {
        val database = targetApplication().appGraph.database
        waitUntil { composeRule.onAllNodesWithText("Task 19 Volume").fetchSemanticsNodes().isNotEmpty() }
        val initialGroups = countRows(database, "pending_operation_group")
        clickVisibleControl("Increase volume to 3")
        waitUntil { entry(database, "task19-emulator-volume").effectiveProgress == 3 }
        assertEquals(StoredCollectionStatus.COMPLETED, entry(database, "task19-emulator-volume").effectiveStatus)
        assertEquals(initialGroups + 1, countRows(database, "pending_operation_group"))
        assertEquals(2, countRows(database, "pending_operation"))
        waitUntil { composeRule.onAllNodesWithText("Undo").fetchSemanticsNodes().size == 1 }
        composeRule.onNodeWithText("Undo").performClick()
        waitUntil { entry(database, "task19-emulator-volume").effectiveProgress == 2 }
        assertEquals(
            io.morgan.mio.core.database.model.StoredProgressUnit.VOLUME,
            entry(database, "task19-emulator-volume").progressUnit,
        )
        clickVisibleControl("Decrease chapter to 40")
        waitUntil { entry(database, "task19-emulator-chapter").effectiveProgress == 40 }
        val correction = runBlocking { requireNotNull(database.progressDao().getLatestActivity(ACCOUNT_ID)) }
        assertEquals("decrease", correction.direction)
        assertEquals(41, correction.beforeProgress)
        assertEquals(40, correction.afterProgress)
        composeRule.onNodeWithText("Task 19 Chapter").performScrollTo().performClick()
        waitUntil { composeRule.onAllNodesWithText("In library").fetchSemanticsNodes().isNotEmpty() }
        composeRule.onNodeWithContentDescription("Increase chapter to 41").performScrollTo().performClick()
        waitUntil { entry(database, "task19-emulator-chapter").effectiveProgress == 41 }
        val latest = runBlocking { requireNotNull(database.progressDao().getLatestActivity(ACCOUNT_ID)) }
        org.junit.Assert.assertNotEquals(correction.operationGroupId, latest.operationGroupId)
        composeRule.onNodeWithContentDescription("Back").performClick()
        waitUntil { composeRule.onAllNodesWithText("Today").fetchSemanticsNodes().isNotEmpty() }
        waitUntil { composeRule.onAllNodesWithText("Undo").fetchSemanticsNodes().size == 1 }
        composeRule.onNodeWithText("Undo").performClick()
        waitUntil { entry(database, "task19-emulator-chapter").effectiveProgress == 40 }
        assertEquals(initialGroups + 1, countRows(database, "pending_operation_group"))
        assertEquals(
            io.morgan.mio.core.database.model.StoredProgressUnit.CHAPTER,
            entry(database, "task19-emulator-chapter").progressUnit,
        )
    }

    @Test
    fun consumedScrollDisposalAndSecondaryPointerCannotWrite() {
        val database = targetApplication().appGraph.database
        waitUntil {
            composeRule
                .onAllNodes(hasContentDescription("Increase episode to 9"))
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        val groups = countRows(database, "pending_operation_group")
        val activities = countRows(database, "local_progress_activity")
        val plus = composeRule.onNodeWithContentDescription("Increase episode to 9")
        // A real vertical drag is consumed by Today's scrolling parent.
        plus.performTouchInput {
            down(center)
            moveTo(center + Offset(0f, -200f), delayMillis = 200)
            moveTo(center, delayMillis = 200)
            up()
        }
        composeRule.waitForIdle()
        assertEquals(groups, countRows(database, "pending_operation_group"))
        assertEquals(activities, countRows(database, "local_progress_activity"))
        plus.performScrollTo()
        // Only the original pointer can commit; its outside release cancels.
        plus.performTouchInput {
            down(0, center)
            down(1, center)
            moveTo(0, Offset(-40f, center.y))
            up(1)
            up(0)
        }
        composeRule.waitForIdle()
        assertEquals(activities, countRows(database, "local_progress_activity"))
        plus.performTouchInput { down(center) }
        // Navigating disposes the pressed control before the original up.
        composeRule.onNodeWithText("Task 19 Now").performSemanticsAction(SemanticsActions.OnClick) { it() }
        waitUntil { composeRule.onAllNodesWithText("In library").fetchSemanticsNodes().isNotEmpty() }
        composeRule.onRoot().performTouchInput { up() }
        composeRule.waitForIdle()
        assertEquals(8, entry(database, NOW_MEDIA_ID).effectiveProgress)
        assertEquals(groups, countRows(database, "pending_operation_group"))
        assertEquals(activities, countRows(database, "local_progress_activity"))
        // A Room eligibility change removes the dock while the finger is down.
        val detailPlus = composeRule.onNodeWithContentDescription("Increase episode to 9").performScrollTo()
        detailPlus.performTouchInput { down(center) }
        runBlocking {
            database.collectionEntryDao().upsertEntries(
                listOf(entry(database, NOW_MEDIA_ID).copy(effectiveStatus = StoredCollectionStatus.PAUSED)),
            )
        }
        waitUntil {
            composeRule
                .onAllNodes(
                    hasContentDescription("Increase episode to 9"),
                ).fetchSemanticsNodes()
                .isEmpty()
        }
        composeRule.onRoot().performTouchInput { up() }
        composeRule.waitForIdle()
        assertEquals(groups, countRows(database, "pending_operation_group"))
        assertEquals(activities, countRows(database, "local_progress_activity"))
    }

    @Test
    fun completedNowRemainsPinnedUntilThePersistedUndoDeadlineThenShowsCaughtUp() {
        val database = targetApplication().appGraph.database
        waitUntil { composeRule.onAllNodesWithText("Task 19 Now").fetchSemanticsNodes().isNotEmpty() }
        // Fixture-only setup leaves exactly one actionable unit in the account.
        database.openHelper.writableDatabase.execSQL(
            "UPDATE release SET unit_number = 12 WHERE media_id = 'task19-emulator-now'",
        )
        database.openHelper.writableDatabase.execSQL(
            "UPDATE collection_entry SET effective_status = 'completed' WHERE media_id != 'task19-emulator-now'",
        )
        runBlocking {
            database.collectionEntryDao().upsertEntries(
                listOf(entry(database, NOW_MEDIA_ID).copy(effectiveProgress = 11)),
            )
        }
        waitUntil {
            composeRule
                .onAllNodes(
                    hasContentDescription("Increase episode to 12"),
                ).fetchSemanticsNodes()
                .isNotEmpty()
        }
        composeRule.onNodeWithContentDescription("Increase episode to 12").performClick()
        waitForProgress(database, 12)
        waitUntil { composeRule.onAllNodesWithText("Undo").fetchSemanticsNodes().isNotEmpty() }
        composeRule.onNodeWithText("Task 19 Now").assertIsDisplayed()
        composeRule.onNodeWithText("Completed").assertIsDisplayed()
        val action = runBlocking { requireNotNull(database.progressDao().getLatestActivity(ACCOUNT_ID)) }
        assertEquals(6_000L, (action.undoUntilUtcMs - action.createdAtUtcMs).inWholeMilliseconds)
        waitUntil { composeRule.onAllNodesWithText("You are caught up").fetchSemanticsNodes().isNotEmpty() }
        assertEquals(1, countRows(database, "pending_operation_group"))
        assertEquals(2, countRows(database, "pending_operation"))
        assertEquals(1, countRows(database, "local_progress_activity"))
        composeRule.onNodeWithText("Undo").assertDoesNotExist()
        composeRule.activityRule.scenario.recreate()
        waitUntil { composeRule.onAllNodesWithText("You are caught up").fetchSemanticsNodes().isNotEmpty() }
        composeRule.onNodeWithText("Undo").assertDoesNotExist()
        assertEquals(1, countRows(database, "local_progress_activity"))
    }

    @Test
    fun productionRoutesHonorDisabledHapticsAndDoNotReplayOnUndoOrRecomposition() {
        val graph = targetApplication().appGraph
        val database = graph.database
        runBlocking {
            val release =
                database
                    .releaseDao()
                    .observeReleases(NOW_MEDIA_ID)
                    .first()
                    .single()
            database.releaseDao().upsertReleases(listOf(release.copy(unitNumber = 10)))
        }
        waitUntil {
            composeRule
                .onAllNodes(
                    hasContentDescription("Increase episode to 9"),
                ).fetchSemanticsNodes()
                .isNotEmpty()
        }
        val enabled = androidx.compose.runtime.mutableStateOf(false)
        val calls =
            java.util.concurrent.atomic
                .AtomicInteger()
        val feedback =
            object : androidx.compose.ui.hapticfeedback.HapticFeedback {
                override fun performHapticFeedback(
                    hapticFeedbackType: androidx.compose.ui.hapticfeedback.HapticFeedbackType,
                ) {
                    calls.incrementAndGet()
                }
            }
        val factory = requireNotNull(graph.accountGraphLifecycle.activeHandle()).withGraph { it.metroViewModelFactory }
        composeRule.activityRule.scenario.onActivity { activity ->
            activity.setContent {
                androidx.compose.runtime.CompositionLocalProvider(
                    androidx.compose.ui.platform.LocalHapticFeedback provides feedback,
                ) {
                    MioApp(
                        root = AppRoot.AUTHENTICATED,
                        navigationRegistry = graph.navigationRegistry,
                        imageLoader = graph.imageLoader,
                        viewModelFactory = factory,
                        hapticsEnabled = enabled.value,
                        reducedMotionOverride = true,
                    )
                }
            }
        }
        waitUntil {
            composeRule
                .onAllNodes(
                    hasContentDescription("Increase episode to 9"),
                ).fetchSemanticsNodes()
                .isNotEmpty()
        }
        composeRule.onNodeWithContentDescription("Increase episode to 9").performClick()
        waitForProgress(database, 9)
        composeRule.waitForIdle()
        assertEquals(0, calls.get())
        composeRule.runOnIdle { enabled.value = true }
        composeRule.onNodeWithContentDescription("Decrease episode to 8").performClick()
        waitForProgress(database, 8)
        waitUntil { calls.get() == 1 }
        composeRule.onNodeWithText("Undo").performClick()
        waitForProgress(database, 9)
        composeRule.waitForIdle()
        assertEquals(1, calls.get())
        composeRule.runOnIdle { enabled.value = false }
        composeRule.waitForIdle()
        assertEquals(1, calls.get())
        composeRule.onNodeWithText("Task 19 Now").performClick()
        waitUntil { composeRule.onAllNodesWithText("In library").fetchSemanticsNodes().isNotEmpty() }
        composeRule.onNodeWithContentDescription("Increase episode to 10").performScrollTo().performClick()
        waitForProgress(database, 10)
        composeRule.waitForIdle()
        assertEquals(1, calls.get())
        composeRule.runOnIdle { enabled.value = true }
        composeRule.onNodeWithContentDescription("Decrease episode to 9").performClick()
        waitForProgress(database, 9)
        waitUntil { calls.get() == 2 }
        composeRule.onNodeWithText("Undo").performClick()
        waitForProgress(database, 10)
        composeRule.waitForIdle()
        assertEquals(2, calls.get())
    }

    private fun clickVisibleControl(description: String) {
        val control = composeRule.onNodeWithContentDescription(description).performScrollTo()
        val scroll = composeRule.onAllNodes(hasScrollAction())[0]
        val viewport = scroll.fetchSemanticsNode().boundsInRoot
        val center = control.fetchSemanticsNode().boundsInRoot.center
        scroll.performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, center.y - viewport.center.y) }
        composeRule.waitForIdle()
        control.assertIsDisplayed().performClick()
    }

    private fun entry(
        database: MioDatabase,
        mediaId: String,
    ) = runBlocking {
        requireNotNull(database.progressDao().getEntryForMedia(ACCOUNT_ID, mediaId))
    }

    private fun targetApplication(): MioApplication =
        InstrumentationRegistry
            .getInstrumentation()
            .targetContext
            .applicationContext as MioApplication

    private fun waitUntil(condition: () -> Boolean) {
        composeRule.waitUntil(timeoutMillis = 30_000) { condition() }
    }

    private fun waitForProgress(
        database: MioDatabase,
        expected: Int,
    ) {
        waitUntil {
            runBlocking {
                database.progressDao().getEntryForMedia(ACCOUNT_ID, NOW_MEDIA_ID)?.effectiveProgress == expected
            }
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
        const val ACCOUNT_ID = "task19-emulator-account"
        const val NOW_MEDIA_ID = "task19-emulator-now"
    }
}
