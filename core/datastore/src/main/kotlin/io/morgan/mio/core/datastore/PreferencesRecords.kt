package io.morgan.mio.core.datastore

public data class DevicePreferencesRecord(
    public val appearance: String,
    public val motion: String,
    public val hapticsEnabled: Boolean,
)

public data class AccountPreferencesRecord(
    public val accountId: String,
    public val libraryMediaFilter: String,
)
