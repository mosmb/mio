package io.morgan.mio.feature.discover.impl

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import io.morgan.mio.core.designsystem.preview.MioPreviewConfiguration
import io.morgan.mio.core.designsystem.preview.MioPreviewSurface
import io.morgan.mio.core.designsystem.theme.MioTheme

@Composable
internal fun DiscoverRoute(modifier: Modifier = Modifier) {
    DiscoverScreen(modifier = modifier)
}

@Composable
internal fun DiscoverScreen(modifier: Modifier = Modifier) {
    Text(
        text = "Discover",
        style = MioTheme.typography.body,
        modifier = modifier.fillMaxSize(),
    )
}

@Preview(name = "Discover light", widthDp = 393, heightDp = 852, showBackground = true)
@Composable
internal fun DiscoverScreenLightPreview() {
    MioPreviewSurface(MioPreviewConfiguration.Light) { DiscoverScreen() }
}

@Preview(
    name = "Discover dark",
    widthDp = 393,
    heightDp = 852,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
    showBackground = true,
)
@Composable
internal fun DiscoverScreenDarkPreview() {
    MioPreviewSurface(MioPreviewConfiguration.Dark) { DiscoverScreen() }
}
