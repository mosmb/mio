package io.morgan.mio.data.provider.api

public enum class ProviderKey {
    ANILIST,
    MY_ANIME_LIST,
}

@JvmInline
public value class ProviderAccountId(
    public val value: String,
) {
    init {
        require(value.isNotBlank())
    }
}

@JvmInline
public value class ProviderMediaId(
    public val value: String,
) {
    init {
        require(value.isNotBlank())
    }
}

@JvmInline
public value class ProviderCollectionEntryId(
    public val value: String,
) {
    init {
        require(value.isNotBlank())
    }
}

@JvmInline
public value class ProviderImageReference(
    public val value: String,
) {
    init {
        require(value.isNotBlank())
    }
}

public data class ProviderIdentity(
    public val provider: ProviderKey,
    public val accountId: ProviderAccountId,
    public val displayName: String,
    public val avatar: ProviderImageReference?,
) {
    init {
        require(displayName.isNotBlank())
    }
}
