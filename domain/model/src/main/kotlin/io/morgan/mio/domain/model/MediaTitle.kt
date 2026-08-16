package io.morgan.mio.domain.model

@JvmInline
public value class MediaTitle(
    public val value: String,
) {
    init {
        require(value.isNotBlank())
    }
}
