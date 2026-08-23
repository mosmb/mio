package io.morgan.mio

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import io.morgan.mio.domain.account.AccountSessionState
import kotlinx.coroutines.launch

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
        }
        setContent {
            val observedSessionState = sessionState.collectAsStateWithLifecycle()
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
            )
        }
    }
}
