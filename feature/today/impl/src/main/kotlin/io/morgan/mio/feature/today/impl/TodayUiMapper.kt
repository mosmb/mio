package io.morgan.mio.feature.today.impl

import io.morgan.mio.domain.collection.CollectionEntry
import io.morgan.mio.domain.collection.CollectionStatus
import io.morgan.mio.domain.model.MediaType
import io.morgan.mio.domain.model.ProgressUnit
import io.morgan.mio.domain.progress.ProgressAction
import io.morgan.mio.domain.progress.ProgressDirection
import io.morgan.mio.domain.progress.ProgressRules
import io.morgan.mio.domain.queue.CaughtUpSummary
import io.morgan.mio.domain.queue.NewRecommendation
import io.morgan.mio.domain.queue.NextReleaseRecommendation
import io.morgan.mio.domain.queue.NowRecommendation
import io.morgan.mio.domain.queue.TodayQueue
import io.morgan.mio.domain.sync.SyncAccountIssueKind
import io.morgan.mio.domain.sync.SyncIssue
import io.morgan.mio.domain.sync.SyncRecoveryState
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import io.morgan.mio.domain.sync.SyncIssueKind as DomainSyncIssueKind

internal object TodayUiMapper {
    fun map(queue: TodayQueue): TodayUiState = map(queue, emptyList(), null, null)

    fun map(
        queue: TodayQueue,
        collection: List<CollectionEntry>,
        latestUndo: ProgressAction?,
        progressError: TodayProgressErrorUiModel?,
        syncIssues: List<TodayUiState.SyncIssueUiModel> = emptyList(),
        accountIssue: TodayUiState.AccountIssueUiModel? = null,
    ): TodayUiState {
        val entriesByMediaId = collection.associateBy { entry -> entry.mediaId }
        val undo = latestUndo?.toUndoUiModel()
        return queue.now?.let { recommendation ->
            TodayUiState.Populated(
                now = recommendation.toUiModel(entriesByMediaId[recommendation.mediaId], undo),
                newRecommendations =
                    queue.newRecommendations.map { item ->
                        item.toUiModel(entriesByMediaId[item.mediaId], undo)
                    },
                remainingReadyCount = queue.remainingReadyCount,
                latestUndo = undo,
                progressError = progressError,
                syncIssues = syncIssues,
                accountIssue = accountIssue,
            )
        } ?: TodayUiState.CaughtUp(
            receipt = requireNotNull(queue.caughtUp).toUiModel(),
            isCollectionEmpty = collection.isEmpty(),
            latestUndo = undo,
            progressError = progressError,
            syncIssues = syncIssues,
            accountIssue = accountIssue,
        )
    }

    internal fun mapRecovery(
        recovery: SyncRecoveryState,
        titleByEntryId: Map<String, String?>,
        mediaIdByEntryId: Map<String, String>,
    ): TodayRecoveryUiModel =
        TodayRecoveryUiModel(
            syncIssues =
                recovery.entryIssues.map { issue ->
                    issue.toUiModel(
                        title = titleByEntryId[issue.entryId.value],
                        mediaId = mediaIdByEntryId[issue.entryId.value],
                    )
                },
            accountIssue =
                recovery.accountIssue?.let { issue ->
                    TodayUiState.AccountIssueUiModel(issue.kind.toUiKind())
                },
        )

    private fun NowRecommendation.toUiModel(
        entry: CollectionEntry?,
        undo: TodayUndoUiModel?,
    ): TodayNowUiModel {
        val effectiveProgress = entry?.progress ?: progress
        val effectiveProgressUnit = entry?.progressUnit ?: progressUnit
        val effectiveTotal = entry?.total ?: total
        return TodayNowUiModel(
            mediaId = mediaId.value,
            mediaType = mediaType.toUiType(),
            title = title.value,
            posterReference = poster?.value,
            bannerReference = banner?.value,
            progress = effectiveProgress.value,
            progressUnit = effectiveProgressUnit.toUiUnit(),
            total = effectiveTotal?.value,
            nextUnit = nextUnit.value.takeIf { entry == null || entry.status == CollectionStatus.CURRENT },
            availableUnitCount =
                availableUnitCount.takeIf {
                    entry == null || entry.status == CollectionStatus.CURRENT
                },
            status = entry?.status.toTodayStatus(),
            controls = entry?.progressControls(),
            undo = undo?.takeIf { action -> action.mediaId == mediaId.value },
        )
    }

    private fun NewRecommendation.toUiModel(
        entry: CollectionEntry?,
        undo: TodayUndoUiModel?,
    ): TodayNewUiModel {
        val effectiveProgress = entry?.progress ?: progress
        val effectiveProgressUnit = entry?.progressUnit ?: progressUnit
        val effectiveTotal = entry?.total ?: total
        return TodayNewUiModel(
            mediaId = mediaId.value,
            mediaType = mediaType.toUiType(),
            title = title.value,
            posterReference = poster?.value,
            progress = effectiveProgress.value,
            progressUnit = effectiveProgressUnit.toUiUnit(),
            total = effectiveTotal?.value,
            nextUnit = nextUnit.value.takeIf { entry == null || entry.status == CollectionStatus.CURRENT },
            availableUnitCount =
                availableUnitCount.takeIf {
                    entry == null || entry.status == CollectionStatus.CURRENT
                },
            releaseAge =
                releaseAge
                    .toUiTime(isFuture = false)
                    .takeIf { entry == null || entry.status == CollectionStatus.CURRENT },
            status = entry?.status.toTodayStatus(),
            controls = entry?.progressControls(),
            undo = undo?.takeIf { action -> action.mediaId == mediaId.value },
        )
    }

