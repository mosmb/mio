package io.morgan.mio.data.provider.myanimelist

import io.morgan.mio.data.provider.api.ProviderFailure
import kotlinx.serialization.SerializationException
import java.io.IOException
import java.net.SocketTimeoutException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

internal object MyAnimeListFailureMapper {
    // Three tables exist because the same status code means different things on the resource path and
    // on the two token paths. A 400 from the token endpoint is an expired grant, not a malformed body.
    internal fun mapHttpStatus(
        code: Int,
        retryAfterHeader: String?,
    ): ProviderFailure =
        when {
            code == 400 -> ProviderFailure.MalformedResponse
            code == 401 -> ProviderFailure.AuthenticationRequired
            code == 403 -> ProviderFailure.AuthorizationDenied
            code == 429 -> ProviderFailure.RateLimited(retryAfter(retryAfterHeader))
            code in 500..599 -> ProviderFailure.ServiceUnavailable
            else -> ProviderFailure.MalformedResponse
        }

    internal fun mapAuthorizationCodeExchangeStatus(
        code: Int,
        retryAfterHeader: String?,
    ): ProviderFailure =
        when {
            code == 400 || code == 401 -> ProviderFailure.InvalidAuthorizationCallback
            code == 403 -> ProviderFailure.AuthorizationDenied
            code == 429 -> ProviderFailure.RateLimited(retryAfter(retryAfterHeader))
            code in 500..599 -> ProviderFailure.ServiceUnavailable
            else -> ProviderFailure.MalformedResponse
        }

    internal fun mapRefreshStatus(
        code: Int,
        retryAfterHeader: String?,
    ): ProviderFailure =
        when {
            code == 400 || code == 401 -> ProviderFailure.AuthenticationRequired
            code == 403 -> ProviderFailure.AuthorizationDenied
            code == 429 -> ProviderFailure.RateLimited(retryAfter(retryAfterHeader))
            code in 500..599 -> ProviderFailure.ServiceUnavailable
            else -> ProviderFailure.MalformedResponse
        }

    internal fun mapSyncHttpStatus(
        code: Int,
        retryAfterHeader: String?,
    ): ProviderFailure =
        when {
            code == 401 -> ProviderFailure.AuthenticationRequired
            code == 408 -> ProviderFailure.Timeout
            code == 409 -> ProviderFailure.MutationConflict
            code == 429 -> ProviderFailure.RateLimited(retryAfter(retryAfterHeader))
            code in 500..599 -> ProviderFailure.ServiceUnavailable
            code == 400 || code == 403 || code == 404 || code == 422 -> ProviderFailure.MutationRejected
            else -> ProviderFailure.MalformedResponse
        }

    internal fun mapThrowable(throwable: Throwable): ProviderFailure =
        when (throwable) {
            is SocketTimeoutException -> ProviderFailure.Timeout
            is IOException -> ProviderFailure.NetworkUnavailable
            is SerializationException -> ProviderFailure.MalformedResponse
            is IllegalArgumentException -> ProviderFailure.MalformedResponse
            is IllegalStateException -> ProviderFailure.MalformedResponse
            else -> ProviderFailure.Unexpected
        }

    private fun retryAfter(retryAfterHeader: String?): Duration? =
        retryAfterHeader
            ?.trim()
            ?.toLongOrNull()
            ?.takeIf { value -> value >= 0 }
            ?.seconds
}
