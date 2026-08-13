package io.morgan.mio.core.designsystem.component

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import io.morgan.mio.core.designsystem.accessibility.mioDecorative
import io.morgan.mio.core.designsystem.theme.MioTheme
import kotlin.math.roundToInt

@Composable
public fun MioSkeleton(
    modifier: Modifier = Modifier,
    shape: androidx.compose.ui.graphics.Shape = MioTheme.shapes.sm,
) {
    BoxWithConstraints(
        modifier =
            modifier
                .clip(shape)
                .background(MioTheme.colors.surfaceSoft)
                .mioDecorative(),
    ) {
        if (!MioTheme.accessibility.reducedMotionEnabled) {
            val transition = rememberInfiniteTransition(label = "mioSkeletonSweep")
            val sweep by
                transition.animateFloat(
                    initialValue = -0.2f,
                    targetValue = 1.2f,
                    animationSpec =
                        infiniteRepeatable(
                            animation = tween(1200, easing = LinearEasing),
                            repeatMode = RepeatMode.Restart,
                        ),
                    label = "mioSkeletonBand",
                )
            Box(
                modifier =
                    Modifier
                        .offset {
                            IntOffset(
                                x = (maxWidth.toPx() * sweep - 32.dp.toPx()).roundToInt(),
                                y = 0,
                            )
                        }.width(32.dp)
                        .fillMaxHeight()
                        .background(MioTheme.colors.accent.copy(alpha = 0.12f)),
            )
        }
    }
}
