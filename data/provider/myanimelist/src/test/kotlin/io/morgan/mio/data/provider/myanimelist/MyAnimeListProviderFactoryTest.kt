package io.morgan.mio.data.provider.myanimelist

import io.morgan.mio.core.network.NetworkClientFactory
import io.morgan.mio.core.testing.time.FakeClock
import io.morgan.mio.data.provider.api.ProviderKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Test
import kotlin.time.Instant

class MyAnimeListProviderFactoryTest {
    private fun factory() =
        MyAnimeListProviderFactory(
            networkClientFactory = NetworkClientFactory(),
            configuration =
                MyAnimeListClientConfiguration(
                    clientId = "mio-test-client",
                    redirectUri = "mio://oauth/myanimelist",
                ),
            clock = FakeClock(Instant.parse("2026-01-01T00:00:00Z")),
        )

    @Test
    fun createReturnsABindingWhoseEveryMemberReportsMyAnimeList() {
        val binding = factory().create()

        assertEquals(ProviderKey.MY_ANIME_LIST, binding.provider)
        assertEquals(ProviderKey.MY_ANIME_LIST, binding.authenticator.provider)
        assertEquals(ProviderKey.MY_ANIME_LIST, binding.identityDataSource.provider)
        assertEquals(ProviderKey.MY_ANIME_LIST, binding.initialImportDataSource.provider)
        assertEquals(ProviderKey.MY_ANIME_LIST, binding.mediaDetailDataSource.provider)
    }

    @Test
    fun twoCreateCallsProduceIndependentBindings() {
        val factory = factory()
        val first = factory.create()
        val second = factory.create()

        assertNotSame(first, second)
        assertNotSame(first.authenticator, second.authenticator)
        assertNotSame(first.identityDataSource, second.identityDataSource)
        assertNotSame(first.initialImportDataSource, second.initialImportDataSource)
        assertNotSame(first.mediaDetailDataSource, second.mediaDetailDataSource)
    }
}
