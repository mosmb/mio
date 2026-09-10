package io.morgan.mio.feature.media.impl

internal sealed interface MediaDetailAction {
    data object Retry : MediaDetailAction

    data class Step(
        val mediaId: String,
        val direction: MediaDetailProgressDirection,
        val timeoutMillis: Long,
    ) : MediaDetailAction

    data class Undo(
        val groupId: String,
    ) : MediaDetailAction

    data object RetryProgress : MediaDetailAction

    data class InteractionChanged(
        val mediaId: String,
        val pressed: Boolean,
    ) : MediaDetailAction

    data class VisibilityChanged(
        val visible: Boolean,
    ) : MediaDetailAction

    data object DismissError : MediaDetailAction
}

internal enum class MediaDetailProgressDirection {
    INCREASE,
    DECREASE,
}
