package io.morgan.mio.domain.progress

import io.morgan.mio.domain.collection.CollectionEntry

public data class ProgressSnapshot(
    public val entry: CollectionEntry,
    public val revision: Long,
) {
    init {
        require(revision >= 0L)
    }
}
