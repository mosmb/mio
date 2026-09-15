package io.morgan.mio.core.testing.provider

import io.morgan.mio.data.provider.api.ProviderAccountId
import io.morgan.mio.data.provider.api.ProviderCollectionEntry
import io.morgan.mio.data.provider.api.ProviderCollectionEntryId
import io.morgan.mio.data.provider.api.ProviderCollectionStatus
import io.morgan.mio.data.provider.api.ProviderCredentials
import io.morgan.mio.data.provider.api.ProviderKey
import io.morgan.mio.data.provider.api.ProviderMediaId
import io.morgan.mio.data.provider.api.ProviderProgressUnit
import io.morgan.mio.data.provider.api.ProviderResult
import io.morgan.mio.data.provider.api.ProviderSyncDataSource

/** A deterministic provider fixture; production bindings never use this implementation. */
public class FakeProviderSyncDataSource(
    override val provider: ProviderKey,
) : ProviderSyncDataSource {
    private val reads =
        ArrayDeque<
            suspend (
                ProviderCredentials,
                ProviderAccountId,
                ProviderCollectionEntryId,
                ProviderMediaId,
                ProviderProgressUnit,
            ) -> ProviderResult<ProviderCollectionEntry>,
        >()
    private val progressWrites =
        ArrayDeque<
            suspend (
                ProviderCredentials,
                ProviderAccountId,
                ProviderCollectionEntryId,
                ProviderMediaId,
                ProviderProgressUnit,
                Int,
            ) -> ProviderResult<ProviderCollectionEntry>,
        >()
    private val statusWrites =
        ArrayDeque<
            suspend (
                ProviderCredentials,
                ProviderAccountId,
                ProviderCollectionEntryId,
                ProviderMediaId,
                ProviderProgressUnit,
                ProviderCollectionStatus,
            ) -> ProviderResult<ProviderCollectionEntry>,
        >()
    private val callsValue = mutableListOf<SyncCall>()

    public val calls: List<SyncCall>
        get() = callsValue.toList()

    public fun enqueueRead(
        handler: suspend (
            ProviderCredentials,
            ProviderAccountId,
            ProviderCollectionEntryId,
            ProviderMediaId,
            ProviderProgressUnit,
        ) -> ProviderResult<ProviderCollectionEntry>,
    ) {
        reads.addLast(handler)
    }

    public fun enqueueProgress(
        handler: suspend (
            ProviderCredentials,
            ProviderAccountId,
            ProviderCollectionEntryId,
            ProviderMediaId,
            ProviderProgressUnit,
            Int,
        ) -> ProviderResult<ProviderCollectionEntry>,
    ) {
        progressWrites.addLast(handler)
    }

    public fun enqueueStatus(
        handler: suspend (
            ProviderCredentials,
            ProviderAccountId,
            ProviderCollectionEntryId,
            ProviderMediaId,
            ProviderProgressUnit,
            ProviderCollectionStatus,
        ) -> ProviderResult<ProviderCollectionEntry>,
    ) {
        statusWrites.addLast(handler)
    }

    override suspend fun readEntry(
        credentials: ProviderCredentials,
        accountId: ProviderAccountId,
        entryId: ProviderCollectionEntryId,
        mediaId: ProviderMediaId,
        unit: ProviderProgressUnit,
    ): ProviderResult<ProviderCollectionEntry> {
        require(credentials.provider == provider)
        callsValue += SyncCall.Read(credentials, accountId, entryId, mediaId, unit)
        return reads.removeFirstOrNull()?.invoke(credentials, accountId, entryId, mediaId, unit)
            ?: error("No queued readEntry handler for provider $provider")
    }

    override suspend fun setProgress(
        credentials: ProviderCredentials,
        accountId: ProviderAccountId,
        entryId: ProviderCollectionEntryId,
        mediaId: ProviderMediaId,
        unit: ProviderProgressUnit,
        target: Int,
    ): ProviderResult<ProviderCollectionEntry> {
        require(credentials.provider == provider)
        require(target >= 0)
        callsValue += SyncCall.Progress(credentials, accountId, entryId, mediaId, unit, target)
        return progressWrites.removeFirstOrNull()?.invoke(credentials, accountId, entryId, mediaId, unit, target)
            ?: error("No queued setProgress handler for provider $provider")
    }

    override suspend fun setStatus(
        credentials: ProviderCredentials,
        accountId: ProviderAccountId,
        entryId: ProviderCollectionEntryId,
        mediaId: ProviderMediaId,
        unit: ProviderProgressUnit,
        target: ProviderCollectionStatus,
    ): ProviderResult<ProviderCollectionEntry> {
        require(credentials.provider == provider)
        callsValue += SyncCall.Status(credentials, accountId, entryId, mediaId, unit, target)
        return statusWrites.removeFirstOrNull()?.invoke(credentials, accountId, entryId, mediaId, unit, target)
            ?: error("No queued setStatus handler for provider $provider")
    }
}
