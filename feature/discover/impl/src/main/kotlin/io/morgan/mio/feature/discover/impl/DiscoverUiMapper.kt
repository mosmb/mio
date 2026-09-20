package io.morgan.mio.feature.discover.impl

import io.morgan.mio.core.ui.title.MediaTitleNames
import io.morgan.mio.domain.collection.CollectionEntry
import io.morgan.mio.domain.collection.CollectionStatus
import io.morgan.mio.domain.media.MediaSearchResults
import io.morgan.mio.domain.model.MediaType

internal object DiscoverUiMapper {
    internal fun mapResults(
        results: MediaSearchResults?,
        collection: List<CollectionEntry>,
    ): List<DiscoverResultUiModel> {
        val trackedByMediaId = collection.associateBy { entry -> entry.mediaId }
        return results
            ?.media
            .orEmpty()
            .map { media ->
                DiscoverResultUiModel(
                    mediaId = media.id.value,
                    title = media.title.value,
                    titleNames =
                        MediaTitleNames(
                            media.title.english,
                            media.title.japanese,
                            media.title.romaji,
                        ),
                    posterReference = media.artwork.poster?.value,
                    type =
                        when (media.type) {
                            MediaType.ANIME -> DiscoverMediaType.ANIME
                            MediaType.MANGA -> DiscoverMediaType.MANGA
                        },
                    trackedStatus = trackedByMediaId[media.id]?.status?.toUiStatus(),
                )
            }
    }

    private fun CollectionStatus.toUiStatus(): DiscoverTrackedStatus =
        when (this) {
            CollectionStatus.CURRENT -> DiscoverTrackedStatus.CURRENT
            CollectionStatus.PLANNED -> DiscoverTrackedStatus.PLANNED
            CollectionStatus.PAUSED -> DiscoverTrackedStatus.PAUSED
            CollectionStatus.COMPLETED -> DiscoverTrackedStatus.COMPLETED
            CollectionStatus.DROPPED -> DiscoverTrackedStatus.DROPPED
            CollectionStatus.REPEATING -> DiscoverTrackedStatus.REPEATING
        }
}
