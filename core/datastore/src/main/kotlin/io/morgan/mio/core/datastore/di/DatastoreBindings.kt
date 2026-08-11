package io.morgan.mio.core.datastore.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import io.morgan.mio.core.common.di.AppScope
import io.morgan.mio.core.common.di.ApplicationContext
import io.morgan.mio.core.datastore.MioPreferencesDataStoreFactory

@BindingContainer
@ContributesTo(AppScope::class)
public object DatastoreBindings {
    @Provides
    @SingleIn(AppScope::class)
    public fun providePreferencesDataStore(
        @ApplicationContext context: Context,
    ): DataStore<Preferences> = MioPreferencesDataStoreFactory.create(context)
}
