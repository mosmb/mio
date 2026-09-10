package io.morgan.mio.core.ui.progress

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.Crossfade
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import io.morgan.mio.core.designsystem.component.MioProgressBar
import io.morgan.mio.core.designsystem.theme.MioTheme

@Composable
public fun MioProgressValue(
    value: Int,
    total: Int?,
    description: String,
    modifier: Modifier = Modifier,
) {
    require(value >= 0) { "MioProgressValue value must be nonnegative" }
    require(total == null || total > 0) { "MioProgressValue total must be positive when present" }
    require(description.isNotBlank()) { "MioProgressValue requires a nonblank description" }

    val reducedMotion = MioTheme.accessibility.reducedMotionEnabled
    val fraction = total?.let { totalValue -> value.toFloat() / totalValue.toFloat() }?.coerceIn(0f, 1f)
    val animatedFraction by
        animateFloatAsState(
            targetValue = fraction ?: 0f,
            animationSpec = if (reducedMotion) snap() else tween(FILL_ANIMATION_MILLIS),
            label = "mioProgressFill",
        )

    Column(
        modifier =
            modifier.clearAndSetSemantics {
                contentDescription = description
                if (fraction != null) progressBarRangeInfo = ProgressBarRangeInfo(fraction, 0f..1f, 0)
            },
    ) {
        if (reducedMotion) {
            Crossfade(
                targetState = value to description,
                animationSpec = tween(MioTheme.motion.reducedMotionCrossfadeMillis),
                label = "mioProgressValueReducedMotion",
            ) { visibleValue ->
                ProgressNumber(visibleValue)
            }
        } else {
            AnimatedContent(
                targetState = value to description,
                transitionSpec = {
                    val direction = if (targetState.first >= initialState.first) -1 else 1
                    ContentTransform(
                        targetContentEnter =
                            slideInVertically(tween(NUMBER_ANIMATION_MILLIS)) { height ->
                                -direction *
                                    height
                            },
                        initialContentExit =
                            slideOutVertically(tween(NUMBER_ANIMATION_MILLIS)) { height ->
                                direction *
                                    height
                            },
                        sizeTransform = SizeTransform(clip = false),
                    )
                },
                label = "mioProgressValueRoll",
            ) { visibleValue ->
                ProgressNumber(visibleValue)
            }
        }
        if (fraction != null) {
            MioProgressBar(
                progress = animatedFraction,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun ProgressNumber(value: Pair<Int, String>) {
    Text(
        text = value.second,
        style = MioTheme.typography.bodyStrong,
    )
}

private const val NUMBER_ANIMATION_MILLIS: Int = 180
private const val FILL_ANIMATION_MILLIS: Int = 220
