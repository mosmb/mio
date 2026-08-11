package io.morgan.mio.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences

class ValidDataStoreLocalDataSource(
    private val dataStore: DataStore<Preferences>,
)
