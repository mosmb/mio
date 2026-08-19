package io.morgan.mio.data.provider.myanimelist

import io.morgan.mio.core.testing.time.FakeClock
import io.morgan.mio.data.provider.api.ProviderAuthorizationRedirect
import io.morgan.mio.data.provider.api.ProviderAuthorizationRequest
import io.morgan.mio.data.provider.api.ProviderAuthorizationTransaction
import io.morgan.mio.data.provider.api.ProviderCredentials
import io.morgan.mio.data.provider.api.ProviderFailure
import io.morgan.mio.data.provider.api.ProviderKey
import io.morgan.mio.data.provider.api.ProviderResult
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class MyAnimeListAuthenticatorTest {
    private val start = Instant.parse("2026-01-01T00:00:00Z")
    private val clock = FakeClock(start)
    private val configuration =
        MyAnimeListClientConfiguration(
            clientId = "mio-test-client",
            redirectUri = "mio://oauth/myanimelist",
        )
    private val sentinelToken = "fixture-obvious-sentinel-token"

    private lateinit var server: MockMyAnimeListServer
    private lateinit var authenticator: MyAnimeListAuthenticator

    @Before
    fun setUp() {
        server = MockMyAnimeListServer()
        val tokenExchange = MyAnimeListTokenExchange(server.oAuthService, configuration, clock)
        authenticator =
            MyAnimeListAuthenticator(
                configuration = configuration,
                secretGenerator = FakeSecretGenerator(),
                tokenExchange = tokenExchange,
                serializedTokenRefresh = SerializedTokenRefresh { input -> tokenExchange.refresh(input) },
            )
    }

    @After
    fun tearDown() {
        server.close()
    }

    private suspend fun launch() =
        (
            authenticator.beginAuthorization(
                ProviderAuthorizationRequest("mio://oauth/myanimelist"),
            ) as ProviderResult.Success
        ).value

    @Test
    fun theLaunchUrlCarriesEveryPkceParameterInOrder() =
        runTest {
            val launch = launch()

            assertEquals(
                "https://myanimelist.net/v1/oauth2/authorize" +
                    "?response_type=code" +
                    "&client_id=mio-test-client" +
                    "&code_challenge=$FIRST_VERIFIER" +
                    "&code_challenge_method=plain" +
                    "&state=$FIRST_STATE" +
                    "&redirect_uri=mio%3A%2F%2Foauth%2Fmyanimelist",
                launch.authorizationUrl,
            )
        }

    @Test
    fun theTransactionCarriesTheStateAndAPkceLengthCodeVerifier() =
        runTest {
            val launch = launch()

            assertEquals(FIRST_STATE, launch.transaction.state)
            assertNotNull(launch.transaction.codeVerifier)
            assertTrue(launch.transaction.codeVerifier!!.length in 43..128)
            assertTrue(launch.authorizationUrl.contains("code_challenge=${launch.transaction.codeVerifier}"))
        }

    @Test
    fun aMismatchedRequestedRedirectUriIsAnInvalidCallback() =
        runTest {
            val result = authenticator.beginAuthorization(ProviderAuthorizationRequest("mio://oauth/other"))

            assertEquals(
                ProviderResult.Failure(ProviderFailure.InvalidAuthorizationCallback),
                result,
            )
        }

    @Test
    fun aSuccessfulRedirectExchangesTheCodeAndReturnsCredentials() =
        runTest {
            server.enqueue(MockMyAnimeListServer.loadFixture("token-response.json"))
            val launch = launch()

            val result =
                authenticator.completeAuthorization(
                    launch.transaction,
                    ProviderAuthorizationRedirect(
                        "mio://oauth/myanimelist?code=fixture-code&state=${launch.transaction.state}",
                    ),
                )

            val credentials = (result as ProviderResult.Success).value
            assertEquals(ProviderKey.MY_ANIME_LIST, credentials.provider)
            assertEquals(sentinelToken, credentials.accessToken)
            assertEquals("fixture-obvious-sentinel-refresh-token", credentials.refreshToken)
            assertEquals("Bearer", credentials.tokenType)
            assertEquals(start + 3600.seconds, credentials.expiresAt)

            val recorded = server.takeRequest()
            assertEquals("/token", recorded.target)
            assertEquals("POST", recorded.method)
            val body = recorded.body?.utf8().orEmpty()
            assertTrue(body.contains("grant_type=authorization_code"))
            assertTrue(body.contains("code_verifier=${launch.transaction.codeVerifier}"))
            assertTrue(body.contains("client_id=mio-test-client"))
            assertFalse(body.contains("client_secret"))
        }

    @Test
    fun everyMalformedRedirectIsAnInvalidCallback() =
        runTest {
            val launch = launch()
            val state = launch.transaction.state

            val redirects =
                listOf(
                    "mio://oauth/other?code=fixture-code&state=$state",
                    "mio://oauth/myanimelist?code=fixture-code",
                    "mio://oauth/myanimelist?code=fixture-code&state=",
                    "mio://oauth/myanimelist?code=fixture-code&state=fixture-wrong-state",
                    "mio://oauth/myanimelist?state=$state",
                    "mio://oauth/myanimelist?code=&state=$state",
                )

            redirects.forEach { redirect ->
                assertEquals(
                    redirect,
                    ProviderResult.Failure(ProviderFailure.InvalidAuthorizationCallback),
                    authenticator.completeAuthorization(
                        launch.transaction,
                        ProviderAuthorizationRedirect(redirect),
                    ),
                )
            }
            assertEquals(0, server.requestCount())
        }

    @Test
    fun aNullCodeVerifierOnTheTransactionIsAnInvalidCallback() =
        runTest {
            val launch = launch()
            val withoutVerifier =
                ProviderAuthorizationTransaction(state = launch.transaction.state, codeVerifier = null)

            val result =
                authenticator.completeAuthorization(
                    withoutVerifier,
                    ProviderAuthorizationRedirect(
                        "mio://oauth/myanimelist?code=fixture-code&state=${launch.transaction.state}",
                    ),
                )

            assertEquals(ProviderResult.Failure(ProviderFailure.InvalidAuthorizationCallback), result)
            assertEquals(0, server.requestCount())
        }

    @Test
    fun accessDeniedIsAuthorizationDeniedWhileAnotherErrorIsAnInvalidCallback() =
        runTest {
            val launch = launch()
            val state = launch.transaction.state

            assertEquals(
                ProviderResult.Failure(ProviderFailure.AuthorizationDenied),
                authenticator.completeAuthorization(
                    launch.transaction,
                    ProviderAuthorizationRedirect("mio://oauth/myanimelist?error=access_denied&state=$state"),
                ),
            )
            assertEquals(
                ProviderResult.Failure(ProviderFailure.InvalidAuthorizationCallback),
                authenticator.completeAuthorization(
                    launch.transaction,
                    ProviderAuthorizationRedirect("mio://oauth/myanimelist?error=server_error&state=$state"),
                ),
            )
            assertEquals(0, server.requestCount())
        }

    @Test
    fun refreshingWithAnotherProvidersCredentialsThrows() =
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
                    authenticator.refresh(foreign)
                    null
                } catch (caught: IllegalArgumentException) {
                    caught
                }

            assertNotNull(thrown)
            assertEquals(0, server.requestCount())
        }

    @Test
    fun refreshWithoutARefreshTokenResolvesToAuthenticationRequiredWithoutARequest() =
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
                authenticator.refresh(withoutRefresh),
            )
            assertEquals(0, server.requestCount())
        }

    @Test
    fun theRedactedTypesNeverRenderTheSentinelToken() =
        runTest {
            server.enqueue(MockMyAnimeListServer.loadFixture("token-response.json"))
            val launch = launch()
            val credentials =
                (
                    authenticator.completeAuthorization(
                        launch.transaction,
                        ProviderAuthorizationRedirect(
                            "mio://oauth/myanimelist?code=fixture-code&state=${launch.transaction.state}",
                        ),
                    ) as ProviderResult.Success
                ).value

            assertEquals("ProviderAuthorizationLaunch([REDACTED])", launch.toString())
            assertEquals("ProviderAuthorizationTransaction([REDACTED])", launch.transaction.toString())
            assertEquals("ProviderCredentials([REDACTED])", credentials.toString())
            assertFalse(launch.toString().contains(launch.transaction.state))
            assertFalse(credentials.toString().contains(sentinelToken))
        }

    @Test
    fun aBlankGeneratedStateIsUnexpectedRatherThanALaunch() =
        runTest {
            val blankGenerator = MyAnimeListAuthorizationSecretGenerator { "" }
            val tokenExchange = MyAnimeListTokenExchange(server.oAuthService, configuration, clock)
            val blankAuthenticator =
                MyAnimeListAuthenticator(
                    configuration = configuration,
                    secretGenerator = blankGenerator,
                    tokenExchange = tokenExchange,
                    serializedTokenRefresh = SerializedTokenRefresh { input -> tokenExchange.refresh(input) },
                )

            assertEquals(
                ProviderResult.Failure(ProviderFailure.Unexpected),
                blankAuthenticator.beginAuthorization(ProviderAuthorizationRequest("mio://oauth/myanimelist")),
            )
        }

    @Test
    fun aTooShortGeneratedCodeVerifierIsUnexpectedRatherThanALaunch() =
        runTest {
            var call = 0
            val shortVerifierGenerator =
                MyAnimeListAuthorizationSecretGenerator {
                    call += 1
                    if (call == 1) FIRST_STATE else "too-short"
                }
            val tokenExchange = MyAnimeListTokenExchange(server.oAuthService, configuration, clock)
            val shortAuthenticator =
                MyAnimeListAuthenticator(
                    configuration = configuration,
                    secretGenerator = shortVerifierGenerator,
                    tokenExchange = tokenExchange,
                    serializedTokenRefresh = SerializedTokenRefresh { input -> tokenExchange.refresh(input) },
                )

            assertEquals(
                ProviderResult.Failure(ProviderFailure.Unexpected),
                shortAuthenticator.beginAuthorization(ProviderAuthorizationRequest("mio://oauth/myanimelist")),
            )
        }

    private class FakeSecretGenerator : MyAnimeListAuthorizationSecretGenerator {
        private var counter = 0

        override fun next(): String = if (counter++ == 0) FIRST_STATE else FIRST_VERIFIER
    }

    private companion object {
        // Both are URL-safe so URLEncoder leaves them unchanged, which keeps the launch-URL
        // assertion exact. The verifier is padded to PKCE's documented 43-character minimum.
        val FIRST_STATE: String = "fixture-state".padEnd(43, 'a')
        val FIRST_VERIFIER: String = "fixture-verifier".padEnd(43, 'b')
    }
}
