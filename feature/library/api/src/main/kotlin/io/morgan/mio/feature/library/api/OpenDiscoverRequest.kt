package io.morgan.mio.feature.library.api

import io.morgan.mio.core.navigation.TopLevelRootNavigationRequest
import io.morgan.mio.core.navigation.TopLevelSlot
import kotlinx.serialization.Serializable

@Serializable
public data object OpenDiscoverRequest : TopLevelRootNavigationRequest {
    override val targetSlot: TopLevelSlot = TopLevelSlot.DISCOVER
}
