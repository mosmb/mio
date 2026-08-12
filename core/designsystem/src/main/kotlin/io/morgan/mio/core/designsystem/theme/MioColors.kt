package io.morgan.mio.core.designsystem.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

@Immutable
public data class MioColors(
    public val background: Color,
    public val surface: Color,
    public val surfaceSoft: Color,
    public val surfaceRaised: Color,
    public val text: Color,
    public val textMuted: Color,
    public val accent: Color,
    public val onAccent: Color,
    public val border: Color,
    public val borderStrong: Color,
    public val scrim: Color,
    public val error: Color,
    public val errorSurface: Color,
) {
    public companion object {
        public val Light: MioColors =
            MioColors(
                background = Color(0xFFFFFFFF),
                surface = Color(0xFFFFFFFF),
                surfaceSoft = Color(0xFFF2F5EF),
                surfaceRaised = Color(0xFFF7F9F5),
                text = Color(0xFF0B0D0F),
                textMuted = Color(0xFF5B635B),
                accent = Color(0xFFC8F53D),
                onAccent = Color(0xFF0B0D0F),
                border = Color(0x1F0B0D0F),
                borderStrong = Color(0x380B0D0F),
                scrim = Color(0x6B0B0D0F),
                error = Color(0xFFB42318),
                errorSurface = Color(0xFFFEE4E2),
            )

        public val Dark: MioColors =
            MioColors(
                background = Color(0xFF0B0D0F),
                surface = Color(0xFF111418),
                surfaceSoft = Color(0xFF171B1E),
                surfaceRaised = Color(0xFF15191C),
                text = Color(0xFFF4F8F2),
                textMuted = Color(0xFFA6AFA5),
                accent = Color(0xFFC8F53D),
                onAccent = Color(0xFF0B0D0F),
                border = Color(0x24F4F8F2),
                borderStrong = Color(0x40F4F8F2),
                scrim = Color(0x85000000),
                error = Color(0xFFFFB4AB),
                errorSurface = Color(0xFF3B1512),
            )

        public val HighContrastLight: MioColors =
            Light.copy(
                border = Light.borderStrong,
                textMuted = Light.text,
            )

        public val HighContrastDark: MioColors =
            Dark.copy(
                border = Dark.borderStrong,
                textMuted = Dark.text,
            )
    }
}

internal fun MioColors.materialColorScheme(darkTheme: Boolean): ColorScheme =
    if (darkTheme) {
        darkColorScheme(
            primary = accent,
            onPrimary = onAccent,
            primaryContainer = accent,
            onPrimaryContainer = onAccent,
            inversePrimary = accent,
            secondary = surfaceSoft,
            onSecondary = text,
            secondaryContainer = surfaceSoft,
            onSecondaryContainer = text,
            tertiary = surfaceSoft,
            onTertiary = text,
            tertiaryContainer = surfaceSoft,
            onTertiaryContainer = text,
            background = background,
            onBackground = text,
            surface = surface,
            onSurface = text,
            surfaceVariant = surfaceSoft,
            onSurfaceVariant = textMuted,
            surfaceTint = Color.Transparent,
            inverseSurface = text,
            inverseOnSurface = background,
            error = error,
            onError = Color(0xFF0B0D0F),
            errorContainer = errorSurface,
            onErrorContainer = error,
            outline = borderStrong,
            outlineVariant = border,
            scrim = scrim,
            surfaceBright = surfaceSoft,
            surfaceDim = background,
            surfaceContainer = surface,
            surfaceContainerHigh = surfaceSoft,
            surfaceContainerHighest = surfaceSoft,
            surfaceContainerLow = surfaceRaised,
            surfaceContainerLowest = background,
            primaryFixed = accent,
            primaryFixedDim = accent,
            onPrimaryFixed = onAccent,
            onPrimaryFixedVariant = onAccent,
            secondaryFixed = surfaceSoft,
            secondaryFixedDim = surfaceSoft,
            onSecondaryFixed = text,
            onSecondaryFixedVariant = text,
            tertiaryFixed = surfaceSoft,
            tertiaryFixedDim = surfaceSoft,
            onTertiaryFixed = text,
            onTertiaryFixedVariant = text,
        )
    } else {
        lightColorScheme(
            primary = accent,
            onPrimary = onAccent,
            primaryContainer = accent,
            onPrimaryContainer = onAccent,
            inversePrimary = accent,
            secondary = surfaceSoft,
            onSecondary = text,
            secondaryContainer = surfaceSoft,
            onSecondaryContainer = text,
            tertiary = surfaceSoft,
            onTertiary = text,
            tertiaryContainer = surfaceSoft,
            onTertiaryContainer = text,
            background = background,
            onBackground = text,
            surface = surface,
            onSurface = text,
            surfaceVariant = surfaceSoft,
            onSurfaceVariant = textMuted,
            surfaceTint = Color.Transparent,
            inverseSurface = text,
            inverseOnSurface = background,
            error = error,
            onError = Color.White,
            errorContainer = errorSurface,
            onErrorContainer = error,
            outline = borderStrong,
            outlineVariant = border,
            scrim = scrim,
            surfaceBright = background,
            surfaceDim = surfaceSoft,
            surfaceContainer = surface,
            surfaceContainerHigh = surfaceSoft,
            surfaceContainerHighest = surfaceSoft,
            surfaceContainerLow = surfaceRaised,
            surfaceContainerLowest = background,
            primaryFixed = accent,
            primaryFixedDim = accent,
            onPrimaryFixed = onAccent,
            onPrimaryFixedVariant = onAccent,
            secondaryFixed = surfaceSoft,
            secondaryFixedDim = surfaceSoft,
            onSecondaryFixed = text,
            onSecondaryFixedVariant = text,
            tertiaryFixed = surfaceSoft,
            tertiaryFixedDim = surfaceSoft,
            onTertiaryFixed = text,
            onTertiaryFixedVariant = text,
        )
    }
