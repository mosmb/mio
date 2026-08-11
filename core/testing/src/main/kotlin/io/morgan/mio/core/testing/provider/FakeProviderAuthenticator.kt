package io.morgan.mio.core.testing.provider

import io.morgan.mio.data.provider.api.ProviderAuthenticator
import io.morgan.mio.data.provider.api.ProviderAuthorizationCallback
import io.morgan.mio.data.provider.api.ProviderAuthorizationLaunch
import io.morgan.mio.data.provider.api.ProviderAuthorizationRequest
import io.morgan.mio.data.provider.api.ProviderAuthorizationTransaction
import io.morgan.mio.data.provider.api.ProviderCredentials
import io.morgan.mio.data.provider.api.ProviderKey
import io.morgan.mio.data.provider.api.ProviderResult

public class FakeProviderAuthenticator(
    override val provider: ProviderKey,
) : ProviderAuthenticator {
    private val beginHandlers =
        ArrayDeque<suspend (ProviderAuthorizationRequest) -> ProviderResult<ProviderAuthorizationLaunch>>()
    private val completeHandlers =
        ArrayDeque<
            suspend (ProviderAuthorizationTransaction, ProviderAuthorizationCallback) ->
            ProviderResult<ProviderCredentials>,
        >()
    private val refreshHandlers =
        ArrayDeque<suspend (ProviderCredentials) -> ProviderResult<ProviderCredentials>>()

    private val beginRequestsValue = mutableListOf<ProviderAuthorizationRequest>()
    private val completeCallsValue = mutableListOf<AuthorizationCompletionCall>()
    private val refreshCredentialsValue = mutableListOf<ProviderCredentials>()

    public val beginRequests: List<ProviderAuthorizationRequest>
        get() = beginRequestsValue.toList()

    public val completeCalls: List<AuthorizationCompletionCall>
        get() = completeCallsValue.toList()

    public val refreshCredentials: List<ProviderCredentials>
        get() = refreshCredentialsValue.toList()

    public fun enqueueBeginAuthorization(
        handler: suspend (ProviderAuthorizationRequest) -> ProviderResult<ProviderAuthorizationLaunch>,
    ) {
        beginHandlers.addLast(handler)
    }

    public fun enqueueCompleteAuthorization(
        handler: suspend (ProviderAuthorizationTransaction, ProviderAuthorizationCallback) ->
        ProviderResult<ProviderCredentials>,
    ) {
        completeHandlers.addLast(handler)
    }

    public fun enqueueRefresh(handler: suspend (ProviderCredentials) -> ProviderResult<ProviderCredentials>) {
        refreshHandlers.addLast(handler)
    }

    override suspend fun beginAuthorization(
        request: ProviderAuthorizationRequest,
    ): ProviderResult<ProviderAuthorizationLaunch> {
        beginRequestsValue += request
        return beginHandlers.removeFirstOrNull()?.invoke(request)
            ?: error("No queued beginAuthorization handler for provider $provider")
    }

    override suspend fun completeAuthorization(
        transaction: ProviderAuthorizationTransaction,
        callback: ProviderAuthorizationCallback,
    ): ProviderResult<ProviderCredentials> {
        completeCallsValue += AuthorizationCompletionCall(transaction, callback)
        val result =
            completeHandlers.removeFirstOrNull()?.invoke(transaction, callback)
                ?: error("No queued completeAuthorization handler for provider $provider")
        result.requireProvider(provider)
        return result
    }

    override suspend fun refresh(credentials: ProviderCredentials): ProviderResult<ProviderCredentials> {
        require(credentials.provider == provider)
        refreshCredentialsValue += credentials
        val result =
            refreshHandlers.removeFirstOrNull()?.invoke(credentials)
                ?: error("No queued refresh handler for provider $provider")
        result.requireProvider(provider)
        return result
    }

    private fun ProviderResult<ProviderCredentials>.requireProvider(expected: ProviderKey) {
        if (this is ProviderResult.Success) {
            check(value.provider == expected) {
                "Scripted credentials for ${value.provider} do not match fake provider $expected"
            }
        }
    }
}

public data class AuthorizationCompletionCall(
    public val transaction: ProviderAuthorizationTransaction,
    public val callback: ProviderAuthorizationCallback,
)
