package io.morgan.mio.data.provider.api

public data class ProviderMediaSearchRequest(
    public val query: String,
    public val type: ProviderMediaType,
    public val limit: Int,
    public val cursor: ProviderSearchCursor?,
) {
    init {
        require(query.isNotBlank())
        require(query == query.trim())
        require(limit > 0)
    }
}
