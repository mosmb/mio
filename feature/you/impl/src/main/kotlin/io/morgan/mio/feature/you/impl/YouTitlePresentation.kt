package io.morgan.mio.feature.you.impl

import io.morgan.mio.core.ui.title.MediaTitleNames

internal fun YouUiState.withTitleLanguage(resolve: (String, MediaTitleNames) -> String): YouUiState =
    when (this) {
        is YouUiState.Content ->
            copy(
                statistics =
                    when (val stats = statistics) {
                        is YouCollectionStatisticsUiState.Content ->
                            stats.copy(
                                artworkCandidates =
                                    stats.artworkCandidates.map {
                                        it.copy(title = resolve(it.title, it.titleNames))
                                    },
                            )
                        else -> stats
                    },
            )
        else -> this
    }
