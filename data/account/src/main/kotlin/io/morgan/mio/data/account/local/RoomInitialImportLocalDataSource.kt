package io.morgan.mio.data.account.local

import dev.zacsweers.metro.Inject
import io.morgan.mio.core.database.MioDatabase
import io.morgan.mio.data.account.mapper.InitialImportPersistenceSnapshot

@Inject
public class RoomInitialImportLocalDataSource(
    private val database: MioDatabase,
) {
    internal suspend fun commit(snapshot: InitialImportPersistenceSnapshot) {
        database.inTransaction {
            database.selectedAccountDao().replaceSelectedAccount(snapshot.account, snapshot.accountProviderId)

            if (snapshot.media.isNotEmpty()) {
                database.mediaDao().upsertMedia(snapshot.media)
            }

            val mediaIds = snapshot.media.map { entity -> entity.mediaId }.distinct()
            mediaIds.forEach { mediaId ->
                database.mediaDao().deleteGenres(mediaId)
                database.mediaDao().deleteRelatedMedia(mediaId)
                database.releaseDao().deleteReleases(mediaId)
                database.providerIdDao().deleteMediaProviderIds(mediaId)
            }

            if (snapshot.genres.isNotEmpty()) {
                database.mediaDao().upsertGenres(snapshot.genres)
            }
            if (snapshot.relatedMedia.isNotEmpty()) {
                database.mediaDao().upsertRelatedMedia(snapshot.relatedMedia)
            }
            if (snapshot.releases.isNotEmpty()) {
                database.releaseDao().upsertReleases(snapshot.releases)
            }
            if (snapshot.mediaProviderIds.isNotEmpty()) {
                database.providerIdDao().upsertMediaProviderIds(snapshot.mediaProviderIds)
            }

            // A complete initial snapshot replaces the account's collection outright, so clearing by
            // account is equivalent to deleting the entries absent from the snapshot. It is also
            // bounded: `deleteEntriesNotIn` binds one SQL variable per entry and a collection larger
            // than the device's SQLite host-parameter limit would fail to prepare.
            database.collectionEntryDao().deleteAllForAccount(snapshot.accountId.value)
            if (snapshot.entries.isNotEmpty()) {
                database.collectionEntryDao().upsertEntries(snapshot.entries)
            }

            snapshot.entries.forEach { entry ->
                database.providerIdDao().deleteCollectionEntryProviderIds(entry.collectionEntryId)
            }
            if (snapshot.entryProviderIds.isNotEmpty()) {
                database.providerIdDao().upsertCollectionEntryProviderIds(snapshot.entryProviderIds)
            }
        }
    }
}
