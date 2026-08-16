package io.morgan.mio.domain.model

@JvmInline
public value class ImageReference(
    public val value: String,
) {
    init {
        require(value.isNotBlank())
    }
}
