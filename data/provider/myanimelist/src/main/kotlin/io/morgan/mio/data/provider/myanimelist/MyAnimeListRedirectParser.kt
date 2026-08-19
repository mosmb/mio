package io.morgan.mio.data.provider.myanimelist

import java.net.URLDecoder

internal object MyAnimeListRedirectParser {
    internal fun queryParameters(redirectUri: String): Map<String, String> = parseParameters(query(redirectUri))

    private fun query(redirectUri: String): String {
        val questionMarkIndex = redirectUri.indexOf('?')
        if (questionMarkIndex < 0) return ""
        val hashIndex = redirectUri.indexOf('#')
        if (hashIndex in 0..questionMarkIndex) return ""
        val end = if (hashIndex < 0) redirectUri.length else hashIndex
        return redirectUri.substring(questionMarkIndex + 1, end)
    }

    private fun parseParameters(section: String): Map<String, String> {
        if (section.isEmpty()) return emptyMap()
        val result = LinkedHashMap<String, String>()
        section.split('&').forEach { pair ->
            if (pair.isEmpty()) return@forEach
            val equalsIndex = pair.indexOf('=')
            val rawKey = if (equalsIndex < 0) pair else pair.substring(0, equalsIndex)
            val rawValue = if (equalsIndex < 0) "" else pair.substring(equalsIndex + 1)
            val key = decode(rawKey) ?: return@forEach
            if (key.isBlank()) return@forEach
            if (key in result) return@forEach
            val value = decode(rawValue) ?: return@forEach
            result[key] = value
        }
        return result
    }

    private fun decode(value: String): String? =
        try {
            URLDecoder.decode(value, "UTF-8")
        } catch (malformed: IllegalArgumentException) {
            null
        }
}
