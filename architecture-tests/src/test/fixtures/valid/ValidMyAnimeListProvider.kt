package io.morgan.mio.data.provider.myanimelist

import io.morgan.mio.core.network.NetworkClient
import io.morgan.mio.data.provider.api.ProviderItem

internal class ValidMyAnimeListProvider(
    val networkClient: NetworkClient,
    val item: ProviderItem,
)
