package io.morgan.mio.di

import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.morgan.mio.core.common.di.AppScope
import io.morgan.mio.data.account.AccountRefreshRequester
import io.morgan.mio.data.collection.CollectionRefreshRequester
import io.morgan.mio.data.provider.api.ProviderBinding
import io.morgan.mio.domain.model.AccountId

internal data class AccountGraphRequest(
    val accountId: AccountId,
    val providerBinding: ProviderBinding,
    val accountRefreshRequester: AccountRefreshRequester,
    val collectionRefreshRequester: CollectionRefreshRequester,
)

internal class AccountGraphHandle internal constructor(
    private val graph: AccountGraph,
) {
    private var active = true

    internal fun <T> withGraph(block: (AccountGraph) -> T): T =
        synchronized(this) {
            check(active) { "Account graph is no longer active" }
            block(graph)
        }

    internal fun invalidate() {
        synchronized(this) {
            active = false
        }
    }
}

@SingleIn(AppScope::class)
@Inject
internal class AccountGraphLifecycle(
    private val factory: AccountGraph.Factory,
) {
    private var currentHandle: AccountGraphHandle? = null

    internal fun activeHandle(): AccountGraphHandle? =
        synchronized(this) {
            currentHandle
        }

    internal fun create(request: AccountGraphRequest): AccountGraphHandle =
        synchronized(this) {
            check(currentHandle == null) { "An account graph is already active" }
            AccountGraphHandle(createGraph(request)).also { handle -> currentHandle = handle }
        }

    internal fun replace(request: AccountGraphRequest): AccountGraphHandle =
        synchronized(this) {
            val replacement = AccountGraphHandle(createGraph(request))
            currentHandle?.invalidate()
            currentHandle = replacement
            replacement
        }

    internal fun destroy() {
        synchronized(this) {
            currentHandle?.invalidate()
            currentHandle = null
        }
    }

    private fun createGraph(request: AccountGraphRequest): AccountGraph =
        factory.createAccountGraph(
            accountId = request.accountId,
            providerBinding = request.providerBinding,
            accountRefreshRequester = request.accountRefreshRequester,
            collectionRefreshRequester = request.collectionRefreshRequester,
        )
}
