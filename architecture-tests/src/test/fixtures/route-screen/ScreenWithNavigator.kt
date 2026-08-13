package io.morgan.mio.feature.today.impl

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.morgan.mio.core.navigation.MioNavigator

@Composable
internal fun BrokenRoute(modifier: Modifier = Modifier) {
    BrokenScreen(modifier = modifier, navigator = null)
}

@Composable
internal fun BrokenScreen(
    modifier: Modifier = Modifier,
    navigator: MioNavigator?,
) {
    modifier
    navigator
}
