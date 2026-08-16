package io.morgan.mio.core.navigation

import androidx.navigation3.runtime.NavKey

public data class TopLevelDestination(
    public val slot: TopLevelSlot,
    public val key: NavKey,
)
