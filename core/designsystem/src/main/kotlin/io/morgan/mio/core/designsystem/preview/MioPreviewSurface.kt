package io.morgan.mio.core.designsystem.preview

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.morgan.mio.core.designsystem.theme.MioTheme

@Composable
public fun MioPreviewSurface(
    configuration: MioPreviewConfiguration,
    content: @Composable () -> Unit,
) {
    MioTheme(
        darkTheme = configuration.darkTheme,
        reducedMotionOverride = configuration.reducedMotion,
        hapticsEnabled = configuration.hapticsEnabled,
        highContrastOverride = configuration.highContrast,
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MioTheme.colors.background,
            contentColor = MioTheme.colors.text,
        ) {
            content()
        }
    }
}
