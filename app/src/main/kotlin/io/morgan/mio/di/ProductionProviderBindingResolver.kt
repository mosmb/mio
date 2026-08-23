package io.morgan.mio.di

import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.morgan.mio.BuildConfig
import io.morgan.mio.core.common.di.AppScope
import io.morgan.mio.core.network.NetworkClientFactory
import io.morgan.mio.data.provider.anilist.AniListClientConfiguration
import io.morgan.mio.data.provider.anilist.AniListProviderFactory
import io.morgan.mio.data.provider.api.ProviderBinding
import io.morgan.mio.data.provider.api.ProviderBindingResolver
import io.morgan.mio.data.provider.api.ProviderKey
import io.morgan.mio.data.provider.myanimelist.MyAnimeListClientConfiguration
import io.morgan.mio.data.provider.myanimelist.MyAnimeListProviderFactory
import kotlin.time.Clock

private const val ANILIST_REDIRECT_URI = "mio://oauth/anilist"
private const val MY_ANIME_LIST_REDIRECT_URI = "mio://oauth/myanimelist"

@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
internal class ProductionProviderBindingResolver private constructor(
    private val networkClientFactory: NetworkClientFactory,
    private val clock: Clock,
    private val aniListClientId: String?,
    private val myAnimeListClientId: String?,
) : ProviderBindingResolver {
    @Inject
    internal constructor(
        networkClientFactory: NetworkClientFactory,
        clock: Clock,
    ) : this(
        networkClientFactory = networkClientFactory,
        clock = clock,
        aniListClientId = BuildConfig.MIO_ANILIST_CLIENT_ID.configuredValue(),
        myAnimeListClientId = BuildConfig.MIO_MYANIMELIST_CLIENT_ID.configuredValue(),
    )

    internal constructor(
        networkClientFactory: NetworkClientFactory,
        clock: Clock,
        aniListClientId: String?,
        myAnimeListClientId: String?,
        @Suppress("UNUSED_PARAMETER") testing: Unit = Unit,
    ) : this(
        networkClientFactory = networkClientFactory,
        clock = clock,
        aniListClientId = aniListClientId.configuredValue(),
        myAnimeListClientId = myAnimeListClientId.configuredValue(),
    )

    private val bindings = mutableMapOf<ProviderKey, ProviderBinding>()

    override fun bindingFor(provider: ProviderKey): ProviderBinding? =
        synchronized(bindings) {
            bindings[provider] ?: createBinding(provider)?.also { binding -> bindings[provider] = binding }
        }

    private fun createBinding(provider: ProviderKey): ProviderBinding? =
        when (provider) {
            ProviderKey.ANILIST ->
                aniListClientId?.let { clientId ->
                    AniListProviderFactory(
                        networkClientFactory = networkClientFactory,
                        configuration =
                            AniListClientConfiguration(
                                clientId = clientId,
                                redirectUri = ANILIST_REDIRECT_URI,
                            ),
                        clock = clock,
                    ).create()
                }
            ProviderKey.MY_ANIME_LIST ->
                myAnimeListClientId?.let { clientId ->
                    MyAnimeListProviderFactory(
                        networkClientFactory = networkClientFactory,
                        configuration =
                            MyAnimeListClientConfiguration(
                                clientId = clientId,
                                redirectUri = MY_ANIME_LIST_REDIRECT_URI,
                            ),
                        clock = clock,
                    ).create()
                }
        }
}

private fun String?.configuredValue(): String? = this?.trim()?.takeIf(String::isNotEmpty)
