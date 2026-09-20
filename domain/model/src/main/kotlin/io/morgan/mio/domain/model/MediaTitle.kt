package io.morgan.mio.domain.model

public data class MediaTitle(
    public val value: String,
    public val english: String? = null,
    public val japanese: String? = null,
    public val romaji: String? = null,
) {
    init {
        require(value.isNotBlank())
    }
}
