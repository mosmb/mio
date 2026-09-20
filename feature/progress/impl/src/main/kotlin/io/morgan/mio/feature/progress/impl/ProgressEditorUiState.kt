package io.morgan.mio.feature.progress.impl

import io.morgan.mio.core.ui.title.MediaTitleNames

internal sealed interface ProgressEditorUiState {
    data object Loading : ProgressEditorUiState

    data object Missing : ProgressEditorUiState

    data class Content(
        val title: String,
        val unit: ProgressEditorUnit,
        val currentProgress: Int,
        val total: Int?,
        val targetText: String,
        val canSave: Boolean,
        val saving: Boolean,
        val error: ProgressEditorError?,
        val titleNames: MediaTitleNames =
            io.morgan.mio.core.ui.title
                .MediaTitleNames(),
    ) : ProgressEditorUiState
}

internal enum class ProgressEditorUnit {
    EPISODE,
    CHAPTER,
    VOLUME,
}

internal enum class ProgressEditorError {
    INVALID_VALUE,
    ABOVE_TOTAL,
    UNCHANGED,
    INELIGIBLE,
    STALE,
    SAVE_FAILED,
}
