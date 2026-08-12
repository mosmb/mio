package io.morgan.mio.core.designsystem.theme

import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.pow

@Immutable
public data class MioSpringToken(
    public val responseSeconds: Float,
    public val dampingRatio: Float,
)

public fun MioSpringToken.asSpringSpec(): SpringSpec<Float> {
    val stiffness = ((2f * PI.toFloat()) / responseSeconds).pow(2f)
    return spring(
        dampingRatio = dampingRatio,
        stiffness = stiffness,
    )
}

@Immutable
public data class MioMotion(
    public val pressDownMillis: Int,
    public val pressScale: Float,
    public val pressRelease: MioSpringToken,
    public val rootTabSelection: MioSpringToken,
    public val rootTabIconCrossfadeMillis: Int,
    public val nestedNavigationMillis: Int,
    public val nestedNavigationOffset: Dp,
    public val bottomSheet: MioSpringToken,
    public val scrimMillis: Int,
    public val smallStateCrossfadeMillis: Int,
    public val themeCrossfadeMillis: Int,
    public val reducedMotionCrossfadeMillis: Int,
) {
    public companion object {
        public val Default: MioMotion =
            MioMotion(
                pressDownMillis = 80,
                pressScale = 0.96f,
                pressRelease = MioSpringToken(responseSeconds = 0.28f, dampingRatio = 1.0f),
                rootTabSelection = MioSpringToken(responseSeconds = 0.30f, dampingRatio = 0.9f),
                rootTabIconCrossfadeMillis = 120,
                nestedNavigationMillis = 220,
                nestedNavigationOffset = 16.dp,
                bottomSheet = MioSpringToken(responseSeconds = 0.30f, dampingRatio = 0.8f),
                scrimMillis = 180,
                smallStateCrossfadeMillis = 160,
                themeCrossfadeMillis = 180,
                reducedMotionCrossfadeMillis = 160,
            )
    }
}
