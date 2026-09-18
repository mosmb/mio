package io.morgan.mio.di

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.zacsweers.metro.createGraphFactory
import io.morgan.mio.core.testing.provider.FakeProviderAuthenticator
import io.morgan.mio.core.testing.provider.FakeProviderInitialImportDataSource
import io.morgan.mio.core.testing.provider.providerBindingFixture
import io.morgan.mio.data.provider.api.ProviderBinding
import io.morgan.mio.data.provider.api.ProviderBindingResolver
import io.morgan.mio.data.provider.api.ProviderKey
import io.morgan.mio.domain.account.ConnectedProvider
import io.morgan.mio.domain.model.AccountId
import kotlinx.coroutines.cancel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [35])
class AccountGraphLifecycleTest {
    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    @Test
    fun createReplaceDestroyAndRecreateManageOneActiveGraph() {
        val appGraph = createGraphFactory<AppGraph.Factory>().create(context)
        try {
            val lifecycle = appGraph.accountGraphLifecycle
            assertNull(lifecycle.activeHandle())

            val anilistBinding = providerBindingFixture(provider = ProviderKey.ANILIST)
            val anilistRequest =
                request(
                    providerBinding = anilistBinding,
                    accountId = "anilist-account",
                )
            val first = lifecycle.create(anilistRequest)
            assertSame(first, lifecycle.activeHandle())
            first.withGraph { graph ->
                assertEquals(anilistRequest.accountId, graph.accountId)
                assertSame(anilistBinding, graph.providerBinding)
                assertSame(graph.accountRepository, graph.accountRepository)
                assertSame(graph.mediaRepository, graph.mediaRepository)
                assertSame(graph.collectionRepository, graph.collectionRepository)
                assertTrue(
                    "io.morgan.mio.feature.today.impl.TodayViewModel" in
                        graph.metroViewModelFactory.providerKeys("getViewModelProviders"),
                )
                assertTrue(
                    "io.morgan.mio.feature.discover.impl.DiscoverViewModel" in
                        graph.metroViewModelFactory.providerKeys("getViewModelProviders"),
                )
                assertTrue(
                    "io.morgan.mio.feature.media.impl.MediaDetailViewModel.Factory" in
                        graph.metroViewModelFactory.providerKeys("getManualAssistedFactoryProviders"),
                )
                assertTrue(
                    "io.morgan.mio.feature.library.impl.LibraryViewModel.Factory" in
                        graph.metroViewModelFactory.providerKeys("getManualAssistedFactoryProviders"),
                )
            }

            assertThrows(IllegalStateException::class.java) {
                lifecycle.create(request(anilistBinding, "second-account"))
            }
            assertSame(first, lifecycle.activeHandle())

            val myAnimeListBinding = providerBindingFixture(provider = ProviderKey.MY_ANIME_LIST)
            val replacementRequest = request(myAnimeListBinding, "myanimelist-account")
            val second = lifecycle.replace(replacementRequest)
            assertNotSame(first, second)
            assertSame(second, lifecycle.activeHandle())
            assertThrows(IllegalStateException::class.java) {
                first.withGraph { }
            }
            second.withGraph { graph ->
                assertEquals(replacementRequest.accountId, graph.accountId)
                assertSame(myAnimeListBinding, graph.providerBinding)
                assertSame(appGraph.preferencesRepository, graph.preferencesRepository)
            }

            assertSame(appGraph.database, appGraph.database)
            assertSame(appGraph.clock, appGraph.clock)
            assertSame(appGraph.defaultDispatcher, appGraph.defaultDispatcher)
            assertSame(appGraph.ioDispatcher, appGraph.ioDispatcher)
            assertSame(appGraph.mainDispatcher, appGraph.mainDispatcher)
            assertSame(appGraph.networkClientFactory, appGraph.networkClientFactory)
            assertSame(appGraph.networkMonitor, appGraph.networkMonitor)

            val ordinaryOne =
                appGraph.createAccountGraph(
                    AccountId("ordinary-one"),
                    providerBindingFixture(provider = ProviderKey.ANILIST),
                )
            val ordinaryTwo =
                appGraph.createAccountGraph(
                    AccountId("ordinary-two"),
                    providerBindingFixture(provider = ProviderKey.ANILIST),
                )
            assertNotSame(ordinaryOne.accountRepository, ordinaryTwo.accountRepository)
            assertNotSame(ordinaryOne.mediaRepository, ordinaryTwo.mediaRepository)
            assertNotSame(ordinaryOne.collectionRepository, ordinaryTwo.collectionRepository)
            assertSame(ordinaryOne.preferencesRepository, ordinaryTwo.preferencesRepository)

            val anilistAuthenticator = anilistBinding.authenticator as FakeProviderAuthenticator
            val anilistImport = anilistBinding.initialImportDataSource as FakeProviderInitialImportDataSource
            second.withGraph { graph ->
                graph.accountRepository.observeSelectedAccount()
                graph.mediaRepository.observeMedia(
                    io.morgan.mio.domain.model
                        .MediaId("media"),
                )
                graph.collectionRepository.observeCollection(replacementRequest.accountId)
                graph.preferencesRepository.observeDevicePreferences()
            }
            assertTrue(anilistAuthenticator.beginRequests.isEmpty())
            assertTrue(anilistAuthenticator.completeCalls.isEmpty())
            assertTrue(anilistAuthenticator.refreshCredentials.isEmpty())
            assertTrue(anilistImport.calls.isEmpty())

            lifecycle.destroy()
            assertNull(lifecycle.activeHandle())
            assertThrows(IllegalStateException::class.java) {
                second.withGraph { }
            }
            lifecycle.destroy()
            val recreated = lifecycle.create(request(anilistBinding, "recreated-account"))
            assertSame(recreated, lifecycle.activeHandle())
            lifecycle.destroy()
        } finally {
            appGraph.database.close()
            appGraph.processCoroutineScope.cancel()
            appGraph.imageLoader.shutdown()
        }
    }

