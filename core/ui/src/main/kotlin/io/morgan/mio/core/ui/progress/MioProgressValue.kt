package io.morgan.mio.core.ui.progress

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.Crossfade
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import io.morgan.mio.core.designsystem.component.MioProgressBar
import io.morgan.mio.core.designsystem.theme.MioTheme

@OptIn(androidx.compose.animation.ExperimentalAnimationApi::class)
@Composable
public fun MioProgressValue(
    value: Int,
    total: Int?,
    description: String,
    modifier: Modifier = Modifier,
    onMotionChanged: (Boolean) -> Unit = {},
) {
    require(value >= 0) { "MioProgressValue value must be nonnegative" }
    require(total == null || total > 0) { "MioProgressValue total must be positive when present" }
    require(description.isNotBlank()) { "MioProgressValue requires a nonblank description" }

    val reducedMotion = MioTheme.accessibility.reducedMotionEnabled
    val fraction = total?.let { totalValue -> value.toFloat() / totalValue.toFloat() }?.coerceIn(0f, 1f)
    val latestMotionChanged = rememberUpdatedState(onMotionChanged)
    val activeMotions = remember { mutableSetOf<String>() }
    val progressAnimation = remember { Animatable(fraction ?: 0f) }
    val reportMotion: (String, Boolean) -> Unit = { source, active ->
        val changed = if (active) activeMotions.add(source) else activeMotions.remove(source)
        if (changed) latestMotionChanged.value(activeMotions.isNotEmpty())
    }

    LaunchedEffect(fraction, reducedMotion) {
        val target = fraction ?: 0f
        if (reducedMotion || progressAnimation.value == target) {
            progressAnimation.snapTo(target)
            reportMotion("fill", false)
            return@LaunchedEffect
        }
        reportMotion("fill", true)
        try {
            progressAnimation.animateTo(target, tween(FILL_ANIMATION_MILLIS))
        } finally {
            reportMotion("fill", false)
        }
    }

    val numberTransition = updateTransition(value to description, label = "mioProgressNumber")
    LaunchedEffect(numberTransition) {
        snapshotFlow { numberTransition.isRunning || numberTransition.currentState != numberTransition.targetState }
            .collect { active -> reportMotion("number", active) }
    }

    DisposableEffect(Unit) {
        onDispose {
            activeMotions.clear()
            latestMotionChanged.value(false)
        }
    }

    Column(
        modifier =
            modifier.clearAndSetSemantics {
                contentDescription = description
                if (fraction != null) progressBarRangeInfo = ProgressBarRangeInfo(fraction, 0f..1f, 0)
            },
    ) {
        if (reducedMotion) {
            numberTransition.Crossfade(
                animationSpec = tween(MioTheme.motion.reducedMotionCrossfadeMillis),
            ) { visibleValue ->
                ProgressNumber(visibleValue)
            }
        } else {
            numberTransition.AnimatedContent(
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
            ) { visibleValue ->
                ProgressNumber(visibleValue)
            }
        }
        if (fraction != null) {
            MioProgressBar(
                progress = progressAnimation.value,
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
