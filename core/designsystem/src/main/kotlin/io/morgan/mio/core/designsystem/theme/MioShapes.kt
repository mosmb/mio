package io.morgan.mio.core.designsystem.theme

import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.dp

@Immutable
public data class MioShapes(
    public val xs: CornerBasedShape,
    public val sm: CornerBasedShape,
    public val md: CornerBasedShape,
    public val lg: CornerBasedShape,
    public val nav: CornerBasedShape,
    public val full: CornerBasedShape,
) {
    public val materialShapes: Shapes =
        Shapes(
            extraSmall = xs,
            small = sm,
            medium = md,
            large = lg,
            extraLarge = lg,
        )

    public companion object {
        public val Default: MioShapes =
            MioShapes(
                xs = RoundedCornerShape(10.dp),
                sm = RoundedCornerShape(12.dp),
                md = RoundedCornerShape(16.dp),
                lg = RoundedCornerShape(24.dp),
                nav = RoundedCornerShape(22.dp),
                full = RoundedCornerShape(999.dp),
            )
    }
}
