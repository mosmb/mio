package io.morgan.mio.data.sync

import android.database.sqlite.SQLiteConstraintException
import androidx.test.core.app.ApplicationProvider
import io.morgan.mio.core.database.MioDatabase
import io.morgan.mio.core.database.MioDatabaseFactory
import io.morgan.mio.core.database.entity.AccountProviderIdEntity
import io.morgan.mio.core.database.entity.CollectionEntryEntity
import io.morgan.mio.core.database.entity.MediaEntity
import io.morgan.mio.core.database.entity.PendingOperationEntity
import io.morgan.mio.core.database.entity.PendingOperationGroupEntity
import io.morgan.mio.core.database.entity.SelectedAccountEntity
import io.morgan.mio.core.database.model.StoredCollectionStatus
import io.morgan.mio.core.database.model.StoredConnectedProvider
import io.morgan.mio.core.database.model.StoredMediaFormat
import io.morgan.mio.core.database.model.StoredMediaPublicationStatus
import io.morgan.mio.core.database.model.StoredMediaType
import io.morgan.mio.core.database.model.StoredPendingOperationKind
import io.morgan.mio.core.database.model.StoredPendingOperationState
import io.morgan.mio.core.database.model.StoredProgressUnit
import io.morgan.mio.data.sync.local.RoomPendingOperationLocalDataSource
import io.morgan.mio.domain.collection.CollectionStatus
import io.morgan.mio.domain.model.AccountId
import io.morgan.mio.domain.model.CollectionEntryId
import io.morgan.mio.domain.model.OperationGroupId
import io.morgan.mio.domain.model.OperationId
import io.morgan.mio.domain.model.ProgressValue
import io.morgan.mio.domain.sync.OperationValue
import io.morgan.mio.domain.sync.PendingOperation
import io.morgan.mio.domain.sync.PendingOperationGroup
import io.morgan.mio.domain.sync.PendingOperationKind
import io.morgan.mio.domain.sync.PendingOperationState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [35])
class PendingOperationWriterTest {
    private lateinit var database: MioDatabase
    private lateinit var writer: PendingOperationWriter

    @Before
    fun setUp() =
        runTest {
            database = MioDatabaseFactory.createInMemory(ApplicationProvider.getApplicationContext())
            database.selectedAccountDao().replaceSelectedAccount(account(ACCOUNT_ID), accountMapping(ACCOUNT_ID))
            database.mediaDao().upsertMedia(listOf(media(MEDIA_ID)))
            database.collectionEntryDao().upsertEntries(listOf(entry(ENTRY_ID, ACCOUNT_ID)))
            writer =
                DefaultPendingOperationWriter(
                    accountId = ACCOUNT_ID,
                    localDataSource = RoomPendingOperationLocalDataSource(database),
                )
        }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun accountScopedWriterRejectsAnotherAccountBeforeWriting() =
        runTest {
            val failure =
                expectFailure {
                    writer.enqueue(group("group-other-account", accountId = AccountId("account-other")))
                }
            assertTrue(failure is IllegalArgumentException)
            assertEquals(0, countRows("pending_operation_group"))
        }

    @Test
    fun writerRejectsNonEnqueuedOperationsBeforeWriting() =
        runTest {
            val operation =
                operation(
                    id = "operation-in-flight",
                    groupId = OperationGroupId("group-in-flight"),
                    state = PendingOperationState.IN_FLIGHT,
                    attemptCount = 1,
                    lastAttemptAt = INSTANT,
                )
            val failure =
                expectFailure {
                    writer.enqueue(group("group-in-flight", operations = listOf(operation)))
                }
            assertTrue(failure is IllegalArgumentException)
            assertEquals(0, countRows("pending_operation_group"))
        }

    @Test
    fun sourceRejectsCollectionEntryFromAnotherAccount() =
        runTest {
            insertOtherAccountAndEntry()
            val operation =
                operation(
                    id = "operation-cross-entry",
                    groupId = OperationGroupId("group-cross-entry"),
                    entryId = CollectionEntryId("entry-other"),
                )
            val failure =
                expectFailure {
                    writer.enqueue(group("group-cross-entry", operations = listOf(operation)))
                }
            assertTrue(failure is IllegalArgumentException)
            assertEquals(0, countRows("pending_operation_group"))
        }

    @Test
    fun validOperationPersistsInitialLifecycleMetadata() =
        runTest {
            writer.enqueue(group("group-valid"))

            val stored = database.pendingOperationDao().getOperation("operation-group-valid")
            assertEquals(StoredPendingOperationState.ENQUEUED, stored?.lifecycleState)
            assertEquals(0, stored?.attemptCount)
            assertNull(stored?.lastAttemptAtUtcMs)
            assertNull(stored?.retryNotBeforeUtcMs)
        }

