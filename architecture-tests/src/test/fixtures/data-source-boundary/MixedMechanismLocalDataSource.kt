package io.morgan.mio.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import io.morgan.mio.core.database.MioDatabase

class MixedMechanismLocalDataSource(
    private val dataStore: DataStore<Preferences>,
    private val database: MioDatabase,
)
