package io.morgan.mio.data.provider.myanimelist

import org.junit.Assert.assertEquals
import org.junit.Test

class MyAnimeListRedirectParserTest {
    @Test
    fun queryParametersAreParsed() {
        assertEquals(
            mapOf("code" to "fixture-code", "state" to "fixture-state"),
            MyAnimeListRedirectParser.queryParameters(
                "mio://oauth/myanimelist?code=fixture-code&state=fixture-state",
            ),
        )
    }

    @Test
    fun anAbsentQueryReturnsAnEmptyMap() {
        assertEquals(
            emptyMap<String, String>(),
            MyAnimeListRedirectParser.queryParameters("mio://oauth/myanimelist"),
        )
    }

    @Test
    fun anEmptyQueryReturnsAnEmptyMap() {
        assertEquals(
            emptyMap<String, String>(),
            MyAnimeListRedirectParser.queryParameters("mio://oauth/myanimelist?"),
        )
    }

    @Test
    fun aFragmentAfterTheQueryIsExcluded() {
        assertEquals(
            mapOf("code" to "fixture-code"),
            MyAnimeListRedirectParser.queryParameters("mio://oauth/myanimelist?code=fixture-code#fragment"),
        )
    }

    @Test
    fun aQueryMarkerAfterTheFragmentMarkerIsIgnored() {
        assertEquals(
            emptyMap<String, String>(),
            MyAnimeListRedirectParser.queryParameters("mio://oauth/myanimelist#fragment?code=fixture-code"),
        )
    }

    @Test
    fun valuesArePercentDecoded() {
        assertEquals(
            mapOf("code" to "fixture code", "state" to "fixture+state"),
            MyAnimeListRedirectParser.queryParameters(
                "mio://oauth/myanimelist?code=fixture%20code&state=fixture%2Bstate",
            ),
        )
    }

    @Test
    fun aDuplicateKeyKeepsTheFirstOccurrence() {
        assertEquals(
            mapOf("code" to "first"),
            MyAnimeListRedirectParser.queryParameters("mio://oauth/myanimelist?code=first&code=second"),
        )
    }

    @Test
    fun aBlankKeyIsDropped() {
        assertEquals(
            mapOf("code" to "fixture-code"),
            MyAnimeListRedirectParser.queryParameters("mio://oauth/myanimelist?=value&code=fixture-code"),
        )
    }

    @Test
    fun aMalformedPercentSequenceDropsItsPairInsteadOfThrowing() {
        assertEquals(
            mapOf("state" to "fixture-state"),
            MyAnimeListRedirectParser.queryParameters("mio://oauth/myanimelist?code=%zz&state=fixture-state"),
        )
    }

    @Test
    fun anIncompleteTrailingEscapeDropsItsPairInsteadOfThrowing() {
        assertEquals(
            mapOf("state" to "fixture-state"),
            MyAnimeListRedirectParser.queryParameters("mio://oauth/myanimelist?code=fixture%&state=fixture-state"),
        )
    }

    @Test
    fun aMalformedKeyDropsItsPairInsteadOfThrowing() {
        assertEquals(
            mapOf("state" to "fixture-state"),
            MyAnimeListRedirectParser.queryParameters("mio://oauth/myanimelist?%zz=value&state=fixture-state"),
        )
    }

    @Test
    fun aPairWithoutAValueParsesToAnEmptyValue() {
        assertEquals(
            mapOf("code" to "", "state" to "fixture-state"),
            MyAnimeListRedirectParser.queryParameters("mio://oauth/myanimelist?code&state=fixture-state"),
        )
    }
}
