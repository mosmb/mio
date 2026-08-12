package io.morgan.mio.core.designsystem.preview

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import io.morgan.mio.core.designsystem.accessibility.MioAccessibility
import io.morgan.mio.core.designsystem.testing.DesignSystemActivityRule
import io.morgan.mio.core.designsystem.testing.composeRule
import io.morgan.mio.core.designsystem.theme.MioTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MioPreviewTest {
    private val activityRule = DesignSystemActivityRule()

    @get:Rule
    val composeRule: AndroidComposeTestRule<DesignSystemActivityRule, *> = activityRule.composeRule()

    @Test
    fun namedConfigurationsCarryOnlyTheDocumentedFlags() {
        assertEquals(false, MioPreviewConfiguration.Light.darkTheme)
        assertEquals(false, MioPreviewConfiguration.Light.highContrast)
        assertEquals(false, MioPreviewConfiguration.Light.reducedMotion)
        assertEquals(true, MioPreviewConfiguration.Light.hapticsEnabled)
        assertEquals(true, MioPreviewConfiguration.Dark.darkTheme)
        assertEquals(true, MioPreviewConfiguration.HighContrastLight.highContrast)
        assertEquals(true, MioPreviewConfiguration.HighContrastDark.darkTheme)
        assertEquals(true, MioPreviewConfiguration.ReducedMotionLight.reducedMotion)
        assertEquals(true, MioPreviewConfiguration.ReducedMotionDark.darkTheme)
        assertEquals(true, MioPreviewConfiguration.ReducedMotionDark.reducedMotion)
    }

    @Test
    fun previewSurfaceComposesReferenceCompactAndLargeTextContent() {
        val selectedConfiguration = mutableStateOf(MioPreviewConfiguration.Light)
        var observed: MioAccessibility? = null
        composeRule.setContent {
            MioPreviewSurface(selectedConfiguration.value) {
                val currentAccessibility = MioTheme.accessibility
                SideEffect { observed = currentAccessibility }
                Box(Modifier.width(320.dp).height(693.dp)) {
                    Text("preview content")
                }
            }
        }
        val configurations =
            listOf(
                MioPreviewConfiguration.Light,
                MioPreviewConfiguration.Dark,
                MioPreviewConfiguration.HighContrastLight,
                MioPreviewConfiguration.HighContrastDark,
                MioPreviewConfiguration.ReducedMotionLight,
                MioPreviewConfiguration.ReducedMotionDark,
            )
        configurations.forEach { configuration ->
            composeRule.runOnIdle { selectedConfiguration.value = configuration }
            composeRule.waitForIdle()
            val resolvedAccessibility = requireNotNull(observed)
            assertEquals(configuration.highContrast, resolvedAccessibility.highContrastEnabled)
            assertEquals(configuration.reducedMotion, resolvedAccessibility.reducedMotionEnabled)
            assertEquals(configuration.hapticsEnabled, resolvedAccessibility.hapticsEnabled)
        }
    }

    @Test
    fun foundationCatalogComposesAtReferenceCompactAndLargeTextSizes() {
        val currentCase = mutableStateOf(CatalogCase(width = 393, height = 852, fontScale = 1f))
        composeRule.setContent {
            val case = currentCase.value
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density.density, case.fontScale),
            ) {
                Box(Modifier.width(case.width.dp).height(case.height.dp)) {
                    MioPreviewSurface(MioPreviewConfiguration.Light) {
                        MioFoundationCatalog()
                    }
                }
            }
        }

        listOf(
            CatalogCase(width = 393, height = 852, fontScale = 1f),
            CatalogCase(width = 320, height = 693, fontScale = 1f),
            CatalogCase(width = 393, height = 852, fontScale = 2f),
        ).forEach { case ->
            composeRule.runOnIdle { currentCase.value = case }
            composeRule.waitForIdle()
            composeRule.onNodeWithText("Mio foundation catalog").assertExists()
            composeRule.onNodeWithText("Error").assertExists()
        }
    }

    private data class CatalogCase(
        val width: Int,
        val height: Int,
        val fontScale: Float,
    )
}
