package io.morgan.mio.data.media.mapper

import io.morgan.mio.core.common.identity.StableOpaqueIdFactory
import io.morgan.mio.core.database.model.StoredConnectedProvider
import io.morgan.mio.core.testing.provider.providerMediaDetailsFixture
import io.morgan.mio.data.provider.api.ProviderKey
import io.morgan.mio.domain.model.MediaId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderMediaDetailsPersistenceMapperTest {
    @Test
    fun mapsProviderIdsInStableOrderedMioSpaceAndCopiesSnapshots() {
        val snapshot = ProviderMediaDetailsPersistenceMapper.map(providerMediaDetailsFixture())
        val rootId = MediaId(StableOpaqueIdFactory.create("anilist", "media", "101"))
        val relatedId = MediaId(StableOpaqueIdFactory.create("anilist", "media", "102"))

        assertEquals(rootId, snapshot.owner.media.id)
        assertEquals(listOf(relatedId), snapshot.owner.media.relatedMediaIds)
        assertEquals(listOf(relatedId), snapshot.related.map { item -> item.media.id })
        assertEquals(
            "101",
            snapshot.owner.providerIds
                .single()
                .remoteId,
        )
        assertEquals(
            "102",
            snapshot.related
                .single()
                .providerIds
                .single()
                .remoteId,
        )
        assertEquals(
            StoredConnectedProvider.ANILIST,
            snapshot.owner.providerIds
                .single()
                .provider,
        )
        assertTrue(snapshot.owner.releases.all { release -> release.mediaId == rootId })
    }

    @Test
    fun providerNamespacesRemainDistinct() {
        val anilist =
            ProviderMediaDetailsPersistenceMapper.map(
                providerMediaDetailsFixture(provider = ProviderKey.ANILIST),
            )
        val myAnimeList =
            ProviderMediaDetailsPersistenceMapper.map(
                providerMediaDetailsFixture(
                    provider = ProviderKey.MY_ANIME_LIST,
                    root =
                        io.morgan.mio.core.testing.provider.providerMediaFixture(
                            id = "anime/101",
                            relatedMediaIds = listOf("anime/102"),
                        ),
                    related =
                        listOf(
                            io.morgan.mio.core.testing.provider
                                .providerMediaFixture(id = "anime/102"),
                        ),
                ),
            )

        assertTrue(anilist.owner.media.id != myAnimeList.owner.media.id)
    }
}
