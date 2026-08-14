package io.morgan.mio.domain.account

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class AccountAuthorizationValuesTest {
    @Test
    fun authorizationValuesRejectBlankUrisAndRedactTheirContents() {
        assertThrows<IllegalArgumentException> { AccountAuthorizationLaunch(" ") }
        assertThrows<IllegalArgumentException> { AccountAuthorizationRedirect("\n") }

        val launch = AccountAuthorizationLaunch("https://provider.example/secret-token")
        val redirect = AccountAuthorizationRedirect("mio://callback?code=secret-code")

        assertFalse(launch.toString().contains("secret-token"))
        assertFalse(redirect.toString().contains("secret-code"))
        assertEquals("AccountAuthorizationLaunch([REDACTED])", launch.toString())
        assertEquals("AccountAuthorizationRedirect([REDACTED])", redirect.toString())
    }
}
