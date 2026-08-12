package io.morgan.mio.core.designsystem.accessibility

import androidx.compose.ui.graphics.Color
import io.morgan.mio.core.designsystem.theme.MioColors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MioContrastTest {
    @Test
    fun knownWcagRatiosMatchWithinTheDocumentedTolerance() {
        assertEquals(19.47f, Color(0xFF0B0D0F).contrastRatioAgainst(Color.White), 0.02f)
        assertEquals(6.21f, Color(0xFF5B635B).contrastRatioAgainst(Color.White), 0.02f)
        assertEquals(15.37f, Color(0xFF0B0D0F).contrastRatioAgainst(Color(0xFFC8F53D)), 0.02f)
        assertEquals(18.13f, Color(0xFFF4F8F2).contrastRatioAgainst(Color(0xFF0B0D0F)), 0.02f)
        assertEquals(8.62f, Color(0xFFA6AFA5).contrastRatioAgainst(Color(0xFF0B0D0F)), 0.02f)
        assertEquals(5.45f, Color(0xFFB42318).contrastRatioAgainst(Color(0xFFFEE4E2)), 0.02f)
    }

    @Test
    fun translucentForegroundIsCompositedBeforeContrastIsMeasured() {
        assertEquals(1f, Color.Transparent.contrastRatioAgainst(Color.White), 0.01f)
        assertEquals(
            1.32f,
            Color(0x1F000000).contrastRatioAgainst(Color.White),
            0.02f,
        )
    }

    @Test
    fun designatedMioForegroundPairsMeetTheirContrastContracts() {
        assertTrue(
            MioColors.Light.text.meetsContrastAgainst(
                MioColors.Light.background,
                MioContrast.NormalText,
            ),
        )
        assertTrue(
            MioColors.Light.textMuted.meetsContrastAgainst(
                MioColors.Light.background,
                MioContrast.NormalText,
            ),
        )
        assertTrue(
            MioColors.Dark.text.meetsContrastAgainst(
                MioColors.Dark.background,
                MioContrast.NormalText,
            ),
        )
        assertTrue(
            MioColors.Dark.textMuted.meetsContrastAgainst(
                MioColors.Dark.background,
                MioContrast.NormalText,
            ),
        )
        assertTrue(
            MioColors.Light.onAccent
                .meetsContrastAgainst(
                    MioColors.Light.accent,
                    MioContrast.LargeTextAndEssentialIcons,
                ),
        )
        assertTrue(
            MioColors.Dark.onAccent
                .meetsContrastAgainst(
                    MioColors.Dark.accent,
                    MioContrast.LargeTextAndEssentialIcons,
                ),
        )
        assertTrue(
            MioColors.Light.error.meetsContrastAgainst(
                MioColors.Light.errorSurface,
                MioContrast.NormalText,
            ),
        )
        assertTrue(
            MioColors.Dark.error.meetsContrastAgainst(
                MioColors.Dark.errorSurface,
                MioContrast.NormalText,
            ),
        )
        assertTrue(Color.White.contrastRatioAgainst(MioColors.Light.accent) < MioContrast.LargeTextAndEssentialIcons)
        assertTrue(
            MioColors.Light.onAccent.contrastRatioAgainst(MioColors.Light.accent) >=
                MioContrast.LargeTextAndEssentialIcons,
        )
    }
}
