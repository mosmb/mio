package io.morgan.mio

import android.content.Context
import android.content.ContextWrapper
import androidx.test.core.app.ApplicationProvider
import dev.zacsweers.metro.createGraphFactory
import io.morgan.mio.di.AppGraph
import kotlinx.coroutines.cancel
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [35])
class MioApplicationTest {
    @Test
    fun applicationOwnsAnInstanceGraph() {
        val baseContext = ApplicationProvider.getApplicationContext<Context>()
        val first = newApplication(baseContext)
        val second = newApplication(baseContext)
        try {
            first.onCreate()
            second.onCreate()

            assertNotNull(first.appGraph.accountGraphLifecycle)
            assertNotNull(first.appGraph.accountSessionRepository)
            assertNotNull(first.appGraph.database)
            assertNotNull(first.appGraph.preferencesRepository)
            assertSame(first.appGraph.workManagerConfiguration, first.workManagerConfiguration)
            assertSame(first.appGraph.workerFactory, first.workManagerConfiguration.workerFactory)
            assertSame(first.appGraph.logger, first.appGraph.logger)
            assertNotSame(first.appGraph, second.appGraph)
            assertNotSame(first.appGraph.accountSessionRepository, second.appGraph.accountSessionRepository)
        } finally {
            first.appGraph.database.close()
            second.appGraph.database.close()
            first.appGraph.processCoroutineScope.cancel()
            second.appGraph.processCoroutineScope.cancel()
            first.appGraph.imageLoader.shutdown()
            second.appGraph.imageLoader.shutdown()
        }
    }

    @Test
    fun graphFactoryTypeRemainsTheOnlyProcessGraphCreationBoundary() {
        assertNotNull(createGraphFactory<AppGraph.Factory>())
    }

    private fun newApplication(baseContext: Context): MioApplication =
        MioApplication().also { application ->
            val attachBaseContext =
                ContextWrapper::class.java.getDeclaredMethod("attachBaseContext", Context::class.java)
            attachBaseContext.isAccessible = true
            attachBaseContext.invoke(application, baseContext)
        }
}
