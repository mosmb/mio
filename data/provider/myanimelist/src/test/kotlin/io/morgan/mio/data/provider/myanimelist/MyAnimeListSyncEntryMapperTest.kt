package io.morgan.mio.data.provider.myanimelist

import io.morgan.mio.data.provider.api.ProviderAccountId
import io.morgan.mio.data.provider.api.ProviderCollectionEntryId
import io.morgan.mio.data.provider.api.ProviderFailure
import io.morgan.mio.data.provider.api.ProviderMediaId
import io.morgan.mio.data.provider.api.ProviderProgressUnit
import io.morgan.mio.data.provider.api.ProviderResult
import org.junit.Assert.assertEquals
import org.junit.Test

class MyAnimeListSyncEntryMapperTest {
    @Test
    fun zeroTotalMeansUnknownForAnOngoingTitle() {
        val response =
            MyAnimeListSyncEntryResponseDto(
                id = 1,
                numEpisodes = 0,
                myListStatus =
                    MyAnimeListSyncEntryResponseDto.MyListStatus(
                        status = "watching",
                        numEpisodesWatched = 13,
                        updatedAt = "2026-09-11T10:00:00Z",
                    ),
            )
        val result =
            MyAnimeListSyncEntryMapper.map(
                ProviderAccountId("42"),
                ProviderCollectionEntryId("anime/1"),
                ProviderMediaId("anime/1"),
                ProviderProgressUnit.EPISODE,
                response,
            ) as ProviderResult.Success
        assertEquals(null, result.value.total)
        assertEquals(13, result.value.progress)
    }

    @Test
    fun volumeUnitDoesNotFallBackToChaptersAfterTheRemoteValueReachesZero() {
        val response =
            MyAnimeListSyncEntryResponseDto(
                id = 2,
                numChapters = 30,
                numVolumes = 5,
                myListStatus =
                    MyAnimeListSyncEntryResponseDto.MyListStatus(
                        status = "reading",
                        numChaptersRead = 0,
                        numVolumesRead = 0,
                        isRereading = false,
                        updatedAt = "2026-09-11T10:00:00+00:00",
                    ),
            )

        val result =
            MyAnimeListSyncEntryMapper.map(
                ProviderAccountId("42"),
                ProviderCollectionEntryId("manga/2"),
                ProviderMediaId("manga/2"),
                ProviderProgressUnit.VOLUME,
                response,
            ) as ProviderResult.Success

        assertEquals(ProviderProgressUnit.VOLUME, result.value.progressUnit)
        assertEquals(0, result.value.progress)
        assertEquals(5, result.value.total)
    }

    @Test
    fun rejectsAnExplicitInvalidTotalInsteadOfTreatingItAsUnknown() {
        val response =
            MyAnimeListSyncEntryResponseDto(
                id = 1,
                numEpisodes = 12,
                myListStatus =
                    MyAnimeListSyncEntryResponseDto.MyListStatus(
                        status = "watching",
                        numEpisodesWatched = 13,
                        updatedAt = "2026-09-11T10:00:00+00:00",
                    ),
            )

        assertEquals(
            ProviderResult.Failure(ProviderFailure.MalformedResponse),
            MyAnimeListSyncEntryMapper.map(
                ProviderAccountId("42"),
                ProviderCollectionEntryId("anime/1"),
                ProviderMediaId("anime/1"),
                ProviderProgressUnit.EPISODE,
                response,
            ),
        )
    }
}
