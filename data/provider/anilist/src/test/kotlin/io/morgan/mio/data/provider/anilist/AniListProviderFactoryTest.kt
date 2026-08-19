package io.morgan.mio.data.provider.anilist

import io.morgan.mio.core.network.NetworkClientFactory
import io.morgan.mio.core.testing.time.FakeClock
import io.morgan.mio.data.provider.api.ProviderKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Test
import kotlin.time.Instant

class AniListProviderFactoryTest {
    private val configuration =
        AniListClientConfiguration(clientId = "mio-test-client", redirectUri = "mio://oauth/anilist")
    private val clock = FakeClock(Instant.parse("2026-01-01T00:00:00Z"))

    @Test
    fun createReturnsABindingWhereEveryMemberReportsAniList() {
        val factory = AniListProviderFactory(NetworkClientFactory(), configuration, clock)

        val binding = factory.create()

        assertEquals(ProviderKey.ANILIST, binding.provider)
        assertEquals(ProviderKey.ANILIST, binding.authenticator.provider)
        assertEquals(ProviderKey.ANILIST, binding.identityDataSource.provider)
        assertEquals(ProviderKey.ANILIST, binding.initialImportDataSource.provider)
    }

    @Test
    fun twoCreateCallsProduceIndependentBindings() {
        val factory = AniListProviderFactory(NetworkClientFactory(), configuration, clock)

        val first = factory.create()
        val second = factory.create()

        assertNotSame(first, second)
        assertNotSame(first.authenticator, second.authenticator)
        assertNotSame(first.identityDataSource, second.identityDataSource)
        assertNotSame(first.initialImportDataSource, second.initialImportDataSource)
    }
}
