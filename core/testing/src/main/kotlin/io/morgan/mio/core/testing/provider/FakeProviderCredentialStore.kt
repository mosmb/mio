package io.morgan.mio.core.testing.provider

import io.morgan.mio.data.provider.api.ProviderCredentialStorageException
import io.morgan.mio.data.provider.api.ProviderCredentialStore
import io.morgan.mio.data.provider.api.ProviderCredentials
import io.morgan.mio.data.provider.api.ProviderKey
import java.util.EnumMap

public enum class FakeProviderCredentialOperation {
    READ,
    WRITE,
    DELETE,
    DELETE_ALL,
}

public class FakeProviderCredentialStore : ProviderCredentialStore {
    private val lock = Any()
    private val values = EnumMap<ProviderKey, ProviderCredentials>(ProviderKey::class.java)
    private val readProviderHistory = mutableListOf<ProviderKey>()
    private val writeProviderHistory = mutableListOf<ProviderKey>()
    private val deleteProviderHistory = mutableListOf<ProviderKey>()
    private var deleteAllCalls = 0
    private var nextFailure: FakeProviderCredentialOperation? = null

    public val readCount: Int
        get() = synchronized(lock) { readProviderHistory.size }

    public val writeCount: Int
        get() = synchronized(lock) { writeProviderHistory.size }

    public val deleteCount: Int
        get() = synchronized(lock) { deleteProviderHistory.size }

    public val deleteAllCount: Int
        get() = synchronized(lock) { deleteAllCalls }

    public val readProviders: List<ProviderKey>
        get() = synchronized(lock) { readProviderHistory.toList() }

    public val writeProviders: List<ProviderKey>
        get() = synchronized(lock) { writeProviderHistory.toList() }

    public val deleteProviders: List<ProviderKey>
        get() = synchronized(lock) { deleteProviderHistory.toList() }

    public fun failNext(operation: FakeProviderCredentialOperation) {
        synchronized(lock) {
            nextFailure = operation
        }
    }

    override suspend fun read(provider: ProviderKey): ProviderCredentials? =
        synchronized(lock) {
            readProviderHistory += provider
            failIfScripted(FakeProviderCredentialOperation.READ)
            values[provider]
        }

    override suspend fun write(credentials: ProviderCredentials) {
        synchronized(lock) {
            writeProviderHistory += credentials.provider
            failIfScripted(FakeProviderCredentialOperation.WRITE)
            values[credentials.provider] = credentials
        }
    }

    override suspend fun delete(provider: ProviderKey) {
        synchronized(lock) {
            deleteProviderHistory += provider
            failIfScripted(FakeProviderCredentialOperation.DELETE)
            values.remove(provider)
        }
    }

    override suspend fun deleteAll() {
        synchronized(lock) {
            deleteAllCalls += 1
            failIfScripted(FakeProviderCredentialOperation.DELETE_ALL)
            values.clear()
        }
    }

    override fun toString(): String = "FakeProviderCredentialStore([REDACTED])"

    private fun failIfScripted(operation: FakeProviderCredentialOperation) {
        if (nextFailure == operation) {
            nextFailure = null
            throw ProviderCredentialStorageException()
        }
    }
}
