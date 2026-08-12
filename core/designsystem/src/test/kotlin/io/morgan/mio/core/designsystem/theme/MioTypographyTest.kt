package io.morgan.mio.core.designsystem.theme

import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.font.FontWeight
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class MioTypographyTest {
    @Test
    fun stylesUseGabaritoAndTheExactScale() {
        val expected =
            listOf(
                Triple(DefaultMioTypography.display, 36f, 40f),
                Triple(DefaultMioTypography.title, 28f, 32f),
                Triple(DefaultMioTypography.section, 22f, 28f),
                Triple(DefaultMioTypography.cardTitle, 17f, 22f),
                Triple(DefaultMioTypography.body, 16f, 24f),
                Triple(DefaultMioTypography.bodyStrong, 16f, 22f),
                Triple(DefaultMioTypography.label, 13f, 18f),
                Triple(DefaultMioTypography.caption, 12f, 16f),
            )

        expected.forEach { (style, size, lineHeight) ->
            assertSame(MioGabaritoFontFamily, style.fontFamily)
            assertEquals(size, style.fontSize.value, 0f)
            assertEquals(lineHeight, style.lineHeight.value, 0f)
            assertFalse(requireNotNull((style.platformStyle as PlatformTextStyle).paragraphStyle).includeFontPadding)
        }
        assertEquals(FontWeight.Bold, DefaultMioTypography.display.fontWeight)
        assertEquals(FontWeight.Bold, DefaultMioTypography.title.fontWeight)
        assertEquals(FontWeight.Bold, DefaultMioTypography.section.fontWeight)
        assertEquals(FontWeight.SemiBold, DefaultMioTypography.cardTitle.fontWeight)
        assertEquals(FontWeight.Normal, DefaultMioTypography.body.fontWeight)
        assertEquals(FontWeight.SemiBold, DefaultMioTypography.bodyStrong.fontWeight)
        assertEquals(FontWeight.Medium, DefaultMioTypography.label.fontWeight)
        assertEquals(FontWeight.Medium, DefaultMioTypography.caption.fontWeight)
    }

    @Test
    fun letterSpacingMatchesTheDocumentedEmValues() {
        assertEquals(-0.04f, DefaultMioTypography.display.letterSpacing.value, 0f)
        assertEquals(-0.03f, DefaultMioTypography.title.letterSpacing.value, 0f)
        assertEquals(-0.025f, DefaultMioTypography.section.letterSpacing.value, 0f)
        assertEquals(-0.015f, DefaultMioTypography.cardTitle.letterSpacing.value, 0f)
        assertEquals(0f, DefaultMioTypography.body.letterSpacing.value, 0f)
        assertEquals(-0.01f, DefaultMioTypography.bodyStrong.letterSpacing.value, 0f)
        assertEquals(0f, DefaultMioTypography.label.letterSpacing.value, 0f)
        assertEquals(0.01f, DefaultMioTypography.caption.letterSpacing.value, 0f)
    }

    @Test
    fun materialRolesReuseTheNamedMioStyles() {
        val material = DefaultMioTypography.materialTypography
        assertSame(material, DefaultMioTypography.materialTypography)
        assertEquals(DefaultMioTypography.display, material.displayLarge)
        assertEquals(DefaultMioTypography.display, material.displayMedium)
        assertEquals(DefaultMioTypography.display, material.displaySmall)
        assertEquals(DefaultMioTypography.title, material.headlineLarge)
        assertEquals(DefaultMioTypography.title, material.headlineMedium)
        assertEquals(DefaultMioTypography.title, material.titleLarge)
        assertEquals(DefaultMioTypography.section, material.headlineSmall)
        assertEquals(DefaultMioTypography.cardTitle, material.titleMedium)
        assertEquals(DefaultMioTypography.bodyStrong, material.titleSmall)
        assertEquals(DefaultMioTypography.bodyStrong, material.labelLarge)
        assertEquals(DefaultMioTypography.body, material.bodyLarge)
        assertEquals(DefaultMioTypography.body, material.bodyMedium)
        assertEquals(DefaultMioTypography.caption, material.bodySmall)
        assertEquals(DefaultMioTypography.caption, material.labelSmall)
        assertEquals(DefaultMioTypography.label, material.labelMedium)
    }

    @Test
    fun tabularNumbersAreOptIn() {
        val tabular = DefaultMioTypography.body.withTabularNumbers()
        assertEquals("tnum", tabular.fontFeatureSettings)
        assertTrue(DefaultMioTypography.body.fontFeatureSettings.isNullOrEmpty())
    }
}
