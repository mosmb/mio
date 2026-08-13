package io.morgan.mio.core.designsystem.component

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.morgan.mio.core.designsystem.theme.MioTheme

public enum class MioIconButtonStyle {
    NEUTRAL,
    PRIMARY,
    SELECTED,
}

@Composable
public fun MioIconButton(
    @DrawableRes iconRes: Int,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    style: MioIconButtonStyle = MioIconButtonStyle.NEUTRAL,
) {
    require(contentDescription.isNotBlank()) { "MioIconButton requires a nonblank contentDescription" }

    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val colors = MioTheme.colors
    val selected = style == MioIconButtonStyle.SELECTED
    val containerColor =
        when {
            !enabled -> colors.surfaceSoft
            style == MioIconButtonStyle.NEUTRAL && pressed -> colors.surfaceSoft
            style == MioIconButtonStyle.NEUTRAL -> colors.surface
            else -> colors.accent
        }
    val contentColor =
        when {
            !enabled -> colors.textMuted
            style == MioIconButtonStyle.NEUTRAL -> colors.text
            else -> colors.onAccent
        }

    MioPressVisual(
        interactionSource = interactionSource,
        enabled = enabled,
        modifier =
            modifier
                .size(48.dp)
                .mioClickable(
                    interactionSource = interactionSource,
                    enabled = enabled,
                    role = Role.Button,
                    onClick = onClick,
                ).semantics {
                    this.selected = selected
                },
        visualModifier =
            Modifier
                .clip(MioTheme.shapes.xs)
                .background(containerColor, MioTheme.shapes.xs),
        contentAlignment = Alignment.Center,
    ) {
        MioIcon(
            iconRes = iconRes,
            contentDescription = contentDescription,
            modifier = Modifier.size(24.dp),
            tint = contentColor,
        )
    }
}
