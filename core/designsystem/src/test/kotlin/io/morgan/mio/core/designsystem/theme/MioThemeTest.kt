package io.morgan.mio.core.designsystem.theme

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import io.morgan.mio.core.designsystem.accessibility.MioAccessibility
import io.morgan.mio.core.designsystem.testing.DesignSystemActivityRule
import io.morgan.mio.core.designsystem.testing.composeRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MioThemeTest {
    private val activityRule = DesignSystemActivityRule()

    @get:Rule
    val composeRule: AndroidComposeTestRule<DesignSystemActivityRule, *> = activityRule.composeRule()

    @Test
    fun selectsAllThemePalettesAndMapsMaterialRoles() {
        val cases =
            listOf(
                ThemeCase(false, false, MioColors.Light),
                ThemeCase(true, false, MioColors.Dark),
                ThemeCase(false, true, MioColors.HighContrastLight),
                ThemeCase(true, true, MioColors.HighContrastDark),
            )

        val currentCase = mutableStateOf(cases.first())
        var observedColors: MioColors? = null
        var observedContentColor = androidx.compose.ui.graphics.Color.Unspecified
        var observedSchemePrimary = androidx.compose.ui.graphics.Color.Unspecified
        composeRule.setContent {
            val case = currentCase.value
            MioTheme(
                darkTheme = case.darkTheme,
                highContrastOverride = case.highContrast,
            ) {
                val colors = MioTheme.colors
                observedContentColor = LocalContentColor.current
                observedSchemePrimary = MaterialTheme.colorScheme.primary
                SideEffect { observedColors = colors }
                Text("theme content")
            }
        }
        cases.forEach { case ->
            composeRule.runOnIdle { currentCase.value = case }
            composeRule.waitForIdle()

            assertEquals(case.colors, observedColors)
            assertEquals(case.colors.text, observedContentColor)
            assertEquals(case.colors.accent, observedSchemePrimary)
        }
    }

    @Test
    fun resolvesReducedMotionAndHapticsFlagsThroughTheAccessibilityLocal() {
        var observed: MioAccessibility? = null
        composeRule.setContent {
            MioTheme(
                reducedMotionOverride = true,
                hapticsEnabled = false,
                highContrastOverride = true,
            ) {
                val currentAccessibility = MioTheme.accessibility
                SideEffect { observed = currentAccessibility }
                Text("accessibility content")
            }
        }
        composeRule.waitForIdle()

        assertEquals(
            MioAccessibility(
                highContrastEnabled = true,
                reducedMotionEnabled = true,
                hapticsEnabled = false,
            ),
            observed,
        )
    }

    private data class ThemeCase(
        val darkTheme: Boolean,
        val highContrast: Boolean,
        val colors: MioColors,
    )
}
