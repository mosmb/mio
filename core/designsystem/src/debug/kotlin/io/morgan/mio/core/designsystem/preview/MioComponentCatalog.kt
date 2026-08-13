package io.morgan.mio.core.designsystem.preview

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.morgan.mio.core.designsystem.component.MioBottomNavigation
import io.morgan.mio.core.designsystem.component.MioBottomNavigationItem
import io.morgan.mio.core.designsystem.component.MioBottomSheet
import io.morgan.mio.core.designsystem.component.MioButton
import io.morgan.mio.core.designsystem.component.MioButtonStyle
import io.morgan.mio.core.designsystem.component.MioEmptyState
import io.morgan.mio.core.designsystem.component.MioErrorState
import io.morgan.mio.core.designsystem.component.MioFilterPill
import io.morgan.mio.core.designsystem.component.MioIcon
import io.morgan.mio.core.designsystem.component.MioIconButton
import io.morgan.mio.core.designsystem.component.MioIconButtonStyle
import io.morgan.mio.core.designsystem.component.MioProgressBar
import io.morgan.mio.core.designsystem.component.MioRootTopBar
import io.morgan.mio.core.designsystem.component.MioSkeleton
import io.morgan.mio.core.designsystem.component.MioSnackbar
import io.morgan.mio.core.designsystem.component.MioSnackbarHost
import io.morgan.mio.core.designsystem.component.MioStatusRow
import io.morgan.mio.core.designsystem.component.MioTopBar
import io.morgan.mio.core.designsystem.component.MioTopBarAction
import io.morgan.mio.core.designsystem.icon.MioIcons
import io.morgan.mio.core.designsystem.theme.MioTheme

internal val MioComponentCatalogEntries: Set<String> =
    setOf(
        "MioIcon",
        "MioButton",
        "MioIconButton",
        "MioFilterPill",
        "MioStatusRow",
        "MioProgressBar",
        "MioRootTopBar",
        "MioTopBar",
        "MioBottomNavigation",
        "MioSnackbar",
        "MioSnackbarHost",
        "MioBottomSheet",
        "MioSkeleton",
        "MioEmptyState",
        "MioErrorState",
    )

