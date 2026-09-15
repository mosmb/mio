package io.morgan.mio.di

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.zacsweers.metro.createDynamicGraphFactory
import dev.zacsweers.metro.createGraphFactory
import io.morgan.mio.core.testing.provider.providerBindingFixture
import io.morgan.mio.data.account.AccountRefreshRequester
import io.morgan.mio.data.collection.CollectionRefreshRequester
import io.morgan.mio.data.provider.api.ProviderKey
import io.morgan.mio.domain.model.AccountId
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [35])
class AppGraphTest {
    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    @Test
    fun productionGraphConstructionResolvesEveryDeclaredRoot() {
        val graph = createGraphFactory<AppGraph.Factory>().create(context)
        try {
            assertNotNull(graph.accountGraphLifecycle)
            assertNotNull(graph.accountSessionRepository)
            assertNotNull(graph.metroViewModelFactory)
            assertNotNull(graph.database)
            assertNotNull(graph.networkClientFactory)
            assertNotNull(graph.networkMonitor)
            assertNotNull(graph.clock)
            assertNotNull(graph.preferencesRepository)
            assertNotNull(graph.defaultDispatcher)
            assertNotNull(graph.ioDispatcher)
            assertNotNull(graph.mainDispatcher)
            assertNotNull(graph.processCoroutineScope)
            assertNotNull(graph.logger)
            assertNotNull(graph.providerCredentialStore)
            assertNotNull(graph.imageLoader)
            assertNotNull(graph.workerFactory)
            assertNotNull(graph.workManagerConfiguration)
            assertNotNull(graph.pendingSyncWorkDispatcher)
        } finally {
            graph.database.close()
            graph.processCoroutineScope.cancel()
            graph.imageLoader.shutdown()
        }
    }

    @Test
    fun productionGraphAccessorsKeepProcessScopedIdentity() {
        val graph = createGraphFactory<AppGraph.Factory>().create(context)
        try {
            assertSame(graph.accountGraphLifecycle, graph.accountGraphLifecycle)
            assertSame(graph.accountSessionRepository, graph.accountSessionRepository)
            assertSame(graph.metroViewModelFactory, graph.metroViewModelFactory)
            assertSame(graph.database, graph.database)
            assertSame(graph.networkClientFactory, graph.networkClientFactory)
            assertSame(graph.networkMonitor, graph.networkMonitor)
            assertSame(graph.clock, graph.clock)
            assertSame(graph.preferencesRepository, graph.preferencesRepository)
            assertSame(graph.defaultDispatcher, graph.defaultDispatcher)
            assertSame(graph.ioDispatcher, graph.ioDispatcher)
            assertSame(graph.mainDispatcher, graph.mainDispatcher)
            assertSame(graph.processCoroutineScope, graph.processCoroutineScope)
            assertSame(graph.logger, graph.logger)
            assertSame(graph.providerCredentialStore, graph.providerCredentialStore)
            assertSame(graph.imageLoader, graph.imageLoader)
            assertSame(graph.workerFactory, graph.workerFactory)
            assertSame(graph.workManagerConfiguration, graph.workManagerConfiguration)
            assertSame(graph.pendingSyncWorkDispatcher, graph.pendingSyncWorkDispatcher)
        } finally {
            graph.database.close()
            graph.processCoroutineScope.cancel()
            graph.imageLoader.shutdown()
        }
    }

    @Test
    fun dynamicGraphUsesExactReplacementInstances() =
        runTest {
            val fixture = TestGraphFixture(context, testScheduler)
            try {
                val graph =
                    createDynamicGraphFactory<AppGraph.Factory>(fixture.bindings)
                        .create(context)

                assertSame(fixture.database, graph.database)
                assertSame(fixture.clock, graph.clock)
                assertSame(fixture.dispatcher, graph.defaultDispatcher)
                assertSame(fixture.dispatcher, graph.ioDispatcher)
                assertSame(fixture.dispatcher, graph.mainDispatcher)
                assertSame(fixture.processCoroutineScope, graph.processCoroutineScope)
                assertSame(fixture.logger, graph.logger)
                assertSame(fixture.providerCredentialStore, graph.providerCredentialStore)
                assertSame(fixture.imageLoader, graph.imageLoader)
                assertSame(fixture.workerFactory, graph.workerFactory)
                assertSame(fixture.workManagerConfiguration, graph.workManagerConfiguration)
                assertSame(fixture.preferencesRepository, graph.preferencesRepository)
                assertSame(graph.accountSessionRepository, graph.accountSessionRepository)
                assertSame(graph.metroViewModelFactory, graph.metroViewModelFactory)

                val providerBinding = providerBindingFixture(provider = ProviderKey.ANILIST)
                val accountGraph =
                    graph.createAccountGraph(
                        accountId = AccountId("dynamic-account"),
                        providerBinding = providerBinding,
                        accountRefreshRequester = AccountRefreshRequester {},
                        collectionRefreshRequester = CollectionRefreshRequester {},
                    )

                assertSame(fixture.accountRepository, accountGraph.accountRepository)
                assertSame(fixture.mediaRepository, accountGraph.mediaRepository)
                assertSame(fixture.collectionRepository, accountGraph.collectionRepository)
                assertSame(fixture.preferencesRepository, accountGraph.preferencesRepository)
                assertFalse(fixture.dataStoreFile.fileName.toString() == "mio.preferences_pb")
                assertFalse(fixture.dataStoreFile.fileName.toString() == "mio.db")
            } finally {
                fixture.close()
            }
            assertFalse(fixture.processCoroutineScope.isActive)
            assertEquals(1, fixture.imageLoader.shutdownCalls)
        }

    @Test
    fun dynamicStorageReplacementsStayOutsideProductionNamedFiles() =
        runTest {
            val fixture = TestGraphFixture(context, testScheduler)
            try {
                assertEquals("test.preferences_pb", fixture.dataStoreFile.fileName.toString())
                assertFalse(fixture.dataStoreFile.toFile().isFile)
            } finally {
                fixture.close()
            }
        }
}
