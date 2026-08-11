package io.morgan.mio.core.network

import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.Collections
import java.util.IdentityHashMap
import java.util.concurrent.TimeUnit

public class NetworkClientFactory(
    timeouts: NetworkTimeouts = NetworkTimeouts(),
    eventSink: NetworkEventSink = NetworkEventSink.None,
    json: Json = MioJson,
) {
    private val redactedEventInterceptor = RedactedNetworkEventInterceptor(eventSink)

    public val sharedClient: OkHttpClient =
        OkHttpClient
            .Builder()
            .connectTimeout(timeouts.connectMillis, TimeUnit.MILLISECONDS)
            .readTimeout(timeouts.readMillis, TimeUnit.MILLISECONDS)
            .writeTimeout(timeouts.writeMillis, TimeUnit.MILLISECONDS)
            .callTimeout(timeouts.callMillis, TimeUnit.MILLISECONDS)
            .retryOnConnectionFailure(true)
            .addInterceptor(redactedEventInterceptor)
            .build()

    public val json: Json = json

    public fun createRetrofit(
        baseUrl: HttpUrl,
        interceptors: List<Interceptor> = emptyList(),
    ): Retrofit {
        requireValidBaseUrl(baseUrl)
        val snapshot = interceptors.toList()
        requireUniqueInterceptors(snapshot)
        require(snapshot.none { interceptor -> interceptor is RedactedNetworkEventInterceptor }) {
            "The redacted network event interceptor is owned by NetworkClientFactory"
        }

        val clientBuilder = sharedClient.newBuilder()
        snapshot.forEach(clientBuilder::addInterceptor)

        return Retrofit
            .Builder()
            .baseUrl(baseUrl)
            .client(clientBuilder.build())
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
    }

    private fun requireValidBaseUrl(baseUrl: HttpUrl) {
        val localHost = baseUrl.host in setOf("localhost", "127.0.0.1", "::1")
        require(baseUrl.scheme == "https" || (baseUrl.scheme == "http" && localHost)) {
            "Network base URLs must use HTTPS except for loopback test hosts"
        }
        require(baseUrl.encodedPath.endsWith('/')) {
            "Network base URLs must end with a trailing slash"
        }
    }

    private fun requireUniqueInterceptors(interceptors: List<Interceptor>) {
        val seen = Collections.newSetFromMap(IdentityHashMap<Interceptor, Boolean>())
        require(interceptors.all(seen::add)) {
            "Provider interceptors must not contain duplicate instances"
        }
    }
}
