package io.morgan.mio.feature.you.impl

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import io.morgan.mio.core.designsystem.component.MioIcon
import io.morgan.mio.core.designsystem.theme.MioTheme
import io.morgan.mio.domain.model.MediaType

@Composable
internal fun YouStatisticsSelector(
    selectedType: MediaType,
    onSelect: (MediaType) -> Unit,
) {
    Surface(
        shape = CircleShape,
        color = MioTheme.colors.surface,
        border = BorderStroke(1.dp, MioTheme.colors.border),
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth().padding(4.dp)) {
            val segmentWidth = maxWidth / 2
            val indicatorOffset by animateDpAsState(
                if (selectedType == MediaType.ANIME) 0.dp else segmentWidth,
                label = "Statistics selection",
            )
            Box(
                Modifier
                    .offset { IntOffset(indicatorOffset.roundToPx(), 0) }
                    .width(segmentWidth)
                    .height(48.dp)
                    .background(MioTheme.colors.accent, CircleShape),
            )
            Row(Modifier.selectableGroup()) {
                listOf(MediaType.ANIME, MediaType.MANGA).forEach { type ->
                    val selected = selectedType == type
                    val color = if (selected) MioTheme.colors.onAccent else MioTheme.colors.text
                    Row(
                        modifier =
                            Modifier
                                .weight(1f)
                                .height(48.dp)
                                .clip(CircleShape)
                                .selectable(selected = selected, role = Role.Tab, onClick = { onSelect(type) }),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                    ) {
                        MioIcon(
                            if (type == MediaType.ANIME) R.drawable.you_tv else R.drawable.you_menu_book,
                            null,
                            tint = color,
                        )
                        Text(
                            stringResource(
                                if (type ==
                                    MediaType.ANIME
                                ) {
                                    R.string.you_type_anime
                                } else {
                                    R.string.you_type_manga
                                },
                            ),
                            style = MioTheme.typography.bodyStrong,
                            color = color,
                        )
                    }
                }
            }
        }
    }
}
