package io.morgan.mio.data.preferences

import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.morgan.mio.core.common.di.AppScope
import io.morgan.mio.data.preferences.local.DataStorePreferencesLocalDataSource
import io.morgan.mio.data.preferences.mapper.toDomain
import io.morgan.mio.data.preferences.mapper.toStoredValue
import io.morgan.mio.domain.model.AccountId
import io.morgan.mio.domain.preferences.AccountPreferences
import io.morgan.mio.domain.preferences.AppearancePreference
import io.morgan.mio.domain.preferences.DevicePreferences
import io.morgan.mio.domain.preferences.LibraryMediaFilter
import io.morgan.mio.domain.preferences.LibraryStatusFilter
import io.morgan.mio.domain.preferences.MotionPreference
import io.morgan.mio.domain.preferences.PreferencesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
@Inject
public class DefaultPreferencesRepository(
    private val localDataSource: DataStorePreferencesLocalDataSource,
) : PreferencesRepository {
    override fun observeDevicePreferences(): Flow<DevicePreferences> =
        localDataSource.observeDevicePreferences().map { record -> record.toDomain() }

    override fun observeAccountPreferences(accountId: AccountId): Flow<AccountPreferences> =
        localDataSource.observeAccountPreferences(accountId).map { record -> record.toDomain() }

    override suspend fun setAppearance(preference: AppearancePreference) {
        localDataSource.setAppearance(preference.toStoredValue())
    }

    override suspend fun setMotion(preference: MotionPreference) {
        localDataSource.setMotion(preference.toStoredValue())
    }

    override suspend fun setHapticsEnabled(enabled: Boolean) {
        localDataSource.setHapticsEnabled(enabled)
    }

    override suspend fun setLibraryMediaFilter(
        accountId: AccountId,
        filter: LibraryMediaFilter,
    ) {
        localDataSource.setLibraryMediaFilter(accountId, filter.toStoredValue())
    }

    override suspend fun setLibraryStatusFilter(
        accountId: AccountId,
        filter: LibraryStatusFilter,
    ) {
        localDataSource.setLibraryStatusFilter(accountId, filter.toStoredValue())
    }
}
