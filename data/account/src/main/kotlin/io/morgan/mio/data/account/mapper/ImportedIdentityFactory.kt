package io.morgan.mio.data.account.mapper

import io.morgan.mio.core.database.model.StoredConnectedProvider
import io.morgan.mio.domain.model.AccountId
import io.morgan.mio.domain.model.CollectionEntryId
import io.morgan.mio.domain.model.MediaId
import java.security.MessageDigest

internal object ImportedIdentityFactory {
    fun accountId(
        provider: StoredConnectedProvider,
        remoteAccountId: String,
    ): AccountId = AccountId(localId(provider, "account", remoteAccountId))

    fun mediaId(
        provider: StoredConnectedProvider,
        remoteMediaId: String,
    ): MediaId = MediaId(localId(provider, "media", remoteMediaId))

    fun collectionEntryId(
        provider: StoredConnectedProvider,
        remoteEntryId: String,
    ): CollectionEntryId = CollectionEntryId(localId(provider, "collection-entry", remoteEntryId))

    private fun localId(
        provider: StoredConnectedProvider,
        kind: String,
        remoteId: String,
    ): String {
        require(remoteId.isNotBlank())
        val digest =
            MessageDigest
                .getInstance("SHA-256")
                .digest("${provider.serializedValue}|$kind|$remoteId".toByteArray(Charsets.UTF_8))
        return digest.take(16).joinToString(separator = "") { byte ->
            ((byte.toInt() and 0xFF) + 0x100).toString(16).substring(1)
        }
    }
}
