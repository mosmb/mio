package io.morgan.mio.data.provider.anilist

import io.morgan.mio.data.provider.api.ProviderAuthenticator
import io.morgan.mio.data.provider.api.ProviderAuthorizationLaunch
import io.morgan.mio.data.provider.api.ProviderAuthorizationRedirect
import io.morgan.mio.data.provider.api.ProviderAuthorizationRequest
import io.morgan.mio.data.provider.api.ProviderAuthorizationTransaction
import io.morgan.mio.data.provider.api.ProviderCredentials
import io.morgan.mio.data.provider.api.ProviderFailure
import io.morgan.mio.data.provider.api.ProviderKey
import io.morgan.mio.data.provider.api.ProviderResult
import java.net.URLEncoder
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds

private const val DEFAULT_TOKEN_TYPE = "Bearer"

internal class AniListAuthenticator(
    private val configuration: AniListClientConfiguration,
    private val stateGenerator: AniListAuthorizationStateGenerator,
    private val clock: Clock,
    private val serializedTokenRefresh: SerializedTokenRefresh,
) : ProviderAuthenticator {
    override val provider: ProviderKey = ProviderKey.ANILIST

    override suspend fun beginAuthorization(
        request: ProviderAuthorizationRequest,
    ): ProviderResult<ProviderAuthorizationLaunch> {
        if (request.redirectUri != configuration.redirectUri) {
            return ProviderResult.Failure(ProviderFailure.InvalidAuthorizationCallback)
        }
        val state = stateGenerator.next()
        if (state.isBlank()) {
            return ProviderResult.Failure(ProviderFailure.Unexpected)
        }
        val url =
            // AniList uses the redirect registered with the client. Sending redirect_uri here
            // makes its live implicit-grant endpoint reject the request as unsupported_grant_type.
            "${configuration.authorizationEndpoint}" +
                "?client_id=${encode(configuration.clientId)}" +
                "&response_type=token" +
                "&state=${encode(state)}"
        return ProviderResult.Success(
            ProviderAuthorizationLaunch(
                authorizationUrl = url,
                transaction = ProviderAuthorizationTransaction(state = state, codeVerifier = null),
            ),
        )
    }

    override suspend fun completeAuthorization(
        transaction: ProviderAuthorizationTransaction,
        redirect: ProviderAuthorizationRedirect,
    ): ProviderResult<ProviderCredentials> {
        if (!redirect.redirectUri.startsWith(configuration.redirectUri)) {
            return ProviderResult.Failure(ProviderFailure.InvalidAuthorizationCallback)
        }

        val queryParameters = AniListRedirectParser.queryParameters(redirect.redirectUri)
        val error = queryParameters["error"]
        if (error != null) {
            return if (error == "access_denied") {
                ProviderResult.Failure(ProviderFailure.AuthorizationDenied)
            } else {
                ProviderResult.Failure(ProviderFailure.InvalidAuthorizationCallback)
            }
        }

        val fragmentParameters = AniListRedirectParser.fragmentParameters(redirect.redirectUri)
        val redirectState = fragmentParameters["state"]
        if (redirectState != null && redirectState != transaction.state) {
            return ProviderResult.Failure(ProviderFailure.InvalidAuthorizationCallback)
        }

        val accessToken = fragmentParameters["access_token"]
        if (accessToken.isNullOrBlank()) {
            return ProviderResult.Failure(ProviderFailure.InvalidAuthorizationCallback)
        }
        // AniList documents only `access_token` in the implicit-grant fragment; `token_type` is not
        // documented on any auth page. RFC 6749 section 4.2.2 makes it required, so a compliant
        // response carries it, and AniList's authenticated-request guide documents `Bearer` as the
        // only scheme it accepts. Falling back to `Bearer` therefore uses the one documented value
        // rather than inventing data, and avoids failing the whole connection over an absent field.
        // A present-but-blank value is still a malformed callback.
        val tokenType = fragmentParameters["token_type"]
        if (tokenType != null && tokenType.isBlank()) {
            return ProviderResult.Failure(ProviderFailure.InvalidAuthorizationCallback)
        }
        val expiresAt =
            fragmentParameters["expires_in"]
                ?.toLongOrNull()
                ?.takeIf { seconds -> seconds > 0 }
                ?.let { seconds -> clock.now() + seconds.seconds }

        return ProviderResult.Success(
            ProviderCredentials(
                provider = ProviderKey.ANILIST,
                accessToken = accessToken,
                refreshToken = null,
                expiresAt = expiresAt,
                tokenType = tokenType ?: DEFAULT_TOKEN_TYPE,
            ),
        )
    }

    override suspend fun refresh(credentials: ProviderCredentials): ProviderResult<ProviderCredentials> {
        require(credentials.provider == provider)
        return serializedTokenRefresh.refresh(credentials)
    }

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")
}
