package io.morgan.mio.data.provider.myanimelist

import android.util.Base64
import java.security.SecureRandom

internal fun interface MyAnimeListAuthorizationSecretGenerator {
    fun next(): String
}

internal class SecureMyAnimeListAuthorizationSecretGenerator : MyAnimeListAuthorizationSecretGenerator {
    private val random = SecureRandom()

    override fun next(): String {
        val bytes = ByteArray(32)
        random.nextBytes(bytes)
        return Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_PADDING)
    }
}
