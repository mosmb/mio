package io.morgan.mio.core.network

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.http.GET

class NetworkClientFactoryTest {
    @Test
    fun sharedClientUsesTheFixedTimeoutPolicy() {
        val client = NetworkClientFactory().sharedClient

        assertEquals(15_000, client.connectTimeoutMillis)
        assertEquals(30_000, client.readTimeoutMillis)
        assertEquals(30_000, client.writeTimeoutMillis)
        assertEquals(60_000, client.callTimeoutMillis)
        assertTrue(client.retryOnConnectionFailure)
    }

    @Test
    fun derivedRetrofitsShareConnectionPoolAndDispatcherButIsolateProviderInterceptors() {
        val factory = NetworkClientFactory()
        val firstInterceptor = PassThroughInterceptor("first")
        val secondInterceptor = PassThroughInterceptor("second")
        val first = factory.createRetrofit("https://one.invalid/".toHttpUrl(), listOf(firstInterceptor))
        val second = factory.createRetrofit("https://two.invalid/".toHttpUrl(), listOf(secondInterceptor))
        val firstClient = first.callFactory() as OkHttpClient
        val secondClient = second.callFactory() as OkHttpClient

        assertSame(factory.sharedClient.connectionPool, firstClient.connectionPool)
        assertSame(factory.sharedClient.connectionPool, secondClient.connectionPool)
        assertSame(factory.sharedClient.dispatcher, firstClient.dispatcher)
        assertSame(factory.sharedClient.dispatcher, secondClient.dispatcher)
        assertNotSame(firstClient, secondClient)
        assertTrue(firstClient.interceptors.any { interceptor -> interceptor === firstInterceptor })
        assertTrue(firstClient.interceptors.none { interceptor -> interceptor === secondInterceptor })
        assertTrue(secondClient.interceptors.any { interceptor -> interceptor === secondInterceptor })
        assertTrue(secondClient.interceptors.none { interceptor -> interceptor === firstInterceptor })
    }

    @Test
    fun invalidBaseUrlsAndInterceptorListsAreRejected() {
        val factory = NetworkClientFactory()
        val interceptor = PassThroughInterceptor("duplicate")

        assertThrows<IllegalArgumentException> {
            factory.createRetrofit("http://remote.invalid/".toHttpUrl())
        }
        assertThrows<IllegalArgumentException> {
            factory.createRetrofit("https://remote.invalid/api".toHttpUrl())
        }
        assertThrows<IllegalArgumentException> {
            factory.createRetrofit("https://remote.invalid/".toHttpUrl(), listOf(interceptor, interceptor))
        }
        assertThrows<IllegalArgumentException> {
            factory.createRetrofit(
                "https://remote.invalid/".toHttpUrl(),
                listOf(RedactedNetworkEventInterceptor(NetworkEventSink.None)),
            )
        }
    }

    @Test
    fun retrofitSerializationConverterDecodesAResponseAndLeavesBodyHandlingToRetrofit() =
        runTest {
            val server = MockWebServer()
            server.enqueue(MockResponse.Builder().body("""{"value":"decoded"}""").build())
            server.start()

            try {
                val retrofit = NetworkClientFactory().createRetrofit(server.url("api/"))
                val response = retrofit.create(TestApi::class.java).value()

                assertEquals("decoded", response.value)
            } finally {
                server.close()
            }
        }

    @Test
    fun localLoopbackHttpUrlsAreAllowed() {
        val factory = NetworkClientFactory()

        factory.createRetrofit("http://localhost/".toHttpUrl())
        factory.createRetrofit("http://127.0.0.1/".toHttpUrl())
        factory.createRetrofit("http://[::1]/".toHttpUrl())
    }

    private class PassThroughInterceptor(
        private val label: String,
    ) : Interceptor {
        override fun intercept(chain: Interceptor.Chain): okhttp3.Response = chain.proceed(chain.request())
    }

    private inline fun <reified T : Throwable> assertThrows(block: () -> Unit): T {
        try {
            block()
        } catch (throwable: Throwable) {
            assertTrue(throwable is T)
            @Suppress("UNCHECKED_CAST")
            return throwable as T
        }
        throw AssertionError("Expected ${T::class.simpleName}")
    }

    @Serializable
    private data class TestResponse(
        val value: String,
    )

    private interface TestApi {
        @GET("api/value")
        suspend fun value(): TestResponse
    }
}
