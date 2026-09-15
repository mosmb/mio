package io.morgan.mio.data.provider.myanimelist

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

internal class MyAnimeListSyncDataSource(
    private val service: MyAnimeListApiService,
) : ProviderSyncDataSource {
    override val provider: ProviderKey = ProviderKey.MY_ANIME_LIST

    override suspend fun readEntry(
        credentials: ProviderCredentials,
        accountId: ProviderAccountId,
        entryId: ProviderCollectionEntryId,
        mediaId: ProviderMediaId,
        unit: ProviderProgressUnit,
    ): ProviderResult<ProviderCollectionEntry> {
        require(credentials.provider == provider)
        val match = parseId(entryId) ?: return ProviderResult.Failure(ProviderFailure.MalformedResponse)
        return try {
            val response =
                if (match.kind == Kind.ANIME) {
                    service.syncAnimeDetails(
                        id = match.id,
                        authorization = authorization(credentials),
                        fields = MyAnimeListRequestParameters.SYNC_ANIME_FIELDS,
                    )
                } else {
                    service.syncMangaDetails(
                        id = match.id,
                        authorization = authorization(credentials),
                        fields = MyAnimeListRequestParameters.SYNC_MANGA_FIELDS,
                    )
                }
            if (!response.isSuccessful) {
                return ProviderResult.Failure(
                    MyAnimeListFailureMapper.mapSyncHttpStatus(
                        response.code(),
                        response.headers()["Retry-After"],
                    ),
                )
            }
            val body = response.body() ?: return ProviderResult.Failure(ProviderFailure.MalformedResponse)
            MyAnimeListSyncEntryMapper.map(accountId, entryId, mediaId, unit, body)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (throwable: Throwable) {
            ProviderResult.Failure(MyAnimeListFailureMapper.mapThrowable(throwable))
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
        val match = parseId(entryId) ?: return ProviderResult.Failure(ProviderFailure.MalformedResponse)
        return try {
            val response =
                if (match.kind == Kind.ANIME) {
                    if (unit != ProviderProgressUnit.EPISODE) {
                        return ProviderResult.Failure(ProviderFailure.MutationRejected)
                    }
                    service.updateAnimeListStatus(
                        id = match.id,
                        authorization = authorization(credentials),
                        numWatchedEpisodes = target,
                    )
                } else {
                    when (unit) {
                        ProviderProgressUnit.CHAPTER ->
                            service.updateMangaListStatus(
                                id = match.id,
                                authorization = authorization(credentials),
                                numChaptersRead = target,
                            )
                        ProviderProgressUnit.VOLUME ->
                            service.updateMangaListStatus(
                                id = match.id,
                                authorization = authorization(credentials),
                                numVolumesRead = target,
                            )
                        ProviderProgressUnit.EPISODE ->
                            return ProviderResult.Failure(ProviderFailure.MutationRejected)
                    }
                }
            val failure = mapMutationResponse(response)
            if (failure != null) {
                ProviderResult.Failure(failure)
            } else {
                readEntry(credentials, accountId, entryId, mediaId, unit)
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (throwable: Throwable) {
            ProviderResult.Failure(MyAnimeListFailureMapper.mapThrowable(throwable))
        }
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
        val match = parseId(entryId) ?: return ProviderResult.Failure(ProviderFailure.MalformedResponse)
        val wireStatus =
            target.toWireValue(match.kind) ?: return ProviderResult.Failure(ProviderFailure.MutationRejected)
        return try {
            val response =
                if (match.kind == Kind.ANIME) {
                    service.updateAnimeListStatus(
                        id = match.id,
                        authorization = authorization(credentials),
                        status = wireStatus,
                    )
                } else {
                    service.updateMangaListStatus(
                        id = match.id,
                        authorization = authorization(credentials),
                        status = wireStatus,
                    )
                }
            val failure = mapMutationResponse(response)
            if (failure != null) {
                ProviderResult.Failure(failure)
            } else {
                readEntry(credentials, accountId, entryId, mediaId, unit)
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (throwable: Throwable) {
            ProviderResult.Failure(MyAnimeListFailureMapper.mapThrowable(throwable))
        }
    }

    private fun mapMutationResponse(
        response: retrofit2.Response<MyAnimeListSyncMutationResponseDto>,
    ): ProviderFailure? {
        if (!response.isSuccessful) {
            return MyAnimeListFailureMapper.mapSyncHttpStatus(
                response.code(),
                response.headers()["Retry-After"],
            )
        }
        // MAL's PATCH response is only a partial list-status projection. It is never used as the
        // acknowledgement; the caller always performs the complete authenticated read-back.
        return null
    }

    private fun parseId(entryId: ProviderCollectionEntryId): ParsedId? {
        val match = Regex("^(anime|manga)/([1-9][0-9]*)$").matchEntire(entryId.value) ?: return null
        val id = match.groupValues[2].toIntOrNull() ?: return null
        return ParsedId(if (match.groupValues[1] == "anime") Kind.ANIME else Kind.MANGA, id)
    }

    private fun authorization(credentials: ProviderCredentials): String =
        "${credentials.tokenType} ${credentials.accessToken}"

    private fun ProviderCollectionStatus.toWireValue(kind: Kind): String? =
        when (this) {
            ProviderCollectionStatus.CURRENT -> if (kind == Kind.ANIME) "watching" else "reading"
            ProviderCollectionStatus.PLANNED -> if (kind == Kind.ANIME) "plan_to_watch" else "plan_to_read"
            ProviderCollectionStatus.PAUSED -> "on_hold"
            ProviderCollectionStatus.COMPLETED -> "completed"
            ProviderCollectionStatus.DROPPED -> "dropped"
            ProviderCollectionStatus.REPEATING -> null
        }

    private data class ParsedId(
        val kind: Kind,
        val id: Int,
    )

    private enum class Kind {
        ANIME,
        MANGA,
    }
}
