package io.morgan.mio.data.provider.anilist

import io.morgan.mio.data.provider.api.ProviderFailure
import io.morgan.mio.data.provider.api.ProviderResult
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AniListMediaDetailsMapperTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun keepsAllTitleVariantsWhenAttachingRelatedIds() {
        val node =
            decode(
                "media-details.json",
            ).data!!.media!!.copy(title = AniListMediaTitleDto("Preferred", "Romaji", "English", "日本語"))
        val result = AniListMediaDetailsMapper.map(101, node) as ProviderResult.Success
        assertEquals("Preferred", result.value.root.title)
        assertEquals("English", result.value.root.titleEnglish)
        assertEquals("日本語", result.value.root.titleJapanese)
        assertEquals("Romaji", result.value.root.titleRomaji)
    }

    @Test
    fun mapsRootMetadataReleasesAndOrderedDeduplicatedRelations() {
        val node = decode("media-details.json").data!!.media!!
        val result = AniListMediaDetailsMapper.map(101, node) as ProviderResult.Success

        assertEquals("101", result.value.root.id.value)
        assertEquals(2026, result.value.root.releaseYear)
        assertEquals(87, result.value.root.scorePercent)
        assertEquals(2, result.value.root.releases.size)
        assertEquals(
            listOf("102"),
            result.value.root.relatedMediaIds
                .map { id -> id.value },
        )
        assertEquals(listOf("102"), result.value.related.map { media -> media.id.value })
    }

    @Test
    fun missingOptionalRelatedFieldsAreDroppedWithoutDroppingTheRoot() {
        val node = decode("media-details-missing-fields.json").data!!.media!!
        val result = AniListMediaDetailsMapper.map(101, node) as ProviderResult.Success

        assertEquals("101", result.value.root.id.value)
        assertTrue(result.value.related.isEmpty())
    }

    @Test
    fun unknownAndOutOfRangeOptionalValuesBecomeNull() {
        val node = decode("media-details-unknown-values.json").data!!.media!!
        val result = AniListMediaDetailsMapper.map(101, node) as ProviderResult.Success

        assertNull(result.value.root.releaseYear)
        assertNull(result.value.root.scorePercent)
        assertNull(result.value.root.totals.episodes)
        assertNull(result.value.root.totals.chapters)
    }

    @Test
    fun mismatchedRootIdIsMalformed() {
        val node = decode("media-details.json").data!!.media!!

        assertEquals(
            ProviderResult.Failure(ProviderFailure.MalformedResponse),
            AniListMediaDetailsMapper.map(999, node),
        )
    }

    private fun decode(name: String): AniListMediaDetailsResponseDto =
        json.decodeFromString(AniListMediaDetailsResponseDto.serializer(), MockAniListServer.loadFixture(name))
}
