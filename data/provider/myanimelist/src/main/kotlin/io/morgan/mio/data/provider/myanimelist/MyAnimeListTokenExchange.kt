package io.morgan.mio.data.provider.myanimelist

import io.morgan.mio.data.provider.api.ProviderCredentials
import io.morgan.mio.data.provider.api.ProviderFailure
import io.morgan.mio.data.provider.api.ProviderKey
import io.morgan.mio.data.provider.api.ProviderResult
import kotlinx.coroutines.CancellationException
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds

internal class MyAnimeListTokenExchange(
    private val service: MyAnimeListOAuthService,
    private val configuration: MyAnimeListClientConfiguration,
    private val clock: Clock,
) {
    suspend fun exchangeAuthorizationCode(
        code: String,
        codeVerifier: String,
    ): ProviderResult<ProviderCredentials> =
        try {
            val response =
                service.exchangeAuthorizationCode(
                    configuration.clientId,
                    code,
                    codeVerifier,
                    configuration.redirectUri,
                    "authorization_code",
                )
            if (!response.isSuccessful) {
                return ProviderResult.Failure(
                    MyAnimeListFailureMapper.mapAuthorizationCodeExchangeStatus(
                        response.code(),
                        response.headers()["Retry-After"],
                    ),
                )
            }
            val body = response.body() ?: return ProviderResult.Failure(ProviderFailure.MalformedResponse)
            toCredentials(body)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (throwable: Throwable) {
            ProviderResult.Failure(MyAnimeListFailureMapper.mapThrowable(throwable))
        }

    suspend fun refresh(credentials: ProviderCredentials): ProviderResult<ProviderCredentials> {
        require(credentials.provider == ProviderKey.MY_ANIME_LIST)
        val refreshToken =
            credentials.refreshToken ?: return ProviderResult.Failure(ProviderFailure.AuthenticationRequired)
        return try {
            val response =
                service.refreshToken(
                    configuration.clientId,
                    refreshToken,
                    "refresh_token",
                )
            if (!response.isSuccessful) {
                return ProviderResult.Failure(
                    MyAnimeListFailureMapper.mapRefreshStatus(response.code(), response.headers()["Retry-After"]),
                )
            }
            val body = response.body() ?: return ProviderResult.Failure(ProviderFailure.MalformedResponse)
            toCredentials(body)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (throwable: Throwable) {
            ProviderResult.Failure(MyAnimeListFailureMapper.mapThrowable(throwable))
        }
    }

    private fun toCredentials(body: MyAnimeListTokenResponseDto): ProviderResult<ProviderCredentials> {
        val accessToken =
            body.accessToken?.takeIf(String::isNotBlank)
                ?: return ProviderResult.Failure(ProviderFailure.MalformedResponse)
        val tokenType =
            when {
                body.tokenType != null && body.tokenType.isNotBlank() -> body.tokenType
                body.tokenType == null -> "Bearer"
                else -> return ProviderResult.Failure(ProviderFailure.MalformedResponse)
            }
        val refreshToken = body.refreshToken?.takeIf(String::isNotBlank)
        val expiresAt = body.expiresIn?.takeIf { it > 0 }?.let { clock.now() + it.seconds }
        return ProviderResult.Success(
            ProviderCredentials(
                ProviderKey.MY_ANIME_LIST,
                accessToken,
                refreshToken,
                expiresAt,
                tokenType,
            ),
        )
    }
}
