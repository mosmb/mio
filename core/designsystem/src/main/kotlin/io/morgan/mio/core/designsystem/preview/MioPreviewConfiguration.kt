package io.morgan.mio.core.designsystem.preview

import androidx.compose.runtime.Immutable

@Immutable
public data class MioPreviewConfiguration(
    public val darkTheme: Boolean,
    public val highContrast: Boolean,
    public val reducedMotion: Boolean,
    public val hapticsEnabled: Boolean,
) {
    public companion object {
        public val Light: MioPreviewConfiguration =
            MioPreviewConfiguration(
                darkTheme = false,
                highContrast = false,
                reducedMotion = false,
                hapticsEnabled = true,
            )

        public val Dark: MioPreviewConfiguration =
            Light.copy(darkTheme = true)

        public val HighContrastLight: MioPreviewConfiguration =
            Light.copy(highContrast = true)

        public val HighContrastDark: MioPreviewConfiguration =
            Dark.copy(highContrast = true)

        public val ReducedMotionLight: MioPreviewConfiguration =
            Light.copy(reducedMotion = true)

        public val ReducedMotionDark: MioPreviewConfiguration =
            Dark.copy(reducedMotion = true)
    }
}
