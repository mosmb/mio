package io.morgan.mio.data.provider.myanimelist

import io.morgan.mio.data.provider.api.ProviderFailure
import io.morgan.mio.data.provider.api.ProviderMediaId
import io.morgan.mio.data.provider.api.ProviderResult
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MyAnimeListMediaDetailsMapperTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun mapsAnimeRootAndOrdersAnimeBeforeMangaRelations() {
        val response = decode("anime-details.json")
        val result = MyAnimeListMediaDetailsMapper.map("anime/1", response) as ProviderResult.Success

        assertEquals("anime/1", result.value.root.id.value)
        assertEquals(87, result.value.root.scorePercent)
        assertEquals(2024, result.value.root.releaseYear)
        assertEquals(listOf("anime/2", "manga/3"), result.value.related.map { media -> media.id.value })
        assertEquals(result.value.related.map { media -> media.id }, result.value.root.relatedMediaIds)
    }

    @Test
    fun mapsMangaAndUsesOptionalYearAndScoreNormalization() {
        val result =
            MyAnimeListMediaDetailsMapper.map("manga/7", decode("manga-details.json")) as ProviderResult.Success

        assertEquals("manga/7", result.value.root.id.value)
        assertEquals(91, result.value.root.scorePercent)
        assertEquals(2018, result.value.root.releaseYear)
        assertEquals(listOf(ProviderMediaId("anime/4")), result.value.root.relatedMediaIds)
    }

    @Test
    fun malformedOptionalNodesAreDroppedAndMalformedRootFails() {
        val missing =
            MyAnimeListMediaDetailsMapper.map(
                "anime/1",
                decode("media-details-missing-fields.json"),
            ) as ProviderResult.Success
        assertTrue(missing.value.related.isEmpty())
        assertNull(missing.value.root.scorePercent)

        assertEquals(
            ProviderResult.Failure(ProviderFailure.MalformedResponse),
            MyAnimeListMediaDetailsMapper.map("anime/0", decode("anime-details.json")),
        )
        assertEquals(
            ProviderResult.Failure(ProviderFailure.MalformedResponse),
            MyAnimeListMediaDetailsMapper.map("not-an-id", decode("anime-details.json")),
        )
    }

    private fun decode(name: String): MyAnimeListMediaDetailsResponseDto =
        json.decodeFromString(
            MyAnimeListMediaDetailsResponseDto.serializer(),
            MockMyAnimeListServer.loadFixture(name),
        )
}
