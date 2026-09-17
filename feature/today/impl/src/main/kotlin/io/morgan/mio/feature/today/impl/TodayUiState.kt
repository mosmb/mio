package io.morgan.mio.feature.today.impl

internal sealed interface TodayUiState {
    data class Loading(
        val latestUndo: TodayUndoUiModel? = null,
        val progressError: TodayProgressErrorUiModel? = null,
        val syncIssues: List<SyncIssueUiModel> = emptyList(),
        val accountIssue: AccountIssueUiModel? = null,
    ) : TodayUiState

    data class Populated(
        val now: TodayNowUiModel,
        val newRecommendations: List<TodayNewUiModel>,
        val remainingReadyCount: Int,
        val latestUndo: TodayUndoUiModel? = null,
        val progressError: TodayProgressErrorUiModel? = null,
        val syncIssues: List<SyncIssueUiModel> = emptyList(),
        val accountIssue: AccountIssueUiModel? = null,
    ) : TodayUiState {
        init {
            require(remainingReadyCount >= 0)
            require(newRecommendations.size <= 3)
            require(
                newRecommendations.distinctBy { recommendation -> recommendation.mediaId }.size ==
                    newRecommendations.size,
            )
            require(newRecommendations.none { recommendation -> recommendation.mediaId == now.mediaId })
        }
    }

    data class CaughtUp(
        val receipt: TodayCaughtUpUiModel,
        val latestUndo: TodayUndoUiModel? = null,
        val progressError: TodayProgressErrorUiModel? = null,
        val syncIssues: List<SyncIssueUiModel> = emptyList(),
        val accountIssue: AccountIssueUiModel? = null,
    ) : TodayUiState

    data class SyncIssueUiModel(
        val operationId: String,
        val entryId: String,
        val mediaId: String?,
        val title: String?,
        val kind: SyncIssueKind,
        val localProgress: Int,
        val localUnit: TodayProgressUnit,
        val localStatus: TodayCollectionStatus,
        val localTotal: Int?,
        val remoteProgress: Int?,
        val remoteUnit: TodayProgressUnit?,
        val remoteStatus: TodayCollectionStatus?,
        val canRetry: Boolean,
    )

    data class AccountIssueUiModel(
        val kind: AccountIssueKind,
    )
}

internal data class TodayNowUiModel(
    val mediaId: String,
    val mediaType: TodayMediaType,
    val title: String,
    val posterReference: String?,
    val bannerReference: String?,
    val progress: Int,
    val progressUnit: TodayProgressUnit,
    val total: Int?,
    val nextUnit: Int?,
    val availableUnitCount: Int?,
    val status: TodayCollectionStatus = TodayCollectionStatus.CURRENT,
    val controls: TodayProgressControlsUiModel? = null,
    val undo: TodayUndoUiModel? = null,
)

internal data class TodayNewUiModel(
    val mediaId: String,
    val mediaType: TodayMediaType,
    val title: String,
    val posterReference: String?,
    val progress: Int,
    val progressUnit: TodayProgressUnit,
    val total: Int?,
    val nextUnit: Int?,
    val availableUnitCount: Int?,
    val releaseAge: TodayRelativeTime?,
    val status: TodayCollectionStatus = TodayCollectionStatus.CURRENT,
    val controls: TodayProgressControlsUiModel? = null,
    val undo: TodayUndoUiModel? = null,
)

internal data class TodayProgressControlsUiModel(
    val decreaseEnabled: Boolean,
    val increaseEnabled: Boolean,
)

internal data class TodayUndoUiModel(
    val groupId: String,
    val mediaId: String,
    val progress: Int,
    val unit: TodayProgressUnit,
    val completed: Boolean,
)

internal data class TodayProgressErrorUiModel(
    val retryable: Boolean,
)

internal enum class TodayCollectionStatus {
    CURRENT,
    PLANNED,
    PAUSED,
    COMPLETED,
    DROPPED,
    REPEATING,
}

internal data class TodayCaughtUpUiModel(
    val artwork: List<TodayCaughtUpArtworkUiModel>,
    val nextRelease: TodayNextReleaseUiModel?,
)

internal data class TodayCaughtUpArtworkUiModel(
    val mediaId: String,
    val title: String,
    val reference: String?,
)

internal data class TodayNextReleaseUiModel(
    val mediaId: String,
    val mediaType: TodayMediaType,
    val title: String,
    val posterReference: String?,
    val progressUnit: TodayProgressUnit,
    val unitNumber: Int,
    val waitUntilRelease: TodayRelativeTime,
)

internal data class TodayRelativeTime(
    val amount: Int,
    val unit: TodayRelativeTimeUnit,
)

internal enum class TodayRelativeTimeUnit {
    UNDER_HOUR,
    HOURS,
    DAYS,
}

internal enum class TodayMediaType {
    ANIME,
    MANGA,
}

internal enum class TodayProgressUnit {
    EPISODE,
    CHAPTER,
    VOLUME,
}

internal enum class SyncIssueKind {
    AUTHENTICATION,
    REJECTED,
    CONFLICT,
    RETRY_EXHAUSTED,
    INVALID_REMOTE_DATA,
}

internal enum class AccountIssueKind {
    AUTHENTICATION,
    INVALID_REMOTE_DATA,
}