    @Test
    fun groupedProgressAndAutomaticStatusPersistInOrderWithDependency() =
        runTest {
            val groupId = OperationGroupId("group-automatic")
            val progress =
                operation(
                    id = "operation-progress",
                    groupId = groupId,
                    position = 0,
                )
            val status =
                statusOperation(
                    id = "operation-status",
                    groupId = groupId,
                    position = 1,
                    dependsOn = setOf(progress.id),
                )
            writer.enqueue(group("group-automatic", operations = listOf(status, progress)))

            assertEquals(
                listOf("operation-progress", "operation-status"),
                database.pendingOperationDao().getOperationsForGroup("group-automatic").map { it.operationId },
            )
            assertEquals(
                listOf("operation-progress"),
                database
                    .pendingOperationDao()
                    .getDependenciesForOperation("operation-status")
                    .map { it.dependsOnOperationId },
            )
        }

    @Test
    fun statusGroupTargetingRepeatingPersists() =
        runTest {
            val repeatingOperation =
                PendingOperation(
                    id = OperationId("operation-repeating"),
                    groupId = OperationGroupId("group-repeating"),
                    accountId = ACCOUNT_ID,
                    collectionEntryId = ENTRY_ID,
                    kind = PendingOperationKind.SET_STATUS,
                    base = OperationValue.Status(CollectionStatus.CURRENT),
                    target = OperationValue.Status(CollectionStatus.REPEATING),
                    positionInGroup = 0,
                    dependsOn = emptySet(),
                    state = PendingOperationState.ENQUEUED,
                    attemptCount = 0,
                    lastAttemptAt = null,
                )
            writer.enqueue(group("group-repeating", operations = listOf(repeatingOperation)))

            val stored = database.pendingOperationDao().getOperation("operation-repeating")
            assertEquals(StoredCollectionStatus.CURRENT, stored?.baseStatus)
            assertEquals(StoredCollectionStatus.REPEATING, stored?.targetStatus)
        }

    @Test
    fun externalPredecessorMustExistAndMatchTheAccount() =
        runTest {
            val predecessorId = OperationId("operation-predecessor")
            database.pendingOperationDao().insertGroup(
                PendingOperationGroupEntity(
                    operationGroupId = "group-predecessor",
                    accountId = ACCOUNT_ID.value,
                    createdAtUtcMs = INSTANT,
                    compensatesGroupId = null,
                ),
            )
            database.pendingOperationDao().insertOperations(
                listOf(
                    PendingOperationEntity(
                        operationId = predecessorId.value,
                        operationGroupId = "group-predecessor",
                        accountId = ACCOUNT_ID.value,
                        collectionEntryId = ENTRY_ID.value,
                        positionInGroup = 0,
                        kind = StoredPendingOperationKind.SET_PROGRESS_FORWARD,
                        baseProgress = 1,
                        targetProgress = 2,
                        baseStatus = null,
                        targetStatus = null,
                        lifecycleState = StoredPendingOperationState.ENQUEUED,
                        attemptCount = 0,
                        lastAttemptAtUtcMs = null,
                        retryNotBeforeUtcMs = null,
                    ),
                ),
            )

            val dependent =
                operation(
                    id = "operation-dependent",
                    groupId = OperationGroupId("group-dependent"),
                    dependsOn = setOf(predecessorId),
                )
            writer.enqueue(group("group-dependent", operations = listOf(dependent)))
            assertEquals(
                listOf(predecessorId.value),
                database
                    .pendingOperationDao()
                    .getDependenciesForOperation("operation-dependent")
                    .map { it.dependsOnOperationId },
            )

            val missing =
                operation(
                    id = "operation-missing-predecessor",
                    groupId = OperationGroupId("group-missing-predecessor"),
                    dependsOn = setOf(OperationId("missing-predecessor")),
                )
            val missingFailure =
                expectFailure {
                    writer.enqueue(group("group-missing-predecessor", operations = listOf(missing)))
                }
            assertTrue(missingFailure is IllegalArgumentException)
            assertNull(database.pendingOperationDao().getGroup("group-missing-predecessor"))

            insertOtherAccountAndEntry()
            database.pendingOperationDao().insertGroup(
                PendingOperationGroupEntity(
                    operationGroupId = "group-other-predecessor",
                    accountId = OTHER_ACCOUNT_ID.value,
                    createdAtUtcMs = INSTANT,
                    compensatesGroupId = null,
                ),
            )
            database.pendingOperationDao().insertOperations(
                listOf(
                    PendingOperationEntity(
                        operationId = "operation-other-predecessor",
                        operationGroupId = "group-other-predecessor",
                        accountId = OTHER_ACCOUNT_ID.value,
                        collectionEntryId = "entry-other",
                        positionInGroup = 0,
                        kind = StoredPendingOperationKind.SET_PROGRESS_FORWARD,
                        baseProgress = 1,
                        targetProgress = 2,
                        baseStatus = null,
                        targetStatus = null,
                        lifecycleState = StoredPendingOperationState.ENQUEUED,
                        attemptCount = 0,
                        lastAttemptAtUtcMs = null,
                        retryNotBeforeUtcMs = null,
                    ),
                ),
            )
            val crossAccount =
                operation(
                    id = "operation-cross-account-predecessor",
                    groupId = OperationGroupId("group-cross-account-predecessor"),
                    dependsOn = setOf(OperationId("operation-other-predecessor")),
                )
            val crossAccountFailure =
                expectFailure {
                    writer.enqueue(group("group-cross-account-predecessor", operations = listOf(crossAccount)))
                }
            assertTrue(crossAccountFailure is IllegalArgumentException)
            assertNull(database.pendingOperationDao().getGroup("group-cross-account-predecessor"))
        }

