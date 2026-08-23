package io.morgan.mio.data.media.mapper

internal class MediaDetailsPersistenceSnapshot(
    val owner: MediaPersistenceSnapshot,
    related: List<MediaPersistenceSnapshot>,
) {
    val related: List<MediaPersistenceSnapshot> = related.toList()

    init {
        require(owner.releases.all { release -> release.mediaId == owner.media.id })
        require(owner.providerIds.all { mapping -> mapping.mediaId == owner.media.id })
        require(related.map { snapshot -> snapshot.media.id }.distinct().size == related.size)
        require(owner.media.relatedMediaIds == related.map { snapshot -> snapshot.media.id })
        require(related.none { snapshot -> snapshot.media.id == owner.media.id })
        related.forEach { snapshot ->
            require(snapshot.releases.isEmpty())
            require(snapshot.providerIds.all { mapping -> mapping.mediaId == snapshot.media.id })
        }
    }
}
