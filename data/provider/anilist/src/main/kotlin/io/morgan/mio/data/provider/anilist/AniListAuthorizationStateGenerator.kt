package io.morgan.mio.data.provider.anilist

import java.security.SecureRandom
import java.util.Base64

internal fun interface AniListAuthorizationStateGenerator {
    fun next(): String
}

internal class SecureAniListAuthorizationStateGenerator : AniListAuthorizationStateGenerator {
    private val random = SecureRandom()
    private val encoder = Base64.getUrlEncoder().withoutPadding()

    override fun next(): String {
        val bytes = ByteArray(32)
        random.nextBytes(bytes)
        return encoder.encodeToString(bytes)
    }
}