    @Test
    fun compensationGroupPersistsReversedAbsoluteIntent() =
        runTest {
            val operation =
                operation(
                    id = "operation-compensation",
                    groupId = OperationGroupId("group-compensation"),
                    baseProgress = 2,
                    targetProgress = 1,
                    kind = PendingOperationKind.SET_PROGRESS_CORRECTION,
                )
            writer.enqueue(
                group(
                    id = "group-compensation",
                    operations = listOf(operation),
                    compensatesGroupId = OperationGroupId("group-original"),
                ),
            )
            val stored = database.pendingOperationDao().getOperation("operation-compensation")
            assertEquals(StoredPendingOperationKind.SET_PROGRESS_CORRECTION, stored?.kind)
            assertEquals(2, stored?.baseProgress)
            assertEquals(1, stored?.targetProgress)
            assertEquals(
                "group-original",
                database.pendingOperationDao().getGroup("group-compensation")?.compensatesGroupId,
            )
        }

    @Test
    fun outerTransactionCommitsEffectiveStateAndIntentTogether() =
        runTest {
            database.inTransaction {
                database.collectionEntryDao().upsertEntries(
                    listOf(entry(ENTRY_ID, ACCOUNT_ID, effectiveProgress = 2)),
                )
                writer.enqueue(group("group-outer-success"))
            }

            assertEquals(
                2,
                requireNotNull(database.collectionEntryDao().observeEntry(ENTRY_ID.value).first()).effectiveProgress,
            )
            assertEquals(1, countRows("pending_operation_group"))
            assertEquals(1, countRows("pending_operation"))
        }

    @Test
    fun duplicateOperationFailureRollsBackEffectiveStateAndNewIntent() =
        runTest {
            writer.enqueue(group("group-existing", operationId = "operation-duplicate"))
            val failure =
                expectFailure {
                    database.inTransaction {
                        database.collectionEntryDao().upsertEntries(
                            listOf(entry(ENTRY_ID, ACCOUNT_ID, effectiveProgress = 4)),
                        )
                        writer.enqueue(group("group-duplicate-attempt", operationId = "operation-duplicate"))
                    }
                }
            assertTrue(failure is SQLiteConstraintException)
            assertEquals(
                1,
                requireNotNull(database.collectionEntryDao().observeEntry(ENTRY_ID.value).first()).effectiveProgress,
            )
            assertEquals(1, countRows("pending_operation_group"))
            assertNull(database.pendingOperationDao().getGroup("group-duplicate-attempt"))
        }

    @Test
    fun exceptionAfterWriterCallRollsBackEffectiveStateAndIntent() =
        runTest {
            val failure =
                expectFailure {
                    database.inTransaction {
                        database.collectionEntryDao().upsertEntries(
                            listOf(entry(ENTRY_ID, ACCOUNT_ID, effectiveProgress = 5)),
                        )
                        writer.enqueue(group("group-throw-after-enqueue"))
                        error("test rollback")
                    }
                }
            assertTrue(failure is IllegalStateException)
            assertEquals(
                1,
                requireNotNull(database.collectionEntryDao().observeEntry(ENTRY_ID.value).first()).effectiveProgress,
            )
            assertEquals(0, countRows("pending_operation_group"))
            assertEquals(0, countRows("pending_operation"))
        }

    private suspend fun insertOtherAccountAndEntry() {
        database.openHelper.writableDatabase.execSQL(
            "INSERT OR IGNORE INTO selected_account " +
                "(selection_key, account_id, connected_provider, display_name, avatar_reference) " +
                "VALUES ('other-selection', 'account-other', 'anilist', 'Other account', NULL)",
        )
        database.collectionEntryDao().upsertEntries(listOf(entry(CollectionEntryId("entry-other"), OTHER_ACCOUNT_ID)))
    }

