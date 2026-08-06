package io.morgan.mio

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

public class MainActivity : ComponentActivity() {
    protected override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MioPlaceholder()
        }
    }
}

@Composable
private fun MioPlaceholder() {
    BasicText(
        text = "Mio",
        modifier = Modifier.fillMaxSize(),
    )
}
