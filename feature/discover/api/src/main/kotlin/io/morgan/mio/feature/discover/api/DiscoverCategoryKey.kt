package io.morgan.mio.feature.discover.api

import io.morgan.mio.core.navigation.FocusedDestination
import kotlinx.serialization.Serializable

@Serializable
public data class DiscoverCategoryKey(
    public val mediaType: String,
    public val category: String,
) : FocusedDestination
