package io.morgan.mio.di

import io.morgan.mio.core.network.NetworkClientFactory
import io.morgan.mio.data.provider.api.ProviderKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import kotlin.time.Clock

class ProductionProviderBindingResolverTest {
    @Test
    fun configurationIsLazyProviderSpecificAndCached() {
        val resolver =
            ProductionProviderBindingResolver(
                networkClientFactory = NetworkClientFactory(),
                clock = Clock.System,
                aniListClientId = "fixture-anilist-client",
                myAnimeListClientId = "fixture-myanimelist-client",
            )

        val aniList = resolver.bindingFor(ProviderKey.ANILIST)
        assertEquals(ProviderKey.ANILIST, aniList?.provider)
        assertSame(aniList, resolver.bindingFor(ProviderKey.ANILIST))

        val myAnimeList = resolver.bindingFor(ProviderKey.MY_ANIME_LIST)
        assertEquals(ProviderKey.MY_ANIME_LIST, myAnimeList?.provider)
        assertSame(myAnimeList, resolver.bindingFor(ProviderKey.MY_ANIME_LIST))
    }

    @Test
    fun absentClientConfigurationMakesOnlyThatProviderUnavailable() {
        val resolver =
            ProductionProviderBindingResolver(
                networkClientFactory = NetworkClientFactory(),
                clock = Clock.System,
                aniListClientId = " ",
                myAnimeListClientId = "fixture-myanimelist-client",
            )

        assertNull(resolver.bindingFor(ProviderKey.ANILIST))
        assertEquals(ProviderKey.MY_ANIME_LIST, resolver.bindingFor(ProviderKey.MY_ANIME_LIST)?.provider)
    }
}
