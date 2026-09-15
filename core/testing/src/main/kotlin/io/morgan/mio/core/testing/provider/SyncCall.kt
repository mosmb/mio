package io.morgan.mio.core.testing.provider

import io.morgan.mio.data.provider.api.ProviderAccountId
import io.morgan.mio.data.provider.api.ProviderCollectionEntryId
import io.morgan.mio.data.provider.api.ProviderCollectionStatus
import io.morgan.mio.data.provider.api.ProviderCredentials
import io.morgan.mio.data.provider.api.ProviderMediaId
import io.morgan.mio.data.provider.api.ProviderProgressUnit

public sealed interface SyncCall {
    public val credentials: ProviderCredentials
    public val accountId: ProviderAccountId
    public val entryId: ProviderCollectionEntryId
    public val mediaId: ProviderMediaId
    public val unit: ProviderProgressUnit

    public data class Read(
        override val credentials: ProviderCredentials,
        override val accountId: ProviderAccountId,
        override val entryId: ProviderCollectionEntryId,
        override val mediaId: ProviderMediaId,
        override val unit: ProviderProgressUnit,
    ) : SyncCall

    public data class Progress(
        override val credentials: ProviderCredentials,
        override val accountId: ProviderAccountId,
        override val entryId: ProviderCollectionEntryId,
        override val mediaId: ProviderMediaId,
        override val unit: ProviderProgressUnit,
        public val target: Int,
    ) : SyncCall

    public data class Status(
        override val credentials: ProviderCredentials,
        override val accountId: ProviderAccountId,
        override val entryId: ProviderCollectionEntryId,
        override val mediaId: ProviderMediaId,
        override val unit: ProviderProgressUnit,
        public val target: ProviderCollectionStatus,
    ) : SyncCall
}
