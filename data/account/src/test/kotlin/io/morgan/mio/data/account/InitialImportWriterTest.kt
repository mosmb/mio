package io.morgan.mio.data.account

import android.database.sqlite.SQLiteConstraintException
import androidx.test.core.app.ApplicationProvider
import io.morgan.mio.core.database.MioDatabase
import io.morgan.mio.core.database.MioDatabaseFactory
import io.morgan.mio.core.database.entity.AccountProviderIdEntity
import io.morgan.mio.core.database.entity.CollectionEntryEntity
import io.morgan.mio.core.database.entity.CollectionEntryProviderIdEntity
import io.morgan.mio.core.database.entity.MediaEntity
import io.morgan.mio.core.database.entity.SELECTED_ACCOUNT_KEY
import io.morgan.mio.core.database.entity.SelectedAccountEntity
import io.morgan.mio.core.database.model.StoredCollectionStatus
import io.morgan.mio.core.database.model.StoredConnectedProvider
import io.morgan.mio.core.database.model.StoredMediaPublicationStatus
import io.morgan.mio.core.database.model.StoredMediaType
import io.morgan.mio.core.database.model.StoredProgressUnit
import io.morgan.mio.core.testing.provider.completeProviderImportFixture
import io.morgan.mio.core.testing.provider.providerCollectionEntryFixture
import io.morgan.mio.core.testing.provider.providerIdentityFixture
import io.morgan.mio.core.testing.provider.providerMediaFixture
import io.morgan.mio.data.account.local.RoomInitialImportLocalDataSource
import io.morgan.mio.data.account.mapper.ImportedIdentityFactory
import io.morgan.mio.data.account.mapper.InitialImportPersistenceSnapshot
import io.morgan.mio.data.provider.api.ProviderKey
import io.morgan.mio.domain.model.AccountId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [35])
class InitialImportWriterTest {
    private lateinit var database: MioDatabase
    private lateinit var writer: InitialImportWriter
    private val identity = providerIdentityFixture()

