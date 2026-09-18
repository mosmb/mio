package io.morgan.mio.feature.progress.api

import io.morgan.mio.core.navigation.FocusedDestination
import kotlinx.serialization.Serializable

@Serializable
public data class ProgressConflictKey(
    public val operationIdValue: String,
    public val entryIdValue: String,
) : FocusedDestination
