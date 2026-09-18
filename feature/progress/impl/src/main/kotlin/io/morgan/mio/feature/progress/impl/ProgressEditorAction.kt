package io.morgan.mio.feature.progress.impl

internal sealed interface ProgressEditorAction {
    data class TargetChanged(
        val value: String,
    ) : ProgressEditorAction

    data object Save : ProgressEditorAction
}
