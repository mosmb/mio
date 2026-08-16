package io.morgan.mio.domain.model

@JvmInline
public value class OperationGroupId(
    public val value: String,
) {
    init {
        require(value.isNotBlank())
    }
}
