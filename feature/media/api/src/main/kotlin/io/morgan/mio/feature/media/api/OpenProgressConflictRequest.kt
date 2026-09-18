package io.morgan.mio.feature.media.api

import androidx.navigation3.runtime.NavKey
import io.morgan.mio.domain.model.CollectionEntryId
import io.morgan.mio.domain.model.OperationId
import kotlinx.serialization.Serializable

@Serializable
public data class OpenProgressConflictRequest(
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
