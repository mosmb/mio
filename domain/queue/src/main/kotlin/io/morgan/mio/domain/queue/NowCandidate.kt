package io.morgan.mio.domain.queue

import io.morgan.mio.domain.collection.CollectionEntry
import io.morgan.mio.domain.media.Media
import io.morgan.mio.domain.media.Release

internal class NowCandidate(
    val entry: CollectionEntry,
    val media: Media,
    releases: List<Release>,
) {
    val releases: List<Release> = releases.toList()

    init {
        require(entry.mediaId == media.id)
        require(this.releases.all { release -> release.mediaId == media.id })
    }
}
