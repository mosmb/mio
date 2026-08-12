package io.morgan.mio.core.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Immutable
public data class MioElevation(
    public val flat: Dp,
    public val artwork: Dp,
    public val floatingLow: Dp,
    public val floatingHigh: Dp,
) {
    public companion object {
        public val Default: MioElevation =
            MioElevation(
                flat = 0.dp,
                artwork = 4.dp,
                floatingLow = 6.dp,
                floatingHigh = 8.dp,
            )
    }
}
