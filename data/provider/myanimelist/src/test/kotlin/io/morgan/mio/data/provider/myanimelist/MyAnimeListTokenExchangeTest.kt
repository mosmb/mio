package io.morgan.mio.data.provider.myanimelist

import io.morgan.mio.core.testing.time.FakeClock
import io.morgan.mio.data.provider.api.ProviderCredentials
import io.morgan.mio.data.provider.api.ProviderFailure
import io.morgan.mio.data.provider.api.ProviderKey
import io.morgan.mio.data.provider.api.ProviderResult
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class MyAnimeListTokenExchangeTest {
    private val start = Instant.parse("2026-01-01T00:00:00Z")
    private val clock = FakeClock(start)
    private val configuration =
        MyAnimeListClientConfiguration(
            clientId = "mio-test-client",
            redirectUri = "mio://oauth/myanimelist",
        )
    private val sentinelToken = "fixture-obvious-sentinel-token"

    private lateinit var server: MockMyAnimeListServer
    private lateinit var tokenExchange: MyAnimeListTokenExchange

    private val credentials =
        ProviderCredentials(
            provider = ProviderKey.MY_ANIME_LIST,
            accessToken = sentinelToken,
            refreshToken = "fixture-obvious-sentinel-refresh-token",
            expiresAt = null,
            tokenType = "Bearer",
        )

    @Before
    fun setUp() {
        server = MockMyAnimeListServer()
        tokenExchange = MyAnimeListTokenExchange(server.oAuthService, configuration, clock)
    }

    @After
    fun tearDown() {
        server.close()
    }

    @Test
    fun theRepresentativeTokenPayloadMapsToExactCredentials() =
        runTest {
            server.enqueue(MockMyAnimeListServer.loadFixture("token-response.json"))

            val result =
                tokenExchange.exchangeAuthorizationCode("fixture-code", "fixture-verifier") as ProviderResult.Success

            assertEquals(ProviderKey.MY_ANIME_LIST, result.value.provider)
            assertEquals(sentinelToken, result.value.accessToken)
            assertEquals("fixture-obvious-sentinel-refresh-token", result.value.refreshToken)
            assertEquals("Bearer", result.value.tokenType)
            assertEquals(start + 3600.seconds, result.value.expiresAt)
        }

    @Test
    fun theCodeExchangeRequestIsFormEncodedAndCarriesNoClientSecret() =
        runTest {
            server.enqueue(MockMyAnimeListServer.loadFixture("token-response.json"))

            tokenExchange.exchangeAuthorizationCode("fixture-code", "fixture-verifier")

            val recorded = server.takeRequest()
            assertEquals("/token", recorded.target)
            assertEquals("POST", recorded.method)
            assertEquals("application/x-www-form-urlencoded", recorded.headers["Content-Type"])
            val body = recorded.body?.utf8().orEmpty()
            assertTrue(body.contains("grant_type=authorization_code"))
            assertTrue(body.contains("code=fixture-code"))
            assertTrue(body.contains("code_verifier=fixture-verifier"))
            assertTrue(body.contains("client_id=mio-test-client"))
            assertFalse(body.contains("client_secret"))
        }

    @Test
    fun theRefreshRequestSendsTheRefreshGrantAndNoClientSecret() =
        runTest {
            server.enqueue(MockMyAnimeListServer.loadFixture("token-response.json"))

            tokenExchange.refresh(credentials)

            val recorded = server.takeRequest()
            assertEquals("/token", recorded.target)
            assertEquals("POST", recorded.method)
            val body = recorded.body?.utf8().orEmpty()
            assertTrue(body.contains("grant_type=refresh_token"))
            assertTrue(body.contains("refresh_token=fixture-obvious-sentinel-refresh-token"))
            assertTrue(body.contains("client_id=mio-test-client"))
            assertFalse(body.contains("client_secret"))
            assertFalse(body.contains("code_verifier"))
        }

    @Test
    fun aMissingExpiryYieldsANullExpiresAtAndStillSucceeds() =
        runTest {
            server.enqueue("""{"token_type":"Bearer","access_token":"$sentinelToken"}""")

            val result =
                tokenExchange.exchangeAuthorizationCode("fixture-code", "fixture-verifier") as ProviderResult.Success

            assertNull(result.value.expiresAt)
            assertEquals(sentinelToken, result.value.accessToken)
        }

    @Test
    fun aNonPositiveExpiryYieldsANullExpiresAt() =
        runTest {
            server.enqueue("""{"token_type":"Bearer","expires_in":0,"access_token":"$sentinelToken"}""")

            val result =
                tokenExchange.exchangeAuthorizationCode("fixture-code", "fixture-verifier") as ProviderResult.Success

            assertNull(result.value.expiresAt)
        }

    @Test
    fun anAbsentTokenTypeFallsBackToBearerWhileABlankOneIsMalformed() =
        runTest {
            server.enqueue("""{"expires_in":3600,"access_token":"$sentinelToken"}""")
            val fallback =
                tokenExchange.exchangeAuthorizationCode("fixture-code", "fixture-verifier") as ProviderResult.Success
            assertEquals("Bearer", fallback.value.tokenType)

            server.enqueue("""{"token_type":"   ","expires_in":3600,"access_token":"$sentinelToken"}""")
            assertEquals(
                ProviderResult.Failure(ProviderFailure.MalformedResponse),
                tokenExchange.exchangeAuthorizationCode("fixture-code", "fixture-verifier"),
            )
        }

    @Test
    fun anAbsentRefreshTokenIsNulledRatherThanCarriedForward() =
        runTest {
            server.enqueue("""{"token_type":"Bearer","expires_in":3600,"access_token":"$sentinelToken"}""")

            val result = tokenExchange.refresh(credentials) as ProviderResult.Success

            assertNull(result.value.refreshToken)
        }

    @Test
    fun aBlankOrAbsentAccessTokenIsMalformed() =
        runTest {
            server.enqueue("""{"token_type":"Bearer","expires_in":3600}""")
            assertEquals(
                ProviderResult.Failure(ProviderFailure.MalformedResponse),
                tokenExchange.exchangeAuthorizationCode("fixture-code", "fixture-verifier"),
            )

            server.enqueue("""{"token_type":"Bearer","expires_in":3600,"access_token":"  "}""")
            assertEquals(
                ProviderResult.Failure(ProviderFailure.MalformedResponse),
                tokenExchange.exchangeAuthorizationCode("fixture-code", "fixture-verifier"),
            )
        }

    @Test
    fun everyCodeExchangeStatusMapsThroughTheExchangeTable() =
        runTest {
            val expectations =
                listOf(
                    400 to ProviderFailure.InvalidAuthorizationCallback,
                    401 to ProviderFailure.InvalidAuthorizationCallback,
                    403 to ProviderFailure.AuthorizationDenied,
                    429 to ProviderFailure.RateLimited(null),
                    503 to ProviderFailure.ServiceUnavailable,
                    404 to ProviderFailure.MalformedResponse,
                )

            expectations.forEach { (code, failure) ->
                server.enqueue(MockMyAnimeListServer.loadFixture("error-unauthorized.json"), code = code)
                assertEquals(
                    "status $code",
                    ProviderResult.Failure(failure),
                    tokenExchange.exchangeAuthorizationCode("fixture-code", "fixture-verifier"),
                )
            }
        }

    @Test
    fun everyRefreshStatusMapsThroughTheRefreshTable() =
        runTest {
            val expectations =
                listOf(
                    400 to ProviderFailure.AuthenticationRequired,
                    401 to ProviderFailure.AuthenticationRequired,
                    403 to ProviderFailure.AuthorizationDenied,
                    429 to ProviderFailure.RateLimited(null),
                    503 to ProviderFailure.ServiceUnavailable,
                    404 to ProviderFailure.MalformedResponse,
                )

            expectations.forEach { (code, failure) ->
                server.enqueue(MockMyAnimeListServer.loadFixture("error-unauthorized.json"), code = code)
                assertEquals("status $code", ProviderResult.Failure(failure), tokenExchange.refresh(credentials))
            }
        }

    @Test
    fun theExpiredGrantStatusDivergesBetweenTheTwoTokenPaths() =
        runTest {
            server.enqueue(MockMyAnimeListServer.loadFixture("error-unauthorized.json"), code = 400)
            val exchange = tokenExchange.exchangeAuthorizationCode("fixture-code", "fixture-verifier")

            server.enqueue(MockMyAnimeListServer.loadFixture("error-unauthorized.json"), code = 400)
            val refresh = tokenExchange.refresh(credentials)

            assertEquals(ProviderResult.Failure(ProviderFailure.InvalidAuthorizationCallback), exchange)
            assertEquals(ProviderResult.Failure(ProviderFailure.AuthenticationRequired), refresh)
        }

    @Test
    fun aRateLimitedRefreshCarriesTheRetryAfterDuration() =
        runTest {
            server.enqueue(
                MockMyAnimeListServer.loadFixture("error-unauthorized.json"),
                code = 429,
                headers = mapOf("Retry-After" to "120"),
            )

            assertEquals(
                ProviderResult.Failure(ProviderFailure.RateLimited(120.seconds)),
                tokenExchange.refresh(credentials),
            )
        }

    @Test
    fun refreshingWithoutARefreshTokenIsAuthenticationRequiredWithoutARequest() =
        runTest {
            val withoutRefresh =
                ProviderCredentials(
                    provider = ProviderKey.MY_ANIME_LIST,
                    accessToken = sentinelToken,
                    refreshToken = null,
                    expiresAt = null,
                    tokenType = "Bearer",
                )

            assertEquals(
                ProviderResult.Failure(ProviderFailure.AuthenticationRequired),
                tokenExchange.refresh(withoutRefresh),
            )
            assertEquals(0, server.requestCount())
        }

    @Test
    fun refreshingWithAnotherProvidersCredentialsThrowsBeforeAnyRequest() =
        runTest {
            val foreign =
                ProviderCredentials(
                    provider = ProviderKey.ANILIST,
                    accessToken = sentinelToken,
                    refreshToken = "fixture-obvious-sentinel-refresh-token",
                    expiresAt = null,
                    tokenType = "Bearer",
                )

            val thrown =
                try {
                    tokenExchange.refresh(foreign)
                    null
                } catch (caught: IllegalArgumentException) {
                    caught
                }

            assertTrue(thrown is IllegalArgumentException)
            assertEquals(0, server.requestCount())
        }

    @Test
    fun aMalformedJsonBodyIsMalformedResponse() =
        runTest {
            server.enqueue("not json at all")

            assertEquals(
                ProviderResult.Failure(ProviderFailure.MalformedResponse),
                tokenExchange.exchangeAuthorizationCode("fixture-code", "fixture-verifier"),
            )
        }

    @Test
    fun noFailureRendersTheSentinelToken() =
        runTest {
            server.enqueue(MockMyAnimeListServer.loadFixture("error-unauthorized.json"), code = 401)

            val result = tokenExchange.refresh(credentials) as ProviderResult.Failure

            assertFalse(result.toString().contains(sentinelToken))
            assertFalse(result.toString().contains("invalid_token"))
        }
}
