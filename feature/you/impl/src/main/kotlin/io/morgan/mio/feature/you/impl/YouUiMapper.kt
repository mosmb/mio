package io.morgan.mio.feature.you.impl

import io.morgan.mio.core.ui.title.MediaTitleNames
import io.morgan.mio.domain.account.Account
import io.morgan.mio.domain.account.ConnectedProvider
import io.morgan.mio.domain.collection.CollectionStatisticsSnapshot
import io.morgan.mio.domain.model.MediaType
import io.morgan.mio.domain.model.ProgressUnit
import io.morgan.mio.domain.preferences.AppearancePreference
import io.morgan.mio.domain.progress.RecordedProgressActivity
import io.morgan.mio.domain.sync.SyncAccountIssueKind
import io.morgan.mio.domain.sync.SyncIssue
import io.morgan.mio.domain.sync.SyncIssueKind
import io.morgan.mio.domain.sync.SyncRecoveryState
import kotlinx.datetime.LocalDate

internal object YouUiMapper {
    fun map(
        account: Account,
        appearance: AppearancePreference,
        recovery: SyncRecoveryState,
        pendingRetryIds: Set<String>,
        retryFailed: Boolean,
        statistics: YouCollectionStatisticsUiState,
        activity: YouActivityUiState,
        selectedStatisticsType: MediaType,
        rotationDate: LocalDate,
    ): YouUiState.Content =
        YouUiState.Content(
            identity =
                YouIdentityUiModel(
                    displayName = account.displayName,
                    provider = account.provider.toUiProvider(),
                    avatarReference = account.avatar?.value,
                ),
            appearance = appearance,
            syncIssues = recovery.entryIssues.map { issue -> issue.toUiModel() },
            accountIssue = recovery.accountIssue?.kind?.toUiKind(),
            pendingRetryIds = pendingRetryIds,
            retryFailed = retryFailed,
            statistics = statistics,
            activity = activity,
            selectedStatisticsType = selectedStatisticsType,
            rotationDate = rotationDate,
        )

    fun mapStatistics(snapshot: CollectionStatisticsSnapshot): YouCollectionStatisticsUiState.Content =
        YouCollectionStatisticsUiState.Content(
            completedTitleCount = snapshot.completedTitleCount,
            episodes =
                YouRecordedProgressTotalUiModel(
                    value = snapshot.recordedEpisodes.value,
                    trackedTitleCount = snapshot.recordedEpisodes.trackedTitleCount,
                    includedTitleCount = snapshot.recordedEpisodes.includedTitleCount,
                ),
            chapters =
                YouRecordedProgressTotalUiModel(
                    value = snapshot.recordedChapters.value,
                    trackedTitleCount = snapshot.recordedChapters.trackedTitleCount,
                    includedTitleCount = snapshot.recordedChapters.includedTitleCount,
                ),
            anime = snapshot.anime.toUiModel(),
            manga = snapshot.manga.toUiModel(),
            artworkCandidates =
                snapshot.artwork.map { artwork ->
                    YouArtworkUiModel(
                        mediaId = artwork.mediaId.value,
                        title = artwork.title.value,
                        titleNames =
                            MediaTitleNames(
                                artwork.title.english,
                                artwork.title.japanese,
                                artwork.title.romaji,
                            ),
                        posterReference = artwork.poster?.value,
                    )
                },
        )

    fun mapActivity(
        window: YouActivityWindow,
        activities: List<RecordedProgressActivity>,
        mediaType: MediaType,
    ): YouActivityUiState.Content =
        YouActivityUiState.Content(
            fromDate = window.fromDate,
            throughDate = window.throughDate,
            weeks = window.summarize(activities, mediaType),
        )

    private fun CollectionStatisticsSnapshot.TypeStatistics.toUiModel(): YouMediaStatisticsUiModel =
        YouMediaStatisticsUiModel(
            trackedTitleCount = trackedTitleCount,
            completedTitleCount = completedTitleCount,
            genreMetadataTitleCount = genreMetadataTitleCount,
            topGenres = topGenres.map { genre -> YouGenreUiModel(genre.name, genre.titleCount) },
        )

    private fun SyncIssue.toUiModel(): YouSyncIssueUiModel =
        YouSyncIssueUiModel(
            operationId = operationId.value,
            entryId = entryId.value,
            kind = kind.toUiKind(),
            localProgress = local.progress.value,
            localStatus = local.status.name,
            localUnit = local.unit.toUiUnit(),
            localTotal = local.total?.value,
            remoteProgress = remote?.progress?.value,
            remoteStatus = remote?.status?.name,
            remoteUnit = remote?.unit?.toUiUnit(),
            canRetry = kind == SyncIssueKind.REJECTED || kind == SyncIssueKind.RETRY_EXHAUSTED,
            needsReconnect = kind == SyncIssueKind.AUTHENTICATION,
            needsResolve = kind == SyncIssueKind.CONFLICT,
        )

    private fun ConnectedProvider.toUiProvider(): YouProvider =
        when (this) {
            ConnectedProvider.ANILIST -> YouProvider.ANILIST
            ConnectedProvider.MY_ANIME_LIST -> YouProvider.MY_ANIME_LIST
        }

    private fun SyncIssueKind.toUiKind(): YouSyncIssueKind =
        when (this) {
            SyncIssueKind.AUTHENTICATION -> YouSyncIssueKind.AUTHENTICATION
            SyncIssueKind.REJECTED -> YouSyncIssueKind.REJECTED
            SyncIssueKind.CONFLICT -> YouSyncIssueKind.CONFLICT
            SyncIssueKind.RETRY_EXHAUSTED -> YouSyncIssueKind.RETRY_EXHAUSTED
            SyncIssueKind.INVALID_REMOTE_DATA -> YouSyncIssueKind.INVALID_REMOTE_DATA
        }

    private fun SyncAccountIssueKind.toUiKind(): YouAccountIssueKind =
        when (this) {
            SyncAccountIssueKind.AUTHENTICATION -> YouAccountIssueKind.AUTHENTICATION
            SyncAccountIssueKind.INVALID_REMOTE_DATA -> YouAccountIssueKind.INVALID_REMOTE_DATA
        }

    private fun ProgressUnit.toUiUnit(): YouProgressUnit =
        when (this) {
            ProgressUnit.EPISODE -> YouProgressUnit.EPISODE
            ProgressUnit.CHAPTER -> YouProgressUnit.CHAPTER
            ProgressUnit.VOLUME -> YouProgressUnit.VOLUME
        }
}
