package io.morgan.mio.data.provider.anilist

import io.morgan.mio.data.provider.api.ProviderAccountId
import io.morgan.mio.data.provider.api.ProviderCollectionEntry
import io.morgan.mio.data.provider.api.ProviderCollectionEntryId
import io.morgan.mio.data.provider.api.ProviderCollectionStatus
import io.morgan.mio.data.provider.api.ProviderCredentials
import io.morgan.mio.data.provider.api.ProviderFailure
import io.morgan.mio.data.provider.api.ProviderKey
import io.morgan.mio.data.provider.api.ProviderMediaId
import io.morgan.mio.data.provider.api.ProviderProgressUnit
import io.morgan.mio.data.provider.api.ProviderResult
import io.morgan.mio.data.provider.api.ProviderSyncDataSource
import kotlinx.coroutines.CancellationException

internal class AniListSyncDataSource(
    private val service: AniListGraphQlService,
) : ProviderSyncDataSource {
    override val provider: ProviderKey = ProviderKey.ANILIST

    override suspend fun readEntry(
        credentials: ProviderCredentials,
        accountId: ProviderAccountId,
        entryId: ProviderCollectionEntryId,
        mediaId: ProviderMediaId,
        unit: ProviderProgressUnit,
    ): ProviderResult<ProviderCollectionEntry> {
        require(credentials.provider == provider)
        val remoteEntryId =
            entryId.value.toIntOrNull()?.takeIf { value -> value > 0 }
                ?: return ProviderResult.Failure(ProviderFailure.MalformedResponse)
        return try {
            val response =
                service.syncEntry(
                    authorization = authorization(credentials),
                    request =
                        AniListSyncEntryRequestDto(
                            query = AniListQueries.SYNC_ENTRY,
                            variables = AniListSyncEntryRequestDto.Variables(remoteEntryId),
                        ),
                )
            mapReadResponse(response, accountId, entryId, mediaId, unit)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (throwable: Throwable) {
            ProviderResult.Failure(AniListFailureMapper.mapThrowable(throwable))
        }
    }

    override suspend fun setProgress(
        credentials: ProviderCredentials,
        accountId: ProviderAccountId,
        entryId: ProviderCollectionEntryId,
        mediaId: ProviderMediaId,
        unit: ProviderProgressUnit,
        target: Int,
    ): ProviderResult<ProviderCollectionEntry> {
        require(target >= 0)
        val preflight = readEntry(credentials, accountId, entryId, mediaId, unit)
        if (preflight is ProviderResult.Failure) return preflight
        val remoteEntryId =
            entryId.value.toIntOrNull()?.takeIf { value -> value > 0 }
                ?: return ProviderResult.Failure(ProviderFailure.MalformedResponse)
        val variables =
            when (unit) {
                ProviderProgressUnit.VOLUME ->
                    AniListSyncMutationRequestDto.Variables(id = remoteEntryId, progressVolumes = target)
                ProviderProgressUnit.EPISODE,
                ProviderProgressUnit.CHAPTER,
                -> AniListSyncMutationRequestDto.Variables(id = remoteEntryId, progress = target)
            }
        return mutateAndReadBack(
            credentials = credentials,
            accountId = accountId,
            entryId = entryId,
            mediaId = mediaId,
            unit = unit,
            variables = variables,
        )
    }

    override suspend fun setStatus(
        credentials: ProviderCredentials,
        accountId: ProviderAccountId,
        entryId: ProviderCollectionEntryId,
        mediaId: ProviderMediaId,
        unit: ProviderProgressUnit,
        target: ProviderCollectionStatus,
    ): ProviderResult<ProviderCollectionEntry> {
        val preflight = readEntry(credentials, accountId, entryId, mediaId, unit)
        if (preflight is ProviderResult.Failure) return preflight
        val remoteEntryId =
            entryId.value.toIntOrNull()?.takeIf { value -> value > 0 }
                ?: return ProviderResult.Failure(ProviderFailure.MalformedResponse)
        return mutateAndReadBack(
            credentials = credentials,
            accountId = accountId,
            entryId = entryId,
            mediaId = mediaId,
            unit = unit,
            variables =
                AniListSyncMutationRequestDto.Variables(
                    id = remoteEntryId,
                    status = target.toWireValue(),
                ),
        )
    }

    private suspend fun mutateAndReadBack(
        credentials: ProviderCredentials,
        accountId: ProviderAccountId,
        entryId: ProviderCollectionEntryId,
        mediaId: ProviderMediaId,
        unit: ProviderProgressUnit,
        variables: AniListSyncMutationRequestDto.Variables,
    ): ProviderResult<ProviderCollectionEntry> {
        return try {
            val response =
                service.syncMutation(
                    authorization = authorization(credentials),
                    request = AniListSyncMutationRequestDto(AniListQueries.SYNC_MUTATION, variables),
                )
            when {
                !response.isSuccessful ->
                    ProviderResult.Failure(
                        AniListFailureMapper.mapSyncHttpStatus(
                            response.code(),
                            response.headers()["Retry-After"],
                        ),
                    )
                else -> {
                    val body = response.body() ?: return ProviderResult.Failure(ProviderFailure.MalformedResponse)
                    val graphQlFailure = graphQlFailure(body.errors)
                    if (graphQlFailure != null) {
                        ProviderResult.Failure(graphQlFailure)
                    } else if (body.data?.mediaList == null) {
                        ProviderResult.Failure(ProviderFailure.MalformedResponse)
                    } else {
                        readEntry(credentials, accountId, entryId, mediaId, unit)
                    }
                }
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (throwable: Throwable) {
            ProviderResult.Failure(AniListFailureMapper.mapThrowable(throwable))
        }
    }

    private fun mapReadResponse(
        response: retrofit2.Response<AniListSyncEntryResponseDto>,
        accountId: ProviderAccountId,
        entryId: ProviderCollectionEntryId,
        mediaId: ProviderMediaId,
        unit: ProviderProgressUnit,
    ): ProviderResult<ProviderCollectionEntry> {
        if (!response.isSuccessful) {
            return ProviderResult.Failure(
                AniListFailureMapper.mapSyncHttpStatus(response.code(), response.headers()["Retry-After"]),
            )
        }
        val body = response.body() ?: return ProviderResult.Failure(ProviderFailure.MalformedResponse)
        graphQlFailure(body.errors)?.let { failure -> return ProviderResult.Failure(failure) }
        val entry = body.data?.mediaList ?: return ProviderResult.Failure(ProviderFailure.MutationRejected)
        return AniListSyncEntryMapper.map(
            accountId = accountId,
            entryId = entryId,
            mediaId = mediaId,
            unit = unit,
            remoteEntryId = entry.id,
            remoteUserId = entry.userId,
            remoteMediaId = entry.mediaId ?: entry.media?.id,
            remoteStatus = entry.status,
            progress = entry.progress,
            progressVolumes = entry.progressVolumes,
            updatedAtSeconds = entry.updatedAt,
            totalEpisodes = entry.media?.episodes,
            totalChapters = entry.media?.chapters,
            totalVolumes = entry.media?.volumes,
        )
    }

    private fun graphQlFailure(errors: List<AniListGraphQlErrorDto?>?): ProviderFailure? {
        if (errors.isNullOrEmpty()) return null
        val status = errors.first()?.status ?: return ProviderFailure.MalformedResponse
        return AniListFailureMapper.mapSyncHttpStatus(status, null)
    }

    private fun authorization(credentials: ProviderCredentials): String =
        "${credentials.tokenType} ${credentials.accessToken}"

    private fun ProviderCollectionStatus.toWireValue(): String =
        when (this) {
            ProviderCollectionStatus.CURRENT -> "CURRENT"
            ProviderCollectionStatus.PLANNED -> "PLANNING"
            ProviderCollectionStatus.PAUSED -> "PAUSED"
            ProviderCollectionStatus.COMPLETED -> "COMPLETED"
            ProviderCollectionStatus.DROPPED -> "DROPPED"
            ProviderCollectionStatus.REPEATING -> "REPEATING"
        }
}
