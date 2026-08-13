package io.morgan.mio.feature.you.impl

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import io.morgan.mio.core.designsystem.preview.MioPreviewConfiguration
import io.morgan.mio.core.designsystem.preview.MioPreviewSurface
import io.morgan.mio.core.designsystem.theme.MioTheme

@Composable
internal fun YouRoute(modifier: Modifier = Modifier) {
    YouScreen(modifier = modifier)
}

@Composable
internal fun YouScreen(modifier: Modifier = Modifier) {
    Text(
        text = "You",
        style = MioTheme.typography.body,
        modifier = modifier.fillMaxSize(),
    )
}

@Preview(name = "You light", widthDp = 393, heightDp = 852, showBackground = true)
@Composable
internal fun YouScreenLightPreview() {
    MioPreviewSurface(MioPreviewConfiguration.Light) { YouScreen() }
}

@Preview(
    name = "You dark",
    widthDp = 393,
    heightDp = 852,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
    showBackground = true,
)
@Composable
internal fun YouScreenDarkPreview() {
    MioPreviewSurface(MioPreviewConfiguration.Dark) { YouScreen() }
}