    private fun group(
        id: String,
        accountId: AccountId = ACCOUNT_ID,
        operationId: String = "operation-$id",
        operations: List<PendingOperation> =
            listOf(operation(operationId, OperationGroupId(id), accountId = accountId)),
        compensatesGroupId: OperationGroupId? = null,
    ): PendingOperationGroup =
        PendingOperationGroup(
            id = OperationGroupId(id),
            accountId = accountId,
            createdAt = INSTANT,
            operations = operations,
            compensatesGroupId = compensatesGroupId,
        )

    private fun operation(
        id: String,
        groupId: OperationGroupId,
        entryId: CollectionEntryId = ENTRY_ID,
        position: Int = 0,
        baseProgress: Int = 1,
        targetProgress: Int = 2,
        kind: PendingOperationKind = PendingOperationKind.SET_PROGRESS_FORWARD,
        dependsOn: Set<OperationId> = emptySet(),
        accountId: AccountId = ACCOUNT_ID,
        state: PendingOperationState = PendingOperationState.ENQUEUED,
        attemptCount: Int = 0,
        lastAttemptAt: Instant? = null,
    ): PendingOperation {
        val base: OperationValue =
            if (kind == PendingOperationKind.SET_STATUS) {
                OperationValue.Status(CollectionStatus.CURRENT)
            } else {
                OperationValue.Progress(ProgressValue(baseProgress))
            }
        val target: OperationValue =
            if (kind == PendingOperationKind.SET_STATUS) {
                OperationValue.Status(CollectionStatus.COMPLETED)
            } else {
                OperationValue.Progress(ProgressValue(targetProgress))
            }
        return PendingOperation(
            id = OperationId(id),
            groupId = groupId,
            accountId = accountId,
            collectionEntryId = entryId,
            kind = kind,
            base = base,
            target = target,
            positionInGroup = position,
            dependsOn = dependsOn,
            state = state,
            attemptCount = attemptCount,
            lastAttemptAt = lastAttemptAt,
        )
    }

    private fun statusOperation(
        id: String,
        groupId: OperationGroupId,
        position: Int,
        dependsOn: Set<OperationId>,
    ): PendingOperation =
        operation(
            id = id,
            groupId = groupId,
            position = position,
            kind = PendingOperationKind.SET_STATUS,
            dependsOn = dependsOn,
        )

    private fun account(accountId: AccountId): SelectedAccountEntity =
        SelectedAccountEntity(
            selectionKey = "selected",
            accountId = accountId.value,
            connectedProvider = StoredConnectedProvider.ANILIST,
            displayName = "Account ${accountId.value}",
            avatarReference = null,
        )

    private fun accountMapping(accountId: AccountId) =
        AccountProviderIdEntity(
            accountId = accountId.value,
            provider = StoredConnectedProvider.ANILIST,
            remoteAccountId = "remote-${accountId.value}",
        )

    private fun media(mediaId: String): MediaEntity =
        MediaEntity(
            mediaId = mediaId,
            mediaType = StoredMediaType.ANIME,
            title = "Title $mediaId",
            format = StoredMediaFormat.TV,
            publicationStatus = StoredMediaPublicationStatus.RELEASING,
            synopsis = null,
            posterReference = null,
            bannerReference = null,
            episodeTotal = 12,
            chapterTotal = null,
            volumeTotal = null,
        )

    private fun entry(
        entryId: CollectionEntryId,
        accountId: AccountId,
        effectiveProgress: Int = 1,
    ): CollectionEntryEntity =
        CollectionEntryEntity(
            collectionEntryId = entryId.value,
            accountId = accountId.value,
            mediaId = MEDIA_ID,
            progressUnit = StoredProgressUnit.EPISODE,
            remoteStatus = StoredCollectionStatus.CURRENT,
            remoteProgress = 1,
            remoteTotal = 12,
            remoteUpdatedAtUtcMs = INSTANT,
            effectiveStatus = StoredCollectionStatus.CURRENT,
            effectiveProgress = effectiveProgress,
            effectiveTotal = 12,
            effectiveUpdatedAtUtcMs = INSTANT,
        )

    private suspend fun expectFailure(block: suspend () -> Unit): Throwable {
        var failure: Throwable? = null
        try {
            block()
        } catch (throwable: Throwable) {
            failure = throwable
        }
        return failure ?: error("Expected operation to fail")
    }

    private fun countRows(tableName: String): Int =
        database.openHelper.readableDatabase.query("SELECT COUNT(*) FROM $tableName").use { cursor ->
            cursor.moveToFirst()
            cursor.getInt(0)
        }

    private companion object {
        val ACCOUNT_ID = AccountId("account-writer")
        val OTHER_ACCOUNT_ID = AccountId("account-other")
        val ENTRY_ID = CollectionEntryId("entry-writer")
        const val MEDIA_ID = "media-writer"
        val INSTANT = Instant.parse("2026-01-02T03:04:05.006Z")
    }
}
