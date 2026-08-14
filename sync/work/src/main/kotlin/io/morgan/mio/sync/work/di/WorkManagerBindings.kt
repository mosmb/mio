package io.morgan.mio.sync.work.di

import androidx.work.Configuration
import androidx.work.WorkerFactory
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Multibinds
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import io.morgan.mio.core.common.di.AppScope
import io.morgan.mio.sync.work.factory.DelegatingMioWorkerFactory
import io.morgan.mio.sync.work.factory.MioWorkerFactory

@BindingContainer
@ContributesTo(AppScope::class)
public abstract class WorkManagerBindings {
    @Multibinds(allowEmpty = true)
    public abstract fun workerFactories(): Set<MioWorkerFactory>

    companion object {
        @Provides
        @SingleIn(AppScope::class)
        public fun provideWorkerFactory(factories: Set<MioWorkerFactory>): WorkerFactory =
            DelegatingMioWorkerFactory(factories)

        @Provides
        @SingleIn(AppScope::class)
        public fun provideWorkManagerConfiguration(workerFactory: WorkerFactory): Configuration =
            Configuration.Builder().setWorkerFactory(workerFactory).build()
    }
}
