package io.morgan.mio.core.designsystem.preview

import android.content.res.Configuration
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import io.morgan.mio.core.designsystem.theme.MioTheme

@Preview(
    name = "Light reference",
    widthDp = 393,
    heightDp = 852,
    uiMode = Configuration.UI_MODE_NIGHT_NO,
    showBackground = true,
)
@Preview(
    name = "Dark reference",
    widthDp = 393,
    heightDp = 852,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
    showBackground = true,
)
@Target(AnnotationTarget.ANNOTATION_CLASS, AnnotationTarget.FUNCTION)
public annotation class MioThemePreviews

@Preview(name = "Reference", widthDp = 393, heightDp = 852)
@Preview(name = "Compact", widthDp = 320, heightDp = 693)
@Target(AnnotationTarget.ANNOTATION_CLASS, AnnotationTarget.FUNCTION)
public annotation class MioWidthPreviews

@Preview(name = "Default", fontScale = 1f)
@Preview(name = "Large text", fontScale = 2f)
@Target(AnnotationTarget.ANNOTATION_CLASS, AnnotationTarget.FUNCTION)
public annotation class MioFontScalePreviews

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
