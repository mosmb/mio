package io.morgan.mio.data.preferences

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import io.morgan.mio.core.datastore.PreferencesKeys
import io.morgan.mio.data.preferences.local.DataStorePreferencesLocalDataSource
import io.morgan.mio.domain.model.AccountId
import io.morgan.mio.domain.preferences.AccountPreferences
import io.morgan.mio.domain.preferences.AppearancePreference
import io.morgan.mio.domain.preferences.DevicePreferences
import io.morgan.mio.domain.preferences.LibraryMediaFilter
import io.morgan.mio.domain.preferences.LibraryStatusFilter
import io.morgan.mio.domain.preferences.MotionPreference
import io.morgan.mio.domain.preferences.TitleLanguagePreference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DefaultPreferencesRepositoryTest {
    @get:Rule
    val temporaryFolder: TemporaryFolder = TemporaryFolder()

    @Test
    fun titleLanguageSurvivesReconstructionAndAccountCleanupWithUnknownValueFallback() =
        runTest {
            val store = createDataStore(backgroundScope)
            val local = DataStorePreferencesLocalDataSource(store)
            val repository = DefaultPreferencesRepository(local)
            repository.setTitleLanguage(TitleLanguagePreference.JAPANESE)
            local.clearAccountPreferences(AccountId("account"))
            assertEquals(
                TitleLanguagePreference.JAPANESE,
                DefaultPreferencesRepository(
                    DataStorePreferencesLocalDataSource(store),
                ).observeDevicePreferences().first().titleLanguage,
            )
            store.edit { it[PreferencesKeys.deviceTitleLanguage] = "future-value" }
            assertEquals(
                TitleLanguagePreference.SYSTEM,
                repository.observeDevicePreferences().first().titleLanguage,
            )
        }

    @Test
    fun defaultsAndDeviceAndAccountScopesRemainIndependent() =
        runTest {
            val dataStore = createDataStore(backgroundScope)
            val local = DataStorePreferencesLocalDataSource(dataStore)
            val repository = DefaultPreferencesRepository(local)
            val accountOne = AccountId("account-1")
            val accountTwo = AccountId("account-2")

            assertEquals(DevicePreferences.Default, repository.observeDevicePreferences().first())
            assertEquals(
                AccountPreferences.default(accountOne),
                repository.observeAccountPreferences(accountOne).first(),
            )
            assertEquals(
                AccountPreferences.default(accountTwo),
                repository.observeAccountPreferences(accountTwo).first(),
            )

            repository.setAppearance(AppearancePreference.DARK)
            repository.setMotion(MotionPreference.REDUCED)
            repository.setHapticsEnabled(false)
            repository.setLibraryMediaFilter(accountOne, LibraryMediaFilter.MANGA)
            repository.setLibraryStatusFilter(accountOne, LibraryStatusFilter.COMPLETED)

            assertEquals(
                DevicePreferences(AppearancePreference.DARK, MotionPreference.REDUCED, false),
                repository.observeDevicePreferences().first(),
            )
            assertEquals(
                AccountPreferences(accountOne, LibraryMediaFilter.MANGA, LibraryStatusFilter.COMPLETED),
                repository.observeAccountPreferences(accountOne).first(),
            )
            assertEquals(
                AccountPreferences.default(accountTwo),
                repository.observeAccountPreferences(accountTwo).first(),
            )
        }

    @Test
    fun repositoryAndLocalDataSourceReconstructionReadsTheSameFile() =
        runTest {
            val dataStore = createDataStore(backgroundScope)
            val firstRepository =
                DefaultPreferencesRepository(DataStorePreferencesLocalDataSource(dataStore))
            val accountId = AccountId("account-1")

            firstRepository.setLibraryMediaFilter(accountId, LibraryMediaFilter.ANIME)
            firstRepository.setLibraryStatusFilter(accountId, LibraryStatusFilter.PAUSED)

            val reconstructedRepository =
                DefaultPreferencesRepository(DataStorePreferencesLocalDataSource(dataStore))
            assertEquals(
                AccountPreferences(accountId, LibraryMediaFilter.ANIME, LibraryStatusFilter.PAUSED),
                reconstructedRepository.observeAccountPreferences(accountId).first(),
            )
        }

    @Test
    fun unknownSerializedValuesFallBackWithoutChangingOtherKeys() =
        runTest {
            val dataStore = createDataStore(backgroundScope)
            dataStore.edit { preferences ->
                preferences[PreferencesKeys.deviceAppearance] = "old-value"
                preferences[PreferencesKeys.deviceMotion] = "future-value"
                preferences[PreferencesKeys.accountLibraryMediaFilter("account-1")] = "bad-value"
                preferences[PreferencesKeys.accountLibraryStatusFilter("account-1")] = "future-value"
            }
            val repository =
                DefaultPreferencesRepository(DataStorePreferencesLocalDataSource(dataStore))

            assertEquals(DevicePreferences.Default, repository.observeDevicePreferences().first())
            assertEquals(
                AccountPreferences.default(AccountId("account-1")),
                repository.observeAccountPreferences(AccountId("account-1")).first(),
            )
        }

    @Test
    fun accountOnlyClearingPreservesDevicePreferences() =
        runTest {
            val dataStore = createDataStore(backgroundScope)
            val local = DataStorePreferencesLocalDataSource(dataStore)
            val repository = DefaultPreferencesRepository(local)
            val accountId = AccountId("account-1")

            repository.setAppearance(AppearancePreference.LIGHT)
            repository.setLibraryMediaFilter(accountId, LibraryMediaFilter.MANGA)
            repository.setLibraryStatusFilter(accountId, LibraryStatusFilter.DROPPED)
            local.clearAccountPreferences(accountId)

            assertEquals(
                DevicePreferences(AppearancePreference.LIGHT, MotionPreference.FOLLOW_SYSTEM, true),
                repository.observeDevicePreferences().first(),
            )
            assertEquals(AccountPreferences.default(accountId), repository.observeAccountPreferences(accountId).first())
        }

    private fun createDataStore(scope: CoroutineScope) =
        PreferenceDataStoreFactory.create(
            scope = scope,
            produceFile = { temporaryFolder.root.resolve("test.preferences_pb") },
        )
}