    @Test
    fun failedReplacementLeavesThePreviousHandleActive() {
        val appGraph = createGraphFactory<AppGraph.Factory>().create(context)
        try {
            var fail = false
            val expected = IllegalStateException("factory failed")
            val lifecycle =
                AccountGraphLifecycle(
                    AccountGraph.Factory {
                        accountId,
                        providerBinding,
                        ->
                        if (fail) throw expected
                        appGraph.createAccountGraph(
                            accountId,
                            providerBinding,
                        )
                    },
                )
            val binding = providerBindingFixture()
            val first = lifecycle.create(request(binding, "stable-account"))
            fail = true

            val failure =
                assertThrows(IllegalStateException::class.java) {
                    lifecycle.replace(request(binding, "replacement-account"))
                }
            assertSame(expected, failure)
            assertSame(first, lifecycle.activeHandle())
            assertEquals(AccountId("stable-account"), first.withGraph { graph -> graph.accountId })
            lifecycle.destroy()
        } finally {
            appGraph.database.close()
            appGraph.processCoroutineScope.cancel()
            appGraph.imageLoader.shutdown()
        }
    }

    @Test
    fun authenticatedSessionActivationIsIdempotentAndReplacesOnlyForAnotherAccount() {
        val appGraph = createGraphFactory<AppGraph.Factory>().create(context)
        try {
            val aniListBinding = providerBindingFixture(ProviderKey.ANILIST)
            val myAnimeListBinding = providerBindingFixture(ProviderKey.MY_ANIME_LIST)
            val activator =
                MetroAuthenticatedAccountSessionActivator(
                    accountGraphLifecycle = appGraph.accountGraphLifecycle,
                    bindingResolver =
                        ProviderBindingResolver { provider ->
                            when (provider) {
                                ProviderKey.ANILIST -> aniListBinding
                                ProviderKey.MY_ANIME_LIST -> myAnimeListBinding
                            }
                        },
                )

            activator.activate(AccountId("account-a"), ConnectedProvider.ANILIST)
            val first = appGraph.accountGraphLifecycle.activeHandle()
            activator.activate(AccountId("account-a"), ConnectedProvider.ANILIST)
            assertSame(first, appGraph.accountGraphLifecycle.activeHandle())
            first?.withGraph { graph -> assertSame(aniListBinding, graph.providerBinding) }

            activator.activate(AccountId("account-b"), ConnectedProvider.MY_ANIME_LIST)
            val second = appGraph.accountGraphLifecycle.activeHandle()
            assertNotSame(first, second)
            assertThrows(IllegalStateException::class.java) { first?.withGraph { } }
            second?.withGraph { graph ->
                assertEquals(AccountId("account-b"), graph.accountId)
                assertSame(myAnimeListBinding, graph.providerBinding)
            }

            activator.destroy()
            assertNull(appGraph.accountGraphLifecycle.activeHandle())
        } finally {
            appGraph.database.close()
            appGraph.processCoroutineScope.cancel()
            appGraph.imageLoader.shutdown()
        }
    }

    private fun request(
        providerBinding: ProviderBinding,
        accountId: String,
    ): AccountGraphRequest =
        AccountGraphRequest(
            accountId = AccountId(accountId),
            providerBinding = providerBinding,
        )
}

@Suppress("UNCHECKED_CAST")
private fun dev.zacsweers.metrox.viewmodel.MetroViewModelFactory.providerKeys(getterName: String): Set<String> {
    val getter =
        dev.zacsweers.metrox.viewmodel.MetroViewModelFactory::class.java
            .getDeclaredMethod(getterName)
            .apply { isAccessible = true }
    val providers = getter.invoke(this) as Map<kotlin.reflect.KClass<*>, *>
    return providers.keys.mapNotNull { key -> key.qualifiedName }.toSet()
}
