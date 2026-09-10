package io.morgan.mio.feature.media.impl

import android.text.Html
import io.morgan.mio.domain.collection.CollectionEntry
import io.morgan.mio.domain.collection.CollectionStatus
import io.morgan.mio.domain.media.Media
import io.morgan.mio.domain.media.MediaFormat
import io.morgan.mio.domain.media.MediaPublicationStatus
import io.morgan.mio.domain.model.MediaType
import io.morgan.mio.domain.model.ProgressUnit
import io.morgan.mio.domain.progress.ProgressAction
import io.morgan.mio.domain.progress.ProgressDirection
import io.morgan.mio.domain.progress.ProgressRules

internal object MediaDetailUiMapper {
    internal fun mapUndo(action: ProgressAction): MediaDetailUndoUiModel = action.toUiModel()

    internal fun mapContent(
        media: Media,
        related: List<Media>,
        collection: CollectionEntry?,
        latestUndo: ProgressAction? = null,
        progressError: MediaDetailProgressErrorUiModel? = null,
    ): MediaDetailUiState.Content =
        MediaDetailUiState.Content(
            media = media.toUiModel(),
            relatedTitles = related.map { item -> item.toRelatedUiModel() }.toList(),
            collection = collection?.toUiModel(),
            latestUndo = latestUndo?.toUiModel(),
            progressError = progressError,
        )

    internal fun mapSynopsis(value: String?): String? =
        value
            ?.let { html -> Html.fromHtml(html, Html.FROM_HTML_MODE_COMPACT).toString().trim() }
            ?.takeIf(String::isNotBlank)

    private fun Media.toUiModel(): MediaDetailUiModel =
        MediaDetailUiModel(
            id = id.value,
            title = title.value,
            type = type.toUiType(),
            format = format?.toUiFormat(),
            publicationStatus = publicationStatus.toUiStatus(),
            synopsis = mapSynopsis(synopsis),
            posterReference = artwork.poster?.value,
            bannerReference = artwork.banner?.value,
            totals =
                MediaDetailTotalsUiModel(
                    episodes = totals.episodes?.value,
                    chapters = totals.chapters?.value,
                    volumes = totals.volumes?.value,
                ),
            genres = genres.toList(),
            releaseYear = releaseYear,
            scorePercent = score?.percent,
        )

    private fun Media.toRelatedUiModel(): MediaDetailRelatedTitleUiModel =
        MediaDetailRelatedTitleUiModel(
            id = id.value,
            title = title.value,
            type = type.toUiType(),
            posterReference = artwork.poster?.value,
        )

    private fun CollectionEntry.toUiModel(): MediaDetailCollectionUiModel =
        MediaDetailCollectionUiModel(
            status = status.toUiStatus(),
            progress = progress.value,
            progressUnit = progressUnit.toUiUnit(),
            total = total?.value,
            controls = progressControls(),
        )

    private fun CollectionEntry.progressControls(): MediaDetailProgressControlsUiModel? =
        if (status != CollectionStatus.CURRENT) {
            null
        } else {
            MediaDetailProgressControlsUiModel(
                decreaseEnabled =
                    ProgressRules.decide(this, ProgressDirection.DECREASE) is ProgressRules.Decision.Change,
                increaseEnabled =
                    ProgressRules.decide(this, ProgressDirection.INCREASE) is ProgressRules.Decision.Change,
            )
        }

    private fun ProgressAction.toUiModel(): MediaDetailUndoUiModel =
        MediaDetailUndoUiModel(
            groupId = id.value,
            mediaId = mediaId.value,
            progress = afterProgress.value,
            unit = unit.toUiUnit(),
            completed = afterStatus == CollectionStatus.COMPLETED,
        )

    private fun MediaType.toUiType(): MediaDetailMediaType =
        when (this) {
            MediaType.ANIME -> MediaDetailMediaType.ANIME
            MediaType.MANGA -> MediaDetailMediaType.MANGA
        }

    private fun MediaFormat.toUiFormat(): MediaDetailFormat =
        when (this) {
            MediaFormat.TV -> MediaDetailFormat.TV
            MediaFormat.MOVIE -> MediaDetailFormat.MOVIE
            MediaFormat.OVA -> MediaDetailFormat.OVA
            MediaFormat.ONA -> MediaDetailFormat.ONA
            MediaFormat.SPECIAL -> MediaDetailFormat.SPECIAL
            MediaFormat.MUSIC -> MediaDetailFormat.MUSIC
            MediaFormat.MANGA -> MediaDetailFormat.MANGA
            MediaFormat.NOVEL -> MediaDetailFormat.NOVEL
            MediaFormat.ONE_SHOT -> MediaDetailFormat.ONE_SHOT
            MediaFormat.OTHER -> MediaDetailFormat.OTHER
        }

    private fun MediaPublicationStatus.toUiStatus(): MediaDetailPublicationStatus =
        when (this) {
            MediaPublicationStatus.UPCOMING -> MediaDetailPublicationStatus.UPCOMING
            MediaPublicationStatus.RELEASING -> MediaDetailPublicationStatus.RELEASING
            MediaPublicationStatus.FINISHED -> MediaDetailPublicationStatus.FINISHED
            MediaPublicationStatus.CANCELLED -> MediaDetailPublicationStatus.CANCELLED
            MediaPublicationStatus.HIATUS -> MediaDetailPublicationStatus.HIATUS
            MediaPublicationStatus.UNKNOWN -> MediaDetailPublicationStatus.UNKNOWN
        }

    private fun CollectionStatus.toUiStatus(): MediaDetailCollectionStatus =
        when (this) {
            CollectionStatus.CURRENT -> MediaDetailCollectionStatus.CURRENT
            CollectionStatus.PLANNED -> MediaDetailCollectionStatus.PLANNED
            CollectionStatus.PAUSED -> MediaDetailCollectionStatus.PAUSED
            CollectionStatus.COMPLETED -> MediaDetailCollectionStatus.COMPLETED
            CollectionStatus.DROPPED -> MediaDetailCollectionStatus.DROPPED
            CollectionStatus.REPEATING -> MediaDetailCollectionStatus.REPEATING
        }

    private fun ProgressUnit.toUiUnit(): MediaDetailProgressUnit =
        when (this) {
            ProgressUnit.EPISODE -> MediaDetailProgressUnit.EPISODE
            ProgressUnit.CHAPTER -> MediaDetailProgressUnit.CHAPTER
            ProgressUnit.VOLUME -> MediaDetailProgressUnit.VOLUME
        }
}
