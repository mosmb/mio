package io.morgan.mio.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.Text
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

@Composable
public fun MioFilterPill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val colors = MioTheme.colors
    val shape = MioTheme.shapes.full
    val containerColor =
        when {
            !enabled -> colors.surfaceSoft
            selected -> colors.accent
            pressed && enabled -> colors.surfaceSoft
            else -> colors.surface
        }
    val contentColor =
        when {
            !enabled -> colors.textMuted
            selected -> colors.onAccent
            else -> colors.text
        }

    val borderModifier =
        if (selected) {
            Modifier
        } else {
            Modifier.border(1.dp, colors.border, shape)
        }

    MioPressVisual(
        interactionSource = interactionSource,
        enabled = enabled,
        modifier =
            modifier
                .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                .mioClickable(
                    interactionSource = interactionSource,
                    enabled = enabled,
                    role = Role.Tab,
                    onClick = onClick,
                ).semantics(mergeDescendants = true) {
                    this.selected = selected
                }.padding(vertical = 4.dp),
        visualModifier =
            Modifier
                .clip(shape)
                .then(borderModifier)
                .background(containerColor, shape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 12.dp),
            color = contentColor,
            style = MioTheme.typography.label,
            maxLines = 1,
        )
    }
}
