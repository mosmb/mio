package io.morgan.mio.di

import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.Provides
import io.morgan.mio.core.network.NetworkClientFactory
import io.morgan.mio.data.provider.anilist.AniListClientConfiguration
import io.morgan.mio.data.provider.anilist.AniListProviderFactory
import io.morgan.mio.data.provider.api.ProviderBindingResolver
import io.morgan.mio.data.provider.api.ProviderKey
import io.morgan.mio.data.provider.myanimelist.MyAnimeListClientConfiguration
import io.morgan.mio.data.provider.myanimelist.MyAnimeListProviderFactory
import kotlin.time.Clock

@BindingContainer(includes = [ProcessBindings::class, SyncProcessBindings::class])
internal class SyncEmulatorBindings {
    @Provides
    fun resolver(
        network: NetworkClientFactory,
        clock: Clock,
    ): ProviderBindingResolver =
        object : ProviderBindingResolver {
            private val anilist by lazy {
                AniListProviderFactory(
                    network,
                    AniListClientConfiguration(
                        "fixture",
                        "mio://oauth/anilist",
                        apiBaseUrl = "http://127.0.0.1:18820/",
                    ),
                    clock,
                ).create()
            }
            private val mal by lazy {
                MyAnimeListProviderFactory(
                    network,
                    MyAnimeListClientConfiguration(
                        "fixture",
                        "mio://oauth/myanimelist",
                        apiBaseUrl = "http://127.0.0.1:18820/",
                        tokenBaseUrl = "http://127.0.0.1:18820/",
                    ),
                    clock,
                ).create()
            }

            override fun bindingFor(provider: ProviderKey) =
                when (provider) {
                    ProviderKey.ANILIST -> anilist
                    ProviderKey.MY_ANIME_LIST -> mal
                }
        }
}
