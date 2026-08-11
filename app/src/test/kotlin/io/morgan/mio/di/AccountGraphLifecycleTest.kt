package io.morgan.mio.di

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.zacsweers.metro.createGraphFactory
import io.morgan.mio.core.testing.provider.FakeProviderAuthenticator
import io.morgan.mio.core.testing.provider.FakeProviderInitialImportDataSource
import io.morgan.mio.core.testing.provider.providerBindingFixture
import io.morgan.mio.data.account.AccountRefreshRequester
import io.morgan.mio.data.collection.CollectionRefreshRequester
import io.morgan.mio.data.media.MediaRefreshRequester
import io.morgan.mio.data.provider.api.ProviderBinding
import io.morgan.mio.data.provider.api.ProviderKey
import io.morgan.mio.domain.model.AccountId
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
            val manager = appGraph.accountGraphManager
            assertNull(manager.activeHandle())

            val anilistBinding = providerBindingFixture(provider = ProviderKey.ANILIST)
            val accountRefreshCalls = mutableListOf<Unit>()
            val mediaRefreshCalls = mutableListOf<Unit>()
            val collectionRefreshCalls = mutableListOf<Unit>()
            val anilistRequest =
                request(
                    providerBinding = anilistBinding,
                    accountId = "anilist-account",
                    accountRefreshCalls = accountRefreshCalls,
                    mediaRefreshCalls = mediaRefreshCalls,
                    collectionRefreshCalls = collectionRefreshCalls,
                )
            val first = manager.create(anilistRequest)
            assertSame(first, manager.activeHandle())
            first.withGraph { graph ->
                assertEquals(anilistRequest.accountId, graph.accountId)
                assertSame(anilistBinding, graph.providerBinding)
                assertSame(graph.accountRepository, graph.accountRepository)
                assertSame(graph.mediaRepository, graph.mediaRepository)
                assertSame(graph.collectionRepository, graph.collectionRepository)
            }

            assertThrows(IllegalStateException::class.java) {
                manager.create(request(anilistBinding, "second-account"))
            }
            assertSame(first, manager.activeHandle())

            val myAnimeListBinding = providerBindingFixture(provider = ProviderKey.MY_ANIME_LIST)
            val replacementRequest = request(myAnimeListBinding, "myanimelist-account")
            val second = manager.replace(replacementRequest)
            assertNotSame(first, second)
            assertSame(second, manager.activeHandle())
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
                    AccountRefreshRequester {},
                    MediaRefreshRequester {},
                    CollectionRefreshRequester {},
                )
            val ordinaryTwo =
                appGraph.createAccountGraph(
                    AccountId("ordinary-two"),
                    providerBindingFixture(provider = ProviderKey.ANILIST),
                    AccountRefreshRequester {},
                    MediaRefreshRequester {},
                    CollectionRefreshRequester {},
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
            assertTrue(accountRefreshCalls.isEmpty())
            assertTrue(mediaRefreshCalls.isEmpty())
            assertTrue(collectionRefreshCalls.isEmpty())

            manager.destroy()
            assertNull(manager.activeHandle())
            assertThrows(IllegalStateException::class.java) {
                second.withGraph { }
            }
            manager.destroy()
            val recreated = manager.create(request(anilistBinding, "recreated-account"))
            assertSame(recreated, manager.activeHandle())
            manager.destroy()
        } finally {
            appGraph.database.close()
        }
    }

    @Test
    fun failedReplacementLeavesThePreviousHandleActive() {
        val appGraph = createGraphFactory<AppGraph.Factory>().create(context)
        try {
            var fail = false
            val expected = IllegalStateException("factory failed")
            val manager =
                AccountGraphManager(
                    AccountGraph.Factory {
                        accountId,
                        providerBinding,
                        accountRefreshRequester,
                        mediaRefreshRequester,
                        collectionRefreshRequester,
                        ->
                        if (fail) throw expected
                        appGraph.createAccountGraph(
                            accountId,
                            providerBinding,
                            accountRefreshRequester,
                            mediaRefreshRequester,
                            collectionRefreshRequester,
                        )
                    },
                )
            val binding = providerBindingFixture()
            val first = manager.create(request(binding, "stable-account"))
            fail = true

            val failure =
                assertThrows(IllegalStateException::class.java) {
                    manager.replace(request(binding, "replacement-account"))
                }
            assertSame(expected, failure)
            assertSame(first, manager.activeHandle())
            assertEquals(AccountId("stable-account"), first.withGraph { graph -> graph.accountId })
            manager.destroy()
        } finally {
            appGraph.database.close()
        }
    }

    private fun request(
        providerBinding: ProviderBinding,
        accountId: String,
        accountRefreshCalls: MutableList<Unit> = mutableListOf(),
        mediaRefreshCalls: MutableList<Unit> = mutableListOf(),
        collectionRefreshCalls: MutableList<Unit> = mutableListOf(),
    ): AccountGraphRequest =
        AccountGraphRequest(
            accountId = AccountId(accountId),
            providerBinding = providerBinding,
            accountRefreshRequester = AccountRefreshRequester { accountRefreshCalls += Unit },
            mediaRefreshRequester = MediaRefreshRequester { mediaRefreshCalls += Unit },
            collectionRefreshRequester = CollectionRefreshRequester { collectionRefreshCalls += Unit },
        )
}
