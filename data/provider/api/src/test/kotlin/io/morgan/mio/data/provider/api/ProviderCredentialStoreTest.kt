package io.morgan.mio.data.provider.api

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ProviderCredentialStoreTest {
    @Test
    fun contractCanBeImplementedWithoutWideningTheProviderBoundary() =
        runTest {
            val credentials = ProviderCredentials(ProviderKey.ANILIST, "access", null, null, "Bearer")
            val store =
                object : ProviderCredentialStore {
                    private var value: ProviderCredentials? = null

                    override suspend fun read(provider: ProviderKey): ProviderCredentials? =
                        value?.takeIf { it.provider == provider }

                    override suspend fun write(credentials: ProviderCredentials) {
                        value = credentials
                    }

                    override suspend fun delete(provider: ProviderKey) {
                        if (value?.provider == provider) value = null
                    }

                    override suspend fun deleteAll() {
                        value = null
                    }
                }

            store.write(credentials)
            assertEquals(credentials, store.read(ProviderKey.ANILIST))
            store.delete(ProviderKey.ANILIST)
            assertNull(store.read(ProviderKey.ANILIST))
        }

    @Test
    fun storageFailureIsConstantAndRedacted() {
        val failure = ProviderCredentialStorageException()

        assertEquals("Secure credential storage failed", failure.message)
        assertEquals("ProviderCredentialStorageException([REDACTED])", failure.toString())
        assertNull(failure.cause)
        assertTrue(!failure.toString().contains("Secure credential storage failed"))
    }
}
