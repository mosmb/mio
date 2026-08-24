package io.morgan.mio.domain.queue

public class TodayQueue(
    public val now: NowRecommendation?,
    newRecommendations: List<NewRecommendation>,
    public val remainingReadyCount: Int,
    public val caughtUp: CaughtUpSummary?,
) {
    public val newRecommendations: List<NewRecommendation> = newRecommendations.toList()

    init {
        require(remainingReadyCount >= 0)
        require(this.newRecommendations.size <= 3)
        require(
            this.newRecommendations.distinctBy { recommendation -> recommendation.mediaId }.size ==
                this.newRecommendations.size,
        )
        if (now != null) {
            require(caughtUp == null)
            require(now.mediaId !in this.newRecommendations.map { recommendation -> recommendation.mediaId })
        } else {
            require(caughtUp != null)
            require(this.newRecommendations.isEmpty())
            require(remainingReadyCount == 0)
        }
    }

    override fun equals(other: Any?): Boolean =
        this === other ||
            (
                other is TodayQueue &&
                    now == other.now &&
                    newRecommendations == other.newRecommendations &&
                    remainingReadyCount == other.remainingReadyCount &&
                    caughtUp == other.caughtUp
            )

    override fun hashCode(): Int {
        var result = now?.hashCode() ?: 0
        result = 31 * result + newRecommendations.hashCode()
        result = 31 * result + remainingReadyCount
        result = 31 * result + (caughtUp?.hashCode() ?: 0)
        return result
    }

    override fun toString(): String =
        "TodayQueue(now=$now, newRecommendations=$newRecommendations, " +
            "remainingReadyCount=$remainingReadyCount, caughtUp=$caughtUp)"
}
