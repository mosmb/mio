package io.morgan.mio.core.designsystem.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import io.morgan.mio.core.designsystem.theme.MioTheme
import io.morgan.mio.core.designsystem.theme.asSpringSpec

@Composable
internal fun MioPressVisual(
    interactionSource: MutableInteractionSource,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    visualModifier: Modifier = Modifier,
    contentAlignment: Alignment = Alignment.TopStart,
    content: @Composable () -> Unit,
) {
    val pressed by interactionSource.collectIsPressedAsState()
    val reducedMotion = MioTheme.accessibility.reducedMotionEnabled
    val scale by
        animateFloatAsState(
            targetValue = if (enabled && pressed && !reducedMotion) MioTheme.motion.pressScale else 1f,
            animationSpec =
                when {
                    reducedMotion -> snap()
                    pressed -> tween(MioTheme.motion.pressDownMillis)
                    else -> MioTheme.motion.pressRelease.asSpringSpec()
                },
            label = "mioPressScale",
        )

    Box(
        modifier =
            modifier
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }.then(visualModifier)
                .indication(
                    interactionSource = interactionSource,
                    indication = ripple(color = MioTheme.colors.text),
                ),
        contentAlignment = contentAlignment,
    ) {
        content()
    }
}

@Composable
internal fun mioIsPressed(interactionSource: MutableInteractionSource): Boolean =
    interactionSource.collectIsPressedAsState().value

internal fun Modifier.mioClickable(
    interactionSource: MutableInteractionSource,
    enabled: Boolean = true,
    role: Role? = null,
    onClick: () -> Unit,
): Modifier =
    composed {
        val latestOnClick by rememberUpdatedState(onClick)
        this
            .semantics(mergeDescendants = true) {
                if (role != null) this.role = role
                if (enabled) {
                    onClick {
                        latestOnClick()
                        true
                    }
                } else {
                    disabled()
                }
            }.pointerInput(enabled, interactionSource) {
                if (enabled) {
                    awaitEachGesture {
                        var activePress: PressInteraction.Press? = null
                        var gestureSettled = false
                        var isInside = true
                        var originalPointer: PointerId? = null

                        fun cancelActivePress() {
                            activePress?.let { press -> interactionSource.tryEmit(PressInteraction.Cancel(press)) }
                            activePress = null
                        }

                        try {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            originalPointer = down.id
                            if (down.isConsumed) {
                                gestureSettled = true
                            } else {
                                activePress = PressInteraction.Press(down.position)
                                interactionSource.tryEmit(requireNotNull(activePress))

                                while (true) {
                                    val event = awaitPointerEvent()
                                    val change =
                                        event.changes.firstOrNull { pointerChange ->
                                            pointerChange.id == originalPointer
                                        } ?: continue

                                    if (change.isConsumed) {
                                        cancelActivePress()
                                        gestureSettled = true
                                        break
                                    }

                                    val nowInside = change.isInside(size.width, size.height)
                                    if (nowInside && !isInside && activePress == null) {
                                        activePress = PressInteraction.Press(change.position)
                                        interactionSource.tryEmit(requireNotNull(activePress))
                                    } else if (!nowInside && isInside) {
                                        cancelActivePress()
                                    }
                                    isInside = nowInside

                                    if (change.changedToUp()) {
                                        val press = activePress
                                        activePress = null
                                        if (press != null) {
                                            interactionSource.tryEmit(PressInteraction.Release(press))
                                            latestOnClick()
                                        }
                                        gestureSettled = true
                                        break
                                    }
                                }
                            }
                        } finally {
                            if (!gestureSettled) cancelActivePress()
                        }
                    }
                }
            }
    }

private fun PointerInputChange.isInside(
    width: Int,
    height: Int,
): Boolean = position.x >= 0f && position.y >= 0f && position.x <= width.toFloat() && position.y <= height.toFloat()
