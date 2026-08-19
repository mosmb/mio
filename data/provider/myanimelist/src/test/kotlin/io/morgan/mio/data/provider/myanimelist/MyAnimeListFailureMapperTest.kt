package io.morgan.mio.data.provider.myanimelist

import io.morgan.mio.data.provider.api.ProviderFailure
import kotlinx.serialization.SerializationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.io.IOException
import java.net.SocketTimeoutException
import kotlin.time.Duration.Companion.seconds

class MyAnimeListFailureMapperTest {
    private val sentinel = "fixture-obvious-sentinel-token"

    @Test
    fun everyResourceStatusMapsToItsFailure() {
        assertEquals(ProviderFailure.MalformedResponse, MyAnimeListFailureMapper.mapHttpStatus(400, null))
        assertEquals(ProviderFailure.AuthenticationRequired, MyAnimeListFailureMapper.mapHttpStatus(401, null))
        assertEquals(ProviderFailure.AuthorizationDenied, MyAnimeListFailureMapper.mapHttpStatus(403, null))
        assertEquals(ProviderFailure.RateLimited(null), MyAnimeListFailureMapper.mapHttpStatus(429, null))
        assertEquals(ProviderFailure.ServiceUnavailable, MyAnimeListFailureMapper.mapHttpStatus(500, null))
        assertEquals(ProviderFailure.ServiceUnavailable, MyAnimeListFailureMapper.mapHttpStatus(599, null))
        assertEquals(ProviderFailure.MalformedResponse, MyAnimeListFailureMapper.mapHttpStatus(404, null))
        assertEquals(ProviderFailure.MalformedResponse, MyAnimeListFailureMapper.mapHttpStatus(302, null))
    }

    @Test
    fun everyAuthorizationCodeExchangeStatusMapsToItsFailure() {
        val map = MyAnimeListFailureMapper::mapAuthorizationCodeExchangeStatus
        assertEquals(ProviderFailure.InvalidAuthorizationCallback, map(400, null))
        assertEquals(ProviderFailure.InvalidAuthorizationCallback, map(401, null))
        assertEquals(ProviderFailure.AuthorizationDenied, map(403, null))
        assertEquals(ProviderFailure.RateLimited(null), map(429, null))
        assertEquals(ProviderFailure.ServiceUnavailable, map(503, null))
        assertEquals(ProviderFailure.MalformedResponse, map(404, null))
    }

    @Test
    fun everyRefreshStatusMapsToItsFailure() {
        val map = MyAnimeListFailureMapper::mapRefreshStatus
        assertEquals(ProviderFailure.AuthenticationRequired, map(400, null))
        assertEquals(ProviderFailure.AuthenticationRequired, map(401, null))
        assertEquals(ProviderFailure.AuthorizationDenied, map(403, null))
        assertEquals(ProviderFailure.RateLimited(null), map(429, null))
        assertEquals(ProviderFailure.ServiceUnavailable, map(503, null))
        assertEquals(ProviderFailure.MalformedResponse, map(404, null))
    }

    @Test
    fun theThreeStatusTablesDisagreeOnFourHundredAndFourHundredOne() {
        assertEquals(ProviderFailure.MalformedResponse, MyAnimeListFailureMapper.mapHttpStatus(400, null))
        assertEquals(
            ProviderFailure.InvalidAuthorizationCallback,
            MyAnimeListFailureMapper.mapAuthorizationCodeExchangeStatus(400, null),
        )
        assertEquals(ProviderFailure.AuthenticationRequired, MyAnimeListFailureMapper.mapRefreshStatus(400, null))

        assertEquals(ProviderFailure.AuthenticationRequired, MyAnimeListFailureMapper.mapHttpStatus(401, null))
        assertEquals(
            ProviderFailure.InvalidAuthorizationCallback,
            MyAnimeListFailureMapper.mapAuthorizationCodeExchangeStatus(401, null),
        )
        assertEquals(ProviderFailure.AuthenticationRequired, MyAnimeListFailureMapper.mapRefreshStatus(401, null))
    }

    @Test
    fun retryAfterIsParsedOnlyFromANonNegativeWholeNumber() {
        assertEquals(ProviderFailure.RateLimited(60.seconds), MyAnimeListFailureMapper.mapHttpStatus(429, "60"))
        assertEquals(ProviderFailure.RateLimited(30.seconds), MyAnimeListFailureMapper.mapHttpStatus(429, " 30 "))
        assertEquals(ProviderFailure.RateLimited(0.seconds), MyAnimeListFailureMapper.mapHttpStatus(429, "0"))
        assertEquals(ProviderFailure.RateLimited(null), MyAnimeListFailureMapper.mapHttpStatus(429, "-5"))
        assertEquals(ProviderFailure.RateLimited(null), MyAnimeListFailureMapper.mapHttpStatus(429, "not-a-number"))
        assertEquals(ProviderFailure.RateLimited(null), MyAnimeListFailureMapper.mapHttpStatus(429, ""))
        assertEquals(ProviderFailure.RateLimited(null), MyAnimeListFailureMapper.mapHttpStatus(429, null))
    }

    @Test
    fun everyThrowableMapsToItsFailure() {
        assertEquals(ProviderFailure.Timeout, MyAnimeListFailureMapper.mapThrowable(SocketTimeoutException(sentinel)))
        assertEquals(ProviderFailure.NetworkUnavailable, MyAnimeListFailureMapper.mapThrowable(IOException(sentinel)))
        assertEquals(
            ProviderFailure.MalformedResponse,
            MyAnimeListFailureMapper.mapThrowable(SerializationException(sentinel)),
        )
        assertEquals(
            ProviderFailure.MalformedResponse,
            MyAnimeListFailureMapper.mapThrowable(IllegalArgumentException(sentinel)),
        )
        assertEquals(
            ProviderFailure.MalformedResponse,
            MyAnimeListFailureMapper.mapThrowable(IllegalStateException(sentinel)),
        )
        assertEquals(ProviderFailure.Unexpected, MyAnimeListFailureMapper.mapThrowable(RuntimeException(sentinel)))
    }

    @Test
    fun noProducedFailureRendersTheSuppliedSentinel() {
        val failures =
            listOf(
                MyAnimeListFailureMapper.mapHttpStatus(401, sentinel),
                MyAnimeListFailureMapper.mapHttpStatus(429, sentinel),
                MyAnimeListFailureMapper.mapAuthorizationCodeExchangeStatus(400, sentinel),
                MyAnimeListFailureMapper.mapRefreshStatus(400, sentinel),
                MyAnimeListFailureMapper.mapThrowable(SocketTimeoutException(sentinel)),
                MyAnimeListFailureMapper.mapThrowable(IOException(sentinel)),
                MyAnimeListFailureMapper.mapThrowable(SerializationException(sentinel)),
                MyAnimeListFailureMapper.mapThrowable(RuntimeException(sentinel)),
            )

        failures.forEach { failure ->
            assertFalse(failure.toString().contains(sentinel))
        }
    }
}
