package io.morgan.mio.core.datastore

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey

public object PreferencesKeys {
    public const val DEVICE_APPEARANCE_NAME: String = "device.appearance"
    public const val DEVICE_MOTION_NAME: String = "device.motion"
    public const val DEVICE_HAPTICS_ENABLED_NAME: String = "device.haptics_enabled"
    public const val ACCOUNT_LIBRARY_MEDIA_FILTER_PREFIX: String = "account."
    public const val ACCOUNT_LIBRARY_MEDIA_FILTER_SUFFIX: String = ".library_media_filter"
    public const val ACCOUNT_LIBRARY_STATUS_FILTER_SUFFIX: String = ".library_status_filter"

    public val deviceAppearance: Preferences.Key<String> =
        stringPreferencesKey(DEVICE_APPEARANCE_NAME)
    public val deviceMotion: Preferences.Key<String> = stringPreferencesKey(DEVICE_MOTION_NAME)
    public val deviceHapticsEnabled: Preferences.Key<Boolean> =
        booleanPreferencesKey(DEVICE_HAPTICS_ENABLED_NAME)

    public fun accountLibraryMediaFilter(accountId: String): Preferences.Key<String> {
        require(accountId.isNotBlank())
        return stringPreferencesKey(
            "$ACCOUNT_LIBRARY_MEDIA_FILTER_PREFIX$accountId$ACCOUNT_LIBRARY_MEDIA_FILTER_SUFFIX",
        )
    }

    public fun accountLibraryStatusFilter(accountId: String): Preferences.Key<String> {
        require(accountId.isNotBlank())
        return stringPreferencesKey(
            "$ACCOUNT_LIBRARY_MEDIA_FILTER_PREFIX$accountId$ACCOUNT_LIBRARY_STATUS_FILTER_SUFFIX",
        )
    }
}
