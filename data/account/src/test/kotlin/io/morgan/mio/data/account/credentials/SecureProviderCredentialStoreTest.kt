package io.morgan.mio.data.account.credentials

import android.content.Context
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import io.morgan.mio.data.provider.api.ProviderCredentialStorageException
import io.morgan.mio.data.provider.api.ProviderCredentials
import io.morgan.mio.data.provider.api.ProviderKey
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [35])
class SecureProviderCredentialStoreTest {
    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    @Test
    fun writeReadOverwriteDeleteAndProviderIsolation() =
        runTest {
            val cipher = XorCredentialCipher()
            val preferencesName = "task10.credentials.operations"
            val store = store(StandardTestDispatcher(testScheduler), cipher, preferencesName)
            val anilist = credentials(ProviderKey.ANILIST, "fixture-access-anilist")
            val replacement = credentials(ProviderKey.ANILIST, "fixture-access-replacement")
            val myAnimeList = credentials(ProviderKey.MY_ANIME_LIST, "fixture-access-myanimelist")

            store.write(anilist)
            store.write(myAnimeList)
            assertCredentials(anilist, store.read(ProviderKey.ANILIST))
            assertCredentials(myAnimeList, store.read(ProviderKey.MY_ANIME_LIST))
            store.write(replacement)
            assertCredentials(replacement, store.read(ProviderKey.ANILIST))

            val preferences = context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
            val anilistEnvelope = preferences.getString("credential.anilist", null)
            assertNotNull(anilistEnvelope)
            assertFalse(preferences.all.toString().contains("fixture-access-replacement"))

            store.delete(ProviderKey.ANILIST)
            assertNull(store.read(ProviderKey.ANILIST))
            assertCredentials(myAnimeList, store.read(ProviderKey.MY_ANIME_LIST))
            assertTrue(anilistEnvelope!!.isNotBlank())
        }

    @Test
    fun movingAnEnvelopeBetweenProvidersFailsWithoutExposingTheCause() =
        runTest {
            val preferencesName = "task10.credentials.move"
            val cipher = XorCredentialCipher()
            val store = store(StandardTestDispatcher(testScheduler), cipher, preferencesName)
            store.write(credentials(ProviderKey.ANILIST, "fixture-access-move"))
            val preferences = context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
            val envelope = preferences.getString("credential.anilist", null)
            preferences.edit().putString("credential.myanimelist", envelope).commit()

            val failure = runCatching { store.read(ProviderKey.MY_ANIME_LIST) }.exceptionOrNull()

            assertTrue(failure is ProviderCredentialStorageException)
            assertEquals("Secure credential storage failed", failure?.message)
            assertFalse(failure.toString().contains("fixture-access-move"))
        }

    @Test
    fun deleteAllAttemptsKeyDeletionAfterPreferenceFailure() =
        runTest {
            val cipher = XorCredentialCipher(failDelete = true)
            val preferences = context.getSharedPreferences("task10.credentials.delete-all", Context.MODE_PRIVATE)
            val store =
                SecureProviderCredentialStore(
                    CommitFailingPreferences(preferences),
                    StandardTestDispatcher(testScheduler),
                    CredentialRecordCodec(),
                    cipher,
                )

            val failure = runCatching { store.deleteAll() }.exceptionOrNull()

            assertTrue(failure is ProviderCredentialStorageException)
            assertEquals(1, cipher.deleteCalls)
        }

    @Test
    fun falseCommitIsNormalizedAndCancellationIsPropagated() =
        runTest {
            val commitPreferences = context.getSharedPreferences("task10.credentials.commit", Context.MODE_PRIVATE)
            val commitStore =
                SecureProviderCredentialStore(
                    CommitFailingPreferences(commitPreferences),
                    StandardTestDispatcher(testScheduler),
                    CredentialRecordCodec(),
                    XorCredentialCipher(),
                )
            val failure =
                runCatching {
                    commitStore.write(credentials(ProviderKey.ANILIST, "fixture-access-commit"))
                }.exceptionOrNull()
            assertTrue(failure is ProviderCredentialStorageException)

            val dispatcher = StandardTestDispatcher(testScheduler)
            val cancellableStore = store(dispatcher, XorCredentialCipher(), "task10.credentials.cancel")
            val read = async(dispatcher) { cancellableStore.read(ProviderKey.ANILIST) }
            read.cancel()
            try {
                read.await()
                throw AssertionError("Expected cancellation")
            } catch (cancellation: CancellationException) {
                assertTrue(read.isCancelled)
            }

            val expectedCancellation = CancellationException("Fixture cipher cancellation")
            val exactCancellationStore =
                store(
                    StandardTestDispatcher(testScheduler),
                    XorCredentialCipher(encryptFailure = expectedCancellation),
                    "task10.credentials.exact-cancel",
                )
            val propagated =
                runCatching {
                    exactCancellationStore.write(credentials(ProviderKey.ANILIST, "fixture-access-cancel"))
                }.exceptionOrNull()
            assertSame(expectedCancellation, propagated)
        }

