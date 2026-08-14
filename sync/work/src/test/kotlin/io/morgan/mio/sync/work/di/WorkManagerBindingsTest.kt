package io.morgan.mio.sync.work.di

import io.morgan.mio.sync.work.factory.MioWorkerFactory
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [35])
class WorkManagerBindingsTest {
    @Test
    fun emptySetProducesAnInjectableWorkerFactoryAndConfiguration() {
        val workerFactory = WorkManagerBindings.provideWorkerFactory(emptySet())
        val configuration = WorkManagerBindings.provideWorkManagerConfiguration(workerFactory)

        assertSame(workerFactory, configuration.workerFactory)
    }

    @Test
    fun workerFactoryExtensionPointAcceptsClaimsWithoutExposingScheduling() {
        val factory = MioWorkerFactory { _, _, _ -> null }
        val workerFactory = WorkManagerBindings.provideWorkerFactory(setOf(factory))

        assertSame(workerFactory, WorkManagerBindings.provideWorkManagerConfiguration(workerFactory).workerFactory)
    }
}
