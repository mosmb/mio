package io.morgan.mio.core.ui.title

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaTitleNamesTest {
    private val names = MediaTitleNames("My Hero Academia", "僕のヒーローアカデミア", "Boku no Hero Academia")

    @Test fun preferenceAndDeviceLanguageSelectOnlyAvailableNames() {
        assertEquals("My Hero Academia", names.resolve("Canonical", TitleLanguage.ENGLISH, "ja"))
        assertEquals("僕のヒーローアカデミア", names.resolve("Canonical", TitleLanguage.JAPANESE, "en"))
        assertEquals("Boku no Hero Academia", names.resolve("Canonical", TitleLanguage.ROMAJI, "en"))
        assertEquals("僕のヒーローアカデミア", names.resolve("Canonical", TitleLanguage.SYSTEM, "ja"))
        assertEquals("My Hero Academia", names.resolve("Canonical", TitleLanguage.SYSTEM, "fr"))
        assertEquals("Original", MediaTitleNames(english = " ").resolve("Original", TitleLanguage.ENGLISH, "en"))
        assertEquals("Original", MediaTitleNames().resolve("Original", TitleLanguage.JAPANESE, "ja"))
    }

    @Test fun searchesMatchAllNamesRegardlessOfDisplayChoice() {
        assertTrue(names.matches("hero"))
        assertTrue(names.matches("ヒーロー"))
        assertTrue(names.matches("BOKU"))
    }
}
