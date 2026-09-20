package io.morgan.mio.data.collection

import dev.zacsweers.metro.Inject
import io.morgan.mio.core.database.MioDatabase
import io.morgan.mio.core.database.entity.MediaGenreEntity
import io.morgan.mio.core.database.model.CollectionStatisticsAggregate
import io.morgan.mio.core.database.model.StoredCollectionStatus
import io.morgan.mio.core.database.model.StoredMediaType
import io.morgan.mio.core.database.model.StoredProgressUnit
import io.morgan.mio.domain.collection.CollectionStatisticsSnapshot
import io.morgan.mio.domain.model.AccountId
import io.morgan.mio.domain.model.ImageReference
import io.morgan.mio.domain.model.MediaId
import io.morgan.mio.domain.model.MediaTitle
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.Locale

@Inject
public class RoomCollectionStatisticsLocalDataSource(
    private val database: MioDatabase,
) {
    public fun observeStatistics(accountId: AccountId): Flow<CollectionStatisticsSnapshot> =
        database.collectionEntryDao().observeStatistics(accountId.value).map(::toStatistics)

    private fun toStatistics(aggregates: List<CollectionStatisticsAggregate>): CollectionStatisticsSnapshot {
        val anime = aggregates.filter { it.media.mediaType == StoredMediaType.ANIME }
        val manga = aggregates.filter { it.media.mediaType == StoredMediaType.MANGA }
        val eligibleArtwork =
            aggregates
                .asSequence()
                .filter {
                    it.entry.effectiveStatus == StoredCollectionStatus.CURRENT ||
                        it.entry.effectiveStatus == StoredCollectionStatus.COMPLETED
                }.sortedBy { it.entry.mediaId }
                .take(ARTWORK_CANDIDATE_LIMIT)
                .map { aggregate ->
                    CollectionStatisticsSnapshot.Artwork(
                        mediaId = MediaId(aggregate.media.mediaId),
                        title =
                            MediaTitle(
                                aggregate.media.title,
                                aggregate.media.titleEnglish,
                                aggregate.media.titleJapanese,
                                aggregate.media.titleRomaji,
                            ),
                        poster = aggregate.media.posterReference?.let(::ImageReference),
                    )
                }.toList()

        return CollectionStatisticsSnapshot(
            completedTitleCount = anime.completedCount() + manga.completedCount(),
            recordedEpisodes = anime.progressTotal(StoredProgressUnit.EPISODE),
            recordedChapters = manga.progressTotal(StoredProgressUnit.CHAPTER),
            anime = anime.typeStatistics(),
            manga = manga.typeStatistics(),
            artwork = eligibleArtwork,
        )
    }

    private fun List<CollectionStatisticsAggregate>.completedCount(): Long =
        count { it.entry.effectiveStatus == StoredCollectionStatus.COMPLETED }.toLong()

    private fun List<CollectionStatisticsAggregate>.progressTotal(
        unit: StoredProgressUnit,
    ): CollectionStatisticsSnapshot.RecordedProgressTotal {
        val included = filter { it.entry.progressUnit == unit }
        val total =
            when {
                included.isNotEmpty() -> included.sumOf { it.entry.effectiveProgress.toLong() }
                isEmpty() -> 0L
                else -> null
            }
        return CollectionStatisticsSnapshot.RecordedProgressTotal(
            value = total,
            trackedTitleCount = size.toLong(),
            includedTitleCount = included.size.toLong(),
        )
    }

    private fun List<CollectionStatisticsAggregate>.typeStatistics(): CollectionStatisticsSnapshot.TypeStatistics {
        val genreCounts = mutableMapOf<String, GenreAccumulator>()
        var titlesWithMetadata = 0L
        forEach { aggregate ->
            val distinctGenres =
                aggregate.genres
                    .asSequence()
                    .map(MediaGenreEntity::genre)
                    .map(String::trim)
                    .filter(String::isNotEmpty)
                    .toList()
                    .groupBy { name -> name.lowercase(Locale.ROOT) }
                    .values
                    .map { names ->
                        names.minWith(compareBy<String> { it.lowercase(Locale.ROOT) }.thenBy { it })
                    }.sortedWith(compareBy<String> { it.lowercase(Locale.ROOT) }.thenBy { it })
            if (distinctGenres.isNotEmpty()) titlesWithMetadata += 1L
            distinctGenres.forEach { name ->
                val key = name.lowercase(Locale.ROOT)
                val existing = genreCounts[key]
                if (existing == null) {
                    genreCounts[key] = GenreAccumulator(name, 1L)
                } else {
                    genreCounts[key] =
                        existing.copy(
                            name =
                                listOf(existing.name, name)
                                    .minWith(compareBy<String> { it.lowercase(Locale.ROOT) }.thenBy { it }),
                            titleCount = existing.titleCount + 1L,
                        )
                }
            }
        }
        val topGenres =
            genreCounts
                .map { (key, value) -> key to value }
                .sortedWith(
                    compareByDescending<Pair<String, GenreAccumulator>> { it.second.titleCount }.thenBy { it.first },
                ).take(MAX_GENRES)
                .map { (_, value) ->
                    CollectionStatisticsSnapshot.GenreCount(value.name, value.titleCount)
                }
        return CollectionStatisticsSnapshot.TypeStatistics(
            trackedTitleCount = size.toLong(),
            completedTitleCount = completedCount(),
            genreMetadataTitleCount = titlesWithMetadata,
            topGenres = topGenres,
        )
    }

    private data class GenreAccumulator(
        val name: String,
        val titleCount: Long,
    )

    private companion object {
        const val MAX_GENRES = 5
        const val ARTWORK_CANDIDATE_LIMIT = 24
    }
}
