package io.morgan.mio.core.datastore

public data class DevicePreferencesRecord(
    public val appearance: String,
    public val motion: String,
    public val hapticsEnabled: Boolean,
)
