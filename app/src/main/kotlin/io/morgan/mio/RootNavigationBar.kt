package io.morgan.mio

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.morgan.mio.core.navigation.TopLevelDestination
import io.morgan.mio.core.navigation.TopLevelSlot

@Composable
internal fun RootNavigationBar(
    modifier: Modifier = Modifier,
    destinations: List<TopLevelDestination>,
    selectedSlot: TopLevelSlot,
    onSelected: (TopLevelDestination) -> Unit,
) {
    Row(
        modifier = modifier.fillMaxWidth().selectableGroup(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TopLevelSlot.entries.forEach { slot ->
            val destination = destinations.single { it.slot == slot }
            BasicText(
                text = stringResource(navigationLabel(slot)),
                modifier =
                    Modifier
                        .weight(1f)
                        .selectable(
                            selected = selectedSlot == slot,
                            role = Role.Tab,
                            onClick = { onSelected(destination) },
                        ).heightIn(min = 48.dp)
                        .wrapContentHeight(Alignment.CenterVertically),
            )
        }
    }
}

private fun navigationLabel(slot: TopLevelSlot): Int =
    when (slot) {
        TopLevelSlot.TODAY -> R.string.navigation_today
        TopLevelSlot.LIBRARY -> R.string.navigation_library
        TopLevelSlot.DISCOVER -> R.string.navigation_discover
        TopLevelSlot.YOU -> R.string.navigation_you
    }
