package io.morgan.mio.feature.media.impl

internal sealed interface MediaDetailAction {
    data object Retry : MediaDetailAction
}
