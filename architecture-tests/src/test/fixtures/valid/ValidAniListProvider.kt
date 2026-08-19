package io.morgan.mio.data.provider.anilist

import io.morgan.mio.core.network.NetworkClient
import io.morgan.mio.data.provider.api.ProviderItem

class ValidAniListProvider(
    private val client: NetworkClient,
    private val item: ProviderItem,
)
