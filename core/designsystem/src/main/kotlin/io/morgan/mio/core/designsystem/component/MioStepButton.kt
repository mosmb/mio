package io.morgan.mio.core.designsystem.component

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.morgan.mio.core.designsystem.theme.MioTheme

@Composable
public fun MioStepButton(
    @DrawableRes iconRes: Int,
    contentDescription: String,
    enabled: Boolean,
    accent: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    interactionSource: MutableInteractionSource? = null,
) {
    require(contentDescription.isNotBlank()) { "MioStepButton requires a nonblank contentDescription" }
    val rememberedInteractionSource = remember { MutableInteractionSource() }
    val resolvedInteractionSource = interactionSource ?: rememberedInteractionSource
    val pressed by resolvedInteractionSource.collectIsPressedAsState()
    val colors = MioTheme.colors
    val shape = MioTheme.shapes.md
    val containerColor =
        when {
            !enabled -> colors.surfaceSoft
            accent -> colors.accent
            pressed -> colors.surfaceSoft
            else -> colors.surface
        }
    val contentColor =
        when {
            !enabled -> colors.textMuted
            accent -> colors.onAccent
            else -> colors.text
        }

    MioPressVisual(
        interactionSource = resolvedInteractionSource,
        enabled = enabled,
        modifier =
            modifier
                .size(52.dp)
                .semantics { this.contentDescription = contentDescription }
                .mioClickable(
                    interactionSource = resolvedInteractionSource,
                    enabled = enabled,
                    role = Role.Button,
                    onClick = onClick,
                ),
        visualModifier =
            Modifier
                .clip(shape)
                .background(containerColor, shape)
                .then(if (accent) Modifier else Modifier.border(1.dp, colors.borderStrong, shape)),
        contentAlignment = Alignment.Center,
    ) {
        MioIcon(
            iconRes = iconRes,
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = contentColor,
        )
    }
}
