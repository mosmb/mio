package io.morgan.mio.domain.media

@JvmInline
public value class MediaScore(
    public val percent: Int,
) {
    init {
        require(percent in 1..100)
    }
}
