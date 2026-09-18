package io.morgan.mio.data.provider.myanimelist

import io.morgan.mio.core.network.NetworkClientFactory
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.HttpUrl

internal class MockMyAnimeListServer {
    private val server = MockWebServer()
    val baseUrl: HttpUrl
    val apiService: MyAnimeListApiService
    val oAuthService: MyAnimeListOAuthService

    init {
        server.start()
        baseUrl = server.url("/")
        val retrofit = NetworkClientFactory().createRetrofit(baseUrl)
        apiService = retrofit.create(MyAnimeListApiService::class.java)
        oAuthService = retrofit.create(MyAnimeListOAuthService::class.java)
    }

    fun enqueue(
        body: String,
        code: Int = 200,
        headers: Map<String, String> = emptyMap(),
    ) {
        val builder = MockResponse.Builder().code(code).body(body)
        headers.forEach { (name, value) -> builder.addHeader(name, value) }
        server.enqueue(builder.build())
    }

    fun takeRequest(): RecordedRequest = server.takeRequest()

    fun requestCount(): Int = server.requestCount

    fun close() {
        server.close()
    }

    companion object {
        fun loadFixture(name: String): String =
            requireNotNull(MockMyAnimeListServer::class.java.classLoader?.getResourceAsStream("myanimelist/$name"))
                .bufferedReader()
                .use { reader -> reader.readText() }
    }
}
