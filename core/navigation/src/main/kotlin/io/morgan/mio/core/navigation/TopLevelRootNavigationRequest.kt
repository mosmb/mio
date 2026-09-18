package io.morgan.mio.core.navigation

import androidx.navigation3.runtime.NavKey

public interface TopLevelRootNavigationRequest : NavKey {
    public val targetSlot: TopLevelSlot
}
