package io.morgan.mio.core.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Immutable
public data class MioSpacing(
    public val space1: Dp,
    public val space2: Dp,
    public val space3: Dp,
    public val space4: Dp,
    public val space5: Dp,
    public val space6: Dp,
    public val space8: Dp,
    public val space12: Dp,
    public val screenGutter: Dp,
    public val rootTopContentPadding: Dp,
    public val unrelatedSectionMinimum: Dp,
    public val rootBottomClearance: Dp,
) {
    public companion object {
        public val Default: MioSpacing =
            MioSpacing(
                space1 = 4.dp,
                space2 = 8.dp,
                space3 = 12.dp,
                space4 = 16.dp,
                space5 = 20.dp,
                space6 = 24.dp,
                space8 = 32.dp,
                space12 = 48.dp,
                screenGutter = 16.dp,
                rootTopContentPadding = 24.dp,
                unrelatedSectionMinimum = 24.dp,
                rootBottomClearance = 28.dp,
            )
    }
}
