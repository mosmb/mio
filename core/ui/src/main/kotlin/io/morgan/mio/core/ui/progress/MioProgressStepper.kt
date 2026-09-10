package io.morgan.mio.core.ui.progress

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.morgan.mio.core.designsystem.component.MioStepButton
import io.morgan.mio.core.designsystem.icon.MioIcons
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop

@Composable
public fun MioProgressStepper(
    decreaseDescription: String,
    increaseDescription: String,
    decreaseEnabled: Boolean,
    increaseEnabled: Boolean,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
    modifier: Modifier = Modifier,
    onInteractionChanged: (Boolean) -> Unit = {},
) {
    require(decreaseDescription.isNotBlank()) { "MioProgressStepper requires a decrease description" }
    require(increaseDescription.isNotBlank()) { "MioProgressStepper requires an increase description" }
    val latestInteractionChanged by rememberUpdatedState(onInteractionChanged)
    val decreaseInteractionSource = remember { MutableInteractionSource() }
    val increaseInteractionSource = remember { MutableInteractionSource() }
    val decreasePressed = decreaseInteractionSource.collectIsPressedAsState()
    val increasePressed = increaseInteractionSource.collectIsPressedAsState()

    LaunchedEffect(decreaseInteractionSource, increaseInteractionSource) {
        snapshotFlow { decreasePressed.value || increasePressed.value }
            .distinctUntilChanged()
            .drop(1)
            .collect { pressed -> latestInteractionChanged(pressed) }
    }
    DisposableEffect(Unit) {
        onDispose { latestInteractionChanged(false) }
    }

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        MioStepButton(
            iconRes = MioIcons.Remove,
            contentDescription = decreaseDescription,
            enabled = decreaseEnabled,
            accent = false,
            onClick = onDecrease,
            modifier = Modifier.size(52.dp),
            interactionSource = decreaseInteractionSource,
        )
        MioStepButton(
            iconRes = MioIcons.Add,
            contentDescription = increaseDescription,
            enabled = increaseEnabled,
            accent = true,
            onClick = onIncrease,
            modifier = Modifier.size(52.dp),
            interactionSource = increaseInteractionSource,
        )
    }
}

@androidx.compose.ui.tooling.preview.Preview(name = "Progress bounds", widthDp = 320, fontScale = 2f)
@Composable
private fun ProgressBoundsPreview() {
    io.morgan.mio.core.designsystem.preview.MioPreviewSurface(
        io.morgan.mio.core.designsystem.preview.MioPreviewConfiguration.Light,
    ) {
        androidx.compose.foundation.layout.Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            MioProgressValue(0, null, "Episode 0")
            MioProgressStepper("Decrease episode to 0", "Increase episode to 1", false, true, {}, {})
            MioProgressValue(Int.MAX_VALUE, null, "Chapter ${Int.MAX_VALUE}")
            MioProgressStepper(
                "Decrease chapter to ${Int.MAX_VALUE - 1}",
                "Maximum chapter reached",
                true,
                false,
                {},
                {},
            )
        }
    }
}
