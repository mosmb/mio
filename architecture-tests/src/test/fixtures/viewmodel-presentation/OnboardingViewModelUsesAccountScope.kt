package io.morgan.mio.feature.today.impl

import androidx.lifecycle.ViewModel
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import io.morgan.mio.core.common.di.AccountScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

@ContributesIntoMap(AccountScope::class)
@ViewModelKey
@Inject
internal class OnboardingViewModel : ViewModel() {
    internal val uiState: StateFlow<Boolean> = MutableStateFlow(false)
}
