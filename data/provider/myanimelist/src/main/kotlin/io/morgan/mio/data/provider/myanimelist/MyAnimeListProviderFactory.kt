package io.morgan.mio.data.provider.myanimelist

import io.morgan.mio.core.network.NetworkClientFactory
import io.morgan.mio.data.provider.api.ProviderBinding
import io.morgan.mio.data.provider.api.ProviderKey
import okhttp3.HttpUrl.Companion.toHttpUrl
import kotlin.time.Clock

public class MyAnimeListProviderFactory(
    private val networkClientFactory: NetworkClientFactory,
    private val configuration: MyAnimeListClientConfiguration,
    private val clock: Clock,
) {
    public fun create(): ProviderBinding {
        val apiService =
            networkClientFactory
                .createRetrofit(configuration.apiBaseUrl.toHttpUrl())
                .create(MyAnimeListApiService::class.java)

        val oAuthService =
            networkClientFactory
                .createRetrofit(configuration.tokenBaseUrl.toHttpUrl())
                .create(MyAnimeListOAuthService::class.java)

        val tokenExchange = MyAnimeListTokenExchange(oAuthService, configuration, clock)
        val serializedTokenRefresh = SerializedTokenRefresh { credentials -> tokenExchange.refresh(credentials) }

        val authenticator =
            MyAnimeListAuthenticator(
                configuration,
                SecureMyAnimeListAuthorizationSecretGenerator(),
                tokenExchange,
                serializedTokenRefresh,
            )

        val identityDataSource = MyAnimeListIdentityDataSource(apiService)
        val initialImportDataSource = MyAnimeListInitialImportDataSource(apiService)
        val mediaDetailDataSource = MyAnimeListMediaDetailDataSource(apiService)

        return ProviderBinding(
            provider = ProviderKey.MY_ANIME_LIST,
            authenticator = authenticator,
            identityDataSource = identityDataSource,
            initialImportDataSource = initialImportDataSource,
            mediaDetailDataSource = mediaDetailDataSource,
        )
    }
}
