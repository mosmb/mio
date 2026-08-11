package io.morgan.mio.core.database

import android.content.Context
import androidx.annotation.VisibleForTesting
import androidx.room.Room

public object MioDatabaseFactory {
    public fun create(
        context: Context,
        name: String = "mio.db",
    ): MioDatabase =
        Room
            .databaseBuilder(context.applicationContext, MioDatabase::class.java, name)
            .addMigrations(*MioMigrations.ALL)
            .build()

    @VisibleForTesting
    public fun createInMemory(context: Context): MioDatabase =
        Room
            .inMemoryDatabaseBuilder(context.applicationContext, MioDatabase::class.java)
            .allowMainThreadQueries()
            .build()
}
