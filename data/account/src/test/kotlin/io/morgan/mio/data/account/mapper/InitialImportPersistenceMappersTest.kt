package io.morgan.mio.data.account.mapper

import io.morgan.mio.core.database.model.StoredCollectionStatus
import io.morgan.mio.core.database.model.StoredConnectedProvider
import io.morgan.mio.core.database.model.StoredReleaseTimeKind
import io.morgan.mio.core.testing.provider.completeProviderImportFixture
import io.morgan.mio.core.testing.provider.providerCollectionEntryFixture
import io.morgan.mio.core.testing.provider.providerIdentityFixture
import io.morgan.mio.core.testing.provider.providerMediaFixture
import io.morgan.mio.data.provider.api.ProviderAccountId
import io.morgan.mio.data.provider.api.ProviderCollectionStatus
import io.morgan.mio.data.provider.api.ProviderKey
import io.morgan.mio.data.provider.api.ProviderMediaId
import io.morgan.mio.data.provider.api.ProviderRelease
import io.morgan.mio.data.provider.api.ProviderReleaseTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import kotlin.time.Instant

class InitialImportPersistenceMappersTest {
    private val identity = providerIdentityFixture()
    private val expectedAccountId =
        ImportedIdentityFactory.accountId(StoredConnectedProvider.ANILIST, identity.accountId.value)

    @Test
    fun everyEntityGroupIsProducedWithTheExpectedValues() {
        val import =
            completeProviderImportFixture(
                media = listOf(providerMediaFixture(releaseYear = 2026, scorePercent = 87)),
            )

        val snapshot = import.toInitialImportPersistence(identity)

        assertEquals(expectedAccountId, snapshot.accountId)
        assertEquals(expectedAccountId.value, snapshot.account.accountId)
        assertEquals(StoredConnectedProvider.ANILIST, snapshot.account.connectedProvider)
        assertEquals(identity.displayName, snapshot.account.displayName)
        assertEquals(identity.avatar?.value, snapshot.account.avatarReference)

        assertEquals(expectedAccountId.value, snapshot.accountProviderId.accountId)
        assertEquals(StoredConnectedProvider.ANILIST, snapshot.accountProviderId.provider)
        assertEquals(identity.accountId.value, snapshot.accountProviderId.remoteAccountId)

        val expectedMediaId = ImportedIdentityFactory.mediaId(StoredConnectedProvider.ANILIST, "media-fixture")
        assertEquals(1, snapshot.media.size)
        assertEquals(expectedMediaId.value, snapshot.media.single().mediaId)
        assertEquals(2026, snapshot.media.single().releaseYear)
        assertEquals(87, snapshot.media.single().scorePercent)

        assertEquals(1, snapshot.mediaProviderIds.size)
        assertEquals(StoredConnectedProvider.ANILIST, snapshot.mediaProviderIds.single().provider)
        assertEquals("media-fixture", snapshot.mediaProviderIds.single().remoteMediaId)
        assertEquals(expectedMediaId.value, snapshot.mediaProviderIds.single().mediaId)

        assertEquals(listOf("Adventure", "Drama"), snapshot.genres.sortedBy { it.position }.map { it.genre })
        assertEquals(listOf(0, 1), snapshot.genres.sortedBy { it.position }.map { it.position })

        assertEquals(1, snapshot.releases.size)
        assertEquals(expectedMediaId.value, snapshot.releases.single().mediaId)

        val expectedEntryId =
            ImportedIdentityFactory.collectionEntryId(
                StoredConnectedProvider.ANILIST,
                "entry-fixture",
            )
        assertEquals(1, snapshot.entries.size)
        val entry = snapshot.entries.single()
        assertEquals(expectedEntryId.value, entry.collectionEntryId)
        assertEquals(expectedAccountId.value, entry.accountId)
        assertEquals(expectedMediaId.value, entry.mediaId)

        assertEquals(1, snapshot.entryProviderIds.size)
        assertEquals(StoredConnectedProvider.ANILIST, snapshot.entryProviderIds.single().provider)
        assertEquals("entry-fixture", snapshot.entryProviderIds.single().remoteCollectionEntryId)
        assertEquals(expectedEntryId.value, snapshot.entryProviderIds.single().collectionEntryId)
    }

    @Test
    fun missingOptionalYearAndScoreRemainNull() {
        val snapshot =
            completeProviderImportFixture(
                media = listOf(providerMediaFixture(releaseYear = null, scorePercent = null)),
            ).toInitialImportPersistence(identity)

        assertNull(snapshot.media.single().releaseYear)
        assertNull(snapshot.media.single().scorePercent)
    }

