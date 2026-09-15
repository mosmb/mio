package io.morgan.mio

import androidx.test.core.app.ApplicationProvider
import io.morgan.mio.core.database.model.StoredPendingOperationState
import io.morgan.mio.data.progress.DefaultProgressRepository
import io.morgan.mio.data.progress.local.RoomProgressLocalDataSource
import io.morgan.mio.data.sync.DefaultPendingOperationWriter
import io.morgan.mio.data.sync.local.RoomPendingOperationLocalDataSource
import io.morgan.mio.domain.model.AccountId
import io.morgan.mio.domain.model.MediaId
import io.morgan.mio.domain.model.OperationGroupId
import io.morgan.mio.domain.progress.ProgressDirection
import io.morgan.mio.domain.progress.ProgressWriteResult
import io.morgan.mio.domain.progress.StepProgressUseCase
import io.morgan.mio.domain.progress.UndoProgressUseCase
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds

public class SyncEmulatorTest {
    @Before
    public fun requireExplicitFixtureOptIn() {
        assumeTrue(
            androidx.test.platform.app.InstrumentationRegistry
                .getArguments()
                .getString("task20Fixture") == "true",
        )
    }

    @Test
    public fun deliveredUndoRestoresProgressAndAutomaticStatus() = verifyUndo(waitForDelivery = true)

    @Test
    public fun inFlightUndoDoesNotResurrectTheForwardTarget() = verifyUndo(waitForDelivery = false)

    private fun verifyUndo(waitForDelivery: Boolean) =
        runBlocking {
            val app = ApplicationProvider.getApplicationContext<SyncEmulatorApplication>()
            check(File(app.filesDir, "task20-marker").exists())
            val database = app.appGraph.database
            val before = checkNotNull(database.syncDao().getEntry("entry-one"))
            val account = AccountId("task20-account")
            val repository =
                DefaultProgressRepository(
                    account,
                    database,
                    RoomProgressLocalDataSource(database),
                    DefaultPendingOperationWriter(account, RoomPendingOperationLocalDataSource(database)),
                    Clock.System,
                )
            val action = OperationGroupId("undo-device-${System.currentTimeMillis()}")
            val result =
                StepProgressUseCase(
                    repository,
                    Clock.System,
                )(MediaId("media-one"), ProgressDirection.INCREASE, action, 6.seconds)
            assertTrue("Progress action must commit: $result", result is ProgressWriteResult.Committed)
            withTimeout(5.seconds) {
                while (true) {
                    val operations = database.syncDao().getOperationsForGroup(action.value)
                    if (waitForDelivery &&
                        operations.isNotEmpty() &&
                        operations.all { it.lifecycleState == StoredPendingOperationState.DELIVERED }
                    ) {
                        break
                    }
                    if (!waitForDelivery &&
                        operations.any { it.lifecycleState == StoredPendingOperationState.IN_FLIGHT }
                    ) {
                        break
                    }
                    delay(20)
                }
            }
            val undo = UndoProgressUseCase(repository, Clock.System)(action)
            assertTrue("Undo must commit inside original deadline: $undo", undo is ProgressWriteResult.Committed)
            assertEquals(before.effectiveProgress, database.syncDao().getEntry("entry-one")?.effectiveProgress)
            assertEquals(before.effectiveStatus, database.syncDao().getEntry("entry-one")?.effectiveStatus)
            withTimeout(120.seconds) {
                while (true) {
                    val after = checkNotNull(database.syncDao().getEntry("entry-one"))
                    val inverse = database.syncDao().getOperationsForGroup("${action.value}:undo")
                    if (after.remoteProgress == before.remoteProgress &&
                        after.remoteStatus == before.remoteStatus &&
                        inverse.all { it.lifecycleState == StoredPendingOperationState.DELIVERED } &&
                        database.syncDao().getIssues(account.value).isEmpty()
                    ) {
                        break
                    }
                    delay(100)
                }
            }
            assertEquals(before.effectiveProgress, database.syncDao().getEntry("entry-one")?.effectiveProgress)
            File(
                app.filesDir,
                "task20-undo-result.txt",
            ).writeText(
                "waitForDelivery=$waitForDelivery\nbefore=$before\nafter=${database.syncDao().getEntry(
                    "entry-one",
                )}\noperations=${database.syncDao().getOrderedOperations(account.value)}",
            )
        }
}
