package io.morgan.mio

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen

public class MainActivity : ComponentActivity() {
    protected override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val graph = (application as MioApplication).appGraph
        setContent {
            MioApp(
                root = AppRoot.ONBOARDING,
                navigationRegistry = graph.navigationRegistry,
                imageLoader = graph.imageLoader,
            )
        }
    }
}
