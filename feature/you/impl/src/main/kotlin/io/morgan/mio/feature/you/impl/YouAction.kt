package io.morgan.mio.feature.you.impl

import io.morgan.mio.domain.model.MediaType
import io.morgan.mio.domain.preferences.AppearancePreference

internal sealed interface YouAction {
    data class SelectAppearance(
        val preference: AppearancePreference,
    ) : YouAction

    data class SelectStatisticsType(
        val mediaType: MediaType,
    ) : YouAction

    data class RetrySync(
        val operationId: String,
    ) : YouAction

    data object RetryScreen : YouAction

    data object DismissAppearanceError : YouAction

    data object DismissRetryError : YouAction
}
