package io.morgan.mio.feature.progress.impl

internal sealed interface ProgressConflictAction {
    data class Select(
        val selection: ProgressConflictSelection,
    ) : ProgressConflictAction

    data object Confirm : ProgressConflictAction
}
