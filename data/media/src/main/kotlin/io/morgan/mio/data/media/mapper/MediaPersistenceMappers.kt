package io.morgan.mio.data.media.mapper

import io.morgan.mio.core.database.entity.MediaEntity
import io.morgan.mio.core.database.entity.MediaGenreEntity
import io.morgan.mio.core.database.entity.MediaProviderIdEntity
import io.morgan.mio.core.database.entity.RelatedMediaEntity
import io.morgan.mio.core.database.entity.ReleaseEntity
import io.morgan.mio.core.database.model.MediaAggregate
import io.morgan.mio.core.database.model.StoredConnectedProvider
import io.morgan.mio.core.database.model.StoredMediaFormat
import io.morgan.mio.core.database.model.StoredMediaPublicationStatus
import io.morgan.mio.core.database.model.StoredMediaType
import io.morgan.mio.core.database.model.StoredProgressUnit
import io.morgan.mio.core.database.model.StoredReleaseTimeKind
import io.morgan.mio.domain.media.Artwork
import io.morgan.mio.domain.media.Media
import io.morgan.mio.domain.media.MediaFormat
import io.morgan.mio.domain.media.MediaPublicationStatus
import io.morgan.mio.domain.media.MediaScore
import io.morgan.mio.domain.media.MediaTotals
import io.morgan.mio.domain.media.Release
import io.morgan.mio.domain.media.ReleaseTime
import io.morgan.mio.domain.model.ImageReference
import io.morgan.mio.domain.model.MediaId
import io.morgan.mio.domain.model.MediaTitle
import io.morgan.mio.domain.model.MediaType
import io.morgan.mio.domain.model.ProgressUnit
import io.morgan.mio.domain.model.UnitNumber
import io.morgan.mio.domain.model.UnitTotal

internal data class MediaProviderIdInput(
    val mediaId: MediaId,
    val provider: StoredConnectedProvider,
    val remoteId: String,
) {
    init {
        require(remoteId.isNotBlank())
    }
}

internal data class MediaPersistenceSnapshot(
    val media: Media,
    val releases: List<Release> = emptyList(),
    val providerIds: List<MediaProviderIdInput> = emptyList(),
)

internal fun Media.toEntity(): MediaEntity =
    MediaEntity(
        mediaId = id.value,
        mediaType = type.toStoredValue(),
        title = title.value,
        titleEnglish = title.english,
        titleJapanese = title.japanese,
        titleRomaji = title.romaji,
        format = format?.toStoredValue(),
        publicationStatus = publicationStatus.toStoredValue(),
        synopsis = synopsis,
        posterReference = artwork.poster?.value,
        bannerReference = artwork.banner?.value,
        episodeTotal = totals.episodes?.value,
        chapterTotal = totals.chapters?.value,
        volumeTotal = totals.volumes?.value,
        releaseYear = releaseYear,
        scorePercent = score?.percent,
    )

internal fun Media.toGenreEntities(): List<MediaGenreEntity> =
    genres.mapIndexed { position, genre ->
        require(genre.isNotBlank())
        MediaGenreEntity(mediaId = id.value, genre = genre, position = position)
    }

internal fun Media.toRelatedMediaEntities(): List<RelatedMediaEntity> =
    relatedMediaIds.mapIndexed { position, relatedMediaId ->
        require(relatedMediaId != id)
        RelatedMediaEntity(
            mediaId = id.value,
            relatedMediaId = relatedMediaId.value,
            position = position,
        )
    }

internal fun Release.toEntity(): ReleaseEntity =
    ReleaseEntity(
        mediaId = mediaId.value,
        progressUnit = unit.toStoredValue(),
        unitNumber = number.value,
        timeKind = time.toStoredValue(),
        releaseInstantUtcMs = time.instantOrNull(),
    ).also {
        require(number.value > 0)
        when (time) {
            ReleaseTime.Unknown -> require(it.releaseInstantUtcMs == null)
            is ReleaseTime.Trusted, is ReleaseTime.Untrusted -> requireNotNull(it.releaseInstantUtcMs)
        }
    }

internal fun MediaAggregate.toDomain(): Media {
    val orderedGenres = genres.sortedBy(MediaGenreEntity::position).map(MediaGenreEntity::genre)
    val orderedRelatedMediaIds =
        relatedMedia
            .sortedBy(RelatedMediaEntity::position)
            .map { related -> MediaId(related.relatedMediaId) }
    return Media(
        id = MediaId(media.mediaId),
        type = media.mediaType.toDomain(),
        title = MediaTitle(media.title, media.titleEnglish, media.titleJapanese, media.titleRomaji),
        format = media.format?.toDomain(),
        publicationStatus = media.publicationStatus.toDomain(),
        synopsis = media.synopsis,
        artwork =
            Artwork(
                poster = media.posterReference?.let(::ImageReference),
                banner = media.bannerReference?.let(::ImageReference),
            ),
        totals =
            MediaTotals(
                episodes = media.episodeTotal?.let(::UnitTotal),
                chapters = media.chapterTotal?.let(::UnitTotal),
                volumes = media.volumeTotal?.let(::UnitTotal),
            ),
        genres = orderedGenres.toList(),
        relatedMediaIds = orderedRelatedMediaIds.toList(),
        releaseYear = media.releaseYear,
        score = media.scorePercent?.let(::MediaScore),
    )
}

internal fun ReleaseEntity.toDomain(): Release =
    Release(
        mediaId = MediaId(mediaId),
        unit = progressUnit.toDomain(),
        number = UnitNumber(unitNumber),
        time = timeKind.toDomain(releaseInstantUtcMs),
    )

