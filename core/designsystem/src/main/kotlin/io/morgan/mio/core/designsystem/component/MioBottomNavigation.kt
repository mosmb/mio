package io.morgan.mio.core.designsystem.component

import androidx.annotation.DrawableRes
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import io.morgan.mio.core.designsystem.theme.MioTheme
import io.morgan.mio.core.designsystem.theme.asSpringSpec
import kotlin.math.roundToInt

@Immutable
public data class MioBottomNavigationItem(
    public val id: String,
    public val label: String,
    @field:DrawableRes public val outlineIconRes: Int,
    @field:DrawableRes public val filledIconRes: Int,
)

@Composable
public fun MioBottomNavigation(
    items: List<MioBottomNavigationItem>,
    selectedItemId: String,
    onItemSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    require(items.isNotEmpty()) { "MioBottomNavigation requires at least one item" }
    require(items.all { it.id.isNotBlank() && it.label.isNotBlank() }) {
        "MioBottomNavigation items require nonblank IDs and labels"
    }
    require(items.map(MioBottomNavigationItem::id).distinct().size == items.size) {
        "MioBottomNavigation item IDs must be unique"
    }
    require(items.any { it.id == selectedItemId }) {
        "MioBottomNavigation selectedItemId must exist in items"
    }

    Box(
        modifier = modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
    ) {
        BoxWithConstraints(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 68.dp)
                    .shadow(MioTheme.elevation.floatingLow, MioTheme.shapes.nav)
                    .clip(MioTheme.shapes.nav)
                    .background(MioTheme.colors.surface)
                    .border(1.dp, MioTheme.colors.border, MioTheme.shapes.nav)
                    .padding(6.dp),
        ) {
            val itemGap = 4.dp
            val itemWidth = (maxWidth - itemGap * (items.size - 1)) / items.size
            val selectedIndex = items.indexOfFirst { it.id == selectedItemId }
            val animatedIndex by
                animateFloatAsState(
                    targetValue = selectedIndex.toFloat(),
                    animationSpec =
                        if (MioTheme.accessibility.reducedMotionEnabled) {
                            snap()
                        } else {
                            MioTheme.motion.rootTabSelection.asSpringSpec()
                        },
                    label = "mioRootTabSelection",
                )

            Box(Modifier.matchParentSize()) {
                Box(
                    modifier =
                        Modifier
                            .offset {
                                IntOffset(
                                    x = ((itemWidth + itemGap).toPx() * animatedIndex).roundToInt(),
                                    y = 0,
                                )
                            }.width(itemWidth)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(16.dp))
                            .background(MioTheme.colors.accent),
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items.forEach { item ->
                    MioBottomNavigationItemContent(
                        item = item,
                        selected = item.id == selectedItemId,
                        onClick = { onItemSelected(item.id) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun MioBottomNavigationItemContent(
    item: MioBottomNavigationItem,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val contentColor = if (selected) MioTheme.colors.onAccent else MioTheme.colors.textMuted
    MioPressVisual(
        interactionSource = interactionSource,
        enabled = true,
        modifier =
            modifier
                .heightIn(min = 54.dp)
                .mioClickable(
                    interactionSource = interactionSource,
                    role = Role.Tab,
                    onClick = onClick,
                ).semantics(mergeDescendants = true) {
                    this.selected = selected
                },
        visualModifier = Modifier.clip(RoundedCornerShape(16.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Crossfade(
                targetState = selected,
                animationSpec = tween(MioTheme.motion.rootTabIconCrossfadeMillis),
                label = "mioRootTabIcon",
            ) { isSelected ->
                MioIcon(
                    iconRes = if (isSelected) item.filledIconRes else item.outlineIconRes,
                    contentDescription = null,
                    modifier =
                        Modifier.testTag(
                            "MioBottomNavigation:${item.id}:${if (isSelected) "filled" else "outline"}",
                        ),
                    tint = contentColor,
                )
            }
            Text(
                text = item.label,
                color = contentColor,
                style = MioTheme.typography.label,
            )
        }
    }
}
