package io.morgan.mio.data.account

import androidx.test.core.app.ApplicationProvider
import io.morgan.mio.core.database.MioDatabase
import io.morgan.mio.core.database.MioDatabaseFactory
import io.morgan.mio.core.testing.provider.FakeProviderAuthenticator
import io.morgan.mio.core.testing.provider.FakeProviderCredentialStore
import io.morgan.mio.core.testing.provider.FakeProviderIdentityDataSource
import io.morgan.mio.core.testing.provider.FakeProviderInitialImportDataSource
import io.morgan.mio.core.testing.provider.completeProviderImportFixture
import io.morgan.mio.core.testing.provider.providerAuthorizationLaunchFixture
import io.morgan.mio.core.testing.provider.providerBindingFixture
import io.morgan.mio.core.testing.provider.providerCredentialsFixture
import io.morgan.mio.core.testing.provider.providerIdentityFixture
import io.morgan.mio.data.account.local.RoomAccountLocalDataSource
import io.morgan.mio.data.account.local.RoomInitialImportLocalDataSource
import io.morgan.mio.data.provider.api.ProviderBinding
import io.morgan.mio.data.provider.api.ProviderBindingResolver
import io.morgan.mio.data.provider.api.ProviderFailure
import io.morgan.mio.data.provider.api.ProviderKey
import io.morgan.mio.data.provider.api.ProviderResult
import io.morgan.mio.domain.account.AccountAuthorizationRedirect
import io.morgan.mio.domain.account.AccountSessionFailureReason
import io.morgan.mio.domain.account.AccountSessionOperation
import io.morgan.mio.domain.account.AccountSessionState
import io.morgan.mio.domain.account.ConnectedProvider
import io.morgan.mio.domain.model.AccountId
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [35])
class ProductionAccountSessionRepositoryTest {
    private lateinit var database: MioDatabase
    private lateinit var credentialStore: FakeProviderCredentialStore
    private lateinit var activator: RecordingAccountSessionActivator

