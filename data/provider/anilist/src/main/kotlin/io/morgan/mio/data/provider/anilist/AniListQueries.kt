package io.morgan.mio.data.provider.anilist

internal object AniListQueries {
    internal const val MEDIA_LIST_PAGE_SIZE: Int = 50
    internal const val AIRING_SCHEDULE_PAGE_SIZE: Int = 25
    internal const val MAX_MEDIA_LIST_PAGES: Int = 200

    internal const val VIEWER: String =
        "query MioViewer { Viewer { id name avatar { large medium } } }"

    internal const val MEDIA_LIST_PAGE: String =
        "query MioMediaListPage(\$userId: Int!, \$type: MediaType!, \$page: Int!, \$perPage: Int!) { " +
            "Page(page: \$page, perPage: \$perPage) { " +
            "pageInfo { currentPage hasNextPage } " +
            "mediaList(userId: \$userId, type: \$type, sort: [MEDIA_ID]) { " +
            "id status progress progressVolumes updatedAt createdAt " +
            "media { " +
            "id type format status episodes chapters volumes description bannerImage genres seasonYear averageScore " +
            "title { userPreferred romaji english native } " +
            "coverImage { extraLarge large medium } " +
            "airingSchedule(notYetAired: false, perPage: $AIRING_SCHEDULE_PAGE_SIZE) " +
            "{ nodes { episode airingAt } } " +
            "} } } }"

    internal const val MEDIA_DETAILS: String =
        "query MioMediaDetails(\$id: Int!) { " +
            "Media(id: \$id) { " +
            "id type format status episodes chapters volumes description bannerImage genres seasonYear averageScore " +
            "title { userPreferred romaji english native } " +
            "coverImage { extraLarge large medium } " +
            "airingSchedule(notYetAired: false, perPage: $AIRING_SCHEDULE_PAGE_SIZE) " +
            "{ nodes { episode airingAt } } " +
            "relations { nodes { " +
            "id type format status episodes chapters volumes description bannerImage genres seasonYear averageScore " +
            "title { userPreferred romaji english native } " +
            "coverImage { extraLarge large medium } " +
            "} } } }"
}
