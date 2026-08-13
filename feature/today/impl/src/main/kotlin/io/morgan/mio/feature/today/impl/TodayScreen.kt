package io.morgan.mio.feature.today.impl

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import io.morgan.mio.core.designsystem.preview.MioPreviewConfiguration
import io.morgan.mio.core.designsystem.preview.MioPreviewSurface
import io.morgan.mio.core.designsystem.theme.MioTheme

@Composable
internal fun TodayRoute(modifier: Modifier = Modifier) {
    TodayScreen(modifier = modifier)
}

@Composable
internal fun TodayScreen(modifier: Modifier = Modifier) {
    Text(
        text = "Today",
        style = MioTheme.typography.body,
        modifier = modifier.fillMaxSize(),
    )
}

@Preview(name = "Today light", widthDp = 393, heightDp = 852, showBackground = true)
@Composable
internal fun TodayScreenLightPreview() {
    MioPreviewSurface(MioPreviewConfiguration.Light) { TodayScreen() }
}

@Preview(
    name = "Today dark",
    widthDp = 393,
    heightDp = 852,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
    showBackground = true,
)
@Composable
internal fun TodayScreenDarkPreview() {
    MioPreviewSurface(MioPreviewConfiguration.Dark) { TodayScreen() }
}
