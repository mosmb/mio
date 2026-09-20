package io.morgan.mio.feature.library.impl

import io.morgan.mio.core.ui.title.MediaTitleNames

internal fun LibraryUiState.withTitleLanguage(resolve: (String, MediaTitleNames) -> String): LibraryUiState =
    copy(
        rows =
            rows.map {
                it.copy(title = resolve(it.title, it.titleNames))
            },
    )
