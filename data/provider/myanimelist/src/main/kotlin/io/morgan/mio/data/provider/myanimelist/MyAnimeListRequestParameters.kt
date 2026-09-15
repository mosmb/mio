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

    const val SYNC_ANIME_FIELDS: String =
        "id,num_episodes,my_list_status{status,num_episodes_watched,is_rewatching,updated_at}"
    const val SYNC_MANGA_FIELDS: String =
        "id,num_chapters,num_volumes,my_list_status{status,num_chapters_read,num_volumes_read,is_rereading,updated_at}"
}
