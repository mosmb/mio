package io.morgan.mio.core.database

import android.database.sqlite.SQLiteConstraintException
import androidx.test.core.app.ApplicationProvider
import io.morgan.mio.core.database.entity.AccountProviderIdEntity
import io.morgan.mio.core.database.entity.CollectionEntryEntity
import io.morgan.mio.core.database.entity.MediaEntity
import io.morgan.mio.core.database.entity.MediaProviderIdEntity
import io.morgan.mio.core.database.entity.RelatedMediaEntity
import io.morgan.mio.core.database.entity.ReleaseEntity
import io.morgan.mio.core.database.entity.SelectedAccountEntity
import io.morgan.mio.core.database.model.StoredCollectionStatus
import io.morgan.mio.core.database.model.StoredConnectedProvider
import io.morgan.mio.core.database.model.StoredMediaFormat
import io.morgan.mio.core.database.model.StoredMediaPublicationStatus
import io.morgan.mio.core.database.model.StoredMediaType
import io.morgan.mio.core.database.model.StoredProgressUnit
import io.morgan.mio.core.database.model.StoredReleaseTimeKind
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [35])
class MioDatabaseTest {
    private lateinit var database: MioDatabase

    @Before
    fun setUp() {
        database = MioDatabaseFactory.createInMemory(ApplicationProvider.getApplicationContext())
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun inMemoryDatabaseEnablesForeignKeysAndSelectedAccountReplacementIsSingleRow() =
        runTest {
            assertEquals(1, pragmaForeignKeys())
            assertNull(database.selectedAccountDao().observeSelectedAccount().first())

            database.selectedAccountDao().replaceSelectedAccount(account("account-1"), accountMapping("account-1"))
            assertEquals(
                "account-1",
                database
                    .selectedAccountDao()
                    .observeSelectedAccount()
                    .first()
                    ?.accountId,
            )

            database.selectedAccountDao().replaceSelectedAccount(account("account-2"), accountMapping("account-2"))
            assertEquals(
                "account-2",
                database
                    .selectedAccountDao()
                    .observeSelectedAccount()
                    .first()
                    ?.accountId,
            )
            assertEquals(1, countRows("selected_account"))
            assertEquals(1, countRows("account_provider_id"))
        }

    @Test
    fun deletingSelectedAccountCascadesAccountAndCollectionRows() =
        runTest {
            database.selectedAccountDao().replaceSelectedAccount(account("account-1"), accountMapping("account-1"))
            database.mediaDao().upsertMedia(listOf(media("media-1")))
            database.collectionEntryDao().upsertEntries(listOf(collectionEntry("entry-1")))
            database.providerIdDao().upsertCollectionEntryProviderIds(emptyList())

            database.selectedAccountDao().deleteAccount("account-1")

            assertEquals(0, countRows("account_provider_id"))
            assertEquals(0, countRows("collection_entry"))
        }

    @Test
    fun relatedMediaRequiresBothParentsByCommit() =
        runTest {
            database.mediaDao().upsertMedia(listOf(media("media-1")))

            assertConstraint {
                database.inTransaction {
                    database.mediaDao().upsertRelatedMedia(
                        listOf(
                            RelatedMediaEntity(
                                mediaId = "media-1",
                                relatedMediaId = "missing",
                                position = 0,
                            ),
                        ),
                    )
                }
            }
        }

    @Test
    fun relatedMediaFlowIsOrderedAndEmptyWhenNoRelationsExist() =
        runTest {
            database.mediaDao().upsertMedia(listOf(media("media-1"), media("media-2"), media("media-3")))
            database.mediaDao().upsertRelatedMedia(
                listOf(
                    RelatedMediaEntity("media-1", "media-3", 1),
                    RelatedMediaEntity("media-1", "media-2", 0),
                ),
            )

            assertEquals(
                listOf("media-2", "media-3"),
                database
                    .mediaDao()
                    .observeRelatedMedia("media-1")
                    .first()
                    .map { item -> item.media.mediaId },
            )
            assertEquals(emptyList<Any>(), database.mediaDao().observeRelatedMedia("media-2").first())
        }

    @Test
    fun collectionUniquenessIsAccountAndMediaScoped() =
        runTest {
            database.selectedAccountDao().replaceSelectedAccount(account("account-1"), accountMapping("account-1"))
            database.mediaDao().upsertMedia(listOf(media("media-1"), media("media-2")))

            assertConstraint {
                database.openHelper.writableDatabase.execSQL(
                    "INSERT INTO collection_entry " +
                        "(collection_entry_id, account_id, media_id, progress_unit, remote_status, " +
                        "remote_progress, remote_total, remote_updated_at_utc_ms, effective_status, " +
                        "effective_progress, effective_total, effective_updated_at_utc_ms) " +
                        "VALUES ('entry-1', 'account-1', 'media-1', 'episode', 'current', 1, 12, " +
                        "1767225600000, 'current', 1, 12, 1767225600000)",
                )
                database.openHelper.writableDatabase.execSQL(
                    "INSERT INTO collection_entry " +
                        "(collection_entry_id, account_id, media_id, progress_unit, remote_status, " +
                        "remote_progress, remote_total, remote_updated_at_utc_ms, effective_status, " +
                        "effective_progress, effective_total, effective_updated_at_utc_ms) " +
                        "VALUES ('entry-2', 'account-1', 'media-1', 'episode', 'current', 1, 12, " +
                        "1767225600000, 'current', 1, 12, 1767225600000)",
                )
            }
        }

    @Test
    fun remoteAndEffectiveCollectionColumnsArePhysicallyIndependent() =
        runTest {
            database.selectedAccountDao().replaceSelectedAccount(account("account-1"), accountMapping("account-1"))
            database.mediaDao().upsertMedia(listOf(media("media-1")))
            database.collectionEntryDao().upsertEntries(
                listOf(
                    collectionEntry(
                        entryId = "entry-1",
                        remoteStatus = StoredCollectionStatus.CURRENT,
                        remoteProgress = 1,
                        effectiveStatus = StoredCollectionStatus.PLANNED,
                        effectiveProgress = 2,
                    ),
                ),
            )

            val effective = database.collectionEntryDao().observeEntry("entry-1").first()
            assertNotNull(effective)
            assertEquals(StoredCollectionStatus.PLANNED, effective?.effectiveStatus)
            assertEquals(2, effective?.effectiveProgress)
            assertEquals(StoredCollectionStatus.CURRENT, effective?.remoteStatus)
            assertEquals(1, effective?.remoteProgress)
        }

    @Test
    fun repeatingStatusRoundTripsThroughRemoteAndEffectiveColumns() =
        runTest {
            database.selectedAccountDao().replaceSelectedAccount(account("account-1"), accountMapping("account-1"))
            database.mediaDao().upsertMedia(listOf(media("media-1")))
            database.collectionEntryDao().upsertEntries(
                listOf(
                    collectionEntry(
                        entryId = "entry-1",
                        remoteStatus = StoredCollectionStatus.REPEATING,
                        effectiveStatus = StoredCollectionStatus.REPEATING,
                    ),
                ),
            )

            val stored = database.collectionEntryDao().observeEntry("entry-1").first()
            assertNotNull(stored)
            assertEquals(StoredCollectionStatus.REPEATING, stored?.remoteStatus)
            assertEquals(StoredCollectionStatus.REPEATING, stored?.effectiveStatus)
        }

    @Test
    fun deletingMediaCascadesAllMediaOwnedChildrenAndCollectionEntries() =
        runTest {
            database.selectedAccountDao().replaceSelectedAccount(account("account-1"), accountMapping("account-1"))
            database.mediaDao().upsertMedia(listOf(media("media-1"), media("media-2")))
            database.mediaDao().upsertGenres(
                listOf(
                    io.morgan.mio.core.database.entity
                        .MediaGenreEntity("media-1", "Action", 0),
                ),
            )
            database.mediaDao().upsertRelatedMedia(
                listOf(RelatedMediaEntity("media-1", "media-2", 0)),
            )
            database.releaseDao().upsertReleases(
                listOf(
                    ReleaseEntity(
                        mediaId = "media-1",
                        progressUnit = StoredProgressUnit.EPISODE,
                        unitNumber = 1,
                        timeKind = StoredReleaseTimeKind.UNKNOWN,
                        releaseInstantUtcMs = null,
                    ),
                ),
            )
            database.providerIdDao().upsertMediaProviderIds(
                listOf(MediaProviderIdEntity(StoredConnectedProvider.ANILIST, "101", "media-1")),
            )
            database.collectionEntryDao().upsertEntries(listOf(collectionEntry("entry-1")))

            database.openHelper.writableDatabase.execSQL("DELETE FROM media WHERE media_id = 'media-1'")

            assertEquals(0, countRows("media_genre"))
            assertEquals(0, countRows("related_media"))
            assertEquals(0, countRows("release"))
            assertEquals(0, countRows("media_provider_id"))
            assertEquals(0, countRows("collection_entry"))
        }

    @Test
    fun providerReverseMappingIsUnique() =
        runTest {
            database.mediaDao().upsertMedia(listOf(media("media-1"), media("media-2")))
            database.providerIdDao().upsertMediaProviderIds(
                listOf(MediaProviderIdEntity(StoredConnectedProvider.ANILIST, "101", "media-1")),
            )

            assertConstraint {
                database.openHelper.writableDatabase.execSQL(
                    "INSERT INTO media_provider_id (provider, remote_media_id, media_id) " +
                        "VALUES ('anilist', '102', 'media-1')",
                )
            }
        }

    @Test
    fun persistenceEntitiesRejectInvalidValuesBeforeRoomWrite() {
        assertThrows(IllegalArgumentException::class.java) {
            account(" ")
        }
        assertThrows(IllegalArgumentException::class.java) {
            media("media-1").copy(episodeTotal = 0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            RelatedMediaEntity("media-1", "media-1", 0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            ReleaseEntity(
                mediaId = "media-1",
                progressUnit = StoredProgressUnit.EPISODE,
                unitNumber = 1,
                timeKind = StoredReleaseTimeKind.UNKNOWN,
                releaseInstantUtcMs = Instant.parse("2026-01-01T00:00:00Z"),
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            collectionEntry("entry-1").copy(effectiveProgress = -1)
        }
    }

    @Test
    fun invalidSelectedAccountMappingDoesNotDeleteTheCurrentAccount() =
        runTest {
            database.selectedAccountDao().replaceSelectedAccount(account("account-1"), accountMapping("account-1"))

            try {
                database.selectedAccountDao().replaceSelectedAccount(
                    account("account-2"),
                    accountMapping("account-1"),
                )
                fail("Expected IllegalArgumentException")
            } catch (_: IllegalArgumentException) {
                // Expected mismatched aggregate input.
            }

            assertEquals(
                "account-1",
                database
                    .selectedAccountDao()
                    .observeSelectedAccount()
                    .first()
                    ?.accountId,
            )
        }

    private fun account(accountId: String): SelectedAccountEntity =
        SelectedAccountEntity(
            selectionKey = "selected",
            accountId = accountId,
            connectedProvider = StoredConnectedProvider.ANILIST,
            displayName = "Account $accountId",
            avatarReference = null,
        )

    private fun accountMapping(accountId: String): AccountProviderIdEntity =
        AccountProviderIdEntity(
            accountId = accountId,
            provider = StoredConnectedProvider.ANILIST,
            remoteAccountId = "remote-$accountId",
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

    private fun collectionEntry(
        entryId: String,
        remoteStatus: StoredCollectionStatus = StoredCollectionStatus.CURRENT,
        remoteProgress: Int = 1,
        effectiveStatus: StoredCollectionStatus = remoteStatus,
        effectiveProgress: Int = remoteProgress,
    ): CollectionEntryEntity =
        CollectionEntryEntity(
            collectionEntryId = entryId,
            accountId = "account-1",
            mediaId = "media-1",
            progressUnit = StoredProgressUnit.EPISODE,
            remoteStatus = remoteStatus,
            remoteProgress = remoteProgress,
            remoteTotal = 12,
            remoteUpdatedAtUtcMs = Instant.parse("2026-01-01T00:00:00Z"),
            effectiveStatus = effectiveStatus,
            effectiveProgress = effectiveProgress,
            effectiveTotal = 12,
            effectiveUpdatedAtUtcMs = Instant.parse("2026-01-01T00:00:00Z"),
        )

    private fun countRows(tableName: String): Int =
        database.openHelper.readableDatabase.query("SELECT COUNT(*) FROM $tableName").use { cursor ->
            cursor.moveToFirst()
            cursor.getInt(0)
        }

    private fun pragmaForeignKeys(): Int =
        database.openHelper.readableDatabase.query("PRAGMA foreign_keys").use { cursor ->
            cursor.moveToFirst()
            cursor.getInt(0)
        }

    private suspend fun assertConstraint(block: suspend () -> Unit) {
        try {
            block()
            fail("Expected SQLiteConstraintException")
        } catch (_: SQLiteConstraintException) {
            // Expected constraint violation.
        }
    }
}
