package io.morgan.mio.data.provider.api

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class ProviderAuthenticationTest {
    @Test
    fun requiredIdentityAndAuthenticationStringsRejectBlankValues() {
        assertThrows<IllegalArgumentException> { ProviderAccountId(" ") }
        assertThrows<IllegalArgumentException> { ProviderMediaId("") }
        assertThrows<IllegalArgumentException> { ProviderCollectionEntryId("\t") }
        assertThrows<IllegalArgumentException> { ProviderImageReference(" ") }
        assertThrows<IllegalArgumentException> {
            ProviderIdentity(ProviderKey.ANILIST, ProviderAccountId("account"), "", null)
        }
        assertThrows<IllegalArgumentException> { ProviderAuthorizationRequest("") }
        assertThrows<IllegalArgumentException> { ProviderAuthorizationTransaction("", null) }
        assertThrows<IllegalArgumentException> {
            ProviderAuthorizationTransaction("state", " ")
        }
        assertThrows<IllegalArgumentException> {
            ProviderAuthorizationLaunch("", ProviderAuthorizationTransaction("state", null))
        }
        assertThrows<IllegalArgumentException> { ProviderAuthorizationRedirect(" ") }
        assertThrows<IllegalArgumentException> {
            ProviderCredentials(ProviderKey.ANILIST, "", null, null, "Bearer")
        }
        assertThrows<IllegalArgumentException> {
            ProviderCredentials(ProviderKey.ANILIST, "access", " ", null, "Bearer")
        }
        assertThrows<IllegalArgumentException> {
            ProviderCredentials(ProviderKey.ANILIST, "access", null, null, "")
        }
    }

    @Test
    fun secretBearingAuthenticationTypesAreRedacted() {
        val sentinel = "fixture-sensitive-value"
        val transaction = ProviderAuthorizationTransaction(sentinel, sentinel)
        val launch = ProviderAuthorizationLaunch(sentinel, transaction)
        val redirect = ProviderAuthorizationRedirect(sentinel)
        val credentials = ProviderCredentials(ProviderKey.ANILIST, sentinel, sentinel, null, sentinel)

        assertEquals("ProviderAuthorizationTransaction([REDACTED])", transaction.toString())
        assertEquals("ProviderAuthorizationLaunch([REDACTED])", launch.toString())
        assertEquals("ProviderAuthorizationRedirect([REDACTED])", redirect.toString())
        assertEquals("ProviderCredentials([REDACTED])", credentials.toString())
        assertTrue(transaction.toString().contains("REDACTED"))
        assertTrue(
            !listOf(transaction, launch, redirect, credentials).any { value ->
                value.toString().contains(sentinel)
            },
        )
    }

    @Test
    fun nullableVerifierAndRefreshTokenRepresentAbsence() {
        val transaction = ProviderAuthorizationTransaction("state", null)
        val credentials = ProviderCredentials(ProviderKey.ANILIST, "access", null, null, "Bearer")

        assertEquals(null, transaction.codeVerifier)
        assertEquals(null, credentials.refreshToken)
    }
}
