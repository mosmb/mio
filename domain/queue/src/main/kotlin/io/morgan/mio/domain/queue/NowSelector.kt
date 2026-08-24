package io.morgan.mio.domain.queue

internal object NowSelector {
    fun select(readyTitles: List<ReadyTitle>): NowRecommendation? =
        readyTitles
            .sortedWith(
                compareByDescending<ReadyTitle> { ready -> ready.candidate.entry.updatedAt }
                    .thenBy { ready -> ready.candidate.media.id.value }
                    .thenBy { ready -> ready.candidate.entry.id.value },
            ).firstOrNull()
            ?.let { ready ->
                val candidate = ready.candidate
                NowRecommendation(
                    mediaId = candidate.media.id,
                    mediaType = candidate.media.type,
                    title = candidate.media.title,
                    poster = candidate.media.artwork.poster,
                    banner = candidate.media.artwork.banner,
                    progress = candidate.entry.progress,
                    progressUnit = candidate.entry.progressUnit,
                    total = candidate.entry.total,
                    nextUnit = ready.nextUnit,
                    availableUnitCount = ready.availableUnitCount,
                )
            }
}
