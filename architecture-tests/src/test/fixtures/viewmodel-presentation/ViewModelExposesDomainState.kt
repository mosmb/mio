package io.morgan.mio.feature.today.impl

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import dev.zacsweers.metrox.viewmodel.metroViewModel
import io.morgan.mio.core.common.di.AppScope
import io.morgan.mio.domain.account.AccountSessionState
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

internal sealed interface DomainEvent

@ContributesIntoMap(AppScope::class)
@ViewModelKey
@Inject
internal class DomainStateViewModel : ViewModel() {
    internal val uiState: StateFlow<AccountSessionState> = MutableStateFlow(AccountSessionState.Restoring)
    internal val events: SharedFlow<DomainEvent> = MutableSharedFlow()
}

@Composable
internal fun DomainStateRoute(modifier: Modifier = Modifier) {
    val viewModel = metroViewModel<DomainStateViewModel>()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(viewModel, lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.events.collect { }
        }
    }
    DomainStateScreen(uiState, modifier)
}

@Composable
internal fun DomainStateScreen(
    state: AccountSessionState,
    modifier: Modifier = Modifier,
) {
    state
    modifier
}
