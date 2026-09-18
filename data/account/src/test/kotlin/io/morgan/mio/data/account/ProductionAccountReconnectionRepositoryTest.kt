package io.morgan.mio.data.account

import androidx.test.core.app.ApplicationProvider
import io.morgan.mio.core.database.MioDatabase
import io.morgan.mio.core.database.MioDatabaseFactory
import io.morgan.mio.core.database.entity.AccountProviderIdEntity
import io.morgan.mio.core.database.entity.CollectionEntryEntity
import io.morgan.mio.core.database.entity.MediaEntity
import io.morgan.mio.core.database.entity.PendingOperationEntity
import io.morgan.mio.core.database.entity.PendingOperationGroupEntity
import io.morgan.mio.core.database.entity.SELECTED_ACCOUNT_KEY
import io.morgan.mio.core.database.entity.SelectedAccountEntity
import io.morgan.mio.core.database.model.StoredCollectionStatus
import io.morgan.mio.core.database.model.StoredConnectedProvider
import io.morgan.mio.core.database.model.StoredMediaFormat
import io.morgan.mio.core.database.model.StoredMediaPublicationStatus
import io.morgan.mio.core.database.model.StoredMediaType
import io.morgan.mio.core.database.model.StoredPendingOperationKind
import io.morgan.mio.core.database.model.StoredPendingOperationState
import io.morgan.mio.core.database.model.StoredProgressUnit
import io.morgan.mio.core.testing.provider.FakeProviderAuthenticator
import io.morgan.mio.core.testing.provider.FakeProviderInitialImportDataSource
import io.morgan.mio.core.testing.provider.providerAuthorizationLaunchFixture
import io.morgan.mio.core.testing.provider.providerBindingFixture
import io.morgan.mio.core.testing.provider.providerCredentialsFixture
import io.morgan.mio.data.account.local.RoomAccountLocalDataSource
import io.morgan.mio.data.provider.api.ProviderAccountId
import io.morgan.mio.data.provider.api.ProviderBinding
import io.morgan.mio.data.provider.api.ProviderBindingResolver
import io.morgan.mio.data.provider.api.ProviderCredentialStorageException
import io.morgan.mio.data.provider.api.ProviderCredentialStore
import io.morgan.mio.data.provider.api.ProviderCredentials
import io.morgan.mio.data.provider.api.ProviderFailure
import io.morgan.mio.data.provider.api.ProviderIdentity
import io.morgan.mio.data.provider.api.ProviderIdentityDataSource
import io.morgan.mio.data.provider.api.ProviderKey
import io.morgan.mio.data.provider.api.ProviderResult
import io.morgan.mio.domain.account.AccountAuthorizationRedirect
import io.morgan.mio.domain.account.AccountReconnectionState
import io.morgan.mio.domain.model.AccountId
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [35])
@OptIn(ExperimentalCoroutinesApi::class)
class ProductionAccountReconnectionRepositoryTest {
    private lateinit var database: MioDatabase
    private lateinit var transitions: AccountSessionTransitions

