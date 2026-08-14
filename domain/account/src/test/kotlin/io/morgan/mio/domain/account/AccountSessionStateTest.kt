package io.morgan.mio.domain.account

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

class AccountSessionStateTest {
    @Test
    fun failedStateSeparatesOperationFromReason() {
        val state =
            AccountSessionState.Failed(
                provider = ConnectedProvider.MY_ANIME_LIST,
                operation = AccountSessionOperation.AUTHENTICATION,
                reason = AccountSessionFailureReason.UNAVAILABLE,
                retryable = true,
            )

        assertEquals(AccountSessionOperation.AUTHENTICATION, state.operation)
        assertEquals(AccountSessionFailureReason.UNAVAILABLE, state.reason)
        assertFalse(AccountSessionOperation.entries.any { operation -> operation.name == "UNAVAILABLE" })
    }
}
