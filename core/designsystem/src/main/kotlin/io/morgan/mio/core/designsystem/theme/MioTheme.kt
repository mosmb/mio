package io.morgan.mio.core.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import io.morgan.mio.core.designsystem.accessibility.MioAccessibility
import io.morgan.mio.core.designsystem.accessibility.rememberSystemHighContrastEnabled
import io.morgan.mio.core.designsystem.accessibility.rememberSystemReducedMotionEnabled

internal val LocalMioColors =
    staticCompositionLocalOf<MioColors> {
        error("MioTheme.colors is not available outside MioTheme")
    }

internal val LocalMioTypography =
    staticCompositionLocalOf<MioTypography> {
        error("MioTheme.typography is not available outside MioTheme")
    }

internal val LocalMioSpacing =
    staticCompositionLocalOf<MioSpacing> {
        error("MioTheme.spacing is not available outside MioTheme")
    }

internal val LocalMioShapes =
    staticCompositionLocalOf<MioShapes> {
        error("MioTheme.shapes is not available outside MioTheme")
    }

internal val LocalMioElevation =
    staticCompositionLocalOf<MioElevation> {
        error("MioTheme.elevation is not available outside MioTheme")
    }

internal val LocalMioMotion =
    staticCompositionLocalOf<MioMotion> {
        error("MioTheme.motion is not available outside MioTheme")
    }

internal val LocalMioAccessibility =
    staticCompositionLocalOf<MioAccessibility> {
        error("MioTheme.accessibility is not available outside MioTheme")
    }

public object MioTheme {
    @Composable
    public operator fun invoke(
        darkTheme: Boolean = isSystemInDarkTheme(),
        reducedMotionOverride: Boolean = false,
        hapticsEnabled: Boolean = true,
        highContrastOverride: Boolean? = null,
        content: @Composable () -> Unit,
    ) {
        val systemReducedMotion = rememberSystemReducedMotionEnabled()
        val systemHighContrast = rememberSystemHighContrastEnabled()
        val reducedMotion = reducedMotionOverride || systemReducedMotion
        val highContrast = highContrastOverride ?: systemHighContrast
        val colors =
            when {
                darkTheme && highContrast -> MioColors.HighContrastDark
                darkTheme -> MioColors.Dark
                highContrast -> MioColors.HighContrastLight
                else -> MioColors.Light
            }
        val typography = DefaultMioTypography
        val spacing = MioSpacing.Default
        val shapes = MioShapes.Default
        val elevation = MioElevation.Default
        val motion = MioMotion.Default
        val accessibility =
            MioAccessibility(
                highContrastEnabled = highContrast,
                reducedMotionEnabled = reducedMotion,
                hapticsEnabled = hapticsEnabled,
            )

        CompositionLocalProvider(
            LocalMioColors provides colors,
            LocalMioTypography provides typography,
            LocalMioSpacing provides spacing,
            LocalMioShapes provides shapes,
            LocalMioElevation provides elevation,
            LocalMioMotion provides motion,
            LocalMioAccessibility provides accessibility,
        ) {
            MaterialTheme(
                colorScheme = colors.materialColorScheme(darkTheme),
                typography = typography.materialTypography,
                shapes = shapes.materialShapes,
            ) {
                CompositionLocalProvider(LocalContentColor provides colors.text) {
                    content()
                }
            }
        }
    }

    public val colors: MioColors
        @Composable get() = LocalMioColors.current

    public val typography: MioTypography
        @Composable get() = LocalMioTypography.current

    public val spacing: MioSpacing
        @Composable get() = LocalMioSpacing.current

    public val shapes: MioShapes
        @Composable get() = LocalMioShapes.current

    public val elevation: MioElevation
        @Composable get() = LocalMioElevation.current

    public val motion: MioMotion
        @Composable get() = LocalMioMotion.current

    public val accessibility: MioAccessibility
        @Composable get() = LocalMioAccessibility.current
}
