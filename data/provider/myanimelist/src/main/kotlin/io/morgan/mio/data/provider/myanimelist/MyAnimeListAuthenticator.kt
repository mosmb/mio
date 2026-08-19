package io.morgan.mio.data.provider.myanimelist

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

internal class MyAnimeListAuthenticator(
    private val configuration: MyAnimeListClientConfiguration,
    private val secretGenerator: MyAnimeListAuthorizationSecretGenerator,
    private val tokenExchange: MyAnimeListTokenExchange,
    private val serializedTokenRefresh: SerializedTokenRefresh,
) : ProviderAuthenticator {
    override val provider: ProviderKey = ProviderKey.MY_ANIME_LIST

    override suspend fun beginAuthorization(
        request: ProviderAuthorizationRequest,
    ): ProviderResult<ProviderAuthorizationLaunch> {
        if (request.redirectUri != configuration.redirectUri) {
            return ProviderResult.Failure(ProviderFailure.InvalidAuthorizationCallback)
        }
        val state = secretGenerator.next()
        if (state.isBlank()) {
            return ProviderResult.Failure(ProviderFailure.Unexpected)
        }
        val codeVerifier = secretGenerator.next()
        if (codeVerifier.length !in 43..128) {
            return ProviderResult.Failure(ProviderFailure.Unexpected)
        }
        val url =
            "${configuration.authorizationEndpoint}" +
                "?response_type=code" +
                "&client_id=${encode(configuration.clientId)}" +
                "&code_challenge=${encode(codeVerifier)}" +
                "&code_challenge_method=plain" +
                "&state=${encode(state)}" +
                "&redirect_uri=${encode(configuration.redirectUri)}"
        return ProviderResult.Success(
            ProviderAuthorizationLaunch(
                authorizationUrl = url,
                transaction = ProviderAuthorizationTransaction(state = state, codeVerifier = codeVerifier),
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

        val parameters = MyAnimeListRedirectParser.queryParameters(redirect.redirectUri)
        val error = parameters["error"]
        if (error != null) {
            return if (error == "access_denied") {
                ProviderResult.Failure(ProviderFailure.AuthorizationDenied)
            } else {
                ProviderResult.Failure(ProviderFailure.InvalidAuthorizationCallback)
            }
        }

        val state = parameters["state"]
        if (state.isNullOrBlank() || state != transaction.state) {
            return ProviderResult.Failure(ProviderFailure.InvalidAuthorizationCallback)
        }

        val code = parameters["code"]
        if (code.isNullOrBlank()) {
            return ProviderResult.Failure(ProviderFailure.InvalidAuthorizationCallback)
        }

        val codeVerifier =
            transaction.codeVerifier ?: return ProviderResult.Failure(ProviderFailure.InvalidAuthorizationCallback)
        return tokenExchange.exchangeAuthorizationCode(code, codeVerifier)
    }

    override suspend fun refresh(credentials: ProviderCredentials): ProviderResult<ProviderCredentials> {
        require(credentials.provider == provider)
        return serializedTokenRefresh.refresh(credentials)
    }

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")
}
