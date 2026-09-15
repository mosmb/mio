package io.morgan.mio.data.provider.anilist

import io.morgan.mio.data.provider.api.ProviderAccountId
import io.morgan.mio.data.provider.api.ProviderCollectionEntryId
import io.morgan.mio.data.provider.api.ProviderFailure
import io.morgan.mio.data.provider.api.ProviderMediaId
import io.morgan.mio.data.provider.api.ProviderProgressUnit
import io.morgan.mio.data.provider.api.ProviderResult
import org.junit.Assert.assertEquals
import org.junit.Test

class AniListSyncEntryMapperTest {
    @Test
    fun zeroTotalMeansUnknownForAnOngoingTitle() {
        val result =
            AniListSyncEntryMapper.map(
                ProviderAccountId("42"),
                ProviderCollectionEntryId("7"),
                ProviderMediaId("101"),
                ProviderProgressUnit.EPISODE,
                7,
                42,
                101,
                "CURRENT",
                13,
                0,
                1_720_000_000,
                0,
                null,
                null,
            ) as ProviderResult.Success
        assertEquals(null, result.value.total)
        assertEquals(13, result.value.progress)
    }

    @Test
    fun mapsTheRequestedUnitIncludingZeroVolumeProgress() {
        val result =
            AniListSyncEntryMapper.map(
                accountId = ProviderAccountId("42"),
                entryId = ProviderCollectionEntryId("7"),
                mediaId = ProviderMediaId("101"),
                unit = ProviderProgressUnit.VOLUME,
                remoteEntryId = 7,
                remoteUserId = 42,
                remoteMediaId = 101,
                remoteStatus = "CURRENT",
                progress = 9,
                progressVolumes = 0,
                updatedAtSeconds = 1_720_000_000,
                totalEpisodes = null,
                totalChapters = null,
                totalVolumes = 4,
            ) as ProviderResult.Success

        assertEquals(ProviderProgressUnit.VOLUME, result.value.progressUnit)
        assertEquals(0, result.value.progress)
        assertEquals(4, result.value.total)
    }

    @Test
    fun rejectsMissingIdentityTimestampAndUnknownStatus() {
        val missingUser =
            AniListSyncEntryMapper.map(
                ProviderAccountId("42"),
                ProviderCollectionEntryId("7"),
                ProviderMediaId("101"),
                ProviderProgressUnit.EPISODE,
                remoteUserId = null,
                remoteEntryId = 7,
                remoteMediaId = 101,
                remoteStatus = "CURRENT",
                progress = 1,
                progressVolumes = 0,
                updatedAtSeconds = 1_720_000_000,
                totalEpisodes = 12,
                totalChapters = null,
                totalVolumes = null,
            )
        assertEquals(ProviderResult.Failure(ProviderFailure.MalformedResponse), missingUser)
        assertEquals(
            ProviderResult.Failure(ProviderFailure.MalformedResponse),
            AniListSyncEntryMapper.map(
                ProviderAccountId("42"),
                ProviderCollectionEntryId("7"),
                ProviderMediaId("101"),
                ProviderProgressUnit.EPISODE,
                7,
                42,
                101,
                "UNKNOWN",
                1,
                0,
                1_720_000_000,
                12,
                null,
                null,
            ),
        )
    }

    @Test
    fun rejectsAnExplicitInvalidTotalInsteadOfTreatingItAsUnknown() {
        val result =
            AniListSyncEntryMapper.map(
                accountId = ProviderAccountId("42"),
                entryId = ProviderCollectionEntryId("7"),
                mediaId = ProviderMediaId("101"),
                unit = ProviderProgressUnit.EPISODE,
                remoteEntryId = 7,
                remoteUserId = 42,
                remoteMediaId = 101,
                remoteStatus = "CURRENT",
                progress = 13,
                progressVolumes = 0,
                updatedAtSeconds = 1_720_000_000,
                totalEpisodes = 12,
                totalChapters = null,
                totalVolumes = null,
            )

        assertEquals(ProviderResult.Failure(ProviderFailure.MalformedResponse), result)
    }
}
