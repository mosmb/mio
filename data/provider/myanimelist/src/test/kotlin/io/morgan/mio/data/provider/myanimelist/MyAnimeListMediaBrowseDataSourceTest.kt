package io.morgan.mio.data.provider.myanimelist

import io.morgan.mio.data.provider.api.ProviderBrowseCategory
import io.morgan.mio.data.provider.api.ProviderCredentials
import io.morgan.mio.data.provider.api.ProviderKey
import io.morgan.mio.data.provider.api.ProviderMediaBrowseRequest
import io.morgan.mio.data.provider.api.ProviderMediaType
import io.morgan.mio.data.provider.api.ProviderResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MyAnimeListMediaBrowseDataSourceTest {
    private val credentials = ProviderCredentials(ProviderKey.MY_ANIME_LIST, "fixture", null, null, "Bearer")

    @Test fun usesSeasonAndPopularityEndpointsAndFiltersPublishingFromBoundedRanking() =
        runTest {
            val server = MockMyAnimeListServer()
            try {
                val source = MyAnimeListMediaBrowseDataSource(server.apiService)
                assertFalse(ProviderBrowseCategory.TRENDING in source.categories(ProviderMediaType.ANIME))
                server.enqueue("""{"data":[],"paging":{}}""")
                source.browse(
                    credentials,
                    ProviderMediaBrowseRequest(
                        ProviderMediaType.ANIME,
                        ProviderBrowseCategory.NEXT_SEASON,
                        2027,
                        "WINTER",
                    ),
                )
                assertTrue(server.takeRequest().target.startsWith("/anime/season/2027/winter?"))
                server.enqueue(
                    """
{"data":[{"node":{"id":12,
"title":"Finished",
"media_type":"manga",
"status":"finished"}},
{"node":{"id":13,
"title":"Publishing",
"media_type":"manga",
"status":"currently_publishing"}}],
"paging":{}}
""",
                )
                val result =
                    source.browse(
                        credentials,
                        ProviderMediaBrowseRequest(
                            ProviderMediaType.MANGA,
                            ProviderBrowseCategory.PUBLISHING,
                            2026,
                            "FALL",
                        ),
                    ) as ProviderResult.Success
                assertEquals(
                    "manga/13",
                    result.value
                        .single()
                        .id.value,
                )
                val request = server.takeRequest()
                assertTrue(request.target.startsWith("/manga/ranking?"))
                assertTrue(request.target.contains("limit=100"))
                assertTrue(request.target.contains("ranking_type=bypopularity"))
            } finally {
                server.close()
            }
        }
}
