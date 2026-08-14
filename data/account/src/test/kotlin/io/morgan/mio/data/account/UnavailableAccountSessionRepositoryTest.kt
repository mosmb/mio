package io.morgan.mio.data.account

import io.morgan.mio.domain.account.AccountAuthorizationRedirect
import io.morgan.mio.domain.account.AccountSessionFailureReason
import io.morgan.mio.domain.account.AccountSessionOperation
import io.morgan.mio.domain.account.AccountSessionState
import io.morgan.mio.domain.account.ConnectedProvider
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class UnavailableAccountSessionRepositoryTest {
    @Test
    fun restorationHonestlyResolvesToSignedOutAndIsIdempotent() =
        runTest {
            val repository = UnavailableAccountSessionRepository()

            repository.restore()
            repository.restore()

            assertEquals(AccountSessionState.SignedOut, repository.state.value)
        }

    @Test
    fun connectionReportsUnavailableWithoutSimulatingSuccess() =
        runTest {
            val repository = UnavailableAccountSessionRepository()
            repository.restore()

            assertNull(repository.startConnection(ConnectedProvider.ANILIST))
            assertEquals(
                AccountSessionState.Failed(
                    provider = ConnectedProvider.ANILIST,
                    operation = AccountSessionOperation.AUTHORIZATION,
                    reason = AccountSessionFailureReason.UNAVAILABLE,
                    retryable = false,
                ),
                repository.state.value,
            )
        }

    @Test
    fun outOfOrderRedirectIsRejectedWithoutEchoingItsUri() {
        val repository = UnavailableAccountSessionRepository()
        val redirect = AccountAuthorizationRedirect("mio://callback?code=secret-code")

        val failure =
            assertThrows(IllegalStateException::class.java) {
                kotlinx.coroutines.test.runTest { repository.completeAuthorization(redirect) }
            }

        assertFalse(failure.message.orEmpty().contains(redirect.redirectUri))
    }

    @Test
    fun launchFailureReportingIsIrrelevantWhenProductionCannotLaunchAuthorization() =
        runTest {
            val repository = UnavailableAccountSessionRepository()
            repository.restore()

            repository.failAuthorizationLaunch()

            assertEquals(AccountSessionState.SignedOut, repository.state.value)
        }
}
