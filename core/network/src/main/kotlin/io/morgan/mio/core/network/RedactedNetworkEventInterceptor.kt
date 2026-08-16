package io.morgan.mio.core.network

import okhttp3.Interceptor
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.Locale
import java.util.concurrent.CancellationException

internal class RedactedNetworkEventInterceptor(
    private val eventSink: NetworkEventSink,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): okhttp3.Response {
        val request = chain.request()
        val method = request.method.uppercase(Locale.US)
        val host = request.url.host
        recordSafely(NetworkEvent.Request(method = method, host = host))

        return try {
            val response = chain.proceed(request)
            recordSafely(
                NetworkEvent.Response(
                    method = method,
                    host = host,
                    statusCode = response.code,
                ),
            )
            response
        } catch (throwable: Throwable) {
            val category =
                when {
                    chain.call().isCanceled() -> NetworkFailureCategory.CANCELLED
                    throwable is SocketTimeoutException -> NetworkFailureCategory.TIMEOUT
                    throwable is IOException -> NetworkFailureCategory.IO
                    else -> NetworkFailureCategory.UNEXPECTED
                }
            recordSafely(NetworkEvent.Failure(method = method, host = host, category = category))
            throw throwable
        }
    }

    private fun recordSafely(event: NetworkEvent) {
        try {
            eventSink.record(event)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: RuntimeException) {
            // Observability must not turn a successful or faithfully failed HTTP call into a logging failure.
        }
    }
}
