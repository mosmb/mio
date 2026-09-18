package io.morgan.mio.feature.you.api

import androidx.navigation3.runtime.NavKey
import io.morgan.mio.domain.model.CollectionEntryId
import io.morgan.mio.domain.model.OperationId
import kotlinx.serialization.Serializable

/** Intercepted by the app and replaced with the progress feature's registered resolution key. */
@Serializable
public data class YouResolveProgressRequest(
    public val operationIdValue: String,
    public val entryIdValue: String,
) : NavKey {
    init {
        require(operationIdValue.isNotBlank())
        require(entryIdValue.isNotBlank())
    }

    public val operationId: OperationId get() = OperationId(operationIdValue)

    public val entryId: CollectionEntryId get() = CollectionEntryId(entryIdValue)
}
