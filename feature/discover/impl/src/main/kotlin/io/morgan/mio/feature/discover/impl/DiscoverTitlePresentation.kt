package io.morgan.mio.feature.discover.impl

import io.morgan.mio.core.ui.title.MediaTitleNames

internal fun DiscoverUiState.withTitleLanguage(resolve: (String, MediaTitleNames) -> String): DiscoverUiState =
    copy(
        results =
            results.map {
                it.copy(title = resolve(it.title, it.titleNames))
            },
    )
