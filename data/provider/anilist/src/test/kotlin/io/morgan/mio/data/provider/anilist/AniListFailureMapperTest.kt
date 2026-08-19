package io.morgan.mio.data.provider.anilist

import io.morgan.mio.data.provider.api.ProviderFailure
import kotlinx.serialization.SerializationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.io.IOException
import java.net.SocketTimeoutException
import kotlin.time.Duration.Companion.seconds

class AniListFailureMapperTest {
    @Test
    fun httpStatusesMapToTheirDocumentedFailures() {
        assertEquals(ProviderFailure.AuthenticationRequired, AniListFailureMapper.mapHttpStatus(401, null))
        assertEquals(ProviderFailure.AuthorizationDenied, AniListFailureMapper.mapHttpStatus(403, null))
        assertEquals(ProviderFailure.RateLimited(null), AniListFailureMapper.mapHttpStatus(429, null))
        assertEquals(ProviderFailure.ServiceUnavailable, AniListFailureMapper.mapHttpStatus(500, null))
        assertEquals(ProviderFailure.ServiceUnavailable, AniListFailureMapper.mapHttpStatus(599, null))
        assertEquals(ProviderFailure.MalformedResponse, AniListFailureMapper.mapHttpStatus(418, null))
        assertEquals(ProviderFailure.MalformedResponse, AniListFailureMapper.mapHttpStatus(404, null))
    }

    @Test
    fun retryAfterHeaderIsParsedOrIgnored() {
        assertEquals(
            ProviderFailure.RateLimited(30.seconds),
            AniListFailureMapper.mapHttpStatus(429, "30"),
        )
        assertEquals(
            ProviderFailure.RateLimited(null),
            AniListFailureMapper.mapHttpStatus(429, "-5"),
        )
        assertEquals(
            ProviderFailure.RateLimited(null),
            AniListFailureMapper.mapHttpStatus(429, "not-a-number"),
        )
        assertEquals(
            ProviderFailure.RateLimited(null),
            AniListFailureMapper.mapHttpStatus(429, null),
        )
    }

    @Test
    fun throwablesMapToTheirDocumentedFailures() {
        assertEquals(ProviderFailure.Timeout, AniListFailureMapper.mapThrowable(SocketTimeoutException()))
        assertEquals(ProviderFailure.NetworkUnavailable, AniListFailureMapper.mapThrowable(IOException()))
        assertEquals(
            ProviderFailure.MalformedResponse,
            AniListFailureMapper.mapThrowable(SerializationException()),
        )
        assertEquals(
            ProviderFailure.MalformedResponse,
            AniListFailureMapper.mapThrowable(IllegalArgumentException()),
        )
        assertEquals(
            ProviderFailure.MalformedResponse,
            AniListFailureMapper.mapThrowable(IllegalStateException()),
        )
        assertEquals(ProviderFailure.Unexpected, AniListFailureMapper.mapThrowable(RuntimeException()))
    }

    @Test
    fun noProducedFailureStringContainsASuppliedSentinel() {
        val sentinel = "fixture-secret-value"

        val httpFailure = AniListFailureMapper.mapHttpStatus(404, sentinel)
        val throwableFailure = AniListFailureMapper.mapThrowable(RuntimeException(sentinel))

        assertFalse(httpFailure.toString().contains(sentinel))
        assertFalse(throwableFailure.toString().contains(sentinel))
    }
}
