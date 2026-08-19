package io.morgan.mio.data.provider.anilist

import io.morgan.mio.data.provider.api.ProviderFailure
import kotlinx.serialization.SerializationException
import java.io.IOException
import java.net.SocketTimeoutException
import kotlin.time.Duration.Companion.seconds

internal object AniListFailureMapper {
    internal fun mapHttpStatus(
        code: Int,
        retryAfterHeader: String?,
    ): ProviderFailure =
        when {
            code == 401 -> ProviderFailure.AuthenticationRequired
            code == 403 -> ProviderFailure.AuthorizationDenied
            code == 429 -> ProviderFailure.RateLimited(retryAfter(retryAfterHeader))
            code in 500..599 -> ProviderFailure.ServiceUnavailable
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

    private fun retryAfter(retryAfterHeader: String?) =
        retryAfterHeader
            ?.trim()
            ?.toLongOrNull()
            ?.takeIf { value -> value >= 0 }
            ?.seconds
}
