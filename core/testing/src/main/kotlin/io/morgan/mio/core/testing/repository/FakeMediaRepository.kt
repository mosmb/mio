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
    )

    private val state = MutableStateFlow(State(media = emptyList(), releases = emptyList()))
    private val refreshedMediaIdValues = MutableStateFlow<List<MediaId>>(emptyList())

    public val refreshedMediaIds: List<MediaId>
        get() = refreshedMediaIdValues.value.toList()

    public fun setMedia(media: List<Media>) {
        state.update { current -> current.copy(media = media.toList()) }
    }

    public fun setReleases(releases: List<Release>) {
        state.update { current -> current.copy(releases = releases.toList()) }
    }

    override fun observeMedia(mediaId: MediaId): Flow<Media?> =
        state
            .map { current -> current.media.firstOrNull { media -> media.id == mediaId } }
            .distinctUntilChanged()

    override fun observeReleases(mediaId: MediaId): Flow<List<Release>> =
        state
            .map { current -> current.releases.filter { release -> release.mediaId == mediaId }.toList() }
            .distinctUntilChanged()

    override suspend fun refreshMedia(mediaId: MediaId) {
        refreshedMediaIdValues.update { mediaIds -> mediaIds + mediaId }
    }
}