@Composable
internal fun MioComponentCatalog() {
    var showBottomSheet by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Text("Mio component catalog", style = MioTheme.typography.title)

        CatalogSection("Icon") {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                MioIcon(MioIcons.Search, "Search")
                MioIcon(MioIcons.Check, null)
                MioIcon(MioIcons.Error, "Error")
            }
        }

        CatalogSection("Buttons") {
            MioButton("Primary", onClick = {})
            MioButton("Secondary", onClick = {}, style = MioButtonStyle.SECONDARY)
            MioButton("Disabled", onClick = {}, enabled = false)
            MioButton("Add with leading icon", onClick = {}, leadingIconRes = MioIcons.Add)
            MioButton(
                "A wrapping action label that remains readable at large text sizes",
                onClick = {},
                style = MioButtonStyle.SECONDARY,
            )
        }

        CatalogSection("Icon buttons") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MioIconButton(MioIcons.Search, "Search", onClick = {})
                MioIconButton(MioIcons.Add, "Add", onClick = {}, style = MioIconButtonStyle.PRIMARY)
                MioIconButton(MioIcons.Check, "Selected", onClick = {}, style = MioIconButtonStyle.SELECTED)
            }
        }

        CatalogSection("Filters and status rows") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MioFilterPill("Selected", selected = true, onClick = {})
                MioFilterPill("Available", selected = false, onClick = {})
                MioFilterPill("Disabled", selected = false, enabled = false, onClick = {})
            }
            MioStatusRow("watching", "Watching", selected = true, onSelected = {})
            MioStatusRow("planned", "Planned", selected = false, onSelected = {}, supportingText = "Choose later")
        }

        CatalogSection("Top bars") {
            MioRootTopBar(
                title = "Today",
                actions =
                    listOf(
                        MioTopBarAction("search", MioIcons.Search, "Search"),
                        MioTopBarAction("sort", MioIcons.Sort, "Sort"),
                    ),
                onAction = {},
            )
            MioRootTopBar(title = "No actions", onAction = {})
            MioTopBar(
                title = "A long nested title that wraps without centering the media title",
                backContentDescription = "Back",
                actions = listOf(MioTopBarAction("close", MioIcons.Close, "Close")),
                onBack = {},
                onAction = {},
            )
        }

        CatalogSection("Bottom navigation") {
            MioBottomNavigation(
                items = catalogNavigationItems,
                selectedItemId = "TODAY",
                onItemSelected = {},
            )
            MioBottomNavigation(
                items = catalogNavigationItems,
                selectedItemId = "DISCOVER",
                onItemSelected = {},
            )
        }

        CatalogSection("Snackbar") {
            MioSnackbar(message = "Library imported")
            MioSnackbar(message = "Progress updated", actionLabel = "Undo", onAction = {})
            Text("Host empty state", style = MioTheme.typography.label)
            Box(Modifier.fillMaxWidth().height(72.dp)) {
                MioSnackbarHost(snackbar = null, bottomNavigationVisible = false, onAction = {})
            }
            Text("Host above bottom navigation", style = MioTheme.typography.label)
            Box(Modifier.fillMaxWidth().height(164.dp)) {
                MioSnackbarHost(
                    snackbar = MioSnackbarMessagePreview,
                    bottomNavigationVisible = true,
                    onAction = {},
                )
            }
        }

        CatalogSection("Bottom sheet") {
            Text(
                "Open the focused modal preview to inspect the opaque sheet and its gesture behavior.",
                style = MioTheme.typography.body,
            )
            MioButton("Show bottom sheet", onClick = { showBottomSheet = true })
        }

        CatalogSection("Progress and skeletons") {
            MioProgressBar(0f, progressDescription = "0 percent")
            MioProgressBar(0.42f, progressDescription = "42 percent")
            MioProgressBar(1f, progressDescription = "100 percent")
            MioSkeleton(Modifier.fillMaxWidth().height(48.dp))
            MioSkeleton(Modifier.fillMaxWidth().height(96.dp), shape = MioTheme.shapes.md)
            MioSkeleton(Modifier.fillMaxWidth(0.45f).height(18.dp), shape = MioTheme.shapes.full)
        }

        CatalogSection("Empty and error") {
            MioEmptyState(
                title = "Nothing here yet",
                message = "A concise empty result can explain the next step.",
                illustration = {
                    Box(Modifier.size(96.dp).background(MioTheme.colors.surfaceSoft, MioTheme.shapes.lg))
                },
                actionLabel = "Take action",
                onAction = {},
            )
            MioErrorState(
                title = "Could not load",
                message = "Try again when the local operation is ready.",
                retryLabel = "Retry",
                onRetry = {},
            )
        }
    }
    MioBottomSheet(
        visible = showBottomSheet,
        onDismissRequest = { showBottomSheet = false },
        title = "Status",
        subtitle = "Choose one status",
    ) {
        MioStatusRow("watching", "Watching", selected = true, onSelected = {})
        MioStatusRow("completed", "Completed", selected = false, onSelected = {})
    }
}

private val catalogNavigationItems =
    listOf(
        MioBottomNavigationItem("TODAY", "Today", MioIcons.TodayOutline, MioIcons.TodayFilled),
        MioBottomNavigationItem("LIBRARY", "Library", MioIcons.LibraryOutline, MioIcons.LibraryFilled),
        MioBottomNavigationItem("DISCOVER", "Discover", MioIcons.DiscoverOutline, MioIcons.DiscoverFilled),
        MioBottomNavigationItem("YOU", "You", MioIcons.YouOutline, MioIcons.YouFilled),
    )

private val MioSnackbarMessagePreview =
    io.morgan.mio.core.designsystem.component.MioSnackbarMessage(
        id = "progress",
        message = "Progress updated",
        actionLabel = "Undo",
    )

@Composable
private fun CatalogSection(
    title: String,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(title, style = MioTheme.typography.section)
        content()
    }
}

