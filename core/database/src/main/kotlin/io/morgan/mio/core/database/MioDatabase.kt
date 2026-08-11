package io.morgan.mio.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.withTransaction
import io.morgan.mio.core.database.dao.CollectionEntryDao
import io.morgan.mio.core.database.dao.MediaDao
import io.morgan.mio.core.database.dao.ProviderIdDao
import io.morgan.mio.core.database.dao.ReleaseDao
import io.morgan.mio.core.database.dao.SelectedAccountDao
import io.morgan.mio.core.database.entity.AccountProviderIdEntity
import io.morgan.mio.core.database.entity.CollectionEntryEntity
import io.morgan.mio.core.database.entity.CollectionEntryProviderIdEntity
import io.morgan.mio.core.database.entity.MediaEntity
import io.morgan.mio.core.database.entity.MediaGenreEntity
import io.morgan.mio.core.database.entity.MediaProviderIdEntity
import io.morgan.mio.core.database.entity.RelatedMediaEntity
import io.morgan.mio.core.database.entity.ReleaseEntity
import io.morgan.mio.core.database.entity.SelectedAccountEntity
import io.morgan.mio.core.database.model.MioTypeConverters

@Database(
    entities = [
        SelectedAccountEntity::class,
        AccountProviderIdEntity::class,
        MediaEntity::class,
        MediaGenreEntity::class,
        RelatedMediaEntity::class,
        ReleaseEntity::class,
        MediaProviderIdEntity::class,
        CollectionEntryEntity::class,
        CollectionEntryProviderIdEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(MioTypeConverters::class)
public abstract class MioDatabase : RoomDatabase() {
    public abstract fun selectedAccountDao(): SelectedAccountDao

    public abstract fun mediaDao(): MediaDao

    public abstract fun releaseDao(): ReleaseDao

    public abstract fun collectionEntryDao(): CollectionEntryDao

    public abstract fun providerIdDao(): ProviderIdDao

    public suspend fun <T> inTransaction(block: suspend () -> T): T = withTransaction(block)

    public companion object {
        public const val VERSION: Int = 1
    }
}
