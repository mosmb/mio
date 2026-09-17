package io.morgan.mio.di

import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.morgan.mio.core.common.di.AppScope
import io.morgan.mio.data.account.AuthenticatedAccountSessionActivator
import io.morgan.mio.data.provider.api.ProviderBindingResolver
import io.morgan.mio.data.provider.api.ProviderKey
import io.morgan.mio.domain.account.ConnectedProvider
import io.morgan.mio.domain.model.AccountId

@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
@Inject
internal class MetroAuthenticatedAccountSessionActivator(
    private val accountGraphLifecycle: AccountGraphLifecycle,
    private val bindingResolver: ProviderBindingResolver,
) : AuthenticatedAccountSessionActivator {
    override fun activate(
        accountId: AccountId,
        provider: ConnectedProvider,
    ) {
        val providerBinding =
            checkNotNull(bindingResolver.bindingFor(provider.toProviderKey())) {
                "The selected provider is not configured"
            }
        val current = accountGraphLifecycle.activeHandle()
        if (
            current?.withGraph { graph ->
                graph.accountId == accountId && graph.providerBinding.provider == providerBinding.provider
            } == true
        ) {
            return
        }

        val request =
            AccountGraphRequest(
                accountId = accountId,
                providerBinding = providerBinding,
            )
        if (current == null) {
            accountGraphLifecycle.create(request)
        } else {
            accountGraphLifecycle.replace(request)
        }
    }

    override fun destroy() {
        accountGraphLifecycle.destroy()
    }
}

private fun ConnectedProvider.toProviderKey(): ProviderKey =
    when (this) {
        ConnectedProvider.ANILIST -> ProviderKey.ANILIST
        ConnectedProvider.MY_ANIME_LIST -> ProviderKey.MY_ANIME_LIST
    }
