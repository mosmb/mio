package io.morgan.mio.domain.model

@JvmInline
public value class UnitTotal(
    public val value: Int,
) {
    init {
        require(value > 0)
    }
}
