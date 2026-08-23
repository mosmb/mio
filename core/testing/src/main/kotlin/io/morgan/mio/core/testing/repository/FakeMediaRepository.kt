package io.morgan.mio.core.testing.repository

import io.morgan.mio.domain.media.Media
import io.morgan.mio.domain.media.MediaRepository
import io.morgan.mio.domain.media.Release
import io.morgan.mio.domain.model.MediaId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

public class FakeMediaRepository : MediaRepository {
    private data class State(
        val media: List<Media>,
        val releases: List<Release>,
        val relatedMedia: Map<MediaId, List<Media>>,
    )

    private val state =
        MutableStateFlow(
            State(
                media = emptyList(),
                releases = emptyList(),
                relatedMedia = emptyMap(),
            ),
        )
    private val refreshedMediaIdValues = MutableStateFlow<List<MediaId>>(emptyList())
    private val refreshFailures = MutableStateFlow<Map<MediaId, Throwable>>(emptyMap())

    public val refreshedMediaIds: List<MediaId>
        get() = refreshedMediaIdValues.value.toList()

    public fun setMedia(media: List<Media>) {
        state.update { current -> current.copy(media = media.toList()) }
    }

    public fun setReleases(releases: List<Release>) {
        state.update { current -> current.copy(releases = releases.toList()) }
    }

    public fun setRelatedMedia(
        mediaId: MediaId,
        relatedMedia: List<Media>,
    ) {
        state.update { current ->
            current.copy(relatedMedia = current.relatedMedia + (mediaId to relatedMedia.toList()))
        }
    }

    public fun failNextRefresh(
        mediaId: MediaId,
        failure: Throwable,
    ) {
        refreshFailures.update { current -> current + (mediaId to failure) }
    }

    override fun observeMedia(mediaId: MediaId): Flow<Media?> =
        state
            .map { current -> current.media.firstOrNull { media -> media.id == mediaId } }
            .distinctUntilChanged()

    override fun observeReleases(mediaId: MediaId): Flow<List<Release>> =
        state
            .map { current -> current.releases.filter { release -> release.mediaId == mediaId }.toList() }
            .distinctUntilChanged()

    override fun observeRelatedMedia(mediaId: MediaId): Flow<List<Media>> =
        state
            .map { current -> current.relatedMedia[mediaId].orEmpty() }
            .distinctUntilChanged()

    override suspend fun refreshMedia(mediaId: MediaId) {
        refreshedMediaIdValues.update { mediaIds -> mediaIds + mediaId }
        val failure = refreshFailures.value[mediaId]
        if (failure != null) {
            refreshFailures.update { current -> current - mediaId }
            throw failure
        }
    }
}
