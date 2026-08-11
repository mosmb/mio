package io.morgan.mio.domain.model

public enum class ProgressUnit {
    EPISODE,
    CHAPTER,
    VOLUME,
}

@JvmInline
public value class ProgressValue(
    public val value: Int,
) {
    init {
        require(value >= 0)
    }
}

@JvmInline
public value class UnitTotal(
    public val value: Int,
) {
    init {
        require(value > 0)
    }
}

@JvmInline
public value class UnitNumber(
    public val value: Int,
) {
    init {
        require(value > 0)
    }
}
