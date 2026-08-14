package io.morgan.mio.feature.today.impl

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.zacsweers.metrox.viewmodel.metroViewModel

@Composable
internal fun ValidRouteRoute(modifier: Modifier = Modifier) {
    val viewModel = metroViewModel<ValidRouteViewModel>()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val effect by viewModel.effect.collectAsStateWithLifecycle()

    ValidRouteScreen(state = uiState, effect = effect, modifier = modifier)
}

@Composable
internal fun ValidRouteScreen(
    state: ValidRouteUiState,
    effect: ValidRouteUiEffect?,
    modifier: Modifier = Modifier,
) {
    state
    effect
    modifier
}
