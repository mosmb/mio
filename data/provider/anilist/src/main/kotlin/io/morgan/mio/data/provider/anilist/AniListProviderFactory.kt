package io.morgan.mio.data.provider.anilist

import io.morgan.mio.core.network.NetworkClientFactory
import io.morgan.mio.data.provider.api.ProviderBinding
import io.morgan.mio.data.provider.api.ProviderFailure
import io.morgan.mio.data.provider.api.ProviderKey
import io.morgan.mio.data.provider.api.ProviderResult
import okhttp3.HttpUrl.Companion.toHttpUrl
import kotlin.time.Clock

public class AniListProviderFactory(
    private val networkClientFactory: NetworkClientFactory,
    private val configuration: AniListClientConfiguration,
    private val clock: Clock,
) {
    public fun create(): ProviderBinding {
        val retrofit = networkClientFactory.createRetrofit(configuration.apiBaseUrl.toHttpUrl())
        val service = retrofit.create(AniListGraphQlService::class.java)
        val serializedTokenRefresh =
            SerializedTokenRefresh { _ -> ProviderResult.Failure(ProviderFailure.AuthenticationRequired) }

        return ProviderBinding(
            provider = ProviderKey.ANILIST,
            authenticator =
                AniListAuthenticator(
                    configuration = configuration,
                    stateGenerator = SecureAniListAuthorizationStateGenerator(),
                    clock = clock,
                    serializedTokenRefresh = serializedTokenRefresh,
                ),
            identityDataSource = AniListIdentityDataSource(service),
            initialImportDataSource = AniListInitialImportDataSource(service),
            mediaDetailDataSource = AniListMediaDetailDataSource(service),
            mediaSearchDataSource = AniListMediaSearchDataSource(service),
            syncDataSource = AniListSyncDataSource(service),
        )
    }
}
