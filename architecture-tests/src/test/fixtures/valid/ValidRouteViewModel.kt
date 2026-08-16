package io.morgan.mio.feature.today.impl

import androidx.lifecycle.ViewModel
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import io.morgan.mio.core.common.di.AppScope
import kotlinx.coroutines.flow.StateFlow

@ContributesIntoMap(AppScope::class)
@ViewModelKey
@Inject
internal class ValidRouteViewModel : ViewModel() {
    internal val uiState: StateFlow<ValidRouteUiState> = TODO()
    internal val effect: StateFlow<ValidRouteUiEffect?> = TODO()
}
