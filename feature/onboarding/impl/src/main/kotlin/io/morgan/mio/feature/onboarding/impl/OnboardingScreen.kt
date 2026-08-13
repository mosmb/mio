package io.morgan.mio.feature.onboarding.impl

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import io.morgan.mio.core.designsystem.preview.MioPreviewConfiguration
import io.morgan.mio.core.designsystem.preview.MioPreviewSurface
import io.morgan.mio.core.designsystem.theme.MioTheme

@Composable
internal fun OnboardingRoute(modifier: Modifier = Modifier) {
    OnboardingScreen(modifier = modifier)
}

@Composable
internal fun OnboardingScreen(modifier: Modifier = Modifier) {
    Text(
        text = "Onboarding",
        style = MioTheme.typography.body,
        modifier = modifier.fillMaxSize(),
    )
}

@Preview(name = "Onboarding light", widthDp = 393, heightDp = 852, showBackground = true)
@Composable
internal fun OnboardingScreenLightPreview() {
    MioPreviewSurface(MioPreviewConfiguration.Light) { OnboardingScreen() }
}

@Preview(
    name = "Onboarding dark",
    widthDp = 393,
    heightDp = 852,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
    showBackground = true,
)
@Composable
internal fun OnboardingScreenDarkPreview() {
    MioPreviewSurface(MioPreviewConfiguration.Dark) { OnboardingScreen() }
}
