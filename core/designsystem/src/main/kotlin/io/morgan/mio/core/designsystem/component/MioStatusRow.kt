package io.morgan.mio.core.designsystem.component

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import io.morgan.mio.core.designsystem.icon.MioIcons
import io.morgan.mio.core.designsystem.theme.MioTheme

@Composable
public fun MioStatusRow(
    id: String,
    label: String,
    selected: Boolean,
    onSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
    @DrawableRes leadingIconRes: Int? = null,
    supportingText: String? = null,
    enabled: Boolean = true,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val colors = MioTheme.colors

    MioPressVisual(
        interactionSource = interactionSource,
        enabled = enabled,
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp)
                .mioClickable(
                    interactionSource = interactionSource,
                    enabled = enabled,
                    role = Role.RadioButton,
                    onClick = { onSelected(id) },
                ).semantics(mergeDescendants = true) {
                    this.selected = selected
                },
        visualModifier =
            Modifier
                .clip(MioTheme.shapes.sm)
                .background(if (pressed && enabled) colors.surfaceSoft else colors.surface),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (leadingIconRes != null) {
                Box(
                    modifier =
                        Modifier
                            .size(32.dp)
                            .clip(MioTheme.shapes.sm)
                            .background(colors.surfaceSoft),
                    contentAlignment = Alignment.Center,
                ) {
                    MioIcon(
                        iconRes = leadingIconRes,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = colors.textMuted,
                    )
                }
                Spacer(Modifier.width(12.dp))
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = label,
                    color = if (enabled) colors.text else colors.textMuted,
                    style = if (selected) MioTheme.typography.bodyStrong else MioTheme.typography.body,
                )
                if (supportingText != null) {
                    Text(
                        text = supportingText,
                        color = colors.textMuted,
                        style = MioTheme.typography.caption,
                    )
                }
            }
            if (selected) {
                Spacer(Modifier.width(12.dp))
                MioIcon(
                    iconRes = MioIcons.Check,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = colors.accent,
                )
            }
        }
    }
}
