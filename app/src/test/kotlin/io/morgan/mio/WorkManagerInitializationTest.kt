package io.morgan.mio

import android.content.ComponentName
import android.content.pm.PackageManager
import androidx.startup.InitializationProvider
import androidx.test.core.app.ApplicationProvider
import androidx.work.WorkManager
import androidx.work.impl.WorkManagerImpl
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = MioApplication::class, sdk = [35])
class WorkManagerInitializationTest {
    @Test
    fun onDemandInitializationUsesAppGraphConfigurationAndRemovesDefaultInitializer() {
        val application = ApplicationProvider.getApplicationContext<MioApplication>()
        val workManager = WorkManager.getInstance(application)

        assertTrue(application.appGraph.workManagerConfiguration === application.workManagerConfiguration)
        assertSame(application.appGraph.workerFactory, workManager.configuration.workerFactory)

        val providerInfo =
            application.packageManager.getProviderInfo(
                ComponentName(application, InitializationProvider::class.java),
                PackageManager.GET_META_DATA,
            )
        assertFalse(providerInfo.metaData?.containsKey("androidx.work.WorkManagerInitializer") == true)

        (workManager as? WorkManagerImpl)?.closeDatabase()
    }
}
