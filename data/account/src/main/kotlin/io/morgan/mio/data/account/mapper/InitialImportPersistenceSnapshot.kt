package io.morgan.mio.data.account.mapper

import io.morgan.mio.core.database.entity.AccountProviderIdEntity
import io.morgan.mio.core.database.entity.CollectionEntryEntity
import io.morgan.mio.core.database.entity.CollectionEntryProviderIdEntity
import io.morgan.mio.core.database.entity.MediaEntity
import io.morgan.mio.core.database.entity.MediaGenreEntity
import io.morgan.mio.core.database.entity.MediaProviderIdEntity
import io.morgan.mio.core.database.entity.RelatedMediaEntity
import io.morgan.mio.core.database.entity.ReleaseEntity
import io.morgan.mio.core.database.entity.SelectedAccountEntity
import io.morgan.mio.domain.model.AccountId

internal class InitialImportPersistenceSnapshot(
    val accountId: AccountId,
    val account: SelectedAccountEntity,
    val accountProviderId: AccountProviderIdEntity,
    media: List<MediaEntity>,
    genres: List<MediaGenreEntity>,
    relatedMedia: List<RelatedMediaEntity>,
    releases: List<ReleaseEntity>,
    mediaProviderIds: List<MediaProviderIdEntity>,
    entries: List<CollectionEntryEntity>,
    entryProviderIds: List<CollectionEntryProviderIdEntity>,
) {
    val media: List<MediaEntity> = media.toList()
    val genres: List<MediaGenreEntity> = genres.toList()
    val relatedMedia: List<RelatedMediaEntity> = relatedMedia.toList()
    val releases: List<ReleaseEntity> = releases.toList()
    val mediaProviderIds: List<MediaProviderIdEntity> = mediaProviderIds.toList()
    val entries: List<CollectionEntryEntity> = entries.toList()
    val entryProviderIds: List<CollectionEntryProviderIdEntity> = entryProviderIds.toList()
}
