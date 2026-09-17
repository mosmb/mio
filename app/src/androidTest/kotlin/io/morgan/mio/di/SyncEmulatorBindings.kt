package io.morgan.mio.di

import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.Provides
import io.morgan.mio.core.network.NetworkClientFactory
import io.morgan.mio.data.provider.anilist.AniListClientConfiguration
import io.morgan.mio.data.provider.anilist.AniListProviderFactory
import io.morgan.mio.data.provider.api.ProviderAuthenticator
import io.morgan.mio.data.provider.api.ProviderAuthorizationLaunch
import io.morgan.mio.data.provider.api.ProviderAuthorizationRedirect
import io.morgan.mio.data.provider.api.ProviderAuthorizationRequest
import io.morgan.mio.data.provider.api.ProviderAuthorizationTransaction
import io.morgan.mio.data.provider.api.ProviderBinding
import io.morgan.mio.data.provider.api.ProviderBindingResolver
import io.morgan.mio.data.provider.api.ProviderCredentials
import io.morgan.mio.data.provider.api.ProviderFailure
import io.morgan.mio.data.provider.api.ProviderKey
import io.morgan.mio.data.provider.api.ProviderResult
import io.morgan.mio.data.provider.myanimelist.MyAnimeListClientConfiguration
import io.morgan.mio.data.provider.myanimelist.MyAnimeListProviderFactory
import kotlin.time.Clock

@BindingContainer(includes = [ProcessBindings::class, SyncProcessBindings::class])
internal class SyncEmulatorBindings(
    private val secureStore: io.morgan.mio.data.provider.api.ProviderCredentialStore,
) {
    @Provides
    fun credentialStore(): io.morgan.mio.data.provider.api.ProviderCredentialStore =
        object : io.morgan.mio.data.provider.api.ProviderCredentialStore by secureStore {
            override suspend fun write(credentials: ProviderCredentials) {
                if (task21ReconnectMode() == "write_failure") {
                    throw io.morgan.mio.data.provider.api
                        .ProviderCredentialStorageException()
                }
                secureStore.write(credentials)
            }
        }

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

            override fun bindingFor(provider: ProviderKey): ProviderBinding? {
                val binding =
                    when (provider) {
                        ProviderKey.ANILIST -> anilist
                        ProviderKey.MY_ANIME_LIST -> mal
                    }
                if (
                    androidx.test.platform.app.InstrumentationRegistry
                        .getArguments()
                        .getString("task21Fixture") != "true"
                ) {
                    return binding
                }
                return ProviderBinding(
                    provider = binding.provider,
                    authenticator = Task21FixtureAuthenticator(binding.authenticator),
                    identityDataSource = binding.identityDataSource,
                    initialImportDataSource = binding.initialImportDataSource,
                    mediaDetailDataSource = binding.mediaDetailDataSource,
                    syncDataSource = binding.syncDataSource,
                )
            }
        }
}

private class Task21FixtureAuthenticator(
    private val delegate: ProviderAuthenticator,
) : ProviderAuthenticator {
    override val provider: ProviderKey = delegate.provider

    override suspend fun beginAuthorization(
        request: ProviderAuthorizationRequest,
    ): ProviderResult<ProviderAuthorizationLaunch> {
        require(request.redirectUri == provider.callbackUri)
        val context =
            androidx.test.platform.app.InstrumentationRegistry
                .getInstrumentation()
                .targetContext
        val attempts = java.io.File(context.filesDir, "task21-reconnect-attempt")
        attempts.writeText(
            java.util.UUID
                .randomUUID()
                .toString(),
        )
        val mode = reconnectMode()
        if (mode == "launch_failure") {
            return ProviderResult.Failure(ProviderFailure.Unexpected)
        }
        return ProviderResult.Success(
            ProviderAuthorizationLaunch(
                authorizationUrl =
                    if (mode == "browser_cancel") {
                        "http://127.0.0.1:18820/task21-authorize"
                    } else {
                        "${provider.callbackUri}#task21-fixture-state"
                    },
                transaction = ProviderAuthorizationTransaction("task21-fixture-state", "task21-fixture-verifier"),
            ),
        )
    }

    override suspend fun completeAuthorization(
        transaction: ProviderAuthorizationTransaction,
        redirect: ProviderAuthorizationRedirect,
    ): ProviderResult<ProviderCredentials> {
        if (
            transaction.state != "task21-fixture-state" ||
            !redirect.redirectUri.startsWith(provider.callbackUri)
        ) {
            return ProviderResult.Failure(ProviderFailure.InvalidAuthorizationCallback)
        }
        when (reconnectMode()) {
            "denied" -> return ProviderResult.Failure(ProviderFailure.AuthorizationDenied)
            "invalid_callback" -> return ProviderResult.Failure(ProviderFailure.InvalidAuthorizationCallback)
        }
        val accessToken =
            if (reconnectMode() == "wrong_identity") {
                "task21-wrong-identity-token"
            } else {
                "task21-reconnected-access"
            }
        return ProviderResult.Success(
            ProviderCredentials(
                provider = provider,
                accessToken = accessToken,
                refreshToken = "task21-reconnected-refresh",
                expiresAt = null,
                tokenType = "Bearer",
            ),
        )
    }

    override suspend fun refresh(credentials: ProviderCredentials): ProviderResult<ProviderCredentials> =
        delegate.refresh(credentials)

    private fun reconnectMode(): String = task21ReconnectMode()
}

private val ProviderKey.callbackUri: String
    get() =
        when (this) {
            ProviderKey.ANILIST -> "mio://oauth/anilist"
            ProviderKey.MY_ANIME_LIST -> "mio://oauth/myanimelist"
        }

private fun task21ReconnectMode(): String {
    val context =
        androidx.test.platform.app.InstrumentationRegistry
            .getInstrumentation()
            .targetContext
    val mode = java.io.File(context.filesDir, "task21-reconnect-mode")
    return if (mode.isFile) {
        mode.readText().trim()
    } else {
        androidx.test.platform.app.InstrumentationRegistry
            .getArguments()
            .getString("task21ReconnectMode")
            .orEmpty()
    }
}