    @Before
    fun setUp() {
        transitions = AccountSessionTransitions()
        database = MioDatabaseFactory.createInMemory(ApplicationProvider.getApplicationContext())
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun sameAccountReconnectForEachProviderReplacesOnlyCredentialsAndPreservesCacheAndOutbox() =
        runTest {
            ProviderKey.entries.forEach { provider ->
                database.clearAllTables()
                seedAccount(provider)
                seedCacheAndOutbox()
                val oldCredentials = credentials(provider, "old-token")
                val otherProvider = provider.other()
                val otherCredentials = credentials(otherProvider, "other-token")
                val credentialStore = RecordingCredentialStore(oldCredentials, otherCredentials)
                val binding = configuredBinding(provider)
                val repository = repository(binding, credentialStore)
                val selectedBefore = database.selectedAccountDao().observeSelectedAccount().first()
                val mappingBefore = database.syncDao().getAccountProviderId(ACCOUNT_ID.value)
                val entryBefore = database.collectionEntryDao().getEntry(ENTRY_ID)
                val operationsBefore = database.pendingOperationDao().getOperationsInEnqueueOrder(ACCOUNT_ID.value)

                val launch = repository.start()

                assertEquals("https://auth.invalid/authorize", launch?.authorizationUri)
                assertEquals(AccountReconnectionState.Authorizing(ACCOUNT_ID), repository.state.value)
                assertEquals(oldCredentials, credentialStore.value(provider))
                assertEquals(emptyList<ProviderKey>(), credentialStore.writes)

                repository.complete(AccountAuthorizationRedirect(VALID_REDIRECT))

                assertEquals(AccountReconnectionState.Succeeded(ACCOUNT_ID), repository.state.value)
                assertEquals("fixture-access-value", credentialStore.value(provider)?.accessToken)
                assertEquals(otherCredentials, credentialStore.value(otherProvider))
                assertEquals(listOf(provider), credentialStore.writes)
                assertEquals(selectedBefore, database.selectedAccountDao().observeSelectedAccount().first())
                assertEquals(mappingBefore, database.syncDao().getAccountProviderId(ACCOUNT_ID.value))
                assertEquals(entryBefore, database.collectionEntryDao().getEntry(ENTRY_ID))
                assertEquals(
                    operationsBefore,
                    database.pendingOperationDao().getOperationsInEnqueueOrder(ACCOUNT_ID.value),
                )
                assertTrue(
                    (binding.initialImportDataSource as FakeProviderInitialImportDataSource).calls.isEmpty(),
                )

                repository.complete(AccountAuthorizationRedirect(VALID_REDIRECT))

                assertEquals(1, (binding.authenticator as FakeProviderAuthenticator).completeCalls.size)
                assertEquals(listOf(provider), credentialStore.writes)
            }
        }

    @Test
    fun wrongAccountOrProviderIdentityNeverReplacesCredentials() =
        runTest {
            listOf(
                ProviderKey.ANILIST to ProviderKey.ANILIST,
                ProviderKey.ANILIST to ProviderKey.MY_ANIME_LIST,
            ).forEachIndexed { index, (provider, returnedProvider) ->
                database.clearAllTables()
                seedAccount(provider)
                val oldCredentials = credentials(provider, "old-token-$index")
                val credentialStore = RecordingCredentialStore(oldCredentials)
                val binding =
                    configuredBinding(
                        provider = provider,
                        identityProvider = returnedProvider,
                        identityAccountId = if (index == 0) "different-account" else REMOTE_ACCOUNT_ID,
                    )
                val repository = repository(binding, credentialStore)

                repository.start()
                repository.complete(AccountAuthorizationRedirect(VALID_REDIRECT))

                assertEquals(
                    AccountReconnectionState.Failed(
                        ACCOUNT_ID,
                        io.morgan.mio.domain.account.AccountSessionFailureReason.PROVIDER_FAILURE,
                    ),
                    repository.state.value,
                )
                assertEquals(oldCredentials, credentialStore.value(provider))
                assertEquals(emptyList<ProviderKey>(), credentialStore.writes)
                assertEquals(
                    0,
                    (binding.initialImportDataSource as FakeProviderInitialImportDataSource).calls.size,
                )
            }
        }

    @Test
    fun authorizationLaunchFailureAndDeniedOrInvalidRedirectKeepOldCredentials() =
        runTest {
            listOf(
                ProviderFailure.ServiceUnavailable to null,
                null to ProviderFailure.AuthorizationDenied,
                null to ProviderFailure.InvalidAuthorizationCallback,
            ).forEachIndexed { index, (launchFailure, completionFailure) ->
                database.clearAllTables()
                seedAccount(ProviderKey.ANILIST)
                val oldCredentials = credentials(ProviderKey.ANILIST, "old-token-$index")
                val credentialStore = RecordingCredentialStore(oldCredentials)
                val binding = configuredBinding(ProviderKey.ANILIST, launchFailure, completionFailure)
                val repository = repository(binding, credentialStore)

                val launch = repository.start()
                if (launchFailure == null) {
                    assertTrue(launch != null)
                    repository.complete(
                        AccountAuthorizationRedirect(
                            "mio://oauth/anilist?error=access_denied",
                        ),
                    )
                } else {
                    assertNull(launch)
                }

                assertTrue(repository.state.value is AccountReconnectionState.Failed)
                assertEquals(oldCredentials, credentialStore.value(ProviderKey.ANILIST))
                assertEquals(emptyList<ProviderKey>(), credentialStore.writes)
            }
        }

    @Test
    fun processRecreationAndDuplicateRedirectCannotReuseOrRepeatAuthorizationTransaction() =
        runTest {
            seedAccount(ProviderKey.ANILIST)
            val oldCredentials = credentials(ProviderKey.ANILIST, "old-token")
            val credentialStore = RecordingCredentialStore(oldCredentials)
            val binding = configuredBinding(ProviderKey.ANILIST)
            val firstProcessRepository = repository(binding, credentialStore)
            firstProcessRepository.start()

            val recreatedRepository = repository(binding, credentialStore)
            recreatedRepository.complete(
                AccountAuthorizationRedirect(VALID_REDIRECT),
            )

            assertEquals(AccountReconnectionState.Idle, recreatedRepository.state.value)
            assertTrue((binding.authenticator as FakeProviderAuthenticator).completeCalls.isEmpty())
            assertEquals(oldCredentials, credentialStore.value(ProviderKey.ANILIST))
            assertTrue(credentialStore.writes.isEmpty())
        }

    @Test
    fun secureStoreFailurePreservesOldCredentialAndRoomState() =
        runTest {
            seedAccount(ProviderKey.ANILIST)
            seedCacheAndOutbox()
            val oldCredentials = credentials(ProviderKey.ANILIST, "old-token")
            val credentialStore = RecordingCredentialStore(oldCredentials).apply { failWrites = true }
            val entryBefore = database.collectionEntryDao().getEntry(ENTRY_ID)
            val operationsBefore = database.pendingOperationDao().getOperationsInEnqueueOrder(ACCOUNT_ID.value)
            val binding = configuredBinding(ProviderKey.ANILIST)
            val repository = repository(binding, credentialStore)

            repository.start()
            repository.complete(AccountAuthorizationRedirect(VALID_REDIRECT))

            assertEquals(
                AccountReconnectionState.Failed(
                    ACCOUNT_ID,
                    io.morgan.mio.domain.account.AccountSessionFailureReason.PROVIDER_FAILURE,
                ),
                repository.state.value,
            )
            assertEquals(oldCredentials, credentialStore.value(ProviderKey.ANILIST))
            assertEquals(entryBefore, database.collectionEntryDao().getEntry(ENTRY_ID))
            assertEquals(operationsBefore, database.pendingOperationDao().getOperationsInEnqueueOrder(ACCOUNT_ID.value))
            assertTrue(
                (binding.initialImportDataSource as FakeProviderInitialImportDataSource).calls.isEmpty(),
            )
        }

    @Test
    fun cancellationBeforeSecureCommitRetainsOldCredential() =
        runTest {
            seedAccount(ProviderKey.ANILIST)
            val oldCredentials = credentials(ProviderKey.ANILIST, "old-token")
            val enteredWrite = CompletableDeferred<Unit>()
            val waitBeforeCommit = CompletableDeferred<Unit>()
            val credentialStore =
                RecordingCredentialStore(oldCredentials).apply {
                    beforeCommit = {
                        enteredWrite.complete(Unit)
                        waitBeforeCommit.await()
                    }
                }
            val repository = repository(configuredBinding(ProviderKey.ANILIST), credentialStore)
            repository.start()

            val completion =
                async {
                    repository.complete(AccountAuthorizationRedirect(VALID_REDIRECT))
                }
            enteredWrite.await()
            completion.cancelAndJoin()

            assertEquals(oldCredentials, credentialStore.value(ProviderKey.ANILIST))
            assertEquals(emptyList<ProviderKey>(), credentialStore.writes)
            assertEquals(AccountReconnectionState.Idle, repository.state.value)
        }

    @Test
    fun cancellationAfterSecureCommitNeverDeletesNewCredential() =
        runTest {
            seedAccount(ProviderKey.ANILIST)
            val oldCredentials = credentials(ProviderKey.ANILIST, "old-token")
            val enteredAfterCommit = CompletableDeferred<Unit>()
            val waitAfterCommit = CompletableDeferred<Unit>()
            val credentialStore =
                RecordingCredentialStore(oldCredentials).apply {
                    afterCommit = {
                        enteredAfterCommit.complete(Unit)
                        waitAfterCommit.await()
                    }
                }
            val repository = repository(configuredBinding(ProviderKey.ANILIST), credentialStore)
            repository.start()

            val completion =
                async {
                    repository.complete(AccountAuthorizationRedirect(VALID_REDIRECT))
                }
            enteredAfterCommit.await()
            completion.cancelAndJoin()

            assertEquals("fixture-access-value", credentialStore.value(ProviderKey.ANILIST)?.accessToken)
            assertEquals(listOf(ProviderKey.ANILIST), credentialStore.writes)
            assertEquals(AccountReconnectionState.Idle, repository.state.value)
        }

    @Test
    fun concurrentStartsShareOnlyOneBrowserTransaction() =
        runTest {
            seedAccount(ProviderKey.ANILIST)
            val launchEntered = CompletableDeferred<Unit>()
            val releaseLaunch = CompletableDeferred<Unit>()
            val binding =
                configuredBinding(ProviderKey.ANILIST, launchGate = {
                    launchEntered.complete(Unit)
                    releaseLaunch.await()
                })
            val repository = repository(binding, RecordingCredentialStore(providerCredentialsFixture()))
            val firstStart = async { repository.start() }
            launchEntered.await()
            val secondStart = async { repository.start() }
            runCurrent()
            assertEquals(1, (binding.authenticator as FakeProviderAuthenticator).beginRequests.size)

            releaseLaunch.complete(Unit)

            assertTrue(firstStart.await() != null)
            assertNull(secondStart.await())
            assertEquals(1, (binding.authenticator as FakeProviderAuthenticator).beginRequests.size)
        }

    @Test
    fun disconnectWaitsForReconnectCredentialCommitThenRemovesTheNewCredential() =
        runTest {
            seedAccount(ProviderKey.ANILIST)
            val enteredWrite = CompletableDeferred<Unit>()
            val releaseWrite = CompletableDeferred<Unit>()
            val store =
                RecordingCredentialStore(providerCredentialsFixture()).apply {
                    beforeCommit = {
                        enteredWrite.complete(Unit)
                        releaseWrite.await()
                    }
                }
            val binding = configuredBinding(ProviderKey.ANILIST)
            val session = sessionRepository(binding, store)
            session.restore()
            val reconnect = repository(binding, store)
            reconnect.start()
            val completion = async { reconnect.complete(AccountAuthorizationRedirect(VALID_REDIRECT)) }
            enteredWrite.await()
            val disconnect = async { session.disconnect() }
            runCurrent()
            assertTrue(!disconnect.isCompleted)
            assertTrue(session.state.value is io.morgan.mio.domain.account.AccountSessionState.Authenticated)

            releaseWrite.complete(Unit)
            completion.await()
            disconnect.await()

            assertNull(store.value(ProviderKey.ANILIST))
            assertNull(database.selectedAccountDao().observeSelectedAccount().first())
            assertEquals(io.morgan.mio.domain.account.AccountSessionState.SignedOut, session.state.value)
        }

    @Test
    fun disconnectInvalidatesPendingBrowserTransactionAndAllowsAFreshReconnect() =
        runTest {
            seedAccount(ProviderKey.ANILIST)
            val store = RecordingCredentialStore(providerCredentialsFixture())
            val binding = configuredBinding(ProviderKey.ANILIST)
            val session = sessionRepository(binding, store)
            session.restore()
            val reconnect = repository(binding, store)
            reconnect.start()
            session.disconnect()

            reconnect.complete(AccountAuthorizationRedirect(VALID_REDIRECT))

            assertTrue((binding.authenticator as FakeProviderAuthenticator).completeCalls.isEmpty())
            assertNull(store.value(ProviderKey.ANILIST))
            assertEquals(AccountReconnectionState.Idle, reconnect.state.value)
            seedAccount(ProviderKey.ANILIST)
            (binding.authenticator as FakeProviderAuthenticator).enqueueBeginAuthorization {
                ProviderResult.Success(providerAuthorizationLaunchFixture())
            }
            assertTrue(reconnect.start() != null)
        }

    private fun sessionRepository(
        binding: ProviderBinding,
        store: ProviderCredentialStore,
    ): ProductionAccountSessionRepository =
        ProductionAccountSessionRepository(
            transitions = transitions,
            bindingResolver = ProviderBindingResolver { binding },
            credentialStore = store,
            initialImportWriter =
                InitialImportWriter(
                    io.morgan.mio.data.account.local
                        .RoomInitialImportLocalDataSource(database),
                ),
            accountLocalDataSource = RoomAccountLocalDataSource(database),
            accountSessionActivator =
                object : AuthenticatedAccountSessionActivator {
                    override fun activate(
                        accountId: AccountId,
                        provider: io.morgan.mio.domain.account.ConnectedProvider,
                    ) = Unit

                    override fun destroy() = Unit
                },
            disconnectSupport =
                object : AccountSessionDisconnectSupport {
                    override suspend fun cancelAccountWork(accountId: AccountId) = Unit

                    override suspend fun clearAccountPreferences(accountId: AccountId) = Unit

                    override fun reportCleanupFailure() = Unit
                },
        )

    private fun repository(
        binding: ProviderBinding,
        credentialStore: ProviderCredentialStore,
    ): ProductionAccountReconnectionRepository =
        ProductionAccountReconnectionRepository(
            transitions = transitions,
            bindingResolver = ProviderBindingResolver { provider -> binding.takeIf { it.provider == provider } },
            credentialStore = credentialStore,
            accountLocalDataSource = RoomAccountLocalDataSource(database),
            database = database,
        )

    private fun configuredBinding(
        provider: ProviderKey,
        launchFailure: ProviderFailure? = null,
        completionFailure: ProviderFailure? = null,
        identityProvider: ProviderKey = provider,
        identityAccountId: String = REMOTE_ACCOUNT_ID,
        launchGate: (suspend () -> Unit)? = null,
    ): ProviderBinding {
        val components = providerBindingFixture(provider)
        val authenticator = components.authenticator as FakeProviderAuthenticator
        authenticator.enqueueBeginAuthorization {
            launchGate?.invoke()
            if (launchFailure == null) {
                ProviderResult.Success(providerAuthorizationLaunchFixture())
            } else {
                ProviderResult.Failure(launchFailure)
            }
        }
        authenticator.enqueueCompleteAuthorization { _, _ ->
            if (completionFailure == null) {
                ProviderResult.Success(providerCredentialsFixture(provider))
            } else {
                ProviderResult.Failure(completionFailure)
            }
        }
        val identityDataSource =
            object : ProviderIdentityDataSource {
                override val provider: ProviderKey = provider

                override suspend fun fetchIdentity(credentials: ProviderCredentials): ProviderResult<ProviderIdentity> {
                    assertEquals(provider, credentials.provider)
                    return ProviderResult.Success(
                        ProviderIdentity(
                            provider = identityProvider,
                            accountId = ProviderAccountId(identityAccountId),
                            displayName = "Fixture identity",
                            avatar = null,
                        ),
                    )
                }
            }
        return ProviderBinding(
            provider = provider,
            authenticator = authenticator,
            identityDataSource = identityDataSource,
            initialImportDataSource = components.initialImportDataSource,
            mediaDetailDataSource = components.mediaDetailDataSource,
            mediaSearchDataSource =
                io.morgan.mio.core.testing.provider
                    .FakeProviderMediaSearchDataSource(provider),
            syncDataSource = components.syncDataSource,
        )
    }

    private suspend fun seedAccount(provider: ProviderKey) {
        val storedProvider = provider.toStoredProvider()
        database.selectedAccountDao().replaceSelectedAccount(
            account =
                SelectedAccountEntity(
                    selectionKey = SELECTED_ACCOUNT_KEY,
                    accountId = ACCOUNT_ID.value,
                    connectedProvider = storedProvider,
                    displayName = "Fixture account",
                    avatarReference = null,
                ),
            providerId =
                AccountProviderIdEntity(
                    accountId = ACCOUNT_ID.value,
                    provider = storedProvider,
                    remoteAccountId = REMOTE_ACCOUNT_ID,
                ),
        )
    }

    private suspend fun seedCacheAndOutbox() {
        database.mediaDao().upsertMedia(
            listOf(
                MediaEntity(
                    mediaId = MEDIA_ID,
                    mediaType = StoredMediaType.ANIME,
                    title = "Cached fixture",
                    format = StoredMediaFormat.TV,
                    publicationStatus = StoredMediaPublicationStatus.RELEASING,
                    synopsis = null,
                    posterReference = null,
                    bannerReference = null,
                    episodeTotal = 12,
                    chapterTotal = null,
                    volumeTotal = null,
                ),
            ),
        )
        database.collectionEntryDao().upsertEntries(
            listOf(
                CollectionEntryEntity(
                    collectionEntryId = ENTRY_ID,
                    accountId = ACCOUNT_ID.value,
                    mediaId = MEDIA_ID,
                    progressUnit = StoredProgressUnit.EPISODE,
                    remoteStatus = StoredCollectionStatus.CURRENT,
                    remoteProgress = 2,
                    remoteTotal = 12,
                    remoteUpdatedAtUtcMs = NOW,
                    effectiveStatus = StoredCollectionStatus.CURRENT,
                    effectiveProgress = 3,
                    effectiveTotal = 12,
                    effectiveUpdatedAtUtcMs = NOW,
                    localProgressRevision = 1,
                ),
            ),
        )
        database.pendingOperationDao().insertGroup(
            PendingOperationGroupEntity(
                operationGroupId = GROUP_ID,
                accountId = ACCOUNT_ID.value,
                createdAtUtcMs = NOW,
                compensatesGroupId = null,
            ),
        )
        database.pendingOperationDao().insertOperations(
            listOf(
                PendingOperationEntity(
                    operationId = OPERATION_ID,
                    operationGroupId = GROUP_ID,
                    accountId = ACCOUNT_ID.value,
                    collectionEntryId = ENTRY_ID,
                    positionInGroup = 0,
                    kind = StoredPendingOperationKind.SET_PROGRESS_FORWARD,
                    baseProgress = 2,
                    targetProgress = 3,
                    baseStatus = null,
                    targetStatus = null,
                    lifecycleState = StoredPendingOperationState.ENQUEUED,
                    attemptCount = 0,
                    lastAttemptAtUtcMs = null,
                    retryNotBeforeUtcMs = null,
                ),
            ),
        )
    }

    private fun ProviderKey.toStoredProvider(): StoredConnectedProvider =
        when (this) {
            ProviderKey.ANILIST -> StoredConnectedProvider.ANILIST
            ProviderKey.MY_ANIME_LIST -> StoredConnectedProvider.MY_ANIME_LIST
        }

    private fun credentials(
        provider: ProviderKey,
        accessToken: String,
    ): ProviderCredentials =
        ProviderCredentials(
            provider = provider,
            accessToken = accessToken,
            refreshToken = "fixture-refresh-value",
            expiresAt = Instant.parse("2026-01-01T00:00:00Z"),
            tokenType = "Fixture",
        )

    private class RecordingCredentialStore(
        vararg initial: ProviderCredentials,
    ) : ProviderCredentialStore {
        private val values = initial.associateByTo(mutableMapOf()) { credentials -> credentials.provider }
        private val writeProvidersValue = mutableListOf<ProviderKey>()
        var failWrites: Boolean = false
        var beforeCommit: (suspend () -> Unit)? = null
        var afterCommit: (suspend () -> Unit)? = null

        val writes: List<ProviderKey>
            get() = writeProvidersValue.toList()

        fun value(provider: ProviderKey): ProviderCredentials? = values[provider]

        override suspend fun read(provider: ProviderKey): ProviderCredentials? = values[provider]

        override suspend fun write(credentials: ProviderCredentials) {
            beforeCommit?.invoke()
            if (failWrites) throw ProviderCredentialStorageException()
            values[credentials.provider] = credentials
            writeProvidersValue += credentials.provider
            afterCommit?.invoke()
        }

        override suspend fun delete(provider: ProviderKey) {
            values.remove(provider)
        }

        override suspend fun deleteAll() {
            values.clear()
        }
    }

    private companion object {
        val ACCOUNT_ID = AccountId("reconnect-account")
        const val REMOTE_ACCOUNT_ID = "remote-reconnect-account"
        const val MEDIA_ID = "cached-reconnect-media"
        const val ENTRY_ID = "cached-reconnect-entry"
        const val GROUP_ID = "cached-reconnect-group"
        const val OPERATION_ID = "cached-reconnect-operation"
        const val VALID_REDIRECT = "mio://oauth/callback?code=fixture"
        val NOW = Instant.parse("2026-09-16T10:00:00Z")
    }
}

private fun ProviderKey.other(): ProviderKey =
    when (this) {
        ProviderKey.ANILIST -> ProviderKey.MY_ANIME_LIST
        ProviderKey.MY_ANIME_LIST -> ProviderKey.ANILIST
    }
