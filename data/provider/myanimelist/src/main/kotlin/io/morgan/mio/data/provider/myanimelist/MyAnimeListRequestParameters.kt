package io.morgan.mio.data.provider.myanimelist

internal object MyAnimeListRequestParameters {
    const val LIST_PAGE_SIZE: Int = 100
    const val MAX_LIST_PAGES: Int = 200
    const val ANIME_FIELDS: String =
        "id,title,main_picture,synopsis,media_type,status,num_episodes,genres,list_status"
    const val MANGA_FIELDS: String =
        "id,title,main_picture,synopsis,media_type,status,num_chapters,num_volumes,genres,list_status"
}
