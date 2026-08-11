package io.morgan.mio.domain.preferences

import io.morgan.mio.domain.model.AccountId

public enum class AppearancePreference {
    SYSTEM,
    LIGHT,
    DARK,
}

public enum class MotionPreference {
    FOLLOW_SYSTEM,
    REDUCED,
}

public enum class LibraryMediaFilter {
    ALL,
    ANIME,
    MANGA,
}

public data class DevicePreferences(
    public val appearance: AppearancePreference,
    public val motion: MotionPreference,
    public val hapticsEnabled: Boolean,
) {
    public companion object {
        public val Default: DevicePreferences =
            DevicePreferences(
                appearance = AppearancePreference.SYSTEM,
                motion = MotionPreference.FOLLOW_SYSTEM,
                hapticsEnabled = true,
            )
    }
}

public data class AccountPreferences(
    public val accountId: AccountId,
    public val libraryMediaFilter: LibraryMediaFilter,
) {
    public companion object {
        public fun default(accountId: AccountId): AccountPreferences =
            AccountPreferences(
                accountId = accountId,
                libraryMediaFilter = LibraryMediaFilter.ALL,
            )
    }
}
