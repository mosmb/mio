package io.morgan.mio.feature.media.impl

import android.text.Html
import io.morgan.mio.domain.collection.CollectionAction
import io.morgan.mio.domain.collection.CollectionActionKind
import io.morgan.mio.domain.collection.CollectionEntry
import io.morgan.mio.domain.collection.CollectionStatus
import io.morgan.mio.domain.media.Media
import io.morgan.mio.domain.media.MediaFormat
import io.morgan.mio.domain.media.MediaPublicationStatus
import io.morgan.mio.domain.model.MediaId
import io.morgan.mio.domain.model.MediaType
import io.morgan.mio.domain.model.ProgressUnit
import io.morgan.mio.domain.progress.ProgressAction
import io.morgan.mio.domain.progress.ProgressDirection
import io.morgan.mio.domain.progress.ProgressRules
import io.morgan.mio.domain.sync.SyncAccountIssueKind
import io.morgan.mio.domain.sync.SyncIssue
import io.morgan.mio.domain.sync.SyncRecoveryState
import io.morgan.mio.domain.sync.SyncIssueKind as DomainSyncIssueKind

internal object MediaDetailUiMapper {
    internal fun mapUndo(action: ProgressAction): MediaDetailUndoUiModel = action.toUiModel()

    internal fun mapContent(
        media: Media,
        related: List<Media>,
        collection: CollectionEntry?,
        latestUndo: MediaDetailUndoUiModel? = null,
        writableStatuses: List<CollectionStatus> = CollectionStatus.entries.toList(),
        progressError: MediaDetailProgressErrorUiModel? = null,
        syncIssues: List<MediaDetailUiState.SyncIssueUiModel> = emptyList(),
        accountIssue: MediaDetailUiState.AccountIssueUiModel? = null,
    ): MediaDetailUiState.Content =
        MediaDetailUiState.Content(
            media = media.toUiModel(),
            relatedTitles = related.map { item -> item.toRelatedUiModel() }.toList(),
            collection = collection?.toUiModel(),
            writableStatuses = writableStatuses.map { status -> status.toUiStatus() },
            latestUndo = latestUndo,
            progressError = progressError,
            syncIssues = syncIssues,
            accountIssue = accountIssue,
        )

    internal fun mapRecovery(
        recovery: SyncRecoveryState,
        collection: CollectionEntry?,
        mediaId: MediaId,
        title: String,
    ): MediaDetailRecoveryUiModel =
        MediaDetailRecoveryUiModel(
            syncIssues =
                recovery.entryIssues
                    .filter { issue -> issue.mediaId == mediaId || issue.entryId == collection?.id }
                    .map { issue -> issue.toUiModel(title) },
            accountIssue =
                recovery.accountIssue?.let { issue ->
                    MediaDetailUiState.AccountIssueUiModel(issue.kind.toUiKind())
                },
        )

    internal fun mapUndo(action: CollectionAction): MediaDetailUndoUiModel =
        MediaDetailUndoUiModel(
            groupId = action.id.value,
            mediaId = action.mediaId.value,
            progress = action.after.progress.value,
            unit = action.after.progressUnit.toUiUnit(),
            completed = action.after.status == CollectionStatus.COMPLETED,
            collectionAction = true,
            collectionKind =
                if (action.kind == CollectionActionKind.ADD) {
                    MediaDetailCollectionActionKind.ADD
                } else {
                    MediaDetailCollectionActionKind.STATUS
                },
            status = action.after.status.toUiStatus(),
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

    private fun SyncIssue.toUiModel(title: String): MediaDetailUiState.SyncIssueUiModel =
        MediaDetailUiState.SyncIssueUiModel(
            operationId = operationId.value,
            entryId = entryId.value,
            title = title,
            kind = kind.toUiKind(),
            localProgress = local.progress.value,
            localUnit = local.unit.toUiUnit(),
            localStatus = local.status.toUiStatus(),
            localTotal = local.total?.value,
            remoteProgress = remote?.progress?.value,
            remoteUnit = remote?.unit?.toUiUnit(),
            remoteStatus = remote?.status?.toUiStatus(),
            canRetry = kind == DomainSyncIssueKind.REJECTED || kind == DomainSyncIssueKind.RETRY_EXHAUSTED,
        )

    private fun DomainSyncIssueKind.toUiKind(): SyncIssueKind =
        when (this) {
            DomainSyncIssueKind.AUTHENTICATION -> SyncIssueKind.AUTHENTICATION
            DomainSyncIssueKind.REJECTED -> SyncIssueKind.REJECTED
            DomainSyncIssueKind.CONFLICT -> SyncIssueKind.CONFLICT
            DomainSyncIssueKind.RETRY_EXHAUSTED -> SyncIssueKind.RETRY_EXHAUSTED
            DomainSyncIssueKind.INVALID_REMOTE_DATA -> SyncIssueKind.INVALID_REMOTE_DATA
        }

    private fun SyncAccountIssueKind.toUiKind(): AccountIssueKind =
        when (this) {
            SyncAccountIssueKind.AUTHENTICATION -> AccountIssueKind.AUTHENTICATION
            SyncAccountIssueKind.INVALID_REMOTE_DATA -> AccountIssueKind.INVALID_REMOTE_DATA
        }

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

internal data class MediaDetailRecoveryUiModel(
    val syncIssues: List<MediaDetailUiState.SyncIssueUiModel>,
    val accountIssue: MediaDetailUiState.AccountIssueUiModel?,
)
