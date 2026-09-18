package io.morgan.mio

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.net.toUri
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import io.morgan.mio.core.designsystem.component.MioSnackbarMessage
import io.morgan.mio.core.ui.AccountRecoveryActions
import io.morgan.mio.core.ui.AccountSessionActions
import io.morgan.mio.core.ui.LocalAccountRecoveryActions
import io.morgan.mio.core.ui.LocalAccountSessionActions
import io.morgan.mio.domain.account.AccountAuthorizationRedirect
import io.morgan.mio.domain.account.AccountReconnectionState
import io.morgan.mio.domain.account.AccountSessionState
import io.morgan.mio.domain.model.AccountId
import io.morgan.mio.domain.preferences.AppearancePreference
import io.morgan.mio.domain.preferences.DevicePreferences
import io.morgan.mio.domain.preferences.MotionPreference
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.launch
import java.io.IOException
import kotlin.time.Duration.Companion.seconds

public class MainActivity : ComponentActivity() {
    private var reconnectAccountId: AccountId? = null
    private var reconnectIssueIds: Set<String> = emptySet()

    private var returningFromReconnect = false

    protected override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        reconnectAccountId = savedInstanceState?.getString("reconnectAccountId")?.let(::AccountId)
        reconnectIssueIds = savedInstanceState?.getStringArrayList("reconnectIssueIds")?.toSet().orEmpty()
        returningFromReconnect = savedInstanceState?.getBoolean("returningFromReconnect") ?: false
        enableEdgeToEdge()
        val graph = (application as MioApplication).appGraph
        val accountSessionRepository = graph.accountSessionRepository
        val sessionState = accountSessionRepository.state
        splashScreen.setKeepOnScreenCondition {
            sessionState.value is AccountSessionState.Restoring
        }
        lifecycleScope.launch {
            accountSessionRepository.restore()
            intent.authorizationRedirectOrNull()?.let { redirect ->
                setIntent(Intent(this@MainActivity, MainActivity::class.java).setAction(Intent.ACTION_MAIN))
                completeAuthorizationRedirect(redirect)
            }
        }
        setContent {
            val observedSessionState = sessionState.collectAsStateWithLifecycle()
            val preferencesFlow =
                remember(graph.preferencesRepository) {
                    graph.preferencesRepository.observeDevicePreferences().retryWhen { cause, _ ->
                        if (cause is IOException) {
                            // Keep the last rendered theme while a transient storage read recovers.
                            delay(1.seconds)
                            true
                        } else {
                            false
                        }
                    }
                }
            val devicePreferences by
                preferencesFlow.collectAsStateWithLifecycle(initialValue = DevicePreferences.Default)
            val darkTheme =
                when (devicePreferences.appearance) {
                    AppearancePreference.SYSTEM -> isSystemInDarkTheme()
                    AppearancePreference.LIGHT -> false
                    AppearancePreference.DARK -> true
                }
            SideEffect {
                val insetsController = WindowInsetsControllerCompat(window, window.decorView)
                insetsController.isAppearanceLightStatusBars = !darkTheme
                insetsController.isAppearanceLightNavigationBars = !darkTheme
            }
            var previousSessionState by
                androidx.compose.runtime.remember {
                    androidx.compose.runtime.mutableStateOf<AccountSessionState>(sessionState.value)
                }
            var snackbar by
                androidx.compose.runtime.remember {
                    androidx.compose.runtime.mutableStateOf<MioSnackbarMessage?>(null)
                }
            androidx.compose.runtime.LaunchedEffect(observedSessionState.value) {
                val current = observedSessionState.value
                if (
                    previousSessionState is AccountSessionState.Importing &&
                    current is AccountSessionState.Authenticated
                ) {
                    snackbar =
                        MioSnackbarMessage(
                            id = "library-imported",
                            message = getString(R.string.library_imported),
                        )
                }
                previousSessionState = current
            }
            androidx.compose.runtime.LaunchedEffect(snackbar?.id) {
                val visible = snackbar ?: return@LaunchedEffect
                delay(4.seconds)
                if (snackbar == visible) snackbar = null
            }
            val root =
                if (observedSessionState.value is AccountSessionState.Authenticated) {
                    AppRoot.AUTHENTICATED
                } else {
                    AppRoot.ONBOARDING
                }
            val viewModelFactory =
                graph.accountGraphLifecycle.withActiveGraph { accountGraph ->
                    accountGraph.metroViewModelFactory
                } ?: graph.metroViewModelFactory
            CompositionLocalProvider(
                LocalAccountRecoveryActions provides
                    AccountRecoveryActions(
                        providerLabel =
                            observedSessionState.value.providerLabel(
                                anilist = getString(R.string.provider_anilist),
                                myAnimeList = getString(R.string.provider_myanimelist),
                                fallback = getString(R.string.provider_unknown),
                            ),
                        onReconnect = ::startReconnect,
                        onRetryRefresh = graph.accountRefreshObserver::requestRefresh,
                    ),
                LocalAccountSessionActions provides
                    AccountSessionActions(
                        onDisconnect = {
                            graph.processCoroutineScope.launch {
                                graph.accountSessionRepository.disconnect()
                            }
                        },
                    ),
            ) {
                MioApp(
                    root = root,
                    navigationRegistry = graph.navigationRegistry,
                    imageLoader = graph.imageLoader,
                    viewModelFactory = viewModelFactory,
                    darkTheme = darkTheme,
                    reducedMotionOverride = devicePreferences.motion == MotionPreference.REDUCED,
                    hapticsEnabled = devicePreferences.hapticsEnabled,
                    snackbar = snackbar,
                    onSnackbarAction = { snackbar = null },
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        (application as MioApplication).appGraph.accountRefreshObserver.start(lifecycleScope)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("returningFromReconnect", returningFromReconnect)
        outState.putString("reconnectAccountId", reconnectAccountId?.value)
        outState.putStringArrayList("reconnectIssueIds", ArrayList(reconnectIssueIds))
        super.onSaveInstanceState(outState)
    }

    override fun onResume() {
        super.onResume()
        if (returningFromReconnect) {
            returningFromReconnect = false
            lifecycleScope.launch {
                (application as MioApplication).appGraph.accountReconnectionRepository.cancel()
                reconnectAccountId = null
                reconnectIssueIds = emptySet()
            }
        }
    }

    override fun onStop() {
        returningFromReconnect = reconnectAccountId != null && !isChangingConfigurations
        (application as MioApplication).appGraph.accountRefreshObserver.stop()
        super.onStop()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val redirect = intent.authorizationRedirectOrNull() ?: return
        completeAuthorizationRedirect(redirect)
    }

    private fun completeAuthorizationRedirect(redirect: AccountAuthorizationRedirect) {
        returningFromReconnect = false
        val graph = (application as MioApplication).appGraph
        val sessionRepository = graph.accountSessionRepository
        if (sessionRepository.state.value is AccountSessionState.Authorizing) {
            lifecycleScope.launch { sessionRepository.completeAuthorization(redirect) }
            return
        }
        if (graph.accountReconnectionRepository.state.value !is AccountReconnectionState.Authorizing) return
        lifecycleScope.launch {
            graph.accountReconnectionRepository.complete(redirect)
            val state = graph.accountReconnectionRepository.state.value
            val accountId = reconnectAccountId
            if (state is AccountReconnectionState.Succeeded && state.accountId == accountId) {
                val recoveredAccountId = state.accountId
                val recoveryRepository =
                    graph.accountGraphLifecycle.withActiveGraph { accountGraph ->
                        accountGraph.syncRecoveryRepository
                    }
                try {
                    recoveryRepository?.recoverAuthentication(recoveredAccountId, reconnectIssueIds)
                } catch (_: io.morgan.mio.domain.sync.SyncInfrastructureException) {
                    // A failed recovery commit retains the durable issue so Reconnect remains available.
                }
                graph.accountRefreshObserver.requestRefresh()
            }
            reconnectAccountId = null
            reconnectIssueIds = emptySet()
        }
    }

    private fun startReconnect() {
        val graph = (application as MioApplication).appGraph
        lifecycleScope.launch {
            if (graph.accountReconnectionRepository.state.value is AccountReconnectionState.Authorizing) return@launch
            val authenticated =
                graph.accountSessionRepository.state.value as? AccountSessionState.Authenticated ?: return@launch
            val recoveryRepository =
                graph.accountGraphLifecycle.withActiveGraph { accountGraph ->
                    accountGraph.syncRecoveryRepository
                }
            val recoveryState =
                recoveryRepository?.observeState()?.first()
            reconnectAccountId = authenticated.accountId
            reconnectIssueIds =
                buildSet {
                    recoveryState?.accountIssue?.id?.let(::add)
                    recoveryState?.entryIssues?.forEach { issue -> add("${issue.operationId.value}:sync") }
                }
            val launch =
                graph.accountReconnectionRepository.start()
                    ?: run {
                        reconnectAccountId = null
                        reconnectIssueIds = emptySet()
                        return@launch
                    }
            try {
                startActivity(Intent(Intent.ACTION_VIEW, launch.authorizationUri.toUri()))
            } catch (_: ActivityNotFoundException) {
                graph.accountReconnectionRepository.cancel()
                reconnectAccountId = null
                reconnectIssueIds = emptySet()
            }
        }
    }
}

private fun AccountSessionState.providerLabel(
    anilist: String,
    myAnimeList: String,
    fallback: String,
): String =
    when (this) {
        is AccountSessionState.Authenticated -> provider.label(anilist, myAnimeList)
        is AccountSessionState.Authorizing -> provider.label(anilist, myAnimeList)
        is AccountSessionState.Importing -> provider.label(anilist, myAnimeList)
        is AccountSessionState.Failed -> provider?.label(anilist, myAnimeList) ?: fallback
        is AccountSessionState.Disconnecting -> provider.label(anilist, myAnimeList)
        AccountSessionState.Restoring,
        AccountSessionState.SignedOut,
        -> fallback
    }

private fun io.morgan.mio.domain.account.ConnectedProvider.label(
    anilist: String,
    myAnimeList: String,
): String =
    when (this) {
        io.morgan.mio.domain.account.ConnectedProvider.ANILIST -> anilist
        io.morgan.mio.domain.account.ConnectedProvider.MY_ANIME_LIST -> myAnimeList
    }

private fun Intent.authorizationRedirectOrNull(): AccountAuthorizationRedirect? {
    if (action != Intent.ACTION_VIEW) return null
    val callback = data ?: return null
    if (callback.scheme != "mio" || callback.host != "oauth") return null
    if (callback.path !in setOf("/anilist", "/myanimelist")) return null
    return dataString?.takeIf(String::isNotBlank)?.let(::AccountAuthorizationRedirect)
}
