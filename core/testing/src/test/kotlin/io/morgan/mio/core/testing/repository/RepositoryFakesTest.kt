package io.morgan.mio.core.testing.repository

import io.morgan.mio.domain.account.Account
import io.morgan.mio.domain.account.ConnectedProvider
import io.morgan.mio.domain.collection.CollectionEntry
import io.morgan.mio.domain.collection.CollectionStatus
import io.morgan.mio.domain.media.Artwork
import io.morgan.mio.domain.media.Media
import io.morgan.mio.domain.media.MediaFormat
import io.morgan.mio.domain.media.MediaPublicationStatus
import io.morgan.mio.domain.media.MediaTotals
import io.morgan.mio.domain.media.Release
import io.morgan.mio.domain.media.ReleaseTime
import io.morgan.mio.domain.model.AccountId
import io.morgan.mio.domain.model.CollectionEntryId
import io.morgan.mio.domain.model.MediaId
import io.morgan.mio.domain.model.MediaTitle
import io.morgan.mio.domain.model.MediaType
import io.morgan.mio.domain.model.ProgressUnit
import io.morgan.mio.domain.model.ProgressValue
import io.morgan.mio.domain.model.UnitNumber
import io.morgan.mio.domain.model.UnitTotal
import io.morgan.mio.domain.preferences.AccountPreferences
import io.morgan.mio.domain.preferences.AppearancePreference
import io.morgan.mio.domain.preferences.DevicePreferences
import io.morgan.mio.domain.preferences.LibraryMediaFilter
import io.morgan.mio.domain.preferences.MotionPreference
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import kotlin.time.Instant

class RepositoryFakesTest {
    private val accountId = AccountId("account-1")
    private val secondAccountId = AccountId("account-2")

    @Test
    fun accountFakeEmitsStateAndRecordsRefreshes() =
        runTest {
            val fake = FakeAccountRepository()
            val account = Account(accountId, ConnectedProvider.ANILIST, "Morgan", null)

            assertNull(fake.observeSelectedAccount().first())
            fake.setSelectedAccount(account)
            assertEquals(account, fake.observeSelectedAccount().first())

            fake.refreshSelectedAccount()
            fake.refreshSelectedAccount()
            assertEquals(2, fake.refreshCount)
        }

    @Test
    fun mediaFakeSnapshotsListsFiltersTypedIdsAndRecordsRefreshes() =
        runTest {
            val fake = FakeMediaRepository()
            val mediaA = media("media-a")
            val mediaB = media("media-b")
            val mutableMedia = mutableListOf(mediaB, mediaA)
            val mutableReleases =
                mutableListOf(
                    release(mediaA.id, 2),
                    release(mediaB.id, 1),
                    release(mediaA.id, 1),
                )

            assertNull(fake.observeMedia(mediaA.id).first())
            assertEquals(emptyList<Release>(), fake.observeReleases(mediaA.id).first())

            fake.setMedia(mutableMedia)
            fake.setReleases(mutableReleases)
            mutableMedia.clear()
            mutableReleases.clear()

            assertEquals(mediaA, fake.observeMedia(mediaA.id).first())
            assertEquals(
                listOf(release(mediaA.id, 2), release(mediaA.id, 1)),
                fake.observeReleases(mediaA.id).first(),
            )

            fake.setMedia(listOf(mediaB))
            assertNull(fake.observeMedia(mediaA.id).first())
            fake.refreshMedia(mediaB.id)
            fake.refreshMedia(mediaA.id)
            assertEquals(listOf(mediaB.id, mediaA.id), fake.refreshedMediaIds)

            (fake.refreshedMediaIds as MutableList).clear()
            assertEquals(listOf(mediaB.id, mediaA.id), fake.refreshedMediaIds)
        }

    @Test
    fun collectionFakeSortsAndFiltersOneStateFlow() =
        runTest {
            val fake = FakeCollectionRepository()
            val entryB = entry("entry-b", accountId, MediaId("media-b"))
            val entryA = entry("entry-a", accountId, MediaId("media-a"))
            val otherAccountEntry = entry("entry-c", secondAccountId, MediaId("media-c"))
            val mutableEntries = mutableListOf(entryB, otherAccountEntry, entryA)

            assertEquals(emptyList<CollectionEntry>(), fake.observeCollection(accountId).first())
            fake.setEntries(mutableEntries)
            mutableEntries.clear()

            assertEquals(
                listOf(entryA, entryB),
                fake.observeCollection(accountId).first(),
            )
            assertEquals(entryA, fake.observeEntry(entryA.id).first())
            assertEquals(entryB, fake.observeEntryForMedia(accountId, MediaId("media-b")).first())

            fake.setEntries(listOf(otherAccountEntry))
            assertEquals(emptyList<CollectionEntry>(), fake.observeCollection(accountId).first())
            fake.refreshCollection(accountId)
            fake.refreshCollection(secondAccountId)
            assertEquals(listOf(accountId, secondAccountId), fake.refreshedAccountIds)

            (fake.refreshedAccountIds as MutableList).clear()
            assertEquals(listOf(accountId, secondAccountId), fake.refreshedAccountIds)
        }

    @Test
    fun preferencesFakeKeepsDeviceAndAccountStateSeparate() =
        runTest {
            val fake = FakePreferencesRepository()

            assertEquals(DevicePreferences.Default, fake.observeDevicePreferences().first())
            assertEquals(AccountPreferences.default(accountId), fake.observeAccountPreferences(accountId).first())

            fake.setAppearance(AppearancePreference.DARK)
            fake.setMotion(MotionPreference.REDUCED)
            fake.setHapticsEnabled(false)
            fake.setLibraryMediaFilter(accountId, LibraryMediaFilter.MANGA)

            assertEquals(
                DevicePreferences(AppearancePreference.DARK, MotionPreference.REDUCED, false),
                fake.observeDevicePreferences().first(),
            )
            assertEquals(
                AccountPreferences(accountId, LibraryMediaFilter.MANGA),
                fake.observeAccountPreferences(accountId).first(),
            )
            assertEquals(
                AccountPreferences.default(secondAccountId),
                fake.observeAccountPreferences(secondAccountId).first(),
            )
        }

    private fun media(id: String): Media =
        Media(
            id = MediaId(id),
            type = MediaType.ANIME,
            title = MediaTitle("Title $id"),
            format = MediaFormat.TV,
            publicationStatus = MediaPublicationStatus.RELEASING,
            synopsis = null,
            artwork = Artwork(null, null),
            totals = MediaTotals(UnitTotal(12), null, null),
            genres = emptyList(),
            relatedMediaIds = emptyList(),
        )

    private fun release(
        mediaId: MediaId,
        number: Int,
    ): Release =
        Release(
            mediaId = mediaId,
            unit = ProgressUnit.EPISODE,
            number = UnitNumber(number),
            time = ReleaseTime.Trusted(Instant.parse("2026-01-01T00:00:00Z")),
        )

    private fun entry(
        id: String,
        accountId: AccountId,
        mediaId: MediaId,
    ): CollectionEntry =
        CollectionEntry(
            id = CollectionEntryId(id),
            accountId = accountId,
            mediaId = mediaId,
            status = CollectionStatus.CURRENT,
            progress = ProgressValue(0),
            progressUnit = ProgressUnit.EPISODE,
            total = null,
            updatedAt = Instant.parse("2026-01-01T00:00:00Z"),
        )
}
