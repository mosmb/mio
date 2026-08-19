package io.morgan.mio.data.provider.anilist

import io.morgan.mio.data.provider.api.ProviderCredentials
import io.morgan.mio.data.provider.api.ProviderKey
import io.morgan.mio.data.provider.api.ProviderResult
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CancellationException
import java.util.concurrent.atomic.AtomicInteger

class SerializedTokenRefreshTest {
    private val credentials =
        ProviderCredentials(
            provider = ProviderKey.ANILIST,
            accessToken = "fixture-token",
            refreshToken = null,
            expiresAt = null,
            tokenType = "Bearer",
        )

    @Test
    fun concurrentRefreshCallsNeverOverlap() =
        runTest {
            val inFlight = AtomicInteger(0)
            val maxObserved = AtomicInteger(0)
            val refresh =
                SerializedTokenRefresh { input ->
                    val current = inFlight.incrementAndGet()
                    maxObserved.updateAndGet { previous -> maxOf(previous, current) }
                    assertEquals(1, current)
                    inFlight.decrementAndGet()
                    ProviderResult.Success(input)
                }

            val deferredResults =
                List(20) {
                    async { refresh.refresh(credentials) }
                }
            val results = deferredResults.map { it.await() }

            assertTrue(results.all { result -> result == ProviderResult.Success(credentials) })
            assertEquals(1, maxObserved.get())
        }

    @Test
    fun cancellationFromTheAttemptPropagatesUnchanged() =
        runTest {
            val cancellation = CancellationException("fixture-cancellation")
            val refresh = SerializedTokenRefresh { throw cancellation }

            val thrown =
                try {
                    refresh.refresh(credentials)
                    null
                } catch (caught: CancellationException) {
                    caught
                }

            assertTrue(thrown === cancellation)
        }
}
