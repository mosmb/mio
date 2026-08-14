package io.morgan.mio.feature.onboarding.impl

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.zacsweers.metrox.viewmodel.metroViewModel
import io.morgan.mio.feature.today.impl.CrossModuleViewModel

@Composable
internal fun CrossModuleRoute(modifier: Modifier = Modifier) {
    val viewModel = metroViewModel<CrossModuleViewModel>()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    CrossModuleScreen(uiState, modifier)
}

@Composable
internal fun CrossModuleScreen(
    state: Any,
    modifier: Modifier = Modifier,
) {
    state
    modifier
}
