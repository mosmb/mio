package io.morgan.mio.domain.queue

import io.morgan.mio.domain.model.ImageReference
import io.morgan.mio.domain.model.MediaId
import io.morgan.mio.domain.model.MediaTitle

public class CaughtUpSummary(
    artwork: List<Artwork>,
    public val nextRelease: NextReleaseRecommendation?,
) {
    public val artwork: List<Artwork> =
        artwork
            .also { values -> require(values.distinctBy { value -> value.mediaId }.size == values.size) }
            .take(MAX_ARTWORK)
            .toList()

    init {
        require(this.artwork.distinctBy { value -> value.mediaId }.size == this.artwork.size)
    }

    override fun equals(other: Any?): Boolean =
        this === other ||
            (other is CaughtUpSummary && artwork == other.artwork && nextRelease == other.nextRelease)

    override fun hashCode(): Int = 31 * artwork.hashCode() + (nextRelease?.hashCode() ?: 0)

    override fun toString(): String = "CaughtUpSummary(artwork=$artwork, nextRelease=$nextRelease)"

    public data class Artwork(
        public val mediaId: MediaId,
        public val title: MediaTitle,
        public val reference: ImageReference?,
    )

    private companion object {
        const val MAX_ARTWORK: Int = 6
    }
}
