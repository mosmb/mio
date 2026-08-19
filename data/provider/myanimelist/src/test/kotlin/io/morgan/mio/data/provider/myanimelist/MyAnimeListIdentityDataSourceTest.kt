package io.morgan.mio.data.provider.myanimelist

import io.morgan.mio.data.provider.api.ProviderCredentials
import io.morgan.mio.data.provider.api.ProviderFailure
import io.morgan.mio.data.provider.api.ProviderImageReference
import io.morgan.mio.data.provider.api.ProviderKey
import io.morgan.mio.data.provider.api.ProviderResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.time.Duration.Companion.seconds

class MyAnimeListIdentityDataSourceTest {
    private val sentinelToken = "fixture-obvious-sentinel-token"
    private val credentials =
        ProviderCredentials(
            provider = ProviderKey.MY_ANIME_LIST,
            accessToken = sentinelToken,
            refreshToken = null,
            expiresAt = null,
            tokenType = "Bearer",
        )

    private lateinit var server: MockMyAnimeListServer
    private lateinit var dataSource: MyAnimeListIdentityDataSource

    @Before
    fun setUp() {
        server = MockMyAnimeListServer()
        dataSource = MyAnimeListIdentityDataSource(server.apiService)
    }

    @After
    fun tearDown() {
        server.close()
    }

    @Test
    fun theRepresentativeUserPayloadMapsToTheExactIdentity() =
        runTest {
            server.enqueue(MockMyAnimeListServer.loadFixture("user.json"))

            val result = dataSource.fetchIdentity(credentials) as ProviderResult.Success

            assertEquals(ProviderKey.MY_ANIME_LIST, result.value.provider)
            assertEquals("12345", result.value.accountId.value)
            assertEquals("TestUser", result.value.displayName)
            assertEquals(
                ProviderImageReference("https://images.invalid/user-avatar.jpg"),
                result.value.avatar,
            )

            val recorded = server.takeRequest()
            assertEquals("/users/@me", recorded.target)
            assertEquals("GET", recorded.method)
            assertEquals("Bearer $sentinelToken", recorded.headers["Authorization"])
        }

    @Test
    fun aNullNameIsUnsupportedMandatoryData() =
        runTest {
            server.enqueue(MockMyAnimeListServer.loadFixture("user-missing-name.json"))

            assertEquals(
                ProviderResult.Failure(ProviderFailure.UnsupportedMandatoryData),
                dataSource.fetchIdentity(credentials),
            )
        }

    @Test
    fun aBlankNameIsUnsupportedMandatoryData() =
        runTest {
            server.enqueue("""{"id":12345,"name":"   "}""")

            assertEquals(
                ProviderResult.Failure(ProviderFailure.UnsupportedMandatoryData),
                dataSource.fetchIdentity(credentials),
            )
        }

    @Test
    fun aNullOrNonPositiveIdIsMalformed() =
        runTest {
            server.enqueue("""{"name":"TestUser"}""")
            assertEquals(
                ProviderResult.Failure(ProviderFailure.MalformedResponse),
                dataSource.fetchIdentity(credentials),
            )

            server.enqueue("""{"id":0,"name":"TestUser"}""")
            assertEquals(
                ProviderResult.Failure(ProviderFailure.MalformedResponse),
                dataSource.fetchIdentity(credentials),
            )

            server.enqueue("""{"id":-7,"name":"TestUser"}""")
            assertEquals(
                ProviderResult.Failure(ProviderFailure.MalformedResponse),
                dataSource.fetchIdentity(credentials),
            )
        }

    @Test
    fun anAbsentOrBlankPictureYieldsANullAvatarRatherThanAPlaceholder() =
        runTest {
            server.enqueue("""{"id":12345,"name":"TestUser"}""")
            assertNull((dataSource.fetchIdentity(credentials) as ProviderResult.Success).value.avatar)

            server.enqueue("""{"id":12345,"name":"TestUser","picture":"  "}""")
            assertNull((dataSource.fetchIdentity(credentials) as ProviderResult.Success).value.avatar)
        }

    @Test
    fun everyResourceStatusMapsToItsFailure() =
        runTest {
            val expectations =
                listOf(
                    401 to ProviderFailure.AuthenticationRequired,
                    403 to ProviderFailure.AuthorizationDenied,
                    429 to ProviderFailure.RateLimited(null),
                    503 to ProviderFailure.ServiceUnavailable,
                    400 to ProviderFailure.MalformedResponse,
                )

            expectations.forEach { (code, failure) ->
                server.enqueue(MockMyAnimeListServer.loadFixture("error-unauthorized.json"), code = code)
                assertEquals(
                    "status $code",
                    ProviderResult.Failure(failure),
                    dataSource.fetchIdentity(credentials),
                )
            }
        }

    @Test
    fun aRateLimitedResponseCarriesTheRetryAfterDuration() =
        runTest {
            server.enqueue(
                MockMyAnimeListServer.loadFixture("error-unauthorized.json"),
                code = 429,
                headers = mapOf("Retry-After" to "45"),
            )

            assertEquals(
                ProviderResult.Failure(ProviderFailure.RateLimited(45.seconds)),
                dataSource.fetchIdentity(credentials),
            )
        }

    @Test
    fun aMalformedJsonBodyIsMalformedResponse() =
        runTest {
            server.enqueue("not json at all")

            assertEquals(
                ProviderResult.Failure(ProviderFailure.MalformedResponse),
                dataSource.fetchIdentity(credentials),
            )
        }

    @Test
    fun credentialsForAnotherProviderThrowBeforeAnyRequest() =
        runTest {
            val foreign =
                ProviderCredentials(
                    provider = ProviderKey.ANILIST,
                    accessToken = sentinelToken,
                    refreshToken = null,
                    expiresAt = null,
                    tokenType = "Bearer",
                )

            val thrown =
                try {
                    dataSource.fetchIdentity(foreign)
                    null
                } catch (caught: IllegalArgumentException) {
                    caught
                }

            assertTrue(thrown is IllegalArgumentException)
            assertEquals(0, server.requestCount())
        }

    @Test
    fun cancellingTheCallingCoroutineRethrowsCancellation() =
        runTest {
            val deferred = async { dataSource.fetchIdentity(credentials) }
            deferred.cancel()

            val thrown =
                try {
                    deferred.await()
                    null
                } catch (caught: CancellationException) {
                    caught
                }

            assertTrue(thrown is CancellationException)
        }

    @Test
    fun noFailureRendersTheSentinelTokenOrTheProviderErrorText() =
        runTest {
            server.enqueue(MockMyAnimeListServer.loadFixture("error-unauthorized.json"), code = 401)

            val result = dataSource.fetchIdentity(credentials) as ProviderResult.Failure

            assertTrue(!result.toString().contains(sentinelToken))
            assertTrue(!result.toString().contains("invalid_token"))
        }
}