    @Before
    fun setUp() {
        database = MioDatabaseFactory.createInMemory(ApplicationProvider.getApplicationContext())
        writer = InitialImportWriter(RoomInitialImportLocalDataSource(database))
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun representativeImportCommitsEveryTableAndReturnsTheMatchingAccountId() =
        runTest {
            val import = completeProviderImportFixture()

            val accountId = writer.commit(identity, import)

            val expectedAccountId =
                ImportedIdentityFactory.accountId(StoredConnectedProvider.ANILIST, identity.accountId.value)
            assertEquals(expectedAccountId, accountId)
            assertEquals(1, countRows("selected_account"))
            assertEquals(1, countRows("account_provider_id"))
            assertEquals(1, countRows("media"))
            assertEquals(1, countRows("media_provider_id"))
            assertEquals(1, countRows("collection_entry"))
            assertEquals(1, countRows("collection_entry_provider_id"))
        }

    @Test
    fun observingCollectionAfterwardYieldsEffectiveValuesAndRemoteColumnsHoldTheSameValues() =
        runTest {
            val import = completeProviderImportFixture()

            val accountId = writer.commit(identity, import)

            val entry =
                database
                    .collectionEntryDao()
                    .observeCollection(accountId.value)
                    .first()
                    .single()
            assertEquals(entry.effectiveStatus, entry.remoteStatus)
            assertEquals(entry.effectiveProgress, entry.remoteProgress)
            assertEquals(entry.effectiveTotal, entry.remoteTotal)
        }

    @Test
    fun committingTheSameImportTwiceIsIdempotent() =
        runTest {
            val import = completeProviderImportFixture()

            writer.commit(identity, import)
            writer.commit(identity, import)

            assertEquals(1, countRows("selected_account"))
            assertEquals(1, countRows("media"))
            assertEquals(1, countRows("collection_entry"))
        }

    @Test
    fun anEmptyCompleteProviderImportCommitsAnAccountWithZeroEntries() =
        runTest {
            val import = completeProviderImportFixture(media = emptyList(), entries = emptyList())

            writer.commit(identity, import)

            assertEquals(1, countRows("selected_account"))
            assertEquals(0, countRows("collection_entry"))
        }

    @Test
    fun atomicityAFailingCommitRollsBackCompletelyAndDeletesNothingThatExistedBefore() =
        runTest {
            val accountA = seedAccountWithEntries("account-a")
            val localDataSource = RoomInitialImportLocalDataSource(database)
            val badSnapshot = brokenSnapshotReferencingMissingMedia("account-b")

            var thrown: Throwable? = null
            try {
                localDataSource.commit(badSnapshot)
            } catch (failure: SQLiteConstraintException) {
                thrown = failure
            }

            assertNotNull(thrown)
            assertEquals(1, countRows("selected_account"))
            val remainingAccount = database.selectedAccountDao().observeSelectedAccount().first()
            assertEquals(accountA.value, remainingAccount?.accountId)
            assertEquals(1, countRows("collection_entry"))
            assertEquals(0, countAccountRows("account-b"))
        }

    @Test
    fun myAnimeListImportCommitsEveryTableWithZeroReleases() =
        runTest {
            val myAnimeListIdentity = providerIdentityFixture(provider = ProviderKey.MY_ANIME_LIST)
            val import =
                completeProviderImportFixture(
                    provider = ProviderKey.MY_ANIME_LIST,
                    accountId = myAnimeListIdentity.accountId,
                    media = listOf(providerMediaFixture(releases = emptyList())),
                    entries = listOf(providerCollectionEntryFixture(accountId = myAnimeListIdentity.accountId)),
                )

            val accountId = writer.commit(myAnimeListIdentity, import)

            val expectedAccountId =
                ImportedIdentityFactory.accountId(
                    StoredConnectedProvider.MY_ANIME_LIST,
                    myAnimeListIdentity.accountId.value,
                )
            assertEquals(expectedAccountId, accountId)
            assertEquals(1, countRows("selected_account"))
            assertEquals(1, countRows("account_provider_id"))
            assertEquals(1, countRows("media"))
            assertEquals(1, countRows("media_provider_id"))
            assertEquals(0, countRows("release"))
            assertEquals(0, countRows("related_media"))
            assertEquals(1, countRows("collection_entry"))
            assertEquals(1, countRows("collection_entry_provider_id"))
        }

    @Test
    fun myAnimeListCommitWithMissingMediaThrowsAndRollsBack() =
        runTest {
            val accountA = seedAccountWithEntries("account-a")

            val badSnapshot =
                brokenSnapshotReferencingMissingMedia(
                    remoteAccountId = "account-b",
                    provider = StoredConnectedProvider.MY_ANIME_LIST,
                )

            val localDataSource = RoomInitialImportLocalDataSource(database)
            var thrown: Throwable? = null
            try {
                localDataSource.commit(badSnapshot)
            } catch (failure: SQLiteConstraintException) {
                thrown = failure
            }

            assertNotNull(thrown)
            assertEquals(1, countRows("selected_account"))
            val remainingAccount = database.selectedAccountDao().observeSelectedAccount().first()
            assertEquals(accountA.value, remainingAccount?.accountId)
            assertEquals(1, countRows("collection_entry"))
            assertEquals(0, countAccountRows("account-b"))
        }

    private fun countAccountRows(remoteAccountId: String): Int =
        database.openHelper.readableDatabase
            .query("SELECT COUNT(*) FROM account_provider_id WHERE remote_account_id = ?", arrayOf(remoteAccountId))
            .use { cursor ->
                cursor.moveToFirst()
                cursor.getInt(0)
            }

    private suspend fun seedAccountWithEntries(remoteAccountId: String): AccountId {
        val localAccountId = ImportedIdentityFactory.accountId(StoredConnectedProvider.ANILIST, remoteAccountId)
        val localMediaId = ImportedIdentityFactory.mediaId(StoredConnectedProvider.ANILIST, "seed-media")
        val localEntryId = ImportedIdentityFactory.collectionEntryId(StoredConnectedProvider.ANILIST, "seed-entry")

        database.selectedAccountDao().replaceSelectedAccount(
            SelectedAccountEntity(
                selectionKey = SELECTED_ACCOUNT_KEY,
                accountId = localAccountId.value,
                connectedProvider = StoredConnectedProvider.ANILIST,
                displayName = "Seed Account",
                avatarReference = null,
            ),
            AccountProviderIdEntity(
                accountId = localAccountId.value,
                provider = StoredConnectedProvider.ANILIST,
                remoteAccountId = remoteAccountId,
            ),
        )
        database.mediaDao().upsertMedia(
            listOf(
                MediaEntity(
                    mediaId = localMediaId.value,
                    mediaType = StoredMediaType.ANIME,
                    title = "Seed Media",
                    format = null,
                    publicationStatus = StoredMediaPublicationStatus.UNKNOWN,
                    synopsis = null,
                    posterReference = null,
                    bannerReference = null,
                    episodeTotal = null,
                    chapterTotal = null,
                    volumeTotal = null,
                ),
            ),
        )
        database.collectionEntryDao().upsertEntries(
            listOf(
                CollectionEntryEntity(
                    collectionEntryId = localEntryId.value,
                    accountId = localAccountId.value,
                    mediaId = localMediaId.value,
                    progressUnit = StoredProgressUnit.EPISODE,
                    remoteStatus = StoredCollectionStatus.CURRENT,
                    remoteProgress = 1,
                    remoteTotal = 12,
                    remoteUpdatedAtUtcMs = Instant.parse("2026-01-01T00:00:00Z"),
                    effectiveStatus = StoredCollectionStatus.CURRENT,
                    effectiveProgress = 1,
                    effectiveTotal = 12,
                    effectiveUpdatedAtUtcMs = Instant.parse("2026-01-01T00:00:00Z"),
                ),
            ),
        )
        return localAccountId
    }

    private fun brokenSnapshotReferencingMissingMedia(
        remoteAccountId: String,
        provider: StoredConnectedProvider = StoredConnectedProvider.ANILIST,
    ): InitialImportPersistenceSnapshot {
        val localAccountId = ImportedIdentityFactory.accountId(provider, remoteAccountId)
        val danglingMediaId = ImportedIdentityFactory.mediaId(provider, "missing-media")
        val localEntryId = ImportedIdentityFactory.collectionEntryId(provider, "orphan-entry")

        return InitialImportPersistenceSnapshot(
            accountId = localAccountId,
            account =
                SelectedAccountEntity(
                    selectionKey = SELECTED_ACCOUNT_KEY,
                    accountId = localAccountId.value,
                    connectedProvider = provider,
                    displayName = "Account B",
                    avatarReference = null,
                ),
            accountProviderId =
                AccountProviderIdEntity(
                    accountId = localAccountId.value,
                    provider = provider,
                    remoteAccountId = remoteAccountId,
                ),
            media = emptyList(),
            genres = emptyList(),
            relatedMedia = emptyList(),
            releases = emptyList(),
            mediaProviderIds = emptyList(),
            entries =
                listOf(
                    CollectionEntryEntity(
                        collectionEntryId = localEntryId.value,
                        accountId = localAccountId.value,
                        mediaId = danglingMediaId.value,
                        progressUnit = StoredProgressUnit.EPISODE,
                        remoteStatus = StoredCollectionStatus.CURRENT,
                        remoteProgress = 1,
                        remoteTotal = 12,
                        remoteUpdatedAtUtcMs = Instant.parse("2026-01-01T00:00:00Z"),
                        effectiveStatus = StoredCollectionStatus.CURRENT,
                        effectiveProgress = 1,
                        effectiveTotal = 12,
                        effectiveUpdatedAtUtcMs = Instant.parse("2026-01-01T00:00:00Z"),
                    ),
                ),
            entryProviderIds =
                listOf(
                    CollectionEntryProviderIdEntity(
                        provider = provider,
                        remoteCollectionEntryId = "orphan-entry",
                        collectionEntryId = localEntryId.value,
                    ),
                ),
        )
    }

    private fun countRows(tableName: String): Int =
        database.openHelper.readableDatabase.query("SELECT COUNT(*) FROM $tableName").use { cursor ->
            cursor.moveToFirst()
            cursor.getInt(0)
        }
}