    @Before
    fun setUp() {
        database = MioDatabaseFactory.createInMemory(ApplicationProvider.getApplicationContext())
        credentialStore = FakeProviderCredentialStore()
        activator = RecordingAccountSessionActivator()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun eachProviderUsesOnlyItsSelectedBindingAndAuthenticatesAfterTheAtomicImport() =
        runTest {
            ProviderKey.entries.forEach { providerKey ->
                database.clearAllTables()
                credentialStore = FakeProviderCredentialStore()
                activator = RecordingAccountSessionActivator()
                val selected = configuredBinding(providerKey)
                val other = providerBindingFixture(providerKey.other())
                val resolver = RecordingBindingResolver(mapOf(providerKey to selected, providerKey.other() to other))
                val repository = repository(resolver)

                repository.restore()
                val launch = repository.startConnection(providerKey.toConnectedProvider())
                repository.completeAuthorization(
                    AccountAuthorizationRedirect("mio://oauth/callback?code=fixture-sensitive-value"),
                )

                val authenticated = repository.state.value as AccountSessionState.Authenticated
                assertEquals(providerKey.toConnectedProvider(), authenticated.provider)
                assertEquals("https://auth.invalid/authorize", launch?.authorizationUri)
                assertEquals(listOf(providerKey), resolver.requestedProviders)
                assertEquals(1, (selected.authenticator as FakeProviderAuthenticator).beginRequests.size)
                assertEquals(
                    1,
                    selected.identityDataSource
                        .let { it as FakeProviderIdentityDataSource }
                        .credentials.size,
                )
                assertEquals(
                    1,
                    selected.initialImportDataSource
                        .let { it as FakeProviderInitialImportDataSource }
                        .calls.size,
                )
                assertTrue((other.authenticator as FakeProviderAuthenticator).beginRequests.isEmpty())
                assertEquals(listOf(providerKey), credentialStore.writeProviders)
                assertEquals(listOf(Activation(authenticated.accountId, authenticated.provider)), activator.activations)
                assertEquals(
                    authenticated.accountId.value,
                    database
                        .selectedAccountDao()
                        .observeSelectedAccount()
                        .first()
                        ?.accountId,
                )
                assertEquals(1, rowCount("collection_entry"))
                assertFalse(
                    repository.state.value
                        .toString()
                        .contains("fixture-sensitive-value"),
                )
            }
        }

    @Test
    fun failedImportKeepsOnboardingAndLeavesNoCredentialGraphOrCommittedAccount() =
        runTest {
            ProviderKey.entries.forEach { providerKey ->
                database.clearAllTables()
                credentialStore = FakeProviderCredentialStore()
                activator = RecordingAccountSessionActivator()
                val binding = configuredBinding(providerKey, importFailure = ProviderFailure.ServiceUnavailable)
                val repository = repository(RecordingBindingResolver(mapOf(providerKey to binding)))

                repository.restore()
                repository.startConnection(providerKey.toConnectedProvider())
                repository.completeAuthorization(AccountAuthorizationRedirect("mio://oauth/callback?code=fixture"))

                assertEquals(
                    AccountSessionState.Failed(
                        provider = providerKey.toConnectedProvider(),
                        operation = AccountSessionOperation.INITIAL_IMPORT,
                        reason = AccountSessionFailureReason.PROVIDER_FAILURE,
                        retryable = true,
                    ),
                    repository.state.value,
                )
                assertEquals(0, credentialStore.writeCount)
                assertTrue(activator.activations.isEmpty())
                assertNull(database.selectedAccountDao().observeSelectedAccount().first())
                assertEquals(0, rowCount("collection_entry"))
            }
        }

    @Test
    fun cancellationDuringImportReturnsToSignedOutAndLeavesNoTentativeState() =
        runTest {
            ProviderKey.entries.forEach { providerKey ->
                database.clearAllTables()
                credentialStore = FakeProviderCredentialStore()
                activator = RecordingAccountSessionActivator()
                val releaseImport = CompletableDeferred<Unit>()
                val binding = configuredBinding(providerKey, releaseImport = releaseImport)
                val repository = repository(RecordingBindingResolver(mapOf(providerKey to binding)))
                repository.restore()
                repository.startConnection(providerKey.toConnectedProvider())

                val completion =
                    async {
                        repository.completeAuthorization(
                            AccountAuthorizationRedirect("mio://oauth/callback?code=fixture"),
                        )
                    }
                testScheduler.runCurrent()
                assertEquals(
                    AccountSessionState.Importing(providerKey.toConnectedProvider()),
                    repository.state.value,
                )
                completion.cancelAndJoin()

                assertEquals(AccountSessionState.SignedOut, repository.state.value)
                assertEquals(0, credentialStore.writeCount)
                assertTrue(activator.activations.isEmpty())
                assertNull(database.selectedAccountDao().observeSelectedAccount().first())
            }
        }

    @Test
    fun committedSessionRestoresItsProviderGraphWithoutRepeatingProviderWork() =
        runTest {
            ProviderKey.entries.forEach { providerKey ->
                database.clearAllTables()
                credentialStore = FakeProviderCredentialStore()
                activator = RecordingAccountSessionActivator()
                val binding = configuredBinding(providerKey)
                val resolver = RecordingBindingResolver(mapOf(providerKey to binding))
                val first = repository(resolver)
                first.restore()
                first.startConnection(providerKey.toConnectedProvider())
                first.completeAuthorization(AccountAuthorizationRedirect("mio://oauth/callback?code=fixture"))
                val committed = first.state.value as AccountSessionState.Authenticated
                val providerCalls = providerCallCount(binding)

                activator = RecordingAccountSessionActivator()
                val restored = repository(resolver)
                restored.restore()

                assertEquals(committed, restored.state.value)
                assertEquals(providerCalls, providerCallCount(binding))
                assertEquals(listOf(Activation(committed.accountId, committed.provider)), activator.activations)
                assertEquals(listOf(providerKey), credentialStore.readProviders)
            }
        }

    @Test
    fun unavailableProviderAndLaunchFailureStayInlineAndRetryableOnlyWhenAppropriate() =
        runTest {
            val unavailable = repository(RecordingBindingResolver(emptyMap()))
            unavailable.restore()
            assertNull(unavailable.startConnection(ConnectedProvider.ANILIST))
            assertEquals(
                AccountSessionFailureReason.UNAVAILABLE,
                (unavailable.state.value as AccountSessionState.Failed).reason,
            )
            assertFalse((unavailable.state.value as AccountSessionState.Failed).retryable)

            val binding = configuredBinding(ProviderKey.ANILIST)
            val repository = repository(RecordingBindingResolver(mapOf(ProviderKey.ANILIST to binding)))
            repository.restore()
            repository.startConnection(ConnectedProvider.ANILIST)
            repository.failAuthorizationLaunch()
            val failed = repository.state.value as AccountSessionState.Failed
            assertEquals(AccountSessionFailureReason.AUTHORIZATION_LAUNCH_FAILED, failed.reason)
            assertTrue(failed.retryable)
        }

    @Test
    fun concurrentConnectionAndOutOfOrderRedirectCannotCreateASecondSession() =
        runTest {
            val releaseLaunch = CompletableDeferred<Unit>()
            val binding = configuredBinding(ProviderKey.ANILIST, releaseLaunch = releaseLaunch)
            val repository = repository(RecordingBindingResolver(mapOf(ProviderKey.ANILIST to binding)))
            repository.restore()

            val first = async { repository.startConnection(ConnectedProvider.ANILIST) }
            testScheduler.runCurrent()
            val second = async { repository.startConnection(ConnectedProvider.MY_ANIME_LIST) }
            testScheduler.runCurrent()
            releaseLaunch.complete(Unit)

            assertTrue(first.await() != null)
            assertNull(second.await())
            assertEquals(1, (binding.authenticator as FakeProviderAuthenticator).beginRequests.size)
        }

    private fun repository(resolver: ProviderBindingResolver): ProductionAccountSessionRepository =
        ProductionAccountSessionRepository(
            bindingResolver = resolver,
            credentialStore = credentialStore,
            initialImportWriter = InitialImportWriter(RoomInitialImportLocalDataSource(database)),
            accountLocalDataSource = RoomAccountLocalDataSource(database),
            accountSessionActivator = activator,
        )

    private fun configuredBinding(
        provider: ProviderKey,
        importFailure: ProviderFailure? = null,
        releaseLaunch: CompletableDeferred<Unit>? = null,
        releaseImport: CompletableDeferred<Unit>? = null,
    ): ProviderBinding {
        val binding = providerBindingFixture(provider)
        val authenticator = binding.authenticator as FakeProviderAuthenticator
        val identity = binding.identityDataSource as FakeProviderIdentityDataSource
        val initialImport = binding.initialImportDataSource as FakeProviderInitialImportDataSource
        authenticator.enqueueBeginAuthorization {
            releaseLaunch?.await()
            ProviderResult.Success(providerAuthorizationLaunchFixture())
        }
        authenticator.enqueueCompleteAuthorization { _, _ ->
            ProviderResult.Success(providerCredentialsFixture(provider))
        }
        identity.enqueue { ProviderResult.Success(providerIdentityFixture(provider)) }
        initialImport.enqueue { _, accountId ->
            releaseImport?.await()
            if (importFailure == null) {
                ProviderResult.Success(completeProviderImportFixture(provider = provider, accountId = accountId))
            } else {
                ProviderResult.Failure(importFailure)
            }
        }
        return binding
    }

    private fun providerCallCount(binding: ProviderBinding): Int =
        with(binding.authenticator as FakeProviderAuthenticator) {
            beginRequests.size + completeCalls.size
        } +
            (binding.identityDataSource as FakeProviderIdentityDataSource).credentials.size +
            (binding.initialImportDataSource as FakeProviderInitialImportDataSource).calls.size

    private fun rowCount(table: String): Int =
        database.openHelper.readableDatabase
            .query("SELECT COUNT(*) FROM $table")
            .use { cursor ->
                cursor.moveToFirst()
                cursor.getInt(0)
            }
}

private data class Activation(
    val accountId: AccountId,
    val provider: ConnectedProvider,
)

private class RecordingAccountSessionActivator : AuthenticatedAccountSessionActivator {
    val activations = mutableListOf<Activation>()
    var destroyCalls = 0

    override fun activate(
        accountId: AccountId,
        provider: ConnectedProvider,
    ) {
        activations += Activation(accountId, provider)
    }

    override fun destroy() {
        destroyCalls += 1
    }
}

private class RecordingBindingResolver(
    private val bindings: Map<ProviderKey, ProviderBinding>,
) : ProviderBindingResolver {
    val requestedProviders = mutableListOf<ProviderKey>()

    override fun bindingFor(provider: ProviderKey): ProviderBinding? {
        requestedProviders += provider
        return bindings[provider]
    }
}

private fun ProviderKey.toConnectedProvider(): ConnectedProvider =
    when (this) {
        ProviderKey.ANILIST -> ConnectedProvider.ANILIST
        ProviderKey.MY_ANIME_LIST -> ConnectedProvider.MY_ANIME_LIST
    }

private fun ProviderKey.other(): ProviderKey =
    when (this) {
        ProviderKey.ANILIST -> ProviderKey.MY_ANIME_LIST
        ProviderKey.MY_ANIME_LIST -> ProviderKey.ANILIST
    }
