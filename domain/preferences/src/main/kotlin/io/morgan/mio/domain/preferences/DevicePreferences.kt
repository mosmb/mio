package io.morgan.mio.domain.preferences

public data class DevicePreferences(
    public val appearance: AppearancePreference,
    public val motion: MotionPreference,
    public val hapticsEnabled: Boolean,
    public val titleLanguage: TitleLanguagePreference = TitleLanguagePreference.SYSTEM,
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
