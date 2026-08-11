package io.morgan.mio.core.database.di

import android.content.Context
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import io.morgan.mio.core.common.di.AppScope
import io.morgan.mio.core.common.di.ApplicationContext
import io.morgan.mio.core.database.MioDatabase
import io.morgan.mio.core.database.MioDatabaseFactory

@BindingContainer
@ContributesTo(AppScope::class)
public object DatabaseBindings {
    @Provides
    @SingleIn(AppScope::class)
    public fun provideMioDatabase(
        @ApplicationContext context: Context,
    ): MioDatabase = MioDatabaseFactory.create(context)
}
