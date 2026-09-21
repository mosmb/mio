package io.morgan.mio.core.ui.progress

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import io.morgan.mio.core.designsystem.accessibility.MioHapticType
import io.morgan.mio.core.designsystem.accessibility.rememberMioHaptics
import io.morgan.mio.core.designsystem.theme.MioTheme
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** A status label whose celebration is keyed by an already committed, undoable action. */
@Composable
public fun MioAchievementStamp(
    completed: Boolean,
    label: String,
    completionId: String?,
    modifier: Modifier = Modifier,
    sourceBounds: Rect? = null,
    labelStyle: TextStyle = MioTheme.typography.bodyStrong,
    labelColor: Color = LocalContentColor.current,
    onFinished: (String) -> Unit = {},
) {
    val reducedMotion = MioTheme.accessibility.reducedMotionEnabled
    val haptics by rememberUpdatedState(rememberMioHaptics())
    val finished by rememberUpdatedState(onFinished)
    // Existing completed state is static on entry; only a new commit while mounted celebrates.
    var previousId by rememberSaveable { mutableStateOf(completionId) }
    var targetBounds by remember { mutableStateOf<Rect?>(null) }
    val travel = remember(completionId, completed, reducedMotion) { Animatable(1f) }
    val particles = remember(completionId, completed, reducedMotion) { Animatable(1f) }
    val opacity = remember(completionId, completed, reducedMotion) { Animatable(1f) }
    LaunchedEffect(completionId, completed, reducedMotion) {
        if (!completed || completionId == null) return@LaunchedEffect
        if (previousId != completionId) {
            previousId = completionId
            if (reducedMotion) {
                opacity.snapTo(0f)
                opacity.animateTo(1f, tween(160))
                haptics.perform(MioHapticType.CONFIRMATION)
            } else {
                travel.snapTo(0f)
                opacity.snapTo(0f)
                opacity.animateTo(1f, tween(durationMillis = 1, delayMillis = 120))
                travel.animateTo(1f, tween(380))
                haptics.perform(MioHapticType.CONFIRMATION)
                particles.snapTo(0f)
                particles.animateTo(1f, tween(350, easing = LinearEasing))
            }
        }
        finished(completionId)
    }
    val accent = MioTheme.colors.accent
    val textColor = if (completed) MioTheme.colors.onAccent else labelColor
    Box(modifier.onGloballyPositioned { targetBounds = it.boundsInWindow() }) {
        if (label.isNotEmpty()) {
            Text(
                text = label,
                style = labelStyle,
                color = textColor,
                modifier =
                    Modifier
                        .graphicsLayer {
                            if (completed) {
                                alpha = opacity.value
                                val t = travel.value
                                val target = targetBounds
                                val source = sourceBounds
                                if (!reducedMotion && target != null && source != null) {
                                    translationX = (source.right - target.right) * (1f - t)
                                    translationY = (source.center.y - target.center.y) * (1f - t)
                                    val pulse = sin(t * PI).toFloat()
                                    scaleX = 1f + 0.05f * pulse
                                    scaleY = scaleX
                                    rotationZ = -6f * pulse
                                }
                            }
                        }.drawBehind {
                            val p = particles.value
                            if (completed && !reducedMotion && p < 1f) {
                                repeat(6) { index ->
                                    val angle = index * PI / 3
                                    val distance = (12f + 20f * p).dp.toPx()
                                    val center =
                                        Offset(
                                            if (index in 2..4) 0f else size.width,
                                            size.height / 2f,
                                        ) + Offset(cos(angle).toFloat(), sin(angle).toFloat()) * distance
                                    drawRect(
                                        color = (if (index % 2 == 0) accent else Color.White).copy(alpha = 1f - p),
                                        topLeft = center,
                                        size =
                                            androidx.compose.ui.geometry
                                                .Size(3.dp.toPx(), 3.dp.toPx()),
                                    )
                                }
                            }
                        }.then(
                            if (completed) {
                                Modifier
                                    .background(
                                        accent,
                                        MioTheme.shapes.sm,
                                    ).padding(horizontal = 8.dp, vertical = 4.dp)
                            } else {
                                Modifier
                            },
                        ),
            )
        }
    }
}

@androidx.compose.ui.tooling.preview.Preview(name = "Completed light", widthDp = 393)
@androidx.compose.ui.tooling.preview.Preview(name = "Completed large text", widthDp = 393, fontScale = 2f)
@Composable
private fun StampLightPreview() {
    io.morgan.mio.core.designsystem.preview.MioPreviewSurface(
        io.morgan.mio.core.designsystem.preview.MioPreviewConfiguration.Light,
    ) { MioAchievementStamp(true, "Completed", null, Modifier.padding(24.dp)) }
}

@androidx.compose.ui.tooling.preview.Preview(name = "Completed dark reduced motion", widthDp = 393)
@Composable
private fun StampReducedMotionPreview() {
    io.morgan.mio.core.designsystem.preview.MioPreviewSurface(
        io.morgan.mio.core.designsystem.preview.MioPreviewConfiguration.ReducedMotionDark,
    ) { MioAchievementStamp(true, "Completed", null, Modifier.padding(24.dp)) }
}
