package io.morgan.mio.core.testing.provider

import io.morgan.mio.data.provider.api.ProviderAccountId
import io.morgan.mio.data.provider.api.ProviderAuthorizationRedirect
import io.morgan.mio.data.provider.api.ProviderAuthorizationRequest
import io.morgan.mio.data.provider.api.ProviderAuthorizationTransaction
import io.morgan.mio.data.provider.api.ProviderCollectionEntryId
import io.morgan.mio.data.provider.api.ProviderFailure
import io.morgan.mio.data.provider.api.ProviderKey
import io.morgan.mio.data.provider.api.ProviderMediaId
import io.morgan.mio.data.provider.api.ProviderProgressUnit
import io.morgan.mio.data.provider.api.ProviderResult
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.concurrent.CancellationException

class ProviderFakesTest {
    @Test
    fun authenticatorUsesFifoHandlersAndRecordsImmutableCalls() =
        runTest {
            val fake = FakeProviderAuthenticator(ProviderKey.ANILIST)
            val first = ProviderResult.Success(providerAuthorizationLaunchFixture())
            val second = ProviderResult.Failure(ProviderFailure.AuthorizationDenied)
            fake.enqueueBeginAuthorization { first }
            fake.enqueueBeginAuthorization { second }

            val request = ProviderAuthorizationRequest("https://callback.invalid/return")
            assertEquals(first, fake.beginAuthorization(request))
            assertEquals(second, fake.beginAuthorization(request))
            assertEquals(listOf(request, request), fake.beginRequests)

            val transaction = ProviderAuthorizationTransaction("state", null)
            val redirect = ProviderAuthorizationRedirect("https://callback.invalid/return?code=fixture")
            fake.enqueueCompleteAuthorization { _, _ -> ProviderResult.Success(providerCredentialsFixture()) }
            fake.completeAuthorization(transaction, redirect)
            assertEquals(transaction, fake.completeCalls.single().transaction)
            assertEquals(redirect, fake.completeCalls.single().redirect)

            val credentials = providerCredentialsFixture()
            fake.enqueueRefresh { ProviderResult.Success(credentials) }
            assertEquals(ProviderResult.Success(credentials), fake.refresh(credentials))
            assertEquals(listOf(credentials), fake.refreshCredentials)
        }

    @Test
    fun identityAndImportFakesRecordCallsAndDeliverNormalizedFailures() =
        runTest {
            val credentials = providerCredentialsFixture()
            val identityFake = FakeProviderIdentityDataSource(ProviderKey.ANILIST)
            identityFake.enqueue { ProviderResult.Failure(ProviderFailure.Timeout) }
            assertEquals(
                ProviderResult.Failure(ProviderFailure.Timeout),
                identityFake.fetchIdentity(credentials),
            )
            assertEquals(listOf(credentials), identityFake.credentials)

            val importFake = FakeProviderInitialImportDataSource(ProviderKey.ANILIST)
            val accountId = providerIdentityFixture().accountId
            importFake.enqueue {
                _,
                account,
                ->
                ProviderResult.Success(completeProviderImportFixture(accountId = account))
            }
            val result = importFake.fetchCompleteImport(credentials, accountId)
            assertTrue(result is ProviderResult.Success)
            assertEquals(credentials, importFake.calls.single().credentials)
            assertEquals(accountId, importFake.calls.single().accountId)
        }

    @Test
    fun emptyQueuesFailFastAndCancellationIsNotConverted() =
        runTest {
            val fake = FakeProviderAuthenticator(ProviderKey.ANILIST)
            val request = ProviderAuthorizationRequest("https://callback.invalid/return")
            var failure: IllegalStateException? = null
            try {
                fake.beginAuthorization(request)
            } catch (exception: IllegalStateException) {
                failure = exception
            }
            assertTrue(failure?.message.orEmpty().contains("beginAuthorization"))

            val cancellation = CancellationException("fixture cancellation")
            fake.enqueueBeginAuthorization { throw cancellation }
            var propagated: CancellationException? = null
            try {
                fake.beginAuthorization(request)
            } catch (exception: CancellationException) {
                propagated = exception
            }
            assertSame(cancellation, propagated)
        }

    @Test
    fun fixturesCreateAValidBindingAndRedactedCredentials() {
        val binding = providerBindingFixture()

        assertEquals(ProviderKey.ANILIST, binding.provider)
        assertEquals("ProviderCredentials([REDACTED])", providerCredentialsFixture().toString())
        assertEquals(1, completeProviderImportFixture().media.size)
        assertEquals(ProviderKey.ANILIST, binding.mediaDetailDataSource.provider)
    }

