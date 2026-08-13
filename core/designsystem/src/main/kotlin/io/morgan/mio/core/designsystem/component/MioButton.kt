package io.morgan.mio.core.designsystem.component

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.morgan.mio.core.designsystem.theme.MioTheme

public enum class MioButtonStyle {
    PRIMARY,
    SECONDARY,
}

@Composable
public fun MioButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    style: MioButtonStyle = MioButtonStyle.PRIMARY,
    @DrawableRes leadingIconRes: Int? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed = mioIsPressed(interactionSource)
    val colors = MioTheme.colors
    val shape = MioTheme.shapes.md
    val containerColor =
        when {
            !enabled -> colors.surfaceSoft
            pressed && style == MioButtonStyle.SECONDARY -> colors.surfaceSoft
            style == MioButtonStyle.PRIMARY -> colors.accent
            else -> colors.surface
        }
    val contentColor = if (enabled) style.contentColor(colors) else colors.textMuted
    val borderModifier =
        if (style == MioButtonStyle.SECONDARY) {
            Modifier.border(1.dp, if (enabled) colors.border else colors.borderStrong, shape)
        } else {
            Modifier
        }

    MioPressVisual(
        interactionSource = interactionSource,
        enabled = enabled,
        modifier =
            modifier
                .heightIn(min = 52.dp)
                .mioClickable(
                    interactionSource = interactionSource,
                    enabled = enabled,
                    role = Role.Button,
                    onClick = onClick,
                ),
        visualModifier =
            Modifier
                .clip(shape)
                .then(borderModifier)
                .background(containerColor),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 15.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (leadingIconRes != null) {
                MioIcon(
                    iconRes = leadingIconRes,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = contentColor,
                )
                Spacer(Modifier.size(8.dp))
            }
            Text(
                text = text,
                color = contentColor,
                style = MioTheme.typography.bodyStrong,
            )
        }
    }
}

private fun MioButtonStyle.contentColor(colors: io.morgan.mio.core.designsystem.theme.MioColors): Color =
    when (this) {
        MioButtonStyle.PRIMARY -> colors.onAccent
        MioButtonStyle.SECONDARY -> colors.text
    }
