package io.morgan.mio.data.provider.anilist

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AniListRedirectParserTest {
    @Test
    fun fragmentParametersAreParsed() {
        val parameters =
            AniListRedirectParser.fragmentParameters(
                "https://app.invalid/redirect#access_token=abc&token_type=Bearer&expires_in=3600",
            )

        assertEquals("abc", parameters["access_token"])
        assertEquals("Bearer", parameters["token_type"])
        assertEquals("3600", parameters["expires_in"])
    }

    @Test
    fun queryParametersAreParsed() {
        val parameters = AniListRedirectParser.queryParameters("https://app.invalid/redirect?error=access_denied")

        assertEquals("access_denied", parameters["error"])
    }

    @Test
    fun absentSectionReturnsEmptyMap() {
        assertTrue(AniListRedirectParser.queryParameters("https://app.invalid/redirect").isEmpty())
        assertTrue(AniListRedirectParser.fragmentParameters("https://app.invalid/redirect").isEmpty())
    }

    @Test
    fun emptySectionReturnsEmptyMap() {
        assertTrue(AniListRedirectParser.queryParameters("https://app.invalid/redirect?").isEmpty())
        assertTrue(AniListRedirectParser.fragmentParameters("https://app.invalid/redirect#").isEmpty())
    }

    @Test
    fun percentEncodedValuesAreDecoded() {
        val parameters = AniListRedirectParser.fragmentParameters("https://app.invalid/redirect#name=a%20b%26c")

        assertEquals("a b&c", parameters["name"])
    }

    @Test
    fun duplicateKeysKeepTheFirstOccurrence() {
        val parameters = AniListRedirectParser.fragmentParameters("https://app.invalid/redirect#key=first&key=second")

        assertEquals("first", parameters["key"])
    }

    @Test
    fun blankKeysAreDropped() {
        val parameters = AniListRedirectParser.fragmentParameters("https://app.invalid/redirect#=value&real=value2")

        assertEquals(setOf("real"), parameters.keys)
    }

    @Test
    fun queryStopsAtTheFirstFragmentMarker() {
        val parameters =
            AniListRedirectParser.queryParameters("https://app.invalid/redirect?a=1#fragment?notquery=2")

        assertEquals(mapOf("a" to "1"), parameters)
    }

    @Test
    fun fragmentAfterHashIncludesEverythingAfterIt() {
        val parameters =
            AniListRedirectParser.fragmentParameters("https://app.invalid/redirect?a=1#access_token=xyz&extra=1")

        assertEquals("xyz", parameters["access_token"])
        assertEquals("1", parameters["extra"])
    }

    @Test
    fun malformedPercentEncodedValueDropsThatParameterWithoutThrowing() {
        val parameters =
            AniListRedirectParser.fragmentParameters(
                "https://app.invalid/redirect#access_token=%zz&token_type=Bearer",
            )

        assertEquals(setOf("token_type"), parameters.keys)
    }

    @Test
    fun incompleteTrailingEscapeDropsThatParameterWithoutThrowing() {
        val parameters =
            AniListRedirectParser.fragmentParameters("https://app.invalid/redirect#access_token=abc%&extra=1")

        assertEquals(setOf("extra"), parameters.keys)
    }

    @Test
    fun malformedPercentEncodedKeyDropsThatParameterWithoutThrowing() {
        val parameters = AniListRedirectParser.fragmentParameters("https://app.invalid/redirect#%zz=value&real=ok")

        assertEquals(setOf("real"), parameters.keys)
    }
}
