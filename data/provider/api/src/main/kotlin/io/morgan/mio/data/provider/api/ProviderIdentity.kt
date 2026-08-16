package io.morgan.mio.data.provider.api

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
