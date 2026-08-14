package io.morgan.mio.data.provider.api

import kotlin.time.Instant

public data class ProviderAuthorizationRequest(
    public val redirectUri: String,
) {
    init {
        require(redirectUri.isNotBlank())
    }
}

public class ProviderAuthorizationTransaction(
    public val state: String,
    public val codeVerifier: String?,
) {
    init {
        require(state.isNotBlank())
        require(codeVerifier == null || codeVerifier.isNotBlank())
    }

    override fun toString(): String = "ProviderAuthorizationTransaction([REDACTED])"
}

public class ProviderAuthorizationLaunch(
    public val authorizationUrl: String,
    public val transaction: ProviderAuthorizationTransaction,
) {
    init {
        require(authorizationUrl.isNotBlank())
    }

    override fun toString(): String = "ProviderAuthorizationLaunch([REDACTED])"
}

public class ProviderAuthorizationRedirect(
    public val redirectUri: String,
) {
    init {
        require(redirectUri.isNotBlank())
    }

    override fun toString(): String = "ProviderAuthorizationRedirect([REDACTED])"
}

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

public interface ProviderAuthenticator {
    public val provider: ProviderKey

    public suspend fun beginAuthorization(
        request: ProviderAuthorizationRequest,
    ): ProviderResult<ProviderAuthorizationLaunch>

    public suspend fun completeAuthorization(
        transaction: ProviderAuthorizationTransaction,
        redirect: ProviderAuthorizationRedirect,
    ): ProviderResult<ProviderCredentials>

    public suspend fun refresh(credentials: ProviderCredentials): ProviderResult<ProviderCredentials>
}
