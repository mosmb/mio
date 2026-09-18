package io.morgan.mio.domain.media

public data class MediaSearchResults(
    public val media: List<Media>,
    public val hasMore: Boolean,
)