private fun MediaType.toStoredValue(): StoredMediaType =
    when (this) {
        MediaType.ANIME -> StoredMediaType.ANIME
        MediaType.MANGA -> StoredMediaType.MANGA
    }

private fun StoredMediaType.toDomain(): MediaType =
    when (this) {
        StoredMediaType.ANIME -> MediaType.ANIME
        StoredMediaType.MANGA -> MediaType.MANGA
    }

private fun MediaFormat.toStoredValue(): StoredMediaFormat =
    when (this) {
        MediaFormat.TV -> StoredMediaFormat.TV
        MediaFormat.MOVIE -> StoredMediaFormat.MOVIE
        MediaFormat.OVA -> StoredMediaFormat.OVA
        MediaFormat.ONA -> StoredMediaFormat.ONA
        MediaFormat.SPECIAL -> StoredMediaFormat.SPECIAL
        MediaFormat.MUSIC -> StoredMediaFormat.MUSIC
        MediaFormat.MANGA -> StoredMediaFormat.MANGA
        MediaFormat.NOVEL -> StoredMediaFormat.NOVEL
        MediaFormat.ONE_SHOT -> StoredMediaFormat.ONE_SHOT
        MediaFormat.OTHER -> StoredMediaFormat.OTHER
    }

private fun StoredMediaFormat.toDomain(): MediaFormat =
    when (this) {
        StoredMediaFormat.TV -> MediaFormat.TV
        StoredMediaFormat.MOVIE -> MediaFormat.MOVIE
        StoredMediaFormat.OVA -> MediaFormat.OVA
        StoredMediaFormat.ONA -> MediaFormat.ONA
        StoredMediaFormat.SPECIAL -> MediaFormat.SPECIAL
        StoredMediaFormat.MUSIC -> MediaFormat.MUSIC
        StoredMediaFormat.MANGA -> MediaFormat.MANGA
        StoredMediaFormat.NOVEL -> MediaFormat.NOVEL
        StoredMediaFormat.ONE_SHOT -> MediaFormat.ONE_SHOT
        StoredMediaFormat.OTHER -> MediaFormat.OTHER
    }

private fun MediaPublicationStatus.toStoredValue(): StoredMediaPublicationStatus =
    when (this) {
        MediaPublicationStatus.UPCOMING -> StoredMediaPublicationStatus.UPCOMING
        MediaPublicationStatus.RELEASING -> StoredMediaPublicationStatus.RELEASING
        MediaPublicationStatus.FINISHED -> StoredMediaPublicationStatus.FINISHED
        MediaPublicationStatus.CANCELLED -> StoredMediaPublicationStatus.CANCELLED
        MediaPublicationStatus.HIATUS -> StoredMediaPublicationStatus.HIATUS
        MediaPublicationStatus.UNKNOWN -> StoredMediaPublicationStatus.UNKNOWN
    }

private fun StoredMediaPublicationStatus.toDomain(): MediaPublicationStatus =
    when (this) {
        StoredMediaPublicationStatus.UPCOMING -> MediaPublicationStatus.UPCOMING
        StoredMediaPublicationStatus.RELEASING -> MediaPublicationStatus.RELEASING
        StoredMediaPublicationStatus.FINISHED -> MediaPublicationStatus.FINISHED
        StoredMediaPublicationStatus.CANCELLED -> MediaPublicationStatus.CANCELLED
        StoredMediaPublicationStatus.HIATUS -> MediaPublicationStatus.HIATUS
        StoredMediaPublicationStatus.UNKNOWN -> MediaPublicationStatus.UNKNOWN
    }

private fun ProgressUnit.toStoredValue(): StoredProgressUnit =
    when (this) {
        ProgressUnit.EPISODE -> StoredProgressUnit.EPISODE
        ProgressUnit.CHAPTER -> StoredProgressUnit.CHAPTER
        ProgressUnit.VOLUME -> StoredProgressUnit.VOLUME
    }

private fun StoredProgressUnit.toDomain(): ProgressUnit =
    when (this) {
        StoredProgressUnit.EPISODE -> ProgressUnit.EPISODE
        StoredProgressUnit.CHAPTER -> ProgressUnit.CHAPTER
        StoredProgressUnit.VOLUME -> ProgressUnit.VOLUME
    }

private fun ReleaseTime.toStoredValue(): StoredReleaseTimeKind =
    when (this) {
        ReleaseTime.Unknown -> StoredReleaseTimeKind.UNKNOWN
        is ReleaseTime.Trusted -> StoredReleaseTimeKind.TRUSTED
        is ReleaseTime.Untrusted -> StoredReleaseTimeKind.UNTRUSTED
    }

private fun ReleaseTime.instantOrNull() =
    when (this) {
        ReleaseTime.Unknown -> null
        is ReleaseTime.Trusted -> instant
        is ReleaseTime.Untrusted -> instant
    }

private fun StoredReleaseTimeKind.toDomain(instant: kotlin.time.Instant?): ReleaseTime =
    when (this) {
        StoredReleaseTimeKind.UNKNOWN -> {
            require(instant == null)
            ReleaseTime.Unknown
        }

        StoredReleaseTimeKind.TRUSTED -> ReleaseTime.Trusted(requireNotNull(instant))
        StoredReleaseTimeKind.UNTRUSTED -> ReleaseTime.Untrusted(requireNotNull(instant))
    }

internal fun MediaProviderIdInput.toEntity(): MediaProviderIdEntity =
    MediaProviderIdEntity(
        provider = provider,
        remoteMediaId = remoteId,
        mediaId = mediaId.value,
    )
