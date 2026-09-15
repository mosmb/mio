package io.morgan.mio.data.provider.myanimelist

import io.morgan.mio.data.provider.api.ProviderAccountId
import io.morgan.mio.data.provider.api.ProviderCollectionEntry
import io.morgan.mio.data.provider.api.ProviderCollectionEntryId
import io.morgan.mio.data.provider.api.ProviderCollectionStatus
import io.morgan.mio.data.provider.api.ProviderFailure
import io.morgan.mio.data.provider.api.ProviderMediaId
import io.morgan.mio.data.provider.api.ProviderProgressUnit
import io.morgan.mio.data.provider.api.ProviderResult
import kotlin.time.Instant

internal object MyAnimeListSyncEntryMapper {
    internal fun map(
        accountId: ProviderAccountId,
        entryId: ProviderCollectionEntryId,
        mediaId: ProviderMediaId,
        unit: ProviderProgressUnit,
        response: MyAnimeListSyncEntryResponseDto,
    ): ProviderResult<ProviderCollectionEntry> {
        val match =
            Regex("^(anime|manga)/([1-9][0-9]*)$").matchEntire(entryId.value)
                ?: return ProviderResult.Failure(ProviderFailure.MalformedResponse)
        val expectedMediaId = mediaId.value
        if (expectedMediaId != entryId.value) return ProviderResult.Failure(ProviderFailure.MalformedResponse)
        val expectedId =
            match.groupValues[2].toIntOrNull()
                ?: return ProviderResult.Failure(ProviderFailure.MalformedResponse)
        if (response.id != expectedId) return ProviderResult.Failure(ProviderFailure.MalformedResponse)
        val listStatus = response.myListStatus ?: return ProviderResult.Failure(ProviderFailure.MutationRejected)
        val status =
            when {
                listStatus.isRewatching == true || listStatus.isRereading == true ->
                    ProviderCollectionStatus.REPEATING
                else -> mapStatus(listStatus.status)
            } ?: return ProviderResult.Failure(ProviderFailure.MalformedResponse)
        val progressAndTotal =
            when (unit) {
                ProviderProgressUnit.EPISODE -> listStatus.numEpisodesWatched to response.numEpisodes
                ProviderProgressUnit.CHAPTER -> listStatus.numChaptersRead to response.numChapters
                ProviderProgressUnit.VOLUME -> listStatus.numVolumesRead to response.numVolumes
            }
        val progress = progressAndTotal.first ?: return ProviderResult.Failure(ProviderFailure.MalformedResponse)
        if (progress < 0) return ProviderResult.Failure(ProviderFailure.MalformedResponse)
        val rawTotal = progressAndTotal.second
        if (rawTotal != null && (rawTotal < 0 || (rawTotal > 0 && progress > rawTotal))) {
            return ProviderResult.Failure(ProviderFailure.MalformedResponse)
        }
        val total = rawTotal?.takeIf { it > 0 }
        val updatedAt =
            try {
                listStatus.updatedAt?.takeIf(String::isNotBlank)?.let(Instant::parse)
            } catch (_: IllegalArgumentException) {
                null
            } ?: return ProviderResult.Failure(ProviderFailure.MalformedResponse)
        return try {
            ProviderResult.Success(
                ProviderCollectionEntry(
                    id = entryId,
                    accountId = accountId,
                    mediaId = mediaId,
                    status = status,
                    progress = progress,
                    progressUnit = unit,
                    total = total,
                    updatedAt = updatedAt,
                ),
            )
        } catch (_: IllegalArgumentException) {
            ProviderResult.Failure(ProviderFailure.MalformedResponse)
        }
    }

    private fun mapStatus(value: String?): ProviderCollectionStatus? =
        when (value) {
            "watching", "reading" -> ProviderCollectionStatus.CURRENT
            "plan_to_watch", "plan_to_read" -> ProviderCollectionStatus.PLANNED
            "on_hold" -> ProviderCollectionStatus.PAUSED
            "completed" -> ProviderCollectionStatus.COMPLETED
            "dropped" -> ProviderCollectionStatus.DROPPED
            else -> null
        }
}
