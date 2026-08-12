package io.morgan.mio.core.designsystem.accessibility

import androidx.compose.foundation.layout.sizeIn
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

public object MioTouchTarget {
    public val MinimumSize: Dp = 48.dp
    public val MinimumSeparation: Dp = 4.dp
}

public fun Modifier.mioMinimumTouchTarget(): Modifier =
    sizeIn(
        minWidth = MioTouchTarget.MinimumSize,
        minHeight = MioTouchTarget.MinimumSize,
    )
