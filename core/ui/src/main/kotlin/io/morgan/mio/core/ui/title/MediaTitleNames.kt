package io.morgan.mio.core.ui.title

public data class MediaTitleNames(
    public val english: String? = null,
    public val japanese: String? = null,
    public val romaji: String? = null,
) {
    public fun resolve(
        fallback: String,
        language: TitleLanguage,
        deviceLanguage: String,
    ): String {
        val effective =
            if (language == TitleLanguage.SYSTEM) {
                if (deviceLanguage.equals("ja", ignoreCase = true)) TitleLanguage.JAPANESE else TitleLanguage.ENGLISH
            } else {
                language
            }
        val preferred =
            when (effective) {
                TitleLanguage.JAPANESE -> japanese
                TitleLanguage.ROMAJI -> romaji
                else -> english
            }
        return preferred?.takeIf(String::isNotBlank) ?: fallback
    }

    public fun matches(query: String): Boolean =
        listOfNotNull(english, japanese, romaji).any {
            it.contains(query, ignoreCase = true)
        }
}