@Preview(name = "Reference light", widthDp = 393, heightDp = 852, showBackground = true)
@Composable
internal fun MioComponentCatalogReferenceLightPreview() {
    MioPreviewSurface(MioPreviewConfiguration.Light) { MioComponentCatalog() }
}

@Preview(
    name = "Reference dark",
    widthDp = 393,
    heightDp = 852,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
    showBackground = true,
)
@Composable
internal fun MioComponentCatalogReferenceDarkPreview() {
    MioPreviewSurface(MioPreviewConfiguration.Dark) { MioComponentCatalog() }
}

@Preview(name = "Compact light", widthDp = 320, heightDp = 693, showBackground = true)
@Composable
internal fun MioComponentCatalogCompactLightPreview() {
    MioPreviewSurface(MioPreviewConfiguration.Light) { MioComponentCatalog() }
}

@Preview(name = "Large text light", widthDp = 393, heightDp = 852, fontScale = 2f, showBackground = true)
@Composable
internal fun MioComponentCatalogLargeTextLightPreview() {
    MioPreviewSurface(MioPreviewConfiguration.Light) { MioComponentCatalog() }
}

@Preview(
    name = "Large text dark",
    widthDp = 393,
    heightDp = 852,
    fontScale = 2f,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
    showBackground = true,
)
@Composable
internal fun MioComponentCatalogLargeTextDarkPreview() {
    MioPreviewSurface(MioPreviewConfiguration.Dark) { MioComponentCatalog() }
}

@Preview(name = "High contrast light", widthDp = 393, heightDp = 852, showBackground = true)
@Composable
internal fun MioComponentCatalogHighContrastLightPreview() {
    MioPreviewSurface(MioPreviewConfiguration.HighContrastLight) { MioComponentCatalog() }
}

@Preview(
    name = "High contrast dark",
    widthDp = 393,
    heightDp = 852,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
    showBackground = true,
)
@Composable
internal fun MioComponentCatalogHighContrastDarkPreview() {
    MioPreviewSurface(MioPreviewConfiguration.HighContrastDark) { MioComponentCatalog() }
}

@Preview(name = "Reduced motion light", widthDp = 393, heightDp = 852, showBackground = true)
@Composable
internal fun MioComponentCatalogReducedMotionLightPreview() {
    MioPreviewSurface(MioPreviewConfiguration.ReducedMotionLight) { MioComponentCatalog() }
}

@Preview(
    name = "Reduced motion dark",
    widthDp = 393,
    heightDp = 852,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
    showBackground = true,
)
@Composable
internal fun MioComponentCatalogReducedMotionDarkPreview() {
    MioPreviewSurface(MioPreviewConfiguration.ReducedMotionDark) { MioComponentCatalog() }
}

@Preview(name = "Focused bottom navigation", widthDp = 393, heightDp = 160, showBackground = true)
@Composable
internal fun MioBottomNavigationFocusedPreview() {
    MioPreviewSurface(MioPreviewConfiguration.Light) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
            MioBottomNavigation(
                items = catalogNavigationItems,
                selectedItemId = "LIBRARY",
                onItemSelected = {},
            )
        }
    }
}

@Preview(name = "Focused snackbar", widthDp = 393, heightDp = 160, showBackground = true)
@Composable
internal fun MioSnackbarFocusedPreview() {
    MioPreviewSurface(MioPreviewConfiguration.Dark) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
            MioSnackbar(message = "Progress updated", actionLabel = "Undo", onAction = {})
        }
    }
}

@Preview(name = "Focused bottom sheet", widthDp = 393, heightDp = 852, showBackground = true)
@Composable
internal fun MioBottomSheetFocusedPreview() {
    MioPreviewSurface(MioPreviewConfiguration.Light) {
        MioBottomSheet(
            visible = true,
            onDismissRequest = {},
            title = "Status",
            subtitle = "Choose one status",
        ) {
            MioStatusRow("watching", "Watching", selected = true, onSelected = {})
            MioStatusRow("completed", "Completed", selected = false, onSelected = {})
        }
    }
}
