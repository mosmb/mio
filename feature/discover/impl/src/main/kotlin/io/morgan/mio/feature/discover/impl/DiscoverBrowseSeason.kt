package io.morgan.mio.feature.discover.impl

import kotlin.time.Instant

/** UTC quarters match the provider request's season boundaries. */
internal data class DiscoverBrowseSeason(
    val year: Int,
    val quarter: Int,
) {
    fun next(): DiscoverBrowseSeason =
        if (quarter == 3) DiscoverBrowseSeason(year + 1, 0) else copy(quarter = quarter + 1)

    companion object {
        fun at(instant: Instant): DiscoverBrowseSeason {
            val date = instant.toString()
            return DiscoverBrowseSeason(date.substring(0, 4).toInt(), (date.substring(5, 7).toInt() - 1) / 3)
        }
    }
}
