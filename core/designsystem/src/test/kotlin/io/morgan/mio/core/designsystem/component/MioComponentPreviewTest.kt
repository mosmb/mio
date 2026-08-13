package io.morgan.mio.core.designsystem.component

import io.morgan.mio.core.designsystem.preview.MioComponentCatalogEntries
import org.junit.Assert.assertEquals
import org.junit.Test

class MioComponentPreviewTest {
    @Test
    fun catalogManifestCoversTheCanonicalTaskNineComponents() {
        assertEquals(
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
            ),
            MioComponentCatalogEntries,
        )
    }
}
