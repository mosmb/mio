package io.morgan.mio.feature.library.api

import io.morgan.mio.core.navigation.TopLevelNavigationRequest
import io.morgan.mio.core.navigation.TopLevelSlot
import kotlinx.serialization.Serializable

@Serializable
public data class LibraryKey(
    public val readyRequestId: String? = null,
) : TopLevelNavigationRequest {
    override val targetSlot: TopLevelSlot = TopLevelSlot.LIBRARY

    override val replacesTargetStack: Boolean
        get() = readyRequestId != null

    init {
        require(readyRequestId == null || readyRequestId.isNotBlank())
    }
}
