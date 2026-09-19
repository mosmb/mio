package io.morgan.mio.data.preferences.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import dev.zacsweers.metro.Inject
import io.morgan.mio.core.datastore.AccountPreferencesRecord
import io.morgan.mio.core.datastore.DevicePreferencesRecord
import io.morgan.mio.core.datastore.PreferencesKeys
import io.morgan.mio.domain.model.AccountId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Inject
public class DataStorePreferencesLocalDataSource(
    private val dataStore: DataStore<Preferences>,
) {
    public fun observeDevicePreferences(): Flow<DevicePreferencesRecord> =
        dataStore.data.map { preferences ->
            DevicePreferencesRecord(
                appearance = preferences[PreferencesKeys.deviceAppearance] ?: "system",
                motion = preferences[PreferencesKeys.deviceMotion] ?: "follow_system",
                hapticsEnabled = preferences[PreferencesKeys.deviceHapticsEnabled] ?: true,
            )
        }

    public fun observeAccountPreferences(accountId: AccountId): Flow<AccountPreferencesRecord> =
        dataStore.data.map { preferences ->
            AccountPreferencesRecord(
                accountId = accountId.value,
                libraryMediaFilter =
                    preferences[PreferencesKeys.accountLibraryMediaFilter(accountId.value)] ?: "all",
                libraryStatusFilter =
                    preferences[PreferencesKeys.accountLibraryStatusFilter(accountId.value)] ?: "current",
            )
        }

    public suspend fun setAppearance(value: String) {
        dataStore.edit { preferences -> preferences[PreferencesKeys.deviceAppearance] = value }
    }

    public suspend fun setMotion(value: String) {
        dataStore.edit { preferences -> preferences[PreferencesKeys.deviceMotion] = value }
    }

    public suspend fun setHapticsEnabled(value: Boolean) {
        dataStore.edit { preferences -> preferences[PreferencesKeys.deviceHapticsEnabled] = value }
    }

    public suspend fun setLibraryMediaFilter(
        accountId: AccountId,
        value: String,
    ) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.accountLibraryMediaFilter(accountId.value)] = value
        }
    }

    public suspend fun setLibraryStatusFilter(
        accountId: AccountId,
        value: String,
    ) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.accountLibraryStatusFilter(accountId.value)] = value
        }
    }

    public suspend fun clearAccountPreferences(accountId: AccountId) {
        dataStore.edit { preferences ->
            preferences.remove(PreferencesKeys.accountLibraryMediaFilter(accountId.value))
            preferences.remove(PreferencesKeys.accountLibraryStatusFilter(accountId.value))
        }
    }
}
