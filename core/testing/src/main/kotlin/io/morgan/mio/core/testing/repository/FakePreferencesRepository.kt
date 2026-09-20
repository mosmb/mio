package io.morgan.mio.core.testing.repository

import io.morgan.mio.domain.model.AccountId
import io.morgan.mio.domain.preferences.AccountPreferences
import io.morgan.mio.domain.preferences.AppearancePreference
import io.morgan.mio.domain.preferences.DevicePreferences
import io.morgan.mio.domain.preferences.LibraryMediaFilter
import io.morgan.mio.domain.preferences.LibraryStatusFilter
import io.morgan.mio.domain.preferences.MotionPreference
import io.morgan.mio.domain.preferences.PreferencesRepository
import io.morgan.mio.domain.preferences.TitleLanguagePreference
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

public class FakePreferencesRepository : PreferencesRepository {
    private data class State(
        val device: DevicePreferences,
        val accounts: Map<AccountId, AccountPreferences>,
    )

    private val state = MutableStateFlow(State(device = DevicePreferences.Default, accounts = emptyMap()))

    override fun observeDevicePreferences(): Flow<DevicePreferences> =
        state
            .map { current -> current.device }
            .distinctUntilChanged()

    override fun observeAccountPreferences(accountId: AccountId): Flow<AccountPreferences> =
        state
            .map { current -> current.accounts[accountId] ?: AccountPreferences.default(accountId) }
            .distinctUntilChanged()

    override suspend fun setTitleLanguage(preference: TitleLanguagePreference) {
        state.update { current -> current.copy(device = current.device.copy(titleLanguage = preference)) }
    }

    override suspend fun setAppearance(preference: AppearancePreference) {
        state.update { current -> current.copy(device = current.device.copy(appearance = preference)) }
    }

    override suspend fun setMotion(preference: MotionPreference) {
        state.update { current -> current.copy(device = current.device.copy(motion = preference)) }
    }

    override suspend fun setHapticsEnabled(enabled: Boolean) {
        state.update { current -> current.copy(device = current.device.copy(hapticsEnabled = enabled)) }
    }

    override suspend fun setLibraryMediaFilter(
        accountId: AccountId,
        filter: LibraryMediaFilter,
    ) {
        state.update { current ->
            val accountPreferences =
                current.accounts[accountId]
                    ?: AccountPreferences.default(accountId)
            current.copy(
                accounts =
                    current.accounts +
                        (accountId to accountPreferences.copy(libraryMediaFilter = filter)),
            )
        }
    }

    override suspend fun setLibraryStatusFilter(
        accountId: AccountId,
        filter: LibraryStatusFilter,
    ) {
        state.update { current ->
            val accountPreferences = current.accounts[accountId] ?: AccountPreferences.default(accountId)
            current.copy(
                accounts = current.accounts + (accountId to accountPreferences.copy(libraryStatusFilter = filter)),
            )
        }
    }
}