    @Test
    @OptIn(ExperimentalCoroutinesApi::class)
    fun operationsDispatchThroughTheInjectedIoDispatcher() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val store = store(dispatcher, XorCredentialCipher(), "task10.credentials.dispatch")
            val write =
                async(start = CoroutineStart.UNDISPATCHED) {
                    store.write(credentials(ProviderKey.ANILIST, "fixture-access-dispatch"))
                }

            assertFalse(write.isCompleted)
            runCurrent()
            assertTrue(write.isCompleted)
        }

    private fun store(
        dispatcher: kotlinx.coroutines.CoroutineDispatcher,
        cipher: CredentialCipher,
        preferencesName: String,
    ): SecureProviderCredentialStore =
        SecureProviderCredentialStore(
            context = context,
            ioDispatcher = dispatcher,
            codec = CredentialRecordCodec(),
            cipher = cipher,
            preferencesName = preferencesName,
        )

    private fun credentials(
        provider: ProviderKey,
        accessToken: String,
    ): ProviderCredentials = ProviderCredentials(provider, accessToken, "fixture-refresh", null, "Bearer")

    private fun assertCredentials(
        expected: ProviderCredentials,
        actual: ProviderCredentials?,
    ) {
        assertNotNull(actual)
        assertEquals(expected.provider, actual?.provider)
        assertEquals(expected.accessToken, actual?.accessToken)
        assertEquals(expected.refreshToken, actual?.refreshToken)
        assertEquals(expected.expiresAt, actual?.expiresAt)
        assertEquals(expected.tokenType, actual?.tokenType)
    }

    private class XorCredentialCipher(
        private val failDelete: Boolean = false,
        private val encryptFailure: CancellationException? = null,
    ) : CredentialCipher {
        var deleteCalls: Int = 0

        override fun encrypt(
            providerStorageKey: String,
            plaintext: ByteArray,
        ): ByteArray {
            encryptFailure?.let { throw it }
            return transform(providerStorageKey, plaintext)
        }

        override fun decrypt(
            providerStorageKey: String,
            envelope: ByteArray,
        ): ByteArray {
            val mask = mask(providerStorageKey)
            require(envelope.firstOrNull() == mask)
            return envelope.drop(1).map { value -> (value.toInt() xor mask.toInt()).toByte() }.toByteArray()
        }

        override fun deleteKey() {
            deleteCalls += 1
            if (failDelete) error("fixture cipher failure")
        }

        private fun transform(
            providerStorageKey: String,
            bytes: ByteArray,
        ): ByteArray {
            val mask = mask(providerStorageKey)
            return byteArrayOf(mask) + bytes.map { value -> (value.toInt() xor mask.toInt()).toByte() }
        }

        private fun mask(providerStorageKey: String): Byte =
            providerStorageKey.fold(0) { result, character -> result xor character.code }.toByte()
    }

    private class CommitFailingPreferences(
        private val delegate: SharedPreferences,
    ) : SharedPreferences by delegate {
        override fun edit(): SharedPreferences.Editor {
            val editor = delegate.edit()
            return object : SharedPreferences.Editor by editor {
                override fun putString(
                    key: String?,
                    value: String?,
                ): SharedPreferences.Editor {
                    editor.putString(key, value)
                    return this
                }

                override fun clear(): SharedPreferences.Editor {
                    editor.clear()
                    return this
                }

                override fun commit(): Boolean = false
            }
        }
    }
}