    @Test
    fun mediaDetailFakeRecordsCallsUsesFifoAndRejectsMismatchedResults() =
        runTest {
            val fake = FakeProviderMediaDetailDataSource(ProviderKey.ANILIST)
            val credentials = providerCredentialsFixture()
            val details = providerMediaDetailsFixture()
            fake.enqueue { _, _ -> ProviderResult.Success(details) }

            assertEquals(
                ProviderResult.Success(details),
                fake.fetchMediaDetails(credentials, ProviderMediaId("101")),
            )
            assertEquals(ProviderMediaId("101"), fake.calls.single().mediaId)
            assertEquals(credentials, fake.calls.single().credentials)

            fake.enqueue { _, _ ->
                ProviderResult.Success(
                    providerMediaDetailsFixture(provider = ProviderKey.MY_ANIME_LIST),
                )
            }
            assertThrows<IllegalStateException> {
                fake.fetchMediaDetails(credentials, ProviderMediaId("101"))
            }
        }

    @Test
    fun fakesRejectCrossProviderInputsAndScriptedSuccesses() =
        runTest {
            val anilistCredentials = providerCredentialsFixture(ProviderKey.ANILIST)
            val myAnimeListCredentials = providerCredentialsFixture(ProviderKey.MY_ANIME_LIST)
            val authenticator = FakeProviderAuthenticator(ProviderKey.ANILIST)

            assertThrows<IllegalArgumentException> {
                authenticator.refresh(myAnimeListCredentials)
            }
            authenticator.enqueueRefresh { ProviderResult.Success(myAnimeListCredentials) }
            assertThrows<IllegalStateException> {
                authenticator.refresh(anilistCredentials)
            }

            val identity = FakeProviderIdentityDataSource(ProviderKey.ANILIST)
            identity.enqueue {
                ProviderResult.Success(providerIdentityFixture(provider = ProviderKey.MY_ANIME_LIST))
            }
            assertThrows<IllegalStateException> {
                identity.fetchIdentity(anilistCredentials)
            }

            val initialImport = FakeProviderInitialImportDataSource(ProviderKey.ANILIST)
            val accountId = providerIdentityFixture().accountId
            initialImport.enqueue { _, requestedAccountId ->
                ProviderResult.Success(
                    completeProviderImportFixture(
                        provider = ProviderKey.MY_ANIME_LIST,
                        accountId = requestedAccountId,
                    ),
                )
            }
            assertThrows<IllegalStateException> {
                initialImport.fetchCompleteImport(anilistCredentials, accountId)
            }
        }

    @Test
    fun syncFakeRecordsFixedUnitAndAbsoluteMutationInputs() =
        runTest {
            val fake = FakeProviderSyncDataSource(ProviderKey.ANILIST)
            val credentials = providerCredentialsFixture()
            val account = ProviderAccountId("42")
            val entry = ProviderCollectionEntryId("7")
            val media = ProviderMediaId("101")
            val normalized =
                providerCollectionEntryFixture(
                    id = entry.value,
                    accountId = account,
                    mediaId = media.value,
                    progressUnit = ProviderProgressUnit.VOLUME,
                )
            fake.enqueueRead { _, _, _, _, _ -> ProviderResult.Success(normalized) }
            fake.enqueueProgress { _, _, _, _, _, _ -> ProviderResult.Success(normalized) }
            fake.enqueueStatus { _, _, _, _, _, _ -> ProviderResult.Success(normalized) }

            fake.readEntry(credentials, account, entry, media, ProviderProgressUnit.VOLUME)
            fake.setProgress(credentials, account, entry, media, ProviderProgressUnit.VOLUME, 2)
            fake.setStatus(
                credentials,
                account,
                entry,
                media,
                ProviderProgressUnit.VOLUME,
                io.morgan.mio.data.provider.api.ProviderCollectionStatus.COMPLETED,
            )

            assertEquals(3, fake.calls.size)
            assertEquals(ProviderProgressUnit.VOLUME, fake.calls[0].unit)
            assertEquals(2, (fake.calls[1] as SyncCall.Progress).target)
            assertEquals(
                io.morgan.mio.data.provider.api.ProviderCollectionStatus.COMPLETED,
                (fake.calls[2] as SyncCall.Status).target,
            )
        }
}
