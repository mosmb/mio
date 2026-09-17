package io.morgan.mio.data.sync

import androidx.test.core.app.ApplicationProvider
import io.morgan.mio.core.database.MioDatabase
import io.morgan.mio.core.database.MioDatabaseFactory
import io.morgan.mio.core.database.entity.AccountProviderIdEntity
import io.morgan.mio.core.database.entity.CollectionEntryEntity
import io.morgan.mio.core.database.entity.CollectionEntryProviderIdEntity
import io.morgan.mio.core.database.entity.MediaEntity
import io.morgan.mio.core.database.entity.MediaProviderIdEntity
import io.morgan.mio.core.database.entity.SelectedAccountEntity
import io.morgan.mio.core.database.model.StoredCollectionStatus
import io.morgan.mio.core.database.model.StoredConnectedProvider
import io.morgan.mio.core.database.model.StoredMediaPublicationStatus
import io.morgan.mio.core.database.model.StoredMediaType
import io.morgan.mio.core.database.model.StoredProgressUnit
import io.morgan.mio.core.testing.provider.FakeProviderAuthenticator
import io.morgan.mio.core.testing.provider.FakeProviderCredentialStore
import io.morgan.mio.core.testing.provider.FakeProviderIdentityDataSource
import io.morgan.mio.core.testing.provider.FakeProviderInitialImportDataSource
import io.morgan.mio.core.testing.provider.FakeProviderSyncDataSource
import io.morgan.mio.core.testing.provider.completeProviderImportFixture
import io.morgan.mio.core.testing.provider.providerBindingFixture
import io.morgan.mio.core.testing.provider.providerMediaFixture
import io.morgan.mio.data.provider.api.CompleteProviderImport
import io.morgan.mio.data.provider.api.ProviderAccountId
import io.morgan.mio.data.provider.api.ProviderCollectionEntry
import io.morgan.mio.data.provider.api.ProviderCollectionEntryId
import io.morgan.mio.data.provider.api.ProviderCollectionStatus
import io.morgan.mio.data.provider.api.ProviderCredentials
import io.morgan.mio.data.provider.api.ProviderFailure
import io.morgan.mio.data.provider.api.ProviderIdentity
import io.morgan.mio.data.provider.api.ProviderKey
import io.morgan.mio.data.provider.api.ProviderMediaId
import io.morgan.mio.data.provider.api.ProviderProgressUnit
import io.morgan.mio.data.provider.api.ProviderResult
import io.morgan.mio.domain.model.AccountId
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [35])
class ProviderRefreshRemoteDataSourceTest {
    private lateinit var database: MioDatabase

