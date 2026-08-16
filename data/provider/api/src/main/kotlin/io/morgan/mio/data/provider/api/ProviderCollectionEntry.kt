package io.morgan.mio.data.provider.api

import kotlin.time.Instant

public data class ProviderCollectionEntry(
    public val id: ProviderCollectionEntryId,
    public val accountId: ProviderAccountId,
    public val mediaId: ProviderMediaId,
    public val status: ProviderCollectionStatus,
    public val progress: Int,
    public val progressUnit: ProviderProgressUnit,
    public val total: Int?,
    public val updatedAt: Instant,
) {
    init {
        require(progress >= 0)
        require(total == null || total > 0)
        require(total == null || progress <= total)
    }
}
