package io.morgan.mio

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import io.morgan.mio.core.designsystem.component.MioSnackbarMessage
import io.morgan.mio.domain.account.AccountAuthorizationRedirect
import io.morgan.mio.domain.account.AccountSessionState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds

public class MainActivity : ComponentActivity() {
    protected override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
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
                if (accountSessionRepository.state.value is AccountSessionState.Authorizing) {
                    accountSessionRepository.completeAuthorization(redirect)
                }
            }
        }
        setContent {
            val observedSessionState = sessionState.collectAsStateWithLifecycle()
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
                graph.accountGraphLifecycle.activeHandle()?.withGraph { accountGraph ->
                    accountGraph.metroViewModelFactory
                } ?: graph.metroViewModelFactory
            MioApp(
                root = root,
                navigationRegistry = graph.navigationRegistry,
                imageLoader = graph.imageLoader,
                viewModelFactory = viewModelFactory,
                snackbar = snackbar,
                onSnackbarAction = { snackbar = null },
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val redirect = intent.authorizationRedirectOrNull() ?: return
        val repository = (application as MioApplication).appGraph.accountSessionRepository
        if (repository.state.value !is AccountSessionState.Authorizing) return
        lifecycleScope.launch {
            repository.completeAuthorization(redirect)
        }
    }
}

private fun Intent.authorizationRedirectOrNull(): AccountAuthorizationRedirect? {
    if (action != Intent.ACTION_VIEW) return null
    val callback = data ?: return null
    if (callback.scheme != "mio" || callback.host != "oauth") return null
    if (callback.path !in setOf("/anilist", "/myanimelist")) return null
    return dataString?.takeIf(String::isNotBlank)?.let(::AccountAuthorizationRedirect)
}
