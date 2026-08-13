package io.morgan.mio.core.designsystem.component

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.morgan.mio.core.designsystem.icon.MioIcons
import io.morgan.mio.core.designsystem.theme.MioTheme

@Immutable
public data class MioTopBarAction(
    public val id: String,
    @field:DrawableRes public val iconRes: Int,
    public val contentDescription: String,
    public val enabled: Boolean = true,
)

@Composable
public fun MioRootTopBar(
    title: String,
    onAction: (String) -> Unit,
    modifier: Modifier = Modifier,
    actions: List<MioTopBarAction> = emptyList(),
) {
    require(actions.size <= 2) { "MioRootTopBar supports at most two actions" }
    require(actions.all { it.id.isNotBlank() && it.contentDescription.isNotBlank() }) {
        "MioRootTopBar actions require nonblank IDs and descriptions"
    }

    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = 72.dp)
                .background(MioTheme.colors.surface)
                .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MioTheme.typography.display,
            modifier = Modifier.weight(1f).semantics { heading() },
        )
        if (actions.isNotEmpty()) {
            Spacer(Modifier.width(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                actions.forEach { action ->
                    MioIconButton(
                        iconRes = action.iconRes,
                        contentDescription = action.contentDescription,
                        onClick = { onAction(action.id) },
                        enabled = action.enabled,
                    )
                }
            }
        }
    }
}

@Composable
public fun MioTopBar(
    title: String,
    backContentDescription: String,
    onBack: () -> Unit,
    onAction: (String) -> Unit,
    modifier: Modifier = Modifier,
    actions: List<MioTopBarAction> = emptyList(),
) {
    require(backContentDescription.isNotBlank()) { "MioTopBar requires a nonblank back description" }
    require(actions.size <= 2) { "MioTopBar supports at most two actions" }
    require(actions.all { it.id.isNotBlank() && it.contentDescription.isNotBlank() }) {
        "MioTopBar actions require nonblank IDs and descriptions"
    }

    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .background(MioTheme.colors.surface)
                .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MioIconButton(
            iconRes = MioIcons.Back,
            contentDescription = backContentDescription,
            onClick = onBack,
        )
        Text(
            text = title,
            style = MioTheme.typography.title,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier =
                Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp)
                    .semantics { heading() },
        )
        if (actions.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                actions.forEach { action ->
                    MioIconButton(
                        iconRes = action.iconRes,
                        contentDescription = action.contentDescription,
                        onClick = { onAction(action.id) },
                        enabled = action.enabled,
                    )
                }
            }
        }
    }
}
