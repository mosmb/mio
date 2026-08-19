package io.morgan.mio.data.provider.anilist

import io.morgan.mio.core.network.NetworkClientFactory
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest

internal class MockAniListServer {
    private val server = MockWebServer()
    val service: AniListGraphQlService

    init {
        server.start()
        val retrofit = NetworkClientFactory().createRetrofit(server.url("/"))
        service = retrofit.create(AniListGraphQlService::class.java)
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
            requireNotNull(MockAniListServer::class.java.classLoader?.getResourceAsStream("anilist/$name"))
                .bufferedReader()
                .use { reader -> reader.readText() }
    }
}
