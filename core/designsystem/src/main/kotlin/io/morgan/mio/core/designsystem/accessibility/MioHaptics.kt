package io.morgan.mio.core.designsystem.accessibility

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import io.morgan.mio.core.designsystem.theme.MioTheme

public enum class MioHapticType {
    ROUTINE_TICK,
    CONFIRMATION,
    REJECTED,
}

public interface MioHaptics {
    public fun perform(type: MioHapticType)
}

@Composable
public fun rememberMioHaptics(): MioHaptics {
    val hapticFeedback = LocalHapticFeedback.current
    val enabled = MioTheme.accessibility.hapticsEnabled
    return remember(hapticFeedback, enabled) {
        RememberedMioHaptics(hapticFeedback, enabled)
    }
}

@Stable
private class RememberedMioHaptics(
    private val hapticFeedback: HapticFeedback,
    private val enabled: Boolean,
) : MioHaptics {
    override fun perform(type: MioHapticType) {
        if (enabled) {
            hapticFeedback.performHapticFeedback(type.toComposeType())
        }
    }
}

private fun MioHapticType.toComposeType(): HapticFeedbackType =
    when (this) {
        MioHapticType.ROUTINE_TICK -> HapticFeedbackType.SegmentFrequentTick
        MioHapticType.CONFIRMATION -> HapticFeedbackType.Confirm
        MioHapticType.REJECTED -> HapticFeedbackType.Reject
    }
