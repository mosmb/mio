package io.morgan.mio

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge

public class MainActivity : ComponentActivity() {
    protected override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val navigationRegistry = (application as MioApplication).appGraph.navigationRegistry
        setContent {
            MioApp(
                root = AppRoot.ONBOARDING,
                navigationRegistry = navigationRegistry,
            )
        }
    }
}
