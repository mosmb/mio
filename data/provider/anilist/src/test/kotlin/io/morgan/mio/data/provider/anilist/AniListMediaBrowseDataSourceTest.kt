package io.morgan.mio.data.provider.anilist

import io.morgan.mio.data.provider.api.ProviderBrowseCategory
import io.morgan.mio.data.provider.api.ProviderCredentials
import io.morgan.mio.data.provider.api.ProviderKey
import io.morgan.mio.data.provider.api.ProviderMediaBrowseRequest
import io.morgan.mio.data.provider.api.ProviderMediaType
import io.morgan.mio.data.provider.api.ProviderResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AniListMediaBrowseDataSourceTest {
    private val credentials = ProviderCredentials(ProviderKey.ANILIST, "fixture", null, null, "Bearer")

    @Test fun requestsBoundedSeasonAndPublishingWithNoChapterReleaseInference() =
        runTest {
            val server = MockAniListServer()
            try {
                val source = AniListMediaBrowseDataSource(server.service)
                server.enqueue("""{"data":{"Page":{"media":[]}}}""")
                val result =
                    source.browse(
                        credentials,
                        ProviderMediaBrowseRequest(
                            ProviderMediaType.ANIME,
                            ProviderBrowseCategory.NEXT_SEASON,
                            2027,
                            "WINTER",
                        ),
                    )
                assertTrue(result is ProviderResult.Success)
                val seasonal = server.takeRequest().body!!.utf8()
                assertTrue(seasonal.contains("\"seasonYear\":2027"))
                assertTrue(seasonal.contains("\"season\":\"WINTER\""))
                assertTrue(seasonal.contains("\"perPage\":40"))
                server.enqueue(
                    """
{"data":{"Page":{"media":[{"id":203,
"type":"MANGA",
"format":"MANGA",
"status":"RELEASING",
"chapters":20,
"title":{"userPreferred":"Ongoing"}}]}}}
""",
                )
                val manga =
                    source.browse(
                        credentials,
                        ProviderMediaBrowseRequest(
                            ProviderMediaType.MANGA,
                            ProviderBrowseCategory.PUBLISHING,
                            2026,
                            "FALL",
                        ),
                    ) as ProviderResult.Success
                assertTrue(
                    manga.value
                        .single()
                        .releases
                        .isEmpty(),
                )
                assertTrue(
                    server
                        .takeRequest()
                        .body!!
                        .utf8()
                        .contains("\"status\":\"RELEASING\""),
                )
                assertFalse(ProviderBrowseCategory.NEXT_SEASON in source.categories(ProviderMediaType.MANGA))
                server.enqueue("""{"errors":[{"status":429}]}""")
                assertTrue(
                    source.browse(
                        credentials,
                        ProviderMediaBrowseRequest(
                            ProviderMediaType.ANIME,
                            ProviderBrowseCategory.TRENDING,
                            2026,
                            "FALL",
                        ),
                    ) is ProviderResult.Failure,
                )
            } finally {
                server.close()
            }
        }
}
