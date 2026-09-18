package io.morgan.mio.domain.collection

public fun orderLibraryEntries(entries: List<CollectionEntry>): List<CollectionEntry> =
    entries.sortedWith(
        compareByDescending<CollectionEntry> { it.updatedAt }
            .thenBy { it.mediaId.value }
            .thenBy { it.id.value },
    )
