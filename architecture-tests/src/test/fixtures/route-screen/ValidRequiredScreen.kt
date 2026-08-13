package io.morgan.mio.feature.today.impl

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
internal fun ValidRequiredRoute(
    title: String,
    modifier: Modifier = Modifier,
) {
    ValidRequiredScreen(title = title, modifier = modifier)
}

@Composable
internal fun ValidRequiredScreen(
    title: String,
    modifier: Modifier = Modifier,
) {
    val repository = title
    modifier
    repository
}
