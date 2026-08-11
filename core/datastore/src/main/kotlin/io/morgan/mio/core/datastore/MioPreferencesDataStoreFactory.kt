package io.morgan.mio.core.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore

private val Context.mioPreferencesDataStore: DataStore<Preferences> by preferencesDataStore(name = "mio")

public object MioPreferencesDataStoreFactory {
    public fun create(context: Context): DataStore<Preferences> = context.applicationContext.mioPreferencesDataStore
}