    private fun CollectionStatus?.toTodayStatus(): TodayCollectionStatus =
        when (this) {
            null,
            CollectionStatus.CURRENT,
            -> TodayCollectionStatus.CURRENT
            CollectionStatus.PLANNED -> TodayCollectionStatus.PLANNED
            CollectionStatus.PAUSED -> TodayCollectionStatus.PAUSED
            CollectionStatus.COMPLETED -> TodayCollectionStatus.COMPLETED
            CollectionStatus.DROPPED -> TodayCollectionStatus.DROPPED
            CollectionStatus.REPEATING -> TodayCollectionStatus.REPEATING
        }

    private fun CollectionEntry.progressControls(): TodayProgressControlsUiModel? =
        if (status != CollectionStatus.CURRENT) {
            null
        } else {
            TodayProgressControlsUiModel(
                decreaseEnabled =
                    ProgressRules.decide(this, ProgressDirection.DECREASE) is ProgressRules.Decision.Change,
                increaseEnabled =
                    ProgressRules.decide(this, ProgressDirection.INCREASE) is ProgressRules.Decision.Change,
            )
        }

    private fun ProgressAction.toUndoUiModel(): TodayUndoUiModel =
        TodayUndoUiModel(
            groupId = id.value,
            mediaId = mediaId.value,
            progress = afterProgress.value,
            unit = unit.toUiUnit(),
            completed = afterStatus == CollectionStatus.COMPLETED,
        )

    private fun CaughtUpSummary.toUiModel(): TodayCaughtUpUiModel =
        TodayCaughtUpUiModel(
            artwork =
                artwork.map { item ->
                    TodayCaughtUpArtworkUiModel(
                        mediaId = item.mediaId.value,
                        title = item.title.value,
                        reference = item.reference?.value,
                    )
                },
            nextRelease = nextRelease?.toUiModel(),
        )

    private fun NextReleaseRecommendation.toUiModel(): TodayNextReleaseUiModel =
        TodayNextReleaseUiModel(
            mediaId = mediaId.value,
            mediaType = mediaType.toUiType(),
            title = title.value,
            posterReference = poster?.value,
            progressUnit = progressUnit.toUiUnit(),
            unitNumber = unitNumber.value,
            waitUntilRelease = waitUntilRelease.toUiTime(isFuture = true),
        )

    private fun Duration.toUiTime(isFuture: Boolean): TodayRelativeTime {
        require(isFinite())
        if (isFuture) {
            require(this > Duration.ZERO)
        } else {
            require(this >= Duration.ZERO)
        }
        return when {
            this < 1.hours -> TodayRelativeTime(0, TodayRelativeTimeUnit.UNDER_HOUR)
            this < 24.hours -> TodayRelativeTime(inWholeHours.toInt(), TodayRelativeTimeUnit.HOURS)
            else ->
                TodayRelativeTime(
                    amount = inWholeDays.coerceAtLeast(1).coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
                    unit = TodayRelativeTimeUnit.DAYS,
                )
        }
    }

    private fun MediaType.toUiType(): TodayMediaType =
        when (this) {
            MediaType.ANIME -> TodayMediaType.ANIME
            MediaType.MANGA -> TodayMediaType.MANGA
        }

    private fun ProgressUnit.toUiUnit(): TodayProgressUnit =
        when (this) {
            ProgressUnit.EPISODE -> TodayProgressUnit.EPISODE
            ProgressUnit.CHAPTER -> TodayProgressUnit.CHAPTER
            ProgressUnit.VOLUME -> TodayProgressUnit.VOLUME
        }

    private fun SyncIssue.toUiModel(
        title: String?,
        mediaId: String?,
    ): TodayUiState.SyncIssueUiModel =
        TodayUiState.SyncIssueUiModel(
            operationId = operationId.value,
            entryId = entryId.value,
            mediaId = mediaId,
            title = title,
            kind = kind.toUiKind(),
            localProgress = local.progress.value,
            localUnit = local.unit.toUiUnit(),
            localStatus = local.status.toTodayStatus(),
            localTotal = local.total?.value,
            remoteProgress = remote?.progress?.value,
            remoteUnit = remote?.unit?.toUiUnit(),
            remoteStatus = remote?.status?.toTodayStatus(),
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
}

internal data class TodayRecoveryUiModel(
    val syncIssues: List<TodayUiState.SyncIssueUiModel>,
    val accountIssue: TodayUiState.AccountIssueUiModel?,
)

private fun io.morgan.mio.domain.collection.CollectionStatus.toTodayStatus(): TodayCollectionStatus =
    when (this) {
        CollectionStatus.CURRENT -> TodayCollectionStatus.CURRENT
        CollectionStatus.PLANNED -> TodayCollectionStatus.PLANNED
        CollectionStatus.PAUSED -> TodayCollectionStatus.PAUSED
        CollectionStatus.COMPLETED -> TodayCollectionStatus.COMPLETED
        CollectionStatus.DROPPED -> TodayCollectionStatus.DROPPED
        CollectionStatus.REPEATING -> TodayCollectionStatus.REPEATING
    }
