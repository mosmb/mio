package io.morgan.mio.data.account.credentials

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.morgan.mio.data.provider.api.ProviderCredentialStorageException
import io.morgan.mio.data.provider.api.ProviderCredentials
import io.morgan.mio.data.provider.api.ProviderKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.security.KeyStore
import kotlin.time.Instant

@RunWith(AndroidJUnit4::class)
class SecureProviderCredentialStoreInstrumentedTest {
    private lateinit var context: Context
    private lateinit var preferences: android.content.SharedPreferences
    private lateinit var cipher: AndroidKeystoreCredentialCipher
    private lateinit var store: SecureProviderCredentialStore

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
        preferences.edit().clear().commit()
        cipher = AndroidKeystoreCredentialCipher(KEY_ALIAS)
        cipher.deleteKey()
        store = newStore(cipher)
    }

    @After
    fun tearDown() {
        runCatching { runBlocking { store.deleteAll() } }
        preferences.edit().clear().commit()
        runCatching { cipher.deleteKey() }
    }

    @Test
    fun realKeystoreStoreSurvivesReconstructionAndDoesNotPersistPlaintext() =
        runBlocking {
            val credentials = credentials(ProviderKey.ANILIST)
            store.write(credentials)

            val secondStore = newStore(AndroidKeystoreCredentialCipher(KEY_ALIAS))
            assertEquals(credentials.accessToken, secondStore.read(ProviderKey.ANILIST)?.accessToken)
            assertEquals(credentials.refreshToken, secondStore.read(ProviderKey.ANILIST)?.refreshToken)

            val rawPreferenceText =
                context.dataDir
                    .resolve("shared_prefs/$PREFERENCES_NAME.xml")
                    .readText()
            assertFalse(rawPreferenceText.contains(credentials.accessToken))
            assertFalse(rawPreferenceText.contains(credentials.refreshToken!!))

            val firstEnvelope = cipher.encrypt(CODEC.storageKey(ProviderKey.ANILIST), SAME_PLAINTEXT)
            val secondEnvelope = cipher.encrypt(CODEC.storageKey(ProviderKey.ANILIST), SAME_PLAINTEXT)
            assertNotEquals(firstEnvelope.toList(), secondEnvelope.toList())
        }

    @Test
    fun providerEnvelopeSwapFailsWithConstantRedactedError() =
        runBlocking {
            store.write(credentials(ProviderKey.ANILIST))
            val anilistEnvelope = preferences.getString(CODEC.storageKey(ProviderKey.ANILIST), null)
            assertTrue(anilistEnvelope != null)
            assertTrue(
                preferences
                    .edit()
                    .putString(CODEC.storageKey(ProviderKey.MY_ANIME_LIST), anilistEnvelope)
                    .commit(),
            )

            val failure = runCatching { store.read(ProviderKey.MY_ANIME_LIST) }.exceptionOrNull()
            assertTrue(failure is ProviderCredentialStorageException)
            assertEquals("Secure credential storage failed", failure?.message)
            assertEquals("ProviderCredentialStorageException([REDACTED])", failure.toString())
        }

    @Test
    fun deleteAndDeleteAllRemoveRecordsAndKeystoreAlias() =
        runBlocking {
            store.write(credentials(ProviderKey.ANILIST))
            store.write(credentials(ProviderKey.MY_ANIME_LIST))
            store.delete(ProviderKey.ANILIST)
            assertNull(store.read(ProviderKey.ANILIST))
            assertTrue(store.read(ProviderKey.MY_ANIME_LIST) != null)

            store.deleteAll()
            assertNull(store.read(ProviderKey.MY_ANIME_LIST))

            val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            assertFalse(keyStore.containsAlias(KEY_ALIAS))
        }

    private fun newStore(testCipher: AndroidKeystoreCredentialCipher): SecureProviderCredentialStore =
        SecureProviderCredentialStore(
            context = context,
            ioDispatcher = Dispatchers.IO,
            codec = CODEC,
            cipher = testCipher,
            preferencesName = PREFERENCES_NAME,
        )

    private fun credentials(provider: ProviderKey): ProviderCredentials =
        ProviderCredentials(
            provider = provider,
            accessToken = ACCESS_TOKEN,
            refreshToken = REFRESH_TOKEN,
            expiresAt = Instant.fromEpochMilliseconds(1_735_689_600_123),
            tokenType = "Bearer",
        )

    private companion object {
        const val PREFERENCES_NAME = "mio.task10.instrumented.credentials"
        const val KEY_ALIAS = "io.morgan.mio.task10.instrumented.credentials.v1"
        const val ACCESS_TOKEN = "task10-access-sentinel"
        const val REFRESH_TOKEN = "task10-refresh-sentinel"
        val SAME_PLAINTEXT = "same-plaintext".encodeToByteArray()
        val CODEC = CredentialRecordCodec()
    }
}
