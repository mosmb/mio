package io.morgan.mio.data.media

import io.morgan.mio.domain.model.MediaId

public fun interface MediaRefreshRequester {
    public suspend fun refreshMedia(mediaId: MediaId)
}