    @Test
    fun remoteAndEffectiveColumnsAreIdenticalForEveryEntry() {
        val entry =
            providerCollectionEntryFixture(status = ProviderCollectionStatus.REPEATING, total = null)
        val import = completeProviderImportFixture(entries = listOf(entry))

        val snapshot = import.toInitialImportPersistence(identity)
        val entity = snapshot.entries.single()

        assertEquals(entity.remoteStatus, entity.effectiveStatus)
        assertEquals(entity.remoteProgress, entity.effectiveProgress)
        assertEquals(entity.remoteTotal, entity.effectiveTotal)
        assertEquals(entity.remoteUpdatedAtUtcMs, entity.effectiveUpdatedAtUtcMs)
        assertEquals(StoredCollectionStatus.REPEATING, entity.remoteStatus)
    }

    @Test
    fun everyCollectionStatusMapsToItsStoredEquivalentIncludingRepeating() {
        val expected =
            mapOf(
                ProviderCollectionStatus.CURRENT to StoredCollectionStatus.CURRENT,
                ProviderCollectionStatus.PLANNED to StoredCollectionStatus.PLANNED,
                ProviderCollectionStatus.PAUSED to StoredCollectionStatus.PAUSED,
                ProviderCollectionStatus.COMPLETED to StoredCollectionStatus.COMPLETED,
                ProviderCollectionStatus.DROPPED to StoredCollectionStatus.DROPPED,
                ProviderCollectionStatus.REPEATING to StoredCollectionStatus.REPEATING,
            )

        expected.forEach { (providerStatus, storedStatus) ->
            val import =
                completeProviderImportFixture(entries = listOf(providerCollectionEntryFixture(status = providerStatus)))
            val snapshot = import.toInitialImportPersistence(identity)
            assertEquals(storedStatus, snapshot.entries.single().remoteStatus)
        }
    }

    @Test
    fun everyReleaseTimeKindMapsWithCorrectInstantNullability() {
        val instant = Instant.parse("2026-01-05T00:00:00Z")
        val media =
            providerMediaFixture(
                releases =
                    listOf(
                        ProviderRelease(
                            ProviderMediaId("media-fixture"),
                            io.morgan.mio.data.provider.api.ProviderProgressUnit.EPISODE,
                            1,
                            ProviderReleaseTime.Unknown,
                        ),
                        ProviderRelease(
                            ProviderMediaId("media-fixture"),
                            io.morgan.mio.data.provider.api.ProviderProgressUnit.EPISODE,
                            2,
                            ProviderReleaseTime.Trusted(instant),
                        ),
                        ProviderRelease(
                            ProviderMediaId("media-fixture"),
                            io.morgan.mio.data.provider.api.ProviderProgressUnit.EPISODE,
                            3,
                            ProviderReleaseTime.Untrusted(instant),
                        ),
                    ),
            )
        val import = completeProviderImportFixture(media = listOf(media))

        val snapshot = import.toInitialImportPersistence(identity)
        val releasesByNumber = snapshot.releases.associateBy { it.unitNumber }

        assertEquals(StoredReleaseTimeKind.UNKNOWN, releasesByNumber.getValue(1).timeKind)
        assertNull(releasesByNumber.getValue(1).releaseInstantUtcMs)
        assertEquals(StoredReleaseTimeKind.TRUSTED, releasesByNumber.getValue(2).timeKind)
        assertEquals(instant, releasesByNumber.getValue(2).releaseInstantUtcMs)
        assertEquals(StoredReleaseTimeKind.UNTRUSTED, releasesByNumber.getValue(3).timeKind)
        assertEquals(instant, releasesByNumber.getValue(3).releaseInstantUtcMs)
    }

