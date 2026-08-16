package io.morgan.mio.domain.model

@JvmInline
public value class MediaId(
    public val value: String,
) {
    init {
        require(value.isNotBlank())
    }
}
