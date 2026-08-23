package io.morgan.mio.data.provider.api

public fun interface ProviderBindingResolver {
    public fun bindingFor(provider: ProviderKey): ProviderBinding?
}
