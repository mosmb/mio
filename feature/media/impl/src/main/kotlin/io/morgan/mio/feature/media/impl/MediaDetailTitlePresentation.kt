package io.morgan.mio.feature.media.impl

import io.morgan.mio.core.ui.title.MediaTitleNames

internal fun MediaDetailUiState.withTitleLanguage(resolve: (String, MediaTitleNames) -> String): MediaDetailUiState =
    when (this) {
        is MediaDetailUiState.Content ->
            copy(
                media =
                    media.copy(
                        title = resolve(media.title, media.titleNames),
                    ),
                relatedTitles =
                    relatedTitles.map {
                        it.copy(title = resolve(it.title, it.titleNames))
                    },
                syncIssues = syncIssues.map { it.copy(title = resolve(it.title, media.titleNames)) },
            )
        else -> this
    }
