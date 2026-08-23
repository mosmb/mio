package io.morgan.mio.feature.today.impl

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import io.morgan.mio.core.common.di.AccountScope
import io.morgan.mio.domain.model.AccountId
import io.morgan.mio.domain.queue.ObserveTodayQueueUseCase
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

@ContributesIntoMap(AccountScope::class)
@ViewModelKey
@Inject
internal class TodayViewModel(
    accountId: AccountId,
    observeTodayQueue: ObserveTodayQueueUseCase,
) : ViewModel() {
    internal val uiState: StateFlow<TodayUiState> =
        observeTodayQueue(accountId)
            .map(TodayUiMapper::map)
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = TodayUiState.Loading,
            )
}