    @Before
    fun setUp() {
        database = MioDatabaseFactory.createInMemory(ApplicationProvider.getApplicationContext())
        runTest {
            database.selectedAccountDao().replaceSelectedAccount(
                SelectedAccountEntity("selected", ACCOUNT_ID.value, StoredConnectedProvider.ANILIST, "Fixture", null),
                AccountProviderIdEntity(ACCOUNT_ID.value, StoredConnectedProvider.ANILIST, REMOTE_ACCOUNT_ID),
            )
        }
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun completeImportUsesVerifiedSelectedRemoteAccount() =
        runTest {
            val fixture = fixture()
            val snapshot =
                completeProviderImportFixture(
                    provider = ProviderKey.ANILIST,
                    accountId = ProviderAccountId(REMOTE_ACCOUNT_ID),
                    media = listOf(providerMediaFixture(id = "101")),
                    entries = listOf(remoteEntry(progress = 1, unit = ProviderProgressUnit.EPISODE)),
                )
            fixture.initialImport.enqueue { _, requestedAccount ->
                assertEquals(REMOTE_ACCOUNT_ID, requestedAccount.value)
                ProviderResult.Success(snapshot)
            }

            val result = fixture.remote.read(ACCOUNT_ID)

            assertTrue(result is ProviderResult.Success)
            assertEquals(1, fixture.initialImport.calls.size)
            assertEquals(
                REMOTE_ACCOUNT_ID,
                fixture.initialImport.calls
                    .single()
                    .accountId.value,
            )
            assertEquals(1, fixture.identity.credentials.size)
            assertTrue(fixture.sync.calls.isEmpty())
        }

    @Test
    fun existingVolumeAtZeroIsRereadUsingThePersistedFixedUnit() =
        runTest {
            database.mediaDao().upsertMedia(listOf(localMedia("local-media")))
            database.collectionEntryDao().upsertEntries(listOf(localEntry("local-entry", "local-media")))
            database.providerIdDao().upsertMediaProviderIds(
                listOf(MediaProviderIdEntity(StoredConnectedProvider.ANILIST, "101", "local-media")),
            )
            database.providerIdDao().upsertCollectionEntryProviderIds(
                listOf(CollectionEntryProviderIdEntity(StoredConnectedProvider.ANILIST, "201", "local-entry")),
            )
            val fixture = fixture()
            fixture.initialImport.enqueue { _, _ ->
                ProviderResult.Success(
                    CompleteProviderImport(
                        provider = ProviderKey.ANILIST,
                        accountId = ProviderAccountId(REMOTE_ACCOUNT_ID),
                        media = listOf(providerMediaFixture(id = "101")),
                        entries = listOf(remoteEntry(progress = 4, unit = ProviderProgressUnit.VOLUME)),
                    ),
                )
            }
            fixture.sync.enqueueRead { _, account, entry, media, unit ->
                assertEquals(REMOTE_ACCOUNT_ID, account.value)
                assertEquals("201", entry.value)
                assertEquals("101", media.value)
                assertEquals(ProviderProgressUnit.VOLUME, unit)
                ProviderResult.Success(remoteEntry(progress = 0, unit = unit))
            }

            val result = fixture.remote.read(ACCOUNT_ID) as ProviderResult.Success

            assertEquals(
                0,
                result.value.entries
                    .single()
                    .progress,
            )
            assertEquals(
                ProviderProgressUnit.VOLUME,
                result.value.entries
                    .single()
                    .progressUnit,
            )
            assertEquals(
                ProviderProgressUnit.VOLUME,
                fixture.sync.calls
                    .single()
                    .unit,
            )
        }

    @Test
    fun malformedAccountIdentityAndEmptyCollectionAreHandledWithoutMutation() =
        runTest {
            val mismatch = fixture()
            mismatch.initialImport.enqueue { _, _ ->
                ProviderResult.Success(
                    CompleteProviderImport(
                        provider = ProviderKey.ANILIST,
                        accountId = ProviderAccountId("other-account"),
                        media = emptyList(),
                        entries = emptyList(),
                    ),
                )
            }
            assertEquals(
                ProviderResult.Failure(ProviderFailure.MalformedResponse),
                mismatch.remote.read(ACCOUNT_ID),
            )

            val empty = fixture()
            empty.initialImport.enqueue { _, _ ->
                ProviderResult.Success(
                    CompleteProviderImport(
                        provider = ProviderKey.ANILIST,
                        accountId = ProviderAccountId(REMOTE_ACCOUNT_ID),
                        media = emptyList(),
                        entries = emptyList(),
                    ),
                )
            }
            val emptyResult = empty.remote.read(ACCOUNT_ID) as ProviderResult.Success
            assertTrue(emptyResult.value.entries.isEmpty())
            assertTrue(emptyResult.value.media.isEmpty())
            assertTrue(database.syncDao().getEntries(ACCOUNT_ID.value).isEmpty())
        }

    @Test
    fun myAnimeListAuthenticationRefreshesCredentialsOnceAndReplaysTheRead() =
        runTest {
            database.selectedAccountDao().replaceSelectedAccount(
                SelectedAccountEntity(
                    "selected",
                    ACCOUNT_ID.value,
                    StoredConnectedProvider.MY_ANIME_LIST,
                    "Fixture",
                    null,
                ),
                AccountProviderIdEntity(ACCOUNT_ID.value, StoredConnectedProvider.MY_ANIME_LIST, REMOTE_ACCOUNT_ID),
            )
            val fixture = fixture(ProviderKey.MY_ANIME_LIST)
            val oldCredentials = ProviderCredentials(ProviderKey.MY_ANIME_LIST, "old-access", "refresh", null, "Bearer")
            val newCredentials = ProviderCredentials(ProviderKey.MY_ANIME_LIST, "new-access", "refresh", null, "Bearer")
            fixture.credentials.write(oldCredentials)
            fixture.identity.enqueue {
                ProviderResult.Success(
                    ProviderIdentity(ProviderKey.MY_ANIME_LIST, ProviderAccountId(REMOTE_ACCOUNT_ID), "Fixture", null),
                )
            }
            fixture.initialImport.enqueue { _, _ -> ProviderResult.Failure(ProviderFailure.AuthenticationRequired) }
            fixture.authenticator.enqueueRefresh { ProviderResult.Success(newCredentials) }
            fixture.identity.enqueue {
                ProviderResult.Success(
                    ProviderIdentity(ProviderKey.MY_ANIME_LIST, ProviderAccountId(REMOTE_ACCOUNT_ID), "Fixture", null),
                )
            }
            val snapshot =
                completeProviderImportFixture(
                    provider = ProviderKey.MY_ANIME_LIST,
                    accountId = ProviderAccountId(REMOTE_ACCOUNT_ID),
                    media = emptyList(),
                    entries = emptyList(),
                )
            fixture.initialImport.enqueue { _, _ -> ProviderResult.Success(snapshot) }

            val result = fixture.remote.read(ACCOUNT_ID)

            assertTrue(result is ProviderResult.Success)
            assertEquals(1, fixture.authenticator.refreshCredentials.size)
            assertEquals(2, fixture.initialImport.calls.size)
            assertEquals(
                "new-access",
                fixture.initialImport.calls
                    .last()
                    .credentials.accessToken,
            )
            assertEquals("new-access", fixture.credentials.read(ProviderKey.MY_ANIME_LIST)?.accessToken)
        }

    private suspend fun fixture(provider: ProviderKey = ProviderKey.ANILIST): Fixture {
        val binding = providerBindingFixture(provider)
        val credentials = FakeProviderCredentialStore()
        credentials.write(ProviderCredentials(provider, "fixture-access", null, null, "Bearer"))
        val identity = binding.identityDataSource as FakeProviderIdentityDataSource
        identity.enqueue {
            ProviderResult.Success(ProviderIdentity(provider, ProviderAccountId(REMOTE_ACCOUNT_ID), "Fixture", null))
        }
        val reader =
            ProviderSyncCredentialReader(
                database = database,
                accountLocalDataSource =
                    io.morgan.mio.data.sync.local
                        .RoomSyncAccountLocalDataSource(database),
                providerBinding = binding,
                credentialStore = credentials,
            )
        return Fixture(
            remote = ProviderRefreshRemoteDataSource(database, reader, binding),
            credentials = credentials,
            initialImport = binding.initialImportDataSource as FakeProviderInitialImportDataSource,
            identity = identity,
            sync = binding.syncDataSource as FakeProviderSyncDataSource,
            authenticator = binding.authenticator as FakeProviderAuthenticator,
        )
    }

    private fun remoteEntry(
        progress: Int,
        unit: ProviderProgressUnit,
    ): ProviderCollectionEntry =
        ProviderCollectionEntry(
            id = ProviderCollectionEntryId("201"),
            accountId = ProviderAccountId(REMOTE_ACCOUNT_ID),
            mediaId = ProviderMediaId("101"),
            status = ProviderCollectionStatus.CURRENT,
            progress = progress,
            progressUnit = unit,
            total = 8,
            updatedAt = NOW,
        )

    private fun localMedia(id: String): MediaEntity =
        MediaEntity(
            mediaId = id,
            mediaType = StoredMediaType.MANGA,
            title = "Cached volume media",
            format = null,
            publicationStatus = StoredMediaPublicationStatus.UNKNOWN,
            synopsis = null,
            posterReference = null,
            bannerReference = null,
            episodeTotal = null,
            chapterTotal = null,
            volumeTotal = 8,
        )

    private fun localEntry(
        entryId: String,
        mediaId: String,
    ): CollectionEntryEntity =
        CollectionEntryEntity(
            collectionEntryId = entryId,
            accountId = ACCOUNT_ID.value,
            mediaId = mediaId,
            progressUnit = StoredProgressUnit.VOLUME,
            remoteStatus = StoredCollectionStatus.CURRENT,
            remoteProgress = 0,
            remoteTotal = 8,
            remoteUpdatedAtUtcMs = NOW,
            effectiveStatus = StoredCollectionStatus.CURRENT,
            effectiveProgress = 0,
            effectiveTotal = 8,
            effectiveUpdatedAtUtcMs = NOW,
        )

    private data class Fixture(
        val remote: ProviderRefreshRemoteDataSource,
        val credentials: FakeProviderCredentialStore,
        val initialImport: FakeProviderInitialImportDataSource,
        val identity: FakeProviderIdentityDataSource,
        val sync: FakeProviderSyncDataSource,
        val authenticator: FakeProviderAuthenticator,
    )

    private companion object {
        val ACCOUNT_ID = AccountId("account-sync")
        const val REMOTE_ACCOUNT_ID = "42"
        val NOW = Instant.parse("2026-09-11T10:00:00Z")
    }
}
