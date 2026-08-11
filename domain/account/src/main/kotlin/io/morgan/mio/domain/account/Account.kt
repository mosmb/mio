package io.morgan.mio.domain.account

import io.morgan.mio.domain.model.AccountId
import io.morgan.mio.domain.model.ImageReference

public enum class ConnectedProvider {
    ANILIST,
    MY_ANIME_LIST,
}

public data class Account(
    public val id: AccountId,
    public val provider: ConnectedProvider,
    public val displayName: String,
    public val avatar: ImageReference?,
) {
    init {
        require(displayName.isNotBlank())
    }
}
