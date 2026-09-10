package io.morgan.mio.feature.today.impl

internal sealed interface TodayAction {
    data class Step(
        val mediaId: String,
        val direction: TodayProgressDirection,
        val timeoutMillis: Long,
    ) : TodayAction

    data class Undo(
        val groupId: String,
    ) : TodayAction

    data object RetryProgress : TodayAction

    data class InteractionChanged(
        val mediaId: String,
        val pressed: Boolean,
    ) : TodayAction

    data class VisibilityChanged(
        val visible: Boolean,
    ) : TodayAction

    data object DismissError : TodayAction
}

internal enum class TodayProgressDirection {
    INCREASE,
    DECREASE,
}
