package io.morgan.mio.feature.today.impl

import androidx.lifecycle.ViewModel
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactory
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactoryKey
import io.morgan.mio.core.common.di.AppScope
import io.morgan.mio.domain.model.MediaId

@AssistedInject
internal class AuthenticatedViewModel(
    @Assisted private val mediaId: MediaId,
) : ViewModel() {
    @AssistedFactory
    @ContributesIntoMap(AppScope::class)
    @ManualViewModelAssistedFactoryKey
    internal fun interface Factory : ManualViewModelAssistedFactory {
        fun create(@Assisted mediaId: MediaId): AuthenticatedViewModel
    }
}
