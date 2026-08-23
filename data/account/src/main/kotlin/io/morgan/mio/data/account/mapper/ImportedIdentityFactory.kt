package io.morgan.mio.data.account.mapper

import io.morgan.mio.core.common.identity.StableOpaqueIdFactory
import io.morgan.mio.core.database.model.StoredConnectedProvider
import io.morgan.mio.domain.model.AccountId
import io.morgan.mio.domain.model.CollectionEntryId
import io.morgan.mio.domain.model.MediaId

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
        return StableOpaqueIdFactory.create(provider.serializedValue, kind, remoteId)
    }
}
