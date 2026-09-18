package io.morgan.mio.core.navigation

import androidx.navigation3.runtime.NavKey

public interface TopLevelNavigationRequest : NavKey {
    public val targetSlot: TopLevelSlot

    public val replacesTargetStack: Boolean
}
