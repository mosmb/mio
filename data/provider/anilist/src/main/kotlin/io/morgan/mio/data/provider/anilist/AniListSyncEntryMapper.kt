package io.morgan.mio.data.provider.anilist

import io.morgan.mio.data.provider.api.ProviderAccountId
import io.morgan.mio.data.provider.api.ProviderCollectionEntry
import io.morgan.mio.data.provider.api.ProviderCollectionEntryId
import io.morgan.mio.data.provider.api.ProviderCollectionStatus
import io.morgan.mio.data.provider.api.ProviderFailure
import io.morgan.mio.data.provider.api.ProviderMediaId
import io.morgan.mio.data.provider.api.ProviderProgressUnit
import io.morgan.mio.data.provider.api.ProviderResult
import kotlin.time.Instant

internal object AniListSyncEntryMapper {
    internal fun map(
        accountId: ProviderAccountId,
        entryId: ProviderCollectionEntryId,
        mediaId: ProviderMediaId,
        unit: ProviderProgressUnit,
        remoteEntryId: Int?,
        remoteUserId: Int?,
        remoteMediaId: Int?,
        remoteStatus: String?,
        progress: Int?,
        progressVolumes: Int?,
        updatedAtSeconds: Long?,
        totalEpisodes: Int?,
        totalChapters: Int?,
        totalVolumes: Int?,
    ): ProviderResult<ProviderCollectionEntry> {
        val expectedEntryId = entryId.value.toIntOrNull()?.takeIf { value -> value > 0 }
        val expectedMediaId = mediaId.value.toIntOrNull()?.takeIf { value -> value > 0 }
        if (expectedEntryId == null || expectedMediaId == null) {
            return ProviderResult.Failure(ProviderFailure.MalformedResponse)
        }
        if (
            remoteEntryId == null ||
            remoteEntryId != expectedEntryId ||
            remoteUserId == null ||
            remoteUserId.toString() != accountId.value
        ) {
            return ProviderResult.Failure(ProviderFailure.MalformedResponse)
        }
        if (remoteMediaId == null || remoteMediaId != expectedMediaId) {
            return ProviderResult.Failure(ProviderFailure.MalformedResponse)
        }
        val status = mapStatus(remoteStatus) ?: return ProviderResult.Failure(ProviderFailure.MalformedResponse)
        val rawProgress =
            when (unit) {
                ProviderProgressUnit.EPISODE,
                ProviderProgressUnit.CHAPTER,
                -> progress
                ProviderProgressUnit.VOLUME -> progressVolumes
            } ?: return ProviderResult.Failure(ProviderFailure.MalformedResponse)
        if (rawProgress < 0) return ProviderResult.Failure(ProviderFailure.MalformedResponse)
        val rawTotal =
            when (unit) {
                ProviderProgressUnit.EPISODE -> totalEpisodes
                ProviderProgressUnit.CHAPTER -> totalChapters
                ProviderProgressUnit.VOLUME -> totalVolumes
            }
        if (rawTotal != null && (rawTotal < 0 || (rawTotal > 0 && rawProgress > rawTotal))) {
            return ProviderResult.Failure(ProviderFailure.MalformedResponse)
        }
        val total = rawTotal?.takeIf { it > 0 }
        val updatedAt =
            try {
                updatedAtSeconds?.takeIf { value -> value > 0 }?.let(Instant::fromEpochSeconds)
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
                    progress = rawProgress,
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
            "CURRENT" -> ProviderCollectionStatus.CURRENT
            "PLANNING" -> ProviderCollectionStatus.PLANNED
            "PAUSED" -> ProviderCollectionStatus.PAUSED
            "COMPLETED" -> ProviderCollectionStatus.COMPLETED
            "DROPPED" -> ProviderCollectionStatus.DROPPED
            "REPEATING" -> ProviderCollectionStatus.REPEATING
            else -> null
        }
}
