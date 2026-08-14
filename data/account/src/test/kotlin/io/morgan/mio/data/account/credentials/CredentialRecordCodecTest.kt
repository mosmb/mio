package io.morgan.mio.data.account.credentials

import io.morgan.mio.data.provider.api.ProviderCredentials
import io.morgan.mio.data.provider.api.ProviderKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import kotlin.time.Instant

class CredentialRecordCodecTest {
    private val codec = CredentialRecordCodec()

    @Test
    fun roundTripsBothProvidersAndNullableFields() {
        val records =
            listOf(
                ProviderCredentials(
                    ProviderKey.ANILIST,
                    "fixture-access-anilist",
                    "fixture-refresh-anilist",
                    Instant.parse("2026-08-14T09:10:11.123Z"),
                    "Bearer",
                ),
                ProviderCredentials(ProviderKey.MY_ANIME_LIST, "fixture-access-myanimelist", null, null, "Token"),
            )

        records.forEach { credentials ->
            val decoded = codec.decode(codec.storageKey(credentials.provider), codec.encode(credentials))

            assertEquals(credentials.provider, decoded.provider)
            assertEquals(credentials.accessToken, decoded.accessToken)
            assertEquals(credentials.refreshToken, decoded.refreshToken)
            assertEquals(credentials.expiresAt, decoded.expiresAt)
            assertEquals(credentials.tokenType, decoded.tokenType)
        }
    }

    @Test
    fun rejectsCorruptionTrailingBytesAndProviderMoves() {
        val credentials = ProviderCredentials(ProviderKey.ANILIST, "fixture-access", null, null, "Bearer")
        val encoded = codec.encode(credentials)

        listOf(
            encoded.copyOfRange(0, encoded.lastIndex),
            encoded + byteArrayOf(0),
            encoded.copyOf().also { bytes -> bytes[3] = 2 },
        ).forEach { corrupted ->
            assertIllegalArgument { codec.decode(codec.storageKey(ProviderKey.ANILIST), corrupted) }
        }
        assertIllegalArgument {
            codec.decode(codec.storageKey(ProviderKey.MY_ANIME_LIST), encoded)
        }
        assertFalse(codec.storageKey(ProviderKey.ANILIST).contains("ANILIST"))
    }

    private fun assertIllegalArgument(block: () -> Unit) {
        try {
            block()
        } catch (_: IllegalArgumentException) {
            return
        }
        throw AssertionError("Expected an invalid credential record")
    }
}
