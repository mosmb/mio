package io.morgan.mio.core.database

import android.database.sqlite.SQLiteConstraintException
import androidx.test.core.app.ApplicationProvider
import io.morgan.mio.core.database.entity.CollectionEntryEntity
import io.morgan.mio.core.database.entity.PendingOperationDependencyEntity
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
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [35])
class PendingOperationDaoTest {
    private lateinit var database: MioDatabase

    @Before
    fun setUp() {
        runTest {
            database = MioDatabaseFactory.createInMemory(ApplicationProvider.getApplicationContext())
            database.selectedAccountDao().replaceSelectedAccount(account("account-dao"), accountMapping("account-dao"))
            database.mediaDao().upsertMedia(listOf(media("media-dao"), media("media-dao-2")))
            database.collectionEntryDao().upsertEntries(
                listOf(
                    entry("entry-dao", "account-dao", "media-dao"),
                    entry("entry-dao-2", "account-dao", "media-dao-2"),
                ),
            )
        }
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun persistsGroupsOperationsDependenciesAndReadsStableOperationOrder() =
        runTest {
            database.pendingOperationDao().insertGroup(group("group-dao"))
            val progress =
                operation(
                    "operation-progress",
                    "group-dao",
                    "entry-dao",
                    0,
                )
            val status =
                statusOperation(
                    id = "operation-status",
                    groupId = "group-dao",
                    position = 1,
                )
            database.pendingOperationDao().insertOperations(listOf(status, progress))
            database.pendingOperationDao().insertDependencies(
                listOf(PendingOperationDependencyEntity(status.operationId, progress.operationId)),
            )

            assertEquals("group-dao", database.pendingOperationDao().getGroup("group-dao")?.operationGroupId)
            assertEquals(
                listOf("operation-progress", "operation-status"),
                database.pendingOperationDao().getOperationsForGroup("group-dao").map { it.operationId },
            )
            assertEquals(
                PendingOperationDependencyEntity("operation-status", "operation-progress"),
                database.pendingOperationDao().getDependenciesForOperation("operation-status").single(),
            )
            val storedStatus = database.pendingOperationDao().getOperation("operation-status")
            assertNotNull(storedStatus)
            assertEquals(StoredPendingOperationKind.SET_STATUS, storedStatus?.kind)
            assertEquals(StoredCollectionStatus.CURRENT, storedStatus?.baseStatus)
            assertEquals(StoredCollectionStatus.COMPLETED, storedStatus?.targetStatus)
            assertEquals(StoredPendingOperationState.ENQUEUED, storedStatus?.lifecycleState)
            assertEquals(0, storedStatus?.attemptCount)
        }

    @Test
    fun abortsDuplicateGroupOperationAndPositionWithoutReplacingRows() =
        runTest {
            database.pendingOperationDao().insertGroup(group("group-duplicates"))
            val original = operation("operation-original", "group-duplicates", "entry-dao", 0)
            database.pendingOperationDao().insertOperations(listOf(original))

            assertConstraint {
                database.pendingOperationDao().insertGroup(group("group-duplicates"))
            }
            assertConstraint {
                database.pendingOperationDao().insertOperations(
                    listOf(operation("operation-original", "group-duplicates", "entry-dao", 1)),
                )
            }
            assertConstraint {
                database.pendingOperationDao().insertOperations(
                    listOf(operation("operation-position", "group-duplicates", "entry-dao", 0)),
                )
            }

            assertEquals(1, countRows("pending_operation_group"))
            assertEquals(1, countRows("pending_operation"))
            assertEquals(
                "operation-original",
                database.pendingOperationDao().getOperation("operation-original")?.operationId,
            )
        }

    @Test
    fun usesDatabaseGroupOrderWhenCreatedAtIsEqual() =
        runTest {
            database.pendingOperationDao().insertGroup(group("group-first"))
            database.pendingOperationDao().insertOperations(
                listOf(operation("operation-first", "group-first", "entry-dao", 0)),
            )
            database.pendingOperationDao().insertGroup(group("group-second"))
            database.pendingOperationDao().insertOperations(
                listOf(operation("operation-second", "group-second", "entry-dao", 0)),
            )

            assertEquals(
                listOf("operation-first", "operation-second"),
                database.pendingOperationDao().getOperationsInEnqueueOrder("account-dao").map { it.operationId },
            )
        }

    @Test
    fun foreignKeysAndCascadesProtectAndRemovePendingIntent() =
        runTest {
            assertConstraint {
                database.pendingOperationDao().insertGroup(group("missing-account", accountId = "missing"))
            }

            database.pendingOperationDao().insertGroup(group("group-cascade"))
            val predecessor = operation("operation-predecessor", "group-cascade", "entry-dao", 0)
            val dependent = operation("operation-dependent", "group-cascade", "entry-dao", 1)
            database.pendingOperationDao().insertOperations(listOf(predecessor, dependent))
            database.pendingOperationDao().insertDependencies(
                listOf(PendingOperationDependencyEntity(dependent.operationId, predecessor.operationId)),
            )

            assertConstraint {
                database.pendingOperationDao().insertOperations(
                    listOf(operation("missing-group", "missing-group", "entry-dao", 0)),
                )
            }
            assertConstraint {
                database.pendingOperationDao().insertOperations(
                    listOf(
                        operation("missing-operation-account", "group-cascade", "entry-dao", 2)
                            .copy(accountId = "missing"),
                    ),
                )
            }

            assertConstraint {
                database.pendingOperationDao().insertOperations(
                    listOf(operation("missing-entry", "group-cascade", "missing-entry", 2)),
                )
            }
            assertConstraint {
                database.pendingOperationDao().insertDependencies(
                    listOf(PendingOperationDependencyEntity("missing-dependent", predecessor.operationId)),
                )
            }
            assertConstraint {
                database.pendingOperationDao().insertDependencies(
                    listOf(PendingOperationDependencyEntity(dependent.operationId, "missing-predecessor")),
                )
            }

            database.openHelper.writableDatabase.execSQL(
                "DELETE FROM pending_operation WHERE operation_id = 'operation-predecessor'",
            )
            assertEquals(1, countRows("pending_operation"))
            assertEquals(0, countRows("pending_operation_dependency"))

            database.openHelper.writableDatabase.execSQL(
                "DELETE FROM pending_operation_group WHERE operation_group_id = 'group-cascade'",
            )
            assertEquals(0, countRows("pending_operation"))

            database.pendingOperationDao().insertGroup(group("group-account-cascade"))
            database.pendingOperationDao().insertOperations(
                listOf(operation("operation-account-cascade", "group-account-cascade", "entry-dao", 0)),
            )
            database.openHelper.writableDatabase.execSQL(
                "DELETE FROM selected_account WHERE account_id = 'account-dao'",
            )
            assertEquals(0, countRows("pending_operation_group"))
            assertEquals(0, countRows("pending_operation"))
            assertEquals(0, countRows("pending_operation_dependency"))
        }

    @Test
    fun deletingACollectionEntryCascadesOnlyItsPendingIntent() =
        runTest {
            database.pendingOperationDao().insertGroup(group("group-entry-one"))
            database.pendingOperationDao().insertOperations(
                listOf(operation("operation-entry-one", "group-entry-one", "entry-dao", 0)),
            )
            database.pendingOperationDao().insertGroup(group("group-entry-two"))
            database.pendingOperationDao().insertOperations(
                listOf(operation("operation-entry-two", "group-entry-two", "entry-dao-2", 0)),
            )

            database.openHelper.writableDatabase.execSQL(
                "DELETE FROM collection_entry WHERE collection_entry_id = 'entry-dao'",
            )

            assertEquals(null, database.pendingOperationDao().getOperation("operation-entry-one"))
            assertNotNull(database.pendingOperationDao().getOperation("operation-entry-two"))
        }

    @Test
    fun compensationLinkSurvivesOriginalGroupDeletion() =
        runTest {
            database.pendingOperationDao().insertGroup(group("group-original"))
            database.pendingOperationDao().insertOperations(
                listOf(operation("operation-original-group", "group-original", "entry-dao", 0)),
            )
            database.pendingOperationDao().insertGroup(
                group("group-compensation", compensatesGroupId = "group-original"),
            )
            database.pendingOperationDao().insertOperations(
                listOf(
                    operation(
                        id = "operation-compensation",
                        groupId = "group-compensation",
                        entryId = "entry-dao",
                        position = 0,
                        baseProgress = 2,
                        targetProgress = 1,
                    ),
                ),
            )

            database.openHelper.writableDatabase.execSQL(
                "DELETE FROM pending_operation_group WHERE operation_group_id = 'group-original'",
            )

            assertEquals(
                "group-original",
                database.pendingOperationDao().getGroup("group-compensation")?.compensatesGroupId,
            )
            assertEquals(1, countRows("pending_operation"))
        }

    @Test
    fun compensationLinkDoesNotRequireAnExistingOriginalAndRejectsSelfReference() =
        runTest {
            database.pendingOperationDao().insertGroup(
                group("group-compensation-without-original", compensatesGroupId = "already-reconciled-group"),
            )

            assertEquals(
                "already-reconciled-group",
                database.pendingOperationDao().getGroup("group-compensation-without-original")?.compensatesGroupId,
            )
            assertThrows(IllegalArgumentException::class.java) {
                group("group-self-compensation", compensatesGroupId = "group-self-compensation")
            }
        }

    private fun group(
        groupId: String,
        accountId: String = "account-dao",
        compensatesGroupId: String? = null,
    ): PendingOperationGroupEntity =
        PendingOperationGroupEntity(
            operationGroupId = groupId,
            accountId = accountId,
            createdAtUtcMs = INSTANT,
            compensatesGroupId = compensatesGroupId,
        )

    private fun operation(
        id: String,
        groupId: String,
        entryId: String,
        position: Int,
        baseProgress: Int = 1,
        targetProgress: Int = 2,
    ): PendingOperationEntity =
        PendingOperationEntity(
            operationId = id,
            operationGroupId = groupId,
            accountId = "account-dao",
            collectionEntryId = entryId,
            positionInGroup = position,
            kind =
                if (targetProgress > baseProgress) {
                    StoredPendingOperationKind.SET_PROGRESS_FORWARD
                } else {
                    StoredPendingOperationKind.SET_PROGRESS_CORRECTION
                },
            baseProgress = baseProgress,
            targetProgress = targetProgress,
            baseStatus = null,
            targetStatus = null,
            lifecycleState = StoredPendingOperationState.ENQUEUED,
            attemptCount = 0,
            lastAttemptAtUtcMs = null,
            retryNotBeforeUtcMs = null,
        )

    private fun statusOperation(
        id: String,
        groupId: String,
        position: Int,
    ): PendingOperationEntity =
        PendingOperationEntity(
            operationId = id,
            operationGroupId = groupId,
            accountId = "account-dao",
            collectionEntryId = "entry-dao",
            positionInGroup = position,
            kind = StoredPendingOperationKind.SET_STATUS,
            baseProgress = null,
            targetProgress = null,
            baseStatus = StoredCollectionStatus.CURRENT,
            targetStatus = StoredCollectionStatus.COMPLETED,
            lifecycleState = StoredPendingOperationState.ENQUEUED,
            attemptCount = 0,
            lastAttemptAtUtcMs = null,
            retryNotBeforeUtcMs = null,
        )

    private fun account(accountId: String): SelectedAccountEntity =
        SelectedAccountEntity(
            selectionKey = "selected",
            accountId = accountId,
            connectedProvider = StoredConnectedProvider.ANILIST,
            displayName = "Account $accountId",
            avatarReference = null,
        )

    private fun accountMapping(accountId: String): io.morgan.mio.core.database.entity.AccountProviderIdEntity =
        io.morgan.mio.core.database.entity.AccountProviderIdEntity(
            accountId = accountId,
            provider = StoredConnectedProvider.ANILIST,
            remoteAccountId = "remote-$accountId",
        )

    private fun media(mediaId: String): io.morgan.mio.core.database.entity.MediaEntity =
        io.morgan.mio.core.database.entity.MediaEntity(
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
        entryId: String,
        accountId: String,
        mediaId: String,
    ): CollectionEntryEntity =
        CollectionEntryEntity(
            collectionEntryId = entryId,
            accountId = accountId,
            mediaId = mediaId,
            progressUnit = StoredProgressUnit.EPISODE,
            remoteStatus = StoredCollectionStatus.CURRENT,
            remoteProgress = 1,
            remoteTotal = 12,
            remoteUpdatedAtUtcMs = INSTANT,
            effectiveStatus = StoredCollectionStatus.CURRENT,
            effectiveProgress = 1,
            effectiveTotal = 12,
            effectiveUpdatedAtUtcMs = INSTANT,
        )

    private fun countRows(tableName: String): Int =
        database.openHelper.readableDatabase.query("SELECT COUNT(*) FROM $tableName").use { cursor ->
            cursor.moveToFirst()
            cursor.getInt(0)
        }

    private suspend fun assertConstraint(block: suspend () -> Unit) {
        try {
            block()
            throw AssertionError("Expected SQLiteConstraintException")
        } catch (_: SQLiteConstraintException) {
            // Expected constraint violation.
        }
    }

    private companion object {
        val INSTANT = Instant.parse("2026-01-02T03:04:05.006Z")
    }
}
