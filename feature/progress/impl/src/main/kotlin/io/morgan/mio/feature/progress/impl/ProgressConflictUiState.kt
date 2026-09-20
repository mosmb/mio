package io.morgan.mio.feature.progress.impl

import io.morgan.mio.core.ui.title.MediaTitleNames
import io.morgan.mio.domain.sync.SyncIssue

internal sealed interface ProgressConflictUiState {
    data object Loading : ProgressConflictUiState

    data object Unavailable : ProgressConflictUiState

    data class Content(
        val issue: SyncIssue,
        val title: String,
        val deviceProgress: Int,
        val providerProgress: Int,
        val unit: ProgressConflictUnit,
        val selected: ProgressConflictSelection?,
        val saving: Boolean,
        val error: ProgressConflictError?,
        val titleNames: MediaTitleNames =
            io.morgan.mio.core.ui.title
                .MediaTitleNames(),
    ) : ProgressConflictUiState {
        val canConfirm: Boolean get() = selected != null && !saving
    }
}

internal enum class ProgressConflictSelection {
    KEEP_DEVICE,
    USE_PROVIDER,
}

internal enum class ProgressConflictUnit {
    EPISODE,
    CHAPTER,
    VOLUME,
}

internal enum class ProgressConflictError {
    CHANGED,
    SAVE_FAILED,
}
