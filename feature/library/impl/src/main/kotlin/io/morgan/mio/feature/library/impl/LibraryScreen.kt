package io.morgan.mio.feature.library.impl

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import io.morgan.mio.core.designsystem.preview.MioPreviewConfiguration
import io.morgan.mio.core.designsystem.preview.MioPreviewSurface
import io.morgan.mio.core.designsystem.theme.MioTheme

@Composable
internal fun LibraryRoute(modifier: Modifier = Modifier) {
    LibraryScreen(modifier = modifier)
}

@Composable
internal fun LibraryScreen(modifier: Modifier = Modifier) {
    Text(
        text = "Library",
        style = MioTheme.typography.body,
        modifier = modifier.fillMaxSize(),
    )
}

@Preview(name = "Library light", widthDp = 393, heightDp = 852, showBackground = true)
@Composable
internal fun LibraryScreenLightPreview() {
    MioPreviewSurface(MioPreviewConfiguration.Light) { LibraryScreen() }
}

@Preview(
    name = "Library dark",
    widthDp = 393,
    heightDp = 852,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
    showBackground = true,
)
@Composable
internal fun LibraryScreenDarkPreview() {
    MioPreviewSurface(MioPreviewConfiguration.Dark) { LibraryScreen() }
}
