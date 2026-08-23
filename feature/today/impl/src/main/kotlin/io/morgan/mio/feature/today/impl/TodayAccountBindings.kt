package io.morgan.mio.feature.today.impl

import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import io.morgan.mio.core.common.di.AccountScope
import io.morgan.mio.domain.collection.CollectionRepository
import io.morgan.mio.domain.media.MediaRepository
import io.morgan.mio.domain.queue.ObserveTodayQueueUseCase
import kotlin.time.Clock

@BindingContainer
@ContributesTo(AccountScope::class)
public object TodayAccountBindings {
    @Provides
    @SingleIn(AccountScope::class)
    public fun provideObserveTodayQueueUseCase(
        collectionRepository: CollectionRepository,
        mediaRepository: MediaRepository,
        clock: Clock,
    ): ObserveTodayQueueUseCase =
        ObserveTodayQueueUseCase(
            collectionRepository = collectionRepository,
            mediaRepository = mediaRepository,
            clock = clock,
        )
}
