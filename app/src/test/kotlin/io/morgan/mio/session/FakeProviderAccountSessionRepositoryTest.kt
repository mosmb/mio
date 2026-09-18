package io.morgan.mio.session

import io.morgan.mio.core.testing.provider.FakeProviderAuthenticator
import io.morgan.mio.core.testing.provider.FakeProviderIdentityDataSource
import io.morgan.mio.core.testing.provider.FakeProviderInitialImportDataSource
import io.morgan.mio.core.testing.provider.FakeProviderMediaDetailDataSource
import io.morgan.mio.core.testing.provider.FakeProviderMediaSearchDataSource
import io.morgan.mio.core.testing.provider.FakeProviderSyncDataSource
import io.morgan.mio.core.testing.provider.completeProviderImportFixture
import io.morgan.mio.core.testing.provider.providerAuthorizationLaunchFixture
import io.morgan.mio.core.testing.provider.providerCredentialsFixture
import io.morgan.mio.core.testing.provider.providerIdentityFixture
import io.morgan.mio.data.provider.api.ProviderAccountId
import io.morgan.mio.data.provider.api.ProviderBinding
import io.morgan.mio.data.provider.api.ProviderFailure
import io.morgan.mio.data.provider.api.ProviderKey
import io.morgan.mio.data.provider.api.ProviderResult
import io.morgan.mio.domain.account.AccountAuthorizationRedirect
import io.morgan.mio.domain.account.AccountSessionFailureReason
import io.morgan.mio.domain.account.AccountSessionOperation
import io.morgan.mio.domain.account.AccountSessionState
import io.morgan.mio.domain.account.ConnectedProvider
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class FakeProviderAccountSessionRepositoryTest {
    @Test
    fun bothProvidersRunLaunchRedirectIdentityAndImportBeforeAuthentication() =
        runTest {
            ProviderKey.entries.forEach { providerKey ->
                val repository = configuredRepository(providerKey)
                val provider = providerKey.toConnectedProvider()

                repository.restore()
                val launch = repository.startConnection(provider)
                repository.completeAuthorization(AccountAuthorizationRedirect("mio://callback?code=fixture-code"))

                assertEquals("https://auth.invalid/authorize", launch?.authorizationUri)
                assertEquals("mio://task12/callback", repository.lastAuthorizationRequest?.redirectUri)
                assertEquals(
                    AccountSessionState.Authenticated(
                        accountId =
                            io.morgan.mio.domain.model
                                .AccountId("fixture-${providerKey.name.lowercase()}"),
                        provider = provider,
                    ),
                    repository.state.value,
                )
                assertEquals(1, repository.providerCredentialStore.writeCount)
                assertEquals(
                    listOf(
                        FakeProviderAccountSessionRepository.Operation.Restore,
                        FakeProviderAccountSessionRepository.Operation.Start(provider),
                        FakeProviderAccountSessionRepository.Operation.Complete,
                        FakeProviderAccountSessionRepository.Operation.Import,
                    ),
                    repository.operations,
                )
                assertFalse(launch.toString().contains("fixture-state"))
                assertFalse(repository.lastRedirect.toString().contains("fixture-code"))
            }
        }

    @Test
    fun completedSessionRestoresWithoutRepeatingProviderWork() =
        runTest {
            val repository = configuredRepository(ProviderKey.ANILIST)
            repository.restore()
            repository.startConnection(ConnectedProvider.ANILIST)
            repository.completeAuthorization(AccountAuthorizationRedirect("mio://callback"))
            val completed = repository.state.value
            val providerWorkCount = repository.operations.size

            val restored =
                FakeProviderAccountSessionRepository(
                    providerBinding = repository.providerBinding,
                    providerCredentialStore = repository.providerCredentialStore,
                    sessionStore = repository.sessionStore,
                )
            restored.restore()

            assertEquals(completed, restored.state.value)
            assertEquals(listOf(FakeProviderAccountSessionRepository.Operation.Restore), restored.operations)
            assertEquals(providerWorkCount, repository.operations.size)
        }

    @Test
    fun launchFailureCanRetryAndDoesNotStrandAuthorizingState() =
        runTest {
            val repository = configuredRepository(ProviderKey.ANILIST)
            repository.restore()
            repository.startConnection(ConnectedProvider.ANILIST)
            repository.failAuthorizationLaunch()

            assertEquals(
                AccountSessionState.Failed(
                    provider = ConnectedProvider.ANILIST,
                    operation = AccountSessionOperation.AUTHORIZATION,
                    reason = AccountSessionFailureReason.AUTHORIZATION_LAUNCH_FAILED,
                    retryable = true,
                ),
                repository.state.value,
            )

            assertTrue(repository.startConnection(ConnectedProvider.ANILIST) != null)
        }

    @Test
    fun concurrentAndAuthenticatedConnectionsCannotStartASecondProvider() =
        runTest {
            val authenticator = FakeProviderAuthenticator(ProviderKey.ANILIST)
            val identity = FakeProviderIdentityDataSource(ProviderKey.ANILIST)
            val initialImport = FakeProviderInitialImportDataSource(ProviderKey.ANILIST)
            val mediaDetails = FakeProviderMediaDetailDataSource(ProviderKey.ANILIST)
            val sync = FakeProviderSyncDataSource(ProviderKey.ANILIST)
            val release = kotlinx.coroutines.CompletableDeferred<Unit>()
            authenticator.enqueueBeginAuthorization {
                release.await()
                ProviderResult.Success(providerAuthorizationLaunchFixture())
            }
            authenticator.enqueueCompleteAuthorization { _, _ -> ProviderResult.Success(providerCredentialsFixture()) }
            identity.enqueue { ProviderResult.Success(providerIdentityFixture()) }
            initialImport.enqueue { _, accountId ->
                ProviderResult.Success(completeProviderImportFixture(accountId = accountId))
            }
            val repository =
                FakeProviderAccountSessionRepository(
                    providerBinding =
                        ProviderBinding(
                            provider = ProviderKey.ANILIST,
                            authenticator = authenticator,
                            identityDataSource = identity,
                            initialImportDataSource = initialImport,
                            mediaDetailDataSource = mediaDetails,
                            mediaSearchDataSource = FakeProviderMediaSearchDataSource(ProviderKey.ANILIST),
                            syncDataSource = sync,
                        ),
                )
            repository.restore()

            val first = async { repository.startConnection(ConnectedProvider.ANILIST) }
            testScheduler.runCurrent()
            val second = async { repository.startConnection(ConnectedProvider.MY_ANIME_LIST) }
            testScheduler.runCurrent()
            release.complete(Unit)

            assertTrue(first.await() != null)
            assertNull(second.await())
            assertEquals(1, authenticator.beginRequests.size)

            repository.completeAuthorization(AccountAuthorizationRedirect("mio://callback"))
            try {
                repository.startConnection(ConnectedProvider.MY_ANIME_LIST)
                fail("Expected a completed session to reject a second provider")
            } catch (_: IllegalStateException) {
                // Expected: an authenticated session must be disconnected first.
            }
        }

    @Test
    fun redirectOutsideAuthorizationIsRejectedWithoutEchoingTheUri() =
        runTest {
            val repository = configuredRepository(ProviderKey.ANILIST)
            repository.restore()
            val rawRedirect = "mio://callback?code=secret-code"

            val failure =
                try {
                    repository.completeAuthorization(AccountAuthorizationRedirect(rawRedirect))
                    fail("Expected a redirect outside authorization to be rejected")
                    error("unreachable")
                } catch (failure: IllegalStateException) {
                    failure
                }

            assertFalse(failure.message.orEmpty().contains(rawRedirect))
            assertTrue(repository.operations.none { it is FakeProviderAccountSessionRepository.Operation.Complete })
        }

    @Test
    fun bothProvidersRetryAfterAuthenticationFailure() =
        runTest {
            ProviderKey.entries.forEach { providerKey ->
                val provider = providerKey.toConnectedProvider()
                val authenticator = FakeProviderAuthenticator(providerKey)
                val identity = FakeProviderIdentityDataSource(providerKey)
                val initialImport = FakeProviderInitialImportDataSource(providerKey)
                repeat(2) {
                    authenticator.enqueueBeginAuthorization {
                        ProviderResult.Success(providerAuthorizationLaunchFixture())
                    }
                }
                authenticator.enqueueCompleteAuthorization {
                    _,
                    _,
                    ->
                    ProviderResult.Failure(ProviderFailure.Timeout)
                }
                authenticator.enqueueCompleteAuthorization {
                    _,
                    _,
                    ->
                    ProviderResult.Success(providerCredentialsFixture(providerKey))
                }
                identity.enqueue { ProviderResult.Success(providerIdentityFixture(provider = providerKey)) }
                initialImport.enqueue { _, accountId ->
                    ProviderResult.Success(
                        completeProviderImportFixture(provider = providerKey, accountId = accountId),
                    )
                }
                val repository = repository(providerKey, authenticator, identity, initialImport)

                repository.restore()
                repository.startConnection(provider)
                repository.completeAuthorization(AccountAuthorizationRedirect("mio://callback?code=first"))
                assertEquals(
                    AccountSessionState.Failed(
                        provider = provider,
                        operation = AccountSessionOperation.AUTHENTICATION,
                        reason = AccountSessionFailureReason.PROVIDER_FAILURE,
                        retryable = true,
                    ),
                    repository.state.value,
                )
                assertEquals(0, repository.providerCredentialStore.writeCount)

                assertTrue(repository.startConnection(provider) != null)
                repository.completeAuthorization(AccountAuthorizationRedirect("mio://callback?code=second"))
                assertTrue(repository.state.value is AccountSessionState.Authenticated)
            }
        }

    @Test
    fun bothProvidersRetryAfterInitialImportFailure() =
        runTest {
            ProviderKey.entries.forEach { providerKey ->
                val provider = providerKey.toConnectedProvider()
                val authenticator = FakeProviderAuthenticator(providerKey)
                val identity = FakeProviderIdentityDataSource(providerKey)
                val initialImport = FakeProviderInitialImportDataSource(providerKey)
                repeat(2) {
                    authenticator.enqueueBeginAuthorization {
                        ProviderResult.Success(providerAuthorizationLaunchFixture())
                    }
                    authenticator.enqueueCompleteAuthorization {
                        _,
                        _,
                        ->
                        ProviderResult.Success(providerCredentialsFixture(providerKey))
                    }
                    identity.enqueue { ProviderResult.Success(providerIdentityFixture(provider = providerKey)) }
                }
                initialImport.enqueue { _, _ -> ProviderResult.Failure(ProviderFailure.ServiceUnavailable) }
                initialImport.enqueue { _, accountId ->
                    ProviderResult.Success(
                        completeProviderImportFixture(provider = providerKey, accountId = accountId),
                    )
                }
                val repository = repository(providerKey, authenticator, identity, initialImport)

                repository.restore()
                repository.startConnection(provider)
                repository.completeAuthorization(AccountAuthorizationRedirect("mio://callback?code=first"))
                assertEquals(
                    AccountSessionState.Failed(
                        provider = provider,
                        operation = AccountSessionOperation.INITIAL_IMPORT,
                        reason = AccountSessionFailureReason.PROVIDER_FAILURE,
                        retryable = true,
                    ),
                    repository.state.value,
                )
                assertEquals(0, repository.providerCredentialStore.writeCount)

                assertTrue(repository.startConnection(provider) != null)
                repository.completeAuthorization(AccountAuthorizationRedirect("mio://callback?code=second"))
                assertTrue(repository.state.value is AccountSessionState.Authenticated)
            }
        }

    @Test
    fun cancellationDuringInitialImportLeavesNoCredentialOrRestorableSession() =
        runTest {
            val authenticator = FakeProviderAuthenticator(ProviderKey.ANILIST)
            val identity = FakeProviderIdentityDataSource(ProviderKey.ANILIST)
            val initialImport = FakeProviderInitialImportDataSource(ProviderKey.ANILIST)
            val releaseImport = kotlinx.coroutines.CompletableDeferred<Unit>()
            authenticator.enqueueBeginAuthorization {
                ProviderResult.Success(providerAuthorizationLaunchFixture())
            }
            authenticator.enqueueCompleteAuthorization { _, _ ->
                ProviderResult.Success(providerCredentialsFixture())
            }
            identity.enqueue { ProviderResult.Success(providerIdentityFixture()) }
            initialImport.enqueue { _, accountId ->
                releaseImport.await()
                ProviderResult.Success(completeProviderImportFixture(accountId = accountId))
            }
            val repository = repository(ProviderKey.ANILIST, authenticator, identity, initialImport)
            repository.restore()
            repository.startConnection(ConnectedProvider.ANILIST)

            val completion =
                async {
                    repository.completeAuthorization(AccountAuthorizationRedirect("mio://callback?code=fixture"))
                }
            testScheduler.runCurrent()
            assertEquals(AccountSessionState.Importing(ConnectedProvider.ANILIST), repository.state.value)

            completion.cancelAndJoin()

            assertEquals(AccountSessionState.SignedOut, repository.state.value)
            assertNull(repository.providerCredentialStore.read(ProviderKey.ANILIST))
            val restored =
                FakeProviderAccountSessionRepository(
                    providerBinding = repository.providerBinding,
                    providerCredentialStore = repository.providerCredentialStore,
                    sessionStore = repository.sessionStore,
                )
            restored.restore()
            assertEquals(AccountSessionState.SignedOut, restored.state.value)
        }

    private fun configuredRepository(providerKey: ProviderKey): FakeProviderAccountSessionRepository {
        val authenticator = FakeProviderAuthenticator(providerKey)
        val identity = FakeProviderIdentityDataSource(providerKey)
        val initialImport = FakeProviderInitialImportDataSource(providerKey)
        val mediaDetails = FakeProviderMediaDetailDataSource(providerKey)
        authenticator.enqueueBeginAuthorization { ProviderResult.Success(providerAuthorizationLaunchFixture()) }
        authenticator.enqueueBeginAuthorization { ProviderResult.Success(providerAuthorizationLaunchFixture()) }
        authenticator.enqueueCompleteAuthorization {
            _,
            _,
            ->
            ProviderResult.Success(providerCredentialsFixture(providerKey))
        }
        identity.enqueue { ProviderResult.Success(providerIdentityFixture(provider = providerKey)) }
        initialImport.enqueue { _, accountId ->
            assertEquals(ProviderAccountId("account-fixture"), accountId)
            ProviderResult.Success(completeProviderImportFixture(provider = providerKey, accountId = accountId))
        }
        return repository(providerKey, authenticator, identity, initialImport, mediaDetails)
    }

    private fun repository(
        providerKey: ProviderKey,
        authenticator: FakeProviderAuthenticator,
        identity: FakeProviderIdentityDataSource,
        initialImport: FakeProviderInitialImportDataSource,
        mediaDetails: FakeProviderMediaDetailDataSource = FakeProviderMediaDetailDataSource(providerKey),
        sync: FakeProviderSyncDataSource = FakeProviderSyncDataSource(providerKey),
    ): FakeProviderAccountSessionRepository =
        FakeProviderAccountSessionRepository(
            providerBinding =
                ProviderBinding(
                    provider = providerKey,
                    authenticator = authenticator,
                    identityDataSource = identity,
                    initialImportDataSource = initialImport,
                    mediaDetailDataSource = mediaDetails,
                    mediaSearchDataSource = FakeProviderMediaSearchDataSource(providerKey),
                    syncDataSource = sync,
                ),
        )
}

private fun ProviderKey.toConnectedProvider(): ConnectedProvider =
    when (this) {
        ProviderKey.ANILIST -> ConnectedProvider.ANILIST
        ProviderKey.MY_ANIME_LIST -> ConnectedProvider.MY_ANIME_LIST
    }