    @Test
    fun aRelatedIdOutsideTheImportIsDroppedAndRemainingPositionsAreReindexedFromZero() {
        val owner =
            providerMediaFixture(
                id = "owner",
                relatedMediaIds = listOf("present-1", "missing", "present-2"),
                releases = emptyList(),
            )
        val present1 = providerMediaFixture(id = "present-1", releases = emptyList())
        val present2 = providerMediaFixture(id = "present-2", releases = emptyList())
        val import =
            completeProviderImportFixture(
                media = listOf(owner, present1, present2),
                entries =
                    listOf(
                        providerCollectionEntryFixture(id = "entry-owner", mediaId = "owner"),
                        providerCollectionEntryFixture(id = "entry-present-1", mediaId = "present-1"),
                        providerCollectionEntryFixture(id = "entry-present-2", mediaId = "present-2"),
                    ),
            )

        val snapshot = import.toInitialImportPersistence(identity)
        val ownerLocalId = ImportedIdentityFactory.mediaId(StoredConnectedProvider.ANILIST, "owner")
        val related = snapshot.relatedMedia.filter { it.mediaId == ownerLocalId.value }.sortedBy { it.position }

        assertEquals(2, related.size)
        assertEquals(listOf(0, 1), related.map { it.position })
        assertEquals(
            listOf(
                ImportedIdentityFactory.mediaId(StoredConnectedProvider.ANILIST, "present-1").value,
                ImportedIdentityFactory.mediaId(StoredConnectedProvider.ANILIST, "present-2").value,
            ),
            related.map { it.relatedMediaId },
        )
    }

    @Test
    fun returnedListsAreSnapshotsThatDoNotAliasTheInput() {
        val import = completeProviderImportFixture()

        val snapshot = import.toInitialImportPersistence(identity)
        val mutatedCopy = snapshot.media.toMutableList()
        mutatedCopy.clear()

        assertNotSame(import.media, snapshot.media)
        assertEquals(1, snapshot.media.size)
    }

    @Test
    fun myAnimeListImportPersistenceMapping() {
        val identity = providerIdentityFixture(provider = ProviderKey.MY_ANIME_LIST)
        val media =
            providerMediaFixture(
                id = "anime/1",
                releases = emptyList(),
                relatedMediaIds = emptyList(),
            )
        val entry =
            providerCollectionEntryFixture(
                id = "anime/1",
                accountId = identity.accountId,
                mediaId = "anime/1",
            )
        val import =
            completeProviderImportFixture(
                provider = ProviderKey.MY_ANIME_LIST,
                accountId = identity.accountId,
                media = listOf(media),
                entries = listOf(entry),
            )

        val snapshot = import.toInitialImportPersistence(identity)

        val expectedAccountId =
            ImportedIdentityFactory.accountId(StoredConnectedProvider.MY_ANIME_LIST, identity.accountId.value)
        assertEquals(expectedAccountId, snapshot.accountId)
        assertEquals(StoredConnectedProvider.MY_ANIME_LIST, snapshot.account.connectedProvider)
        assertEquals(StoredConnectedProvider.MY_ANIME_LIST, snapshot.accountProviderId.provider)

        val expectedMediaId = ImportedIdentityFactory.mediaId(StoredConnectedProvider.MY_ANIME_LIST, "anime/1")
        assertEquals(1, snapshot.media.size)
        assertEquals(expectedMediaId.value, snapshot.media.single().mediaId)

        // The same remote values under AniList must allocate different local IDs.
        assertNotEquals(
            ImportedIdentityFactory.accountId(StoredConnectedProvider.ANILIST, identity.accountId.value),
            expectedAccountId,
        )
        assertNotEquals(
            ImportedIdentityFactory.mediaId(StoredConnectedProvider.ANILIST, "anime/1"),
            expectedMediaId,
        )

        assertEquals(0, snapshot.releases.size)
        assertEquals(0, snapshot.relatedMedia.size)

        val expectedEntryId =
            ImportedIdentityFactory.collectionEntryId(
                StoredConnectedProvider.MY_ANIME_LIST,
                "anime/1",
            )
        assertEquals(1, snapshot.entries.size)
        val persistedEntry = snapshot.entries.single()
        assertEquals(expectedEntryId.value, persistedEntry.collectionEntryId)
        assertEquals(expectedMediaId.value, persistedEntry.mediaId)
        assertEquals(persistedEntry.remoteStatus, persistedEntry.effectiveStatus)
    }

    @Test
    fun mismatchedIdentityProviderThrows() {
        val import = completeProviderImportFixture(provider = ProviderKey.ANILIST)
        val mismatchedIdentity = providerIdentityFixture(provider = ProviderKey.MY_ANIME_LIST)

        assertThrows(IllegalArgumentException::class.java) {
            import.toInitialImportPersistence(mismatchedIdentity)
        }
    }

    @Test
    fun mismatchedIdentityAccountIdThrows() {
        val import = completeProviderImportFixture(accountId = ProviderAccountId("account-fixture"))
        val mismatchedIdentity = providerIdentityFixture(accountId = ProviderAccountId("different-account"))

        assertThrows(IllegalArgumentException::class.java) {
            import.toInitialImportPersistence(mismatchedIdentity)
        }
    }
}
