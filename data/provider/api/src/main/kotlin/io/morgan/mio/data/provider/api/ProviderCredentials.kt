package io.morgan.mio.data.provider.api

import kotlin.time.Instant

public class ProviderCredentials(
    public val provider: ProviderKey,
    public val accessToken: String,
    public val refreshToken: String?,
    public val expiresAt: Instant?,
    public val tokenType: String,
) {
    init {
        require(accessToken.isNotBlank())
        require(refreshToken == null || refreshToken.isNotBlank())
        require(tokenType.isNotBlank())
    }

    override fun toString(): String = "ProviderCredentials([REDACTED])"
}
