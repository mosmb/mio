package io.morgan.mio.data.provider.api

public data class ProviderMediaBrowseRequest(
    public val type: ProviderMediaType,
    public val category: ProviderBrowseCategory,
    public val year: Int,
    public val season: String,
    public val limit: Int = 40,
) {
    init {
        require(year in 1900..9999)
        require(season in listOf("WINTER", "SPRING", "SUMMER", "FALL"))
        require(limit in 1..50)
    }
}
