package io.morgan.mio.core.testing.provider

import io.morgan.mio.data.provider.api.ProviderKey
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class FakeProviderCredentialStoreTest {
    @Test
    fun storesReplaceAndDeletesProvidersIndependently() =
        runTest {
            val store = FakeProviderCredentialStore()
            val anilist = providerCredentialsFixture(ProviderKey.ANILIST)
            val myAnimeList = providerCredentialsFixture(ProviderKey.MY_ANIME_LIST)

            store.write(anilist)
            store.write(myAnimeList)
            assertEquals(anilist, store.read(ProviderKey.ANILIST))
            assertEquals(myAnimeList, store.read(ProviderKey.MY_ANIME_LIST))

            store.delete(ProviderKey.ANILIST)
            assertNull(store.read(ProviderKey.ANILIST))
            assertEquals(myAnimeList, store.read(ProviderKey.MY_ANIME_LIST))

            store.deleteAll()
            assertNull(store.read(ProviderKey.MY_ANIME_LIST))
            assertEquals(listOf(ProviderKey.ANILIST, ProviderKey.MY_ANIME_LIST), store.writeProviders)
            assertEquals(listOf(ProviderKey.ANILIST), store.deleteProviders)
            assertEquals(1, store.deleteAllCount)
        }

    @Test
    fun failureScriptingAndDiagnosticsNeverRetainCredentialValues() =
        runTest {
            val store = FakeProviderCredentialStore()
            val credentials = providerCredentialsFixture()
            store.failNext(FakeProviderCredentialOperation.WRITE)

            runCatching { store.write(credentials) }
            assertEquals(1, store.writeCount)
            assertFalse(store.toString().contains("fixture-access-value"))
            assertFalse(store.writeProviders.toString().contains("fixture-access-value"))
        }
}
