package io.morgan.mio.domain.preferences

import io.morgan.mio.domain.model.AccountId
import kotlinx.coroutines.flow.Flow

public interface PreferencesRepository {
    public fun observeDevicePreferences(): Flow<DevicePreferences>

    public fun observeAccountPreferences(accountId: AccountId): Flow<AccountPreferences>

    public suspend fun setTitleLanguage(preference: TitleLanguagePreference)

    public suspend fun setAppearance(preference: AppearancePreference)

    public suspend fun setMotion(preference: MotionPreference)

    public suspend fun setHapticsEnabled(enabled: Boolean)

    public suspend fun setLibraryMediaFilter(
        accountId: AccountId,
        filter: LibraryMediaFilter,
    )

    public suspend fun setLibraryStatusFilter(
        accountId: AccountId,
        filter: LibraryStatusFilter,
    )
}
