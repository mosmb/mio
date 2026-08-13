package io.morgan.mio

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.morgan.mio.core.designsystem.component.MioBottomNavigation
import io.morgan.mio.core.designsystem.component.MioBottomNavigationItem
import io.morgan.mio.core.designsystem.icon.MioIcons
import io.morgan.mio.core.navigation.TopLevelDestination
import io.morgan.mio.core.navigation.TopLevelSlot

@Composable
internal fun RootNavigationBar(
    modifier: Modifier = Modifier,
    destinations: List<TopLevelDestination>,
    selectedSlot: TopLevelSlot,
    onSelected: (TopLevelDestination) -> Unit,
) {
    val orderedDestinations = TopLevelSlot.entries.map { slot -> destinations.single { it.slot == slot } }
    val destinationById = orderedDestinations.associateBy { it.slot.name }
    val items =
        TopLevelSlot.entries.map { slot ->
            MioBottomNavigationItem(
                id = slot.name,
                label = stringResource(navigationLabel(slot)),
                outlineIconRes = navigationOutlineIcon(slot),
                filledIconRes = navigationFilledIcon(slot),
            )
        }

    MioBottomNavigation(
        items = items,
        selectedItemId = selectedSlot.name,
        onItemSelected = { id -> onSelected(destinationById.getValue(id)) },
        modifier = modifier,
    )
}

private fun navigationLabel(slot: TopLevelSlot): Int =
    when (slot) {
        TopLevelSlot.TODAY -> R.string.navigation_today
        TopLevelSlot.LIBRARY -> R.string.navigation_library
        TopLevelSlot.DISCOVER -> R.string.navigation_discover
        TopLevelSlot.YOU -> R.string.navigation_you
    }

private fun navigationOutlineIcon(slot: TopLevelSlot): Int =
    when (slot) {
        TopLevelSlot.TODAY -> MioIcons.TodayOutline
        TopLevelSlot.LIBRARY -> MioIcons.LibraryOutline
        TopLevelSlot.DISCOVER -> MioIcons.DiscoverOutline
        TopLevelSlot.YOU -> MioIcons.YouOutline
    }

private fun navigationFilledIcon(slot: TopLevelSlot): Int =
    when (slot) {
        TopLevelSlot.TODAY -> MioIcons.TodayFilled
        TopLevelSlot.LIBRARY -> MioIcons.LibraryFilled
        TopLevelSlot.DISCOVER -> MioIcons.DiscoverFilled
        TopLevelSlot.YOU -> MioIcons.YouFilled
    }
