package io.morgan.mio.data.provider.api

/**
 * Provider boundary for reading and delivering one collection entry.
 *
 * The progress unit is supplied by the persisted collection entry and is therefore never inferred
 * from the current remote counter. Implementations must validate the complete returned entry before
 * reporting success.
 */
public interface ProviderSyncDataSource {
    public val provider: ProviderKey

    /** Authenticated lookup; null means the title is not in this account's collection. */
    public suspend fun findEntry(
        credentials: ProviderCredentials,
        accountId: ProviderAccountId,
        mediaId: ProviderMediaId,
        unit: ProviderProgressUnit,
    ): ProviderResult<ProviderCollectionEntry?>

    public suspend fun readEntry(
        credentials: ProviderCredentials,
        accountId: ProviderAccountId,
        entryId: ProviderCollectionEntryId,
        mediaId: ProviderMediaId,
        unit: ProviderProgressUnit,
    ): ProviderResult<ProviderCollectionEntry>

    public suspend fun setProgress(
        credentials: ProviderCredentials,
        accountId: ProviderAccountId,
        entryId: ProviderCollectionEntryId,
        mediaId: ProviderMediaId,
        unit: ProviderProgressUnit,
        target: Int,
    ): ProviderResult<ProviderCollectionEntry>

    public suspend fun setStatus(
        credentials: ProviderCredentials,
        accountId: ProviderAccountId,
        entryId: ProviderCollectionEntryId,
        mediaId: ProviderMediaId,
        unit: ProviderProgressUnit,
        target: ProviderCollectionStatus,
    ): ProviderResult<ProviderCollectionEntry>

    public suspend fun createEntry(
        credentials: ProviderCredentials,
        accountId: ProviderAccountId,
        mediaId: ProviderMediaId,
        unit: ProviderProgressUnit,
        status: ProviderCollectionStatus,
        progress: Int,
    ): ProviderResult<ProviderCollectionEntry>

    public suspend fun removeEntry(
        credentials: ProviderCredentials,
        accountId: ProviderAccountId,
        entryId: ProviderCollectionEntryId,
        mediaId: ProviderMediaId,
        unit: ProviderProgressUnit,
    ): ProviderResult<Unit>
}
