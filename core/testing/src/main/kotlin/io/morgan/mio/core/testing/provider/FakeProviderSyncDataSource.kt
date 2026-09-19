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
    public var lookup: suspend (ProviderMediaId) -> ProviderResult<ProviderCollectionEntry?> = {
        ProviderResult.Success(null)
    }

    override suspend fun findEntry(
        credentials: ProviderCredentials,
        accountId: ProviderAccountId,
        mediaId: ProviderMediaId,
        unit: ProviderProgressUnit,
    ): ProviderResult<ProviderCollectionEntry?> = lookup(mediaId)

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
    private val creates =
        ArrayDeque<
            suspend (
                ProviderCredentials,
                ProviderAccountId,
                ProviderMediaId,
                ProviderProgressUnit,
                ProviderCollectionStatus,
                Int,
            ) -> ProviderResult<ProviderCollectionEntry>,
        >()
    private val removes =
        ArrayDeque<
            suspend (
                ProviderCredentials,
                ProviderAccountId,
                ProviderCollectionEntryId,
                ProviderMediaId,
                ProviderProgressUnit,
            ) -> ProviderResult<Unit>,
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

    public fun enqueueCreate(
        handler: suspend (
            ProviderCredentials,
            ProviderAccountId,
            ProviderMediaId,
            ProviderProgressUnit,
            ProviderCollectionStatus,
            Int,
        ) -> ProviderResult<ProviderCollectionEntry>,
    ) {
        creates.addLast(handler)
    }

    public fun enqueueRemove(
        handler: suspend (
            ProviderCredentials,
            ProviderAccountId,
            ProviderCollectionEntryId,
            ProviderMediaId,
            ProviderProgressUnit,
        ) -> ProviderResult<Unit>,
    ) {
        removes.addLast(handler)
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

    override suspend fun createEntry(
        credentials: ProviderCredentials,
        accountId: ProviderAccountId,
        mediaId: ProviderMediaId,
        unit: ProviderProgressUnit,
        status: ProviderCollectionStatus,
        progress: Int,
    ): ProviderResult<ProviderCollectionEntry> {
        require(credentials.provider == provider)
        callsValue +=
            SyncCall.Create(credentials, accountId, ProviderCollectionEntryId("new"), mediaId, unit, status, progress)
        return creates.removeFirstOrNull()?.invoke(credentials, accountId, mediaId, unit, status, progress)
            ?: error("No queued createEntry handler for provider $provider")
    }

    override suspend fun removeEntry(
        credentials: ProviderCredentials,
        accountId: ProviderAccountId,
        entryId: ProviderCollectionEntryId,
        mediaId: ProviderMediaId,
        unit: ProviderProgressUnit,
    ): ProviderResult<Unit> {
        require(credentials.provider == provider)
        callsValue += SyncCall.Remove(credentials, accountId, entryId, mediaId, unit)
        return removes.removeFirstOrNull()?.invoke(credentials, accountId, entryId, mediaId, unit)
            ?: error("No queued removeEntry handler for provider $provider")
    }
}
