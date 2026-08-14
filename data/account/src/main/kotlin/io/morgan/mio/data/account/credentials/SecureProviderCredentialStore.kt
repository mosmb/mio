package io.morgan.mio.data.account.credentials

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import io.morgan.mio.core.common.di.AppScope
import io.morgan.mio.core.common.di.ApplicationContext
import io.morgan.mio.core.common.di.IoDispatcher
import io.morgan.mio.data.provider.api.ProviderCredentialStorageException
import io.morgan.mio.data.provider.api.ProviderCredentialStore
import io.morgan.mio.data.provider.api.ProviderCredentials
import io.morgan.mio.data.provider.api.ProviderKey
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

internal class SecureProviderCredentialStore internal constructor(
    private val preferences: SharedPreferences,
    private val ioDispatcher: CoroutineDispatcher,
    private val codec: CredentialRecordCodec,
    private val cipher: CredentialCipher,
) : ProviderCredentialStore {
    @Inject
    internal constructor(
        @ApplicationContext context: Context,
        @IoDispatcher ioDispatcher: CoroutineDispatcher,
        codec: CredentialRecordCodec,
        cipher: CredentialCipher,
    ) : this(
        preferences = context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE),
        ioDispatcher = ioDispatcher,
        codec = codec,
        cipher = cipher,
    )

    internal constructor(
        context: Context,
        ioDispatcher: CoroutineDispatcher,
        codec: CredentialRecordCodec,
        cipher: CredentialCipher,
        preferencesName: String,
    ) : this(
        preferences = context.applicationContext.getSharedPreferences(preferencesName, Context.MODE_PRIVATE),
        ioDispatcher = ioDispatcher,
        codec = codec,
        cipher = cipher,
    )

    override suspend fun read(provider: ProviderKey): ProviderCredentials? =
        runOnIo {
            val encoded = preferences.getString(codec.storageKey(provider), null) ?: return@runOnIo null
            val envelope = Base64.decode(encoded, Base64.NO_WRAP)
            val plaintext = cipher.decrypt(codec.storageKey(provider), envelope)
            codec.decode(codec.storageKey(provider), plaintext)
        }

    override suspend fun write(credentials: ProviderCredentials) {
        runOnIo {
            val storageKey = codec.storageKey(credentials.provider)
            val plaintext = codec.encode(credentials)
            val envelope = cipher.encrypt(storageKey, plaintext)
            val encoded = Base64.encodeToString(envelope, Base64.NO_WRAP)
            check(preferences.edit().putString(storageKey, encoded).commit())
        }
    }

    override suspend fun delete(provider: ProviderKey) {
        runOnIo {
            check(preferences.edit().remove(codec.storageKey(provider)).commit())
        }
    }

    override suspend fun deleteAll() {
        val result = withContext(ioDispatcher) { deleteAllOnIo() }
        when (result) {
            is StorageOperationResult.Cancelled -> throw result.exception
            StorageOperationResult.Failed -> throw ProviderCredentialStorageException()
            is StorageOperationResult.Succeeded -> Unit
        }
    }

    private fun deleteAllOnIo(): StorageOperationResult<Unit> {
        var failed = false
        var cancellation: CancellationException? = null
        try {
            if (!preferences.edit().clear().commit()) failed = true
        } catch (exception: CancellationException) {
            cancellation = exception
        } catch (_: Exception) {
            failed = true
        }

        try {
            cipher.deleteKey()
        } catch (exception: CancellationException) {
            if (cancellation == null) cancellation = exception
        } catch (_: Exception) {
            failed = true
        }

        return when {
            cancellation != null -> StorageOperationResult.Cancelled(cancellation)
            failed -> StorageOperationResult.Failed
            else -> StorageOperationResult.Succeeded(Unit)
        }
    }

    private suspend fun <T> runOnIo(block: () -> T): T {
        val result =
            withContext(ioDispatcher) {
                try {
                    StorageOperationResult.Succeeded(block())
                } catch (cancellation: CancellationException) {
                    StorageOperationResult.Cancelled(cancellation)
                } catch (_: Exception) {
                    StorageOperationResult.Failed
                }
            }
        return when (result) {
            is StorageOperationResult.Cancelled -> throw result.exception
            StorageOperationResult.Failed -> throw ProviderCredentialStorageException()
            is StorageOperationResult.Succeeded -> result.value
        }
    }

    private sealed interface StorageOperationResult<out T> {
        data class Succeeded<T>(
            val value: T,
        ) : StorageOperationResult<T>

        data class Cancelled(
            val exception: CancellationException,
        ) : StorageOperationResult<Nothing>

        data object Failed : StorageOperationResult<Nothing>
    }

    private companion object {
        const val PREFERENCES_NAME: String = "mio.secure_credentials"
    }
}

@BindingContainer
@ContributesTo(AppScope::class)
public object CredentialBindings {
    @Provides
    @SingleIn(AppScope::class)
    public fun provideProviderCredentialStore(
        @ApplicationContext context: Context,
        @IoDispatcher ioDispatcher: CoroutineDispatcher,
    ): ProviderCredentialStore =
        SecureProviderCredentialStore(
            context = context,
            ioDispatcher = ioDispatcher,
            codec = CredentialRecordCodec(),
            cipher = AndroidKeystoreCredentialCipher(),
        )
}
