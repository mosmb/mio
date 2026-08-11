package io.morgan.mio.core.network

import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.concurrent.CancellationException

class RedactedNetworkEventInterceptorTest {
    @Test
    fun eventsContainOnlyRedactedMethodHostStatusAndFailureCategory() {
        val events = mutableListOf<NetworkEvent>()
        val server = MockWebServer()
        val sentinel = "fixture-sensitive-value"
        server.enqueue(
            MockResponse
                .Builder()
                .addHeader("X-Sensitive", sentinel)
                .body(sentinel)
                .build(),
        )
        server.start()

        try {
            val client = NetworkClientFactory(eventSink = NetworkEventSink { event -> events += event }).sharedClient
            val request =
                Request
                    .Builder()
                    .url(server.url("path/$sentinel?query=$sentinel"))
                    .header("Authorization", sentinel)
                    .header("Cookie", sentinel)
                    .post(sentinel.toRequestBody())
                    .build()
            val response = client.newCall(request).execute()

            assertEquals(sentinel, response.body.string())
            assertEquals(NetworkEvent.Request("POST", "localhost"), events.first())
            assertTrue(events.last() is NetworkEvent.Response)
            assertEquals(200, (events.last() as NetworkEvent.Response).statusCode)
            assertFalse(events.joinToString().contains(sentinel))
            assertFalse(events.any { event -> event.toString().contains(sentinel) })
        } finally {
            server.close()
        }
    }

    @Test
    fun ioAndUnexpectedFailuresAreRethrownUnchanged() {
        val events = mutableListOf<NetworkEvent>()
        val expectedIo = IOException("fixture-sensitive-value")
        val ioFactory =
            NetworkClientFactory(eventSink = NetworkEventSink { event -> events += event })
        val ioClient =
            ioFactory
                .createRetrofit(
                    "http://127.0.0.1/".toHttpUrl(),
                    listOf(throwingInterceptor(expectedIo)),
                ).callFactory() as okhttp3.OkHttpClient
        val ioRequest = Request.Builder().url("http://127.0.0.1/".toHttpUrl()).build()

        val actualIo = assertThrows<IOException> { ioClient.newCall(ioRequest).execute() }

        assertSame(expectedIo, actualIo)
        assertTrue(events.last() == NetworkEvent.Failure("GET", "127.0.0.1", NetworkFailureCategory.IO))

        val expectedUnexpected = IllegalStateException("fixture-sensitive-value")
        val unexpectedEvents = mutableListOf<NetworkEvent>()
        val unexpectedClient =
            NetworkClientFactory(eventSink = NetworkEventSink { event -> unexpectedEvents += event })
                .createRetrofit(
                    "http://127.0.0.1/".toHttpUrl(),
                    listOf(throwingInterceptor(expectedUnexpected)),
                ).callFactory() as okhttp3.OkHttpClient

        val actualUnexpected =
            assertThrows<IllegalStateException> {
                unexpectedClient.newCall(ioRequest).execute()
            }

        assertSame(expectedUnexpected, actualUnexpected)
        assertEquals(
            NetworkEvent.Failure("GET", "127.0.0.1", NetworkFailureCategory.UNEXPECTED),
            unexpectedEvents.last(),
        )
        assertFalse(unexpectedEvents.joinToString().contains("fixture-sensitive-value"))
    }

    @Test
    fun cancelledCallsAreCategorizedWithoutChangingTheThrownInstance() {
        val events = mutableListOf<NetworkEvent>()
        val expected = IOException("fixture-sensitive-value")
        val cancelAndThrow =
            Interceptor { chain ->
                chain.call().cancel()
                throw expected
            }
        val client =
            NetworkClientFactory(eventSink = NetworkEventSink { event -> events += event })
                .createRetrofit("http://127.0.0.1/".toHttpUrl(), listOf(cancelAndThrow))
                .callFactory() as okhttp3.OkHttpClient

        val actual =
            assertThrows<IOException> {
                client.newCall(Request.Builder().url("http://127.0.0.1/".toHttpUrl()).build()).execute()
            }

        assertSame(expected, actual)
        assertEquals(
            NetworkEvent.Failure("GET", "127.0.0.1", NetworkFailureCategory.CANCELLED),
            events.last(),
        )
    }

    @Test
    fun socketTimeoutsAreCategorizedWithoutExposingTheirMessage() {
        val events = mutableListOf<NetworkEvent>()
        val expected = SocketTimeoutException("fixture-sensitive-value")
        val client =
            NetworkClientFactory(eventSink = NetworkEventSink { event -> events += event })
                .createRetrofit(
                    "http://127.0.0.1/".toHttpUrl(),
                    listOf(throwingInterceptor(expected)),
                ).callFactory() as okhttp3.OkHttpClient

        val actual =
            assertThrows<SocketTimeoutException> {
                client.newCall(Request.Builder().url("http://127.0.0.1/".toHttpUrl()).build()).execute()
            }

        assertSame(expected, actual)
        assertEquals(
            NetworkEvent.Failure("GET", "127.0.0.1", NetworkFailureCategory.TIMEOUT),
            events.last(),
        )
        assertFalse(events.joinToString().contains("fixture-sensitive-value"))
    }

    @Test
    fun eventModelsValidateTheirRedactedFields() {
        assertThrows<IllegalArgumentException> { NetworkEvent.Request("", "host") }
        assertThrows<IllegalArgumentException> { NetworkEvent.Request("GET", "") }
        assertThrows<IllegalArgumentException> { NetworkEvent.Response("GET", "host", 99) }
        assertThrows<IllegalArgumentException> { NetworkEvent.Response("GET", "host", 600) }
    }

    @Test
    fun ordinaryEventSinkFailureCannotBreakARequestButCancellationStillPropagates() {
        val server = MockWebServer()
        server.enqueue(MockResponse.Builder().body("response").build())
        server.start()

        try {
            val client =
                NetworkClientFactory(
                    eventSink = NetworkEventSink { throw IllegalStateException("sink failure") },
                ).sharedClient
            val response = client.newCall(Request.Builder().url(server.url("value")).build()).execute()

            assertEquals("response", response.body.string())
        } finally {
            server.close()
        }

        val cancellation = CancellationException("cancel sink")
        val cancellingClient =
            NetworkClientFactory(eventSink = NetworkEventSink { throw cancellation }).sharedClient

        val actual =
            assertThrows<CancellationException> {
                cancellingClient
                    .newCall(Request.Builder().url("http://127.0.0.1/".toHttpUrl()).build())
                    .execute()
            }
        assertSame(cancellation, actual)
    }

    private fun throwingInterceptor(throwable: Throwable): Interceptor = Interceptor { throw throwable }

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
}
