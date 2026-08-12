package io.morgan.mio.core.designsystem.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class MioColorsTest {
    @Test
    fun lightAndDarkPalettesUseEverySpecifiedArgbToken() {
        val light = MioColors.Light
        assertEquals(Color(0xFFFFFFFF), light.background)
        assertEquals(Color(0xFFFFFFFF), light.surface)
        assertEquals(Color(0xFFF2F5EF), light.surfaceSoft)
        assertEquals(Color(0xFFF7F9F5), light.surfaceRaised)
        assertEquals(Color(0xFF0B0D0F), light.text)
        assertEquals(Color(0xFF5B635B), light.textMuted)
        assertEquals(Color(0xFFC8F53D), light.accent)
        assertEquals(Color(0xFF0B0D0F), light.onAccent)
        assertEquals(Color(0x1F0B0D0F), light.border)
        assertEquals(Color(0x380B0D0F), light.borderStrong)
        assertEquals(Color(0x6B0B0D0F), light.scrim)
        assertEquals(Color(0xFFB42318), light.error)
        assertEquals(Color(0xFFFEE4E2), light.errorSurface)

        val dark = MioColors.Dark
        assertEquals(Color(0xFF0B0D0F), dark.background)
        assertEquals(Color(0xFF111418), dark.surface)
        assertEquals(Color(0xFF171B1E), dark.surfaceSoft)
        assertEquals(Color(0xFF15191C), dark.surfaceRaised)
        assertEquals(Color(0xFFF4F8F2), dark.text)
        assertEquals(Color(0xFFA6AFA5), dark.textMuted)
        assertEquals(Color(0xFFC8F53D), dark.accent)
        assertEquals(Color(0xFF0B0D0F), dark.onAccent)
        assertEquals(Color(0x24F4F8F2), dark.border)
        assertEquals(Color(0x40F4F8F2), dark.borderStrong)
        assertEquals(Color(0x85000000), dark.scrim)
        assertEquals(Color(0xFFFFB4AB), dark.error)
        assertEquals(Color(0xFF3B1512), dark.errorSurface)
    }

    @Test
    fun highContrastChangesOnlyMutedTextAndBorder() {
        assertEquals(MioColors.Light.borderStrong, MioColors.HighContrastLight.border)
        assertEquals(MioColors.Light.text, MioColors.HighContrastLight.textMuted)
        assertEquals(MioColors.Dark.borderStrong, MioColors.HighContrastDark.border)
        assertEquals(MioColors.Dark.text, MioColors.HighContrastDark.textMuted)
        assertNotEquals(MioColors.Light.border, MioColors.HighContrastLight.border)
        assertNotEquals(MioColors.Dark.textMuted, MioColors.HighContrastDark.textMuted)

        assertEquals(
            MioColors.Light.copy(
                border = MioColors.Light.borderStrong,
                textMuted = MioColors.Light.text,
            ),
            MioColors.HighContrastLight,
        )
        assertEquals(
            MioColors.Dark.copy(
                border = MioColors.Dark.borderStrong,
                textMuted = MioColors.Dark.text,
            ),
            MioColors.HighContrastDark,
        )
    }

    @Test
    fun materialSchemeDoesNotExposeMaterialDefaults() {
        val light = MioColors.Light.materialColorScheme(darkTheme = false)
        val dark = MioColors.Dark.materialColorScheme(darkTheme = true)

        assertEquals(MioColors.Light.accent, light.primary)
        assertEquals(MioColors.Dark.accent, dark.primary)
        assertEquals(MioColors.Light.text, light.onBackground)
        assertEquals(MioColors.Dark.text, dark.onBackground)
        assertEquals(Color.Transparent, light.surfaceTint)
        assertEquals(Color.Transparent, dark.surfaceTint)
        assertEquals(MioColors.Light.surfaceSoft, light.tertiaryContainer)
        assertEquals(MioColors.Dark.surfaceSoft, dark.tertiaryContainer)
    }
}
