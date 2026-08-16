package io.morgan.mio.domain.model

@JvmInline
public value class OperationId(
    public val value: String,
) {
    init {
        require(value.isNotBlank())
    }
}
