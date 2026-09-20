package io.morgan.mio.feature.today.impl

import io.morgan.mio.core.ui.title.MediaTitleNames

internal fun TodayUiState.withTitleLanguage(resolve: (String, MediaTitleNames) -> String): TodayUiState =
    when (this) {
        is TodayUiState.Populated ->
            copy(
                syncIssues =
                    syncIssues.map { issue ->
                        issue.copy(title = issue.title?.let { resolve(it, issue.titleNames) })
                    },
                now =
                    now.copy(
                        title = resolve(now.title, now.titleNames),
                    ),
                newRecommendations =
                    newRecommendations.map {
                        it.copy(title = resolve(it.title, it.titleNames))
                    },
            )
        is TodayUiState.CaughtUp ->
            copy(
                syncIssues =
                    syncIssues.map { issue ->
                        issue.copy(title = issue.title?.let { resolve(it, issue.titleNames) })
                    },
                receipt =
                    receipt.copy(
                        artwork =
                            receipt.artwork.map {
                                it.copy(title = resolve(it.title, it.titleNames))
                            },
                        nextRelease = receipt.nextRelease?.let { it.copy(title = resolve(it.title, it.titleNames)) },
                    ),
            )
        is TodayUiState.Loading ->
            copy(
                syncIssues =
                    syncIssues.map { issue ->
                        issue.copy(title = issue.title?.let { resolve(it, issue.titleNames) })
                    },
            )
    }
