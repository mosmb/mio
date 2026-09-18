package io.morgan.mio.feature.library.impl

import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import io.morgan.mio.core.common.di.AccountScope
import io.morgan.mio.domain.collection.CollectionRepository
import io.morgan.mio.domain.media.MediaRepository
import io.morgan.mio.domain.queue.ObserveReadyMediaIdsUseCase
import kotlin.time.Clock

@BindingContainer
@ContributesTo(AccountScope::class)
public object LibraryAccountBindings {
    @Provides
    @SingleIn(AccountScope::class)
    public fun provideObserveReadyMediaIdsUseCase(
        collectionRepository: CollectionRepository,
        mediaRepository: MediaRepository,
        clock: Clock,
    ): ObserveReadyMediaIdsUseCase =
        ObserveReadyMediaIdsUseCase(
            collectionRepository = collectionRepository,
            mediaRepository = mediaRepository,
            clock = clock,
        )
}
