package io.morgan.mio.domain.model

@JvmInline
public value class MediaTitle(
    public val value: String,
) {
    init {
        require(value.isNotBlank())
    }
}

@JvmInline
public value class ImageReference(
    public val value: String,
) {
    init {
        require(value.isNotBlank())
    }
}
