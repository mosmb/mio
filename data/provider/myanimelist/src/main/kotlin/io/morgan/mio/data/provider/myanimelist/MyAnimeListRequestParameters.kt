package io.morgan.mio.data.provider.myanimelist

internal object MyAnimeListRequestParameters {
    const val LIST_PAGE_SIZE: Int = 100
    const val MAX_LIST_PAGES: Int = 200
    const val ANIME_FIELDS: String =
        "id,title,main_picture,synopsis,media_type,status,num_episodes,mean,start_date,start_season{year},genres,list_status"
    const val MANGA_FIELDS: String =
        "id,title,main_picture,synopsis,media_type,status,num_chapters,num_volumes,mean,start_date,start_season{year},genres,list_status"
    const val ANIME_DETAIL_FIELDS: String =
        "id,title,main_picture,synopsis,media_type,status,num_episodes,mean,start_date,start_season{year},genres," +
            "related_anime{node{id,title,main_picture,synopsis,media_type,status,num_episodes,mean," +
            "start_date,start_season{year},genres}}," +
            "related_manga{node{id,title,main_picture,synopsis,media_type,status,num_chapters,num_volumes,mean," +
            "start_date,start_season{year},genres}}"
    const val MANGA_DETAIL_FIELDS: String =
        "id,title,main_picture,synopsis,media_type,status,num_chapters,num_volumes,mean,start_date," +
            "start_season{year},genres," +
            "related_anime{node{id,title,main_picture,synopsis,media_type,status,num_episodes,mean," +
            "start_date,start_season{year},genres}}," +
            "related_manga{node{id,title,main_picture,synopsis,media_type,status,num_chapters,num_volumes,mean," +
            "start_date,start_season{year},genres}}"
}
