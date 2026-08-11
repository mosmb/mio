package io.morgan.mio

import android.content.Context
import android.content.ContextWrapper
import androidx.test.core.app.ApplicationProvider
import dev.zacsweers.metro.createGraphFactory
import io.morgan.mio.di.AppGraph
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
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

            assertNotNull(first.appGraph.accountGraphManager)
            assertNotNull(first.appGraph.database)
            assertNotNull(first.appGraph.preferencesRepository)
            assertNotSame(first.appGraph, second.appGraph)
        } finally {
            first.appGraph.database.close()
            second.appGraph.database.close()
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
