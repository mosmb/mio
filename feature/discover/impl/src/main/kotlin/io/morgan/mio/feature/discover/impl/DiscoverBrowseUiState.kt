package io.morgan.mio.feature.discover.impl

import io.morgan.mio.domain.media.MediaBrowseCategory
import io.morgan.mio.domain.model.MediaType

internal data class DiscoverBrowseUiState(
    val type: MediaType = MediaType.ANIME,
    val season: DiscoverBrowseSeason,
    val categories: List<MediaBrowseCategory> = emptyList(),
    val items: Map<MediaBrowseCategory, List<DiscoverResultUiModel>> = emptyMap(),
    val loading: Set<MediaBrowseCategory> = emptySet(),
    val failed: Set<MediaBrowseCategory> = emptySet(),
)
