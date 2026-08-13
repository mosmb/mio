package io.morgan.mio.core.designsystem.component

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.morgan.mio.core.designsystem.layout.MioWindowInsets
import io.morgan.mio.core.designsystem.theme.MioTheme

@Immutable
public data class MioSnackbarMessage(
    public val id: String,
    public val message: String,
    public val actionLabel: String? = null,
)

@Composable
public fun MioSnackbar(
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    require((actionLabel == null) == (onAction == null)) {
        "MioSnackbar requires both actionLabel and onAction, or neither"
    }

    val announcement =
        if (actionLabel == null) {
            message
        } else {
            "$message. $actionLabel available."
        }
    val shape = MioTheme.shapes.md
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .padding(horizontal = 16.dp)
                .shadow(MioTheme.elevation.floatingLow, shape)
                .clip(shape)
                .background(MioTheme.colors.surfaceRaised)
                .border(1.dp, MioTheme.colors.border, shape)
                .semantics(mergeDescendants = true) {
                    liveRegion = LiveRegionMode.Polite
                    contentDescription = announcement
                }.padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = message,
            color = MioTheme.colors.text,
            style = MioTheme.typography.body,
            modifier = Modifier.weight(1f),
        )
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.width(8.dp))
            MioSnackbarAction(
                label = actionLabel,
                onClick = onAction,
            )
        }
    }
}

@Composable
public fun MioSnackbarHost(
    snackbar: MioSnackbarMessage?,
    bottomNavigationVisible: Boolean,
    onAction: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .fillMaxSize()
                .padding(bottom = if (bottomNavigationVisible) 92.dp else 12.dp)
                .windowInsetsPadding(MioWindowInsets.ime),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Crossfade(
            targetState = snackbar,
            animationSpec =
                tween(
                    if (MioTheme.accessibility.reducedMotionEnabled) {
                        MioTheme.motion.reducedMotionCrossfadeMillis
                    } else {
                        MioTheme.motion.smallStateCrossfadeMillis
                    },
                ),
            label = "mioSnackbarReplacement",
        ) { visibleSnackbar ->
            if (visibleSnackbar != null) {
                MioSnackbar(
                    message = visibleSnackbar.message,
                    actionLabel = visibleSnackbar.actionLabel,
                    onAction = { onAction(visibleSnackbar.id) }.takeIf { visibleSnackbar.actionLabel != null },
                )
            }
        }
    }
}

@Composable
private fun MioSnackbarAction(
    label: String,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val shape = MioTheme.shapes.full
    MioPressVisual(
        interactionSource = interactionSource,
        enabled = true,
        modifier =
            Modifier
                .heightIn(min = 48.dp)
                .widthIn(min = 48.dp)
                .mioClickable(
                    interactionSource = interactionSource,
                    role = Role.Button,
                    onClick = onClick,
                ),
        visualModifier = Modifier.clip(shape).background(MioTheme.colors.accent, shape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = MioTheme.colors.onAccent,
            style = MioTheme.typography.bodyStrong,
            modifier = Modifier.padding(horizontal = 12.dp),
        )
    }
}
