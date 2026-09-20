package io.morgan.mio.feature.you.impl

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import io.morgan.mio.domain.model.MediaType
import io.morgan.mio.domain.preferences.AppearancePreference
import io.morgan.mio.domain.preferences.TitleLanguagePreference
import kotlinx.datetime.LocalDate

@Immutable
internal sealed interface YouUiState {
    data object Loading : YouUiState

    data object NoAccount : YouUiState

    data object Error : YouUiState

    data class Content(
        val identity: YouIdentityUiModel,
        val appearance: AppearancePreference,
        val syncIssues: List<YouSyncIssueUiModel>,
        val accountIssue: YouAccountIssueKind?,
        val pendingRetryIds: Set<String>,
        val retryFailed: Boolean,
        val statistics: YouCollectionStatisticsUiState,
        val activity: YouActivityUiState,
        val selectedStatisticsType: MediaType,
        val rotationDate: LocalDate,
        val appearanceSaveFailed: Boolean = false,
        val titleLanguage: TitleLanguagePreference = TitleLanguagePreference.SYSTEM,
    ) : YouUiState
}

@Immutable
internal data class YouIdentityUiModel(
    val displayName: String,
    val provider: YouProvider,
    val avatarReference: String?,
)

internal enum class YouProvider(
    @param:StringRes val labelRes: Int,
) {
    ANILIST(R.string.you_provider_anilist),
    MY_ANIME_LIST(R.string.you_provider_myanimelist),
}

@Immutable
internal data class YouSyncIssueUiModel(
    val operationId: String,
    val entryId: String,
    val kind: YouSyncIssueKind,
    val localProgress: Int,
    val localStatus: String,
    val localUnit: YouProgressUnit,
    val localTotal: Int?,
    val remoteProgress: Int?,
    val remoteStatus: String?,
    val remoteUnit: YouProgressUnit?,
    val canRetry: Boolean,
    val needsReconnect: Boolean,
    val needsResolve: Boolean,
)

internal enum class YouSyncIssueKind {
    AUTHENTICATION,
    REJECTED,
    CONFLICT,
    RETRY_EXHAUSTED,
    INVALID_REMOTE_DATA,
}

internal enum class YouProgressUnit {
    EPISODE,
    CHAPTER,
    VOLUME,
}

internal enum class YouAccountIssueKind {
    AUTHENTICATION,
    INVALID_REMOTE_DATA,
}
