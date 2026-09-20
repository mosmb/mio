package io.morgan.mio.data.provider.anilist

import io.morgan.mio.core.testing.time.FakeClock
import io.morgan.mio.data.provider.api.ProviderAuthorizationRedirect
import io.morgan.mio.data.provider.api.ProviderAuthorizationRequest
import io.morgan.mio.data.provider.api.ProviderAuthorizationTransaction
import io.morgan.mio.data.provider.api.ProviderCredentials
import io.morgan.mio.data.provider.api.ProviderFailure
import io.morgan.mio.data.provider.api.ProviderKey
import io.morgan.mio.data.provider.api.ProviderResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Instant

class AniListAuthenticatorTest {
    private val configuration =
        AniListClientConfiguration(clientId = "mio-test-client", redirectUri = "mio://oauth/anilist")
    private val clock = FakeClock(Instant.parse("2026-01-01T00:00:00Z"))
    private val sentinelToken = "fixture-obvious-sentinel-token"

    private fun authenticator(state: String = "fixture-state") =
        AniListAuthenticator(
            configuration = configuration,
            stateGenerator = AniListAuthorizationStateGenerator { state },
            clock = clock,
            serializedTokenRefresh =
                SerializedTokenRefresh { _ ->
                    ProviderResult.Failure(ProviderFailure.AuthenticationRequired)
                },
        )

    @Test
    fun implicitLaunchUsesRegisteredRedirectWithoutSendingRedirectUri() =
        runTest {
            val result =
                authenticator("state-value").beginAuthorization(
                    ProviderAuthorizationRequest(redirectUri = configuration.redirectUri),
                )

            val success = result as ProviderResult.Success
            val expected =
                "${configuration.authorizationEndpoint}?client_id=mio-test-client" +
                    "&response_type=token&state=state-value"
            assertEquals(expected, success.value.authorizationUrl)
            assertEquals("state-value", success.value.transaction.state)
            assertNull(success.value.transaction.codeVerifier)
        }

    @Test
    fun mismatchedRequestedRedirectUriIsRejected() =
        runTest {
            val result = authenticator().beginAuthorization(ProviderAuthorizationRequest(redirectUri = "mio://other"))

            assertEquals(ProviderResult.Failure(ProviderFailure.InvalidAuthorizationCallback), result)
        }

    @Test
    fun successfulFragmentYieldsCredentialsWithNullRefreshToken() =
        runTest {
            val authenticator = authenticator("state-value")
            val transaction = ProviderAuthorizationTransaction(state = "state-value", codeVerifier = null)
            val redirect =
                ProviderAuthorizationRedirect(
                    redirectUri = "${configuration.redirectUri}#access_token=abc&token_type=Bearer&expires_in=3600",
                )

            val result = authenticator.completeAuthorization(transaction, redirect) as ProviderResult.Success

            assertEquals("abc", result.value.accessToken)
            assertNull(result.value.refreshToken)
            assertEquals("Bearer", result.value.tokenType)
            assertEquals(clock.now() + kotlin.time.Duration.parse("3600s"), result.value.expiresAt)
        }

    @Test
    fun missingExpiresInYieldsNullExpiryAndStillSucceeds() =
        runTest {
            val authenticator = authenticator("state-value")
            val transaction = ProviderAuthorizationTransaction(state = "state-value", codeVerifier = null)
            val redirect =
                ProviderAuthorizationRedirect(
                    redirectUri = "${configuration.redirectUri}#access_token=abc&token_type=Bearer",
                )

            val result = authenticator.completeAuthorization(transaction, redirect) as ProviderResult.Success

            assertNull(result.value.expiresAt)
        }

    @Test
    fun mismatchedStateMissingAccessTokenAndBlankAccessTokenAreRejected() =
        runTest {
            val authenticator = authenticator("state-value")
            val transaction = ProviderAuthorizationTransaction(state = "state-value", codeVerifier = null)

            assertEquals(
                ProviderResult.Failure(ProviderFailure.InvalidAuthorizationCallback),
                authenticator.completeAuthorization(
                    transaction,
                    ProviderAuthorizationRedirect(
                        "${configuration.redirectUri}#access_token=abc&token_type=Bearer&state=other",
                    ),
                ),
            )
            assertEquals(
                ProviderResult.Failure(ProviderFailure.InvalidAuthorizationCallback),
                authenticator.completeAuthorization(
                    transaction,
                    ProviderAuthorizationRedirect("${configuration.redirectUri}#token_type=Bearer&state=state-value"),
                ),
            )
            assertEquals(
                ProviderResult.Failure(ProviderFailure.InvalidAuthorizationCallback),
                authenticator.completeAuthorization(
                    transaction,
                    ProviderAuthorizationRedirect(
                        "${configuration.redirectUri}#access_token=&token_type=Bearer&state=state-value",
                    ),
                ),
            )
        }

    // AniList documents only `access_token` in the implicit-grant fragment. An absent `token_type`
    // must not fail the connection; `Bearer` is the only scheme its authenticated-request guide
    // documents. A present-but-blank value is still malformed.
    @Test
    fun absentTokenTypeFallsBackToBearerWhileABlankTokenTypeIsRejected() =
        runTest {
            val authenticator = authenticator("state-value")
            val transaction = ProviderAuthorizationTransaction(state = "state-value", codeVerifier = null)

            val absent =
                authenticator.completeAuthorization(
                    transaction,
                    ProviderAuthorizationRedirect(
                        "${configuration.redirectUri}#access_token=$sentinelToken&state=state-value",
                    ),
                )
            val credentials = (absent as ProviderResult.Success).value
            assertEquals("Bearer", credentials.tokenType)
            assertEquals(sentinelToken, credentials.accessToken)
            assertNull(credentials.refreshToken)

            val supplied =
                authenticator.completeAuthorization(
                    transaction,
                    ProviderAuthorizationRedirect(
                        "${configuration.redirectUri}#access_token=abc&token_type=MAC&state=state-value",
                    ),
                )
            assertEquals("MAC", (supplied as ProviderResult.Success).value.tokenType)

            val blank =
                authenticator.completeAuthorization(
                    transaction,
                    ProviderAuthorizationRedirect(
                        "${configuration.redirectUri}#access_token=abc&token_type=&state=state-value",
                    ),
                )
            assertEquals(ProviderResult.Failure(ProviderFailure.InvalidAuthorizationCallback), blank)
        }

    @Test
    fun accessDeniedErrorMapsToAuthorizationDeniedAndOtherErrorsAreInvalidCallback() =
        runTest {
            val authenticator = authenticator("state-value")
            val transaction = ProviderAuthorizationTransaction(state = "state-value", codeVerifier = null)

            assertEquals(
                ProviderResult.Failure(ProviderFailure.AuthorizationDenied),
                authenticator.completeAuthorization(
                    transaction,
                    ProviderAuthorizationRedirect("${configuration.redirectUri}?error=access_denied"),
                ),
            )
            assertEquals(
                ProviderResult.Failure(ProviderFailure.InvalidAuthorizationCallback),
                authenticator.completeAuthorization(
                    transaction,
                    ProviderAuthorizationRedirect("${configuration.redirectUri}?error=server_error"),
                ),
            )
        }

    @Test
    fun redirectForADifferentUriIsRejected() =
        runTest {
            val authenticator = authenticator("state-value")
            val transaction = ProviderAuthorizationTransaction(state = "state-value", codeVerifier = null)

            val result =
                authenticator.completeAuthorization(
                    transaction,
                    ProviderAuthorizationRedirect("mio://different#access_token=abc&token_type=Bearer"),
                )

            assertEquals(ProviderResult.Failure(ProviderFailure.InvalidAuthorizationCallback), result)
        }

    @Test
    fun malformedPercentEncodingInTheRedirectIsANormalizedFailureRatherThanAThrownException() =
        runTest {
            val authenticator = authenticator("state-value")
            val transaction = ProviderAuthorizationTransaction(state = "state-value", codeVerifier = null)

            listOf(
                "${configuration.redirectUri}#access_token=%zz&token_type=Bearer",
                "${configuration.redirectUri}#access_token=abc%&token_type=Bearer",
                "${configuration.redirectUri}?error=%zz",
            ).forEach { redirectUri ->
                val result =
                    authenticator.completeAuthorization(transaction, ProviderAuthorizationRedirect(redirectUri))

                assertEquals(
                    "redirect '$redirectUri' must normalize instead of throwing",
                    ProviderResult.Failure(ProviderFailure.InvalidAuthorizationCallback),
                    result,
                )
            }
        }

    @Test
    fun refreshAlwaysResolvesToAuthenticationRequired() =
        runTest {
            val credentials =
                ProviderCredentials(
                    provider = ProviderKey.ANILIST,
                    accessToken = sentinelToken,
                    refreshToken = null,
                    expiresAt = null,
                    tokenType = "Bearer",
                )

            val result = authenticator().refresh(credentials)

            assertEquals(ProviderResult.Failure(ProviderFailure.AuthenticationRequired), result)
        }

    @Test
    fun sensitiveTypesRenderOnlyRedactedToString() =
        runTest {
            val credentials =
                ProviderCredentials(
                    provider = ProviderKey.ANILIST,
                    accessToken = sentinelToken,
                    refreshToken = null,
                    expiresAt = null,
                    tokenType = "Bearer",
                )
            val launch =
                (
                    authenticator("state-value").beginAuthorization(
                        ProviderAuthorizationRequest(redirectUri = configuration.redirectUri),
                    ) as ProviderResult.Success
                ).value

            assertFalse(credentials.toString().contains(sentinelToken))
            assertFalse(launch.toString().contains(sentinelToken))
            assertFalse(launch.transaction.toString().contains(sentinelToken))
            assertTrue(credentials.toString().contains("REDACTED"))
        }
}
